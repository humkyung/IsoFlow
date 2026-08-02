// ComponentType.java — PCF 컴포넌트 타입. 알 수 없는 키워드는 UNKNOWN 으로 두고 원문을 보존한다
package co.atools.isoflow.engine.model;

import java.util.Map;

public enum ComponentType {

    PIPE, ELBOW, BEND, TEE, CROSS, OLET, REDUCER_CONCENTRIC, REDUCER_ECCENTRIC,
    FLANGE, VALVE, GASKET, BOLT, CAP, COUPLING, UNION, INSTRUMENT, SUPPORT,
    FILTER, TRAP, MISC_COMPONENT, WELD, FLOW_ARROW,
    END_CONNECTION_PIPELINE, END_POSITION_OPEN,
    /** 파서가 모르는 컴포넌트 — 형상은 그리지 않되 원문은 attrs 에 남긴다 */
    UNKNOWN;

    private static final Map<String, ComponentType> BY_KEYWORD = Map.ofEntries(
            Map.entry("PIPE", PIPE),
            Map.entry("PIPE-BLOCK-FIXED", PIPE),
            Map.entry("PIPE-BLOCK-VARIABLE", PIPE),
            Map.entry("PIPE-FIXED", PIPE),
            Map.entry("ELBOW", ELBOW),
            Map.entry("ELBOW-REDUCING", ELBOW),
            Map.entry("ELBOW-TEED", TEE),
            Map.entry("BEND", BEND),
            Map.entry("BEND-TEED", TEE),
            Map.entry("TEE", TEE),
            Map.entry("TEE-SET-ON", TEE),
            Map.entry("TEE-STUB", TEE),
            Map.entry("CROSS", CROSS),
            Map.entry("CROSS-SET-ON", CROSS),
            Map.entry("CROSS-STUB", CROSS),
            Map.entry("OLET", OLET),
            Map.entry("REDUCER-CONCENTRIC", REDUCER_CONCENTRIC),
            Map.entry("REDUCER-ECCENTRIC", REDUCER_ECCENTRIC),
            Map.entry("FLANGE", FLANGE),
            Map.entry("FLANGE-BLIND", FLANGE),
            Map.entry("FLANGE-REDUCING-CONCENTRIC", FLANGE),
            Map.entry("FLANGE-REDUCING-ECCENTRIC", FLANGE),
            Map.entry("LAPJOINT-RING", FLANGE),
            Map.entry("LAPJOINT-STUB-END", FLANGE),
            Map.entry("VALVE", VALVE),
            Map.entry("VALVE-ANGLE", VALVE),
            Map.entry("VALVE-3WAY", VALVE),
            Map.entry("VALVE-4WAY", VALVE),
            Map.entry("GASKET", GASKET),
            Map.entry("BOLT", BOLT),
            Map.entry("CAP", CAP),
            Map.entry("COUPLING", COUPLING),
            Map.entry("UNION", UNION),
            Map.entry("INSTRUMENT", INSTRUMENT),
            Map.entry("INSTRUMENT-ANGLE", INSTRUMENT),
            Map.entry("INSTRUMENT-DIAL", INSTRUMENT),
            Map.entry("SUPPORT", SUPPORT),
            Map.entry("FILTER", FILTER),
            Map.entry("FILTER-ANGLE", FILTER),
            Map.entry("TRAP", TRAP),
            Map.entry("TRAP-ANGLE", TRAP),
            Map.entry("MISC-COMPONENT", MISC_COMPONENT),
            Map.entry("REINFORCEMENT-PAD", MISC_COMPONENT),
            Map.entry("WELD", WELD),
            Map.entry("FLOW-ARROW", FLOW_ARROW),
            Map.entry("END-CONNECTION-PIPELINE", END_CONNECTION_PIPELINE),
            Map.entry("END-CONNECTION-EQUIPMENT", END_CONNECTION_PIPELINE),
            Map.entry("END-POSITION-OPEN", END_POSITION_OPEN));

    /** PCF 키워드를 타입으로 변환한다. 모르는 키워드는 UNKNOWN */
    public static ComponentType fromKeyword(String keyword) {
        return BY_KEYWORD.getOrDefault(keyword.toUpperCase(), UNKNOWN);
    }

    /** 도면에 형상을 그리지 않고 BOM 집계에만 쓰는 타입 */
    public boolean isMaterialOnly() {
        return this == GASKET || this == BOLT;
    }

    /** 배관 경로를 구성하는(위상 그래프의 노드가 되는) 타입 */
    public boolean isRoutingComponent() {
        return switch (this) {
            case WELD, FLOW_ARROW, END_CONNECTION_PIPELINE, END_POSITION_OPEN, GASKET, BOLT, UNKNOWN -> false;
            default -> true;
        };
    }
}
