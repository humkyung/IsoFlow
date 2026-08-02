// PcfAttribute.java — PCF 레코드의 들여쓴 속성 한 줄 (키워드 + 원문 값)
package co.atools.isoflow.engine.parser;

/**
 * @param keyword 속성 키워드 (END-POINT, SKEY, ITEM-CODE …)
 * @param value   키워드 뒤의 원문. 공백은 trim 하지만 내부 공백은 보존한다
 *                (ITEM-DESCRIPTION 처럼 값에 공백이 있는 속성 때문)
 * @param lineNo  1-base 원본 줄 번호. 진단 메시지에 쓴다
 */
public record PcfAttribute(String keyword, String value, int lineNo) {

    /** 값을 공백으로 분리한다. 값이 비어 있으면 빈 배열 */
    public String[] tokens() {
        return value.isEmpty() ? new String[0] : value.split("\\s+");
    }

    /** 값이 없는 플래그성 속성인지 (FABRICATION-ITEM, CONTINUATION …) */
    public boolean isFlag() {
        return value.isEmpty();
    }
}
