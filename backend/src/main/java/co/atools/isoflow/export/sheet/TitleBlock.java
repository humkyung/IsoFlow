// TitleBlock.java — 타이틀 블록에 들어갈 항목
package co.atools.isoflow.export.sheet;

import co.atools.isoflow.engine.model.Pipeline;

public record TitleBlock(String lineNumber, String pipingSpec, String area,
                         String revision, String sheet) {

    /** IR 에서 타이틀 블록 항목을 뽑는다 */
    public static TitleBlock of(Pipeline pipeline, int sheetNo, int sheetCount) {
        return new TitleBlock(
                blank(pipeline.lineNumber(), "(NO LINE NUMBER)"),
                blank(pipeline.pipingSpec(), ""),
                blank(pipeline.area(), ""),
                blank(pipeline.revision(), ""),
                sheetNo + " / " + sheetCount);
    }

    private static String blank(String v, String fallback) {
        return (v == null || v.isBlank()) ? fallback : v;
    }
}
