// IdfParser.java — PDS 계열 IDF 를 PCF 와 동일한 IR 로 흡수한다
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * IDF 는 PCF 와 성격이 아주 다르다.
 * <ul>
 *   <li>고정 컬럼 수치 레코드 — 키워드가 없다</li>
 *   <li><b>꺾이는 부품이 다리별로 쪼개져 있다</b> (엘보 35/36, 티 45/46/47)</li>
 *   <li><b>그 조각들이 파일에서 붙어 있지 않다</b> — 실샘플에서 티의 47 이 파일 끝에 떨어져 있었다.
 *       그래서 순차 병합이 아니라 좌표를 공유하는 조각끼리 묶는다</li>
 *   <li>자재 정보는 파일 끝 텍스트 블록에 있고 컴포넌트는 <b>순번</b>으로 가리킨다</li>
 *   <li>긴 문자열은 13자씩 잘려 {@code -1} 레코드로 이어진다</li>
 * </ul>
 *
 * <p>정식 스펙 없이 실샘플에서 역공학한 것이라, 확실하지 않은 항목은
 * <b>추측해서 채우지 않고</b> 진단으로 남긴다. 특히 보어가 그렇다.
 */
public final class IdfParser {

    private static final Pattern INTEGER = Pattern.compile("-?\\d+");
    /** 같은 점으로 볼 허용오차(mm) */
    private static final double TOLERANCE_MM = 0.5;

    private final Diagnostics diag = new Diagnostics();
    private final Pipeline pipeline = new Pipeline();
    private final List<String> itemCodes = new ArrayList<>();
    private final List<String> descriptions = new ArrayList<>();

    private boolean boreWarned;

    public static ParseResult parse(Reader reader) throws IOException {
        return new IdfParser().run(reader);
    }

    /** 컴포넌트 레코드 한 줄 */
    private record Raw(int code, Vec3 start, Vec3 end, Integer itemIndex,
                       String boreField, String skey, Double angleDeg, int lineNo) {
        double length() {
            return start.distanceTo(end);
        }
    }

    /** 여러 조각이 모여 하나가 되는 부품 */
    private static final class Group {
        final int headCode;
        final ComponentType type;
        final List<Raw> legs = new ArrayList<>();

        Group(int headCode, ComponentType type) {
            this.headCode = headCode;
            this.type = type;
        }

        boolean touches(Raw r) {
            for (Raw l : legs) {
                if (near(l.start(), r.start()) || near(l.start(), r.end())
                        || near(l.end(), r.start()) || near(l.end(), r.end())) return true;
            }
            return false;
        }
    }

    private ParseResult run(Reader reader) throws IOException {
        List<String[]> texts = new ArrayList<>();
        List<Raw> raws = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(reader)) {
            String line;
            int lineNo = 0;
            while ((line = br.readLine()) != null) {
                lineNo++;
                String s = line.stripTrailing();
                if (s.isBlank()) continue;

                Integer code = codeOf(s);
                if (code == null) continue;                       // 헤더/옵션 블록
                if (code < 0) {
                    readText(texts, code, s);
                    continue;
                }
                if (!s.contains(",")) continue;                   // 컴포넌트 레코드가 아니다
                if (IdfRecordMap.isControl(code)) continue;

                Raw r = readFields(code, s, lineNo);
                if (r != null) raws.add(r);
            }
        }

        applyTexts(texts);
        for (Group g : groupLegs(raws)) buildComponent(g);

        if (pipeline.lineNumber() == null) diag.warn(DiagnosticCodes.NO_PIPELINE_REFERENCE, 0);
        return new ParseResult(pipeline, diag);
    }

    // ─────────────────────────── 조각 묶기 ───────────────────────────

    /**
     * 머리 코드로 그룹을 열고, 이어지는 조각은 <b>좌표를 공유하는</b> 그룹에 붙인다.
     * 파일 순서를 믿으면 안 된다 — 실샘플에서 티의 세 번째 조각이 멀리 떨어져 있었다.
     */
    private List<Group> groupLegs(List<Raw> raws) {
        List<Group> groups = new ArrayList<>();

        for (Raw r : raws) {
            if (IdfRecordMap.continuesComponent(r.code())) {
                Integer head = IdfRecordMap.headOf(r.code());
                Group target = null;
                // 뒤에서부터 찾아 가장 가까운(최근) 그룹에 붙인다
                for (int i = groups.size() - 1; i >= 0; i--) {
                    Group g = groups.get(i);
                    if (g.headCode == head && g.touches(r)) {
                        target = g;
                        break;
                    }
                }
                if (target == null) {
                    // 짝을 못 찾았다 — 조용히 버리지 않고 단독 그룹으로 남긴다
                    diag.warn(DiagnosticCodes.IDF_ORPHAN_LEG, r.lineNo(), "record", r.code());
                    target = new Group(head, IdfRecordMap.typeOf(head));
                    groups.add(target);
                }
                target.legs.add(r);
                continue;
            }

            ComponentType type = IdfRecordMap.typeOf(r.code());
            if (type == null) {
                diag.warn(DiagnosticCodes.UNKNOWN_COMPONENT, r.lineNo(), "keyword", "IDF-" + r.code());
                type = ComponentType.UNKNOWN;
            }
            Group g = new Group(r.code(), type);
            g.legs.add(r);
            groups.add(g);
        }
        return groups;
    }

    // ─────────────────────────── 컴포넌트 만들기 ───────────────────────────

    private void buildComponent(Group g) {
        Raw first = g.legs.get(0);
        PipingComponent c = new PipingComponent(g.type, "IDF-" + g.headCode);
        c.setSourceIndex(first.lineNo());
        c.putAttr("IDF-RECORD", String.valueOf(g.headCode));
        if (!first.boreField().isEmpty()) c.putAttr("IDF-BORE-FIELD", first.boreField());

        for (Raw r : g.legs) {
            if (c.skey() == null && r.skey() != null) c.setSkey(r.skey());
            if (c.angleDeg() == null && r.angleDeg() != null) c.setAngleDeg(r.angleDeg());
        }
        linkMaterial(c, first.itemIndex());

        List<Raw> legs = g.legs.stream().filter(r -> r.length() > TOLERANCE_MM).toList();

        if (g.type == ComponentType.OLET) {
            // 올렛은 모재 접속점(CENTRE) → 분기(BRANCH1) 스텁이다. 길이 0 조각은 버린다
            Raw stub = legs.isEmpty() ? first : legs.get(0);
            c.addPort(new Port(PortKind.CENTRE, 0, stub.start(), null, null));
            c.addPort(new Port(PortKind.BRANCH1, 0, stub.end(), null, null));
            pipeline.addComponent(c);
            return;
        }
        if (legs.size() <= 1) {
            Raw r = legs.isEmpty() ? first : legs.get(0);
            c.addPort(new Port(PortKind.END, 0, r.start(), null, null));
            c.addPort(new Port(PortKind.END, 1, r.end(), null, null));
            pipeline.addComponent(c);
            return;
        }

        // 여러 다리 — 공유점이 곧 모서리/분기점이다
        Vec3 hub = sharedPoint(legs);
        if (hub == null) {
            c.addPort(new Port(PortKind.END, 0, first.start(), null, null));
            c.addPort(new Port(PortKind.END, 1, first.end(), null, null));
            pipeline.addComponent(c);
            return;
        }
        List<Vec3> tips = new ArrayList<>();
        for (Raw r : legs) tips.add(near(r.start(), hub) ? r.end() : r.start());

        c.addPort(new Port(PortKind.CENTRE, 0, hub, null, null));
        if (tips.size() == 2) {
            c.addPort(new Port(PortKind.END, 0, tips.get(0), null, null));
            c.addPort(new Port(PortKind.END, 1, tips.get(1), null, null));
        } else {
            // 3갈래 이상 — 서로 가장 반대 방향인 둘이 런, 나머지가 분기다.
            // 레코드 코드로 정하면 안 된다: 실샘플에서 런이 45+47, 분기가 46 이었다
            int[] run = mostOpposite(hub, tips);
            c.addPort(new Port(PortKind.END, 0, tips.get(run[0]), null, null));
            c.addPort(new Port(PortKind.END, 1, tips.get(run[1]), null, null));
            int branch = 0;
            for (int i = 0; i < tips.size(); i++) {
                if (i == run[0] || i == run[1]) continue;
                PortKind kind = branch == 0 ? PortKind.BRANCH1 : PortKind.BRANCH2;
                c.addPort(new Port(kind, 0, tips.get(i), null, null));
                branch++;
            }
        }
        pipeline.addComponent(c);
    }

    /** 다리들이 공유하는 점 (2개 이상의 다리가 닿는 점) */
    private static Vec3 sharedPoint(List<Raw> legs) {
        List<Vec3> candidates = new ArrayList<>();
        for (Raw r : legs) {
            candidates.add(r.start());
            candidates.add(r.end());
        }
        for (Vec3 p : candidates) {
            int touching = 0;
            for (Raw r : legs) {
                if (near(r.start(), p) || near(r.end(), p)) touching++;
            }
            if (touching >= 2) return p;
        }
        return null;
    }

    /** hub 기준으로 방향이 가장 반대인 두 tip 의 인덱스 */
    private static int[] mostOpposite(Vec3 hub, List<Vec3> tips) {
        int[] best = {0, 1};
        double bestDot = Double.MAX_VALUE;
        for (int i = 0; i < tips.size(); i++) {
            for (int j = i + 1; j < tips.size(); j++) {
                double d = tips.get(i).minus(hub).normalized().dot(tips.get(j).minus(hub).normalized());
                if (d < bestDot) {
                    bestDot = d;
                    best = new int[]{i, j};
                }
            }
        }
        return best;
    }

    // ─────────────────────────── 레코드 해석 ───────────────────────────

    private static Integer codeOf(String line) {
        String head = line.length() >= 4 ? line.substring(0, 4).strip() : line.strip();
        if (head.isEmpty() || !INTEGER.matcher(head).matches()) return null;
        return Integer.valueOf(head);
    }

    /** 레이아웃: {@code code x1 y1 z1 x2 y2 z2 gfx itemIndex, ?, boreField, SKEY, angle trailing} */
    private Raw readFields(int code, String line, int lineNo) {
        String body = line.substring(4);
        int comma = body.indexOf(',');
        if (comma < 0) return null;

        String[] head = body.substring(0, comma).trim().split("\\s+");
        if (head.length < 7) {
            diag.error(DiagnosticCodes.BAD_COORDINATE, lineNo, "keyword", "IDF", "value", line.strip());
            return null;
        }
        double[] v = new double[6];
        for (int i = 0; i < 6; i++) {
            try {
                v[i] = Long.parseLong(head[i]) * IdfRecordMap.COORD_TO_MM;
            } catch (NumberFormatException e) {
                diag.error(DiagnosticCodes.BAD_COORDINATE, lineNo, "keyword", "IDF", "value", head[i]);
                return null;
            }
        }
        Integer itemIndex = head.length >= 8 ? toInt(head[7]) : null;

        String[] tail = body.substring(comma + 1).split(",");
        String boreField = tail.length >= 2 ? tail[1].trim() : "";
        String skey = tail.length >= 3 ? tail[2].trim() : "";
        Double angle = null;
        if (tail.length >= 4) {
            String[] t = tail[3].trim().split("\\s+");
            Integer a = t.length > 0 ? toInt(t[0]) : null;
            if (a != null && a != 0) angle = a * IdfRecordMap.ANGLE_SCALE;
        }
        // 보어 인코딩을 확정하지 못했다 — 추측해 채우면 BOM 과 3D 반지름이 조용히 틀린다
        if (!boreField.isEmpty() && !"0".equals(boreField) && !boreWarned) {
            boreWarned = true;
            diag.warn(DiagnosticCodes.IDF_BORE_UNRESOLVED, lineNo, "value", boreField);
        }
        return new Raw(code, new Vec3(v[0], v[1], v[2]), new Vec3(v[3], v[4], v[5]),
                itemIndex, boreField, skey.isEmpty() ? null : skey, angle, lineNo);
    }

    // ─────────────────────────── 텍스트 블록 ───────────────────────────

    private static void readText(List<String[]> texts, int code, String line) {
        String payload = line.length() > 4 ? line.substring(4).trim() : "";
        if (code == IdfRecordMap.TEXT_CONTINUATION && !texts.isEmpty()) {
            texts.get(texts.size() - 1)[1] += payload;
            return;
        }
        texts.add(new String[]{String.valueOf(code), payload});
    }

    private void applyTexts(List<String[]> texts) {
        for (String[] t : texts) {
            int code = Integer.parseInt(t[0]);
            String value = t[1];
            switch (code) {
                case IdfRecordMap.TEXT_ITEM_CODE -> itemCodes.add(value);
                case IdfRecordMap.TEXT_DESCRIPTION -> descriptions.add(value);
                case IdfRecordMap.TEXT_LINE_REFERENCE -> {
                    // "[]79QCD01-BR022-01" 처럼 대괄호 접두가 붙는다
                    if (pipeline.lineNumber() == null) pipeline.setLineNumber(value.replaceFirst("^\\[]", ""));
                }
                default -> pipeline.putAttr("IDF" + code, value);
            }
        }
        for (int i = 0; i < itemCodes.size(); i++) {
            MaterialItem m = pipeline.material(itemCodes.get(i));
            if (i < descriptions.size()) m.setDescription(descriptions.get(i));
        }
    }

    /** 자재 순번(1-base)으로 품목 코드/설명을 붙인다 */
    private void linkMaterial(PipingComponent c, Integer idx) {
        if (idx == null || idx < 1 || idx > itemCodes.size()) return;
        c.setItemCode(itemCodes.get(idx - 1));
        if (idx - 1 < descriptions.size()) c.setItemDescription(descriptions.get(idx - 1));
    }

    private static boolean near(Vec3 a, Vec3 b) {
        return a.distanceTo(b) <= TOLERANCE_MM;
    }

    private static Integer toInt(String s) {
        try {
            return Integer.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
