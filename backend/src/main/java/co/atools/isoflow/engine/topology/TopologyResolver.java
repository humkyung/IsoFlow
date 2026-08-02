// TopologyResolver.java — 접합점 병합 → 그래프 구축 → 무결성 진단까지 한 번에 수행한다
package co.atools.isoflow.engine.topology;

import co.atools.isoflow.engine.diagnostic.DiagnosticCodes;
import co.atools.isoflow.engine.diagnostic.Diagnostics;
import co.atools.isoflow.engine.model.ComponentType;
import co.atools.isoflow.engine.model.Pipeline;
import co.atools.isoflow.engine.model.PipingComponent;
import co.atools.isoflow.engine.model.Port;
import co.atools.isoflow.engine.model.PortKind;

import java.util.List;
import java.util.Set;

public final class TopologyResolver {

    /** 보어가 이 값(mm)보다 더 차이 나면 불일치로 본다. 호칭경이라 반올림 여유를 둔다 */
    private static final double BORE_EPSILON_MM = 0.5;

    private TopologyResolver() {
    }

    public static Topology resolve(Pipeline pipeline, Diagnostics diag) {
        return resolve(pipeline, diag, JointResolver.DEFAULT_TOLERANCE_MM);
    }

    public static Topology resolve(Pipeline pipeline, Diagnostics diag, double toleranceMm) {
        List<Joint> joints = new JointResolver(toleranceMm).run(pipeline);
        // 올렛처럼 배관 중간에 올라타는 분기는 좌표 일치로는 절대 연결되지 않는다 — 선분 위에서 찾아 붙인다
        List<SegmentAttachment> attachments = BranchAttacher.attach(pipeline, joints, toleranceMm);
        PipeGraph graph = PipeGraph.build(pipeline, joints, attachments);

        for (SegmentAttachment a : attachments) {
            diag.info(DiagnosticCodes.BRANCH_ATTACHED_MIDSPAN, 0,
                    "branch", a.joint().ports().get(0).component().label(),
                    "host", a.host().label());
        }
        checkJoints(joints, diag);
        checkZeroLengthPipes(pipeline, diag, toleranceMm);
        checkConnectivity(graph, diag);

        return new Topology(joints, graph, attachments);
    }

    /** 접합점별 검사 — 미연결, 보어 불일치, 과밀 */
    private static void checkJoints(List<Joint> joints, Diagnostics diag) {
        for (Joint j : joints) {
            if (j.degree() == 1 && !j.isTerminator() && !j.isAttachedToHost()) {
                PortRef ref = j.ports().get(0);
                diag.warn(DiagnosticCodes.DANGLING_PORT, 0,
                        "component", ref.component().label(),
                        "port", ref.port().kind().name(),
                        "joint", j.key());
            }
            // 분기점은 3방향이 정상이다. 배관이 분기 자리에서 쪼개지면
            // (파이프 끝 + 파이프 끝 + 올렛 접속점) 처럼 차수가 3이 된다.
            int expected = 2 + branchPortCount(j);
            if (j.degree() > expected) {
                diag.warn(DiagnosticCodes.OVERCROWDED_JOINT, 0,
                        "joint", j.key(), "degree", j.degree(), "expected", expected);
            }
            checkBore(j, diag);
        }
    }

    /**
     * 이 접합점에 모인 '분기 성격' 포트 수.
     * BRANCH 포트이거나, 올렛처럼 보어를 가진 CENTRE(모재 접속점)이면 분기로 센다.
     */
    private static int branchPortCount(Joint j) {
        int n = 0;
        for (PortRef ref : j.ports()) {
            PortKind k = ref.port().kind();
            if (k == PortKind.BRANCH1 || k == PortKind.BRANCH2) n++;
            else if (k == PortKind.CENTRE && ref.port().boreMm() != null) n++;
        }
        return n;
    }

    /** 같은 접합점에 모인 포트들의 보어가 일치하는지 본다 */
    private static void checkBore(Joint j, Diagnostics diag) {
        Double min = null, max = null;
        for (PortRef ref : j.ports()) {
            Double b = ref.port().boreMm();
            if (b == null) continue;
            min = (min == null) ? b : Math.min(min, b);
            max = (max == null) ? b : Math.max(max, b);
        }
        if (min != null && max - min > BORE_EPSILON_MM) {
            diag.warn(DiagnosticCodes.BORE_MISMATCH, 0,
                    "joint", j.key(), "minBoreMm", min, "maxBoreMm", max);
        }
    }

    /** 양 끝이 같은 자리인 파이프 — 도면에서 선이 그려지지 않는다 */
    private static void checkZeroLengthPipes(Pipeline pipeline, Diagnostics diag, double tol) {
        for (PipingComponent c : pipeline.components()) {
            if (c.type() != ComponentType.PIPE) continue;
            List<Port> ends = c.ports().stream().filter(p -> p.kind() == PortKind.END).toList();
            if (ends.size() < 2) continue;
            double len = ends.get(0).position().distanceTo(ends.get(1).position());
            if (len <= tol) {
                diag.warn(DiagnosticCodes.ZERO_LENGTH_PIPE, 0,
                        "component", c.label(), "lengthMm", len);
            }
        }
    }

    /** 라인이 여러 조각으로 끊겨 있으면 알린다 */
    private static void checkConnectivity(PipeGraph graph, Diagnostics diag) {
        List<Set<PipingComponent>> sets = graph.connectedSets();
        if (sets.size() > 1) {
            diag.warn(DiagnosticCodes.DISCONNECTED_SUBGRAPH, 0,
                    "parts", sets.size(),
                    "largest", sets.stream().mapToInt(Set::size).max().orElse(0));
        }
    }
}
