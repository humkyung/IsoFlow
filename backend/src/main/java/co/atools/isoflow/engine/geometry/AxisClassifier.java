// AxisClassifier.java — 방향 벡터를 6방위로 분류한다. 허용각을 벗어나면 SKEW
package co.atools.isoflow.engine.geometry;

import co.atools.isoflow.engine.model.Vec3;

public final class AxisClassifier {

    /**
     * 축으로 인정할 최대 벗어남(도).
     * PCF 좌표에는 mm 이하 오차가 있어 완전 정렬을 요구하면 멀쩡한 직선도 SKEW 로 잡힌다.
     * 0.5° 는 10m 구간에서 약 87mm 어긋남에 해당하므로 실제 사선과는 충분히 구분된다.
     */
    public static final double DEFAULT_TOLERANCE_DEG = 0.5;

    private final double cosTolerance;

    public AxisClassifier(double toleranceDeg) {
        this.cosTolerance = Math.cos(Math.toRadians(toleranceDeg));
    }

    public static Axis6 classify(Vec3 direction) {
        return new AxisClassifier(DEFAULT_TOLERANCE_DEG).of(direction);
    }

    /** 방향 벡터를 6방위 중 하나로 분류한다. 길이가 0 이면 SKEW */
    public Axis6 of(Vec3 direction) {
        Vec3 u = direction.normalized();
        if (u.length() < 0.5) return Axis6.SKEW;   // 영벡터

        Axis6 best = Axis6.SKEW;
        double bestDot = cosTolerance;
        for (Axis6 a : Axis6.values()) {
            if (a.isSkew()) continue;
            double d = u.dot(a.direction());
            if (d >= bestDot) {
                bestDot = d;
                best = a;
            }
        }
        return best;
    }

    /** 두 점을 잇는 구간의 방위 */
    public Axis6 of(Vec3 from, Vec3 to) {
        return of(to.minus(from));
    }
}
