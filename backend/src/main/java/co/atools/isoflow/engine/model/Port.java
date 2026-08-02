// Port.java — 컴포넌트의 연결점. 좌표는 mm, 보어도 mm 로 정규화된 상태다
package co.atools.isoflow.engine.model;

public final class Port {

    private final PortKind kind;
    private final int ordinal;
    private Vec3 position;
    private final Double boreMm;
    private final String endType;

    /** 위상 해석이 채우는 조인트 식별자. 같은 값이면 같은 접합점이다 */
    private String jointKey;

    public Port(PortKind kind, int ordinal, Vec3 position, Double boreMm, String endType) {
        this.kind = kind;
        this.ordinal = ordinal;
        this.position = position;
        this.boreMm = boreMm;
        this.endType = endType;
    }

    public PortKind kind() {
        return kind;
    }

    public int ordinal() {
        return ordinal;
    }

    public Vec3 position() {
        return position;
    }

    /** 좌표 리베이스에서만 호출한다 — 그 외에는 포트 좌표를 바꾸지 않는다 */
    public void setPosition(Vec3 p) {
        this.position = p;
    }

    /** 보어(mm). CENTRE-POINT 처럼 보어가 없는 포트는 null */
    public Double boreMm() {
        return boreMm;
    }

    /** 접합 방식(BW/SW/SC/FL…). PCF 에 표기가 없으면 null */
    public String endType() {
        return endType;
    }

    public String jointKey() {
        return jointKey;
    }

    public void setJointKey(String jointKey) {
        this.jointKey = jointKey;
    }

    /**
     * 위상 그래프에서 연결점으로 취급할지 여부.
     *
     * <p>CENTRE-POINT 는 보통 엘보/티의 호 중심일 뿐 연결점이 아니다.
     * 다만 <b>보어가 붙은 CENTRE-POINT 는 헤더 배관에 대한 접속점</b>이다 — OLET 이 그렇다.
     * OLET 은 END-POINT 없이 CENTRE-POINT(헤더 쪽) + BRANCH1-POINT(분기 쪽)로만 구성된다.
     * 코퍼스 전수 확인 결과 보어를 가진 CENTRE-POINT 는 OLET 뿐이었다.
     */
    public boolean isConnectable() {
        return kind.isConnectable() || (kind == PortKind.CENTRE && boreMm != null);
    }

    @Override
    public String toString() {
        return "%s#%d %s bore=%s".formatted(kind, ordinal, position, boreMm);
    }
}
