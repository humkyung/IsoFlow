// Pipeline.java — 라인 하나의 중립 도메인 모델(IR). 좌표는 리베이스된 로컬 mm 이다
package co.atools.isoflow.engine.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Pipeline {

    private String lineNumber;
    private String pipingSpec;
    private String nominalClass;
    private String area;
    private String revision;

    /** 원본 절대 좌표에서 뺀 오프셋(mm). 로컬좌표 + origin = 원본 플랜트 좌표 */
    private Vec3 origin = Vec3.ZERO;

    /** 헤더의 ATTRIBUTEnn 등 파이프라인 수준 속성 원문 */
    private final Map<String, String> attrs = new LinkedHashMap<>();
    private final List<PipingComponent> components = new ArrayList<>();
    private final Map<String, MaterialItem> materials = new LinkedHashMap<>();

    public void addComponent(PipingComponent c) {
        components.add(c);
    }

    public List<PipingComponent> components() {
        return Collections.unmodifiableList(components);
    }

    /** item-code 로 자재 항목을 얻거나 새로 만든다 */
    public MaterialItem material(String itemCode) {
        return materials.computeIfAbsent(itemCode, MaterialItem::new);
    }

    public Map<String, MaterialItem> materials() {
        return Collections.unmodifiableMap(materials);
    }

    public void putAttr(String key, String value) {
        attrs.put(key, value == null ? "" : value);
    }

    public Map<String, String> attrs() {
        return Collections.unmodifiableMap(attrs);
    }

    public String lineNumber() {
        return lineNumber;
    }

    public void setLineNumber(String v) {
        this.lineNumber = v;
    }

    public String pipingSpec() {
        return pipingSpec;
    }

    public void setPipingSpec(String v) {
        this.pipingSpec = v;
    }

    public String nominalClass() {
        return nominalClass;
    }

    public void setNominalClass(String v) {
        this.nominalClass = v;
    }

    public String area() {
        return area;
    }

    public void setArea(String v) {
        this.area = v;
    }

    public String revision() {
        return revision;
    }

    public void setRevision(String v) {
        this.revision = v;
    }

    public Vec3 origin() {
        return origin;
    }

    public void setOrigin(Vec3 v) {
        this.origin = v;
    }

    @Override
    public String toString() {
        return "Pipeline[%s] components=%d materials=%d".formatted(lineNumber, components.size(), materials.size());
    }
}
