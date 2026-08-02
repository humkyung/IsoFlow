// PcfLexer.java — PCF 텍스트를 레코드 목록으로 쪼갠다. 규칙은 단 하나: 첫 글자가 공백이 아니면 새 레코드
package co.atools.isoflow.engine.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * PCF 는 들여쓰기로 구조를 표현한다.
 * <ul>
 *   <li>컬럼 1에서 시작하는 줄 = 새 레코드 (헤더 항목 또는 컴포넌트)</li>
 *   <li>들여쓴 줄 = 직전 레코드의 속성</li>
 * </ul>
 * 이 규칙만으로 블록 분리가 끝나므로 렉서는 의미를 전혀 모른다.
 * 의미 부여(헤더인지 컴포넌트인지, MATERIALS 섹션인지)는 {@link PcfParser} 책임이다.
 */
public final class PcfLexer {

    private PcfLexer() {
    }

    public static List<PcfRecord> lex(Reader reader) throws IOException {
        List<PcfRecord> records = new ArrayList<>();
        PcfRecord current = null;

        try (BufferedReader br = new BufferedReader(reader)) {
            String raw;
            int lineNo = 0;
            while ((raw = br.readLine()) != null) {
                lineNo++;
                // 개행/캐리지리턴은 readLine 이 제거하지만 CR 이 남는 파일이 있어 한 번 더 턴다
                String line = stripTrailing(raw);
                if (line.isBlank()) continue;

                if (!Character.isWhitespace(line.charAt(0))) {
                    String[] split = splitFirstToken(line);
                    current = new PcfRecord(split[0], split[1], lineNo);
                    records.add(current);
                } else {
                    String[] split = splitFirstToken(line.strip());
                    PcfAttribute attr = new PcfAttribute(split[0], split[1], lineNo);
                    if (current == null) {
                        // 파일 선두에 들여쓴 줄이 오는 비정상 케이스 — 버리지 않고 합성 레코드에 담는다
                        current = new PcfRecord("", "", lineNo);
                        records.add(current);
                    }
                    current.add(attr);
                }
            }
        }
        return records;
    }

    /** 줄 끝의 공백/CR 을 제거한다 */
    private static String stripTrailing(String s) {
        int end = s.length();
        while (end > 0 && (s.charAt(end - 1) == '\r' || Character.isWhitespace(s.charAt(end - 1)))) end--;
        return s.substring(0, end);
    }

    /** 첫 토큰(키워드)과 나머지(값)로 나눈다. 값의 내부 공백은 보존한다 */
    private static String[] splitFirstToken(String line) {
        int i = 0;
        while (i < line.length() && !Character.isWhitespace(line.charAt(i))) i++;
        String keyword = line.substring(0, i);
        String rest = i < line.length() ? line.substring(i).strip() : "";
        return new String[]{keyword, rest};
    }
}
