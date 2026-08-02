// ExportService.java — 등각도를 DXF / PDF / CSV 로 내보낸다
package co.atools.isoflow.isometric;

import co.atools.isoflow.engine.IsometricGenerator;
import co.atools.isoflow.engine.PipelineLoader;
import co.atools.isoflow.pipeline.ImportService;
import co.atools.isoflow.engine.scene.Scene2D;
import co.atools.isoflow.engine.style.IsoStyle;
import co.atools.isoflow.export.dxf.DxfWriter;
import co.atools.isoflow.export.pdf.PdfRenderer;
import co.atools.isoflow.export.sheet.SheetComposer;
import co.atools.isoflow.export.table.CsvWriter;
import co.atools.isoflow.engine.table.DrawingTable;
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
public class ExportService {

    private final co.atools.isoflow.symbolset.SymbolSetService symbolSets;

    public ExportService(co.atools.isoflow.symbolset.SymbolSetService symbolSets) {
        this.symbolSets = symbolSets;
    }

    /**
     * @param bytes       파일 내용
     * @param fileName    내려줄 파일명
     * @param contentType MIME 타입
     */
    public record Exported(byte[] bytes, String fileName, String contentType) {
    }

    /**
     * <b>캐시된 Scene 을 재사용하지 않고 매번 다시 생성한다.</b>
     * 화면 렌더 캐시를 도면 출력의 원천으로 삼으면 뷰어 쪽 임시 변경이 도면에 새어 들어간다.
     *
     * @param format dxf / pdf / bom / cutlist / weldlist
     */
    public Exported export(String fileName, InputStream in, String exportFormat) throws IOException {
        return export(fileName, in, exportFormat, IsoStyle.defaults());
    }

    public Exported export(String fileName, InputStream in, String exportFormat, IsoStyle rawStyle)
            throws IOException {
        return export(fileName, in, exportFormat, rawStyle, null);
    }

    /** 스타일 설정과 사용자 심볼 세트를 적용해 내보낸다 */
    public Exported export(String fileName, InputStream in, String exportFormat, IsoStyle rawStyle,
                           java.util.UUID symbolSetId) throws IOException {
        IsoStyle style = (rawStyle == null ? IsoStyle.defaults() : rawStyle).withDefaults();
        PipelineLoader.Format format = PipelineLoader.formatOf(fileName);
        if (format == null) {
            // 확장자를 모르면 빈 도면이 조용히 나온다 — 명시적으로 거절한다
            throw ApiException.badRequest("UNSUPPORTED_FORMAT", "fileName", String.valueOf(fileName));
        }

        String base = baseName(fileName);
        try (Reader reader = new InputStreamReader(in, ImportService.charsetFor(format))) {
            IsometricGenerator.Generated g = IsometricGenerator.generate(
                    base, reader, format, style, null, symbolSets.resolve(symbolSetId));
            // 표는 엔진이 압축 전에 집계해 둔 것을 쓴다 — 여기서 다시 재면 압축된 길이가 잡힌다
            List<DrawingTable> tables = g.tables();

            List<Scene2D> composed = sheets(g, tables, style);
            return switch (exportFormat == null ? "" : exportFormat.toLowerCase(Locale.ROOT)) {
                // DXF 는 페이지가 없으므로 여러 장을 나란히 배치해 한 파일로 낸다
                case "dxf" -> new Exported(
                        DxfWriter.write(SheetComposer.sideBySide(composed, SHEET_GAP_MM)),
                        base + ".dxf", "application/dxf");
                case "pdf" -> new Exported(
                        PdfRenderer.render(composed,
                                style.sheet().resolvedWidthMm(), style.sheet().resolvedHeightMm()),
                        base + ".pdf", "application/pdf");
                case "bom" -> csv(tables, DrawingTable.BOM, base + "-bom.csv");
                case "cutlist" -> csv(tables, DrawingTable.CUT_LIST, base + "-cutlist.csv");
                case "weldlist" -> csv(tables, DrawingTable.WELD_LIST, base + "-weldlist.csv");
                default -> throw ApiException.badRequest("UNSUPPORTED_EXPORT_FORMAT", "format", exportFormat);
            };
        }
    }

    /** DXF 에서 시트 사이에 둘 간격 */
    private static final double SHEET_GAP_MM = 20;

    /**
     * 용지에 앉힌 시트들 — DXF 와 PDF 가 같은 도면을 쓴다.
     * 자재표는 <b>1장에만</b> 얹는다. 장마다 같은 표를 반복하면 발주 수량으로 오독된다.
     */
    private static List<Scene2D> sheets(IsometricGenerator.Generated g, List<DrawingTable> tables,
                                        IsoStyle style) {
        int total = g.sheetCount();
        List<Scene2D> out = new java.util.ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            out.add(SheetComposer.compose(g.sheets().get(i),
                    co.atools.isoflow.export.sheet.TitleBlock.of(g.pipeline(), i + 1, total),
                    i == 0 ? tables : List.of(), style));
        }
        return out;
    }

    private Exported csv(List<DrawingTable> tables, String kind, String fileName) {
        DrawingTable t = tables.stream().filter(x -> x.kind().equals(kind)).findFirst()
                .orElseThrow(() -> ApiException.badRequest("EMPTY_TABLE", "table", kind));
        return new Exported(CsvWriter.toCsv(t), fileName, "text/csv");
    }

    private static String baseName(String fileName) {
        String name = fileName == null ? "isometric" : fileName;
        int dot = name.lastIndexOf('.');
        String base = dot < 0 ? name : name.substring(0, dot);
        return base.isBlank() ? "isometric" : base;
    }
}
