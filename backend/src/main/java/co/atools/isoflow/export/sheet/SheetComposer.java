// SheetComposer.java — 도면 내용을 용지에 앉히고 도곽·타이틀 블록·자재표를 얹는다
package co.atools.isoflow.export.sheet;

import co.atools.isoflow.engine.scene.Scene2D;
import co.atools.isoflow.engine.symbol.Symbol2dLibrary.Styles;
import co.atools.isoflow.engine.style.IsoStyle;
import co.atools.isoflow.engine.table.DrawingTable;

import java.util.ArrayList;
import java.util.List;

/**
 * 지금까지의 Scene2D 는 배관 좌표(mm)로 그려진 "내용"이다.
 * 여기서 용지 좌표(mm)로 옮겨 실제 도면 한 장을 만든다.
 *
 * <p>뷰어·DXF·PDF 가 <b>같은 Scene2D</b> 를 본다 — 화면과 출력이 갈리지 않게 하기 위함이다.
 */
public final class SheetComposer {

    /** 용지 규격(mm) */
    public record SheetSize(String name, double width, double height) {
        public static final SheetSize A3 = new SheetSize("A3", 420, 297);
        public static final SheetSize A2 = new SheetSize("A2", 594, 420);
    }

    private static final double GUTTER = 5;

    /**
     * 용지 배치 치수. <b>static 가변 필드로 두면 동시 요청에서 값이 섞인다</b> —
     * 반드시 인자로 넘긴다.
     */
    private record Layout(double margin, double tableBandHeight, double titleBlockHeight) {
        static Layout defaults() {
            return new Layout(10, 70, 34);
        }
    }

    /** 타이틀 블록 폭 — 표 띠의 오른쪽 끝을 차지한다 */
    private static final double TITLE_BLOCK_WIDTH = 110;

    private static final double TABLE_TEXT = 2.2;
    private static final double TABLE_ROW_HEIGHT = 4.0;
    private static final double TITLE_LABEL_TEXT = 2.2;
    private static final double TITLE_VALUE_TEXT = 3.6;
    /** 표는 이 행 수를 넘으면 자르고 "…외 N행" 을 남긴다 */
    private static final int MAX_TABLE_ROWS = 28;

    /** 도곽/표 레이어 */
    public static final String LAYER_FRAME = "frame";

    private SheetComposer() {
    }

    /**
     * @param content  배관 좌표로 그려진 도면 내용
     * @param title    타이틀 블록에 넣을 항목들
     * @param tables   우측에 얹을 표 (위에서부터 차례로)
     */
    /** 스타일 설정으로 조립한다 */
    public static Scene2D compose(Scene2D content, TitleBlock title,
                                  List<DrawingTable> tables, IsoStyle rawStyle) {
        IsoStyle style = (rawStyle == null ? IsoStyle.defaults() : rawStyle).withDefaults();
        IsoStyle.Sheet cfg = style.sheet();
        // 표가 실제로 있을 때만 띠를 잡는다.
        // 자재표는 1장에만 얹으므로, 개수만 보지 않으면 2장부터 그 자리가 통째로 비어 버린다
        boolean withTables = Boolean.TRUE.equals(style.display().tables()) && !tables.isEmpty();
        // 표를 끄면 그 높이만큼 도면 영역이 넓어진다
        Layout layout = new Layout(cfg.marginMm(),
                withTables ? cfg.tableBandMm() : 0, cfg.titleBlockMm());
        SheetSize size = new SheetSize(
                cfg.size() == null ? "custom" : cfg.size(),
                cfg.resolvedWidthMm(), cfg.resolvedHeightMm());
        return compose(content, title, withTables ? tables : List.of(), size, layout);
    }

    public static Scene2D compose(Scene2D content, TitleBlock title,
                                  List<DrawingTable> tables, SheetSize sheet) {
        return compose(content, title, tables, sheet, Layout.defaults());
    }

    private static Scene2D compose(Scene2D content, TitleBlock title, List<DrawingTable> tables,
                                   SheetSize sheet, Layout layout) {
        final double MARGIN = layout.margin();
        // 표와 타이틀 블록은 아래 띠에 둔다.
        // 우측 세로 칸으로 두면 도면 영역이 세로로 길어지는데 등각도 내용은 가로로 길다 —
        // 늘 가로에서 배율이 걸려 세로가 30~60% 남았다
        // 띠는 설정값이 아니라 <b>실제 표 내용</b>만큼만 차지한다.
        // 늘 최대 높이를 잡으면 표가 짧은 도면에서 종이가 그만큼 죽는다
        double bandH = Math.max(bandHeightFor(tables, layout), layout.titleBlockHeight());
        double drawW = sheet.width() - MARGIN * 2;
        double drawH = sheet.height() - MARGIN * 2 - bandH - GUTTER;
        double drawX = MARGIN;
        double drawY = MARGIN + bandH + GUTTER;

        List<Scene2D.Element> out = new ArrayList<>(
                fitContent(content, drawX, drawY, drawW, drawH));

        out.addAll(frame(sheet, layout));
        out.addAll(titleBlock(sheet, title, layout));
        out.addAll(tableBand(sheet, tables, layout));

        List<Scene2D.Layer> layers = new ArrayList<>(content.layers());
        layers.add(new Scene2D.Layer(LAYER_FRAME, "Frame", true, layers.size()));

        return new Scene2D(
                Scene2D.SCHEMA_VERSION, content.id(), "mm", content.sheet(),
                new double[]{0, 0, sheet.width(), sheet.height()},
                layers, content.styles(), List.copyOf(out));
    }

    /**
     * 여러 장을 가로로 나란히 이어 붙인 하나의 Scene2D 를 만든다.
     *
     * <p>DXF R12 에는 레이아웃(페이지) 개념이 없다. 시트를 파일로 쪼개면 CAD 에서 다시 모아야 하므로,
     * 한 파일 안에 나란히 배치해 통째로 열 수 있게 한다.
     *
     * @param sheets 이미 용지에 앉힌 시트들
     * @param gapMm  시트 사이 간격
     */
    public static Scene2D sideBySide(List<Scene2D> sheets, double gapMm) {
        if (sheets.isEmpty()) throw new IllegalArgumentException("시트가 없습니다");
        if (sheets.size() == 1) return sheets.get(0);

        List<Scene2D.Element> out = new ArrayList<>();
        double x = 0;
        double maxY = 0;
        for (Scene2D scene : sheets) {
            double[] b = scene.bounds();
            for (Scene2D.Element e : scene.elements()) out.add(transform(e, 1, x - b[0], -b[1]));
            x += (b[2] - b[0]) + gapMm;
            maxY = Math.max(maxY, b[3] - b[1]);
        }
        Scene2D first = sheets.get(0);
        return new Scene2D(Scene2D.SCHEMA_VERSION, first.id(), "mm", 1,
                new double[]{0, 0, x - gapMm, maxY},
                first.layers(), first.styles(), List.copyOf(out));
    }

    /** 내용을 도면 영역에 맞춰 균일 축소/이동한다 */
    private static List<Scene2D.Element> fitContent(Scene2D content,
                                                    double x, double y, double w, double h) {
        double[] b = content.bounds();
        double cw = Math.max(b[2] - b[0], 1e-6);
        double ch = Math.max(b[3] - b[1], 1e-6);
        double scale = Math.min(w / cw, h / ch);
        // 내용을 영역 가운데로
        double tx = x + (w - cw * scale) / 2 - b[0] * scale;
        double ty = y + (h - ch * scale) / 2 - b[1] * scale;

        List<Scene2D.Element> out = new ArrayList<>(content.elements().size());
        for (Scene2D.Element e : content.elements()) out.add(transform(e, scale, tx, ty));
        return out;
    }

    /** 균일 스케일 + 평행이동. 반지름과 문자 높이도 함께 줄여야 한다 */
    private static Scene2D.Element transform(Scene2D.Element e, double s, double tx, double ty) {
        return switch (e) {
            case Scene2D.Line l -> new Scene2D.Line(l.id(), l.layerId(), l.styleRef(),
                    l.x1() * s + tx, l.y1() * s + ty, l.x2() * s + tx, l.y2() * s + ty);
            case Scene2D.Polyline p -> new Scene2D.Polyline(p.id(), p.layerId(), p.styleRef(),
                    p.points().stream().map(q -> new Scene2D.Point(q.x() * s + tx, q.y() * s + ty)).toList());
            case Scene2D.Polygon p -> new Scene2D.Polygon(p.id(), p.layerId(), p.styleRef(),
                    p.points().stream().map(q -> new Scene2D.Point(q.x() * s + tx, q.y() * s + ty)).toList());
            case Scene2D.Circle c -> new Scene2D.Circle(c.id(), c.layerId(), c.styleRef(),
                    c.cx() * s + tx, c.cy() * s + ty, c.r() * s);
            case Scene2D.Ellipse el -> new Scene2D.Ellipse(el.id(), el.layerId(), el.styleRef(),
                    el.cx() * s + tx, el.cy() * s + ty, el.rx() * s, el.ry() * s, el.rotation());
            case Scene2D.Arc a -> new Scene2D.Arc(a.id(), a.layerId(), a.styleRef(),
                    a.cx() * s + tx, a.cy() * s + ty, a.r() * s,
                    a.rx() == null ? null : a.rx() * s, a.ry() == null ? null : a.ry() * s,
                    a.rotation(), a.startAngle(), a.endAngle());
            case Scene2D.Text t -> new Scene2D.Text(t.id(), t.layerId(), t.styleRef(),
                    t.x() * s + tx, t.y() * s + ty, t.content(), t.rotation(), t.anchor(),
                    t.height() == null ? null : t.height() * s);
        };
    }

    // ─────────────────────────── 도곽 / 타이틀 ───────────────────────────

    private static List<Scene2D.Element> frame(SheetSize sheet, Layout layout) {
        double m = layout.margin();
        double x0 = m / 2, y0 = m / 2;
        double x1 = sheet.width() - m / 2, y1 = sheet.height() - m / 2;
        return List.of(rect("frm-outer", x0, y0, x1, y1));
    }

    private static List<Scene2D.Element> titleBlock(SheetSize sheet, TitleBlock title, Layout layout) {
        double x1 = sheet.width() - layout.margin();
        double x0 = x1 - TITLE_BLOCK_WIDTH;
        double y0 = layout.margin();
        double y1 = y0 + layout.titleBlockHeight();

        List<Scene2D.Element> out = new ArrayList<>();
        out.add(rect("tb-box", x0, y0, x1, y1));

        // 라인 번호는 크게, 나머지는 라벨-값 쌍으로
        out.add(text("tb-line", x0 + 3, y1 - 7, title.lineNumber(), TITLE_VALUE_TEXT, "start"));
        out.add(line("tb-sep", x0, y1 - 11, x1, y1 - 11));

        String[][] fields = {
                {"SPEC", title.pipingSpec()},
                {"AREA", title.area()},
                {"REV", title.revision()},
                {"SHEET", title.sheet()},
        };
        double y = y1 - 15;
        for (String[] f : fields) {
            if (f[1] == null || f[1].isBlank()) continue;
            out.add(text("tb-l" + f[0], x0 + 3, y, f[0], TITLE_LABEL_TEXT, "start"));
            out.add(text("tb-v" + f[0], x0 + 26, y, f[1], TITLE_LABEL_TEXT, "start"));
            y -= 4.6;
        }
        return out;
    }

    /**
     * 표가 실제로 차지할 띠 높이. 설정값이 상한이다.
     * 나란히 놓으므로 <b>가장 긴 표 하나</b>만큼이면 된다.
     */
    private static double bandHeightFor(List<DrawingTable> tables, Layout layout) {
        if (layout.tableBandHeight() <= 0 || tables.isEmpty()) return 0;
        double tallest = 0;
        for (DrawingTable t : tables) {
            int rows = Math.min(t.rows().size(), MAX_TABLE_ROWS);
            // drawTable 의 capacity 식(제목 1.4행 + 여유 2행)과 맞춰야 한다 —
            // 어긋나면 자리를 좁게 잡아 행이 통째로 잘린다
            double h = (rows + 3.4) * TABLE_ROW_HEIGHT
                    + (rows < t.rows().size() ? TABLE_ROW_HEIGHT : 0);
            tallest = Math.max(tallest, h);
        }
        return Math.min(tallest, layout.tableBandHeight());
    }

    /**
     * 아래 표 띠 — 표를 가로로 나란히 놓는다.
     *
     * <p>세로로 쌓으면 띠가 높아져 도면 영역을 그만큼 잡아먹는다.
     * 나란히 두면 띠 높이는 <b>가장 긴 표 하나</b>만큼이면 된다.
     * 타이틀 블록이 오른쪽 끝을 차지하므로 표는 그 왼쪽까지만 쓴다.
     */
    private static List<Scene2D.Element> tableBand(SheetSize sheet, List<DrawingTable> tables,
                                                   Layout layout) {
        if (layout.tableBandHeight() <= 0 || tables.isEmpty()) return List.of();

        double left = layout.margin();
        double right = sheet.width() - layout.margin() - TITLE_BLOCK_WIDTH - GUTTER;
        double top = layout.margin() + bandHeightFor(tables, layout);
        double bottom = layout.margin();
        double available = right - left;
        if (available <= 0) return List.of();

        // 열이 많은 표에 폭을 더 준다 — 균등 분배하면 자재 설명이 다 잘린다
        int totalCols = tables.stream().mapToInt(DrawingTable::columnCount).sum();
        double gaps = GUTTER * (tables.size() - 1);
        double perCol = (available - gaps) / Math.max(1, totalCols);

        List<Scene2D.Element> out = new ArrayList<>();
        double x = left;
        int idx = 0;
        for (DrawingTable t : tables) {
            double w = perCol * t.columnCount();
            if (x + w > right + 0.01) break;   // 남은 폭이 없으면 그만
            drawTable(out, t, x, x + w, top, bottom, idx++);
            x += w + GUTTER;
        }
        return out;
    }

    /** 표 하나를 그리고 다음 표가 시작할 y 를 돌려준다 */
    private static double drawTable(List<Scene2D.Element> out, DrawingTable t,
                                    double x0, double x1, double top, double bottom, int idx) {
        String p = "tbl" + idx;
        double y = top;

        out.add(text(p + "-title", x0, y - TABLE_TEXT, t.title(), TABLE_TEXT * 1.3, "start"));
        y -= TABLE_ROW_HEIGHT * 1.4;

        int cols = t.columnCount();
        double colW = (x1 - x0) / cols;

        // 남은 높이에 맞춰 행 수를 자른다 — 넘치면 잘렸다는 사실을 도면에 남긴다
        int capacity = (int) Math.floor((y - bottom) / TABLE_ROW_HEIGHT) - 2;
        int limit = Math.min(Math.min(t.rows().size(), MAX_TABLE_ROWS), Math.max(capacity, 0));

        double headerY = y;
        out.add(line(p + "-h0", x0, headerY, x1, headerY));
        for (int c = 0; c < cols; c++) {
            out.add(text(p + "-hd" + c, x0 + colW * c + 1, headerY - TABLE_ROW_HEIGHT + 1.2,
                    clip(t.headers().get(c), colW), TABLE_TEXT, "start"));
        }
        y -= TABLE_ROW_HEIGHT;
        out.add(line(p + "-h1", x0, y, x1, y));

        for (int r = 0; r < limit; r++) {
            List<String> row = t.rows().get(r);
            for (int c = 0; c < cols && c < row.size(); c++) {
                out.add(text(p + "-r" + r + "c" + c, x0 + colW * c + 1, y - TABLE_ROW_HEIGHT + 1.2,
                        clip(row.get(c), colW), TABLE_TEXT, "start"));
            }
            y -= TABLE_ROW_HEIGHT;
        }
        out.add(line(p + "-bot", x0, y, x1, y));

        if (limit < t.rows().size()) {
            out.add(text(p + "-more", x0 + 1, y - TABLE_ROW_HEIGHT + 1.2,
                    "... +" + (t.rows().size() - limit) + " rows (see CSV)", TABLE_TEXT, "start"));
            y -= TABLE_ROW_HEIGHT;
        }
        // 세로 괘선
        for (int c = 0; c <= cols; c++) {
            double x = x0 + colW * c;
            out.add(line(p + "-v" + c, x, headerY, x, y));
        }
        return y;
    }

    /**
     * 칸 폭을 넘는 문자열을 잘라 준다.
     * 자르지 않으면 자재 설명 같은 긴 문자열이 옆 칸을 덮어 표를 읽을 수 없게 된다.
     * (전체 내용은 CSV 로 받을 수 있다)
     */
    static String clip(String text, double columnWidth) {
        if (text == null || text.isEmpty()) return "";
        double charWidth = TABLE_TEXT * 0.62;
        int max = (int) Math.floor((columnWidth - 2) / charWidth);
        if (max <= 1) return "";
        if (text.length() <= max) return text;
        return text.substring(0, Math.max(1, max - 2)) + "..";
    }

    // ─────────────────────────── 요소 헬퍼 ───────────────────────────

    private static Scene2D.Element rect(String id, double x0, double y0, double x1, double y1) {
        return new Scene2D.Polygon(id, LAYER_FRAME, Styles.OUTLINE, List.of(
                new Scene2D.Point(x0, y0), new Scene2D.Point(x1, y0),
                new Scene2D.Point(x1, y1), new Scene2D.Point(x0, y1)));
    }

    private static Scene2D.Element line(String id, double x0, double y0, double x1, double y1) {
        return new Scene2D.Line(id, LAYER_FRAME, Styles.OUTLINE, x0, y0, x1, y1);
    }

    private static Scene2D.Element text(String id, double x, double y, String s, double h, String anchor) {
        return new Scene2D.Text(id, LAYER_FRAME, Styles.TEXT, x, y, s, 0.0, anchor, h);
    }
}
