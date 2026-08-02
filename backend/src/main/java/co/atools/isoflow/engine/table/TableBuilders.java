// TableBuilders.java — IR 에서 BOM · 절단 리스트 · 용접 리스트를 집계한다
package co.atools.isoflow.engine.table;

import co.atools.isoflow.engine.model.ComponentType;
import co.atools.isoflow.engine.model.MaterialItem;
import co.atools.isoflow.engine.model.Pipeline;
import co.atools.isoflow.engine.model.PipingComponent;
import co.atools.isoflow.engine.model.Port;
import co.atools.isoflow.engine.model.PortKind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TableBuilders {

    private TableBuilders() {
    }

    /** 집계 중간 상태 */
    private static final class Accum {
        String description;
        int count;
        double lengthMm;
        double weightKg;
        boolean byLength;
    }

    /**
     * 자재표.
     * <b>파이프는 개수가 아니라 길이로 센다</b> — 같은 품목코드의 파이프 3개를 "3 EA" 로 적으면
     * 발주에 쓸 수 없다. 나머지 부품은 개수(EA)다.
     */
    public static DrawingTable bom(Pipeline pipeline) {
        Map<String, Accum> byCode = new LinkedHashMap<>();

        for (PipingComponent c : pipeline.components()) {
            if (c.excludedFromBom()) continue;                 // MATERIAL-LIST EXCLUDE
            String code = c.itemCode();
            if (code == null || code.isBlank()) continue;

            Accum a = byCode.computeIfAbsent(code, k -> new Accum());
            if (a.description == null) {
                MaterialItem m = pipeline.materials().get(code);
                a.description = (m != null && m.description() != null)
                        ? m.description() : c.itemDescription();
            }
            if (c.weight() != null) a.weightKg += c.weight();

            if (c.type() == ComponentType.PIPE) {
                a.byLength = true;
                a.lengthMm += pipeLength(c);
            } else {
                a.count++;
            }
        }

        List<List<String>> rows = new ArrayList<>();
        int no = 0;
        for (Map.Entry<String, Accum> e : byCode.entrySet()) {
            Accum a = e.getValue();
            rows.add(List.of(
                    String.valueOf(++no),
                    e.getKey(),
                    a.description == null ? "" : a.description,
                    a.byLength ? fmt(a.lengthMm / 1000.0, 3) : String.valueOf(a.count),
                    a.byLength ? "M" : "EA",
                    fmt(a.weightKg, 2)));
        }
        return new DrawingTable(DrawingTable.BOM, "BILL OF MATERIALS",
                List.of("NO", "ITEM CODE", "DESCRIPTION", "QTY", "UNIT", "WEIGHT(KG)"), rows);
    }

    /**
     * 절단 리스트.
     * PCF 의 {@code CUT-PIECE-LENGTH} 가 있으면 그것을 쓰고, 없으면 끝점 거리로 대체한다.
     * (절단 길이는 여유·개선각을 반영한 값이라 기하 길이와 다를 수 있다)
     */
    public static DrawingTable cutList(Pipeline pipeline) {
        List<List<String>> rows = new ArrayList<>();
        int no = 0;
        for (PipingComponent c : pipeline.components()) {
            if (c.type() != ComponentType.PIPE) continue;
            double cut = c.cutPieceLength() != null ? c.cutPieceLength() : pipeLength(c);
            if (cut <= 0) continue;

            rows.add(List.of(
                    String.valueOf(++no),
                    c.sourceIndex() == null ? "" : String.valueOf(c.sourceIndex()),
                    c.itemCode() == null ? "" : c.itemCode(),
                    fmt(boreOf(c), 1),
                    fmt(cut, 1),
                    c.cutPieceLength() != null ? "PCF" : "GEOM"));
        }
        return new DrawingTable(DrawingTable.CUT_LIST, "CUT PIPE LIST",
                List.of("NO", "COMP", "ITEM CODE", "BORE(MM)", "CUT LENGTH(MM)", "SOURCE"), rows);
    }

    /** 용접 리스트. 번호 규칙은 도면 주석과 같아야 한다(공장 SW# / 현장 FW#) */
    public static DrawingTable weldList(Pipeline pipeline) {
        List<List<String>> rows = new ArrayList<>();
        int shop = 0;
        int field = 0;

        for (PipingComponent c : pipeline.components()) {
            if (c.type() != ComponentType.WELD) continue;
            boolean isField = isFieldWeld(c.skey());
            String no = (isField ? "FW" : "SW") + (isField ? ++field : ++shop);
            String spool = c.attrs().getOrDefault("WELD-ATTRIBUTE1", "");

            rows.add(List.of(
                    no,
                    isField ? "FIELD" : "SHOP",
                    c.skey() == null ? "" : c.skey(),
                    fmt(boreOf(c), 1),
                    spool));
        }
        return new DrawingTable(DrawingTable.WELD_LIST, "WELD LIST",
                // WELD-ATTRIBUTE1 은 용접 번호가 아니라 스풀 식별자다 — 그렇게 표기한다
                List.of("NO", "TYPE", "SKEY", "BORE(MM)", "SPOOL"), rows);
    }

    /** 도면에 얹을 표 묶음 — 비어 있는 표는 뺀다 */
    public static List<DrawingTable> all(Pipeline pipeline) {
        return java.util.stream.Stream.of(bom(pipeline), cutList(pipeline), weldList(pipeline))
                .filter(t -> !t.isEmpty())
                .sorted(Comparator.comparing(DrawingTable::kind))
                .toList();
    }

    static boolean isFieldWeld(String skey) {
        if (skey == null) return false;
        String s = skey.toUpperCase(Locale.ROOT);
        return s.startsWith("WS") || s.startsWith("WF") || s.startsWith("WO") || s.startsWith("XX");
    }

    private static double pipeLength(PipingComponent c) {
        List<Port> ends = c.ports().stream().filter(p -> p.kind() == PortKind.END).toList();
        return ends.size() < 2 ? 0 : ends.get(0).position().distanceTo(ends.get(1).position());
    }

    private static double boreOf(PipingComponent c) {
        return c.ports().stream()
                .map(Port::boreMm).filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue).max().orElse(0);
    }

    private static String fmt(double v, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", v);
    }
}
