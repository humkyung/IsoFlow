// Scene2dBuilder.java — IR 을 등각도(Scene2D)로 만든다. 중심선 작도 + 심볼 배치 + 사선 표기
package co.atools.isoflow.engine.scene;

import co.atools.isoflow.engine.diagnostic.DiagnosticCodes;
import co.atools.isoflow.engine.diagnostic.Diagnostics;
import co.atools.isoflow.engine.geometry.Axis6;
import co.atools.isoflow.engine.geometry.AxisClassifier;
import co.atools.isoflow.engine.geometry.IsoProjection;
import co.atools.isoflow.engine.layout.Annotator;
import co.atools.isoflow.engine.layout.CrossingBreaker;
import co.atools.isoflow.engine.layout.DimensionDrawer;
import co.atools.isoflow.engine.layout.DetailPlanner;
import co.atools.isoflow.engine.layout.DimensionRun;
import co.atools.isoflow.engine.layout.LabelPlacer;
import co.atools.isoflow.engine.layout.SheetSplitter;
import co.atools.isoflow.engine.model.ComponentType;
import co.atools.isoflow.engine.model.Pipeline;
import co.atools.isoflow.engine.model.PipingComponent;
import co.atools.isoflow.engine.model.Port;
import co.atools.isoflow.engine.model.PortKind;
import co.atools.isoflow.engine.model.Vec3;
import co.atools.isoflow.engine.style.IsoStyle;
import co.atools.isoflow.engine.symbol.SkeyTable;
import co.atools.isoflow.engine.symbol.SymbolSet;
import co.atools.isoflow.engine.symbol.Symbol2dLibrary;
import co.atools.isoflow.engine.symbol.Symbol2dLibrary.Styles;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class Scene2dBuilder {

    /** 레이어 id — DXF 레이어와 1:1 로 내보낸다 */
    public static final class Layers {
        public static final String CENTERLINE = "centerline";
        public static final String SYMBOL = "symbol";
        public static final String SKEW = "skew";
        public static final String DIMENSION = "dimension";
        public static final String TEXT = "text";
        /** 확대 상세도(버블 + 확대본) */
        public static final String DETAIL = "detail";

        private Layers() {
        }
    }

    /** 사선 삼각형 빗금 개수 */
    private static final int HATCH_LINES = 4;

    private final IsoProjection projection;
    private final SkeyTable skeyTable;
    private final Symbol2dLibrary symbols;
    private final Diagnostics diag;
    private final IsoStyle style;

    /**
     * 작도 결과의 라벨 밀도.
     *
     * @param placed   놓인 라벨 수
     * @param collided 자리를 못 찾아 겹친 채로 놓인 라벨 수
     */
    public record LabelStats(int placed, int collided) {
        public double collisionRatio() {
            return placed == 0 ? 0 : (double) collided / placed;
        }
    }

    private final List<Scene2D.Element> elements = new ArrayList<>();
    /** 중심선 구간(세계 좌표). 앞뒤 판정을 위해 한꺼번에 모은다 */
    private final List<CrossingBreaker.Segment> centerlines = new ArrayList<>();
    private double symbolUnit = 1;
    private int seq;
    private LabelStats labelStats = new LabelStats(0, 0);

    public Scene2dBuilder(IsoProjection projection, SkeyTable skeyTable,
                          Symbol2dLibrary symbols, Diagnostics diag, IsoStyle style) {
        this.projection = projection;
        this.skeyTable = skeyTable;
        this.symbols = symbols;
        this.diag = diag;
        this.style = style.withDefaults();
    }

    public static Scene2dBuilder standard(Diagnostics diag) {
        return standard(diag, IsoStyle.defaults());
    }

    public static Scene2dBuilder standard(Diagnostics diag, IsoStyle style) {
        return standard(diag, style, null);
    }

    /**
     * @param symbolSet 사용자 심볼이 덮인 세트. null 이면 기본 세트
     */
    public static Scene2dBuilder standard(Diagnostics diag, IsoStyle style, SymbolSet symbolSet) {
        SymbolSet set = symbolSet == null ? SymbolSet.standard() : symbolSet;
        return new Scene2dBuilder(IsoProjection.DEFAULT, new SkeyTable(set),
                new Symbol2dLibrary(set), diag, style);
    }

    /**
     * @param dimensionRuns 치수 계획. <b>길이 압축 전에</b> 만들어 넘겨야 실제 길이가 찍힌다
     */
    public Scene2D build(String sceneId, Pipeline pipeline, List<DimensionRun> dimensionRuns) {
        return build(sceneId, pipeline, pipeline.components(), dimensionRuns, List.of(), 1);
    }

    /**
     * 시트 한 장을 작도한다.
     *
     * @param onSheet   이 장에 그릴 컴포넌트. 나머지는 좌표·방향 조회에만 쓰인다
     * @param links     다른 장으로 이어지는 지점
     * @param sheetNo   시트 번호 (1-based)
     */
    public Scene2D build(String sceneId, Pipeline pipeline, List<PipingComponent> onSheet,
                         List<DimensionRun> dimensionRuns, List<SheetSplitter.Link> links,
                         int sheetNo) {
        java.util.Set<PipingComponent> visible =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        visible.addAll(onSheet);

        // 심볼 크기는 도면 크기에 비례해야 한다 — 먼저 투영 범위를 재고 나서 본작도를 한다
        double[] extent = projectedBounds(onSheet);
        double diagonal = Math.hypot(extent[2] - extent[0], extent[3] - extent[1]);
        symbolUnit = Math.max(diagonal * style.symbols().unitRatio(), style.symbols().minUnitMm());

        centerlines.clear();
        for (PipingComponent c : onSheet) {
            drawCenterlines(c);
        }
        emitCenterlines();
        int beforeSymbols = elements.size();
        for (PipingComponent c : onSheet) {
            drawSymbol(c, pipeline);
        }

        // 라벨이 도면 위에 앉지 않도록 이미 그린 것들의 자리를 먼저 등록한다.
        // 중심선은 상자가 아니라 선분으로 넣는다 — 긴 대각선의 상자는 도면 절반을 덮는다
        LabelPlacer placer = new LabelPlacer(symbolUnit * 0.3);
        occupyLineSegments(placer, 0, beforeSymbols);
        occupySymbolBoxes(placer, beforeSymbols);

        double[] centre = {(extent[0] + extent[2]) / 2, (extent[1] + extent[3]) / 2};
        if (style.display().dimensions()) {
            DimensionDrawer dims = new DimensionDrawer(projection, symbolUnit, placer,
                    Layers.DIMENSION, Styles.DIMENSION, Styles.TEXT, centre, style);
            for (DimensionRun run : dimensionRuns) {
                elements.addAll(dims.draw(run));
            }
        }

        Annotator annotator = new Annotator(projection, symbolUnit, placer, symbols,
                Layers.TEXT, Layers.SYMBOL);
        if (style.display().weldNumbers()) {
            elements.addAll(annotator.weldNumbers(pipeline, visible::contains));
        }
        if (style.display().coordinateTags()) {
            elements.addAll(annotator.coordinateTags(pipeline, visible::contains));
        }
        if (style.display().continuations()) {
            elements.addAll(annotator.continuations(pipeline, visible::contains));
        }
        // 시트 경계는 연속 표기와 같은 성격이라 같은 토글을 따른다
        if (style.display().continuations() && !links.isEmpty()) {
            elements.addAll(annotator.sheetLinks(links));
        }

        // 도곽 부속물(라인 번호·노스 애로우)은 배치기를 쓰지 않으므로 여기서 밀도를 확정한다
        labelStats = new LabelStats(placer.placedCount(), placer.collidedCount());

        // 상세도는 맨 마지막이다 — 치수·주석이 자리를 잡은 뒤 남은 빈 곳에 놓는다
        if (style.display().details()) {
            drawDetails(placer, beforeSymbols);
        }

        // 시트 부속물(라인 번호·노스 애로우)은 도면 전체 범위가 정해진 뒤에 붙인다
        double[] drawn = boundsOf(elements);
        if (style.display().lineNumber()) elements.addAll(annotator.lineNumber(drawn, pipeline));
        if (style.display().northArrow()) elements.addAll(annotator.northArrow(drawn));

        return new Scene2D(
                Scene2D.SCHEMA_VERSION, sceneId, "mm", sheetNo,
                boundsOf(elements),
                List.of(
                        new Scene2D.Layer(Layers.CENTERLINE, "Centerline", true, 0),
                        new Scene2D.Layer(Layers.SKEW, "Skew", true, 1),
                        new Scene2D.Layer(Layers.SYMBOL, "Symbol", true, 2),
                        new Scene2D.Layer(Layers.DIMENSION, "Dimension", true, 3),
                        new Scene2D.Layer(Layers.TEXT, "Text", true, 4),
                        new Scene2D.Layer(Layers.DETAIL, "Detail", true, 5)),
                defaultStyles(),
                List.copyOf(elements));
    }

    /** 직전 {@code build} 의 라벨 밀도 */
    public LabelStats labelStats() {
        return labelStats;
    }

    /** 선 요소를 선분 점유로 등록한다 (중심선·사선 표기) */
    private void occupyLineSegments(LabelPlacer placer, int fromIndex, int toIndex) {
        for (int i = fromIndex; i < toIndex; i++) {
            switch (elements.get(i)) {
                case Scene2D.Line l -> placer.occupySegment(l.x1(), l.y1(), l.x2(), l.y2());
                case Scene2D.Polyline p -> {
                    List<Scene2D.Point> pts = p.points();
                    for (int k = 0; k + 1 < pts.size(); k++) {
                        placer.occupySegment(pts.get(k).x(), pts.get(k).y(),
                                pts.get(k + 1).x(), pts.get(k + 1).y());
                    }
                }
                default -> {
                    // 호·타원·문자는 중심선에 나오지 않는다
                }
            }
        }
    }

    // ─────────────────────────── 상세도 ───────────────────────────

    /** 한 도면에 놓을 상세도 상한 — 더 많아지면 도면이 상세도로 덮인다 */
    private static final int MAX_DETAILS = 4;

    /**
     * 심볼이 겹쳐 읽을 수 없는 구간을 원으로 표시하고, 확대본을 빈 자리에 따로 그린다.
     * <b>본도면의 심볼은 지우지 않는다</b> — 상세도를 안 봐도 무엇이 어디 있는지는 알 수 있어야 한다.
     */
    private void drawDetails(LabelPlacer placer, int symbolFrom) {
        List<LabelPlacer.Box> boxes = symbolBoxes(symbolFrom);
        if (boxes.size() < 2) return;

        double[] content = boundsOf(elements);
        List<DetailPlanner.Region> regions =
                DetailPlanner.plan(boxes, placer, symbolUnit, content, MAX_DETAILS);

        for (DetailPlanner.Region r : regions) {
            // 본도면 버블 + 이름
            elements.add(new Scene2D.Circle(id("dtb"), Layers.DETAIL, Styles.HIDDEN,
                    r.cx(), r.cy(), r.r()));
            addDetailLabel(placer, r.cx(), r.cy(), r.r(), r.label());

            // 확대본 — 원 안의 요소를 배율만큼 키워 옮겨 그린다
            // 확대본 원은 id 를 달리 한다 — 본도면 버블과 구분해야 검증도 렌더도 편하다
            elements.add(new Scene2D.Circle(id("dtv"), Layers.DETAIL, Styles.HIDDEN,
                    r.dx(), r.dy(), r.detailRadius()));
            addDetailLabel(placer, r.dx(), r.dy(), r.detailRadius(),
                    "DETAIL %s (%s:1)".formatted(r.label(), trim(r.scale())));

            for (Scene2D.Element e : copyInto(r)) elements.add(e);
        }
    }

    /**
     * 버블 이름을 원 둘레의 빈 자리에 붙인다.
     * 무조건 위에 붙이면 중심선이나 치수 위에 앉는다.
     */
    private void addDetailLabel(LabelPlacer placer, double cx, double cy, double radius, String text) {
        double h = symbolUnit * 1.1;
        double w = LabelPlacer.estimateTextWidth(text, h);
        // 8방위 × 반경 3단계 — 좁게 훑으면 결국 중심선 위에 앉는다
        double[][] dirs = {{0, 1}, {0, -1}, {1, 0}, {-1, 0},
                {0.71, 0.71}, {-0.71, 0.71}, {0.71, -0.71}, {-0.71, -0.71}};

        double x = cx, y = cy + radius + h;
        boolean placed = false;
        for (double away : new double[]{1.0, 1.8, 2.8}) {
            for (double[] d : dirs) {
                double px = cx + d[0] * (radius + (w / 2 + h) * away);
                double py = cy + d[1] * (radius + h * away);
                if (placer.isFree(LabelPlacer.Box.centred(px, py, w, h))) {
                    x = px;
                    y = py;
                    placed = true;
                    break;
                }
            }
            if (placed) break;
        }
        placer.placeLabel(LabelPlacer.Box.centred(x, y, w, h), !placed);
        elements.add(new Scene2D.Text(id("dtl"), Layers.DETAIL, Styles.TEXT,
                x, y, text, 0.0, "middle", h));
    }

    /** 상세도 원 안에 드는 요소를 확대·이동한 사본 */
    private List<Scene2D.Element> copyInto(DetailPlanner.Region r) {
        List<Scene2D.Element> out = new ArrayList<>();
        // 사본을 다시 훑지 않도록 지금까지의 요소만 본다
        List<Scene2D.Element> source = List.copyOf(elements);

        for (Scene2D.Element e : source) {
            if (Layers.DETAIL.equals(e.layerId())) continue;
            if (!Layers.CENTERLINE.equals(e.layerId()) && !Layers.SYMBOL.equals(e.layerId())) continue;

            if (e instanceof Scene2D.Line l) {
                double[] clipped = DetailPlanner.clipToCircle(l.x1(), l.y1(), l.x2(), l.y2(),
                        r.cx(), r.cy(), r.clipRadius());
                if (clipped == null) continue;
                double[] a = map(clipped[0], clipped[1], r);
                double[] b = map(clipped[2], clipped[3], r);
                out.add(new Scene2D.Line(id("dt"), Layers.DETAIL, l.styleRef(),
                        a[0], a[1], b[0], b[1]));
                continue;
            }
            // 심볼은 원 안에 중심이 들면 통째로 옮긴다 — 조각내면 형상을 알아볼 수 없다.
            // 크기는 그대로 두고 위치만 옮긴다 — 같이 키우면 겹침 비율이 그대로다
            double[] c = centreOf(e);
            if (c == null || Math.hypot(c[0] - r.cx(), c[1] - r.cy()) > r.clipRadius()) continue;
            double[] to = map(c[0], c[1], r);
            out.add(moved(e, to[0] - c[0], to[1] - c[1]));
        }
        return out;
    }

    /** 본도면 좌표 → 상세도 좌표 */
    private static double[] map(double x, double y, DetailPlanner.Region r) {
        return new double[]{r.dx() + (x - r.cx()) * r.scale(), r.dy() + (y - r.cy()) * r.scale()};
    }

    /**
     * 요소를 평행이동한 사본. <b>크기는 바꾸지 않는다</b> —
     * 상세도에서 심볼까지 같이 키우면 겹침 비율이 그대로라 아무것도 나아지지 않는다.
     */
    private Scene2D.Element moved(Scene2D.Element e, double dx, double dy) {
        return switch (e) {
            case Scene2D.Line l -> new Scene2D.Line(id("dt"), Layers.DETAIL, l.styleRef(),
                    l.x1() + dx, l.y1() + dy, l.x2() + dx, l.y2() + dy);
            case Scene2D.Polyline p -> new Scene2D.Polyline(id("dt"), Layers.DETAIL, p.styleRef(),
                    movePoints(p.points(), dx, dy));
            case Scene2D.Polygon p -> new Scene2D.Polygon(id("dt"), Layers.DETAIL, p.styleRef(),
                    movePoints(p.points(), dx, dy));
            case Scene2D.Circle x -> new Scene2D.Circle(id("dt"), Layers.DETAIL, x.styleRef(),
                    x.cx() + dx, x.cy() + dy, x.r());
            case Scene2D.Ellipse x -> new Scene2D.Ellipse(id("dt"), Layers.DETAIL, x.styleRef(),
                    x.cx() + dx, x.cy() + dy, x.rx(), x.ry(), x.rotation());
            case Scene2D.Arc x -> new Scene2D.Arc(id("dt"), Layers.DETAIL, x.styleRef(),
                    x.cx() + dx, x.cy() + dy, x.r(), x.rx(), x.ry(), x.rotation(),
                    x.startAngle(), x.endAngle());
            case Scene2D.Text t -> new Scene2D.Text(id("dt"), Layers.DETAIL, t.styleRef(),
                    t.x() + dx, t.y() + dy, t.content(), t.rotation(), t.anchor(), t.height());
        };
    }

    private List<Scene2D.Point> movePoints(List<Scene2D.Point> pts, double dx, double dy) {
        List<Scene2D.Point> out = new ArrayList<>(pts.size());
        for (Scene2D.Point p : pts) out.add(new Scene2D.Point(p.x() + dx, p.y() + dy));
        return out;
    }

    /** 요소의 대략적인 중심 */
    private static double[] centreOf(Scene2D.Element e) {
        List<double[]> pts = pointsOf(e);
        if (pts.isEmpty()) return null;
        double sx = 0, sy = 0;
        for (double[] p : pts) {
            sx += p[0];
            sy += p[1];
        }
        return new double[]{sx / pts.size(), sy / pts.size()};
    }

    /** 컴포넌트별 심볼 경계 상자 — id 접두사로 묶는다 */
    private List<LabelPlacer.Box> symbolBoxes(int fromIndex) {
        java.util.Map<String, double[]> groups = new java.util.LinkedHashMap<>();
        for (int i = fromIndex; i < elements.size(); i++) {
            Scene2D.Element e = elements.get(i);
            if (!Layers.SYMBOL.equals(e.layerId())) continue;
            String key = e.id().split("-")[0];
            for (double[] p : pointsOf(e)) {
                groups.merge(key, new double[]{p[0], p[1], p[0], p[1]}, (a, b) -> new double[]{
                        Math.min(a[0], b[0]), Math.min(a[1], b[1]),
                        Math.max(a[2], b[2]), Math.max(a[3], b[3])});
            }
        }
        List<LabelPlacer.Box> out = new ArrayList<>(groups.size());
        for (double[] b : groups.values()) out.add(new LabelPlacer.Box(b[0], b[1], b[2], b[3]));
        return out;
    }

    /** 2.5 → "2.5", 3.0 → "3" */
    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    /** 심볼 요소들의 경계를 라벨 배치기에 등록한다 */
    private void occupySymbolBoxes(LabelPlacer placer, int fromIndex) {
        for (int i = fromIndex; i < elements.size(); i++) {
            List<double[]> pts = pointsOf(elements.get(i));
            if (pts.isEmpty()) continue;
            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            for (double[] p : pts) {
                minX = Math.min(minX, p[0]);
                minY = Math.min(minY, p[1]);
                maxX = Math.max(maxX, p[0]);
                maxY = Math.max(maxY, p[1]);
            }
            placer.forceOccupy(new LabelPlacer.Box(minX, minY, maxX, maxY));
        }
    }

    // ─────────────────────────── 중심선 ───────────────────────────

    /**
     * 컴포넌트의 배관 중심선을 그린다.
     * 등각도에서 엘보는 호가 아니라 <b>모서리</b>로 그린다 — 중심점을 지나는 두 직선이다.
     */
    private void drawCenterlines(PipingComponent c) {
        List<Port> ends = c.ports().stream().filter(p -> p.kind() == PortKind.END).toList();
        Optional<Vec3> centre = c.centre();

        switch (c.type()) {
            case ELBOW, BEND -> {
                if (ends.size() < 2) return;
                if (centre.isPresent()) {
                    segment(ends.get(0).position(), centre.get());
                    segment(centre.get(), ends.get(1).position());
                } else {
                    segment(ends.get(0).position(), ends.get(1).position());
                }
            }
            case TEE, CROSS -> {
                if (ends.size() >= 2) segment(ends.get(0).position(), ends.get(1).position());
                Vec3 hub = centre.orElseGet(() -> ends.size() >= 2
                        ? midpoint(ends.get(0).position(), ends.get(1).position())
                        : null);
                if (hub != null) {
                    c.portOf(PortKind.BRANCH1, 0).ifPresent(b -> segment(hub, b.position()));
                    c.portOf(PortKind.BRANCH2, 0).ifPresent(b -> segment(hub, b.position()));
                }
            }
            case OLET -> centre.ifPresent(hub ->
                    c.portOf(PortKind.BRANCH1, 0).ifPresent(b -> segment(hub, b.position())));
            case WELD, FLOW_ARROW, END_CONNECTION_PIPELINE, END_POSITION_OPEN, GASKET, BOLT, UNKNOWN -> {
                // 중심선을 만들지 않는 컴포넌트
            }
            default -> {
                if (ends.size() >= 2) segment(ends.get(0).position(), ends.get(1).position());
            }
        }
    }

    /**
     * 한 구간을 적어 둔다. 사선이면 롤링 오프셋 삼각형을 함께 표기한다.
     *
     * <p>선은 여기서 바로 만들지 않는다 — 어느 배관이 앞인지는 <b>전부 모아 봐야</b> 알 수 있다.
     */
    private void segment(Vec3 a, Vec3 b) {
        centerlines.add(new CrossingBreaker.Segment(a, b));

        if (style.display().skewTriangles() && AxisClassifier.classify(b.minus(a)).isSkew()) {
            drawSkewTriangle(a, b);
        }
    }

    /**
     * 모아 둔 구간을 선으로 만든다. 화면에서 겹치는 곳은 뒤쪽을 끊는다 —
     * 안 끊으면 떨어져 있는 두 배관이 만나는 것처럼 보인다.
     */
    private void emitCenterlines() {
        double gap = style.display().crossingBreaks() ? symbolUnit * 0.6 : 0;
        for (CrossingBreaker.Piece piece
                : CrossingBreaker.breakAtCrossings(centerlines, projection, gap)) {
            CrossingBreaker.Segment s = centerlines.get(piece.segmentIndex());
            double[] p = projection.project(pointAt(s, piece.t0()));
            double[] q = projection.project(pointAt(s, piece.t1()));
            elements.add(new Scene2D.Line(id("cl"), Layers.CENTERLINE, Styles.CENTERLINE,
                    p[0], p[1], q[0], q[1]));
        }
    }

    private static Vec3 pointAt(CrossingBreaker.Segment s, double t) {
        return s.a().plus(s.b().minus(s.a()).scale(t));
    }

    /**
     * 사선 구간의 롤링 오프셋 표기.
     * 사선을 두 축 방향 성분으로 분해해 직각삼각형을 만들고 빗금을 넣는다 —
     * 도면을 읽는 사람이 어느 평면에서 꺾였는지 알 수 있게 하는 표준 관례다.
     */
    private void drawSkewTriangle(Vec3 a, Vec3 b) {
        Vec3 d = b.minus(a);
        double[] comp = {d.x(), d.y(), d.z()};
        // 성분이 큰 두 축으로 삼각형을 만든다 (평면 사선이면 나머지 하나는 0 에 가깝다)
        int i0 = 0, i1 = 1;
        if (Math.abs(comp[2]) > Math.abs(comp[i0])) i0 = 2;
        i1 = (i0 == 0) ? 1 : 0;
        for (int k = 0; k < 3; k++) {
            if (k != i0 && Math.abs(comp[k]) > Math.abs(comp[i1])) i1 = k;
        }
        Vec3 leg = a.plus(axisVector(i0, comp[i0]));

        double[] pa = projection.project(a);
        double[] pl = projection.project(leg);
        double[] pb = projection.project(b);

        elements.add(new Scene2D.Line(id("sk"), Layers.SKEW, Styles.HIDDEN, pa[0], pa[1], pl[0], pl[1]));
        elements.add(new Scene2D.Line(id("sk"), Layers.SKEW, Styles.HIDDEN, pl[0], pl[1], pb[0], pb[1]));

        // 빗변을 따라 짧은 빗금을 넣는다.
        // 삼각형을 가로지르는 긴 현을 그으면 도면 본체보다 빗금이 더 눈에 띈다 —
        // 관례대로 빗변 근처의 짧은 스트로크로만 표시한다
        double hatchLen = symbolUnit * 1.2;
        for (int k = 1; k <= HATCH_LINES; k++) {
            double t = (double) k / (HATCH_LINES + 1);
            double[] onHyp = {pa[0] + (pb[0] - pa[0]) * t, pa[1] + (pb[1] - pa[1]) * t};
            double[] onLeg = {pa[0] + (pl[0] - pa[0]) * t, pa[1] + (pl[1] - pa[1]) * t};
            double dx = onLeg[0] - onHyp[0], dy = onLeg[1] - onHyp[1];
            double len = Math.hypot(dx, dy);
            double f = len < 1e-9 ? 0 : Math.min(1.0, hatchLen / len);
            elements.add(new Scene2D.Line(id("sk"), Layers.SKEW, Styles.HIDDEN,
                    onHyp[0], onHyp[1], onHyp[0] + dx * f, onHyp[1] + dy * f));
        }
    }

    private static Vec3 axisVector(int axis, double value) {
        return switch (axis) {
            case 0 -> new Vec3(value, 0, 0);
            case 1 -> new Vec3(0, value, 0);
            default -> new Vec3(0, 0, value);
        };
    }

    // ─────────────────────────── 심볼 ───────────────────────────

    /** 컴포넌트의 SKEY 를 해석해 심볼을 배치한다 */
    private void drawSymbol(PipingComponent c, Pipeline pipeline) {
        // 자재 집계에만 쓰거나(가스켓/볼트) 표시만 하는(종단) 컴포넌트는 도면 형상이 없다.
        // skey-table 의 notRendered 목록이 그 판단의 단일 출처다
        if (skeyTable.symbolSet().notRenderedTypes().contains(c.rawKeyword().toUpperCase())) return;

        SkeyTable.Resolution res = skeyTable.resolve(c.skey(), c.rawKeyword());
        if (!res.found()) {
            // 절차적으로 그리는 컴포넌트(파이프/엘보/티)는 심볼이 없는 것이 정상이다
            if (!isProcedural(c.type())) {
                diag.warn(DiagnosticCodes.SKEY_UNRESOLVED, 0,
                        "component", c.label(), "skey", c.skey());
            }
            return;
        }
        // 절차적 컴포넌트는 중심선으로 이미 표현했다 — 본체 심볼을 겹쳐 그리지 않는다
        if (isProcedural(c.type())) return;

        Placement pl = placementOf(c, pipeline);
        if (pl == null) return;

        double[] affine = projection.symbolAffine(pl.direction, projection.planeUp(pl.direction, null),
                symbolUnit, pl.origin);
        elements.addAll(symbols.place(res.entry().shape(), affine, symbolUnit,
                Layers.SYMBOL, id("sym")));
        if (res.entry().overlay() != null) {
            elements.addAll(symbols.place(res.entry().overlay(), affine, symbolUnit,
                    Layers.SYMBOL, id("ovl")));
        }
    }

    private record Placement(Vec3 origin, Vec3 direction) {
    }

    /** 심볼을 놓을 위치와 방향을 정한다 */
    private Placement placementOf(PipingComponent c, Pipeline pipeline) {
        List<Port> ends = c.ports().stream().filter(p -> p.kind() == PortKind.END).toList();

        if (c.type() == ComponentType.OLET) {
            Vec3 hub = c.centre().orElse(null);
            Port b = c.portOf(PortKind.BRANCH1, 0).orElse(null);
            if (hub == null || b == null) return null;
            return new Placement(hub, b.position().minus(hub));
        }
        if (c.type() == ComponentType.WELD) {
            if (ends.isEmpty()) return null;
            Vec3 at = ends.get(0).position();
            Vec3 dir = directionAt(at, pipeline);
            return dir == null ? null : new Placement(at, dir);
        }
        if (c.type() == ComponentType.FLOW_ARROW) {
            Port co = c.portOf(PortKind.COORD, 0).orElse(null);
            if (co == null) return null;
            Vec3 dir = directionAt(co.position(), pipeline);
            return dir == null ? null : new Placement(co.position(), dir);
        }
        if (ends.size() >= 2) {
            Vec3 a = ends.get(0).position(), b = ends.get(1).position();
            Vec3 d = b.minus(a);
            // 길이 0 인 본체(플랜지 쌍 등)는 방향을 알 수 없으니 주변 배관에서 가져온다
            if (d.length() < 1e-6) d = directionAt(a, pipeline);
            return d == null ? null : new Placement(midpoint(a, b), d);
        }
        if (ends.size() == 1) {
            Vec3 at = ends.get(0).position();
            Vec3 dir = directionAt(at, pipeline);
            return dir == null ? null : new Placement(at, dir);
        }
        return null;
    }

    /** 해당 위치를 지나거나 끝점으로 갖는 배관의 방향을 찾는다 */
    private Vec3 directionAt(Vec3 at, Pipeline pipeline) {
        Vec3 best = null;
        double bestDist = Double.MAX_VALUE;
        for (PipingComponent c : pipeline.components()) {
            if (c.type() != ComponentType.PIPE) continue;
            List<Port> ends = c.ports().stream().filter(p -> p.kind() == PortKind.END).toList();
            if (ends.size() < 2) continue;
            Vec3 a = ends.get(0).position(), b = ends.get(1).position();
            double d = Math.min(a.distanceTo(at), b.distanceTo(at));
            if (d < bestDist) {
                bestDist = d;
                best = b.minus(a);
            }
        }
        return best;
    }

    private static boolean isProcedural(ComponentType t) {
        return switch (t) {
            case PIPE, ELBOW, BEND, TEE, CROSS -> true;
            default -> false;
        };
    }

    // ─────────────────────────── 유틸 ───────────────────────────

    private static Vec3 midpoint(Vec3 a, Vec3 b) {
        return a.plus(b).scale(0.5);
    }

    private String id(String prefix) {
        return prefix + (seq++);
    }

    /** 심볼 크기를 정하기 위해 포트만으로 투영 범위를 미리 잰다 */
    private double[] projectedBounds(List<PipingComponent> components) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        boolean any = false;
        for (PipingComponent c : components) {
            for (Port p : c.ports()) {
                double[] q = projection.project(p.position());
                minX = Math.min(minX, q[0]);
                minY = Math.min(minY, q[1]);
                maxX = Math.max(maxX, q[0]);
                maxY = Math.max(maxY, q[1]);
                any = true;
            }
        }
        return any ? new double[]{minX, minY, maxX, maxY} : new double[]{0, 0, 0, 0};
    }

    /** 실제로 만들어진 요소들의 경계 상자 */
    private static double[] boundsOf(List<Scene2D.Element> els) {
        double[] b = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        boolean any = false;
        for (Scene2D.Element e : els) {
            for (double[] p : pointsOf(e)) {
                b[0] = Math.min(b[0], p[0]);
                b[1] = Math.min(b[1], p[1]);
                b[2] = Math.max(b[2], p[0]);
                b[3] = Math.max(b[3], p[1]);
                any = true;
            }
        }
        return any ? b : new double[]{0, 0, 0, 0};
    }

    /** 경계 계산용 대표 점들 (호/타원은 반경까지 감싼다) */
    private static List<double[]> pointsOf(Scene2D.Element e) {
        return switch (e) {
            case Scene2D.Line l -> List.of(new double[]{l.x1(), l.y1()}, new double[]{l.x2(), l.y2()});
            case Scene2D.Polyline p -> p.points().stream().map(q -> new double[]{q.x(), q.y()}).toList();
            case Scene2D.Polygon p -> p.points().stream().map(q -> new double[]{q.x(), q.y()}).toList();
            case Scene2D.Circle c -> List.of(new double[]{c.cx() - c.r(), c.cy() - c.r()},
                    new double[]{c.cx() + c.r(), c.cy() + c.r()});
            case Scene2D.Ellipse el -> {
                double m = Math.max(el.rx(), el.ry());
                yield List.of(new double[]{el.cx() - m, el.cy() - m}, new double[]{el.cx() + m, el.cy() + m});
            }
            case Scene2D.Arc a -> {
                double m = Math.max(a.rx() == null ? a.r() : a.rx(), a.ry() == null ? a.r() : a.ry());
                yield List.of(new double[]{a.cx() - m, a.cy() - m}, new double[]{a.cx() + m, a.cy() + m});
            }
            // 문자는 앵커 점만 잡으면 도면 경계 밖으로 삐져나가 잘린다 — 추정 폭까지 감싼다
            case Scene2D.Text t -> {
                double h = t.height() == null ? 0 : t.height();
                double w = LabelPlacer.estimateTextWidth(t.content(), h);
                double half = "start".equals(t.anchor()) ? 0 : ("end".equals(t.anchor()) ? w : w / 2);
                yield List.of(new double[]{t.x() - half, t.y() - h / 2},
                        new double[]{t.x() - half + w, t.y() + h / 2});
            }
        };
    }

    /** 기본 스타일 — 색·굵기는 나중에 도면 스타일 설정이 덮어쓴다 */
    private static List<Scene2D.Style> defaultStyles() {
        return List.of(
                new Scene2D.Style(Styles.CENTERLINE,
                        new Scene2D.Stroke("#111111", 1.6, null), null, null),
                new Scene2D.Style(Styles.OUTLINE,
                        new Scene2D.Stroke("#111111", 1.0, null), null, null),
                new Scene2D.Style(Styles.SOLID,
                        new Scene2D.Stroke("#111111", 1.0, null), new Scene2D.Fill("#111111"), null),
                new Scene2D.Style(Styles.HIDDEN,
                        new Scene2D.Stroke("#666666", 0.8, new double[]{4, 3}), null, null),
                new Scene2D.Style(Styles.DIMENSION,
                        new Scene2D.Stroke("#444444", 0.7, null), null, null),
                new Scene2D.Style(Styles.TEXT, null, null,
                        new Scene2D.Font("sans-serif", null, "center")));
    }
}
