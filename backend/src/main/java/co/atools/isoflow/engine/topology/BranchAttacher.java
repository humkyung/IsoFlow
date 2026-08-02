// BranchAttacher.java — 배관 중간에 올라타는 분기(올렛/셋온)를 모재 배관에 연결한다
package co.atools.isoflow.engine.topology;

import co.atools.isoflow.engine.model.Pipeline;
import co.atools.isoflow.engine.model.PipingComponent;
import co.atools.isoflow.engine.model.Port;
import co.atools.isoflow.engine.model.PortKind;
import co.atools.isoflow.engine.model.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 올렛(WELDOLET 등)은 모재 배관의 <b>끝점이 아니라 중간</b>에 용접된다.
 * 따라서 좌표 일치만으로는 절대 연결되지 않고, 라인이 두 조각으로 끊어져 보인다.
 *
 * <p>이 단계는 차수 1인 접합점을 배관 선분 위에서 찾아 모재와 이어준다.
 */
public final class BranchAttacher {

    private BranchAttacher() {
    }

    /**
     * 아직 짝을 찾지 못한 접합점을 모재 배관 선분에 붙인다.
     * 붙은 접합점은 {@link Joint#isAttachedToHost()} 가 true 가 되어 미연결로 보고되지 않는다.
     */
    public static List<SegmentAttachment> attach(Pipeline pipeline, List<Joint> joints, double toleranceMm) {
        List<PipingComponent> hosts = pipeline.components().stream()
                .filter(c -> c.type().isRoutingComponent())
                .filter(c -> endPorts(c).size() == 2)
                .toList();

        List<SegmentAttachment> out = new ArrayList<>();
        for (Joint j : joints) {
            if (j.degree() != 1) continue;

            for (PipingComponent host : hosts) {
                // 자기 자신에게는 붙지 않는다
                if (j.ports().get(0).component() == host) continue;

                List<Port> ends = endPorts(host);
                Double t = parameterOnSegment(
                        ends.get(0).position(), ends.get(1).position(), j.position(), toleranceMm);
                if (t == null) continue;

                j.markAttachedToHost();
                out.add(new SegmentAttachment(j, host, t));
                break;
            }
        }
        return out;
    }

    private static List<Port> endPorts(PipingComponent c) {
        return c.ports().stream().filter(p -> p.kind() == PortKind.END).toList();
    }

    /**
     * 점 P 가 선분 AB 의 <b>내부</b>에 있으면 위치 파라미터 t 를, 아니면 null 을 반환한다.
     * 끝점 근처는 제외한다 — 그건 일반 접합점 병합이 이미 처리했다.
     */
    static Double parameterOnSegment(Vec3 a, Vec3 b, Vec3 p, double toleranceMm) {
        Vec3 ab = b.minus(a);
        double len2 = ab.dot(ab);
        if (len2 < 1e-9) return null;

        double t = p.minus(a).dot(ab) / len2;
        double len = Math.sqrt(len2);
        // 끝점에서 허용오차만큼은 내부로 치지 않는다
        double margin = toleranceMm / len;
        if (t <= margin || t >= 1 - margin) return null;

        Vec3 foot = a.plus(ab.scale(t));
        return foot.distanceTo(p) <= toleranceMm ? t : null;
    }
}
