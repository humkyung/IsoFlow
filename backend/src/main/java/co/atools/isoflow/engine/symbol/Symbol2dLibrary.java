// Symbol2dLibrary.java — 심볼을 등각 평면에 배치해 Scene2D 요소로 굽는다
package co.atools.isoflow.engine.symbol;

import co.atools.isoflow.engine.geometry.IsoProjection;
import co.atools.isoflow.engine.scene.Scene2D;

import java.util.ArrayList;
import java.util.List;

/**
 * Verso 렌더러는 임의 affine 을 적용하지 않는다(회전조차 기하에 구워 넣는다).
 * 따라서 <b>엔진이 직접 변환해서</b> 내보내야 한다.
 *
 * <p>굽기 규칙(docs/symbol-set.md 3장):
 * <ul>
 *   <li>line/polyline/polygon → 각 점을 affine 변환</li>
 *   <li>rect → polygon 으로 바꾼 뒤 각 점 변환</li>
 *   <li>circle(iso) → ellipse — 2×2 행렬 SVD 로 rx/ry/rotation</li>
 *   <li>arc(iso) → 타원호 — 같은 SVD, 각도는 {@code t' = t - theta} 로 정확히 재매개화</li>
 *   <li>circle/arc(screen) → 중심만 변환, 반지름·각도 유지</li>
 *   <li>text → 앵커 점만 변환, 화면 수평 유지</li>
 * </ul>
 */
public final class Symbol2dLibrary {

    private final SymbolSet set;

    public Symbol2dLibrary(SymbolSet set) {
        this.set = set;
    }

    public static Symbol2dLibrary standard() {
        return new Symbol2dLibrary(SymbolSet.standard());
    }

    /**
     * 형상을 배치해 Scene2D 요소 목록을 만든다.
     *
     * @param shapeName symbols-2d.json 의 형상 이름
     * @param affine    {@link IsoProjection#symbolAffine} 결과
     * @param scale     symbolUnit — plane=screen 요소의 크기 기준
     * @param layerId   레이어
     * @param idPrefix  요소 id 접두사
     */
    public List<Scene2D.Element> place(String shapeName, double[] affine, double scale,
                                       String layerId, String idPrefix) {
        SymbolShape shape = set.shape(shapeName);
        if (shape == null) return List.of();
        return bake(shape.elements(), affine, scale, layerId, idPrefix);
    }

    /** 포트 위치의 접합부 표기(용접점·플랜지 바 등)를 배치한다 */
    public List<Scene2D.Element> placeEndTreatment(String endTypeCode, double[] affine, double scale,
                                                   String layerId, String idPrefix) {
        SymbolShape t = set.endTreatment(endTypeCode);
        if (t == null) return List.of();
        return bake(t.elements(), affine, scale, layerId, idPrefix);
    }

    private List<Scene2D.Element> bake(List<SymbolElement> elements, double[] affine, double scale,
                                       String layerId, String idPrefix) {
        List<Scene2D.Element> out = new ArrayList<>();
        IsoProjection.Svd2 svd = IsoProjection.svd2(affine);
        int i = 0;

        for (SymbolElement e : elements) {
            String id = idPrefix + "-" + (i++);
            String style = styleOf(e.role());
            boolean screen = e.isScreenPlane();

            switch (e.type()) {
                case "line" -> {
                    double[] p1 = map(affine, scale, screen, e.x1(), e.y1());
                    double[] p2 = map(affine, scale, screen, e.x2(), e.y2());
                    out.add(new Scene2D.Line(id, layerId, style, p1[0], p1[1], p2[0], p2[1]));
                }
                case "polyline" -> out.add(new Scene2D.Polyline(id, layerId, style,
                        mapPoints(e.points(), affine, scale, screen)));
                case "polygon" -> out.add(new Scene2D.Polygon(id, layerId, style,
                        mapPoints(e.points(), affine, scale, screen)));
                case "rect" -> {
                    // 축정렬 사각형은 변환 후 유지되지 않는다 — polygon 으로 바꾼다
                    double x = e.x(), y = e.y(), w = e.w(), h = e.h();
                    List<double[]> corners = List.of(
                            new double[]{x, y}, new double[]{x + w, y},
                            new double[]{x + w, y + h}, new double[]{x, y + h});
                    out.add(new Scene2D.Polygon(id, layerId, style,
                            mapPoints(corners, affine, scale, screen)));
                }
                case "circle" -> {
                    double[] c = map(affine, scale, screen, e.cx(), e.cy());
                    if (screen) {
                        out.add(new Scene2D.Circle(id, layerId, style, c[0], c[1], e.r() * scale));
                    } else {
                        out.add(new Scene2D.Ellipse(id, layerId, style, c[0], c[1],
                                svd.rx() * e.r(), svd.ry() * e.r(), Math.toDegrees(svd.phi())));
                    }
                }
                case "arc" -> {
                    double[] c = map(affine, scale, screen, e.cx(), e.cy());
                    if (screen) {
                        out.add(new Scene2D.Arc(id, layerId, style, c[0], c[1], e.r() * scale,
                                null, null, null, e.startAngle(), e.endAngle()));
                    } else {
                        // 원 파라미터 → 타원 파라미터 대응은 근사가 아니라 정확하다 (반사 포함, Svd2.param 참조)
                        out.add(new Scene2D.Arc(id, layerId, style, c[0], c[1], svd.rx() * e.r(),
                                svd.rx() * e.r(), svd.ry() * e.r(), Math.toDegrees(svd.phi()),
                                svd.param(e.startAngle()), svd.param(e.endAngle())));
                    }
                }
                case "text" -> {
                    double[] p = map(affine, scale, screen, e.x(), e.y());
                    out.add(new Scene2D.Text(id, layerId, style, p[0], p[1], e.content(),
                            0.0, anchorOf(e.anchor()), e.height() * scale));
                }
                default -> {
                    // 알 수 없는 타입은 건너뛰되 조용히 사라지지 않도록 호출측이 검증한다
                }
            }
        }
        return out;
    }

    /** plane=screen 이면 원점만 옮기고 크기는 화면 기준으로 유지한다 */
    private static double[] map(double[] affine, double scale, boolean screen, double x, double y) {
        if (screen) return new double[]{affine[4] + x * scale, affine[5] + y * scale};
        return IsoProjection.apply(affine, x, y);
    }

    private static List<Scene2D.Point> mapPoints(List<double[]> pts, double[] affine,
                                                 double scale, boolean screen) {
        List<Scene2D.Point> out = new ArrayList<>(pts.size());
        for (double[] p : pts) {
            double[] m = map(affine, scale, screen, p[0], p[1]);
            out.add(new Scene2D.Point(m[0], m[1]));
        }
        return out;
    }

    /** 심볼 role → Scene2D 공유 스타일 id */
    static String styleOf(String role) {
        return role == null ? Styles.OUTLINE : switch (role) {
            case "solid" -> Styles.SOLID;
            case "hidden" -> Styles.HIDDEN;
            case "text" -> Styles.TEXT;
            default -> Styles.OUTLINE;
        };
    }

    private static String anchorOf(String a) {
        return a == null ? "middle" : a;
    }

    /** 공유 스타일 id 상수 */
    public static final class Styles {
        public static final String OUTLINE = "s-outline";
        public static final String SOLID = "s-solid";
        public static final String HIDDEN = "s-hidden";
        public static final String TEXT = "s-text";
        public static final String CENTERLINE = "s-centerline";
        public static final String DIMENSION = "s-dimension";

        private Styles() {
        }
    }
}
