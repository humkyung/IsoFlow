// Scene2D.java — 등각도 Scene 계약. Verso scene2d.ts 의 Element 서브셋을 그대로 쓴다
package co.atools.isoflow.engine.scene;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

/**
 * 2D 뷰어(three.js)와 DXF/PDF 출력이 함께 쓰는 도면 표현.
 *
 * <p><b>각도 단위 주의</b>(Verso 규약): {@code arc} 의 startAngle/endAngle 은 <b>라디안</b>,
 * {@code ellipse}/{@code text} 의 rotation 은 <b>도</b>.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Scene2D(
        String schemaVersion,
        String id,
        String units,
        /** 시트 번호 (1-base). 시트 분할 전에는 항상 1 */
        int sheet,
        /** [minX, minY, maxX, maxY] */
        double[] bounds,
        List<Layer> layers,
        List<Style> styles,
        List<Element> elements) {

    public static final String SCHEMA_VERSION = "1.0.0";

    /** 표시 레이어 — DXF 레이어와 1:1 로 대응시킨다 */
    public record Layer(String id, String name, boolean visible, int order) {
    }

    /** 공유 스타일. 심볼의 role 이 여기로 매핑된다 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Style(String id, Stroke stroke, Fill fill, Font font) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Stroke(String color, Double width, double[] dash) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Fill(String color) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Font(String family, Double size, String align) {
    }

    public record Point(double x, double y) {
    }

    // record 는 인터페이스의 type() 을 프로퍼티로 노출하지 않는다.
    // EXISTING_PROPERTY 를 쓰면 type 이 아예 직렬화되지 않으므로 Jackson 이 직접 쓰게 한다
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = Line.class, name = "line"),
            @JsonSubTypes.Type(value = Polyline.class, name = "polyline"),
            @JsonSubTypes.Type(value = Polygon.class, name = "polygon"),
            @JsonSubTypes.Type(value = Circle.class, name = "circle"),
            @JsonSubTypes.Type(value = Ellipse.class, name = "ellipse"),
            @JsonSubTypes.Type(value = Arc.class, name = "arc"),
            @JsonSubTypes.Type(value = Text.class, name = "text"),
    })
    public sealed interface Element
            permits Line, Polyline, Polygon, Circle, Ellipse, Arc, Text {
        String type();

        String id();

        String layerId();

        String styleRef();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Line(String id, String layerId, String styleRef,
                       double x1, double y1, double x2, double y2) implements Element {
        public String type() {
            return "line";
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Polyline(String id, String layerId, String styleRef,
                           List<Point> points) implements Element {
        public String type() {
            return "polyline";
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Polygon(String id, String layerId, String styleRef,
                          List<Point> points) implements Element {
        public String type() {
            return "polygon";
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Circle(String id, String layerId, String styleRef,
                         double cx, double cy, double r) implements Element {
        public String type() {
            return "circle";
        }
    }

    /** rotation 은 도(degree) */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Ellipse(String id, String layerId, String styleRef,
                          double cx, double cy, double rx, double ry, Double rotation) implements Element {
        public String type() {
            return "ellipse";
        }
    }

    /** startAngle/endAngle 은 라디안, rotation 은 도 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Arc(String id, String layerId, String styleRef,
                      double cx, double cy, double r, Double rx, Double ry, Double rotation,
                      double startAngle, double endAngle) implements Element {
        public String type() {
            return "arc";
        }
    }

    /** rotation 은 도. anchor 는 start/middle/end */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Text(String id, String layerId, String styleRef,
                       double x, double y, String content, Double rotation,
                       String anchor, Double height) implements Element {
        public String type() {
            return "text";
        }
    }
}
