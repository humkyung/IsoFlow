// IsoProjection.java — 등각 투영과 심볼 배치 affine. render_symbol_sheet.py 의 참조 구현을 이식한 것
package co.atools.isoflow.engine.geometry;

import co.atools.isoflow.engine.model.Vec3;

/**
 * 세계 3D → 도면 2D 등각 투영.
 *
 * <p><b>심볼 배치의 핵심</b>: 심볼은 배관 축을 포함하는 3D 평면에 놓인 뒤 투영된다.
 * 평면 좌표에서 회전만 시키면 "평면도를 등각도에 붙여놓은 것"처럼 보인다.
 * 투영이 선형이므로 심볼 로컬 2D → 도면 2D 전체가 하나의 affine 으로 떨어진다.
 *
 * <p>규약 전문은 {@code docs/symbol-set.md} 3장 참조.
 */
public final class IsoProjection {

    /** 화면 y-up 기준 세계 축의 투영 방향 (기본 30°/30° 등각) */
    private final double[] axisE;
    private final double[] axisN;
    private final double[] axisU;
    /** 수직 런에서 심볼 평면의 up 으로 쓸 수평 기준축 */
    private final Vec3 verticalRunReference;
    /**
     * 보는 방향(관측자 쪽이 +). 투영의 영공간이라 화면에서는 보이지 않는 축이다 —
     * 어느 배관이 앞에 있는지 판정할 때만 쓴다.
     *
     * <p>영공간 자체는 투영 행렬에서 나오지만 <b>부호(어느 쪽이 관측자인가)는 행렬에 없다.</b>
     * 그래서 값으로 들고 있는다.
     */
    private final Vec3 viewDirection;

    public static final IsoProjection DEFAULT = new IsoProjection(
            new double[]{Math.cos(Math.toRadians(-30)), Math.sin(Math.toRadians(-30))},
            new double[]{Math.cos(Math.toRadians(210)), Math.sin(Math.toRadians(210))},
            new double[]{0, 1},
            // 화면에서 위로 읽히는 수평축(-N). +E 를 쓰면 액추에이터가 화면 아래로 향한다
            new Vec3(0, -1, 0),
            // 기본 30/30 등각의 관측 방향 — (+E,+N,+U) 팔분공간에서 원점을 본다
            new Vec3(1, 1, 1).normalized());

    public IsoProjection(double[] axisE, double[] axisN, double[] axisU, Vec3 verticalRunReference) {
        this(axisE, axisN, axisU, verticalRunReference, new Vec3(1, 1, 1).normalized());
    }

    public IsoProjection(double[] axisE, double[] axisN, double[] axisU,
                         Vec3 verticalRunReference, Vec3 viewDirection) {
        this.axisE = axisE;
        this.axisN = axisN;
        this.axisU = axisU;
        this.verticalRunReference = verticalRunReference;
        this.viewDirection = viewDirection.normalized();
    }

    /**
     * 관측자 쪽으로 얼마나 가까운지. <b>큰 쪽이 앞</b>이다.
     * 화면에서 두 배관이 겹칠 때 어느 쪽을 끊을지 정하는 데 쓴다.
     */
    public double depth(Vec3 v) {
        return v.dot(viewDirection);
    }

    public Vec3 viewDirection() {
        return viewDirection;
    }

    /** 세계 좌표/벡터를 도면 2D 로 투영한다 (화면 y-up) */
    public double[] project(Vec3 v) {
        return new double[]{
                v.x() * axisE[0] + v.y() * axisN[0] + v.z() * axisU[0],
                v.x() * axisE[1] + v.y() * axisN[1] + v.z() * axisU[1]};
    }

    /**
     * 배관 방향 {@code u} 에 대한 심볼 평면의 up 벡터를 고른다.
     *
     * <p>선택 순서(잘못 고르면 심볼이 누워 보인다):
     * <ol>
     *   <li>{@code spindle}(PCF SPINDLE-DIRECTION)이 있으면 u 에 직교화해서 쓴다 — 밸브 스템 방향이 곧 심볼 평면</li>
     *   <li>수평 런이면 가장 수직에 가까운 직교축 — 심볼 횡방향이 화면 수직이 된다</li>
     *   <li>수직 런이면 화면에서 위로 읽히는 수평 기준축</li>
     * </ol>
     */
    public Vec3 planeUp(Vec3 u, Vec3 spindle) {
        Vec3 dir = u.normalized();
        if (spindle != null) {
            Vec3 w = spindle.minus(dir.scale(spindle.dot(dir)));
            if (w.length() > 1e-6) return w.normalized();
        }
        double dz = dir.z();
        if (Math.abs(dz) < 0.999) {
            // Z - (Z·u)u  — 가장 수직에 가까운 직교축
            return new Vec3(-dz * dir.x(), -dz * dir.y(), 1 - dz * dir.z()).normalized();
        }
        return verticalRunReference;
    }

    /**
     * 심볼 로컬 좌표 → 도면 좌표 affine {@code [a,b,c,d,e,f]} (Verso Affine 규약, x' = a·x + c·y + e).
     *
     * @param u      배관 진행 방향
     * @param up     심볼 평면의 up ({@link #planeUp} 결과)
     * @param scale  symbolUnit (도면 단위)
     * @param origin 컴포넌트 중심의 세계 좌표
     */
    public double[] symbolAffine(Vec3 u, Vec3 up, double scale, Vec3 origin) {
        double[] ex = project(u.normalized());
        double[] ey = project(up.normalized());
        double[] o = project(origin);
        return new double[]{ex[0] * scale, ex[1] * scale, ey[0] * scale, ey[1] * scale, o[0], o[1]};
    }

    /** affine 을 점에 적용한다 */
    public static double[] apply(double[] a, double x, double y) {
        return new double[]{a[0] * x + a[2] * y + a[4], a[1] * x + a[3] * y + a[5]};
    }

    /**
     * 2×2 행렬 {@code M=[[a,c],[b,d]]} 의 특이값분해 결과.
     * {@code M = R(phi)·diag(sx,sy)·R(theta)} 이므로 단위원의 상은
     * 반지름 (sx, sy) 를 phi 만큼 돌린 타원이고, 원 파라미터 t 는 타원 파라미터 {@code t + theta} 로 대응된다.
     *
     * <p><b>반사 주의</b>: 행렬식이 음수면 두 번째 특이값이 음수로 나온다.
     * 이를 {@code abs} 로 지우면 <b>거울상이 사라져</b> 호의 방향이 뒤집힌다.
     * 대신 {@code ry = |sy|} 로 두고 매개변수 부호를 뒤집어 흡수한다 —
     * {@code (sx·cos u, -|sy|·sin u) == (sx·cos(-u), |sy|·sin(-u))} 이기 때문이다.
     * 그 처리를 {@link #param(double)} 에 모아 두었다.
     */
    public record Svd2(double sx, double sy, double phi, double theta) {

        /** 타원 반지름 (항상 양수) */
        public double rx() {
            return Math.abs(sx);
        }

        public double ry() {
            return Math.abs(sy);
        }

        /** 반사가 포함되어 매개변수를 뒤집어야 하는지 */
        public boolean mirrored() {
            return sy < 0;
        }

        /** 원 파라미터 t → 타원 파라미터 (반사가 있으면 부호를 뒤집어 흡수한다) */
        public double param(double t) {
            double u = t + theta;
            return mirrored() ? -u : u;
        }
    }

    /** affine 의 선형부(2×2)를 SVD 분해한다 — circle→ellipse, arc→타원호 변환에 쓴다 */
    public static Svd2 svd2(double[] affine) {
        double a = affine[0], b = affine[1], c = affine[2], d = affine[3];
        double e = (a + d) / 2, f = (a - d) / 2;
        double g = (b + c) / 2, h = (b - c) / 2;
        double q = Math.hypot(e, h), r = Math.hypot(f, g);
        double a1 = Math.atan2(g, f), a2 = Math.atan2(h, e);
        return new Svd2(q + r, q - r, (a2 + a1) / 2, (a2 - a1) / 2);
    }
}
