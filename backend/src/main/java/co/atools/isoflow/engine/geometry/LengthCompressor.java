// LengthCompressor.java — 등각도는 축척 도면이 아니다. 긴 구간을 눌러 도면에 담기게 만든다
package co.atools.isoflow.engine.geometry;

import co.atools.isoflow.engine.model.Pipeline;
import co.atools.isoflow.engine.model.PipingComponent;
import co.atools.isoflow.engine.model.Port;
import co.atools.isoflow.engine.model.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * 세계 축마다 <b>단조 증가하는 좌표 remap</b> 을 만들어 적용한다.
 * 단조라서 순서와 좌표 일치(=접합점)가 그대로 보존되고, 축 정렬도 깨지지 않는다.
 *
 * <p><b>사선 구간이 있는 축은 건드리지 않는다.</b> 축별로 다른 배율을 쓰면 사선의 방향이 바뀌기 때문이다.
 * 사선이 없는 라인(대부분)에서만 압축이 걸린다.
 */
public final class LengthCompressor {

    /** 이 길이(mm)를 넘는 구간부터 압축한다 */
    private final double maxGapMm;
    /** 좌표를 같은 값으로 볼 허용오차(mm) */
    private final double toleranceMm;

    public LengthCompressor(double maxGapMm, double toleranceMm) {
        this.maxGapMm = maxGapMm;
        this.toleranceMm = toleranceMm;
    }

    public static LengthCompressor defaults() {
        // 2m 를 넘는 구간부터 로그 압축. 배관 부품 크기(수백 mm)는 그대로 남는다
        return new LengthCompressor(2000, 1.0);
    }

    /** 스타일 설정으로 만든다 */
    public static LengthCompressor of(co.atools.isoflow.engine.style.IsoStyle style) {
        return new LengthCompressor(style.compression().maxGapMm(), 1.0);
    }

    /**
     * @param compressedAxes 실제로 압축이 걸린 축 (0=X, 1=Y, 2=Z)
     * @param skippedAxes    사선 때문에 건너뛴 축
     */
    public record Result(List<Integer> compressedAxes, List<Integer> skippedAxes) {
    }

    /** 파이프라인 좌표를 압축한다. 원본 길이가 필요한 치수 계산은 압축 전에 끝내야 한다 */
    public Result compress(Pipeline pipeline) {
        boolean[] hasSkew = skewAxes(pipeline);
        List<Integer> compressed = new ArrayList<>();
        List<Integer> skipped = new ArrayList<>();

        for (int axis = 0; axis < 3; axis++) {
            if (hasSkew[axis]) {
                skipped.add(axis);
                continue;
            }
            if (remapAxis(pipeline, axis)) compressed.add(axis);
        }
        return new Result(compressed, skipped);
    }

    /**
     * 사선 구간이 변화를 만드는 축을 표시한다 — 그 축은 압축하지 않는다.
     * 판정 대상은 <b>실제로 그려지는 중심선 구간</b>이다. END 두 개를 직접 이으면
     * 엘보가 언제나 대각선으로 잡혀 압축이 통째로 무력화된다.
     */
    private boolean[] skewAxes(Pipeline pipeline) {
        boolean[] out = new boolean[3];
        for (PipingComponent c : pipeline.components()) {
            for (CenterlineSegments.Segment s : CenterlineSegments.of(c)) {
                if (!AxisClassifier.classify(s.delta()).isSkew()) continue;
                Vec3 d = s.delta();
                if (Math.abs(d.x()) > toleranceMm) out[0] = true;
                if (Math.abs(d.y()) > toleranceMm) out[1] = true;
                if (Math.abs(d.z()) > toleranceMm) out[2] = true;
            }
        }
        return out;
    }

    /** 한 축의 좌표를 단조 remap 한다. 압축이 실제로 일어났으면 true */
    private boolean remapAxis(Pipeline pipeline, int axis) {
        TreeSet<Double> values = new TreeSet<>();
        for (PipingComponent c : pipeline.components()) {
            for (Port p : c.ports()) values.add(component(p.position(), axis));
        }
        if (values.size() < 2) return false;

        List<Double> src = new ArrayList<>(values);
        List<Double> dst = new ArrayList<>(src.size());
        dst.add(src.get(0));
        boolean changed = false;

        for (int i = 1; i < src.size(); i++) {
            double gap = src.get(i) - src.get(i - 1);
            double squeezed = squeeze(gap);
            if (squeezed < gap - 1e-9) changed = true;
            dst.add(dst.get(i - 1) + squeezed);
        }
        if (!changed) return false;

        for (PipingComponent c : pipeline.components()) {
            for (Port p : c.ports()) {
                double v = component(p.position(), axis);
                p.setPosition(withComponent(p.position(), axis, interpolate(src, dst, v)));
            }
        }
        return true;
    }

    /**
     * 긴 간격을 로그로 눌러 준다.
     * 자르지 않고 로그로 줄이므로 <b>긴 구간이 여전히 더 길게</b> 보인다 — 도면을 읽을 때 중요하다.
     */
    private double squeeze(double gap) {
        if (gap <= maxGapMm) return gap;
        return maxGapMm * (1 + Math.log(gap / maxGapMm));
    }

    /** src(정렬됨) → dst 구간 선형보간. src 에 있는 값이면 정확히 대응값을 돌려준다 */
    private static double interpolate(List<Double> src, List<Double> dst, double v) {
        int lo = 0, hi = src.size() - 1;
        while (lo < hi) {
            int mid = (lo + hi) / 2;
            if (src.get(mid) < v) lo = mid + 1;
            else hi = mid;
        }
        if (Math.abs(src.get(lo) - v) < 1e-9) return dst.get(lo);
        if (lo == 0) return dst.get(0);
        double t = (v - src.get(lo - 1)) / (src.get(lo) - src.get(lo - 1));
        return dst.get(lo - 1) + t * (dst.get(lo) - dst.get(lo - 1));
    }

    private static double component(Vec3 v, int axis) {
        return axis == 0 ? v.x() : axis == 1 ? v.y() : v.z();
    }

    private static Vec3 withComponent(Vec3 v, int axis, double value) {
        return switch (axis) {
            case 0 -> new Vec3(value, v.y(), v.z());
            case 1 -> new Vec3(v.x(), value, v.z());
            default -> new Vec3(v.x(), v.y(), value);
        };
    }
}
