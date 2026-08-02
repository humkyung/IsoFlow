// JointResolver.java — 좌표 허용오차로 포트를 병합해 접합점(Joint)을 만든다
package co.atools.isoflow.engine.topology;

import co.atools.isoflow.engine.model.ComponentType;
import co.atools.isoflow.engine.model.Pipeline;
import co.atools.isoflow.engine.model.PipingComponent;
import co.atools.isoflow.engine.model.Port;
import co.atools.isoflow.engine.model.PortKind;
import co.atools.isoflow.engine.model.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PCF 는 "어느 컴포넌트가 어느 컴포넌트에 붙는다"를 명시하지 않는다.
 * 좌표가 같으면 연결된 것이다 — 다만 반올림 오차가 있으므로 허용오차로 병합한다.
 *
 * <p>병합 대상은 <b>배관 경로를 이루는 컴포넌트의 END/BRANCH 포트</b>뿐이다.
 * 용접·유동화살표는 위치만 겹칠 뿐 경로를 구성하지 않으므로 제외한다
 * (포함하면 접합점 차수가 부풀어 오탐이 난다).
 */
public final class JointResolver {

    /** 기본 허용오차(mm). PCF 좌표는 소수 3자리라 1mm 면 충분하다 */
    public static final double DEFAULT_TOLERANCE_MM = 1.0;

    private final double tolerance;
    private final List<Joint> joints = new ArrayList<>();
    /** 공간 해시 — 셀 좌표 → 그 셀에 속한 joint 인덱스들 */
    private final Map<Long, List<Integer>> grid = new HashMap<>();

    public JointResolver(double tolerance) {
        this.tolerance = tolerance;
    }

    public static List<Joint> resolve(Pipeline pipeline) {
        return new JointResolver(DEFAULT_TOLERANCE_MM).run(pipeline);
    }

    public List<Joint> run(Pipeline pipeline) {
        // 1) 경로 컴포넌트의 연결 포트를 병합한다
        for (PipingComponent c : pipeline.components()) {
            if (!c.type().isRoutingComponent()) continue;
            for (Port p : c.ports()) {
                if (!p.isConnectable()) continue;
                Joint j = findOrCreate(p.position());
                j.add(new PortRef(c, p));
                p.setJointKey(j.key());
            }
        }
        // 2) 종단 표시(END-CONNECTION-PIPELINE / END-POSITION-OPEN)를 가까운 접합점에 붙인다.
        //    새 접합점을 만들지는 않는다 — 표시일 뿐 경로가 아니다.
        for (PipingComponent c : pipeline.components()) {
            if (c.type() != ComponentType.END_CONNECTION_PIPELINE
                    && c.type() != ComponentType.END_POSITION_OPEN) continue;
            for (Port p : c.ports()) {
                if (p.kind() != PortKind.COORD) continue;
                Joint j = find(p.position());
                if (j != null) {
                    j.markTerminator();
                    p.setJointKey(j.key());
                }
            }
        }
        return List.copyOf(joints);
    }

    /** 허용오차 안의 기존 접합점을 찾거나 새로 만든다 */
    private Joint findOrCreate(Vec3 pos) {
        Joint found = find(pos);
        if (found != null) return found;

        Joint j = new Joint("J%04d".formatted(joints.size()), pos);
        int idx = joints.size();
        joints.add(j);
        grid.computeIfAbsent(cellKey(cell(pos.x()), cell(pos.y()), cell(pos.z())),
                k -> new ArrayList<>()).add(idx);
        return j;
    }

    /** 허용오차 안의 기존 접합점을 찾는다. 없으면 null */
    private Joint find(Vec3 pos) {
        long cx = cell(pos.x()), cy = cell(pos.y()), cz = cell(pos.z());
        Joint best = null;
        double bestDist = tolerance;
        // 인접 27셀만 보면 된다 — 셀 크기가 곧 허용오차이므로
        for (long dx = -1; dx <= 1; dx++) {
            for (long dy = -1; dy <= 1; dy++) {
                for (long dz = -1; dz <= 1; dz++) {
                    List<Integer> bucket = grid.get(cellKey(cx + dx, cy + dy, cz + dz));
                    if (bucket == null) continue;
                    for (int i : bucket) {
                        double d = joints.get(i).position().distanceTo(pos);
                        if (d <= bestDist) {
                            bestDist = d;
                            best = joints.get(i);
                        }
                    }
                }
            }
        }
        return best;
    }

    private long cell(double v) {
        return (long) Math.floor(v / tolerance);
    }

    /** 3D 셀 좌표를 하나의 long 키로 섞는다 */
    private static long cellKey(long x, long y, long z) {
        long h = x * 0x9E3779B97F4A7C15L;
        h = (h ^ y) * 0xC2B2AE3D27D4EB4FL;
        h = (h ^ z) * 0x165667B19E3779F9L;
        return h;
    }
}
