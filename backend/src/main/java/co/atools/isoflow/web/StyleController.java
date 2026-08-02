// StyleController.java — 등각도 스타일 설정 조회. 프론트가 기본값을 받아 편집 폼을 채운다
package co.atools.isoflow.web;

import co.atools.isoflow.engine.style.IsoStyle;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/styles")
public class StyleController {

    /**
     * 기본 스타일. 프로젝트별 저장은 DB 준비 후 {@code iso_style} 테이블로 옮긴다.
     * 지금은 생성/내보내기 요청에 스타일 JSON 을 함께 보내는 방식만 지원한다.
     */
    @GetMapping("/default")
    public IsoStyle defaults() {
        return IsoStyle.defaults();
    }
}
