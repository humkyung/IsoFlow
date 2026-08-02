// ImportService.java — 업로드된 PCF/IDF 를 파싱해 Scene3D 로 만든다 (현재는 무상태 — 영속화는 DB 준비 후)
package co.atools.isoflow.pipeline;

import co.atools.isoflow.engine.PipelineLoader;
import co.atools.isoflow.engine.diagnostic.Diagnostic;
import co.atools.isoflow.engine.scene.Scene3D;
import co.atools.isoflow.engine.scene.Scene3dBuilder;
import co.atools.isoflow.web.ApiException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@Service
public class ImportService {

    /**
     * @param scene       3D 뷰어 계약
     * @param diagnostics 파싱·위상 해석 진단 (문구가 아니라 코드 + 파라미터)
     * @param fileName    원본 파일명
     */
    public record ImportResult(Scene3D scene, List<Diagnostic> diagnostics, String fileName) {
    }

    /** 파일명 확장자로 포맷을 정하고 파싱한다 */
    public ImportResult importFile(String fileName, InputStream in) throws IOException {
        String name = fileName == null ? "" : fileName;
        PipelineLoader.Format format = PipelineLoader.formatOf(name);
        if (format == null) {
            // 조용히 빈 결과를 주지 않고 명시적으로 거절한다
            throw ApiException.badRequest("UNSUPPORTED_FORMAT",
                    "fileName", name, "extension", extensionOf(name));
        }

        try (Reader reader = new InputStreamReader(in, charsetFor(format))) {
            PipelineLoader.Loaded loaded = PipelineLoader.load(format, reader);
            Scene3D scene = Scene3dBuilder.build(
                    sceneIdFor(name), loaded.pipeline(), loaded.diagnostics());
            return new ImportResult(scene, loaded.diagnostics().items(), name);
        }
    }

    /** IDF 는 PDS 계열이라 latin-1, PCF 는 UTF-8 로 읽는다 */
    public static java.nio.charset.Charset charsetFor(PipelineLoader.Format format) {
        return format == PipelineLoader.Format.IDF
                ? java.nio.charset.StandardCharsets.ISO_8859_1
                : StandardCharsets.UTF_8;
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** 파일명에서 Scene id 를 만든다 (확장자 제거) */
    private static String sceneIdFor(String name) {
        int dot = name.lastIndexOf('.');
        String base = dot < 0 ? name : name.substring(0, dot);
        return base.isBlank() ? "scene" : base;
    }
}
