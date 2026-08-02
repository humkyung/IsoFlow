// Diagnostics.java — 진단 수집기. 엔진 각 단계가 여기에 문제를 쌓는다
package co.atools.isoflow.engine.diagnostic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Diagnostics {

    private final List<Diagnostic> items = new ArrayList<>();

    public void add(Diagnostic d) {
        items.add(d);
    }

    public void warn(String code, int lineNo, Object... kv) {
        items.add(Diagnostic.warn(code, lineNo, kv));
    }

    public void error(String code, int lineNo, Object... kv) {
        items.add(Diagnostic.error(code, lineNo, kv));
    }

    public void info(String code, int lineNo, Object... kv) {
        items.add(Diagnostic.info(code, lineNo, kv));
    }

    public List<Diagnostic> items() {
        return Collections.unmodifiableList(items);
    }

    public List<Diagnostic> bySeverity(Diagnostic.Severity s) {
        return items.stream().filter(d -> d.severity() == s).toList();
    }

    public boolean hasErrors() {
        return items.stream().anyMatch(d -> d.severity() == Diagnostic.Severity.ERROR);
    }

    public int count(String code) {
        return (int) items.stream().filter(d -> d.code().equals(code)).count();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    @Override
    public String toString() {
        return items.toString();
    }
}
