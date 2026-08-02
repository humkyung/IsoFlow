<!-- IsoFlow 시스템 아키텍처 — 저장소 구조, 책임 분담, 데이터 흐름, API/DB 계약 정의 -->

# IsoFlow 아키텍처

> 도메인 요구사항은 [system-requirements.md](system-requirements.md) 참조.
> 이 문서는 **확정된 기술 결정**과 그에 따른 구조를 정의한다.

## 1. 확정 사항

| 항목 | 결정 |
|---|---|
| 기술 스택 | **Verso 준용** (Vite 6 + React 19 + TS + Tailwind v4 + Zustand + three.js 0.171 + react-i18next) |
| 저장소 | **독립 저장소** (`D:\Projects\IsoFlow`). Verso 코드는 참고·복사만, 런타임 의존 없음 |
| 3D 뷰어 | three.js — PCF/IDF 배관 모델 표시 |
| 2D 뷰어 | three.js (OrthographicCamera) — 생성된 등각도 표시 |
| 입력 | PCF, IDF |
| 출력 | **DXF, PDF — 백엔드에서 생성** |
| 백엔드 | Spring Boot (Java 21) + PostgreSQL + Flyway |
| **엔진 구현** | **Java 단일** — PCF/IDF 파서·위상·기하·등각도 생성·DXF/PDF 출력 모두 Spring Boot 안에. Python 사이드카 없음 |
| 등각도 생성 | **완전 자동 생성** (ISOGEN 대체 목표) |

### 1-1. Java 라이브러리 선정

| 용도 | 선택 | 사유 |
|---|---|---|
| 그래프(위상 해석) | **JGraphT** | networkx 대응. 성숙도 충분 |
| 기하 연산 | **JTS Topology Suite** | shapely 대응. 겹침 검출·버퍼·교차 판정에 사용 |
| PDF 출력 | **Apache PDFBox 3.x** | 벡터 드로잉 + 폰트 임베딩 자유도 높음 |
| DXF 출력 | **자체 Writer 모듈 (`export/dxf/`)** | 아래 사유 참조 |
| 테스트 | JUnit 5 + AssertJ | |

**DXF Writer 를 직접 만드는 이유** — Java DXF 라이브러리 생태계가 빈약하다.
- [Kabeja](https://kabeja.sourceforge.net/) — 유지보수 중단, 게다가 **읽기/변환 중심**이라 생성에 부적합
- [JDXF](https://jsevy.com/wordpress/index.php/java-and-android/jdxf-java-dxf-library/) — `Graphics2D` 서브클래스로 그리는 방식. 등각도에 필수인 **LAYER / BLOCK·INSERT / TEXT·MTEXT / DIMENSION 엔티티를 직접 제어할 수 없어** 부적합
- [DxfWriter](https://github.com/AlessioWang/DxfWriter) — Kabeja 기반, 소규모

DXF는 텍스트 포맷이므로 필요한 엔티티만 직접 쓰는 편이 오히려 단순하고 통제 가능하다.
- **1차 타깃: DXF R12 ASCII (AC1009)** — 섹션 구조가 가장 단순하고 호환성이 넓다. `LAYER` 테이블 + `LINE` / `ARC` / `CIRCLE` / `POLYLINE` / `TEXT` / `SOLID` / `BLOCK`·`INSERT` 만으로 등각도 표현이 가능하다.
- 치수는 DXF `DIMENSION` 엔티티 대신 **선·화살표·문자로 분해(explode)** 해서 출력한다. 등각도 치수는 어차피 자동 계산 결과이고, 뷰어별 치수 렌더링 편차를 피할 수 있다.
- 필요해지면 R2000(AC1015)로 확장해 `LWPOLYLINE` / `MTEXT` 를 쓴다.

> AViewer 의 Python 파서는 **이식 대상**이다(재사용 아님). 로직·레코드 규칙만 참고해 Java 로 새로 작성한다.

---

## 2. 저장소 구조

```
IsoFlow/
├─ index.html, package.json, vite.config.ts, tsconfig*.json
├─ schemas/                        프론트-백엔드 계약 (JSON Schema)
│    pipeline.schema.json            중립 도메인 모델(IR)
│    scene3d.schema.json             3D 배관 Scene
│    scene2d.schema.json             등각도 Scene
├─ src/
│    main.tsx  App.tsx  index.css
│    i18n/          index.ts + locales/{ko,en}.json
│    store/         useAppStore.ts (테마/뷰모드/선택/현재 파이프라인)
│    api/           http.ts(apiFetch), projects.ts, pipelines.ts, isometrics.ts
│    types/         pipeline.ts, scene3d.ts, scene2d.ts   ← schemas/ 와 동기화
│    components/
│      layout/      AppLayout, MenuBar, RibbonBar, LeftPanel(라인 트리),
│                   RightPanel(컴포넌트 속성), StatusBar
│      viewer/      Viewer3D, Viewer2D, FloatingToolbar
│    viewer/
│      Scene3DRenderer.ts   three.js PerspectiveCamera + OrbitControls
│      Scene2DRenderer.ts   three.js OrthographicCamera + troika + Line2 (Verso 이식)
│      primitives3d.ts      Cylinder/Torus/Flange/Valve 지오메트리
│      symbols2d.ts         SKEY → 2D 심볼 path
└─ backend/                       Spring Boot (Java 21) — 엔진 포함
     src/main/java/co/atools/isoflow/
       auth/ config/ web/ storage/
       project/            프로젝트 CRUD
       pipeline/           업로드·IR 저장·조회 (REST 계층)
       isometric/          등각도 생성 요청·Scene 캐시 (REST 계층)
       engine/                     ── 순수 도메인 엔진 (Spring 의존 없음) ──
         parser/
           PcfLexer.java           첫 글자 비공백 = 새 레코드
           PcfParser.java          헤더·컴포넌트·MATERIALS
           PcfKeywords.java
           IdfParser.java          PDS 고정 컬럼
           IdfRecordMap.java       레코드 코드 ↔ 컴포넌트 매핑
           UnitNormalizer.java     INCH/MM 혼재 흡수
         model/                    Pipeline, Component, Port, Joint, Weld, MaterialItem
         topology/                 JointResolver(tolerance 병합), PipeGraph(JGraphT), Diagnostics
         geometry/                 AxisClassifier, IsoProjection(30°/30°), SkewResolver, Rebaser
         layout/                   Router, DimensionEngine, Annotator, SheetSplitter,
                                   CollisionResolver(JTS)
         symbol/                   SkeyTable, Symbol2dLibrary (skey-table.json)
         scene/                    Scene3dBuilder, Scene2dBuilder
       export/
         dxf/                      DxfDocument, DxfWriter(R12 ASCII), DxfLayer, DxfEntity…
         pdf/                      PdfRenderer (PDFBox 3.x)
         table/                    BomBuilder, CutListBuilder, WeldListBuilder, CsvXlsxWriter
     src/main/resources/
       db/migration/V{n}__*.sql
       engine/skey-table.json      SKEY → 심볼 정의
     src/test/resources/corpus/    *.pcf, *.idf 회귀 코퍼스

docs/
```

---

## 3. 데이터 흐름

```
[사용자] PCF/IDF 업로드
   │  POST /api/pipelines/import (multipart)
   ▼
[pipeline]  원본 파일 저장(storage)
   ▼
[engine.parser]    PcfParser / IdfParser → UnitNormalizer
[engine.model]     IR 구성
[engine.topology]  JointResolver → PipeGraph → Diagnostics
[engine.geometry]  Rebaser (origin 오프셋 산출)
[engine.scene]     Scene3dBuilder
   ▼
[pipeline]  IR을 DB 저장 + scene3d 를 drawing_asset 캐시
   │  ← { scene3d, diagnostics }
   ▼
[프론트 3D 뷰어] three.js 렌더

────────────────────────────────────────

[사용자] "등각도 생성"
   │  POST /api/isometrics/{pipelineId}/generate  { styleId }
   ▼
[engine.geometry]  AxisClassifier → SkewResolver → IsoProjection
[engine.layout]    Router → DimensionEngine → Annotator
                   → CollisionResolver → SheetSplitter
[engine.symbol]    SkeyTable 조회
[engine.scene]     Scene2dBuilder (시트별)
[export.table]     BomBuilder / CutListBuilder / WeldListBuilder
   ▼
[isometric]  scene2d[] 를 drawing_asset 캐시
   │  ← { scene2d[], bom, cutList, weldList }
   ▼
[프론트 2D 뷰어] 렌더

────────────────────────────────────────

[사용자] "내보내기 (DXF / PDF)"
   │  POST /api/isometrics/{id}/export?format=dxf|pdf
   ▼
[export.dxf] DxfWriter (R12 ASCII)   또는
[export.pdf] PdfRenderer (PDFBox)
   ▼
바이너리 스트림 → 다운로드
```

### 계층 규칙

`engine/` 패키지는 **Spring 의존이 없는 순수 Java 도메인 코드**로 유지한다.
REST/DB/파일저장은 `pipeline/`·`isometric/`·`storage/` 가 담당하고, 엔진은 값 객체만 주고받는다.
이렇게 두면 엔진을 CLI·배치·단위테스트에서 그대로 돌릴 수 있다.

### 계약 원칙

- **Scene3D / Scene2D JSON 이 프론트-백엔드 계약**이다. `schemas/*.json`(권위) ↔ `src/types/*.ts` 동기화를 항상 유지한다.
- **좌표 리베이스는 엔진 책임**. 엔진이 파이프라인 bbox 중심을 `origin` 으로 계산해 Scene 헤더에 담고, 지오메트리는 로컬 좌표(mm)로 내려준다. 프론트는 절대좌표를 다루지 않는다. (three.js float32 정밀도 문제 회피)
- **단위 정규화도 엔진 책임**. 내부 표준은 좌표 mm / 보어 mm. `UNITS-BORE INCH` + `UNITS-CO-ORDS MM` 같은 혼재는 파서에서 흡수한다.
- **DXF/PDF 는 Scene2D 가 아니라 IR+레이아웃 결과에서 직접 생성**한다. 화면 렌더 캐시를 도면 출력의 원천으로 삼지 않는다.

---

## 4. API 초안 (Spring Boot)

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/projects` | 프로젝트 생성 |
| POST | `/api/projects/{id}/pipelines/import` | PCF/IDF 업로드(다중) → 파싱·저장 |
| GET | `/api/pipelines/{id}` | IR 조회 |
| GET | `/api/pipelines/{id}/scene3d` | 3D Scene 조회 |
| GET | `/api/pipelines/{id}/diagnostics` | 미연결/보어 불일치 등 검증 리포트 |
| POST | `/api/isometrics/{pipelineId}/generate` | 등각도 생성 |
| GET | `/api/isometrics/{id}/scene2d` | 등각도 Scene(시트별) |
| GET | `/api/isometrics/{id}/bom` | BOM / Cut list / Weld list |
| POST | `/api/isometrics/{id}/export` | `format=dxf\|pdf` → 파일 |
| GET/PUT | `/api/styles/{id}` | 등각도 스타일 설정 |

오류 응답은 Verso 계약을 준용한다: `{ code, <보간 파라미터…>, error }` — **번역은 프론트 책임**.

### 현재 구현 (DB 붙기 전, 무상태)

원본을 아직 저장하지 않으므로 파일을 매 요청에 다시 올린다. DB 가 붙으면 id 만 보내는 형태로 옮긴다.

| 메서드 | 경로 | 파라미터 |
|---|---|---|
| POST | `/api/pipelines/import` | `file` |
| POST | `/api/isometrics/generate` | `file`, `compress`(선택), `style`(선택, JSON 문자열), `symbolSetId`(선택) |
| POST | `/api/isometrics/export` | `file`, `format`, `style`(선택), `symbolSetId`(선택) |
| GET | `/api/styles/default` | — (`IsoStyle.defaults()`) |
| GET/POST | `/api/symbol-sets` | 사용자 심볼 세트 목록 / 업로드(`file`, `name`, `description`) |
| DELETE | `/api/symbol-sets/{id}` | 삭제 |

`generate` 응답은 `{ scenes: Scene2D[], diagnostics, fileName }` 이다 —
**시트가 여러 장일 수 있으므로 항상 배열**이다. 나누지 않으면 1개.

`style` 은 `engine/style/IsoStyle` 의 JSON 표현이다. 빠진 항목은 서버가 기본값으로 메우고,
읽지 못하면 `INVALID_STYLE` 로 거절한다 — 조용히 기본값으로 넘어가지 않는다.
`compress` 를 명시하면 `style.compression.enabled` 보다 우선한다.

---

## 5. DB 스키마 초안 (Flyway `V1__init.sql`)

| 테이블 | 용도 |
|---|---|
| `project` | 프로젝트 |
| `iso_file` | 업로드된 원본 PCF/IDF (해시, 저장 경로, 포맷) |
| `pipeline` | 라인 단위. line no, spec, class, area, revision, origin 오프셋 |
| `component` | type, skey, item_code, weight, attributes(JSONB) |
| `component_port` | position(x,y,z), bore, end_type, 연결된 joint |
| `material_item` | item_code ↔ description, qty (BOM 원천) |
| `isometric_drawing` | 생성된 등각도. style_id, sheet 수, 생성 시각 |
| `drawing_asset` | kind = `SCENE2D` / `SCENE3D` / `DXF` / `PDF` |
| `iso_style` | 등각도 스타일 설정(JSONB) |

규약(Verso 준용): PK=UUID(`gen_random_uuid`), enum류=VARCHAR+CHECK, 가변 데이터=JSONB, 스키마 변경은 **새 `V{n}__*.sql` 추가**(기존 파일 수정 금지).

---

## 6. 작업 관행 (Verso 준용)

- 주석·문서는 **한국어**. 새 파일 맨 위 / 새 함수에 한 줄 설명 주석.
- 사용자에게 보이는 **모든 문자열은 i18n 먼저** — `ko.json` → `en.json` → `t()` 순서. 하드코딩 금지.
- 아이콘은 `react-icons/md`.
- 작업 후 `npm run build` (타입체크+번들) 통과 확인. 백엔드는 `./gradlew build` (JUnit 5 포함) 통과 확인.
- 커밋/푸시는 사용자가 명시적으로 요청할 때만.
