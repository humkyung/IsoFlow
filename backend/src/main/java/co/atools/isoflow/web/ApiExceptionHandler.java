// ApiExceptionHandler.java — 오류를 { code, <파라미터…>, error } 형태로 변환한다. 번역은 프론트 책임
package co.atools.isoflow.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link ResponseEntityExceptionHandler} 를 상속해 프레임워크 예외(405, 415, 400 …)는
 * Spring 의 기본 처리를 그대로 쓴다. 이걸 하지 않고 {@code Exception} 만 잡으면
 * 405 Method Not Allowed 까지 500 으로 뭉개진다.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException e) {
        return ResponseEntity.status(e.status()).body(body(e.code(), e.params(), e.code()));
    }

    /**
     * 업로드 크기 초과. 부모가 이미 이 예외를 처리하므로 {@code @ExceptionHandler} 를 새로 달면
     * 매핑이 중복돼 컨텍스트가 뜨지 않는다 — 반드시 오버라이드로 덮어써야 한다.
     */
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(body("FILE_TOO_LARGE", Map.of(), "file too large"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception e) throws Exception {
        // 프레임워크가 스스로 표현할 수 있는 예외는 상위 처리에 맡긴다
        if (e instanceof ErrorResponse) throw e;
        // 예외 원문은 서버 로그에만 남기고 사용자 표시 필드에 싣지 않는다
        log.error("처리되지 않은 예외", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body("INTERNAL_ERROR", Map.of(), "internal error"));
    }

    /** {@code { code, <보간 파라미터…>, error }} 형태로 만든다 */
    private static Map<String, Object> body(String code, Map<String, Object> params, String fallback) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.putAll(params);
        // error 는 코드 매핑이 없을 때만 쓰는 최후 폴백이다
        m.put("error", fallback);
        return m;
    }
}
