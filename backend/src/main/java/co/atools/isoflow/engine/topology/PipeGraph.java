// PipeGraph.java — 컴포넌트를 정점, 접합점을 간선으로 하는 연결 그래프 (JGraphT)
package co.atools.isoflow.engine.topology;

import co.atools.isoflow.engine.model.Pipeline;
import co.atools.isoflow.engine.model.PipingComponent;
import org.jgrapht.Graph;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.Multigraph;

import java.util.List;
import java.util.Set;

public final class PipeGraph {

    /** 어느 접합점에서 이어졌는지 기억하는 간선 */
    public static final class JointEdge extends DefaultEdge {
        private final String jointKey;

        JointEdge(String jointKey) {
            this.jointKey = jointKey;
        }

        public String jointKey() {
            return jointKey;
        }
    }

    private final Graph<PipingComponent, JointEdge> graph = new Multigraph<>(JointEdge.class);

    /** 경로 컴포넌트를 정점으로, 접합점과 중간 분기를 간선으로 그래프를 만든다 */
    public static PipeGraph build(Pipeline pipeline, List<Joint> joints, List<SegmentAttachment> attachments) {
        PipeGraph g = new PipeGraph();
        for (PipingComponent c : pipeline.components()) {
            if (c.type().isRoutingComponent()) g.graph.addVertex(c);
        }
        for (Joint j : joints) {
            List<PortRef> refs = j.ports();
            if (refs.size() < 2) continue;
            // 첫 포트를 허브로 삼아 나머지를 잇는다 — 연결성 판정에는 이것으로 충분하다
            PipingComponent hub = refs.get(0).component();
            for (int i = 1; i < refs.size(); i++) {
                PipingComponent other = refs.get(i).component();
                if (hub == other) continue;   // 같은 컴포넌트의 두 포트가 겹친 경우(길이 0)
                g.graph.addEdge(hub, other, new JointEdge(j.key()));
            }
        }
        // 배관 중간에 올라탄 분기(올렛 등)를 모재와 잇는다
        for (SegmentAttachment a : attachments) {
            PipingComponent branch = a.joint().ports().get(0).component();
            if (branch == a.host()) continue;
            g.graph.addEdge(branch, a.host(), new JointEdge(a.joint().key()));
        }
        return g;
    }

    public Graph<PipingComponent, JointEdge> graph() {
        return graph;
    }

    public int vertexCount() {
        return graph.vertexSet().size();
    }

    public int edgeCount() {
        return graph.edgeSet().size();
    }

    /** 서로 이어지지 않은 조각들. 정상이면 1개다 */
    public List<Set<PipingComponent>> connectedSets() {
        return new ConnectivityInspector<>(graph).connectedSets();
    }
}
