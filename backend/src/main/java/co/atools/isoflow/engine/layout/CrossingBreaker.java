// CrossingBreaker.java — 화면에서 겹치는 배관 중 뒤쪽 것을 끊어 앞뒤를 읽을 수 있게 한다
package co.atools.isoflow.engine.layout;

import co.atools.isoflow.engine.geometry.IsoProjection;
import co.atools.isoflow.engine.model.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 등각도는 3D 를 한 장에 눌러 담으므로 <b>실제로는 떨어져 있는 배관이 화면에서 교차</b>한다.
 * 그대로 두면 두 배관이 만나는 것처럼 보인다. 관례대로 <b>뒤에 있는 쪽을 끊어</b> 앞뒤를 표시한다.
 *
 * <p>앞뒤는 투영의 영공간(=보는 방향)으로 판정한다. 화면 좌표만으로는 알 수 없다.
 */
public final class CrossingBreaker {

    /**
     * 화면에 그릴 한 구간. 세계 좌표를 함께 들고 있어야 깊이를 잴 수 있다.
     *
     * @param a 시작점(세계)
     * @param b 끝점(세계)
     */
    public record Segment(Vec3 a, Vec3 b) {
    }

    /** 끊긴 뒤 남은 조각 — 원래 구간의 매개변수 구간 {@code [t0,t1]} */
    public record Piece(int segmentIndex, double t0, double t1) {
    }

    /** 이보다 가까우면 같은 점으로 본다 (도면 단위) */
    private static final double TOUCH = 1e-6;

    private CrossingBreaker() {
    }

    /**
     * 구간들을 훑어 뒤쪽 것을 끊는다.
     *
     * @param gap 끊을 틈의 폭 (도면 단위). 0 이하면 아무것도 하지 않는다
     * @return 그릴 조각들. 끊기지 않은 구간은 {@code [0,1]} 하나로 나온다
     */
    public static List<Piece> breakAtCrossings(List<Segment> segments, IsoProjection projection,
                                               double gap) {
        List<Piece> whole = new ArrayList<>(segments.size());
        for (int i = 0; i < segments.size(); i++) whole.add(new Piece(i, 0, 1));
        if (gap <= 0 || segments.size() < 2) return whole;

        // 구간별로 잘라낼 매개변수 구간을 모은다
        Map<Integer, List<double[]>> cuts = new LinkedHashMap<>();
        double[][] p = new double[segments.size()][];
        double[][] q = new double[segments.size()][];
        for (int i = 0; i < segments.size(); i++) {
            p[i] = projection.project(segments.get(i).a());
            q[i] = projection.project(segments.get(i).b());
        }

        for (int i = 0; i < segments.size(); i++) {
            for (int j = i + 1; j < segments.size(); j++) {
                double[] hit = crossParameters(p[i], q[i], p[j], q[j]);
                if (hit == null) continue;

                double ti = hit[0], tj = hit[1];
                Vec3 pi = lerp(segments.get(i), ti);
                Vec3 pj = lerp(segments.get(j), tj);
                double di = projection.depth(pi);
                double dj = projection.depth(pj);

                // 3D 에서도 같은 점이면 진짜로 만나는 것이다 — 끊으면 안 된다
                if (pi.distanceTo(pj) < 1.0) continue;

                int behind = di < dj ? i : j;
                double t = di < dj ? ti : tj;
                double len = length(p[behind], q[behind]);
                if (len < TOUCH) continue;

                double half = (gap / 2) / len;
                // 끝점에 너무 가까우면 끊지 않는다 — 연결이 끊어져 보인다
                if (t - half <= 0.02 || t + half >= 0.98) continue;

                cuts.computeIfAbsent(behind, k -> new ArrayList<>())
                        .add(new double[]{t - half, t + half});
            }
        }
        if (cuts.isEmpty()) return whole;

        List<Piece> out = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            List<double[]> mine = cuts.get(i);
            if (mine == null) {
                out.add(new Piece(i, 0, 1));
                continue;
            }
            out.addAll(split(i, mine));
        }
        return out;
    }

    /** 잘라낼 구간들을 빼고 남는 조각을 만든다 */
    private static List<Piece> split(int index, List<double[]> cuts) {
        cuts.sort(Comparator.comparingDouble(c -> c[0]));

        List<Piece> out = new ArrayList<>();
        double cursor = 0;
        for (double[] c : cuts) {
            double from = Math.max(cursor, c[0]);
            if (from > cursor + 1e-9) out.add(new Piece(index, cursor, from));
            cursor = Math.max(cursor, c[1]);
        }
        if (cursor < 1 - 1e-9) out.add(new Piece(index, cursor, 1));
        return out;
    }

    /**
     * 두 선분이 서로를 가로지르는 매개변수 {@code [t, u]}. 만나지 않으면 null.
     * 끝점을 공유하거나 거의 평행하면 교차로 보지 않는다.
     */
    static double[] crossParameters(double[] a0, double[] a1, double[] b0, double[] b1) {
        double rx = a1[0] - a0[0], ry = a1[1] - a0[1];
        double sx = b1[0] - b0[0], sy = b1[1] - b0[1];
        double denom = rx * sy - ry * sx;
        if (Math.abs(denom) < 1e-9) return null;   // 평행하거나 겹쳐 있다

        double t = ((b0[0] - a0[0]) * sy - (b0[1] - a0[1]) * sx) / denom;
        double u = ((b0[0] - a0[0]) * ry - (b0[1] - a0[1]) * rx) / denom;
        if (t <= 0 || t >= 1 || u <= 0 || u >= 1) return null;   // 끝점 접촉은 제외
        return new double[]{t, u};
    }

    private static Vec3 lerp(Segment s, double t) {
        return s.a().plus(s.b().minus(s.a()).scale(t));
    }

    private static double length(double[] a, double[] b) {
        return Math.hypot(b[0] - a[0], b[1] - a[1]);
    }
}
