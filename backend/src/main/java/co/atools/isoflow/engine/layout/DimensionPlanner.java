// DimensionPlanner.java — 축 정렬 중심선을 직선 런으로 묶어 러닝 치수 계획을 만든다
package co.atools.isoflow.engine.layout;

import co.atools.isoflow.engine.geometry.Axis6;
import co.atools.isoflow.engine.geometry.AxisClassifier;
import co.atools.isoflow.engine.geometry.CenterlineSegments;
import co.atools.isoflow.engine.model.ComponentType;
import co.atools.isoflow.engine.model.Pipeline;
import co.atools.isoflow.engine.model.PipingComponent;
import co.atools.isoflow.engine.model.Port;
import co.atools.isoflow.engine.model.Vec3;
import co.atools.isoflow.engine.style.IsoStyle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>반드시 길이 압축 전에 실행해야 한다.</b> 압축은 좌표를 바꾸므로
 * 그 뒤에 길이를 재면 도면에 가짜 치수가 찍힌다.
 *
 * <p>같은 방위의 인접 구간을 하나의 런으로 병합하고, 경계마다 중간점을 남겨
 * 러닝 치수(여러 칸으로 나뉜 치수)를 만들 수 있게 한다.
 */
public final class DimensionPlanner {

    /** 같은 점으로 볼 허용오차(mm) */
    private static final double TOLERANCE_MM = 1.0;

    private DimensionPlanner() {
    }


    /** 구간 하나 — 병합 전 단위. owner 는 치수 대상 노드를 고를 때 쓴다 */
    private record Seg(Port from, Port to, Axis6 axis, double length, ComponentType owner) {
    }

    public static List<DimensionRun> plan(Pipeline pipeline) {
        return plan(pipeline, IsoStyle.defaults());
    }

    /**
     * @param style 최소 칸 길이는 도면 크기에도 비례한다 —
     *              큰 도면에서 절대값만 쓰면 잔치수가 남는다
     */
    public static List<DimensionRun> plan(Pipeline pipeline, IsoStyle style) {
        double minInterval = style.dimensions().effectiveMinIntervalMm(extentOf(pipeline));
        List<Seg> segments = collectSegments(pipeline);

        // 위치 키 → 그 점에 닿는 구간들
        Map<String, List<Integer>> byPoint = new HashMap<>();
        for (int i = 0; i < segments.size(); i++) {
            byPoint.computeIfAbsent(key(segments.get(i).from().position()), k -> new ArrayList<>()).add(i);
            byPoint.computeIfAbsent(key(segments.get(i).to().position()), k -> new ArrayList<>()).add(i);
        }

        boolean[] used = new boolean[segments.size()];
        List<DimensionRun> runs = new ArrayList<>();

        for (int i = 0; i < segments.size(); i++) {
            if (used[i] || segments.get(i).axis().isSkew()) continue;

            // i 를 씨앗으로 양쪽으로 확장한다
            List<Port> points = new ArrayList<>();
            List<Double> lengths = new ArrayList<>();
            List<ComponentType> owners = new ArrayList<>();
            used[i] = true;
            points.add(segments.get(i).from());
            points.add(segments.get(i).to());
            lengths.add(segments.get(i).length());
            owners.add(segments.get(i).owner());

            extend(segments, byPoint, used, points, lengths, owners, segments.get(i).axis(), true);
            extend(segments, byPoint, used, points, lengths, owners, segments.get(i).axis(), false);

            // 엘보 다리만으로 이루어진 런은 잴 구간이 아니다 — 치수를 만들지 않는다
            if (owners.stream().allMatch(DimensionPlanner::isTangent)) continue;
            runs.add(condense(segments.get(i).axis(), points, lengths, owners, minInterval));
        }
        return runs;
    }

    /**
     * 런의 끝에서 같은 방위의 이웃 구간을 계속 이어 붙인다.
     * 분기점(구간 3개 이상이 만나는 곳)에서는 멈춘다 — 어느 쪽으로 이어야 할지 정할 수 없기 때문.
     */
    private static void extend(List<Seg> segments, Map<String, List<Integer>> byPoint, boolean[] used,
                               List<Port> points, List<Double> lengths, List<ComponentType> owners,
                               Axis6 axis, boolean forward) {
        while (true) {
            Port tip = forward ? points.get(points.size() - 1) : points.get(0);
            List<Integer> touching = byPoint.getOrDefault(key(tip.position()), List.of());
            // 분기에서는 런을 끊는다
            if (touching.size() != 2) return;

            Integer next = null;
            for (int idx : touching) {
                if (!used[idx]) next = idx;
            }
            if (next == null) return;

            Seg s = segments.get(next);
            if (!sameLine(s.axis(), axis)) return;

            used[next] = true;
            Port other = samePoint(s.from().position(), tip.position()) ? s.to() : s.from();
            if (forward) {
                points.add(other);
                lengths.add(s.length());
                owners.add(s.owner());
            } else {
                points.add(0, other);
                lengths.add(0, s.length());
                owners.add(0, s.owner());
            }
        }
    }

    /**
     * 치수 대상 노드를 고른다.
     *
     * <p>등각도는 <b>중심선 교점(엘보 모서리) 사이</b>를 치수한다.
     * 엘보의 접점(탄젠트 포인트)은 치수 대상이 아니다 —
     * 붙여 두면 PCF 에서는 엘보 다리 길이(533)가, IDF 에서는 17mm 가 도면을 뒤덮는다.
     *
     * <p>버리는 점의 길이는 이웃 칸에 합치므로 <b>구간 합계는 보존된다</b>.
     */
    private static DimensionRun condense(Axis6 axis, List<Port> points, List<Double> lengths,
                                         List<ComponentType> owners, double minInterval) {
        if (points.size() <= 2) return new DimensionRun(axis, List.copyOf(points), List.copyOf(lengths));

        List<Port> keptPoints = new ArrayList<>();
        List<Double> keptLengths = new ArrayList<>();
        keptPoints.add(points.get(0));

        double carried = 0;
        for (int i = 0; i < lengths.size(); i++) {
            carried += lengths.get(i);
            boolean isLast = i == lengths.size() - 1;
            if (isLast || keepJunction(owners, lengths, i, carried, minInterval)) {
                keptPoints.add(points.get(i + 1));
                keptLengths.add(carried);
                carried = 0;
            }
        }
        return new DimensionRun(axis, List.copyOf(keptPoints), List.copyOf(keptLengths));
    }

    /** 구간 i 와 i+1 사이의 접점을 치수 노드로 남길지 */
    private static boolean keepJunction(List<ComponentType> owners, List<Double> lengths,
                                        int i, double carried, double minInterval) {
        // 엘보/밴드의 접점은 교점이 아니다
        if (isTangent(owners.get(i)) || isTangent(owners.get(i + 1))) return false;
        // 너무 짧은 칸은 이웃에 합친다
        return carried >= minInterval && lengths.get(i + 1) >= minInterval;
    }

    /** 접점만 만들 뿐 치수 교점이 아닌 컴포넌트 */
    private static boolean isTangent(ComponentType t) {
        return t == ComponentType.ELBOW || t == ComponentType.BEND;
    }

    /** 방향이 반대여도 같은 직선이면 이어 붙인다 */
    private static boolean sameLine(Axis6 a, Axis6 b) {
        return !a.isSkew() && !b.isSkew() && a.worldAxisIndex() == b.worldAxisIndex();
    }

    /** 중심선 구간을 모으고 각 구간의 실제 길이를 기록한다 */
    private static List<Seg> collectSegments(Pipeline pipeline) {
        // 좌표 → Port 로 되돌리기 위한 인덱스 (구간은 좌표만 들고 있다)
        Map<String, Port> portAt = new LinkedHashMap<>();
        for (PipingComponent c : pipeline.components()) {
            for (Port p : c.ports()) portAt.putIfAbsent(key(p.position()), p);
        }

        List<Seg> out = new ArrayList<>();
        for (PipingComponent c : pipeline.components()) {
            for (CenterlineSegments.Segment s : CenterlineSegments.of(c)) {
                Port from = portAt.get(key(s.from()));
                Port to = portAt.get(key(s.to()));
                if (from == null || to == null) continue;
                double len = s.from().distanceTo(s.to());
                if (len < TOLERANCE_MM) continue;   // 길이 0 구간에는 치수를 붙이지 않는다
                out.add(new Seg(from, to, AxisClassifier.classify(s.delta()), len, c.type()));
            }
        }
        return out;
    }

    private static boolean samePoint(Vec3 a, Vec3 b) {
        return a.distanceTo(b) <= TOLERANCE_MM;
    }

    /** 파이프라인 경계 상자의 대각선 길이 — 최소 칸을 도면 크기에 비례시키는 기준 */
    private static double extentOf(Pipeline pipeline) {
        double[] b = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE,
                -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        boolean any = false;
        for (PipingComponent c : pipeline.components()) {
            for (Port p : c.ports()) {
                Vec3 v = p.position();
                b[0] = Math.min(b[0], v.x());
                b[1] = Math.min(b[1], v.y());
                b[2] = Math.min(b[2], v.z());
                b[3] = Math.max(b[3], v.x());
                b[4] = Math.max(b[4], v.y());
                b[5] = Math.max(b[5], v.z());
                any = true;
            }
        }
        if (!any) return 0;
        return Math.sqrt(Math.pow(b[3] - b[0], 2) + Math.pow(b[4] - b[1], 2) + Math.pow(b[5] - b[2], 2));
    }

    /** 허용오차 단위로 양자화한 위치 키 */
    private static String key(Vec3 v) {
        return Math.round(v.x() / TOLERANCE_MM) + "|"
                + Math.round(v.y() / TOLERANCE_MM) + "|"
                + Math.round(v.z() / TOLERANCE_MM);
    }
}
