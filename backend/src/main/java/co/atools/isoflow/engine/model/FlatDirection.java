// FlatDirection.java — 편심 리듀서(REDUCER-ECCENTRIC)의 평평한 면이 향하는 방향
package co.atools.isoflow.engine.model;

/**
 * PCF 의 {@code FLAT-DIRECTION} 속성.
 *
 * <p>편심 리듀서는 한쪽 모선이 평평하다. 그 평평한 면이 위/아래 어느 쪽인지를 나타낸다
 * (배관 좌표계는 Z-up 이므로 UP=+Z, DOWN=-Z).
 *
 * <p><b>PCF 의 END-POINT 좌표에는 편심량이 이미 반영되어 있다</b> — 실 코퍼스 확인 결과
 * 작은쪽 중심이 {@code (OD_large - OD_small)/2} 만큼 평평한 면 방향으로 어긋나 있다.
 * 따라서 이 값은 중심을 옮기는 데 쓰는 것이 아니라, <b>런 축과 평평한 면의 방향을 알아내는</b> 데 쓴다.
 */
public enum FlatDirection {
    UP,
    DOWN;

    /** PCF 원문 값을 해석한다. 모르는 값이면 null — 지어내지 않는다 */
    public static FlatDirection fromPcf(String raw) {
        if (raw == null) return null;
        return switch (raw.trim().toUpperCase()) {
            case "UP" -> UP;
            case "DOWN" -> DOWN;
            default -> null;
        };
    }
}
