// SymbolShape.java — 심볼 형상 하나 (요소 목록 + 메타)
package co.atools.isoflow.engine.symbol;

import java.util.List;

/**
 * @param name           사람이 읽는 이름
 * @param elements       심볼 로컬 좌표의 요소들
 * @param userDefinable  표준 SKEY 가 없어 프로젝트에서 배정해야 하는 형상
 */
public record SymbolShape(String name, List<SymbolElement> elements, boolean userDefinable) {
}
