// SkeyEntry.java — SKEY 하나의 매핑 정보
package co.atools.isoflow.engine.symbol;

/**
 * @param skey      SKEY 코드 또는 `**` 패턴 (예: FLWN, VT**)
 * @param shape     symbols-2d.json 의 형상 이름
 * @param overlay   본체 위에 덧그릴 액추에이터 형상 (없으면 null)
 * @param pcfType   대응 PCF 컴포넌트 타입
 * @param category  flange / valve / weld / annotation …
 * @param desc      한국어 설명
 * @param flowArrow 유동 방향 화살표를 함께 그리는 컴포넌트인지
 */
public record SkeyEntry(String skey, String shape, String overlay, String pcfType,
                        String category, String desc, boolean flowArrow) {
}
