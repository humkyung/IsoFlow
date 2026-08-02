// PipelineImportController.java — PCF/IDF 업로드 → Scene3D + 진단 반환
package co.atools.isoflow.web;

import co.atools.isoflow.pipeline.ImportService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/pipelines")
public class PipelineImportController {

    private final ImportService importService;

    public PipelineImportController(ImportService importService) {
        this.importService = importService;
    }

    /**
     * 파일 하나를 파싱해 3D Scene 을 돌려준다.
     * 현재는 저장하지 않는다 — DB 준비 후 이 응답을 그대로 영속화 계층으로 감싼다.
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportService.ImportResult importFile(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("EMPTY_FILE");
        }
        return importService.importFile(file.getOriginalFilename(), file.getInputStream());
    }
}
