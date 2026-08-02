// SkeyTable.java — SKEY 를 형상으로 해석한다. `**` 엔드타입 확장 규칙이 핵심
package co.atools.isoflow.engine.symbol;

import java.util.Locale;

/**
 * 조회 순서 (docs/symbol-set.md 4장):
 * <ol>
 *   <li>정확히 일치하는 항목 우선 (FLWN, WTBW 같은 통짜 코드)</li>
 *   <li>알려진 endType 으로 끝나면 앞 2자 + {@code **} 패턴으로 조회 (ELBW → EL** + BW)</li>
 *   <li>없으면 PCF 컴포넌트 타입으로 폴백</li>
 *   <li>그래도 실패하면 미해결로 표시한다 — 조용히 버리지 않는다</li>
 * </ol>
 */
public final class SkeyTable {

    /**
     * @param entry      찾은 매핑 (없으면 null)
     * @param endType    분리된 접합 방식 코드 (없으면 null)
     * @param resolvedBy 어떤 규칙으로 찾았는지 — 진단에 쓴다
     */
    public record Resolution(SkeyEntry entry, String endType, Source resolvedBy) {
        public boolean found() {
            return entry != null;
        }
    }

    public enum Source {EXACT, PATTERN, PCF_TYPE, UNRESOLVED}

    private final SymbolSet set;

    public SkeyTable(SymbolSet set) {
        this.set = set;
    }

    public static SkeyTable standard() {
        return new SkeyTable(SymbolSet.standard());
    }

    /**
     * @param skey    PCF 의 SKEY 값 (null 가능)
     * @param pcfType PCF 컴포넌트 키워드 — SKEY 조회 실패 시 폴백에 쓴다
     */
    public Resolution resolve(String skey, String pcfType) {
        if (skey != null && !skey.isBlank()) {
            String code = skey.trim().toUpperCase(Locale.ROOT);

            SkeyEntry exact = set.skeys().get(code);
            if (exact != null) return new Resolution(exact, endTypeOf(code), Source.EXACT);

            // 뒤 2자를 `**` 로 바꿔 패턴 조회. 원문이 이미 `**` 인 경우도 여기서 처리된다
            if (code.length() >= 4) {
                String base = code.substring(0, code.length() - 2);
                String tail = code.substring(code.length() - 2);
                SkeyEntry pattern = set.skeys().get(base + "**");
                if (pattern != null) {
                    return new Resolution(pattern, "**".equals(tail) ? null : tail, Source.PATTERN);
                }
            }
        }
        if (pcfType != null) {
            String shape = set.fallbackByPcfType().get(pcfType.toUpperCase(Locale.ROOT));
            if (shape != null) {
                return new Resolution(
                        new SkeyEntry(skey, shape, null, pcfType, "fallback", null, false),
                        null, Source.PCF_TYPE);
            }
        }
        return new Resolution(null, null, Source.UNRESOLVED);
    }

    /** SKEY 끝 2자가 알려진 endType 이면 그것을 돌려준다 */
    private String endTypeOf(String code) {
        if (code.length() < 4) return null;
        String tail = code.substring(code.length() - 2);
        return set.endTypes().contains(tail) ? tail : null;
    }

    public SymbolSet symbolSet() {
        return set;
    }
}
