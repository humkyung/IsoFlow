// MaterialItem.java — PCF MATERIALS 섹션의 자재 항목. BOM 집계의 원천이다
package co.atools.isoflow.engine.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MaterialItem {

    private final String itemCode;
    private String description;
    private final Map<String, String> attrs = new LinkedHashMap<>();

    public MaterialItem(String itemCode) {
        this.itemCode = itemCode;
    }

    public String itemCode() {
        return itemCode;
    }

    public String description() {
        return description;
    }

    public void setDescription(String v) {
        this.description = v;
    }

    /** MATERIAL-USER99 등 부가 속성을 원문 보존한다 */
    public void putAttr(String key, String value) {
        attrs.put(key, value == null ? "" : value);
    }

    public Map<String, String> attrs() {
        return Collections.unmodifiableMap(attrs);
    }

    @Override
    public String toString() {
        return itemCode + " — " + description;
    }
}
