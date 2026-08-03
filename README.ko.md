<!-- README.ko.md — IsoFlow 저장소의 GitHub 소개 문서 (국문). README.md 의 한국어판 -->

# IsoFlow

**PCF / IDF 를 읽어 배관 등각도(Piping Isometric Drawing)를 자동 생성하는 웹 애플리케이션.**

IsoFlow 는 플랜트 배관 내보내기 파일 — **PCF**(Piping Component File) 과
**IDF**(Intergraph PDS Isometric Data File) — 를 읽어 배관 위상을 해석하고 등각 평면에 투영해,
치수와 주석이 붙은 등각도를 만들어 냅니다. 결과물은 **DXF**·**PDF** 와 함께
**BOM**·**Cut list**·**Weld list** 로 내보낼 수 있습니다.

즉, ISOGEN 계열 엔진이 하는 일을 자체 구현하는 프로젝트입니다.

[English README](README.md) ·
[요구사항](docs/system-requirements.md) ·
[아키텍처](docs/architecture.md) ·
[심볼 세트](docs/symbol-set.md) ·
[로드맵](docs/plans/로드맵_수행목록.md)

![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=white)
![TypeScript](https://img.shields.io/badge/TypeScript-5.7-3178C6?logo=typescript&logoColor=white)
![Vite](https://img.shields.io/badge/Vite-6-646CFF?logo=vite&logoColor=white)
![three.js](https://img.shields.io/badge/three.js-0.171-000000?logo=threedotjs&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-6DB33F?logo=springboot&logoColor=white)
![Java](https://img.shields.io/badge/Java-21-437291?logo=openjdk&logoColor=white)
![License](https://img.shields.io/badge/license-Apache--2.0-blue)

---

## 기능

| | |
|---|---|
| **입력** | PCF(ASCII 키워드 블록) 와 IDF(PDS 고정 컬럼 레코드) 를 하나의 중립 IR 로 흡수 |
| **3D 뷰어** | three.js 로 가져온 배관 모델 표시 — **Z-up**, 궤도 조작, 축 기즈모 |
| **2D 뷰어** | 생성된 등각도 표시 (직교 카메라, 팬·줌, 다크/라이트 테마) |
| **위상 해석** | 조인트 병합, 연결 그래프(JGraphT), 미연결·보어 불일치 진단 |
| **기하** | 축 분류, 사선 처리, 등각 투영, 선택적 길이 압축(비축척) |
| **심볼** | SKEY 기반 2D 심볼 라이브러리, **shear** 로 등각 배치(회전이 아님) |
| **주석** | 치수 계획, 라벨 겹침 회피, 용접 번호, 라인 정보 |
| **시트** | 자동 시트 분할 + 시트 간 연속 표기, 균등 배분 |
| **겹침 처리** | 화면 교차부에서 뒤쪽 선 끊기, 밀집 심볼은 상세도(detail bubble) 로 분리 |
| **집계표** | BOM·Cut list·Weld list — **길이 압축 이전에** 집계해 수량이 줄지 않게 함 |
| **출력** | DXF(R12 ASCII, 자체 Writer), PDF(PDFBox), 집계표 파일 |
| **스타일** | 용지·심볼 크기·치수 규칙·압축·표시 항목을 요청마다 덮어쓰기 가능 |
| **확장** | 사용자 정의 심볼 세트(JSON) 업로드 후 그 세트로 생성 |
| **다국어** | 한국어(기본) / 영어, react-i18next |

## 처리 흐름

```
PCF / IDF 업로드
      │  POST /api/pipelines/import
      ▼
 parser      PcfParser / IdfParser → UnitNormalizer     (좌표 mm, 보어 mm)
 model       중립 IR 구성
 topology    JointResolver → PipeGraph → Diagnostics
 geometry    Rebaser (bbox 중심 → origin)
 scene       Scene3D  ──────────────────────────────►  3D 뷰어 (three.js)

"등각도 생성"
      │  POST /api/isometrics/generate
      ▼
 geometry    AxisClassifier → SkewResolver → IsoProjection
 layout      Router → DimensionPlanner → Annotator
             → CollisionResolver → CrossingBreaker → DetailPlanner → SheetSplitter
 symbol      SkeyTable 조회
 scene       시트별 Scene2D  ────────────────────────►  2D 뷰어
 table       BOM / Cut list / Weld list

"내보내기"
      │  POST /api/isometrics/export?format=dxf|pdf|bom|cutlist|weldlist
      ▼
 export      DxfWriter(R12 ASCII) · PdfRenderer(PDFBox) · 표 Writer
      ▼
 파일 다운로드
```

시스템을 지탱하는 두 가지 계약:

- **Scene JSON 이 프론트-백엔드 계약**입니다. `schemas/*.json` 이 권위이고 `src/types/*.ts` 가 이를 미러링합니다.
- **`engine/` 은 순수 도메인 코드**입니다. Spring·JPA·서블릿·export 를 참조하지 않으며
  ArchUnit 테스트(`EngineArchitectureTest`)가 이를 강제합니다. 덕분에 엔진을 CLI·배치·단위테스트에서
  그대로 돌릴 수 있습니다.

## 기술 스택

| 영역 | 기술 |
|---|---|
| 빌드/프레임워크 | Vite 6 + React 19 + TypeScript |
| 스타일 | Tailwind CSS v4 (`@tailwindcss/vite`), Dark/Light 테마 |
| 상태 | Zustand |
| 3D 렌더 | three.js 0.171 — `PerspectiveCamera` + `OrbitControls`, **Z-up** |
| 2D 렌더 | three.js 0.171 — `OrthographicCamera` + troika-three-text |
| 아이콘/다국어 | react-icons(Material) · react-i18next |
| 백엔드 | Spring Boot 3.3 (Java 21) + PostgreSQL + Flyway |
| 엔진 | **Java 단일** — JGraphT(그래프) + JTS(기하) + PDFBox(PDF) + 자체 DXF Writer |
| 테스트 | Vitest(프론트) · JUnit 5 + AssertJ + ArchUnit + 골든 스냅샷(백엔드) |

## 시작하기

### 필요 환경

- Node.js 20+
- JDK 21
- PostgreSQL 14+ *(선택 — 아래 `dev-nodb` 참고)*
- Python 3 *(선택 — 심볼 세트 도구용)*

### 프론트엔드

```bash
npm install
npm run dev
```

**http://localhost:9100** 에서 열리고 `/api` 는 백엔드(**8290**)로 프록시됩니다.
(포트는 Verso 9000/8190 과 동시 구동할 수 있도록 9100/8290 을 씁니다.)

### 백엔드

```bash
cd backend && ./gradlew bootRun
```

**http://localhost:8290** 에서 동작합니다. 기동 시 Flyway 가 `db/migration/V*.sql` 을 적용하며
JPA 는 스키마에 관여하지 않습니다(`ddl-auto: none`). DB 는 한 번만 만들어 두면 됩니다.

```bash
createdb -U postgres isoflow
```

PostgreSQL 없이도 사용자 심볼 세트를 제외한 전 경로가 동작합니다.

```bash
cd backend && ./gradlew bootRun --args='--spring.profiles.active=dev-nodb'
```

### 명령어

```bash
npm run build              # tsc -b && vite build
npm test                   # vitest — 기하 로직 단위 테스트
npm run symbols:validate   # 심볼 세트 무결성 검사
npm run symbols:sheet      # docs/symbol-sheet.svg 재생성
cd backend && ./gradlew build   # 컴파일 + 테스트(골든 회귀 포함)
```

작업 후 `npm run build` 와 `./gradlew build` 가 모두 통과해야 합니다.

## REST API

현재 구현은 무상태입니다 — 요청마다 파일을 다시 올립니다. 영속 계층이 붙으면 id 기반 호출로 옮깁니다.

| 메서드 | 경로 | 파라미터 |
|---|---|---|
| `POST` | `/api/pipelines/import` | `file` → `{ scene3d, diagnostics }` |
| `POST` | `/api/isometrics/generate` | `file`, `compress?`, `style?`(JSON), `symbolSetId?` → `{ scenes[], diagnostics, fileName }` |
| `POST` | `/api/isometrics/export` | `file`, `format`(`dxf`\|`pdf`\|`bom`\|`cutlist`\|`weldlist`), `style?`, `symbolSetId?` → 파일 |
| `GET` | `/api/styles/default` | → `IsoStyle` 기본값 |
| `GET` `POST` | `/api/symbol-sets` | 목록 · 업로드(`file`, `name`, `description?`) |
| `DELETE` | `/api/symbol-sets/{id}` | 삭제 |

도면이 여러 장일 수 있으므로 `scenes` 는 **항상 배열**입니다.
오류는 언어중립 형식 `{ code, <보간 파라미터…>, error }` 이고 번역은 프론트 책임입니다.
잘못된 `style` 은 조용히 기본값으로 넘어가지 않고 `INVALID_STYLE` 로 거절합니다.

## 디렉터리 구조

```
src/
  components/{layout,viewer,dialogs}/   AppLayout, Viewer3D, Viewer2D, StyleDialog, …
  viewer/                               Scene3DRenderer, Scene2DRenderer, AxisGizmo
  types/                                scene2d, scene3d, isoStyle  ← schemas/ · IsoStyle.java 미러
  store/  api/  hooks/  i18n/
schemas/                                Scene JSON 계약 (권위)
backend/src/main/java/co/atools/isoflow/
  engine/                               순수 도메인 — parser, model, topology, geometry,
                                        layout, symbol, scene, table, style, diagnostic
  export/                               dxf, pdf, sheet, table
  pipeline/ isometric/ symbolset/ web/  REST 계층 (Spring)
backend/src/main/resources/
  db/migration/V{n}__*.sql              스키마 권위 (Flyway)
  engine/symbols-2d.json                2D 심볼 형상 라이브러리
  engine/skey-table.json                SKEY → 심볼 매핑
backend/src/test/resources/
  golden/                               Scene2D 스냅샷 (+ 검토용 SVG)
  tools/                                파이썬 검증·미리보기 도구
docs/
```

## 만들면서 알게 된 것

등각도 생성에서 틀리기 쉬운 지점과 IsoFlow 의 처리 방식:

- **좌표는 리베이스해야 합니다.** PCF 좌표는 `5650130.600` 같은 절대 플랜트 좌표입니다.
  float32 인 three.js 에 그대로 넣으면 떨림과 z-fighting 이 납니다. 엔진이 bbox 중심을 `origin`
  으로 빼고 **로컬 좌표(mm)만** 내려보냅니다.
- **한 파일 안에 단위가 섞여 있습니다.** `UNITS-BORE INCH` 와 `UNITS-CO-ORDS MM` 이 함께 나옵니다.
  파서에서 흡수하고 내부 표준은 좌표 mm / 보어 mm 입니다.
- **`CENTRE-POINT` 의 뜻은 컴포넌트마다 다릅니다.** ELBOW/BEND 에서는 두 배관 축이 만나는
  **모서리점**이며 호의 중심이 아닙니다. 모서리를 호의 중심으로 쓰면 원호가 바깥으로 부풀어
  방향이 반대로 보입니다. 호의 중심은 `O = C + normalize(t1+t2) · (L / cos(half))` 로 파생합니다.
- **등각 배치는 회전이 아니라 shear** 입니다. 심볼을 배관 축을 포함하는 평면에 놓고 투영하면
  로컬→도면이 단일 affine 으로 떨어집니다. 평면 안에서 회전만 시키면 "평면도를 붙여놓은 것"처럼
  보입니다.
- **치수는 중심선 교점 사이를 잽니다.** 엘보 접점은 대상이 아닙니다. 이 필터가 없으면 PCF 는
  엘보 다리 길이가, IDF 는 17mm 가 도면을 뒤덮습니다. 버린 점의 길이는 이웃 칸에 더해 구간 합계를
  보존합니다.
- **수량은 압축 전에 셉니다.** 치수 계획과 BOM 집계를 길이 압축보다 먼저 끝냅니다 — 압축 후에
  재면 발주 수량이 조용히 줄어듭니다.
- **교차가 항상 겹침은 아닙니다.** 3D 로도 만나는 점은 실제 분기점이라 끊으면 안 됩니다.
  화면에서만 겹치는 경우에만 뒤쪽을 끊고, 앞뒤는 투영의 영공간으로 판정합니다.
- **상세도는 간격을 벌리고 심볼은 키우지 않습니다.** 구간을 통째로 확대하면 심볼도 같이 커져
  겹침 비율이 그대로입니다 — 위치만 퍼뜨립니다.
- **모르는 입력도 버리지 않습니다.** 미지 PCF 키워드는 `attrs` 에 원문 보존하고, 확정하지 못한 값
  (예: 아직 해독하지 못한 IDF 보어 필드)은 추측하지 않고 진단으로 남깁니다.

IDF 지원은 정식 스펙 없이 실샘플에서 역공학한 결과입니다 — 좌표 스케일 0.01mm, 다리별로 쪼개진
엘보/티, 런·분기 판별 등의 근거는 [로드맵](docs/plans/로드맵_수행목록.md) 에 정리해 두었습니다.

## 진행 상황

| 마일스톤 | |
|---|---|
| M0 스캐폴딩 | ✅ |
| M1 PCF 파서 + IR + 위상 해석 | ✅ |
| M2 3D 뷰어 | ✅ |
| M3 등각 투영 + 2D 심볼 + 2D 뷰어 | ✅ |
| M4 치수 · 주석 | ✅ |
| M5 BOM · 시트 레이아웃 · DXF/PDF 출력 | ✅ |
| M6 시트 분할 · 겹침 회피 · IDF · 스타일 시스템 | ✅ |

미해결 항목은 [로드맵](docs/plans/로드맵_수행목록.md) 끝에 정리되어 있습니다 — IDF 정식 스펙 확보,
사내 표준 도면 템플릿·심볼 세트, 품질 기준선을 위한 상용 ISOGEN 출력 대조가 남았습니다.

## 기여

- 주석과 문서는 **한국어**로 작성하고, 새 파일 맨 위에 한 줄 설명 주석을 답니다.
- 모든 사용자 표시 문자열은 i18n 을 거칩니다 — `ko.json` → `en.json` → `t()`. 두 파일의 키 집합은
  항상 일치해야 합니다.
- 도면 상수는 `engine/style/IsoStyle` 에만 둡니다(프론트 복제본 `src/types/isoStyle.ts` 도 함께).
  `IsoStyleContractTest` 가 어긋남을 잡습니다.
- 적용된 Flyway 마이그레이션은 고치지 않고 새 `V{n}__*.sql` 을 추가합니다.
- 작도를 고치면 골든 스냅샷이 깨집니다. `build/golden-actual/*.svg` 를 눈으로 확인하고 의도한
  변화면 `./gradlew test -Dgolden.update=true` 로 갱신합니다.

## 라이선스

Apache License 2.0 을 따릅니다. [LICENSE](LICENSE) 를 참고하세요.
