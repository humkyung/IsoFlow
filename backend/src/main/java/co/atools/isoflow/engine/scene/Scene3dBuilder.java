// Scene3dBuilder.java — IR 을 3D 뷰어용 Scene3D 로 직렬화한다. 형상 종류 결정이 이 클래스의 핵심 책임
package co.atools.isoflow.engine.scene;

import co.atools.isoflow.engine.diagnostic.DiagnosticCodes;
import co.atools.isoflow.engine.diagnostic.Diagnostics;
import co.atools.isoflow.engine.geometry.Axis6;
import co.atools.isoflow.engine.model.ComponentType;
import co.atools.isoflow.engine.model.FlatDirection;
import co.atools.isoflow.engine.model.MaterialItem;
import co.atools.isoflow.engine.model.Pipeline;
import co.atools.isoflow.engine.model.PipingComponent;
import co.atools.isoflow.engine.model.Port;
import co.atools.isoflow.engine.model.PortKind;
import co.atools.isoflow.engine.model.Vec3;
import co.atools.isoflow.engine.symbol.SkeyTable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Scene3dBuilder {

    /**
     * 앵글 밸브의 PCF 키워드. {@code ComponentType} 은 VALVE-ANGLE / VALVE-3WAY 를 모두 VALVE 로 묶으므로
     * 꺾인 형상 여부는 원문 키워드로만 구분할 수 있다.
     */
    private static final String RAW_VALVE_ANGLE = "VALVE-ANGLE";

    /** skey-table 이 붙이는 형상 이름 — 밸브 종류 판정의 단일 출처다 (버터플라이: VY/ZB 계열) */
    private static final String SKEY_SHAPE_BUTTERFLY = "VALVE_BUTTERFLY";
    /** 게이트 밸브 (VT/VV 계열 + 액추에이터가 붙는 CV/HV/MV/SV 계열) */
    private static final String SKEY_SHAPE_GATE = "VALVE_GATE";
    /** 글로브 밸브 (VG/VX 계열) — 게이트와 같은 보타이에 가운데 구가 하나 더 붙는다 */
    private static final String SKEY_SHAPE_GLOBE = "VALVE_GLOBE";
    /** 볼 밸브 (VB 계열) — 글로브와 같은 보타이+구에 조작부만 레버로 다르다 */
    private static final String SKEY_SHAPE_BALL = "VALVE_BALL";
    /** 체크 밸브 (VC/CK/NV 계열) — 배럴 + 볼트 캡 */
    private static final String SKEY_SHAPE_CHECK = "VALVE_CHECK";
    /** 플러그 밸브 (VP 계열) — 볼과 같은 90° 레버 조작에 가운데만 테이퍼 플러그다 */
    private static final String SKEY_SHAPE_PLUG = "VALVE_PLUG";

    private Scene3dBuilder() {
    }

    public static Scene3D build(String sceneId, Pipeline pipeline, Diagnostics diag) {
        List<Scene3D.Component3D> comps = new ArrayList<>();
        int i = 0;
        for (PipingComponent c : pipeline.components()) {
            Shape3D shape = decideShape(c);

            // 경로 컴포넌트인데 그려지지 않으면 도면에서 조용히 사라진다 — 반드시 알린다
            if (shape == Shape3D.NONE && c.type().isRoutingComponent()) {
                diag.warn(DiagnosticCodes.SHAPE_NOT_RENDERABLE, 0,
                        "component", c.label(), "reason", "insufficient-ports");
            }
            // 편심인데 방향을 모르면 동심으로 그리게 된다 — 조용히 넘기지 않는다
            if (c.type() == ComponentType.REDUCER_ECCENTRIC && c.flatDirection() == null) {
                diag.warn(DiagnosticCodes.ECCENTRIC_FLAT_UNKNOWN, 0, "component", c.label());
            }
            comps.add(toComponent("c" + (i++), c, shape));
        }

        Map<String, String> materials = new LinkedHashMap<>();
        for (Map.Entry<String, MaterialItem> e : pipeline.materials().entrySet()) {
            materials.put(e.getKey(), e.getValue().description());
        }

        return new Scene3D(
                Scene3D.SCHEMA_VERSION,
                sceneId,
                "mm",
                toArray(pipeline.origin()),
                bounds(pipeline),
                new Scene3D.PipelineInfo(
                        pipeline.lineNumber(), pipeline.pipingSpec(), pipeline.nominalClass(),
                        pipeline.area(), pipeline.revision(),
                        pipeline.attrs().isEmpty() ? null : pipeline.attrs()),
                comps,
                materials.isEmpty() ? null : materials);
    }

    /**
     * 컴포넌트가 어떤 지오메트리로 그려질지 정한다.
     * 포트가 모자라면 그릴 수 있는 형상으로 낮춘다 — 없는 좌표를 지어내지 않는다.
     */
    static Shape3D decideShape(PipingComponent c) {
        long ends = c.ports().stream().filter(p -> p.kind() == PortKind.END).count();
        boolean hasCentre = c.portOf(PortKind.CENTRE, 0).isPresent();
        boolean hasBranch1 = c.portOf(PortKind.BRANCH1, 0).isPresent();
        boolean hasBranch2 = c.portOf(PortKind.BRANCH2, 0).isPresent();

        return switch (c.type()) {
            case PIPE -> ends >= 2 ? Shape3D.PIPE : Shape3D.NONE;

            // 호를 그리려면 중심이 필요하다. 없으면 직선으로 낮춘다
            case ELBOW, BEND -> {
                if (ends < 2) yield Shape3D.NONE;
                yield hasCentre ? Shape3D.ELBOW : Shape3D.PIPE;
            }

            case TEE -> {
                if (ends < 2) yield Shape3D.NONE;
                yield hasBranch1 ? Shape3D.TEE : Shape3D.PIPE;
            }
            case CROSS -> {
                if (ends < 2) yield Shape3D.NONE;
                if (hasBranch1 && hasBranch2) yield Shape3D.CROSS;
                yield hasBranch1 ? Shape3D.TEE : Shape3D.PIPE;
            }

            // 올렛은 END 가 없다 — 모재 접속점(보어 있는 CENTRE)과 분기점만 있다
            case OLET -> (hasCentre && hasBranch1) ? Shape3D.OLET : Shape3D.NONE;

            case REDUCER_CONCENTRIC, REDUCER_ECCENTRIC -> ends >= 2 ? Shape3D.REDUCER : Shape3D.NONE;

            // 앵글 밸브는 두 END 가 90° 로 꺾여 있다 — 직선으로 이으면 모서리를 가로지른다.
            // 인라인 밸브도 CENTRE 를 갖지만(두 END 의 중점) 일직선이라 지금처럼 BODY 로 둔다
            case VALVE -> {
                if (ends < 2) yield Shape3D.NONE;
                String skeyShape = skeyShapeOf(c);
                // 버터플라이는 웨이퍼 몸통이 BODY 와 똑같아서 스템이 없으면 구분되지 않는다.
                // 게이트는 몸통 자체가 보타이라 스템 없이도 다르다 — 조작부만 빠진다
                if (SKEY_SHAPE_BUTTERFLY.equals(skeyShape) && spindleOf(c) != null) {
                    yield Shape3D.VALVE_BUTTERFLY;
                }
                if (SKEY_SHAPE_GATE.equals(skeyShape)) yield Shape3D.VALVE_GATE;
                if (SKEY_SHAPE_GLOBE.equals(skeyShape)) yield Shape3D.VALVE_GLOBE;
                if (SKEY_SHAPE_BALL.equals(skeyShape)) yield Shape3D.VALVE_BALL;
                // 스윙 체크는 배럴 + 캡이라 유동을 몰라도 형상이 성립한다.
                // 방향을 알면 캡이 상류로 밀릴 뿐이다
                if (SKEY_SHAPE_CHECK.equals(skeyShape)) yield Shape3D.VALVE_CHECK;
                if (SKEY_SHAPE_PLUG.equals(skeyShape)) yield Shape3D.VALVE_PLUG;
                boolean angle = RAW_VALVE_ANGLE.equalsIgnoreCase(c.rawKeyword());
                yield (angle && hasCentre) ? Shape3D.VALVE_ANGLE : Shape3D.BODY;
            }

            // 가스켓은 두께가 있어 3D 에서 빼면 플랜지 사이에 그만큼 빈 틈이 보인다.
            // (2D 도면은 다르다 — skey-table 의 notRendered 규칙대로 심볼을 그리지 않는다)
            case FLANGE, CAP, COUPLING, UNION, INSTRUMENT, FILTER, TRAP, SUPPORT, MISC_COMPONENT, GASKET ->
                    ends >= 2 ? Shape3D.BODY : Shape3D.NONE;

            // 용접·유동화살표·종단표시·볼트는 3D 에 그리지 않는다
            case WELD, FLOW_ARROW, END_CONNECTION_PIPELINE, END_POSITION_OPEN, BOLT, UNKNOWN ->
                    Shape3D.NONE;
        };
    }

    /**
     * SKEY 가 가리키는 형상 이름. <b>SKEY 로 직접 찾았을 때만</b> 돌려주고
     * PCF 타입 폴백은 null 로 본다 — 폴백은 {@code VALVE → VALVE_GATE} 처럼 근거 없는 기본값이라
     * 이것으로 밸브 종류를 단정하면 정보 없는 밸브에 핸드휠을 지어내게 된다.
     */
    static String skeyShapeOf(PipingComponent c) {
        SkeyTable.Resolution r = SkeyTable.standard().resolve(c.skey(), c.rawKeyword());
        if (!r.found() || r.resolvedBy() == SkeyTable.Source.PCF_TYPE) return null;
        return r.entry().shape();
    }

    /** SPINDLE-DIRECTION 원문을 축으로 해석한다. 없거나 모르는 값이면 null */
    static Axis6 spindleOf(PipingComponent c) {
        return Axis6.fromName(c.spindleDirection());
    }

    /**
     * PCF 의 {@code FLOW} 를 <b>유동이 향하는 END 포트의 ordinal</b> 로 바꾼다.
     *
     * <p><b>규약: {@code FLOW n} = n 번째 END-POINT 쪽으로 흐른다</b> (1-base).
     * END ordinal 은 0-base 라 {@code n - 1} 이다.
     * 코퍼스에는 체크 밸브가 하나도 없어 실 도면으로 교차검증하지 못했다 —
     * 뒤집어야 한다면 <b>이 메서드 한 곳만</b> 고치면 된다.
     *
     * <p>0(미지정)이나 실제 END 가 없는 번호(3-way 의 3 등)는 null 로 본다.
     */
    static Integer flowToEnd(PipingComponent c) {
        String raw = c.flow();
        if (raw == null || raw.isBlank()) return null;
        int n;
        try {
            n = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
        int ordinal = n - 1;
        boolean exists = c.portOf(PortKind.END, ordinal).isPresent();
        return exists ? ordinal : null;
    }

    private static Scene3D.Component3D toComponent(String id, PipingComponent c, Shape3D shape) {
        List<Scene3D.Port3D> ports = new ArrayList<>();
        for (Port p : c.ports()) {
            ports.add(new Scene3D.Port3D(
                    p.kind().name(), p.ordinal(), toArray(p.position()),
                    p.boreMm(), blankToNull(p.endType()), p.jointKey()));
        }
        // 편심 리듀서만 평평한 면 방향을 내려보낸다 — 다른 컴포넌트의 FLAT-DIRECTION 은 의미가 다를 수 있다
        FlatDirection flat = c.type() == ComponentType.REDUCER_ECCENTRIC ? c.flatDirection() : null;
        // 스핀들 방향과 유동 방향은 밸브에만 의미가 있다
        Axis6 spindle = c.type() == ComponentType.VALVE ? spindleOf(c) : null;
        Integer flowToEnd = c.type() == ComponentType.VALVE ? flowToEnd(c) : null;

        return new Scene3D.Component3D(
                id, c.type().name(), c.rawKeyword(), shape, ports,
                blankToNull(c.skey()), blankToNull(c.itemCode()), blankToNull(c.itemDescription()),
                c.weight(), c.angleDeg(), flat == null ? null : flat.name(),
                spindle == null ? null : spindle.name(), flowToEnd,
                c.attrs().isEmpty() ? null : c.attrs());
    }

    /** 모든 포트를 감싸는 경계 상자. 포트가 없으면 원점 크기 0 */
    private static double[] bounds(Pipeline pipeline) {
        double[] b = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE,
                -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        boolean any = false;
        for (PipingComponent c : pipeline.components()) {
            for (Port p : c.ports()) {
                Vec3 v = p.position();
                b[0] = Math.min(b[0], v.x());
                b[1] = Math.min(b[1], v.y());
                b[2] = Math.min(b[2], v.z());
                b[3] = Math.max(b[3], v.x());
                b[4] = Math.max(b[4], v.y());
                b[5] = Math.max(b[5], v.z());
                any = true;
            }
        }
        return any ? b : new double[]{0, 0, 0, 0, 0, 0};
    }

    private static double[] toArray(Vec3 v) {
        return new double[]{v.x(), v.y(), v.z()};
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
