// IsometricController.java — PCF 업로드 → 등각도(Scene2D) 생성
package co.atools.isoflow.web;

import co.atools.isoflow.engine.style.IsoStyle;
import co.atools.isoflow.isometric.ExportService;
import co.atools.isoflow.isometric.IsometricService;
import co.atools.isoflow.symbolset.SymbolSetService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

@RestController
@RequestMapping("/api/isometrics")
public class IsometricController {

    private final IsometricService service;
    private final ExportService exportService;
    private final ObjectMapper mapper;

    public IsometricController(IsometricService service, ExportService exportService,
                               ObjectMapper mapper) {
        this.service = service;
        this.exportService = exportService;
        this.mapper = mapper;
    }

    /** 요청에 실려 온 스타일 JSON 을 읽는다. 없으면 기본값 */
    private IsoStyle parseStyle(String json) {
        if (json == null || json.isBlank()) return IsoStyle.defaults();
        try {
            return mapper.readValue(json, IsoStyle.class).withDefaults();
        } catch (Exception e) {
            // 잘못된 설정을 조용히 무시하면 사용자가 왜 안 먹는지 알 수 없다.
            // Jackson 메시지 뒤에는 파서 내부 위치가 길게 붙으므로 첫 줄만 쓴다
            String msg = e.getMessage() == null ? "" : e.getMessage();
            int cut = msg.indexOf('\n');
            throw ApiException.badRequest("INVALID_STYLE", "reason", cut < 0 ? msg : msg.substring(0, cut));
        }
    }

    /**
     * @param compress 길이 압축(비축척) 적용 여부. 사선이 있는 축은 엔진이 알아서 건너뛴다
     */
    @PostMapping(value = "/generate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public IsometricService.GenerateResult generate(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "compress", required = false) Boolean compress,
            @RequestParam(value = "style", required = false) String style,
            @RequestParam(value = "symbolSetId", required = false) String symbolSetId)
            throws IOException {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("EMPTY_FILE");
        }
        return service.generate(file.getOriginalFilename(), file.getInputStream(),
                compress, parseStyle(style), SymbolSetService.parseId(symbolSetId));
    }

    /**
     * 등각도를 파일로 내보낸다.
     *
     * @param format dxf / pdf / bom / cutlist / weldlist
     */
    @PostMapping(value = "/export", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ByteArrayResource> export(
            @RequestParam("file") MultipartFile file,
            @RequestParam("format") String format,
            @RequestParam(value = "style", required = false) String style,
            @RequestParam(value = "symbolSetId", required = false) String symbolSetId)
            throws IOException {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("EMPTY_FILE");
        }
        ExportService.Exported e = exportService.export(
                file.getOriginalFilename(), file.getInputStream(), format, parseStyle(style),
                SymbolSetService.parseId(symbolSetId));

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(e.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + e.fileName() + "\"")
                .contentLength(e.bytes().length)
                .body(new ByteArrayResource(e.bytes()));
    }
}
