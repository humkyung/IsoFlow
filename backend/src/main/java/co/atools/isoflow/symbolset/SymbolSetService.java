// SymbolSetService.java — 사용자 심볼 세트 업로드·조회. 엔진에 넘길 SymbolSet 을 만들어 준다
package co.atools.isoflow.symbolset;

import co.atools.isoflow.engine.symbol.SymbolSet;
import co.atools.isoflow.web.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class SymbolSetService {

    /**
     * @param shapes 오버레이가 정의한 형상 수
     * @param skeys  오버레이가 정의한 SKEY 수
     */
    public record Summary(UUID id, String name, String description,
                          int shapes, int skeys, OffsetDateTime updatedAt) {
    }

    private final Optional<UserSymbolSetRepository> repository;
    private final ObjectMapper mapper;

    /**
     * DB 없이 기동하는 프로파일(dev-nodb)에서는 저장소 빈이 없다.
     * 그때는 심볼 업로드만 못 쓰고 나머지 기능은 그대로 돌아가야 한다.
     */
    public SymbolSetService(Optional<UserSymbolSetRepository> repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    private UserSymbolSetRepository repo() {
        return repository.orElseThrow(() -> ApiException.unavailable("SYMBOL_STORE_UNAVAILABLE"));
    }

    // ─────────────────────────── 업로드 ───────────────────────────

    /**
     * 심볼 JSON 을 검증하고 저장한다. 같은 이름이면 내용을 갈아끼운다.
     *
     * <p><b>검증에 실패하면 저장하지 않는다.</b> 잘못된 세트를 받아 두면
     * 나중에 도면에서 심볼이 조용히 빠지고, 그때는 원인을 찾기 어렵다.
     */
    @Transactional
    public Summary upload(String name, String description, String json) {
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("SYMBOL_SET_NAME_REQUIRED");
        }
        List<String> problems = SymbolSet.standard().validateOverlay(json);
        if (!problems.isEmpty()) {
            throw ApiException.badRequest("INVALID_SYMBOL_SET",
                    "count", problems.size(), "problems", String.join(" / ", problems));
        }

        int shapes = countOf(json, "shapes");
        int skeys = countOf(json, "skeys");

        UserSymbolSet entity = repo().findByName(name).orElse(null);
        if (entity == null) {
            entity = new UserSymbolSet(null, name, description, json, shapes, skeys);
        } else {
            entity.replaceContent(json, description, shapes, skeys);
        }
        return summaryOf(repo().save(entity));
    }

    /** 오버레이가 정의한 항목 수 — 목록에서 내용을 열지 않고 보여주기 위한 것 */
    private int countOf(String json, String block) {
        try {
            JsonNode node = mapper.readTree(json).path(block);
            if (!node.isObject()) return 0;
            int n = 0;
            var it = node.fieldNames();
            while (it.hasNext()) {
                if (!it.next().startsWith("_")) n++;
            }
            return n;
        } catch (Exception e) {
            return 0;   // 검증을 통과한 뒤라 여기 오면 세는 것만 포기한다
        }
    }

    // ─────────────────────────── 조회 ───────────────────────────

    @Transactional(readOnly = true)
    public List<Summary> list() {
        return repo().findAllByOrderByNameAsc().stream().map(SymbolSetService::summaryOf).toList();
    }

    @Transactional
    public void delete(UUID id) {
        if (!repo().existsById(id)) {
            throw ApiException.notFound("SYMBOL_SET_NOT_FOUND", "id", id.toString());
        }
        repo().deleteById(id);
    }

    /**
     * 엔진에 넘길 심볼 세트를 만든다. id 가 null 이면 기본 세트.
     *
     * <p>저장소가 없으면(DB 미기동) 조용히 기본 세트로 넘어가지 않고 거절한다 —
     * 사용자가 고른 심볼이 무시된 도면을 받으면 안 된다.
     */
    @Transactional(readOnly = true)
    public SymbolSet resolve(UUID id) {
        if (id == null) return SymbolSet.standard();
        UserSymbolSet found = repo().findById(id)
                .orElseThrow(() -> ApiException.notFound("SYMBOL_SET_NOT_FOUND", "id", id.toString()));
        return SymbolSet.standard().withOverlay(found.content());
    }

    /** 문자열 id 를 UUID 로 바꾼다. 빈 값이면 null(기본 세트) */
    public static UUID parseId(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_SYMBOL_SET_ID", "id", raw);
        }
    }

    private static Summary summaryOf(UserSymbolSet e) {
        return new Summary(e.id(), e.name(), e.description(),
                e.shapeCount(), e.skeyCount(), e.updatedAt());
    }
}
