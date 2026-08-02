// Diagnostic.java — 파싱/위상 해석 중 발견한 문제 한 건. 문구가 아니라 코드 + 파라미터로 담는다
package co.atools.isoflow.engine.diagnostic;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 사용자 표시 문구는 프론트가 i18n 으로 만든다(CLAUDE.md 규약).
 * 엔진은 언어중립 {@code code} 와 보간 파라미터만 담는다.
 *
 * @param severity 심각도
 * @param code     안정적 식별자 — 프론트가 {@code diag.<code>} 키로 번역한다
 * @param params   보간 파라미터 (컴포넌트 라벨, 좌표, 값 등)
 * @param lineNo   원본 파일 줄 번호. 없으면 0
 */
public record Diagnostic(Severity severity, String code, Map<String, Object> params, int lineNo) {

    public enum Severity {
        /** 도면 생성을 막지는 않지만 확인이 필요하다 */
        WARNING,
        /** 도면이 틀릴 수 있다 — 반드시 확인해야 한다 */
        ERROR,
        /** 참고 정보 */
        INFO
    }

    public static Diagnostic of(Severity severity, String code, int lineNo, Object... keyValuePairs) {
        Map<String, Object> p = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValuePairs.length; i += 2) {
            // 값이 null 인 파라미터도 있다(SKEY 없는 컴포넌트 등). Map.copyOf 는 null 을 거부하므로
            // 빈 문자열로 바꿔 담는다 — 진단을 못 내는 것보다 낫다
            Object value = keyValuePairs[i + 1];
            p.put(String.valueOf(keyValuePairs[i]), value == null ? "" : value);
        }
        return new Diagnostic(severity, code, Collections.unmodifiableMap(p), lineNo);
    }

    public static Diagnostic warn(String code, int lineNo, Object... kv) {
        return of(Severity.WARNING, code, lineNo, kv);
    }

    public static Diagnostic error(String code, int lineNo, Object... kv) {
        return of(Severity.ERROR, code, lineNo, kv);
    }

    public static Diagnostic info(String code, int lineNo, Object... kv) {
        return of(Severity.INFO, code, lineNo, kv);
    }

    @Override
    public String toString() {
        return "[%s] %s %s%s".formatted(severity, code, params, lineNo > 0 ? " @line " + lineNo : "");
    }
}
