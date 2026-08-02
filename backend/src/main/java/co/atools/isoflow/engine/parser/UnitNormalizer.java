// UnitNormalizer.java — PCF 헤더의 단위 선언을 읽어 좌표/보어를 내부 표준(mm)으로 환산한다
package co.atools.isoflow.engine.parser;

/**
 * 같은 PCF 안에 {@code UNITS-BORE INCH} 와 {@code UNITS-CO-ORDS MM} 이 함께 오는 것이 정상이다.
 * 좌표와 보어의 단위계가 다르므로 반드시 따로 환산해야 한다.
 * 내부 표준은 <b>좌표 mm / 보어 mm</b> 이다.
 */
public final class UnitNormalizer {

    /** 길이 단위 → mm 환산 계수 */
    public enum LengthUnit {
        MM(1.0), CM(10.0), METRE(1000.0), INCH(25.4), FEET(304.8);

        private final double toMm;

        LengthUnit(double toMm) {
            this.toMm = toMm;
        }

        public double toMm() {
            return toMm;
        }

        /** PCF 단위 문자열을 해석한다. 모르는 값이면 null */
        public static LengthUnit parse(String s) {
            if (s == null) return null;
            return switch (s.trim().toUpperCase()) {
                case "MM", "MILLIMETRE", "MILLIMETER" -> MM;
                case "CM", "CENTIMETRE", "CENTIMETER" -> CM;
                case "M", "METRE", "METER" -> METRE;
                case "INCH", "IN", "INCHES" -> INCH;
                case "FEET", "FT", "FOOT" -> FEET;
                default -> null;
            };
        }
    }

    private LengthUnit coordUnit = LengthUnit.MM;
    private LengthUnit boreUnit = LengthUnit.MM;
    private boolean coordDeclared;
    private boolean boreDeclared;

    /** UNITS-CO-ORDS 선언을 반영한다. 해석 실패 시 false 를 반환하고 기존 값을 유지한다 */
    public boolean declareCoordUnit(String s) {
        LengthUnit u = LengthUnit.parse(s);
        if (u == null) return false;
        coordUnit = u;
        coordDeclared = true;
        return true;
    }

    /** UNITS-BORE 선언을 반영한다. 해석 실패 시 false */
    public boolean declareBoreUnit(String s) {
        LengthUnit u = LengthUnit.parse(s);
        if (u == null) return false;
        boreUnit = u;
        boreDeclared = true;
        return true;
    }

    /** 좌표 값을 mm 로 환산한다 */
    public double coordToMm(double v) {
        return v * coordUnit.toMm();
    }

    /** 보어 값을 mm 로 환산한다. PCF 의 보어는 호칭경(NPS)이라 INCH 면 ×25.4 가 곧 호칭 mm 이다 */
    public double boreToMm(double v) {
        return v * boreUnit.toMm();
    }

    public LengthUnit coordUnit() {
        return coordUnit;
    }

    public LengthUnit boreUnit() {
        return boreUnit;
    }

    /** 헤더에 단위 선언이 없었는지 — 진단에서 경고를 내기 위해 쓴다 */
    public boolean coordUnitDeclared() {
        return coordDeclared;
    }

    public boolean boreUnitDeclared() {
        return boreDeclared;
    }
}
