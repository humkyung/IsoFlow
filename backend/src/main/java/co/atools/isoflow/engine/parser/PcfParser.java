// PcfParser.java — PCF 레코드를 중립 도메인 모델(IR)로 변환한다. 단위 정규화와 미지 키워드 보존을 담당
package co.atools.isoflow.engine.parser;

import co.atools.isoflow.engine.diagnostic.DiagnosticCodes;
import co.atools.isoflow.engine.diagnostic.Diagnostics;
import co.atools.isoflow.engine.model.ComponentType;
import co.atools.isoflow.engine.model.MaterialItem;
import co.atools.isoflow.engine.model.Pipeline;
import co.atools.isoflow.engine.model.PipingComponent;
import co.atools.isoflow.engine.model.Port;
import co.atools.isoflow.engine.model.PortKind;
import co.atools.isoflow.engine.model.Vec3;

import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Set;

public final class PcfParser {

    /** 파이프라인 수준 헤더 키워드 — 컴포넌트가 아니다 */
    private static final Set<String> HEADER_KEYWORDS = Set.of(
            "ISOGEN-FILES", "UNITS-BORE", "UNITS-CO-ORDS", "UNITS-WEIGHT",
            "UNITS-BOLT-DIA", "UNITS-BOLT-LENGTH", "PIPELINE-REFERENCE",
            "DATE-DMY", "REVISION", "AREA", "PROJECT-IDENTIFIER", "START-CO-ORDS");

    private final Diagnostics diag = new Diagnostics();
    private final UnitNormalizer units = new UnitNormalizer();
    private final Pipeline pipeline = new Pipeline();

    /** MATERIALS 섹션 안에서는 컬럼 1의 ITEM-CODE 가 컴포넌트가 아니라 자재 항목이다 */
    private boolean inMaterials;

    public static ParseResult parse(Reader reader) throws IOException {
        return new PcfParser().run(PcfLexer.lex(reader));
    }

    private ParseResult run(List<PcfRecord> records) {
        for (PcfRecord r : records) {
            String kw = r.keyword().toUpperCase();

            if ("MATERIALS".equals(kw)) {
                inMaterials = true;
                continue;
            }
            if (inMaterials) {
                if ("ITEM-CODE".equals(kw)) {
                    readMaterial(r);
                    continue;
                }
                // MATERIALS 이후에 다른 레코드가 오면 섹션이 끝난 것으로 본다
                inMaterials = false;
            }

            if (HEADER_KEYWORDS.contains(kw)) {
                readHeader(kw, r);
            } else {
                readComponent(kw, r);
            }
        }

        if (pipeline.lineNumber() == null) {
            diag.warn(DiagnosticCodes.NO_PIPELINE_REFERENCE, 0);
        }
        if (!units.coordUnitDeclared()) {
            diag.warn(DiagnosticCodes.UNITS_NOT_DECLARED, 0, "kind", "CO-ORDS", "assumed", "MM");
        }
        if (!units.boreUnitDeclared()) {
            diag.warn(DiagnosticCodes.UNITS_NOT_DECLARED, 0, "kind", "BORE", "assumed", "MM");
        }
        return new ParseResult(pipeline, diag);
    }

    // ─────────────────────────── 헤더 ───────────────────────────

    private void readHeader(String kw, PcfRecord r) {
        switch (kw) {
            case "UNITS-CO-ORDS" -> {
                if (!units.declareCoordUnit(r.args())) {
                    diag.warn(DiagnosticCodes.UNKNOWN_UNIT, r.lineNo(), "kind", "CO-ORDS", "value", r.args());
                }
            }
            case "UNITS-BORE" -> {
                if (!units.declareBoreUnit(r.args())) {
                    diag.warn(DiagnosticCodes.UNKNOWN_UNIT, r.lineNo(), "kind", "BORE", "value", r.args());
                }
            }
            case "PIPELINE-REFERENCE" -> {
                pipeline.setLineNumber(r.args());
                readPipelineAttributes(r);
            }
            case "REVISION" -> pipeline.setRevision(r.args());
            case "AREA" -> pipeline.setArea(r.args());
            // 나머지 헤더는 원문 보존만 한다
            default -> pipeline.putAttr(kw, r.args());
        }
    }

    /** PIPELINE-REFERENCE 아래 들여쓴 속성들 (PIPING-SPEC, ATTRIBUTEnn …) */
    private void readPipelineAttributes(PcfRecord r) {
        for (PcfAttribute a : r.attributes()) {
            switch (a.keyword().toUpperCase()) {
                case "PIPING-SPEC" -> pipeline.setPipingSpec(a.value());
                case "NOMINAL-CLASS" -> pipeline.setNominalClass(a.value());
                case "AREA" -> pipeline.setArea(a.value());
                case "REVISION" -> pipeline.setRevision(a.value());
                default -> pipeline.putAttr(a.keyword().toUpperCase(), a.value());
            }
        }
    }

    // ────────────────────────── MATERIALS ──────────────────────────

    private void readMaterial(PcfRecord r) {
        String code = r.args();
        if (code.isEmpty()) return;
        MaterialItem m = pipeline.material(code);
        for (PcfAttribute a : r.attributes()) {
            if ("DESCRIPTION".equalsIgnoreCase(a.keyword())) {
                m.setDescription(a.value());
            } else {
                m.putAttr(a.keyword().toUpperCase(), a.value());
            }
        }
    }

    // ────────────────────────── 컴포넌트 ──────────────────────────

    private void readComponent(String kw, PcfRecord r) {
        ComponentType type = ComponentType.fromKeyword(kw);
        if (type == ComponentType.UNKNOWN) {
            diag.warn(DiagnosticCodes.UNKNOWN_COMPONENT, r.lineNo(), "keyword", kw);
        }
        PipingComponent c = new PipingComponent(type, kw);

        int endOrdinal = 0;
        for (PcfAttribute a : r.attributes()) {
            String ak = a.keyword().toUpperCase();
            switch (ak) {
                case "COMPONENT-IDENTIFIER" -> c.setSourceIndex(parseInt(a, c));
                case "MASTER-COMPONENT-IDENTIFIER" -> c.setMasterComponentIndex(parseInt(a, c));
                case "END-POINT" -> {
                    Port p = readPoint(a, PortKind.END, endOrdinal);
                    if (p != null) {
                        c.addPort(p);
                        endOrdinal++;
                    }
                }
                case "CENTRE-POINT", "CENTER-POINT" -> addIfPresent(c, readPoint(a, PortKind.CENTRE, 0));
                case "BRANCH1-POINT" -> addIfPresent(c, readPoint(a, PortKind.BRANCH1, 0));
                case "BRANCH2-POINT" -> addIfPresent(c, readPoint(a, PortKind.BRANCH2, 0));
                case "CO-ORDS" -> addIfPresent(c, readPoint(a, PortKind.COORD, 0));
                case "SKEY" -> c.setSkey(a.value());
                case "ITEM-CODE" -> c.setItemCode(a.value());
                case "ITEM-DESCRIPTION" -> c.setItemDescription(a.value());
                case "UCI" -> c.setUci(a.value());
                case "WEIGHT" -> c.setWeight(parseDouble(a, c));
                // 절단 길이는 좌표 단위계를 따른다
                case "CUT-PIECE-LENGTH" -> {
                    Double v = parseDouble(a, c);
                    if (v != null) c.setCutPieceLength(units.coordToMm(v));
                }
                // PCF 의 ANGLE 은 1/100 도 단위 정수다 (4500 = 45.00°)
                case "ANGLE" -> {
                    Double v = parseDouble(a, c);
                    if (v != null) c.setAngleDeg(v * 0.01);
                }
                case "MATERIAL-LIST" -> {
                    if ("EXCLUDE".equalsIgnoreCase(a.value())) c.setExcludedFromBom(true);
                    else c.putAttr(ak, a.value());
                }
                // 모르는 속성은 원문 그대로 보존한다 — 벤더 확장 대응
                default -> c.putAttr(ak, a.value());
            }
        }

        // 배관 경로를 이루는 컴포넌트인데 연결점이 없으면 도면을 그릴 수 없다
        if (type.isRoutingComponent() && c.connectablePorts().isEmpty()) {
            diag.error(DiagnosticCodes.MISSING_ENDPOINTS, r.lineNo(), "component", c.label());
        }
        pipeline.addComponent(c);
    }

    private void addIfPresent(PipingComponent c, Port p) {
        if (p != null) c.addPort(p);
    }

    /**
     * 좌표 속성을 포트로 읽는다.
     * 형식: {@code x y z [bore] [endType]} — bore/endType 은 없을 수 있다(CENTRE-POINT 등).
     */
    private Port readPoint(PcfAttribute a, PortKind kind, int ordinal) {
        String[] t = a.tokens();
        if (t.length < 3) {
            diag.error(DiagnosticCodes.BAD_COORDINATE, a.lineNo(), "keyword", a.keyword(), "value", a.value());
            return null;
        }
        Double x = toDouble(t[0]), y = toDouble(t[1]), z = toDouble(t[2]);
        if (x == null || y == null || z == null) {
            diag.error(DiagnosticCodes.BAD_COORDINATE, a.lineNo(), "keyword", a.keyword(), "value", a.value());
            return null;
        }
        Vec3 pos = new Vec3(units.coordToMm(x), units.coordToMm(y), units.coordToMm(z));

        Double boreMm = null;
        String endType = null;
        if (t.length >= 4) {
            Double bore = toDouble(t[3]);
            if (bore != null) {
                boreMm = units.boreToMm(bore);
            } else {
                // 4번째 토큰이 숫자가 아니면 엔드타입으로 본다
                endType = t[3];
            }
        }
        if (t.length >= 5 && endType == null) {
            endType = t[4];
        }
        return new Port(kind, ordinal, pos, boreMm, endType);
    }

    private Integer parseInt(PcfAttribute a, PipingComponent c) {
        try {
            return Integer.valueOf(a.value().trim());
        } catch (NumberFormatException e) {
            diag.warn(DiagnosticCodes.BAD_NUMBER, a.lineNo(),
                    "keyword", a.keyword(), "value", a.value(), "component", c.label());
            return null;
        }
    }

    private Double parseDouble(PcfAttribute a, PipingComponent c) {
        Double v = toDouble(a.value().trim());
        if (v == null) {
            diag.warn(DiagnosticCodes.BAD_NUMBER, a.lineNo(),
                    "keyword", a.keyword(), "value", a.value(), "component", c.label());
        }
        return v;
    }

    private static Double toDouble(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Double.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
