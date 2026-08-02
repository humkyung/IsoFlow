// PdfRenderer.java — 용지 좌표(mm)의 Scene2D 를 PDF 한 장으로 그린다 (Apache PDFBox)
package co.atools.isoflow.export.pdf;

import co.atools.isoflow.engine.scene.Scene2D;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PdfRenderer {

    /** mm → PDF 포인트 (1pt = 1/72 inch) */
    private static final double MM_TO_PT = 72.0 / 25.4;
    /** 곡선 분할 */
    private static final int SEGMENTS_PER_RAD = 8;
    private static final int MIN_SEGMENTS = 12;

    private PdfRenderer() {
    }

    /**
     * @param scene      용지 좌표(mm) Scene2D — {@code SheetComposer} 를 거친 것이어야 한다
     * @param widthMm    용지 폭
     * @param heightMm   용지 높이
     */
    public static byte[] render(Scene2D scene, double widthMm, double heightMm) throws IOException {
        return render(List.of(scene), widthMm, heightMm);
    }

    /** 여러 장을 한 PDF 의 연속 페이지로 낸다 — 시트 순서가 그대로 페이지 순서다 */
    public static byte[] render(List<Scene2D> sheets, double widthMm, double heightMm)
            throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (Scene2D scene : sheets) {
                PDPage page = new PDPage(new PDRectangle((float) pt(widthMm), (float) pt(heightMm)));
                doc.addPage(page);

                Map<String, Scene2D.Style> styles = new HashMap<>();
                for (Scene2D.Style s : scene.styles()) styles.put(s.id(), s);

                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.setStrokingColor(black());
                    cs.setNonStrokingColor(black());
                    for (Scene2D.Element e : scene.elements()) {
                        draw(cs, e, styles.get(e.styleRef()));
                    }
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static void draw(PDPageContentStream cs, Scene2D.Element e, Scene2D.Style style)
            throws IOException {
        boolean filled = style != null && style.fill() != null && style.fill().color() != null;
        cs.setLineWidth((float) strokeWidth(style));
        applyDash(cs, style);

        switch (e) {
            case Scene2D.Line l -> {
                cs.moveTo((float) pt(l.x1()), (float) pt(l.y1()));
                cs.lineTo((float) pt(l.x2()), (float) pt(l.y2()));
                cs.stroke();
            }
            case Scene2D.Polyline p -> strokePath(cs, toXy(p.points()), false, false);
            case Scene2D.Polygon p -> strokePath(cs, toXy(p.points()), true, filled);
            case Scene2D.Circle c ->
                    strokePath(cs, sampleEllipse(c.cx(), c.cy(), c.r(), c.r(), 0, 0, Math.PI * 2),
                            true, filled);
            case Scene2D.Ellipse el ->
                    strokePath(cs, sampleEllipse(el.cx(), el.cy(), el.rx(), el.ry(),
                            Math.toRadians(el.rotation() == null ? 0 : el.rotation()),
                            0, Math.PI * 2), true, filled);
            case Scene2D.Arc a -> strokePath(cs, sampleEllipse(a.cx(), a.cy(),
                    a.rx() == null ? a.r() : a.rx(), a.ry() == null ? a.r() : a.ry(),
                    Math.toRadians(a.rotation() == null ? 0 : a.rotation()),
                    a.startAngle(), a.endAngle()), false, false);
            case Scene2D.Text t -> drawText(cs, t);
        }
    }

    private static void drawText(PDPageContentStream cs, Scene2D.Text t) throws IOException {
        // Standard-14 는 비ASCII 를 인코딩하지 못한다. 도면 문자는 치수·태그라 ASCII 범위지만
        // 라인 번호에 한글이 섞일 수 있어 방어한다(폰트 임베딩은 후속 과제).
        String content = ascii(t.content());
        if (content.isEmpty()) return;

        var font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        float size = (float) pt(t.height() == null ? 2.5 : t.height());
        float width = font.getStringWidth(content) / 1000 * size;

        float x = (float) pt(t.x());
        float y = (float) pt(t.y());
        // Scene2D 의 anchor 는 가로 정렬, 세로는 항상 중앙이다
        if ("middle".equals(t.anchor())) x -= width / 2;
        else if ("end".equals(t.anchor())) x -= width;
        y -= size * 0.35f;

        cs.beginText();
        cs.setFont(font, size);
        double rot = t.rotation() == null ? 0 : t.rotation();
        if (Math.abs(rot) > 1e-9) {
            var m = org.apache.pdfbox.util.Matrix.getRotateInstance(Math.toRadians(rot), x, y);
            cs.setTextMatrix(m);
        } else {
            cs.newLineAtOffset(x, y);
        }
        cs.showText(content);
        cs.endText();
    }

    private static void strokePath(PDPageContentStream cs, List<double[]> pts,
                                   boolean close, boolean fill) throws IOException {
        if (pts.size() < 2) return;
        cs.moveTo((float) pt(pts.get(0)[0]), (float) pt(pts.get(0)[1]));
        for (int i = 1; i < pts.size(); i++) {
            cs.lineTo((float) pt(pts.get(i)[0]), (float) pt(pts.get(i)[1]));
        }
        if (close) cs.closePath();
        if (fill) cs.fillAndStroke();
        else cs.stroke();
    }

    private static void applyDash(PDPageContentStream cs, Scene2D.Style style) throws IOException {
        double[] dash = style != null && style.stroke() != null ? style.stroke().dash() : null;
        if (dash == null || dash.length == 0) {
            cs.setLineDashPattern(new float[]{}, 0);
            return;
        }
        float[] f = new float[dash.length];
        for (int i = 0; i < dash.length; i++) f[i] = (float) pt(dash[i] * 0.35);
        cs.setLineDashPattern(f, 0);
    }

    private static double strokeWidth(Scene2D.Style style) {
        double w = style != null && style.stroke() != null && style.stroke().width() != null
                ? style.stroke().width() : 1.0;
        // Scene2D 의 굵기는 화면 픽셀 기준이라 그대로 쓰면 종이에서 너무 굵다
        return Math.max(w * 0.25, 0.2);
    }

    static List<double[]> sampleEllipse(double cx, double cy, double rx, double ry,
                                        double rotation, double start, double end) {
        double sweep = Math.abs(end - start);
        int n = Math.max(MIN_SEGMENTS, (int) Math.ceil(sweep * SEGMENTS_PER_RAD));
        double cos = Math.cos(rotation), sin = Math.sin(rotation);
        List<double[]> pts = new java.util.ArrayList<>(n + 1);
        for (int i = 0; i <= n; i++) {
            double t = start + (end - start) * i / n;
            double ex = rx * Math.cos(t), ey = ry * Math.sin(t);
            pts.add(new double[]{cx + ex * cos - ey * sin, cy + ex * sin + ey * cos});
        }
        return pts;
    }

    private static List<double[]> toXy(List<Scene2D.Point> pts) {
        return pts.stream().map(p -> new double[]{p.x(), p.y()}).toList();
    }

    private static PDColor black() {
        return new PDColor(new float[]{0, 0, 0}, PDDeviceRGB.INSTANCE);
    }

    private static String ascii(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) sb.append(c < 128 ? c : '?');
        return sb.toString();
    }

    private static double pt(double mm) {
        return mm * MM_TO_PT;
    }
}
