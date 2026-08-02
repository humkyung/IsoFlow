// ParseResult.java — 파싱 결과. IR 과 진단을 함께 돌려준다(예외로 중단하지 않는다)
package co.atools.isoflow.engine.parser;

import co.atools.isoflow.engine.diagnostic.Diagnostics;
import co.atools.isoflow.engine.model.Pipeline;

/**
 * 파서는 문제를 만나도 예외를 던지지 않는다.
 * 읽을 수 있는 만큼 IR 을 만들고, 못 읽은 부분은 {@code diagnostics} 에 남긴다.
 * 조용히 버리는 것만 금지다.
 */
public record ParseResult(Pipeline pipeline, Diagnostics diagnostics) {
}
