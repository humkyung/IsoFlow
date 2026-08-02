-- V2__add_user_symbol_set.sql — 사용자 정의 심볼 세트. 기본 세트 위에 덮어 쓰는 오버레이를 보관한다
SET client_encoding TO 'UTF8';

CREATE TABLE user_symbol_set (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id  UUID REFERENCES project (id) ON DELETE CASCADE,
    name        VARCHAR(120) NOT NULL,
    description TEXT,
    -- symbols-2d.json 과 같은 구조. shapes / endTreatments / skeys / fallbackByPcfType
    content     JSONB NOT NULL,
    -- 업로드 시 계산해 둔 형상·SKEY 개수 (목록 화면에서 내용을 열지 않고 보여주기 위함)
    shape_count INTEGER NOT NULL DEFAULT 0,
    skey_count  INTEGER NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_user_symbol_set_project ON user_symbol_set (project_id);
-- 프로젝트 안에서 이름은 유일하다. 프로젝트가 없는(전역) 세트끼리도 이름이 겹치면 고르기 어렵다
CREATE UNIQUE INDEX ux_user_symbol_set_name
    ON user_symbol_set (COALESCE(project_id, '00000000-0000-0000-0000-000000000000'::uuid), name);
