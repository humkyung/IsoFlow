// IsometricGenerator.java — PCF → 등각도(Scene2D) 생성 진입점. 압축 → 작도 순서로 수행한다
package co.atools.isoflow.engine;

import co.atools.isoflow.engine.diagnostic.DiagnosticCodes;
import co.atools.isoflow.engine.diagnostic.Diagnostics;
import co.atools.isoflow.engine.geometry.IsoProjection;
import co.atools.isoflow.engine.geometry.LengthCompressor;
import co.atools.isoflow.engine.layout.DimensionPlanner;
import co.atools.isoflow.engine.layout.DimensionRun;
import co.atools.isoflow.engine.layout.SheetSplitter;
import co.atools.isoflow.engine.model.Pipeline;
import co.atools.isoflow.engine.scene.Scene2D;
import co.atools.isoflow.engine.scene.Scene2dBuilder;
import co.atools.isoflow.engine.style.IsoStyle;
import co.atools.isoflow.engine.symbol.SymbolSet;
import co.atools.isoflow.engine.table.DrawingTable;
import co.atools.isoflow.engine.table.TableBuilders;

import java.io.IOException;
import java.io.Reader;
import java.util.List;

public final class IsometricGenerator {

    /**
     * @param sheets      등각도 <b>내용</b> 시트들 (배관 좌표). 용지에 앉히는 것은 export 계층 책임이다.
     *                    나누지 않으면 한 장이다
     * @param pipeline    IR. <b>좌표는 압축되어 있다</b> — 길이를 다시 재면 안 된다
     * @param tables      자재표·절단·용접 리스트. <b>압축 전 실제 길이</b>로 집계된 것.
     *                    표는 라인 전체 기준이라 시트별로 나누지 않는다
     * @param diagnostics 파싱·위상·작도에서 나온 모든 진단
     */
    public record Generated(List<Scene2D> sheets, Pipeline pipeline,
                            List<DrawingTable> tables, Diagnostics diagnostics) {

        /** 첫 장 — 한 장짜리 도면을 다루는 코드가 대부분이라 편의로 둔다 */
        public Scene2D scene() {
            return sheets.get(0);
        }

        public int sheetCount() {
            return sheets.size();
        }
    }

    private IsometricGenerator() {
    }

    /**
     * PCF 를 읽어 등각도를 만든다.
     *
     * <p><b>순서가 중요하다.</b> 치수 계획을 <i>압축 전에</i> 만들어야 실제 길이가 찍힌다.
     * 압축은 좌표를 바꾸는 표시용 변환이므로, 그 뒤에 길이를 재면 도면에 가짜 치수가 들어간다.
     * 치수 계획은 점을 Port 참조로 들고 있어 압축 후 위치를 따라간다.
     */
    public static Generated generate(String sceneId, Reader source, boolean compressLengths)
            throws IOException {
        return generate(sceneId, source, compressLengths, PipelineLoader.Format.PCF);
    }

    /** 포맷을 지정해 생성한다 (IDF 포함) */
    public static Generated generate(String sceneId, Reader source, boolean compressLengths,
                                     PipelineLoader.Format format) throws IOException {
        return generate(sceneId, source, format, IsoStyle.defaults(), compressLengths);
    }

    /**
     * 스타일 설정을 적용해 생성한다.
     *
     * @param compressOverride null 이면 스타일의 압축 설정을 따른다
     */
    public static Generated generate(String sceneId, Reader source, PipelineLoader.Format format,
                                     IsoStyle rawStyle, Boolean compressOverride) throws IOException {
        return generate(sceneId, source, format, rawStyle, compressOverride, null);
    }

    /**
     * 사용자 심볼 세트까지 지정해 생성한다.
     *
     * @param symbolSet 사용자 심볼이 덮인 세트. null 이면 기본 세트
     */
    public static Generated generate(String sceneId, Reader source, PipelineLoader.Format format,
                                     IsoStyle rawStyle, Boolean compressOverride,
                                     SymbolSet symbolSet) throws IOException {
        // null = 기준을 지정하지 않음 → 스타일의 라벨 밀도 설정을 따른다
        return generate(sceneId, source, format, rawStyle, compressOverride, symbolSet, null);
    }

    /**
     * 시트 분할 기준까지 지정해 생성한다.
     *
     * @param splitCriterion 한 장에 담을 수 있는지 판정한다.
     *                       null 이면 스타일의 라벨 밀도 기준을 따르고,
     *                       {@link SheetSplitter.Criterion#never()} 를 주면 항상 한 장
     */
    public static Generated generate(String sceneId, Reader source, PipelineLoader.Format format,
                                     IsoStyle rawStyle, Boolean compressOverride,
                                     SymbolSet symbolSet, SheetSplitter.Criterion splitCriterion)
            throws IOException {
        IsoStyle style = (rawStyle == null ? IsoStyle.defaults() : rawStyle).withDefaults();
        boolean compressLengths = compressOverride != null
                ? compressOverride : Boolean.TRUE.equals(style.compression().enabled());
        PipelineLoader.Loaded loaded = PipelineLoader.load(format, source);
        Diagnostics diag = loaded.diagnostics();

        // 1) 실제 길이가 필요한 것들을 먼저 끝낸다 (압축 전)
        //    치수뿐 아니라 자재표도 여기서 집계해야 한다 —
        //    압축 후에 재면 BOM 이 발주 수량을 축소 보고한다
        List<DimensionRun> dimensions = DimensionPlanner.plan(loaded.pipeline(), style);
        List<DrawingTable> tables = TableBuilders.all(loaded.pipeline());

        // 2) 표시용 좌표 압축
        if (compressLengths) {
            LengthCompressor.Result r = LengthCompressor.of(style).compress(loaded.pipeline());
            if (!r.skippedAxes().isEmpty()) {
                diag.info(DiagnosticCodes.COMPRESSION_SKIPPED, 0,
                        "axes", r.skippedAxes().toString(), "reason", "skew-segments");
            }
        }

        // 3~4) 시트 분할 + 작도.
        //      나눌지 말지는 그려 보기 전에는 알 수 없다(라벨이 몇 개나 겹치는지가 기준이다).
        //      그래서 한 장으로 그려 보고, 너무 빽빽하면 장수를 늘려 다시 그린다.
        Drawn drawn = drawSheets(sceneId, loaded.pipeline(), dimensions, style, symbolSet,
                splitCriterion, diag);
        if (drawn.sheets().size() > 1) {
            diag.info(DiagnosticCodes.SHEET_SPLIT, 0,
                    "sheets", String.valueOf(drawn.sheets().size()));
        }
        return new Generated(drawn.sheets(), loaded.pipeline(), tables, diag);
    }

    /** 작도 결과와 그 도면이 얼마나 빽빽한지. diagnostics 는 채택될 때만 옮겨 담는다 */
    private record Drawn(List<Scene2D> sheets, double worstCrowding, Diagnostics diagnostics) {
    }

    /**
     * 라벨 밀도가 기준 아래로 내려갈 때까지 장수를 늘려 가며 그린다.
     *
     * <p>기준을 끄면(=null) 한 번만 그리고 끝낸다 — 기존 동작 그대로다.
     * 장수를 늘려도 나아지지 않으면 <b>더 나은 쪽을 남기고 멈춘다</b>.
     */
    private static Drawn drawSheets(String sceneId, Pipeline pipeline, List<DimensionRun> dimensions,
                                    IsoStyle style, SymbolSet symbolSet,
                                    SheetSplitter.Criterion explicitCriterion, Diagnostics diag) {
        Double limit = style.sheet().maxLabelCrowding();
        int maxSheets = Math.max(1, style.sheet().maxSheets() == null ? 1 : style.sheet().maxSheets());

        // 기준을 직접 준 경우(테스트·배치)는 그대로 따른다
        if (explicitCriterion != null) {
            return adopt(drawOnce(sceneId, pipeline, dimensions, style, symbolSet,
                    explicitCriterion), diag);
        }
        Drawn best = drawOnce(sceneId, pipeline, dimensions, style, symbolSet,
                SheetSplitter.Criterion.never());
        if (limit == null || limit <= 0 || best.worstCrowding() <= limit) return adopt(best, diag);

        double extent = extentOf(pipeline);
        for (int k = 2; k <= maxSheets; k++) {
            Drawn candidate = drawOnce(sceneId, pipeline, dimensions, style, symbolSet,
                    SheetSplitter.Criterion.maxExtent(extent / k));
            // 폭을 1/k 로 줄여도 실제 장수는 그보다 많이 나올 수 있다 —
            // 상한은 k 가 아니라 나온 장수로 지켜야 한다
            if (candidate.sheets().size() > maxSheets) break;
            // 더 나눴는데 오히려 나빠지면 되돌린다
            if (candidate.worstCrowding() < best.worstCrowding()) best = candidate;
            if (candidate.worstCrowding() <= limit) return adopt(candidate, diag);
        }
        return adopt(best, diag);
    }

    /** 채택한 결과의 진단만 본 진단으로 옮긴다 — 버려진 시행의 경고가 섞이면 안 된다 */
    private static Drawn adopt(Drawn drawn, Diagnostics diag) {
        drawn.diagnostics().items().forEach(diag::add);
        return drawn;
    }

    /** 주어진 기준으로 한 번 나눠 그린다 */
    private static Drawn drawOnce(String sceneId, Pipeline pipeline, List<DimensionRun> dimensions,
                                  IsoStyle style, SymbolSet symbolSet,
                                  SheetSplitter.Criterion criterion) {
        List<SheetSplitter.Sheet> split =
                SheetSplitter.split(pipeline, dimensions, IsoProjection.DEFAULT, criterion);

        // 여러 번 시도하므로 진단은 따로 받는다 — 채택된 것만 본 진단에 옮긴다
        Diagnostics scratch = new Diagnostics();

        List<Scene2D> sheets = new java.util.ArrayList<>(split.size());
        double worst = 0;
        for (int i = 0; i < split.size(); i++) {
            SheetSplitter.Sheet s = split.get(i);
            String id = split.size() == 1 ? sceneId : sceneId + "-s" + (i + 1);
            Scene2dBuilder builder = Scene2dBuilder.standard(scratch, style, symbolSet);
            sheets.add(builder.build(id, pipeline, s.components(), s.dimensions(), s.links(), i + 1));
            worst = Math.max(worst, builder.labelStats().collisionRatio());
        }
        return new Drawn(List.copyOf(sheets), worst, scratch);
    }

    /** 투영하지 않은 대략적인 도면 크기 — 분할 폭을 정하는 데만 쓴다 */
    private static double extentOf(Pipeline pipeline) {
        double[] b = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        boolean any = false;
        for (var c : pipeline.components()) {
            for (var p : c.ports()) {
                double[] q = IsoProjection.DEFAULT.project(p.position());
                b[0] = Math.min(b[0], q[0]);
                b[1] = Math.min(b[1], q[1]);
                b[2] = Math.max(b[2], q[0]);
                b[3] = Math.max(b[3], q[1]);
                any = true;
            }
        }
        return any ? Math.max(b[2] - b[0], b[3] - b[1]) : 0;
    }
}
