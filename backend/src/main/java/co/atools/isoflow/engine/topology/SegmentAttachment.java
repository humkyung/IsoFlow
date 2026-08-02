// SegmentAttachment.java — 배관 중간에 붙는 분기(올렛 등)와 그 모재 배관의 연결
package co.atools.isoflow.engine.topology;

import co.atools.isoflow.engine.model.PipingComponent;

/**
 * @param joint 분기 컴포넌트의 접속점
 * @param host  분기가 올라탄 모재 배관 (끝점이 아니라 중간에 붙는다)
 * @param t     모재 배관 위의 위치 파라미터 (0=시작 끝점, 1=끝 끝점)
 */
public record SegmentAttachment(Joint joint, PipingComponent host, double t) {
}
