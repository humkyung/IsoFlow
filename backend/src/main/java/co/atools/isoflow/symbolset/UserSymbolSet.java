// UserSymbolSet.java — 업로드된 사용자 정의 심볼 세트 (기본 세트 위에 덮는 오버레이)
package co.atools.isoflow.symbolset;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_symbol_set")
public class UserSymbolSet {

    @Id
    @GeneratedValue
    private UUID id;

    /** 프로젝트별 세트. null 이면 전역 */
    @Column(name = "project_id")
    private UUID projectId;

    @Column(nullable = false, length = 120)
    private String name;

    private String description;

    /** symbols-2d.json 과 같은 구조의 JSON 원문 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String content;

    @Column(name = "shape_count", nullable = false)
    private int shapeCount;

    @Column(name = "skey_count", nullable = false)
    private int skeyCount;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    protected UserSymbolSet() {
    }

    public UserSymbolSet(UUID projectId, String name, String description, String content,
                         int shapeCount, int skeyCount) {
        this.projectId = projectId;
        this.name = name;
        this.description = description;
        this.content = content;
        this.shapeCount = shapeCount;
        this.skeyCount = skeyCount;
        this.updatedAt = OffsetDateTime.now();
    }

    /** 같은 이름으로 다시 올렸을 때 내용을 바꾼다 */
    public void replaceContent(String content, String description, int shapeCount, int skeyCount) {
        this.content = content;
        this.description = description;
        this.shapeCount = shapeCount;
        this.skeyCount = skeyCount;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID id() {
        return id;
    }

    public UUID projectId() {
        return projectId;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public String content() {
        return content;
    }

    public int shapeCount() {
        return shapeCount;
    }

    public int skeyCount() {
        return skeyCount;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    public OffsetDateTime updatedAt() {
        return updatedAt;
    }
}
