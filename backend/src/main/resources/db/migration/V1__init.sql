-- V1__init.sql — IsoFlow 초기 스키마. 프로젝트/원본파일/파이프라인(IR)/등각도/자산/스타일
SET client_encoding TO 'UTF8';

CREATE EXTENSION IF NOT EXISTS pgcrypto;   -- gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS pg_trgm;    -- 태그·품목코드 부분 검색

-- ─────────────────────────── 프로젝트 ───────────────────────────
CREATE TABLE project (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(200) NOT NULL,
    code        VARCHAR(50),
    description TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_project_code ON project (code) WHERE code IS NOT NULL;

-- ─────────────────────── 업로드된 원본 PCF/IDF ───────────────────────
CREATE TABLE iso_file (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id    UUID NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    format        VARCHAR(10) NOT NULL CHECK (format IN ('PCF', 'IDF')),
    file_name     VARCHAR(400) NOT NULL,
    storage_path  TEXT NOT NULL,
    size_bytes    BIGINT NOT NULL,
    -- 동일 파일 재업로드 감지용 SHA-256
    content_hash  CHAR(64) NOT NULL,
    uploaded_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_iso_file_project ON iso_file (project_id);
CREATE INDEX ix_iso_file_hash ON iso_file (content_hash);

-- ───────────────────────── 파이프라인(라인) ─────────────────────────
CREATE TABLE pipeline (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id     UUID NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    iso_file_id    UUID REFERENCES iso_file (id) ON DELETE SET NULL,
    -- PCF PIPELINE-REFERENCE / IDF 도면명
    line_number    VARCHAR(200) NOT NULL,
    piping_spec    VARCHAR(50),
    nominal_class  VARCHAR(50),
    area           VARCHAR(100),
    revision       VARCHAR(20),
    -- 절대 플랜트 좌표를 로컬로 옮길 때 뺀 오프셋(mm). three.js float32 정밀도 대응
    origin_x       DOUBLE PRECISION NOT NULL DEFAULT 0,
    origin_y       DOUBLE PRECISION NOT NULL DEFAULT 0,
    origin_z       DOUBLE PRECISION NOT NULL DEFAULT 0,
    -- 헤더의 ATTRIBUTEnn 등 가변 속성 원문 보존
    attrs          JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_pipeline_project ON pipeline (project_id);
CREATE INDEX ix_pipeline_line_number_trgm ON pipeline USING gin (line_number gin_trgm_ops);

-- ─────────────────────────── 컴포넌트 ───────────────────────────
CREATE TABLE component (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_id       UUID NOT NULL REFERENCES pipeline (id) ON DELETE CASCADE,
    -- PCF COMPONENT-IDENTIFIER (파일 내 순번)
    source_index      INTEGER,
    -- PCF 컴포넌트 타입 (PIPE / ELBOW / TEE / FLANGE / VALVE / WELD …)
    component_type    VARCHAR(60) NOT NULL,
    skey              VARCHAR(10),
    item_code         VARCHAR(120),
    weight            DOUBLE PRECISION,
    -- 절단 길이(PCF CUT-PIECE-LENGTH). Cut Pipe List 원천
    cut_piece_length  DOUBLE PRECISION,
    uci               VARCHAR(80),
    -- 미지 키워드 passthrough 를 포함한 원문 속성
    attrs             JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT uq_component_source UNIQUE (pipeline_id, source_index)
);
CREATE INDEX ix_component_pipeline ON component (pipeline_id);
CREATE INDEX ix_component_type ON component (component_type);
CREATE INDEX ix_component_item_code ON component (item_code);

-- ───────────────────────── 컴포넌트 포트 ─────────────────────────
CREATE TABLE component_port (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    component_id  UUID NOT NULL REFERENCES component (id) ON DELETE CASCADE,
    -- END-POINT / CENTRE-POINT / BRANCH1-POINT / BRANCH2-POINT
    kind          VARCHAR(20) NOT NULL
                  CHECK (kind IN ('END', 'CENTRE', 'BRANCH1', 'BRANCH2')),
    -- 포트 순번 (END-POINT 가 2개 이상일 때 구분)
    ordinal       SMALLINT NOT NULL DEFAULT 0,
    -- 리베이스된 로컬 좌표(mm)
    x             DOUBLE PRECISION NOT NULL,
    y             DOUBLE PRECISION NOT NULL,
    z             DOUBLE PRECISION NOT NULL,
    -- 정규화된 보어(mm). 원본이 INCH 여도 여기서는 mm
    bore_mm       DOUBLE PRECISION,
    end_type      VARCHAR(4),
    -- 위상 해석으로 병합된 조인트 식별자 (같은 값 = 같은 접합점)
    joint_key     VARCHAR(64),
    CONSTRAINT uq_component_port UNIQUE (component_id, kind, ordinal)
);
CREATE INDEX ix_component_port_component ON component_port (component_id);
CREATE INDEX ix_component_port_joint ON component_port (joint_key);

-- ─────────────────────── 자재 항목 (BOM 원천) ───────────────────────
CREATE TABLE material_item (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_id  UUID NOT NULL REFERENCES pipeline (id) ON DELETE CASCADE,
    item_code    VARCHAR(120) NOT NULL,
    description  TEXT,
    attrs        JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT uq_material_item UNIQUE (pipeline_id, item_code)
);
CREATE INDEX ix_material_item_code_trgm ON material_item USING gin (item_code gin_trgm_ops);

-- ────────────────────── 등각도 스타일 설정 ──────────────────────
CREATE TABLE iso_style (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id  UUID REFERENCES project (id) ON DELETE CASCADE,
    name        VARCHAR(120) NOT NULL,
    is_default  BOOLEAN NOT NULL DEFAULT false,
    -- 단위·정밀도·심볼세트·표시항목·시트규격·BOM 컬럼 등
    settings    JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_iso_style_project ON iso_style (project_id);

-- ───────────────────────── 생성된 등각도 ─────────────────────────
CREATE TABLE isometric_drawing (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_id   UUID NOT NULL REFERENCES pipeline (id) ON DELETE CASCADE,
    iso_style_id  UUID REFERENCES iso_style (id) ON DELETE SET NULL,
    sheet_count   INTEGER NOT NULL DEFAULT 1,
    status        VARCHAR(20) NOT NULL DEFAULT 'GENERATED'
                  CHECK (status IN ('GENERATED', 'FAILED', 'STALE')),
    -- 생성 시 발생한 경고/진단
    diagnostics   JSONB NOT NULL DEFAULT '[]'::jsonb,
    generated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_isometric_pipeline ON isometric_drawing (pipeline_id);

-- ──────────────── 도면 자산 (Scene 캐시 / 출력 파일) ────────────────
CREATE TABLE drawing_asset (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_id   UUID REFERENCES pipeline (id) ON DELETE CASCADE,
    drawing_id    UUID REFERENCES isometric_drawing (id) ON DELETE CASCADE,
    kind          VARCHAR(20) NOT NULL
                  CHECK (kind IN ('SCENE3D', 'SCENE2D', 'DXF', 'PDF', 'BOM', 'CUTLIST', 'WELDLIST')),
    -- SCENE2D 는 시트별로 여러 개가 생긴다
    sheet_no      INTEGER,
    -- Scene JSON 은 여기에, 바이너리 산출물은 storage_path 에
    content       JSONB,
    storage_path  TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- 파이프라인 자산(SCENE3D)이거나 도면 자산이거나 — 둘 중 하나에는 반드시 속한다
    CONSTRAINT ck_drawing_asset_owner CHECK (pipeline_id IS NOT NULL OR drawing_id IS NOT NULL)
);
CREATE INDEX ix_drawing_asset_pipeline ON drawing_asset (pipeline_id, kind);
CREATE INDEX ix_drawing_asset_drawing ON drawing_asset (drawing_id, kind, sheet_no);
