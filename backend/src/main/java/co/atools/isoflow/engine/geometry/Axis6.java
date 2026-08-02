// Axis6.java — 배관 방향을 6방위(N/S/E/W/U/D)로 분류한 결과. 어디에도 맞지 않으면 SKEW
package co.atools.isoflow.engine.geometry;

import co.atools.isoflow.engine.model.Vec3;

public enum Axis6 {
    /** +X */ EAST(new Vec3(1, 0, 0)),
    /** -X */ WEST(new Vec3(-1, 0, 0)),
    /** +Y */ NORTH(new Vec3(0, 1, 0)),
    /** -Y */ SOUTH(new Vec3(0, -1, 0)),
    /** +Z */ UP(new Vec3(0, 0, 1)),
    /** -Z */ DOWN(new Vec3(0, 0, -1)),
    /** 어느 축과도 맞지 않는 사선 — 등각도에서 해칭 삼각형으로 표기한다 */
    SKEW(null);

    private final Vec3 direction;

    Axis6(Vec3 direction) {
        this.direction = direction;
    }

    /**
     * PCF 가 방위 이름으로 적어 둔 방향을 읽는다 (SPINDLE-DIRECTION / DIRECTION 등).
     * 모르는 값이면 null — 지어내지 않는다. {@code SKEW} 는 이름으로 오지 않는다.
     */
    public static Axis6 fromName(String raw) {
        if (raw == null) return null;
        return switch (raw.trim().toUpperCase()) {
            case "NORTH", "N" -> NORTH;
            case "SOUTH", "S" -> SOUTH;
            case "EAST", "E" -> EAST;
            case "WEST", "W" -> WEST;
            case "UP", "U" -> UP;
            case "DOWN", "D" -> DOWN;
            default -> null;
        };
    }

    /** 축 단위벡터. SKEW 는 null */
    public Vec3 direction() {
        return direction;
    }

    public boolean isSkew() {
        return this == SKEW;
    }

    /** 수평면 위의 축인지 (U/D 가 아닌지) */
    public boolean isHorizontal() {
        return this == EAST || this == WEST || this == NORTH || this == SOUTH;
    }

    /** 부호를 뺀 축 종류 — 좌표 압축에서 축별로 묶을 때 쓴다 (0=X, 1=Y, 2=Z, -1=SKEW) */
    public int worldAxisIndex() {
        return switch (this) {
            case EAST, WEST -> 0;
            case NORTH, SOUTH -> 1;
            case UP, DOWN -> 2;
            case SKEW -> -1;
        };
    }
}
