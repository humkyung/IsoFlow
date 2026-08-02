// DxfWriter.java — Scene2D 를 DXF R12(AC1009) ASCII 로 쓴다. Java 에 쓸 만한 DXF 생성 라이브러리가 없어 직접 만든다
package co.atools.isoflow.export.dxf;

import co.atools.isoflow.engine.scene.Scene2D;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * R12 를 고른 이유는 섹션 구조가 가장 단순하고 호환성이 넓기 때문이다.
 * 대신 R12 에는 {@code ELLIPSE} / {@code LWPOLYLINE} / {@code MTEXT} 가 없어
 * 타원·타원호는 폴리라인으로 근사한다.
 *
 * <p>치수는 {@code DIMENSION} 엔티티 대신 선·문자로 분해(explode)해서 내보낸다 —
 * 등각도 치수는 어차피 자동 계산 결과이고, 뷰어별 치수 렌더링 편차를 피할 수 있다.
 */
public final class DxfWriter {

    /** 타원/타원호를 폴리라인으로 쪼갤 때 라디안당 분할 수 */
    private static final int SEGMENTS_PER_RAD = 8;
    private static final int MIN_SEGMENTS = 12;

    /** 레이어별 DXF 색 번호 (7=기본 전경, 8=회색) */
    private static final Map<String, Integer> LAYER_COLORS = Map.of(
            "centerline", 7,
            "symbol", 7,
            "skew", 8,
            "dimension", 8,
            "text", 7,
            "detail", 8,
            "frame", 7);

    private final StringBuilder out = new StringBuilder(1 << 16);

    private DxfWriter() {
    }

    public static byte[] write(Scene2D scene) {
        DxfWriter w = new DxfWriter();
        w.build(scene);
        // R12 는 ANSI 기반이다. 우리 도면 문자는 치수·태그라 ASCII 범위이므로 그대로 쓴다
        return w.out.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private void build(Scene2D scene) {
        header(scene);
        tables(scene);
        entities(scene);
        pair(0, "EOF");
    }

    // ─────────────────────────── 섹션 ───────────────────────────

    private void header(Scene2D scene) {
        pair(0, "SECTION");
        pair(2, "HEADER");
        pair(9, "$ACADVER");
        pair(1, "AC1009");
        pair(9, "$INSBASE");
        point(10, 20, 30, 0, 0);
        pair(9, "$EXTMIN");
        point(10, 20, 30, scene.bounds()[0], scene.bounds()[1]);
        pair(9, "$EXTMAX");
        point(10, 20, 30, scene.bounds()[2], scene.bounds()[3]);
        pair(0, "ENDSEC");
    }

    private void tables(Scene2D scene) {
        List<String> layers = new ArrayList<>();
        for (Scene2D.Layer l : scene.layers()) layers.add(l.id());
        if (layers.isEmpty()) layers.add("0");

        pair(0, "SECTION");
        pair(2, "TABLES");
        pair(0, "TABLE");
        pair(2, "LAYER");
        pair(70, layers.size());
        for (String name : layers) {
            pair(0, "LAYER");
            pair(2, name);
            pair(70, 0);
            pair(62, LAYER_COLORS.getOrDefault(name, 7));
            pair(6, "CONTINUOUS");
        }
        pair(0, "ENDTAB");
        pair(0, "ENDSEC");
    }

    private void entities(Scene2D scene) {
        Map<String, Scene2D.Style> styles = new HashMap<>();
        for (Scene2D.Style s : scene.styles()) styles.put(s.id(), s);

        pair(0, "SECTION");
        pair(2, "ENTITIES");
        for (Scene2D.Element e : scene.elements()) {
            String layer = e.layerId() == null ? "0" : e.layerId();
            Scene2D.Style style = styles.get(e.styleRef());
            boolean filled = style != null && style.fill() != null && style.fill().color() != null;
            emit(e, layer, filled);
        }
        pair(0, "ENDSEC");
    }

    // ─────────────────────────── 엔티티 ───────────────────────────

    private void emit(Scene2D.Element e, String layer, boolean filled) {
        switch (e) {
            case Scene2D.Line l -> line(layer, l.x1(), l.y1(), l.x2(), l.y2());
            case Scene2D.Polyline p -> polyline(layer, toXy(p.points()), false);
            case Scene2D.Polygon p -> {
                List<double[]> pts = toXy(p.points());
                if (filled) solid(layer, pts);
                polyline(layer, pts, true);
            }
            case Scene2D.Circle c -> {
                circle(layer, c.cx(), c.cy(), c.r());
                // 채워진 작은 원(용접점)은 R12 에 HATCH 가 없어 윤곽으로만 표현한다
            }
            case Scene2D.Ellipse el -> polyline(layer,
                    sampleEllipse(el.cx(), el.cy(), el.rx(), el.ry(),
                            Math.toRadians(el.rotation() == null ? 0 : el.rotation()),
                            0, Math.PI * 2), true);
            case Scene2D.Arc a -> {
                double rx = a.rx() == null ? a.r() : a.rx();
                double ry = a.ry() == null ? a.r() : a.ry();
                double rot = Math.toRadians(a.rotation() == null ? 0 : a.rotation());
                if (Math.abs(rx - ry) < 1e-9 && Math.abs(rot) < 1e-9) {
                    // 진짜 원호는 ARC 로 — 벡터 정보를 잃지 않는다
                    arc(layer, a.cx(), a.cy(), rx,
                            Math.toDegrees(a.startAngle()), Math.toDegrees(a.endAngle()));
                } else {
                    polyline(layer,
                            sampleEllipse(a.cx(), a.cy(), rx, ry, rot, a.startAngle(), a.endAngle()),
                            false);
                }
            }
            case Scene2D.Text t -> text(layer, t);
        }
    }

    private void line(String layer, double x1, double y1, double x2, double y2) {
        pair(0, "LINE");
        pair(8, layer);
        point(10, 20, 30, x1, y1);
        point(11, 21, 31, x2, y2);
    }

    private void circle(String layer, double cx, double cy, double r) {
        pair(0, "CIRCLE");
        pair(8, layer);
        point(10, 20, 30, cx, cy);
        pair(40, r);
    }

    /** DXF ARC 는 항상 반시계 방향으로 start→end 를 그린다 */
    private void arc(String layer, double cx, double cy, double r, double startDeg, double endDeg) {
        pair(0, "ARC");
        pair(8, layer);
        point(10, 20, 30, cx, cy);
        pair(40, r);
        pair(50, normalizeDeg(startDeg));
        pair(51, normalizeDeg(endDeg));
    }

    /** R12 는 LWPOLYLINE 이 없어 POLYLINE + VERTEX + SEQEND 를 쓴다 */
    private void polyline(String layer, List<double[]> pts, boolean closed) {
        if (pts.size() < 2) return;
        pair(0, "POLYLINE");
        pair(8, layer);
        pair(66, 1);              // vertices follow
        pair(70, closed ? 1 : 0);
        for (double[] p : pts) {
            pair(0, "VERTEX");
            pair(8, layer);
            point(10, 20, 30, p[0], p[1]);
        }
        pair(0, "SEQEND");
        pair(8, layer);
    }

    /**
     * 채움. R12 의 SOLID 는 정점 순서가 1-2-4-3 (나비 순서)이다 —
     * 1-2-3-4 로 넣으면 도형이 꼬여 모래시계 모양이 된다.
     */
    private void solid(String layer, List<double[]> pts) {
        if (pts.size() < 3) return;
        double[] p1 = pts.get(0), p2 = pts.get(1), p3 = pts.get(2);
        double[] p4 = pts.size() >= 4 ? pts.get(3) : p3;

        pair(0, "SOLID");
        pair(8, layer);
        point(10, 20, 30, p1[0], p1[1]);
        point(11, 21, 31, p2[0], p2[1]);
        point(12, 22, 32, p4[0], p4[1]);   // 나비 순서
        point(13, 23, 33, p3[0], p3[1]);
    }

    private void text(String layer, Scene2D.Text t) {
        int justify = switch (t.anchor() == null ? "start" : t.anchor()) {
            case "middle" -> 1;
            case "end" -> 2;
            default -> 0;
        };
        pair(0, "TEXT");
        pair(8, layer);
        point(10, 20, 30, t.x(), t.y());
        pair(40, t.height() == null ? 2.5 : t.height());
        pair(1, ascii(t.content()));
        if (t.rotation() != null && t.rotation() != 0) pair(50, t.rotation());
        if (justify != 0) {
            pair(72, justify);
            // 72 가 0 이 아니면 정렬 기준점(11/21)을 반드시 함께 줘야 한다
            point(11, 21, 31, t.x(), t.y());
        }
    }

    // ─────────────────────────── 유틸 ───────────────────────────

    /** 타원/타원호를 점열로 근사한다 (R12 에는 ELLIPSE 엔티티가 없다) */
    static List<double[]> sampleEllipse(double cx, double cy, double rx, double ry,
                                        double rotation, double start, double end) {
        double sweep = Math.abs(end - start);
        int n = Math.max(MIN_SEGMENTS, (int) Math.ceil(sweep * SEGMENTS_PER_RAD));
        double cos = Math.cos(rotation), sin = Math.sin(rotation);
        List<double[]> pts = new ArrayList<>(n + 1);
        for (int i = 0; i <= n; i++) {
            double t = start + (end - start) * i / n;
            double ex = rx * Math.cos(t), ey = ry * Math.sin(t);
            pts.add(new double[]{cx + ex * cos - ey * sin, cy + ex * sin + ey * cos});
        }
        return pts;
    }

    private static List<double[]> toXy(List<Scene2D.Point> pts) {
        List<double[]> out = new ArrayList<>(pts.size());
        for (Scene2D.Point p : pts) out.add(new double[]{p.x(), p.y()});
        return out;
    }

    private static double normalizeDeg(double deg) {
        double d = deg % 360;
        return d < 0 ? d + 360 : d;
    }

    /** R12 는 ANSI 기반이라 비ASCII 문자는 물음표로 깨진다 — 미리 걸러 알아보기 쉽게 바꾼다 */
    private static String ascii(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) sb.append(c < 128 ? c : '?');
        return sb.toString();
    }

    private void pair(int code, String value) {
        out.append(code).append("\r\n").append(value).append("\r\n");
    }

    private void pair(int code, int value) {
        pair(code, String.valueOf(value));
    }

    private void pair(int code, double value) {
        out.append(code).append("\r\n")
                .append(String.format(Locale.ROOT, "%.4f", value)).append("\r\n");
    }

    private void point(int codeX, int codeY, int codeZ, double x, double y) {
        pair(codeX, x);
        pair(codeY, y);
        pair(codeZ, 0.0);
    }
}
