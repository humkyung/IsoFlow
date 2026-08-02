// IsoStyle.java — 등각도 생성 설정. 지금까지 코드에 흩어져 있던 상수를 한 곳으로 모은 것
package co.atools.isoflow.engine.style;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 프로젝트마다 도면 관례가 다르므로 값을 밖으로 뺀다.
 * JSON 으로 주고받을 수 있고(DB {@code iso_style.settings}), 빠진 항목은 기본값으로 채운다.
 *
 * <p><b>기본값은 지금까지의 동작과 같다</b> — 설정을 도입해도 기존 도면이 달라지지 않아야 한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IsoStyle(Sheet sheet, Symbols symbols, Dimensions dimensions,
                       Compression compression, Display display) {

    public static IsoStyle defaults() {
        return new IsoStyle(Sheet.defaults(), Symbols.defaults(), Dimensions.defaults(),
                Compression.defaults(), Display.defaults());
    }

    /** null 필드를 기본값으로 메운다 — 부분 설정만 보내도 되게 한다 */
    public IsoStyle withDefaults() {
        return new IsoStyle(
                sheet == null ? Sheet.defaults() : sheet.withDefaults(),
                symbols == null ? Symbols.defaults() : symbols.withDefaults(),
                dimensions == null ? Dimensions.defaults() : dimensions.withDefaults(),
                compression == null ? Compression.defaults() : compression.withDefaults(),
                display == null ? Display.defaults() : display.withDefaults());
    }

    // ─────────────────────────── 용지 ───────────────────────────

    /**
     * @param size            용지 규격 이름 (A4/A3/A2/A1) 또는 null
     * @param widthMm         직접 지정 시 폭. size 가 있으면 무시
     * @param marginMm        도곽 여백
     * @param tableBandMm     아래 표 띠 높이. 0 이면 표를 두지 않는다.
     *                        <b>우측 세로 칸이 아니라 아래 띠다</b> — 우측에 두면 도면 영역이
     *                        세로로 길어지는데 등각도 내용은 가로로 길어서 세로가 30~60% 남았다
     * @param titleBlockMm    타이틀 블록 높이
     * @param maxLabelCrowding 자리를 못 찾아 겹친 채 놓인 라벨 비율이 이 값을 넘으면 시트를 나눈다.
     *                         0 이하면 나누지 않는다.
     *                         <b>문자 크기가 아니라 밀도를 본다</b> — 심볼이 도면 크기에 비례해
     *                         커지므로 종이 위 문자 높이는 도면이 커져도 거의 그대로다(실측 3.2~7.5mm).
     *                         실제로 나빠지는 것은 라벨이 서로 밀려나는 정도다
     * @param maxSheets        나눌 수 있는 최대 장수. 개선되지 않는데 계속 쪼개지 않기 위한 상한
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Sheet(String size, Double widthMm, Double heightMm,
                        Double marginMm, Double tableBandMm, Double titleBlockMm,
                        Double maxLabelCrowding, Integer maxSheets) {

        public static Sheet defaults() {
            return new Sheet("A3", null, null, 10.0, 70.0, 34.0, 0.15, 6);
        }

        public Sheet withDefaults() {
            Sheet d = defaults();
            return new Sheet(
                    size == null ? d.size() : size,
                    widthMm, heightMm,
                    marginMm == null ? d.marginMm() : marginMm,
                    tableBandMm == null ? d.tableBandMm() : tableBandMm,
                    titleBlockMm == null ? d.titleBlockMm() : titleBlockMm,
                    maxLabelCrowding == null ? d.maxLabelCrowding() : maxLabelCrowding,
                    maxSheets == null ? d.maxSheets() : maxSheets);
        }

        /** 규격 이름 → 가로 mm (가로 방향 기준). 모르는 이름이면 A3 */
        public double resolvedWidthMm() {
            if (widthMm != null) return widthMm;
            return switch (size == null ? "A3" : size.toUpperCase()) {
                case "A4" -> 297;
                case "A2" -> 594;
                case "A1" -> 841;
                default -> 420;
            };
        }

        public double resolvedHeightMm() {
            if (heightMm != null) return heightMm;
            return switch (size == null ? "A3" : size.toUpperCase()) {
                case "A4" -> 210;
                case "A2" -> 420;
                case "A1" -> 594;
                default -> 297;
            };
        }
    }

    // ─────────────────────────── 심볼 ───────────────────────────

    /**
     * @param unitRatio 도면 대각선 대비 심볼 크기 비율
     * @param minUnitMm 심볼 크기 하한
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Symbols(Double unitRatio, Double minUnitMm) {

        public static Symbols defaults() {
            return new Symbols(0.018, 1.0);
        }

        public Symbols withDefaults() {
            Symbols d = defaults();
            return new Symbols(unitRatio == null ? d.unitRatio() : unitRatio,
                    minUnitMm == null ? d.minUnitMm() : minUnitMm);
        }
    }

    // ─────────────────────────── 치수 ───────────────────────────

    /**
     * @param minIntervalMm     이보다 짧은 칸은 이웃에 합친다 (절대 하한)
     * @param minIntervalRatio  도면 대각선 대비 최소 칸 비율.
     *                          큰 도면에서 절대값만 쓰면 잔치수가 남는다
     * @param offsetUnits       치수선을 배관에서 띄우는 거리 (symbolUnit 배수)
     * @param stepUnits         겹칠 때 추가로 밀어내는 간격
     * @param textHeightUnits   치수 문자 높이
     * @param decimals          치수값 소수 자릿수
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Dimensions(Double minIntervalMm, Double minIntervalRatio,
                             Double offsetUnits, Double stepUnits,
                             Double textHeightUnits, Integer decimals) {

        public static Dimensions defaults() {
            return new Dimensions(50.0, 0.004, 3.5, 2.6, 1.1, 0);
        }

        public Dimensions withDefaults() {
            Dimensions d = defaults();
            return new Dimensions(
                    minIntervalMm == null ? d.minIntervalMm() : minIntervalMm,
                    minIntervalRatio == null ? d.minIntervalRatio() : minIntervalRatio,
                    offsetUnits == null ? d.offsetUnits() : offsetUnits,
                    stepUnits == null ? d.stepUnits() : stepUnits,
                    textHeightUnits == null ? d.textHeightUnits() : textHeightUnits,
                    decimals == null ? d.decimals() : decimals);
        }

        /** 도면 크기를 반영한 실제 최소 칸 길이 */
        public double effectiveMinIntervalMm(double drawingExtentMm) {
            return Math.max(minIntervalMm, drawingExtentMm * minIntervalRatio);
        }
    }

    // ─────────────────────────── 길이 압축 ───────────────────────────

    /**
     * @param enabled  압축 사용 여부
     * @param maxGapMm 이 길이를 넘는 구간부터 로그로 누른다
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Compression(Boolean enabled, Double maxGapMm) {

        public static Compression defaults() {
            return new Compression(true, 2000.0);
        }

        public Compression withDefaults() {
            Compression d = defaults();
            return new Compression(enabled == null ? d.enabled() : enabled,
                    maxGapMm == null ? d.maxGapMm() : maxGapMm);
        }
    }

    // ─────────────────────────── 표시 항목 ───────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Display(Boolean weldNumbers, Boolean coordinateTags, Boolean continuations,
                          Boolean northArrow, Boolean lineNumber, Boolean skewTriangles,
                          Boolean dimensions, Boolean tables, Boolean crossingBreaks,
                          Boolean details) {

        public static Display defaults() {
            return new Display(true, true, true, true, true, true, true, true, true, true);
        }

        public Display withDefaults() {
            Display d = defaults();
            return new Display(
                    weldNumbers == null ? d.weldNumbers() : weldNumbers,
                    coordinateTags == null ? d.coordinateTags() : coordinateTags,
                    continuations == null ? d.continuations() : continuations,
                    northArrow == null ? d.northArrow() : northArrow,
                    lineNumber == null ? d.lineNumber() : lineNumber,
                    skewTriangles == null ? d.skewTriangles() : skewTriangles,
                    dimensions == null ? d.dimensions() : dimensions,
                    tables == null ? d.tables() : tables,
                    crossingBreaks == null ? d.crossingBreaks() : crossingBreaks,
                    details == null ? d.details() : details);
        }
    }
}
