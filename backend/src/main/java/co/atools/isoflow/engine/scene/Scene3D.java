// Scene3D.java — 3D 뷰어로 내보내는 Scene 계약. schemas/scene3d.schema.json / src/types/scene3d.ts 와 동기화
package co.atools.isoflow.engine.scene;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Scene3D(
        String schemaVersion,
        String id,
        String units,
        /** 리베이스 오프셋(mm). 로컬좌표 + origin = 원본 플랜트 좌표 */
        double[] origin,
        /** 로컬 좌표 기준 [minX,minY,minZ,maxX,maxY,maxZ] */
        double[] bounds,
        PipelineInfo pipeline,
        List<Component3D> components,
        Map<String, String> materials) {

    public static final String SCHEMA_VERSION = "1.0.0";

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PipelineInfo(
            String lineNumber,
            String pipingSpec,
            String nominalClass,
            String area,
            String revision,
            Map<String, String> attrs) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Component3D(
            String id,
            String type,
            String rawKeyword,
            Shape3D shape,
            List<Port3D> ports,
            String skey,
            String itemCode,
            String description,
            Double weight,
            Double angleDeg,
            /** 편심 리듀서의 평평한 면 방향(UP/DOWN). 동심 리듀서·기타 컴포넌트는 null */
            String flatDirection,
            /** 밸브 스템이 향하는 방위(NORTH/SOUTH/EAST/WEST/UP/DOWN). 밸브가 아니거나 없으면 null */
            String spindleDirection,
            /**
             * 유동이 향하는 END 포트의 ordinal. PCF 의 {@code FLOW} 규약은 엔진 안에서 흡수하고
             * 프론트에는 <b>포트 번호로만</b> 내려보낸다 — 규약이 두 곳으로 갈라지지 않게.
             */
            Integer flowToEnd,
            Map<String, String> attrs) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Port3D(
            String kind,
            int ordinal,
            double[] p,
            Double bore,
            String endType,
            String joint) {
    }
}
