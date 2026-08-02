// ApiException.java — 언어중립 오류. 문구가 아니라 코드 + 보간 파라미터를 담는다
package co.atools.isoflow.web;

import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 사용자 표시 문구는 프론트가 {@code err.<code>} 키로 만든다(CLAUDE.md 규약).
 * 백엔드는 안정적 코드와 보간 파라미터만 내려보낸다.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> params;

    public ApiException(HttpStatus status, String code, Object... keyValuePairs) {
        super(code);
        this.status = status;
        this.code = code;
        Map<String, Object> p = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValuePairs.length; i += 2) {
            p.put(String.valueOf(keyValuePairs[i]), keyValuePairs[i + 1]);
        }
        this.params = p;
    }

    public static ApiException badRequest(String code, Object... kv) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, kv);
    }

    public static ApiException notFound(String code, Object... kv) {
        return new ApiException(HttpStatus.NOT_FOUND, code, kv);
    }

    /** 기능은 있으나 지금 구성에서 쓸 수 없을 때 (예: DB 없이 기동한 프로파일) */
    public static ApiException unavailable(String code, Object... kv) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, code, kv);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Map<String, Object> params() {
        return params;
    }
}
