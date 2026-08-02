// DimensionDrawer.java — 러닝 치수를 치수선·연장선·틱·문자로 작도한다
package co.atools.isoflow.engine.layout;

import co.atools.isoflow.engine.geometry.IsoProjection;
import co.atools.isoflow.engine.model.Port;
import co.atools.isoflow.engine.model.Vec3;
import co.atools.isoflow.engine.scene.Scene2D;
import co.atools.isoflow.engine.style.IsoStyle;

import java.util.ArrayList;
import java.util.List;

/**
 * 치수선은 배관에서 직각으로 떨어진 자리에 놓는다.
 * 도면 안쪽으로 뻗으면 다른 배관과 겹치므로 <b>도면 중심에서 먼 쪽</b>을 고르고,
 * 그래도 겹치면 한 단계씩 더 밀어낸다.
 */
public final class DimensionDrawer {

    /** 치수선을 배관에서 띄우는 기본 거리 (symbolUnit 배수) */
    private static final double BASE_OFFSET = 3.5;
    /** 겹칠 때 추가로 밀어내는 간격 */
    private static final double STEP_OFFSET = 2.6;
    /** 밀어내기 최대 시도 횟수 */
    private static final int MAX_ATTEMPTS = 5;
    /** 연장선이 배관을 넘어 더 나가는 길이 */
    private static final double EXT_OVERSHOOT = 0.8;
    /** 치수 문자 높이 (symbolUnit 배수) */
    private static final double TEXT_HEIGHT = 1.1;
    /** 틱 마크 길이 */
    private static final double TICK = 0.7;

    private final IsoProjection projection;
    private final double symbolUnit;
    private final LabelPlacer placer;
    private final String layerId;
    private final String dimStyle;
    private final String textStyle;
    private final double[] drawingCentre;
    private final IsoStyle.Dimensions cfg;

    private int seq;

    public DimensionDrawer(IsoProjection projection, double symbolUnit, LabelPlacer placer,
                           String layerId, String dimStyle, String textStyle, double[] drawingCentre,
                           IsoStyle style) {
        this.projection = projection;
        this.symbolUnit = symbolUnit;
        this.placer = placer;
        this.layerId = layerId;
        this.dimStyle = dimStyle;
        this.textStyle = textStyle;
        this.drawingCentre = drawingCentre;
        this.cfg = style.withDefaults().dimensions();
    }

    /** 런 하나를 작도한다. 점이 2개 미만이면 아무것도 그리지 않는다 */
    public List<Scene2D.Element> draw(DimensionRun run) {
        List<Port> pts = run.points();
        if (pts.size() < 2) return List.of();

        double[] first = projection.project(pts.get(0).position());
        double[] last = projection.project(pts.get(pts.size() - 1).position());
        double dx = last[0] - first[0], dy = last[1] - first[1];
        double len = Math.hypot(dx, dy);
        if (len < 1e-6) return List.of();

        // 배관 방향에 직각인 두 후보 중 도면 중심에서 먼 쪽을 고른다
        double[] normal = {-dy / len, dx / len};
        double midX = (first[0] + last[0]) / 2, midY = (first[1] + last[1]) / 2;
        if ((midX - drawingCentre[0]) * normal[0] + (midY - drawingCentre[1]) * normal[1] < 0) {
            normal[0] = -normal[0];
            normal[1] = -normal[1];
        }

        double textHeight = symbolUnit * cfg.textHeightUnits();

        // 라벨이 겹치면 치수선을 한 단계씩 더 밀어낸다
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            double offset = symbolUnit * (cfg.offsetUnits() + attempt * cfg.stepUnits());
            List<LabelPlacer.Box> boxes = labelBoxes(run, normal, offset, textHeight);
            // 한 칸이라도 겹치면 치수선 전체를 밀어낸다 — 칸마다 따로 밀면 치수선이 계단처럼 어긋난다
            boolean free = boxes.stream().allMatch(placer::isFree);
            if (free || attempt == MAX_ATTEMPTS - 1) {
                for (LabelPlacer.Box b : boxes) placer.placeLabel(b, !free);
                return render(run, normal, offset, textHeight);
            }
        }
        return List.of();
    }

    /** 각 칸의 치수 문자가 차지할 상자 */
    private List<LabelPlacer.Box> labelBoxes(DimensionRun run, double[] n, double offset, double textHeight) {
        List<LabelPlacer.Box> out = new ArrayList<>();
        List<Port> pts = run.points();
        for (int i = 0; i + 1 < pts.size(); i++) {
            double[] a = offsetPoint(pts.get(i).position(), n, offset);
            double[] b = offsetPoint(pts.get(i + 1).position(), n, offset);
            String text = formatLength(run.trueLengths().get(i));
            out.add(LabelPlacer.Box.centred((a[0] + b[0]) / 2, (a[1] + b[1]) / 2,
                    LabelPlacer.estimateTextWidth(text, textHeight), textHeight));
        }
        return out;
    }

    private List<Scene2D.Element> render(DimensionRun run, double[] n, double offset, double textHeight) {
        List<Scene2D.Element> out = new ArrayList<>();
        List<Port> pts = run.points();

        // 연장선 — 배관 점에서 치수선까지, 조금 더 넘겨 그린다
        for (Port p : pts) {
            double[] base = projection.project(p.position());
            double[] tip = offsetPoint(p.position(), n, offset + symbolUnit * EXT_OVERSHOOT);
            out.add(new Scene2D.Line(id(), layerId, dimStyle, base[0], base[1], tip[0], tip[1]));
        }

        // 치수선 본체 + 칸마다 틱과 문자
        for (int i = 0; i + 1 < pts.size(); i++) {
            double[] a = offsetPoint(pts.get(i).position(), n, offset);
            double[] b = offsetPoint(pts.get(i + 1).position(), n, offset);
            out.add(new Scene2D.Line(id(), layerId, dimStyle, a[0], a[1], b[0], b[1]));
            out.add(tick(a, b));
            if (i + 2 == pts.size()) out.add(tick(b, a));

            String text = formatLength(run.trueLengths().get(i));
            double angle = readableAngle(Math.toDegrees(Math.atan2(b[1] - a[1], b[0] - a[0])));
            // 문자는 치수선 위에 살짝 띄운다
            double[] up = {n[0] * symbolUnit * 0.5, n[1] * symbolUnit * 0.5};
            out.add(new Scene2D.Text(id(), layerId, textStyle,
                    (a[0] + b[0]) / 2 + up[0], (a[1] + b[1]) / 2 + up[1],
                    text, angle, "middle", textHeight));
        }
        return out;
    }

    /** 치수선 끝의 사선 틱 (건축 관례) */
    private Scene2D.Line tick(double[] at, double[] towards) {
        double dx = towards[0] - at[0], dy = towards[1] - at[1];
        double len = Math.hypot(dx, dy);
        if (len < 1e-9) return new Scene2D.Line(id(), layerId, dimStyle, at[0], at[1], at[0], at[1]);
        double ux = dx / len, uy = dy / len;
        // 진행 방향을 45° 돌린 짧은 사선
        double sx = (ux - uy) * symbolUnit * TICK * 0.5;
        double sy = (uy + ux) * symbolUnit * TICK * 0.5;
        return new Scene2D.Line(id(), layerId, dimStyle, at[0] - sx, at[1] - sy, at[0] + sx, at[1] + sy);
    }

    private double[] offsetPoint(Vec3 world, double[] n, double offset) {
        double[] p = projection.project(world);
        return new double[]{p[0] + n[0] * offset, p[1] + n[1] * offset};
    }

    /** 문자가 거꾸로 서지 않도록 각도를 뒤집는다 */
    private static double readableAngle(double deg) {
        if (deg > 90) return deg - 180;
        if (deg < -90) return deg + 180;
        return deg;
    }

    /** 치수값은 mm 정수로 표기한다 — 실무 관례 */
    String formatLength(double mm) {
        int decimals = cfg.decimals();
        return decimals <= 0 ? String.valueOf(Math.round(mm))
                : String.format(java.util.Locale.ROOT, "%." + decimals + "f", mm);
    }

    private String id() {
        return "dim" + (seq++);
    }
}
