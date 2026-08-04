// IdfRecordMap.java — IDF(PDS) 레코드 코드 ↔ 컴포넌트 대응. 정식 스펙이 없어 실샘플 역공학으로 만든 표
package co.atools.isoflow.engine.parser;

import co.atools.isoflow.engine.model.ComponentType;

import java.util.Map;
import java.util.Set;

/**
 * <b>이 표는 추정이다.</b> Alias/Intergraph 의 IDF 공개 스펙을 구하지 못해
 * {@code AViewer/IDF} 실샘플 2건에서 역공학했다. 새 샘플이 생기면 반드시 재검증할 것.
 *
 * <p>PCF 와 결정적으로 다른 점: <b>꺾이는 부품이 다리별로 쪼개져 있다.</b>
 * 엘보는 35/36 두 레코드, 티는 45/46/47 세 레코드다.
 * 파서가 이들을 하나의 컴포넌트로 합쳐야 PCF 와 같은 IR 이 나온다.
 */
public final class IdfRecordMap {

    /** 좌표 단위 — 실측으로 확정한 값. 값 / 100 = mm */
    public static final double COORD_TO_MM = 0.01;

    /**
     * 컴포넌트 레코드의 <b>보어 컬럼 위치</b> — 4자 코드 뒤 토큰 기준 0-base 인덱스.
     * 좌표 6개 바로 다음 자리이고 값은 <b>이미 호칭 mm</b> 다(환산 없음).
     *
     * <p>SCT_EDW 실 코퍼스 12개 라인(컴포넌트 레코드 1280건) 전수에서
     * 이 컬럼 값이 전부 정수이고 대응 {@code .prt} 리포트의 SIZE 컬럼에 그대로 등장한다.
     * 예: 500X300 리듀싱 티는 런 조각(45/47)이 500, 분기 조각(46)이 300.
     *
     * <p><b>콤마 뒤 필드는 보어가 아니다.</b> 그 자리는 전 코퍼스에서 {@code 0 / 10000 /
     * 1110000 / 1010000} 네 값뿐이고 포트 수와도 무관하다(엘보 35 가 10000 과 1110000 을 둘 다 가진다).
     * "2포트 10000, 3포트 1110000 이라 비트마스크로 보인다"던 예전 서술은 잘못된 컬럼을 본 결과다.
     * 정체가 확정될 때까지 {@code IDF-FIELD-9 / IDF-FIELD-10} 속성에 원문만 보존한다.
     */
    public static final int BORE_TOKEN_INDEX = 6;

    /** 각도 단위 — PCF 와 같은 1/100 도 */
    public static final double ANGLE_SCALE = 0.01;

    private IdfRecordMap() {
    }

    /** 단일 레코드로 끝나는 컴포넌트 */
    private static final Map<Integer, ComponentType> SINGLE = Map.of(
            100, ComponentType.PIPE,
            105, ComponentType.FLANGE,
            110, ComponentType.GASKET,
            115, ComponentType.BOLT,
            120, ComponentType.WELD,
            125, ComponentType.CAP,
            130, ComponentType.VALVE,
            136, ComponentType.FILTER,
            132, ComponentType.TRAP);

    /** 여러 레코드가 모여 하나가 되는 컴포넌트 — 첫 코드 → 타입 */
    private static final Map<Integer, ComponentType> MULTI_HEAD = Map.of(
            35, ComponentType.ELBOW,
            30, ComponentType.BEND,
            45, ComponentType.TEE,
            50, ComponentType.CROSS,
            40, ComponentType.OLET,
            70, ComponentType.TEE,     // teed elbow
            80, ComponentType.VALVE);  // 3-way

    /** 이어지는 조각 코드 → 그룹 머리 코드 (Map.of 는 10쌍까지라 ofEntries 를 쓴다) */
    private static final Map<Integer, Integer> MULTI_TAIL = Map.ofEntries(
            Map.entry(36, 35),
            Map.entry(31, 30),
            Map.entry(46, 45), Map.entry(47, 45),
            Map.entry(51, 50), Map.entry(52, 50), Map.entry(53, 50),
            Map.entry(41, 40), Map.entry(42, 40),
            Map.entry(71, 70), Map.entry(72, 70),
            Map.entry(81, 80), Map.entry(82, 80));

    /** 컴포넌트가 아니라 파일 제어용인 코드 */
    private static final Set<Integer> CONTROL = Set.of(0, 149, 300, 301, 999);

    public static boolean isControl(int code) {
        return CONTROL.contains(code);
    }

    /** 이 코드로 새 컴포넌트가 시작하는지 */
    public static boolean startsComponent(int code) {
        return SINGLE.containsKey(code) || MULTI_HEAD.containsKey(code);
    }

    /** 이 코드가 직전 컴포넌트의 이어지는 조각인지 */
    public static boolean continuesComponent(int code) {
        return MULTI_TAIL.containsKey(code);
    }

    /** 이어지는 조각이 속한 그룹의 머리 코드 */
    public static Integer headOf(int code) {
        return MULTI_TAIL.get(code);
    }

    /** 코드에 대응하는 컴포넌트 타입. 모르면 null */
    public static ComponentType typeOf(int code) {
        ComponentType t = SINGLE.get(code);
        if (t != null) return t;
        return MULTI_HEAD.get(code);
    }

    // ─────────────────────────── 텍스트 레코드 ───────────────────────────

    /** 자재 품목 코드 */
    public static final int TEXT_ITEM_CODE = -20;
    /** 자재 설명 */
    public static final int TEXT_DESCRIPTION = -21;
    /** 태그 */
    public static final int TEXT_TAG = -22;
    /** 라인/도면 참조 */
    public static final int TEXT_LINE_REFERENCE = -30;
    /** 앞 레코드의 이어붙임 */
    public static final int TEXT_CONTINUATION = -1;
}
