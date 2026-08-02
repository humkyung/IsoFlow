// SheetSplitter.java — 도면을 여러 장으로 나눈다. 위상을 따라 묶으므로 요소가 반토막 나지 않는다
package co.atools.isoflow.engine.layout;

import co.atools.isoflow.engine.geometry.IsoProjection;
import co.atools.isoflow.engine.model.Pipeline;
import co.atools.isoflow.engine.model.PipingComponent;
import co.atools.isoflow.engine.model.Port;
import co.atools.isoflow.engine.model.Vec3;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <b>공간을 격자로 잘라 나누면 안 된다.</b> 경계에 걸친 엘보나 밸브가 반쪽만 남는다.
 * 배관을 따라 걸으면서 컴포넌트를 통째로 담아야 도면이 성립한다.
 *
 * <p>나눌지 말지는 {@link Criterion} 이 정한다 — 기준을 바꿔도 묶는 방식은 그대로다.
 */
public final class SheetSplitter {

    /**
     * 한 장에 담긴 것.
     *
     * @param components 이 장에 그릴 컴포넌트
     * @param dimensions 이 장에 걸치는 치수 (경계에서 잘려 있다)
     * @param links      다른 장으로 이어지는 지점 — 연속 표기를 붙인다
     */
    public record Sheet(List<PipingComponent> components, List<DimensionRun> dimensions,
                        List<Link> links) {
    }

    /**
     * @param at        이어지는 지점(로컬 좌표)
     * @param sheetNo   상대 시트 번호 (1-based)
     * @param outgoing  true 면 "다음 장으로", false 면 "이전 장에서"
     */
    public record Link(Vec3 at, int sheetNo, boolean outgoing) {
    }

    /** 이만큼 담았을 때 한 장으로 낼 수 있는지 판정한다 */
    @FunctionalInterface
    public interface Criterion {
        /**
         * @param projectedBounds 지금까지 담은 것들의 투영 경계 {minX,minY,maxX,maxY}
         * @param componentCount  지금까지 담은 컴포넌트 수
         * @return 한 장에 들어가면 true
         */
        boolean fits(double[] projectedBounds, int componentCount);

        /** 나누지 않는다 — 기본값 */
        static Criterion never() {
            return (b, n) -> true;
        }

        /** 투영 범위가 상한을 넘으면 나눈다 */
        static Criterion maxExtent(double maxMm) {
            return (b, n) -> maxMm <= 0
                    || (b[2] - b[0] <= maxMm && b[3] - b[1] <= maxMm);
        }
    }

    private SheetSplitter() {
    }

    /**
     * 도면을 나눈다. 기준이 항상 만족되면 한 장짜리 목록을 돌려준다.
     *
     * @param projection 투영 — 범위를 재는 데 쓴다
     */
    public static List<Sheet> split(Pipeline pipeline, List<DimensionRun> dimensions,
                                    IsoProjection projection, Criterion criterion) {
        // 어디에도 붙지 않은 컴포넌트(흐름 화살표 등)는 걷기에서 뺀다.
        // 연결이 없으면 걷기의 출발점으로 뽑히기 쉽고, 그러면 혼자 한 장을 차지한다 —
        // 흐름 화살표는 자기가 가리키는 배관과 같은 장에 있어야 한다
        List<PipingComponent> isolated = new ArrayList<>();
        List<PipingComponent> order = walkOrder(pipeline, isolated);
        if (order.isEmpty()) {
            // 전부 떨어져 있으면 나눌 근거가 없다 — 한 장에 담는다
            return List.of(new Sheet(List.copyOf(pipeline.components()), List.copyOf(dimensions),
                    List.of()));
        }

        List<List<PipingComponent>> groups = balancedGroups(order, projection, criterion);
        attachIsolated(isolated, groups, projection);
        return assemble(groups, dimensions, pipeline);
    }

    /**
     * 시트 수는 그대로 두면서 컴포넌트를 고르게 나눈다.
     *
     * <p>기준만으로 그리디하게 채우면 <b>앞 시트가 다 먹고 뒤 시트가 헐거워진다</b>
     * (실측: 41개짜리 라인이 383 / 33 요소로 갈렸다).
     * 그래서 먼저 기준만으로 몇 장이 필요한지 세고, 그 장수를 유지하면서
     * 시트당 개수 상한을 조여 다시 채운다.
     *
     * <p>상한을 조이면 그룹이 <i>일찍</i> 닫히기만 하므로 장수는 늘어날 수만 있다.
     * 목표치부터 상한을 올려 가며 <b>장수를 늘리지 않는 것들 중 가장 큰 시트가 가장 작은</b> 배분을 고른다.
     * 장수가 상한에 대해 단조롭지 않아서 처음 성공한 값이 가장 고르다는 보장이 없다.
     *
     * <p>기준이 빡빡하면(시트당 담을 범위가 컴포넌트 하나 크기에 가까우면) 고르게 나눌 수 없다.
     * 그때는 <b>장수를 늘리지 않는 쪽</b>을 택한다 — 종이를 더 쓰는 것보다 낫다.
     */
    private static List<List<PipingComponent>> balancedGroups(
            List<PipingComponent> order, IsoProjection projection, Criterion criterion) {
        List<List<PipingComponent>> greedy = fill(order, projection, criterion, Integer.MAX_VALUE);
        int sheets = greedy.size();
        if (sheets <= 1) return greedy;

        List<List<PipingComponent>> best = greedy;
        int bestMax = largestGroup(greedy);
        int target = (int) Math.ceil((double) order.size() / sheets);

        for (int cap = target; cap <= order.size(); cap++) {
            List<List<PipingComponent>> candidate = fill(order, projection, criterion, cap);
            if (candidate.size() > sheets) continue;
            int max = largestGroup(candidate);
            if (max < bestMax) {
                bestMax = max;
                best = candidate;
            }
            // 목표치에 도달했으면 더 볼 것이 없다
            if (bestMax <= target) break;
        }
        return best;
    }

    private static int largestGroup(List<List<PipingComponent>> groups) {
        int max = 0;
        for (List<PipingComponent> g : groups) max = Math.max(max, g.size());
        return max;
    }

    /**
     * 걷기 순서대로 채운다.
     *
     * @param maxPerGroup 시트당 컴포넌트 상한. 기준보다 먼저 걸리면 여기서 끊는다
     */
    private static List<List<PipingComponent>> fill(List<PipingComponent> order,
                                                    IsoProjection projection, Criterion criterion,
                                                    int maxPerGroup) {
        List<List<PipingComponent>> groups = new ArrayList<>();
        List<PipingComponent> current = new ArrayList<>();
        double[] box = null;

        for (PipingComponent c : order) {
            double[] grown = grow(box, boundsOf(c, projection));
            // 한 컴포넌트만으로 기준을 못 넘기면 그래도 담는다 — 빈 시트를 만들 수는 없다
            boolean full = !current.isEmpty()
                    && (current.size() >= maxPerGroup || !criterion.fits(grown, current.size() + 1));
            if (full) {
                groups.add(current);
                current = new ArrayList<>();
                grown = boundsOf(c, projection);
            }
            current.add(c);
            box = grown;
        }
        if (!current.isEmpty()) groups.add(current);
        return groups;
    }

    /** 떨어져 있는 컴포넌트를 가장 가까운 그룹에 얹는다 */
    private static void attachIsolated(List<PipingComponent> isolated,
                                       List<List<PipingComponent>> groups, IsoProjection projection) {
        if (isolated.isEmpty() || groups.isEmpty()) return;

        List<double[]> centres = new ArrayList<>(groups.size());
        for (List<PipingComponent> g : groups) {
            double[] box = null;
            for (PipingComponent c : g) box = grow(box, boundsOf(c, projection));
            centres.add(box == null ? new double[]{0, 0}
                    : new double[]{(box[0] + box[2]) / 2, (box[1] + box[3]) / 2});
        }

        for (PipingComponent c : isolated) {
            double[] b = boundsOf(c, projection);
            double cx = (b[0] + b[2]) / 2, cy = (b[1] + b[3]) / 2;
            int best = 0;
            double bestDist = Double.MAX_VALUE;
            for (int i = 0; i < centres.size(); i++) {
                double d = Math.hypot(cx - centres.get(i)[0], cy - centres.get(i)[1]);
                if (d < bestDist) {
                    bestDist = d;
                    best = i;
                }
            }
            groups.get(best).add(c);
        }
    }

    // ─────────────────────────── 순서 ───────────────────────────

    /**
     * 배관을 따라 걷는 순서를 만든다.
     * 파일 순서가 라인 순서와 같다는 보장이 없어 포트 좌표로 인접을 세운다.
     *
     * @param isolated 어디에도 붙지 않은 컴포넌트를 여기에 담아 돌려준다 (걷기에서 제외)
     */
    private static List<PipingComponent> walkOrder(Pipeline pipeline,
                                                   List<PipingComponent> isolated) {
        Map<String, List<PipingComponent>> atPoint = new HashMap<>();
        for (PipingComponent c : pipeline.components()) {
            for (String k : keysOf(c)) {
                atPoint.computeIfAbsent(k, x -> new ArrayList<>()).add(c);
            }
        }

        List<PipingComponent> all = new ArrayList<>();
        for (PipingComponent c : pipeline.components()) {
            if (degreeOf(c, atPoint) == 0) isolated.add(c);
            else all.add(c);
        }
        if (all.isEmpty()) return List.of();

        // 연결 수가 가장 적은 것에서 출발한다 — 라인의 끝에서 시작해야 순서가 자연스럽다
        PipingComponent start = null;
        int bestDegree = Integer.MAX_VALUE;
        for (PipingComponent c : all) {
            int degree = degreeOf(c, atPoint);
            if (degree < bestDegree) {
                bestDegree = degree;
                start = c;
            }
        }

        Set<PipingComponent> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        List<PipingComponent> out = new ArrayList<>(all.size());
        Deque<PipingComponent> stack = new ArrayDeque<>();
        if (start != null) stack.push(start);

        while (out.size() < all.size()) {
            if (stack.isEmpty()) {
                // 끊어진 조각이 남았다 — 다음 미방문 컴포넌트에서 다시 시작한다
                for (PipingComponent c : all) {
                    if (!seen.contains(c)) {
                        stack.push(c);
                        break;
                    }
                }
                if (stack.isEmpty()) break;
            }
            PipingComponent c = stack.pop();
            if (!seen.add(c)) continue;
            out.add(c);
            for (String k : keysOf(c)) {
                for (PipingComponent n : atPoint.getOrDefault(k, List.of())) {
                    if (!seen.contains(n)) stack.push(n);
                }
            }
        }
        return out;
    }

    /** 좌표를 공유하는 이웃 수 */
    private static int degreeOf(PipingComponent c, Map<String, List<PipingComponent>> atPoint) {
        int degree = 0;
        for (String k : keysOf(c)) degree += atPoint.getOrDefault(k, List.of()).size() - 1;
        return degree;
    }

    /** 포트 좌표를 mm 단위로 반올림한 키 — 같은 점에 붙은 컴포넌트끼리 묶는다 */
    private static List<String> keysOf(PipingComponent c) {
        List<String> keys = new ArrayList<>(c.ports().size());
        for (Port p : c.ports()) keys.add(keyOf(p.position()));
        return keys;
    }

    private static String keyOf(Vec3 v) {
        return Math.round(v.x()) + "," + Math.round(v.y()) + "," + Math.round(v.z());
    }

    // ─────────────────────────── 조립 ───────────────────────────

    /** 그룹별로 치수를 잘라 붙이고 시트 간 연결 지점을 찾는다 */
    private static List<Sheet> assemble(List<List<PipingComponent>> groups,
                                        List<DimensionRun> dimensions, Pipeline pipeline) {
        // 좌표 → 그 점이 놓인 시트들.
        // 포트 객체가 아니라 좌표로 봐야 한다 — 경계점은 양쪽 컴포넌트가 각자의 Port 로 갖고 있어서
        // 객체로 따지면 한쪽 시트에만 잡히고 반대편 칸의 치수가 통째로 사라진다
        Map<String, Set<Integer>> sheetsAtPoint = new LinkedHashMap<>();
        for (int i = 0; i < groups.size(); i++) {
            for (PipingComponent c : groups.get(i)) {
                for (Port p : c.ports()) {
                    sheetsAtPoint.computeIfAbsent(keyOf(p.position()), x -> new LinkedHashSet<>()).add(i);
                }
            }
        }

        List<List<DimensionRun>> perSheet = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) perSheet.add(new ArrayList<>());
        for (DimensionRun run : dimensions) {
            for (int i = 0; i < groups.size(); i++) {
                perSheet.get(i).addAll(sliceForSheet(run, i, sheetsAtPoint));
            }
        }

        List<List<Link>> links = findLinks(groups);

        List<Sheet> out = new ArrayList<>(groups.size());
        for (int i = 0; i < groups.size(); i++) {
            out.add(new Sheet(List.copyOf(groups.get(i)), List.copyOf(perSheet.get(i)),
                    List.copyOf(links.get(i))));
        }
        return out;
    }

    /**
     * 치수 구간에서 한 시트에 걸치는 조각들을 뽑는다.
     *
     * <p>연속으로 이어지는 점들만 한 조각으로 묶는다. 중간이 빠지면 조각을 끊는다 —
     * 떨어진 두 점을 이으면 있지도 않은 칸이 생긴다.
     * <b>각 칸은 정확히 한 시트에만 들어간다</b>(양 끝이 모두 그 시트에 있어야 하므로)
     * — 그래서 시트별 치수를 모두 더하면 원래 합계가 된다.
     */
    private static List<DimensionRun> sliceForSheet(DimensionRun run, int sheet,
                                                    Map<String, Set<Integer>> sheetsAtPoint) {
        List<DimensionRun> out = new ArrayList<>();
        List<Port> points = new ArrayList<>();
        List<Double> lengths = new ArrayList<>();
        boolean prevOnSheet = false;

        for (int i = 0; i < run.points().size(); i++) {
            Port p = run.points().get(i);
            boolean onSheet = sheetsAtPoint
                    .getOrDefault(keyOf(p.position()), Set.of()).contains(sheet);

            if (!onSheet) {
                flush(run, points, lengths, out);
                prevOnSheet = false;
                continue;
            }
            if (prevOnSheet) lengths.add(run.trueLengths().get(i - 1));
            points.add(p);
            prevOnSheet = true;
        }
        flush(run, points, lengths, out);
        return out;
    }

    /** 모아 둔 점들을 하나의 치수 구간으로 확정한다 */
    private static void flush(DimensionRun run, List<Port> points, List<Double> lengths,
                              List<DimensionRun> out) {
        if (points.size() >= 2 && lengths.size() == points.size() - 1) {
            out.add(new DimensionRun(run.axis(), List.copyOf(points), List.copyOf(lengths)));
        }
        points.clear();
        lengths.clear();
    }

    /** 시트 사이에 배관이 이어지는 지점을 찾는다 */
    private static List<List<Link>> findLinks(List<List<PipingComponent>> groups) {
        List<List<Link>> links = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) links.add(new ArrayList<>());

        Map<String, Vec3> pointOf = new HashMap<>();
        Map<String, Set<Integer>> sheetsAt = new LinkedHashMap<>();
        for (int i = 0; i < groups.size(); i++) {
            for (PipingComponent c : groups.get(i)) {
                for (Port p : c.ports()) {
                    String k = keyOf(p.position());
                    pointOf.putIfAbsent(k, p.position());
                    sheetsAt.computeIfAbsent(k, x -> new LinkedHashSet<>()).add(i);
                }
            }
        }
        for (Map.Entry<String, Set<Integer>> e : sheetsAt.entrySet()) {
            if (e.getValue().size() < 2) continue;
            List<Integer> sheets = new ArrayList<>(e.getValue());
            Vec3 at = pointOf.get(e.getKey());
            for (int a = 0; a < sheets.size(); a++) {
                for (int b = 0; b < sheets.size(); b++) {
                    if (a == b) continue;
                    int from = sheets.get(a), to = sheets.get(b);
                    links.get(from).add(new Link(at, to + 1, to > from));
                }
            }
        }
        return links;
    }

    // ─────────────────────────── 범위 ───────────────────────────

    private static double[] boundsOf(PipingComponent c, IsoProjection projection) {
        double[] b = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (Port p : c.ports()) {
            double[] q = projection.project(p.position());
            b[0] = Math.min(b[0], q[0]);
            b[1] = Math.min(b[1], q[1]);
            b[2] = Math.max(b[2], q[0]);
            b[3] = Math.max(b[3], q[1]);
        }
        c.centre().ifPresent(v -> {
            double[] q = projection.project(v);
            b[0] = Math.min(b[0], q[0]);
            b[1] = Math.min(b[1], q[1]);
            b[2] = Math.max(b[2], q[0]);
            b[3] = Math.max(b[3], q[1]);
        });
        return b[0] > b[2] ? new double[]{0, 0, 0, 0} : b;
    }

    private static double[] grow(double[] box, double[] add) {
        if (box == null) return add;
        return new double[]{
                Math.min(box[0], add[0]), Math.min(box[1], add[1]),
                Math.max(box[2], add[2]), Math.max(box[3], add[3])};
    }
}
