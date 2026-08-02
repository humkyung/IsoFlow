// CsvWriter.java — 표를 CSV 로 내보낸다. 엑셀에서 한글이 깨지지 않도록 UTF-8 BOM 을 붙인다
package co.atools.isoflow.export.table;

import co.atools.isoflow.engine.table.DrawingTable;

import java.nio.charset.StandardCharsets;

public final class CsvWriter {

    /** 엑셀이 UTF-8 로 인식하게 하는 BOM. 없으면 한글 자재 설명이 깨진다 */
    private static final String BOM_MARK = "﻿";

    private CsvWriter() {
    }

    public static byte[] toCsv(DrawingTable table) {
        StringBuilder sb = new StringBuilder(BOM_MARK);
        appendRow(sb, table.headers());
        for (java.util.List<String> row : table.rows()) appendRow(sb, row);
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendRow(StringBuilder sb, java.util.List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(escape(cells.get(i)));
        }
        sb.append("\r\n");   // 엑셀 호환을 위해 CRLF
    }

    /** 쉼표·따옴표·개행이 있으면 따옴표로 감싸고 내부 따옴표는 두 번 쓴다 */
    private static String escape(String v) {
        if (v == null) return "";
        boolean needsQuote = v.indexOf(',') >= 0 || v.indexOf('"') >= 0
                || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0;
        if (!needsQuote) return v;
        return '"' + v.replace("\"", "\"\"") + '"';
    }
}
