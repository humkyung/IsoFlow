// PipingComponent.java — PCF/IDF 컴포넌트 하나. 파서가 모르는 속성도 attrs 에 원문 보존한다
package co.atools.isoflow.engine.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class PipingComponent {

    /** 편심 리듀서의 평평한 면 방향을 담은 PCF 속성 키 (파서가 대문자로 저장한다) */
    private static final String ATTR_FLAT_DIRECTION = "FLAT-DIRECTION";
    /** 밸브 스템(스핀들)이 향하는 방위를 담은 PCF 속성 키 */
    private static final String ATTR_SPINDLE_DIRECTION = "SPINDLE-DIRECTION";
    /** 유동 방향을 담은 PCF 속성 키 */
    private static final String ATTR_FLOW = "FLOW";

    private final ComponentType type;
    /** PCF 원문 키워드 (REDUCER-CONCENTRIC 등). UNKNOWN 타입의 원본을 잃지 않기 위해 보존한다 */
    private final String rawKeyword;
    private final List<Port> ports = new ArrayList<>();
    private final Map<String, String> attrs = new LinkedHashMap<>();

    private Integer sourceIndex;
    private String skey;
    private String itemCode;
    private String itemDescription;
    private Double weight;
    private Double cutPieceLength;
    private String uci;
    /** 엘보/밴드 각도(도). PCF 에 ANGLE 이 없으면 null — 엘보는 90° 로 본다 */
    private Double angleDeg;
    /** WELD 의 MASTER-COMPONENT-IDENTIFIER — 어느 컴포넌트에 붙은 용접인지 */
    private Integer masterComponentIndex;
    /** MATERIAL-LIST EXCLUDE 이면 true — BOM 집계에서 뺀다 */
    private boolean excludedFromBom;

    public PipingComponent(ComponentType type, String rawKeyword) {
        this.type = type;
        this.rawKeyword = rawKeyword;
    }

    // ── 포트 ──

    public void addPort(Port p) {
        ports.add(p);
    }

    public List<Port> ports() {
        return Collections.unmodifiableList(ports);
    }

    /** 연결 가능한 포트(END/BRANCH)만 추린다 */
    public List<Port> connectablePorts() {
        return ports.stream().filter(Port::isConnectable).toList();
    }

    public Optional<Port> portOf(PortKind kind, int ordinal) {
        return ports.stream().filter(p -> p.kind() == kind && p.ordinal() == ordinal).findFirst();
    }

    /** CENTRE-POINT — 엘보/티/올렛의 작도 기준점 */
    public Optional<Vec3> centre() {
        return portOf(PortKind.CENTRE, 0).map(Port::position);
    }

    /**
     * FLAT-DIRECTION — 편심 리듀서의 평평한 면 방향. 없거나 모르는 값이면 null.
     * 원문은 {@link #attrs()} 에 그대로 남겨 두고 여기서 타입만 씌운다(passthrough 규약 유지).
     */
    public FlatDirection flatDirection() {
        return FlatDirection.fromPcf(attrs.get(ATTR_FLAT_DIRECTION));
    }

    /**
     * SPINDLE-DIRECTION — 밸브 스템이 향하는 방위의 <b>원문</b> (NORTH/UP/WEST…). 없으면 null.
     * 해석은 {@code geometry.Axis6.fromName} 이 한다 — model 이 geometry 를 역참조하지 않도록
     * 여기서는 문자열만 꺼내 준다.
     */
    public String spindleDirection() {
        return attrs.get(ATTR_SPINDLE_DIRECTION);
    }

    /**
     * FLOW — 유동 방향의 <b>원문</b>. 해석은 scene 계층이 한다.
     * 코퍼스 관측값은 0(미지정) / 1 / 2 / 3 이다.
     */
    public String flow() {
        return attrs.get(ATTR_FLOW);
    }

    // ── 속성 ──

    /** 파서가 개별 필드로 승격하지 않은 속성을 원문 그대로 보관한다 */
    public void putAttr(String key, String value) {
        attrs.put(key, value == null ? "" : value);
    }

    public Map<String, String> attrs() {
        return Collections.unmodifiableMap(attrs);
    }

    public ComponentType type() {
        return type;
    }

    public String rawKeyword() {
        return rawKeyword;
    }

    public Integer sourceIndex() {
        return sourceIndex;
    }

    public void setSourceIndex(Integer v) {
        this.sourceIndex = v;
    }

    public String skey() {
        return skey;
    }

    public void setSkey(String v) {
        this.skey = v;
    }

    public String itemCode() {
        return itemCode;
    }

    public void setItemCode(String v) {
        this.itemCode = v;
    }

    public String itemDescription() {
        return itemDescription;
    }

    public void setItemDescription(String v) {
        this.itemDescription = v;
    }

    public Double weight() {
        return weight;
    }

    public void setWeight(Double v) {
        this.weight = v;
    }

    public Double cutPieceLength() {
        return cutPieceLength;
    }

    public void setCutPieceLength(Double v) {
        this.cutPieceLength = v;
    }

    public String uci() {
        return uci;
    }

    public void setUci(String v) {
        this.uci = v;
    }

    public Double angleDeg() {
        return angleDeg;
    }

    public void setAngleDeg(Double v) {
        this.angleDeg = v;
    }

    public Integer masterComponentIndex() {
        return masterComponentIndex;
    }

    public void setMasterComponentIndex(Integer v) {
        this.masterComponentIndex = v;
    }

    public boolean excludedFromBom() {
        return excludedFromBom;
    }

    public void setExcludedFromBom(boolean v) {
        this.excludedFromBom = v;
    }

    /** 사람이 읽는 식별자 — 진단 메시지에 쓴다 */
    public String label() {
        return rawKeyword + (sourceIndex != null ? "#" + sourceIndex : "");
    }

    @Override
    public String toString() {
        return "%s ports=%d skey=%s".formatted(label(), ports.size(), skey);
    }
}
