// PcfRecord.java — PCF 최상위 레코드(컬럼 1에서 시작하는 줄)와 그에 딸린 들여쓴 속성들
package co.atools.isoflow.engine.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class PcfRecord {

    private final String keyword;
    private final String args;
    private final int lineNo;
    private final List<PcfAttribute> attributes = new ArrayList<>();

    public PcfRecord(String keyword, String args, int lineNo) {
        this.keyword = keyword;
        this.args = args;
        this.lineNo = lineNo;
    }

    public String keyword() {
        return keyword;
    }

    /** 키워드 뒤 같은 줄의 값 (예: `PIPELINE-REFERENCE P1001-CWR-…`) */
    public String args() {
        return args;
    }

    public int lineNo() {
        return lineNo;
    }

    public List<PcfAttribute> attributes() {
        return attributes;
    }

    void add(PcfAttribute a) {
        attributes.add(a);
    }

    /** 해당 키워드의 첫 속성을 찾는다 */
    public Optional<PcfAttribute> attr(String kw) {
        return attributes.stream().filter(a -> a.keyword().equals(kw)).findFirst();
    }

    /** 해당 키워드의 모든 속성을 순서대로 반환한다 (END-POINT 처럼 반복되는 것) */
    public List<PcfAttribute> attrs(String kw) {
        return attributes.stream().filter(a -> a.keyword().equals(kw)).toList();
    }

    @Override
    public String toString() {
        return "%s(line %d, attrs=%d)".formatted(keyword, lineNo, attributes.size());
    }
}
