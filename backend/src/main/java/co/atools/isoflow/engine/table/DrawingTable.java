// DrawingTable.java — 도면에 얹거나 CSV 로 내보내는 표의 공통 모델
package co.atools.isoflow.engine.table;

import java.util.List;

/**
 * BOM·절단 리스트·용접 리스트가 모두 같은 모양이라 하나로 묶는다.
 * 도면 표 렌더링과 CSV 출력이 표 종류를 몰라도 되게 하기 위함이다.
 *
 * @param kind    표 종류 식별자 (BOM / CUTLIST / WELDLIST)
 * @param title   표 제목
 * @param headers 열 제목
 * @param rows    행 — 각 행의 길이는 headers 와 같아야 한다
 */
public record DrawingTable(String kind, String title, List<String> headers, List<List<String>> rows) {

    public static final String BOM = "BOM";
    public static final String CUT_LIST = "CUTLIST";
    public static final String WELD_LIST = "WELDLIST";

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    public int columnCount() {
        return headers.size();
    }
}
