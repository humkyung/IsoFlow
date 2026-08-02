// SymbolElement.java — 심볼 라이브러리의 요소 하나 (심볼 로컬 좌표). symbols-2d.json 의 element 와 1:1
package co.atools.isoflow.engine.symbol;

import java.util.List;

/**
 * @param type       Verso Element 타입 (line/polyline/polygon/rect/circle/ellipse/arc/text)
 * @param role       outline / solid / hidden / text — 공유 스타일로 매핑된다
 * @param plane      iso(등각 평면에 눕힌다) / screen(화면 기준 유지)
 * @param startAngle 라디안 (Verso 규약)
 */
public record SymbolElement(
        String type,
        String role,
        String plane,
        Double x1, Double y1, Double x2, Double y2,
        List<double[]> points,
        Double x, Double y, Double w, Double h,
        Double cx, Double cy, Double r,
        Double startAngle, Double endAngle,
        String content, Double height, String anchor) {

    public boolean isScreenPlane() {
        return "screen".equals(plane);
    }
}
