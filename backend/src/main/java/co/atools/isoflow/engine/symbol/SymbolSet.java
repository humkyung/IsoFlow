// SymbolSet.java — symbols-2d.json / skey-table.json 을 읽어 담는다. 클래스패스 리소스에서 한 번만 로드
package co.atools.isoflow.engine.symbol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SymbolSet {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SYMBOLS = "engine/symbols-2d.json";
    private static final String SKEYS = "engine/skey-table.json";

    /** 기본 심볼 세트는 불변이므로 한 번만 읽는다 */
    private static volatile SymbolSet defaultInstance;

    private final Map<String, SymbolShape> shapes;
    private final Map<String, SymbolShape> endTreatments;
    private final Map<String, SkeyEntry> skeys;
    private final Map<String, String> fallbackByPcfType;
    private final Set<String> endTypes;
    private final Set<String> notRenderedTypes;

    private SymbolSet(Map<String, SymbolShape> shapes, Map<String, SymbolShape> endTreatments,
                      Map<String, SkeyEntry> skeys, Map<String, String> fallbackByPcfType,
                      Set<String> endTypes, Set<String> notRenderedTypes) {
        this.shapes = shapes;
        this.endTreatments = endTreatments;
        this.skeys = skeys;
        this.fallbackByPcfType = fallbackByPcfType;
        this.endTypes = endTypes;
        this.notRenderedTypes = notRenderedTypes;
    }

    public static SymbolSet standard() {
        SymbolSet local = defaultInstance;
        if (local == null) {
            synchronized (SymbolSet.class) {
                local = defaultInstance;
                if (local == null) {
                    defaultInstance = local = load();
                }
            }
        }
        return local;
    }

    private static SymbolSet load() {
        try {
            JsonNode sym = readResource(SYMBOLS);
            JsonNode skt = readResource(SKEYS);

            Map<String, SymbolShape> shapes = readShapes(sym.path("shapes"));
            Map<String, SymbolShape> ends = readShapes(sym.path("endTreatments"));

            Map<String, SkeyEntry> skeys = readSkeys(skt.path("skeys"));

            Map<String, String> fallback = new LinkedHashMap<>();
            skt.path("fallbackByPcfType").fields().forEachRemaining(e -> {
                if (e.getKey().startsWith("_") || e.getValue().isNull()) return;
                fallback.put(e.getKey(), e.getValue().asText());
            });

            Set<String> endTypes = new java.util.LinkedHashSet<>();
            skt.path("endTypes").fieldNames().forEachRemaining(k -> {
                if (!k.startsWith("_")) endTypes.add(k);
            });

            Set<String> notRendered = new java.util.LinkedHashSet<>();
            skt.path("notRendered").path("types").forEach(n -> notRendered.add(n.asText()));

            return new SymbolSet(shapes, ends, skeys, fallback, endTypes, notRendered);
        } catch (IOException e) {
            throw new UncheckedIOException("심볼 세트를 읽지 못했습니다", e);
        }
    }

    private static JsonNode readResource(String path) throws IOException {
        try (InputStream in = SymbolSet.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new IOException("클래스패스에 없음: " + path);
            return MAPPER.readTree(in);
        }
    }

    private static Map<String, SymbolShape> readShapes(JsonNode node) {
        Map<String, SymbolShape> out = new LinkedHashMap<>();
        node.fields().forEachRemaining(e -> {
            if (e.getKey().startsWith("_") || !e.getValue().isObject()) return;
            JsonNode v = e.getValue();
            List<SymbolElement> elements = new ArrayList<>();
            v.path("elements").forEach(el -> elements.add(readElement(el)));
            out.put(e.getKey(), new SymbolShape(
                    text(v, "name"), List.copyOf(elements), v.path("userDefinable").asBoolean(false)));
        });
        return Collections.unmodifiableMap(out);
    }

    private static SymbolElement readElement(JsonNode e) {
        List<double[]> points = null;
        if (e.has("points")) {
            points = new ArrayList<>();
            for (JsonNode p : e.path("points")) {
                points.add(new double[]{p.path("x").asDouble(), p.path("y").asDouble()});
            }
        }
        return new SymbolElement(
                text(e, "type"), text(e, "role"), text(e, "plane"),
                num(e, "x1"), num(e, "y1"), num(e, "x2"), num(e, "y2"),
                points,
                num(e, "x"), num(e, "y"), num(e, "w"), num(e, "h"),
                num(e, "cx"), num(e, "cy"), num(e, "r"),
                num(e, "startAngle"), num(e, "endAngle"),
                text(e, "content"), num(e, "height"), text(e, "anchor"));
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    private static Double num(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asDouble();
    }

    // ─────────────────────────── 사용자 심볼 오버레이 ───────────────────────────

    /** 오버레이가 다룰 수 있는 요소 타입 — Symbol2dLibrary.bake 의 switch 와 같아야 한다 */
    private static final Set<String> ELEMENT_TYPES =
            Set.of("line", "polyline", "polygon", "rect", "circle", "arc", "text");

    /**
     * 사용자 정의 심볼을 기본 세트 위에 덮은 새 세트를 만든다.
     * 기본 세트는 그대로 두므로 요청마다 다른 오버레이를 써도 서로 간섭하지 않는다.
     *
     * <p>병합 규칙: <b>같은 이름이면 교체, 새 이름이면 추가.</b> 부분 병합은 하지 않는다 —
     * 형상의 일부 요소만 덮으면 무엇이 그려질지 예측할 수 없다.
     *
     * @param json 기본 세트와 같은 구조. {@code shapes} / {@code endTreatments} /
     *             {@code skeys} / {@code fallbackByPcfType} 중 있는 것만 반영한다
     */
    public SymbolSet withOverlay(String json) {
        if (json == null || json.isBlank()) return this;
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (IOException e) {
            throw new IllegalArgumentException("심볼 JSON 을 읽지 못했습니다: " + e.getMessage(), e);
        }

        Map<String, SymbolShape> mergedShapes = new LinkedHashMap<>(shapes);
        mergedShapes.putAll(readShapes(root.path("shapes")));

        Map<String, SymbolShape> mergedEnds = new LinkedHashMap<>(endTreatments);
        mergedEnds.putAll(readShapes(root.path("endTreatments")));

        Map<String, SkeyEntry> mergedSkeys = new LinkedHashMap<>(skeys);
        mergedSkeys.putAll(readSkeys(root.path("skeys")));

        Map<String, String> mergedFallback = new LinkedHashMap<>(fallbackByPcfType);
        root.path("fallbackByPcfType").fields().forEachRemaining(e -> {
            if (!e.getKey().startsWith("_") && !e.getValue().isNull()) {
                mergedFallback.put(e.getKey(), e.getValue().asText());
            }
        });

        return new SymbolSet(Collections.unmodifiableMap(mergedShapes),
                Collections.unmodifiableMap(mergedEnds), mergedSkeys,
                mergedFallback, endTypes, notRenderedTypes);
    }

    /**
     * 오버레이 JSON 의 문제를 모두 모아 돌려준다. 비어 있으면 정상.
     *
     * <p>업로드 시점에 잡지 않으면 <b>도면에서 심볼이 조용히 빠진다</b> —
     * 그때는 원인을 찾기 어렵다.
     */
    public List<String> validateOverlay(String json) {
        List<String> problems = new ArrayList<>();
        JsonNode root;
        try {
            root = MAPPER.readTree(json == null ? "" : json);
        } catch (IOException e) {
            return List.of("JSON 을 읽지 못했습니다: " + e.getMessage());
        }
        if (root == null || !root.isObject()) {
            return List.of("최상위가 JSON 객체가 아닙니다");
        }
        if (root.path("shapes").isMissingNode() && root.path("endTreatments").isMissingNode()
                && root.path("skeys").isMissingNode()) {
            problems.add("shapes / endTreatments / skeys 중 하나는 있어야 합니다");
        }

        validateShapeBlock(root.path("shapes"), "shapes", problems);
        validateShapeBlock(root.path("endTreatments"), "endTreatments", problems);

        // 오버레이가 만드는 형상 이름까지 포함해 SKEY 참조를 확인한다
        Set<String> known = new java.util.LinkedHashSet<>(shapes.keySet());
        root.path("shapes").fieldNames().forEachRemaining(known::add);
        root.path("skeys").fields().forEachRemaining(e -> {
            if (e.getKey().startsWith("_")) return;
            String shape = e.getValue().path("shape").asText(null);
            if (shape == null || shape.isBlank()) {
                problems.add("skeys." + e.getKey() + ": shape 이 없습니다");
            } else if (!known.contains(shape)) {
                problems.add("skeys." + e.getKey() + ": 없는 형상 '" + shape + "' 을 가리킵니다");
            }
        });
        return problems;
    }

    private static void validateShapeBlock(JsonNode block, String where, List<String> problems) {
        if (block.isMissingNode() || block.isNull()) return;
        if (!block.isObject()) {
            problems.add(where + ": 객체여야 합니다");
            return;
        }
        block.fields().forEachRemaining(e -> {
            if (e.getKey().startsWith("_")) return;
            JsonNode v = e.getValue();
            String at = where + "." + e.getKey();
            if (!v.isObject()) {
                problems.add(at + ": 객체여야 합니다");
                return;
            }
            JsonNode els = v.path("elements");
            if (!els.isArray() || els.isEmpty()) {
                problems.add(at + ": elements 배열이 비어 있습니다");
                return;
            }
            for (int i = 0; i < els.size(); i++) {
                String type = els.get(i).path("type").asText(null);
                if (type == null || !ELEMENT_TYPES.contains(type)) {
                    problems.add(at + ".elements[" + i + "]: 지원하지 않는 type '" + type
                            + "' — " + ELEMENT_TYPES);
                }
            }
        });
    }

    /** skeys 블록을 읽는다 (기본 세트 로딩과 오버레이가 함께 쓴다) */
    private static Map<String, SkeyEntry> readSkeys(JsonNode node) {
        Map<String, SkeyEntry> out = new LinkedHashMap<>();
        node.fields().forEachRemaining(e -> {
            if (e.getKey().startsWith("_") || !e.getValue().isObject()) return;
            JsonNode v = e.getValue();
            out.put(e.getKey(), new SkeyEntry(
                    e.getKey(), text(v, "shape"), text(v, "overlay"), text(v, "pcfType"),
                    text(v, "category"), text(v, "desc"), v.path("flowArrow").asBoolean(false)));
        });
        return out;
    }

    public Map<String, SymbolShape> shapes() {
        return shapes;
    }

    public SymbolShape shape(String name) {
        return shapes.get(name);
    }

    public SymbolShape endTreatment(String code) {
        return endTreatments.get(code);
    }

    public Map<String, SkeyEntry> skeys() {
        return skeys;
    }

    public Map<String, String> fallbackByPcfType() {
        return fallbackByPcfType;
    }

    public Set<String> endTypes() {
        return endTypes;
    }

    public Set<String> notRenderedTypes() {
        return notRenderedTypes;
    }
}
