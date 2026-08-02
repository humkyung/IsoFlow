// PipelineLoader.java — 파싱 → 좌표 리베이스 → 위상 해석을 한 번에 수행하는 엔진 진입점
package co.atools.isoflow.engine;

import co.atools.isoflow.engine.diagnostic.Diagnostics;
import co.atools.isoflow.engine.geometry.Rebaser;
import co.atools.isoflow.engine.model.Pipeline;
import co.atools.isoflow.engine.parser.IdfParser;
import co.atools.isoflow.engine.parser.ParseResult;
import co.atools.isoflow.engine.parser.PcfParser;
import co.atools.isoflow.engine.topology.Topology;
import co.atools.isoflow.engine.topology.TopologyResolver;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * REST/DB 계층이 쓰는 엔진의 단일 진입점.
 * 예외를 던지지 않고(입출력 오류 제외) 읽을 수 있는 만큼 IR 을 만든 뒤 진단을 함께 돌려준다.
 */
public final class PipelineLoader {

    /**
     * @param pipeline    리베이스가 끝난 IR (좌표는 로컬 mm, {@code pipeline.origin()} 이 오프셋)
     * @param topology    접합점과 연결 그래프
     * @param diagnostics 파싱·위상 해석에서 나온 모든 진단
     */
    public record Loaded(Pipeline pipeline, Topology topology, Diagnostics diagnostics) {
    }

    private PipelineLoader() {
    }

    /** 지원 입력 포맷 */
    public enum Format {PCF, IDF}

    /** 파일명 확장자로 포맷을 정한다. 모르면 null */
    public static Format formatOf(String fileName) {
        if (fileName == null) return null;
        String lower = fileName.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".pcf")) return Format.PCF;
        if (lower.endsWith(".idf")) return Format.IDF;
        return null;
    }

    /** PCF 파일을 읽어 IR + 위상까지 만든다 */
    public static Loaded loadPcf(Path path) throws IOException {
        // PCF 는 ASCII 가 기본이지만 자재 설명에 비ASCII 가 섞이는 경우가 있어 관용적으로 읽는다
        String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        return loadPcf(new StringReader(text));
    }

    public static Loaded loadPcf(Reader reader) throws IOException {
        return finish(PcfParser.parse(reader));
    }

    /** IDF 파일을 읽어 PCF 와 동일한 IR 로 만든다 */
    public static Loaded loadIdf(Reader reader) throws IOException {
        return finish(IdfParser.parse(reader));
    }

    /** 포맷에 맞는 파서를 고른다 */
    public static Loaded load(Format format, Reader reader) throws IOException {
        return format == Format.IDF ? loadIdf(reader) : loadPcf(reader);
    }

    /** 파싱 이후 공통 단계 — 리베이스와 위상 해석은 포맷과 무관하다 */
    private static Loaded finish(ParseResult parsed) {
        Pipeline pipeline = parsed.pipeline();
        Diagnostics diag = parsed.diagnostics();

        // 위상 해석 전에 리베이스한다 — 허용오차 비교를 작은 수에서 하는 편이 안전하다
        Rebaser.rebase(pipeline);
        Topology topology = TopologyResolver.resolve(pipeline, diag);

        return new Loaded(pipeline, topology, diag);
    }
}
