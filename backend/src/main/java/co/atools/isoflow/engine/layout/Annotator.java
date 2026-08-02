// Annotator.java — 좌표 태그, 노스 애로우, 라인 번호, 용접 번호, 연속 표기를 붙인다
package co.atools.isoflow.engine.layout;

import co.atools.isoflow.engine.geometry.IsoProjection;
import co.atools.isoflow.engine.model.ComponentType;
import co.atools.isoflow.engine.model.Pipeline;
import co.atools.isoflow.engine.model.PipingComponent;
import co.atools.isoflow.engine.model.Port;
import co.atools.isoflow.engine.model.PortKind;
import co.atools.isoflow.engine.model.Vec3;
import co.atools.isoflow.engine.symbol.Symbol2dLibrary;

import java.util.ArrayList;
import java.util.List;
import co.atools.isoflow.engine.scene.Scene2D;

public final class Annotator {

    /** 주석 문자 높이 (symbolUnit 배수) */
    private static final double TEXT_HEIGHT = 1.0;
    /** 라벨을 대상에서 띄우는 기본 거리 */
    private static final double LEADER = 1.8;
    /** 후보 방향 8방위 */
    private static final double[][] CANDIDATE_DIRS = {
            {1, 0.5}, {-1, 0.5}, {1, -0.5}, {-1, -0.5},
            {0, 1.2}, {0, -1.2}, {1.2, 0}, {-1.2, 0},
    };
    /** 방향마다 시도할 거리 배수 — 가까운 자리가 막히면 점점 밀어낸다 */
    private static final double[] CANDIDATE_RADII = {1.0, 2.0, 3.4};

    private final IsoProjection projection;
    private final double symbolUnit;
    private final LabelPlacer placer;
    private final Symbol2dLibrary symbols;
    private final String textLayer;
    private final String symbolLayer;
    private int seq;

    public Annotator(IsoProjection projection, double symbolUnit, LabelPlacer placer,
                     Symbol2dLibrary symbols, String textLayer, String symbolLayer) {
        this.projection = projection;
        this.symbolUnit = symbolUnit;
        this.placer = placer;
        this.symbols = symbols;
        this.textLayer = textLayer;
        this.symbolLayer = symbolLayer;
    }

    /** 도면 좌상단의 라인 번호 */
    public List<Scene2D.Element> lineNumber(double[] bounds, Pipeline pipeline) {
        if (pipeline.lineNumber() == null || pipeline.lineNumber().isBlank()) return List.of();
        double h = symbolUnit * 1.8;
        double x = bounds[0];
        double y = bounds[3] + h * 2;
        return List.of(new Scene2D.Text(id(), textLayer, Symbol2dLibrary.Styles.TEXT,
                x, y, pipeline.lineNumber(), 0.0, "start", h));
    }

    /** 도면 우상단의 노스 애로우 */
    public List<Scene2D.Element> northArrow(double[] bounds) {
        double size = symbolUnit * 2.2;
        double x = bounds[2] + size * 2;
        double y = bounds[3] - size;
        // 화면 기준 심볼이라 affine 의 선형부는 등방 스케일이면 충분하다
        double[] affine = {size, 0, 0, size, x, y};
        return symbols.place("NORTH_ARROW", affine, size, symbolLayer, id());
    }

    /**
     * 라인 종단의 절대 좌표 태그.
     * 도면 좌표는 리베이스된 로컬값이므로 origin 을 더해 원래 플랜트 좌표로 되돌려 표기한다.
     */
    public List<Scene2D.Element> coordinateTags(Pipeline pipeline) {
        return coordinateTags(pipeline, c -> true);
    }

    /**
     * @param onSheet 이 장에 그릴 컴포넌트만 통과시킨다 (시트 분할 시)
     */
    public List<Scene2D.Element> coordinateTags(Pipeline pipeline,
                                                java.util.function.Predicate<PipingComponent> onSheet) {
        List<Scene2D.Element> out = new ArrayList<>();
        for (PipingComponent c : pipeline.components()) {
            if (!onSheet.test(c)) continue;
            if (c.type() != ComponentType.END_CONNECTION_PIPELINE
                    && c.type() != ComponentType.END_POSITION_OPEN) continue;
            Port co = c.portOf(PortKind.COORD, 0).orElse(null);
            if (co == null) continue;

            Vec3 abs = co.position().plus(pipeline.origin());
            out.addAll(placeLabel(co.position(),
                    List.of("N %s  E %s".formatted(round(abs.y()), round(abs.x())),
                            "EL %s".formatted(round(abs.z())))));
        }
        return out;
    }

    /** 다른 라인으로 이어지는 끝에 연속 표기와 참조 라인 번호를 붙인다 */
    public List<Scene2D.Element> continuations(Pipeline pipeline) {
        return continuations(pipeline, c -> true);
    }

    public List<Scene2D.Element> continuations(Pipeline pipeline,
                                               java.util.function.Predicate<PipingComponent> onSheet) {
        List<Scene2D.Element> out = new ArrayList<>();
        for (PipingComponent c : pipeline.components()) {
            if (!onSheet.test(c)) continue;
            if (c.type() != ComponentType.END_CONNECTION_PIPELINE) continue;
            String ref = c.attrs().get("CONNECTION-REFERENCE");
            if (ref == null || ref.isBlank()) continue;
            Port co = c.portOf(PortKind.COORD, 0).orElse(null);
            if (co == null) continue;
            // 화살표는 ASCII 로 쓴다 — DXF R12 는 ANSI 기반이라 유니코드 화살표가 '?' 로 깨진다
            out.addAll(placeLabel(co.position(), List.of("-> " + ref)));
        }
        return out;
    }

    /**
     * 용접 번호. SKEY 로 공장(SW)/현장(FW)을 구분해 각각 1번부터 매긴다.
     *
     * <p><b>{@code WELD-ATTRIBUTE1} 을 번호로 쓰면 안 된다.</b> 실 코퍼스에서 확인해 보니
     * 용접 7개가 같은 값을 공유하는 <i>스풀 식별자</i>였다. 용접마다 유일한 값이 아니다.
     * (스풀 번호로서의 용도는 M5 스풀 작업에서 따로 다룬다.)
     */
    public List<Scene2D.Element> weldNumbers(Pipeline pipeline) {
        return weldNumbers(pipeline, c -> true);
    }

    /**
     * @param onSheet 이 장에 그릴 컴포넌트만 라벨을 만든다.
     *                <b>번호는 라인 전체를 세면서 매긴다</b> — 시트마다 1번부터 다시 매기면
     *                같은 용접이 장마다 다른 번호를 갖게 된다
     */
    public List<Scene2D.Element> weldNumbers(Pipeline pipeline,
                                             java.util.function.Predicate<PipingComponent> onSheet) {
        List<Scene2D.Element> out = new ArrayList<>();
        int shop = 0;
        int field = 0;

        for (PipingComponent c : pipeline.components()) {
            if (c.type() != ComponentType.WELD) continue;
            Port at = c.ports().stream().filter(p -> p.kind() == PortKind.END).findFirst().orElse(null);
            if (at == null) continue;

            boolean isField = isFieldWeld(c.skey());
            String label = (isField ? "FW" : "SW") + (isField ? ++field : ++shop);
            if (onSheet.test(c)) out.addAll(placeLabel(at.position(), List.of(label)));
        }
        return out;
    }

    /** 다른 장으로 이어지는 지점 표기 */
    public List<Scene2D.Element> sheetLinks(List<SheetSplitter.Link> links) {
        List<Scene2D.Element> out = new ArrayList<>();
        for (SheetSplitter.Link l : links) {
            String text = (l.outgoing() ? "-> SHEET " : "<- SHEET ") + l.sheetNo();
            out.addAll(placeLabel(l.at(), List.of(text)));
        }
        return out;
    }

    /** 현장 용접 계열 SKEY 인지 (WS/WF/WO 계열) */
    static boolean isFieldWeld(String skey) {
        if (skey == null) return false;
        String s = skey.toUpperCase();
        return s.startsWith("WS") || s.startsWith("WF") || s.startsWith("WO") || s.startsWith("XX");
    }

    /**
     * 대상 점 주변에서 겹치지 않는 자리를 찾아 여러 줄 라벨을 놓는다.
     * 자리를 못 찾으면 마지막 후보에 그냥 놓는다 — 라벨을 통째로 빠뜨리는 것보다 낫다.
     */
    private List<Scene2D.Element> placeLabel(Vec3 world, List<String> lines) {
        double[] p = projection.project(world);
        double h = symbolUnit * TEXT_HEIGHT;
        double blockH = h * lines.size() * 1.25;
        double blockW = lines.stream()
                .mapToDouble(s -> LabelPlacer.estimateTextWidth(s, h)).max().orElse(h);

        // 가까운 자리부터 8방위로 훑고, 다 막히면 반경을 키워 다시 훑는다
        int total = CANDIDATE_RADII.length * CANDIDATE_DIRS.length;
        int tried = 0;
        for (double radius : CANDIDATE_RADII) {
            for (double[] d : CANDIDATE_DIRS) {
                double cx = p[0] + d[0] * (blockW / 2 + symbolUnit * LEADER * radius);
                double cy = p[1] + d[1] * (blockH / 2 + symbolUnit * LEADER * radius);
                LabelPlacer.Box box = LabelPlacer.Box.centred(cx, cy, blockW, blockH);

                boolean last = ++tried == total;
                boolean free = placer.isFree(box);
                if (free || last) {
                    placer.placeLabel(box, !free);
                    return renderLines(cx, cy, blockH, h, lines);
                }
            }
        }
        return List.of();
    }

    private List<Scene2D.Element> renderLines(double cx, double cy, double blockH, double h, List<String> lines) {
        List<Scene2D.Element> out = new ArrayList<>();
        double top = cy + blockH / 2 - h * 0.625;
        for (int i = 0; i < lines.size(); i++) {
            out.add(new Scene2D.Text(id(), textLayer, Symbol2dLibrary.Styles.TEXT,
                    cx, top - i * h * 1.25, lines.get(i), 0.0, "middle", h));
        }
        return out;
    }

    /** 좌표는 정수 mm 로 표기한다 */
    private static String round(double v) {
        return String.valueOf(Math.round(v));
    }

    private String id() {
        return "an" + (seq++);
    }
}
