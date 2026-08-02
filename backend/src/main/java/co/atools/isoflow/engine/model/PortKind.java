// PortKind.java — 컴포넌트 포트의 종류. PCF 좌표 키워드와 1:1 대응한다
package co.atools.isoflow.engine.model;

public enum PortKind {
    /** END-POINT — 실제 배관이 연결되는 끝점 */
    END,
    /** CENTRE-POINT — 엘보/티의 중심. 연결점이 아니라 작도용 기준점이다 */
    CENTRE,
    /** BRANCH1-POINT — 분기 연결점 */
    BRANCH1,
    /** BRANCH2-POINT — 크로스의 두 번째 분기 */
    BRANCH2,
    /** CO-ORDS — FLOW-ARROW / END-CONNECTION-PIPELINE 등 단일 위치 표기 */
    COORD;

    /** 위상 그래프에서 연결점으로 취급할 포트인지 — CENTRE/COORD 는 제외한다 */
    public boolean isConnectable() {
        return this == END || this == BRANCH1 || this == BRANCH2;
    }
}
