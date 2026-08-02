// Topology.java — 위상 해석 결과. 접합점 목록과 연결 그래프를 담는다
package co.atools.isoflow.engine.topology;

import java.util.List;

public record Topology(List<Joint> joints, PipeGraph graph, List<SegmentAttachment> attachments) {

    /** 라인의 끝도 아니고 모재에 올라탄 것도 아닌데 홀로 떠 있는 접합점 */
    public List<Joint> danglingJoints() {
        return joints.stream()
                .filter(j -> j.degree() == 1 && !j.isTerminator() && !j.isAttachedToHost())
                .toList();
    }

    /** 종단으로 선언된 접합점 (다른 라인/장비 접속 또는 열린 끝) */
    public List<Joint> terminatorJoints() {
        return joints.stream().filter(Joint::isTerminator).toList();
    }
}
