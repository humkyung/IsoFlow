// DimensionRun.java — 한 직선 구간에 붙는 러닝 치수. 길이는 **압축 전 실제 값**을 담는다
package co.atools.isoflow.engine.layout;

import co.atools.isoflow.engine.geometry.Axis6;
import co.atools.isoflow.engine.model.Port;

import java.util.List;

/**
 * 등각도는 축척 도면이 아니므로 화면상 길이와 치수값이 다르다.
 * 그래서 <b>점은 Port 참조로</b>(압축 후 위치를 따라감), <b>길이는 값으로</b>(압축 전에 확정) 들고 있는다.
 *
 * @param axis        구간의 방위
 * @param points      구간 위의 점들 (양 끝 + 중간 접합점). 순서대로 정렬되어 있다
 * @param trueLengths points 의 이웃 간 실제 길이(mm). 크기는 {@code points.size() - 1}
 */
public record DimensionRun(Axis6 axis, List<Port> points, List<Double> trueLengths) {

    /** 구간 전체의 실제 길이 */
    public double totalTrueLength() {
        return trueLengths.stream().mapToDouble(Double::doubleValue).sum();
    }

    /** 중간점이 있어 러닝 치수(여러 칸)로 그려야 하는지 */
    public boolean isRunning() {
        return points.size() > 2;
    }
}
