// CenterlineSegments.java — 컴포넌트가 도면에 실제로 그리는 중심선 구간들. 작도와 사선 판정이 같은 정의를 쓴다
package co.atools.isoflow.engine.geometry;

import co.atools.isoflow.engine.model.PipingComponent;
import co.atools.isoflow.engine.model.Port;
import co.atools.isoflow.engine.model.PortKind;
import co.atools.isoflow.engine.model.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * <b>END-POINT 두 개를 그냥 잇는 것은 틀리다.</b>
 * 엘보는 등각도에서 호가 아니라 모서리로 그려지므로 실제 구간은
 * {@code END0→CENTRE} 와 {@code CENTRE→END1} 두 개다.
 * 두 END 를 직접 이으면 언제나 대각선이 되어 사선으로 오검출된다.
 */
public final class CenterlineSegments {

    /** 구간 하나 */
    public record Segment(Vec3 from, Vec3 to) {
        public Vec3 delta() {
            return to.minus(from);
        }
    }

    private CenterlineSegments() {
    }

    /** 컴포넌트가 그리는 중심선 구간 목록 */
    public static List<Segment> of(PipingComponent c) {
        List<Port> ends = c.ports().stream().filter(p -> p.kind() == PortKind.END).toList();
        Optional<Vec3> centre = c.centre();
        List<Segment> out = new ArrayList<>(3);

        switch (c.type()) {
            case ELBOW, BEND -> {
                if (ends.size() < 2) break;
                if (centre.isPresent()) {
                    out.add(new Segment(ends.get(0).position(), centre.get()));
                    out.add(new Segment(centre.get(), ends.get(1).position()));
                } else {
                    out.add(new Segment(ends.get(0).position(), ends.get(1).position()));
                }
            }
            case TEE, CROSS -> {
                if (ends.size() >= 2) out.add(new Segment(ends.get(0).position(), ends.get(1).position()));
                Vec3 hub = centre.orElseGet(() -> ends.size() >= 2
                        ? ends.get(0).position().plus(ends.get(1).position()).scale(0.5) : null);
                if (hub != null) {
                    c.portOf(PortKind.BRANCH1, 0).ifPresent(b -> out.add(new Segment(hub, b.position())));
                    c.portOf(PortKind.BRANCH2, 0).ifPresent(b -> out.add(new Segment(hub, b.position())));
                }
            }
            case OLET -> centre.ifPresent(hub ->
                    c.portOf(PortKind.BRANCH1, 0).ifPresent(b -> out.add(new Segment(hub, b.position()))));
            case WELD, FLOW_ARROW, END_CONNECTION_PIPELINE, END_POSITION_OPEN, GASKET, BOLT, UNKNOWN -> {
                // 중심선을 만들지 않는다
            }
            default -> {
                if (ends.size() >= 2) out.add(new Segment(ends.get(0).position(), ends.get(1).position()));
            }
        }
        return out;
    }
}
