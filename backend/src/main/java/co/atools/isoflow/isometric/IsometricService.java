// IsometricService.java — 업로드된 PCF 로 등각도를 생성한다 (현재는 무상태 — 영속화는 DB 준비 후)
package co.atools.isoflow.isometric;

import co.atools.isoflow.engine.IsometricGenerator;
import co.atools.isoflow.engine.PipelineLoader;
import co.atools.isoflow.pipeline.ImportService;
import co.atools.isoflow.engine.diagnostic.Diagnostic;
import co.atools.isoflow.engine.scene.Scene2D;
import co.atools.isoflow.engine.style.IsoStyle;
import co.atools.isoflow.export.sheet.SheetComposer;
import co.atools.isoflow.export.sheet.TitleBlock;
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
public class IsometricService {

    private final co.atools.isoflow.symbolset.SymbolSetService symbolSets;

    public IsometricService(co.atools.isoflow.symbolset.SymbolSetService symbolSets) {
        this.symbolSets = symbolSets;
    }

    /**
     * @param scenes      등각도 시트들. 나누지 않으면 1개
     * @param diagnostics 파싱·위상·작도 진단
     * @param fileName    원본 파일명
     */
    public record GenerateResult(List<Scene2D> scenes, List<Diagnostic> diagnostics, String fileName) {
    }

    public GenerateResult generate(String fileName, InputStream in, Boolean compressLengths,
                                   IsoStyle style) throws IOException {
        return generate(fileName, in, compressLengths, style, null);
    }

    /**
     * PCF 를 읽어 등각도를 만든다.
     *
     * @param symbolSetId 사용자 심볼 세트 id. null 이면 기본 세트
     */
    public GenerateResult generate(String fileName, InputStream in, Boolean compressLengths,
                                   IsoStyle style, java.util.UUID symbolSetId) throws IOException {
        String name = fileName == null ? "" : fileName;
        int dot = name.lastIndexOf('.');
        PipelineLoader.Format format = PipelineLoader.formatOf(name);
        if (format == null) {
            String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
            throw ApiException.badRequest("UNSUPPORTED_FORMAT", "fileName", name, "extension", ext);
        }

        try (Reader reader = new InputStreamReader(in, ImportService.charsetFor(format))) {
            String sceneId = dot < 0 ? name : name.substring(0, dot);
            IsometricGenerator.Generated g = IsometricGenerator.generate(
                    sceneId.isBlank() ? "iso" : sceneId, reader, format, style, compressLengths,
                    symbolSets.resolve(symbolSetId));

            // 뷰어도 출력과 같은 도면(도곽·타이틀·자재표 포함)을 보게 한다 —
            // 화면과 종이가 다르면 검토가 무의미해진다.
            // 자재표는 내보내기와 같게 1장에만 얹는다
            int total = g.sheetCount();
            List<Scene2D> scenes = new java.util.ArrayList<>(total);
            for (int i = 0; i < total; i++) {
                scenes.add(SheetComposer.compose(g.sheets().get(i),
                        TitleBlock.of(g.pipeline(), i + 1, total),
                        i == 0 ? g.tables() : List.of(), style));
            }

            return new GenerateResult(List.copyOf(scenes), g.diagnostics().items(), name);
        }
    }
}
