// DetailPlanner.java — 심볼이 겹쳐 읽을 수 없는 구간을 찾아 확대 상세도 자리를 잡는다
package co.atools.isoflow.engine.layout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 같은 축척으로는 분리할 수 없는 밀집 구간이 있다 —
 * 용접 간격 17mm 짜리 배관을 17m 도면에 그리면 심볼이 서로 위에 앉는다.
 * 그 구간을 원으로 표시하고 <b>확대한 상세도를 빈 자리에 따로 그린다</b>.
 *
 * <p>본도면의 심볼은 지우지 않는다 — 상세도를 안 보더라도 무엇이 어디 있는지는 알 수 있어야 한다.
 */
public final class DetailPlanner {

    /**
     * 상세도 하나.
     *
     * @param cx,cy        본도면에서 밀집 구간의 중심
     * @param r            밀집 구간 반경 (본도면 버블 반지름)
     * @param label        상세도 이름 (A, B, …)
     * @param scale        <b>위치</b>를 벌리는 배율. 심볼 크기는 그대로 둔다
     * @param dx,dy        상세도를 그릴 중심
     * @param detailRadius 상세도 원의 반지름 — 벌어진 심볼까지 감싸야 한다
     * @param clipRadius   본도면에서 가져올 범위(본도면 좌표). {@code detailRadius/scale} 이다
     */
    public record Region(double cx, double cy, double r, String label,
                         double scale, double dx, double dy,
                         double detailRadius, double clipRadius) {
    }

    /**
     * 벌린 뒤 심볼 사이가 최소 이만큼은 떨어져야 한다 (symbolUnit 배수).
     *
     * <p><b>구간을 통째로 확대하면 아무 소용이 없다</b> — 심볼도 같은 배율로 커져
     * 겹침 비율이 그대로다. 심볼은 크기를 유지하고 <b>위치만</b> 벌려야 분리된다.
     */
    private static final double TARGET_SPACING_UNITS = 1.3;
    /** 배율 상한/하한 — 너무 키우면 상세도가 도면을 잡아먹는다 */
    private static final double MIN_SCALE = 2;
    private static final double MAX_SCALE = 40;
    /**
     * 상세도 원의 반지름 상한 (symbolUnit 배수).
     * 없으면 밀집이 심할수록 원이 커져 <b>본도면이 상세도에 밀려난다</b>
     * (실측: 상한 전에는 도면 폭이 17.6m → 31.2m 로 늘었다).
     */
    private static final double MAX_RADIUS_UNITS = 2.2;

    private DetailPlanner() {
    }

    /**
     * 겹치는 심볼 뭉치를 찾아 상세도 자리를 잡는다.
     *
     * @param symbolBoxes 컴포넌트별 심볼 경계 상자
     * @param placer      이미 놓인 것들의 자리 — 상세도는 <b>빈 자리에만</b> 놓는다
     * @param contentBounds 도면 내용 경계 {minX,minY,maxX,maxY}
     * @param maxDetails  최대 개수. 넘치면 큰 뭉치부터
     * @return 자리를 잡은 상세도. 놓을 곳이 없으면 그만큼 빠진다
     */
    public static List<Region> plan(List<LabelPlacer.Box> symbolBoxes, LabelPlacer placer,
                                    double symbolUnit, double[] contentBounds, int maxDetails) {
        if (symbolUnit <= 0 || maxDetails <= 0) return List.of();

        List<List<LabelPlacer.Box>> clusters = cluster(symbolBoxes);
        clusters.removeIf(c -> c.size() < 2);
        // 겹친 개수가 많은 뭉치가 더 급하다
        clusters.sort(Comparator.comparingInt((List<LabelPlacer.Box> c) -> c.size()).reversed());

        List<Region> out = new ArrayList<>();
        int index = 0;
        for (List<LabelPlacer.Box> c : clusters) {
            if (out.size() >= maxDetails) break;

            double[] b = union(c);
            double cx = (b[0] + b[2]) / 2, cy = (b[1] + b[3]) / 2;
            double r = Math.max(Math.hypot(b[2] - b[0], b[3] - b[1]) / 2, symbolUnit * 0.6);

            // 가장 가까운 두 심볼이 목표 간격만큼 떨어질 때까지 벌린다
            double spacing = minSpacing(c);
            double scale = clamp(symbolUnit * TARGET_SPACING_UNITS / spacing, MIN_SCALE, MAX_SCALE);
            // 아래에서 상한에 걸리면 낮춘다

            // 반지름은 **심볼 위치가 퍼진 범위**에 배율을 곱해 잡는다.
            // 심볼 크기가 포함된 r 에 배율을 곱하면 상세도가 도면을 삼킨다(실측: 도면 폭의 64%)
            double spread = spreadRadius(c, cx, cy);
            double detailRadius = spread * scale + symbolUnit * 0.9;
            double cap = symbolUnit * MAX_RADIUS_UNITS;
            if (detailRadius > cap) {
                // 상한에 맞춰 배율을 낮춘다 — 덜 벌어지더라도 도면을 지키는 쪽이 낫다
                detailRadius = cap;
                scale = Math.max(MIN_SCALE, (cap - symbolUnit * 0.9) / spread);
            }
            double clipRadius = detailRadius / scale;
            double half = detailRadius;

            double[] spot = findFreeSpot(placer, contentBounds, half, symbolUnit);
            if (spot == null) continue;   // 놓을 자리가 없으면 상세도를 만들지 않는다

            // 본도면 버블과 상세도 자리를 모두 점유로 등록한다
            placer.forceOccupy(new LabelPlacer.Box(cx - r, cy - r, cx + r, cy + r));
            placer.forceOccupy(new LabelPlacer.Box(
                    spot[0] - half, spot[1] - half, spot[0] + half, spot[1] + half));

            out.add(new Region(cx, cy, r, String.valueOf((char) ('A' + index++)),
                    scale, spot[0], spot[1], detailRadius, clipRadius));
        }
        return out;
    }

    /** 뭉치 중심에서 가장 먼 심볼 중심까지의 거리 — 심볼 크기는 빼고 위치만 본다 */
    private static double spreadRadius(List<LabelPlacer.Box> boxes, double cx, double cy) {
        double max = 0;
        for (LabelPlacer.Box b : boxes) {
            max = Math.max(max, Math.hypot((b.minX() + b.maxX()) / 2 - cx,
                    (b.minY() + b.maxY()) / 2 - cy));
        }
        return Math.max(max, 1e-6);
    }

    /** 뭉치 안에서 가장 가까운 두 심볼의 중심 거리. 0 이면 아주 작은 값으로 둔다 */
    private static double minSpacing(List<LabelPlacer.Box> boxes) {
        double min = Double.MAX_VALUE;
        for (int i = 0; i < boxes.size(); i++) {
            for (int j = i + 1; j < boxes.size(); j++) {
                LabelPlacer.Box a = boxes.get(i), b = boxes.get(j);
                double d = Math.hypot((a.minX() + a.maxX()) / 2 - (b.minX() + b.maxX()) / 2,
                        (a.minY() + a.maxY()) / 2 - (b.minY() + b.maxY()) / 2);
                min = Math.min(min, d);
            }
        }
        return min == Double.MAX_VALUE || min < 1e-6 ? 1e-6 : min;
    }

    /** 경계가 겹치는 상자끼리 한 뭉치로 묶는다 */
    private static List<List<LabelPlacer.Box>> cluster(List<LabelPlacer.Box> boxes) {
        int n = boxes.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (boxes.get(i).overlaps(boxes.get(j))) union(parent, i, j);
            }
        }
        java.util.Map<Integer, List<LabelPlacer.Box>> groups = new java.util.LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            groups.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(boxes.get(i));
        }
        return new ArrayList<>(groups.values());
    }

    private static int find(int[] p, int i) {
        while (p[i] != i) {
            p[i] = p[p[i]];
            i = p[i];
        }
        return i;
    }

    private static void union(int[] p, int a, int b) {
        p[find(p, a)] = find(p, b);
    }

    private static double[] union(List<LabelPlacer.Box> boxes) {
        double[] b = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (LabelPlacer.Box x : boxes) {
            b[0] = Math.min(b[0], x.minX());
            b[1] = Math.min(b[1], x.minY());
            b[2] = Math.max(b[2], x.maxX());
            b[3] = Math.max(b[3], x.maxY());
        }
        return b;
    }

    /**
     * 상세도를 놓을 빈 자리를 찾는다.
     *
     * <p>도면 안쪽부터 훑고, 없으면 <b>바깥으로 밀어낸다</b> —
     * 자리를 못 찾았다고 상세도를 포기하면 밀집 구간을 읽을 방법이 없어진다.
     */
    private static double[] findFreeSpot(LabelPlacer placer, double[] bounds, double half,
                                         double symbolUnit) {
        // 촘촘히 훑어야 도면 안쪽 빈 곳을 놓치지 않는다 — 밖으로 밀리면 도면이 그만큼 작아진다
        double step = Math.max(half / 2, symbolUnit * 0.5);
        double pad = symbolUnit * 0.5;

        for (double y = bounds[1] + half; y <= bounds[3] - half; y += step) {
            for (double x = bounds[0] + half; x <= bounds[2] - half; x += step) {
                LabelPlacer.Box box = new LabelPlacer.Box(x - half - pad, y - half - pad,
                        x + half + pad, y + half + pad);
                if (placer.isFree(box)) return new double[]{x, y};
            }
        }

        // 도면 오른쪽 바깥에 세로로 쌓는다
        double x = bounds[2] + half + symbolUnit * 2;
        for (double y = bounds[3] - half; y >= bounds[1] - half * 4; y -= step) {
            LabelPlacer.Box box = new LabelPlacer.Box(x - half - pad, y - half - pad,
                    x + half + pad, y + half + pad);
            if (placer.isFree(box)) return new double[]{x, y};
        }
        return null;
    }

    /**
     * 선분을 원 안쪽만 남기고 자른다. 원과 만나지 않으면 null.
     * 상세도에 담을 중심선을 잘라내는 데 쓴다.
     */
    public static double[] clipToCircle(double x1, double y1, double x2, double y2,
                                        double cx, double cy, double radius) {
        double dx = x2 - x1, dy = y2 - y1;
        double fx = x1 - cx, fy = y1 - cy;
        double a = dx * dx + dy * dy;
        if (a < 1e-12) {
            return Math.hypot(fx, fy) <= radius ? new double[]{x1, y1, x2, y2} : null;
        }
        double b = 2 * (fx * dx + fy * dy);
        double c = fx * fx + fy * fy - radius * radius;
        double disc = b * b - 4 * a * c;
        if (disc < 0) return null;

        double sq = Math.sqrt(disc);
        double t0 = Math.max(0, (-b - sq) / (2 * a));
        double t1 = Math.min(1, (-b + sq) / (2 * a));
        if (t0 >= t1) return null;
        return new double[]{x1 + dx * t0, y1 + dy * t0, x1 + dx * t1, y1 + dy * t1};
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
