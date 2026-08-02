// DiagnosticCodes.java — 진단 코드 상수. 프론트는 diag.<code> 키로 번역한다
package co.atools.isoflow.engine.diagnostic;

public final class DiagnosticCodes {

    private DiagnosticCodes() {
    }

    // ── 파서 ──
    /** UNITS-* 선언을 해석하지 못했다 */
    public static final String UNKNOWN_UNIT = "UNKNOWN_UNIT";
    /** 헤더에 단위 선언이 없어 기본값(mm)을 썼다 */
    public static final String UNITS_NOT_DECLARED = "UNITS_NOT_DECLARED";
    /** PIPELINE-REFERENCE 가 없다 */
    public static final String NO_PIPELINE_REFERENCE = "NO_PIPELINE_REFERENCE";
    /** 파서가 모르는 컴포넌트 키워드 — 원문은 보존했다 */
    public static final String UNKNOWN_COMPONENT = "UNKNOWN_COMPONENT";
    /** 좌표 토큰이 모자라거나 숫자가 아니다 */
    public static final String BAD_COORDINATE = "BAD_COORDINATE";
    /** 숫자 속성을 해석하지 못했다 */
    public static final String BAD_NUMBER = "BAD_NUMBER";
    /** 배관 경로 컴포넌트인데 END-POINT 가 부족하다 */
    public static final String MISSING_ENDPOINTS = "MISSING_ENDPOINTS";

    // ── 위상 해석 ──
    /** 어디에도 연결되지 않은 포트 */
    public static final String DANGLING_PORT = "DANGLING_PORT";
    /** 같은 접합점에서 보어가 서로 다르다 */
    public static final String BORE_MISMATCH = "BORE_MISMATCH";
    /** 같은 접합점에 포트가 3개 이상 모였다 (분기 컴포넌트가 아닌데) */
    public static final String OVERCROWDED_JOINT = "OVERCROWDED_JOINT";
    /** 라인이 여러 조각으로 끊어져 있다 */
    public static final String DISCONNECTED_SUBGRAPH = "DISCONNECTED_SUBGRAPH";
    /** 길이가 0 인 파이프 */
    public static final String ZERO_LENGTH_PIPE = "ZERO_LENGTH_PIPE";
    /** 분기(올렛 등)를 모재 배관 중간에 붙였다 — 정상 동작 기록 */
    public static final String BRANCH_ATTACHED_MIDSPAN = "BRANCH_ATTACHED_MIDSPAN";

    // ── Scene 생성 ──
    /** 경로 컴포넌트인데 포트가 모자라 3D 에 그릴 수 없다 — 도면에서 조용히 사라지는 것을 막는 경고 */
    public static final String SHAPE_NOT_RENDERABLE = "SHAPE_NOT_RENDERABLE";
    /** 편심 리듀서인데 FLAT-DIRECTION 을 읽지 못했다 — 평평한 면을 알 수 없어 동심으로 그린다 */
    public static final String ECCENTRIC_FLAT_UNKNOWN = "ECCENTRIC_FLAT_UNKNOWN";
    /** SKEY 로도 PCF 타입으로도 심볼을 찾지 못했다 */
    public static final String SKEY_UNRESOLVED = "SKEY_UNRESOLVED";
    /** 사선 구간이 있어 해당 축의 길이 압축을 건너뛰었다 */
    public static final String COMPRESSION_SKIPPED = "COMPRESSION_SKIPPED";

    /** 도면을 여러 장으로 나눴다 */
    public static final String SHEET_SPLIT = "SHEET_SPLIT";

    // ── IDF ──
    /**
     * IDF 의 보어 인코딩을 확정하지 못했다. 추측해서 채우면 BOM 수량과 3D 반지름이 조용히 틀리므로
     * 값을 비워 두고 원문만 {@code IDF-BORE-FIELD} 속성에 남긴다.
     */
    public static final String IDF_BORE_UNRESOLVED = "IDF_BORE_UNRESOLVED";
    /** 이어지는 조각인데 붙일 짝을 찾지 못했다 */
    public static final String IDF_ORPHAN_LEG = "IDF_ORPHAN_LEG";
}
