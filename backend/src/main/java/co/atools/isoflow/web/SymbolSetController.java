// SymbolSetController.java — 사용자 정의 심볼 세트 업로드·목록·삭제
package co.atools.isoflow.web;

import co.atools.isoflow.symbolset.SymbolSetService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/symbol-sets")
public class SymbolSetController {

    private final SymbolSetService service;

    public SymbolSetController(SymbolSetService service) {
        this.service = service;
    }

    @GetMapping
    public List<SymbolSetService.Summary> list() {
        return service.list();
    }

    /**
     * 심볼 JSON 파일을 올린다. 같은 이름이면 내용을 갈아끼운다.
     * 검증에 실패하면 저장하지 않고 {@code INVALID_SYMBOL_SET} 으로 거절한다.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SymbolSetService.Summary upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description)
            throws IOException {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("EMPTY_FILE");
        }
        // 심볼 JSON 은 사람이 쓰는 파일이라 UTF-8 로 고정한다
        String json = new String(file.getBytes(), StandardCharsets.UTF_8);
        return service.upload(name, description, json);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(SymbolSetService.parseId(id));
        return ResponseEntity.noContent().build();
    }
}
