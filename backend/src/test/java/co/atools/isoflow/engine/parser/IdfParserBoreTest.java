// IdfParserBoreTest.java — IDF 보어 컬럼(head[6]) 배치와 다리별 보어 분배를 고정하는 회귀 테스트
package co.atools.isoflow.engine.parser;

import co.atools.isoflow.engine.diagnostic.DiagnosticCodes;
import co.atools.isoflow.engine.model.ComponentType;
import co.atools.isoflow.engine.model.PipingComponent;
import co.atools.isoflow.engine.model.Port;
import co.atools.isoflow.engine.model.PortKind;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 보어는 <b>좌표 6개 바로 다음 컬럼</b>이다. 콤마 뒤 필드를 보어로 읽던 예전 해석은 틀렸고,
 * 그 자리는 실 코퍼스에서 {@code 0/10000/1110000/1010000} 네 값뿐이라 보어일 수 없다.
 *
 * <p>실 코퍼스(발주처 자료)는 저장소에 넣지 않는다 — 레코드 배치만 본뜬 익명 픽스처로 고정한다.
 */
class IdfParserBoreTest {

    private static ParseResult result;

    @BeforeAll
    static void parse() throws IOException {
        try (InputStream in = IdfParserBoreTest.class.getResourceAsStream("/idf/anon-bore.idf")) {
            assertThat(in).as("픽스처를 찾지 못했다").isNotNull();
            result = IdfParser.parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    /** 컴포넌트 하나를 타입으로 집어 온다 (픽스처에는 타입마다 필요한 개수만 들어 있다) */
    private static List<PipingComponent> of(ComponentType type) {
        return result.pipeline().components().stream().filter(c -> c.type() == type).toList();
    }

    private static Double boreOf(PipingComponent c, PortKind kind, int ordinal) {
        return c.portOf(kind, ordinal).map(Port::boreMm).orElse(null);
    }

    @Test
    @DisplayName("PIPE 는 좌표 다음 컬럼을 그대로 보어(mm)로 쓴다 — 좌표 스케일 0.01 을 곱하지 않는다")
    void pipeBore() {
        List<PipingComponent> pipes = of(ComponentType.PIPE);
        assertThat(pipes).hasSize(3);
        assertThat(pipes.get(0).ports()).extracting(Port::boreMm).containsExactly(500.0, 500.0);
        // 분기 쪽 배관은 300 이다 — 라인 하나 안에서 보어가 섞인다
        assertThat(pipes.get(2).ports()).extracting(Port::boreMm).containsExactly(300.0, 300.0);
    }

    @Test
    @DisplayName("엘보의 CENTRE 는 모서리점일 뿐이라 보어가 없고, 양 END 는 자기 다리의 보어를 갖는다")
    void elbowBore() {
        PipingComponent elbow = of(ComponentType.ELBOW).get(0);
        assertThat(boreOf(elbow, PortKind.CENTRE, 0)).isNull();
        assertThat(boreOf(elbow, PortKind.END, 0)).isEqualTo(500.0);
        assertThat(boreOf(elbow, PortKind.END, 1)).isEqualTo(500.0);
    }

    @Test
    @DisplayName("리듀싱 티는 다리마다 보어가 다르다 — 런 500 / 분기 300 (BOM 의 500X300)")
    void reducingTeeBore() {
        PipingComponent tee = of(ComponentType.TEE).get(0);
        // 세 번째 조각(47)이 파일 끝에 떨어져 있어도 좌표로 묶여야 런이 둘 다 잡힌다
        assertThat(tee.ports()).hasSize(4);
        assertThat(boreOf(tee, PortKind.CENTRE, 0)).isNull();
        assertThat(boreOf(tee, PortKind.END, 0)).isEqualTo(500.0);
        assertThat(boreOf(tee, PortKind.END, 1)).isEqualTo(500.0);
        assertThat(boreOf(tee, PortKind.BRANCH1, 0)).isEqualTo(300.0);
    }

    @Test
    @DisplayName("올렛의 CENTRE 는 모재 보어(300), BRANCH1 은 분기 보어(50) — BOM 의 300X50")
    void oletBore() {
        PipingComponent olet = of(ComponentType.OLET).get(0);
        assertThat(boreOf(olet, PortKind.CENTRE, 0)).isEqualTo(300.0);
        assertThat(boreOf(olet, PortKind.BRANCH1, 0)).isEqualTo(50.0);
        // 보어가 붙은 CENTRE 는 위상 해석이 모재 접속점으로 쓰는 판별자다 — 올렛만 그래야 한다
        assertThat(olet.portOf(PortKind.CENTRE, 0).orElseThrow().isConnectable()).isTrue();
    }

    @Test
    @DisplayName("보어를 가진 CENTRE 는 올렛뿐 — 엘보/티 CENTRE 에 보어가 새면 위상이 깨진다")
    void onlyOletHasBoredCentre() {
        assertThat(result.pipeline().components().stream()
                .filter(c -> c.ports().stream()
                        .anyMatch(p -> p.kind() == PortKind.CENTRE && p.boreMm() != null))
                .map(PipingComponent::type))
                .containsOnly(ComponentType.OLET);
    }

    @Test
    @DisplayName("배관 컴포넌트인데 보어가 0 이면 경고한다 — 조용히 비우면 반지름·BOM 이 틀어진다")
    void zeroBoreOnComponentWarns() {
        assertThat(result.diagnostics().count(DiagnosticCodes.IDF_BORE_MISSING)).isEqualTo(1);
        PipingComponent valve = of(ComponentType.VALVE).get(0);
        assertThat(valve.ports()).extracting(Port::boreMm).containsOnlyNulls();
    }

    @Test
    @DisplayName("보어를 갖지 않는 참조 레코드(151)의 0 은 정상이라 경고 대상이 아니다")
    void zeroBoreOnNonComponentIsSilent() {
        // 위 테스트의 경고 1건은 밸브 몫이다. 151 이 함께 셌다면 2건이 됐을 것이다
        assertThat(result.diagnostics().count(DiagnosticCodes.IDF_BORE_MISSING)).isEqualTo(1);
        assertThat(of(ComponentType.UNKNOWN)).isNotEmpty();
    }

    @Test
    @DisplayName("정체 미확정 필드는 위치 이름으로 원문 보존한다 — 보어라고 부르지 않는다")
    void unknownFieldsArePreservedPositionally() {
        PipingComponent pipe = of(ComponentType.PIPE).get(0);
        assertThat(pipe.attrs()).containsEntry("IDF-FIELD-10", "10000");
        assertThat(pipe.attrs()).doesNotContainKey("IDF-BORE-FIELD");
    }
}
