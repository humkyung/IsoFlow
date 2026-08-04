# CLAUDE.md

이 파일은 Claude Code(claude.ai/code)가 **IsoFlow** 저장소에서 작업할 때 참고하는 지침입니다.

## 프로젝트 개요

IsoFlow 는 **PCF / IDF 를 읽어 배관 등각도(Piping Isometric Drawing)를 자동 생성**하는 웹 애플리케이션입니다.
ISOGEN 계열 엔진이 하는 일을 자체 구현하는 것이 범위입니다.

- **3D 뷰어** — 가져온 배관 모델 표시 (three.js)
- **2D 뷰어** — 생성된 등각도 표시 (three.js OrthographicCamera)
- **출력** — DXF, PDF (+ BOM / Cut list / Weld list)

기술 스택은 **Verso**(`D:\Projects\00 Verso\Verso`)를 준용하되 **독립 저장소**입니다. 런타임 의존은 없습니다.

문서: [요구사항](docs/system-requirements.md) · [아키텍처](docs/architecture.md) ·
[심볼 세트](docs/symbol-set.md) · [로드맵](docs/plans/로드맵_수행목록.md)

## 기술 스택

| 영역 | 기술 |
|------|------|
| 빌드/프레임워크 | Vite 6 + React 19 + TypeScript |
| 스타일 | Tailwind CSS v4 (`@tailwindcss/vite`), Dark/Light 테마 |
| 상태 | Zustand (`src/store/useAppStore.ts`) |
| 3D 렌더 | three.js 0.171 (PerspectiveCamera + OrbitControls, **Z-up**) |
| 2D 렌더 | three.js 0.171 (OrthographicCamera) + troika-three-text |
| 아이콘 | react-icons (`react-icons/md`) |
| 다국어 | react-i18next (ko 기본 / en) |
| 백엔드 | Spring Boot 3.3 (Java 21) + PostgreSQL + Flyway |
| 엔진 | **Java 단일** — JGraphT(그래프) + JTS(기하) + PDFBox(PDF) + 자체 DXF Writer |

## 명령어

```bash
npm install
npm run dev              # 프론트 개발 서버 (http://localhost:9100)
npm run build            # tsc -b && vite build (타입체크 + 번들)
npm test                 # vitest (기하 로직 단위 테스트)
npm run symbols:validate # 심볼 세트 무결성 검사
npm run symbols:sheet    # 심볼 미리보기 시트 재생성

cd backend && ./gradlew build      # 컴파일 + 테스트

# 백엔드 (http://localhost:8290)
cd backend && ./gradlew bootRun
# PostgreSQL 없이 띄우기 — 뷰어/파서 작업용
cd backend && ./gradlew bootRun --args='--spring.profiles.active=dev-nodb'
```

> `isoflow` DB 가 아직 없으면 기본 프로파일은 기동에 실패한다.
> `createdb -U postgres isoflow` 로 만들거나 `dev-nodb` 프로파일을 쓴다.

포트는 Verso(9000/8190)와 겹치지 않게 **9100 / 8290** 을 씁니다.

> 작업 후 `npm run build` 와 `./gradlew build` 가 모두 통과하는지 확인합니다.

## 디렉터리 구조

```
src/
  main.tsx, App.tsx, index.css, vite-env.d.ts
  i18n/          index.ts + locales/{ko,en}.json
  store/         useAppStore.ts
  types/         pipeline.ts, scene3d.ts, scene2d.ts   ← schemas/ 와 동기화
                 isoStyle.ts                          ← IsoStyle.java 와 동기화
  components/
    layout/      AppLayout, MenuBar, RibbonBar, LeftPanel, RightPanel, MainCanvas, StatusBar
    viewer/      Viewer3D, Viewer2D
    dialogs/     StyleDialog
  viewer/        Scene3DRenderer.ts, Scene2DRenderer.ts
backend/
  src/main/java/co/atools/isoflow/
    project/ pipeline/ isometric/ symbolset/ storage/ config/ web/  ← REST/DB 계층 (Spring)
    engine/                                                 ← 순수 도메인 (Spring 의존 금지)
      parser/ model/ topology/ geometry/ layout/ symbol/ scene/ table/ style/
    export/  dxf/ pdf/ table/
  src/main/resources/
    application.yml
    db/migration/V{n}__*.sql        ← DB 스키마 권위
    engine/symbols-2d.json          ← 심볼 형상 라이브러리
    engine/skey-table.json          ← SKEY 매핑
  src/test/resources/tools/         ← 파이썬 검증·미리보기 도구
  src/test/resources/golden/        ← 등각도 회귀 스냅샷 (JSON + 검토용 SVG)
docs/
```

## 핵심 아키텍처

- **engine 은 순수 도메인**입니다. Spring·JPA·서블릿·export 를 참조하면 안 되고,
  `EngineArchitectureTest`(ArchUnit)가 이를 강제합니다. 덕분에 엔진을 CLI·배치·단위테스트에서 그대로 돌립니다.
- **처리 흐름**: 업로드 → `parser`(단위 정규화) → `model`(IR) → `topology`(조인트 병합·그래프) →
  `geometry`(리베이스·등각 투영) → `layout`(경로·치수·주석·시트) → `scene`(Scene2D/3D) → `export`(DXF/PDF)
- **Scene JSON 이 프론트-백엔드 계약**입니다. `schemas/*.json`(권위) ↔ `src/types/*.ts` 동기화를 유지합니다.
- **DXF/PDF 는 Scene2D 가 아니라 IR+레이아웃 결과에서 직접 생성**합니다.
  화면 렌더 캐시를 도면 출력의 원천으로 삼지 않습니다.
- **작도 순서는 `IsometricGenerator` 가 강제**합니다. ① 치수 계획 ② 자재표 집계는
  **길이 압축보다 먼저** 끝내야 합니다. 압축 후에 재면 발주 수량이 조용히 줄어듭니다.
- **도면은 여러 장일 수 있습니다.** `IsometricGenerator.Generated.sheets` 가 항상 배열이고,
  API 응답도 `scenes[]` 입니다. 자재표는 **1장에만** 얹고, 용접 번호는 **라인 전체 기준**으로 매깁니다.
- **라벨 회피에 선(중심선·치수선)은 선분으로 등록합니다.** 경계 상자로 넣으면
  긴 대각선 하나가 도면 절반을 막아 회피가 무의미해집니다.
- **화면에서 겹치는 배관은 뒤쪽을 끊습니다**(`CrossingBreaker`). 앞뒤는 화면 좌표로 알 수 없으니
  투영의 영공간(`IsoProjection.viewDirection`)으로 봅니다. 3D 로도 만나는 점은 실제 분기점이라
  끊으면 안 됩니다 — 실 코퍼스의 "교차"는 대부분 이쪽입니다.
- **상세도에서는 심볼을 키우지 않습니다**(`DetailPlanner`). 구간을 통째로 확대하면 심볼도 같이 커져
  겹침 비율이 그대로입니다 — 심볼 크기는 두고 **위치만** 벌려야 분리됩니다.
  원 반지름도 심볼 크기가 아니라 **위치가 퍼진 범위**로 잡습니다(안 그러면 도면이 상세도에 밀려납니다).
- **골든 회귀**: 작도를 고치면 `src/test/resources/golden/*.json` 이 깨집니다.
  `build/golden-actual/*.svg` 를 열어 눈으로 확인하고, 의도한 변화면
  `./gradlew test -Dgolden.update=true` 로 갱신합니다.
- **도면 상수는 `engine/style/IsoStyle` 에만 둡니다.** 새 상수를 클래스에 박지 말고 여기에 추가하고,
  기본값은 **기존 도면이 달라지지 않는 값**으로 잡습니다. 프론트 복제본 `src/types/isoStyle.ts` 도
  같이 고쳐야 합니다 — `IsoStyleContractTest` 가 어긋남을 잡습니다.

### 반드시 지켜야 할 데이터 규약

1. **좌표 리베이스** — PCF 좌표는 `5650130.600` 같은 절대 플랜트 좌표입니다.
   three.js 는 float32 라 그대로 넣으면 떨림·z-fighting 이 납니다.
   엔진이 bbox 중심을 `origin` 으로 빼고 **로컬 좌표(mm)만** 내려보냅니다. 프론트는 절대좌표를 다루지 않습니다.
2. **단위 정규화** — 같은 PCF 안에 `UNITS-BORE INCH` + `UNITS-CO-ORDS MM` 이 공존합니다.
   파서에서 흡수하고 내부 표준은 **좌표 mm / 보어 mm** 입니다.
3. **미지 키워드 passthrough** — 파서가 모르는 PCF 키워드도 `attrs`(JSONB)에 원문 보존합니다. 버리지 않습니다.
4. **조용한 실패 금지** — SKEY 조회 실패 등은 UNKNOWN 으로 표시하고 진단 리포트에 남깁니다.
5. **`CENTRE-POINT` 의 뜻은 컴포넌트마다 다릅니다.** 이름만 보고 "호의 중심"으로 쓰면 안 됩니다.
   - **ELBOW / BEND** → 두 배관 축이 만나는 **모서리점**. 호의 중심이 아닙니다.
     호의 중심은 `O = C + normalize(t1+t2)·(L/cos(half))` 로 파생합니다
     (`t1,t2` = C에서 각 END 로 향하는 단위벡터, `L=|A-C|`, `half`=사잇각/2).
     모서리를 호의 중심으로 쓰면 원호가 바깥으로 부풀어 **방향이 반대로** 보입니다.
   - **TEE / CROSS** → 런 위의 분기 교차점
   - **OLET** → 모재 배관 접속점. 여기만 **보어를 가집니다** — 연결점 판별자로 씁니다.
6. **다크 모드 색은 렌더러가 정합니다.** Scene2D 스타일의 `#111111` 을 그대로 쓰면
   다크 모드에서 문자와 채워진 심볼이 배경에 묻힙니다. 선·문자·채움 모두 테마 잉크로 덮어씁니다.
7. **IDF 의 보어는 좌표 6개 바로 다음 컬럼**(`head[6]`, `IdfRecordMap.BORE_TOKEN_INDEX`)이고
   **이미 호칭 mm** 라 좌표 스케일(0.01)을 곱하면 안 됩니다.
   **콤마 뒤 필드는 보어가 아닙니다** — 실 코퍼스 전수에서 `0/10000/1110000/1010000` 네 값뿐이고
   포트 수와도 무관합니다. 정체가 확정될 때까지 `IDF-FIELD-9/10` 으로 원문만 보존합니다.
   보어는 **다리마다 다릅니다** — 리듀싱 티는 런 조각(45/47)과 분기 조각(46)의 값이 다르므로
   포트별로 자기 다리의 보어를 실어야 합니다.

### 심볼 세트

- 형상은 **Verso Scene2D Element 형식**으로 정의합니다(`type`/`role`/`plane`). 상세는 [docs/symbol-set.md](docs/symbol-set.md).
- **등각 배치는 회전이 아니라 shear** 입니다. 심볼을 배관 축 포함 평면에 놓고 투영하면
  로컬→도면이 단일 affine 으로 떨어집니다. 평면 좌표에서 회전만 시키면 "평면도를 붙여놓은 것"처럼 보입니다.
- **Verso 렌더러는 임의 affine 을 적용하지 않습니다.** 엔진이 기하에 구워 넣어야 합니다
  (`circle`(iso)→`ellipse`, `arc`(iso)→타원호).
- `arc` 각도는 **라디안**, `rotation` 은 **도** — Verso 규약입니다.
- 배치 수식의 참조 구현은 `backend/src/test/resources/tools/render_symbol_sheet.py` 입니다.

## 작업 관행

- **주석/문서는 한국어**로 작성합니다. 새 파일 맨 위, 새 함수에 한 줄 설명 주석을 답니다.
- **모든 사용자 표시 문자열은 i18n 먼저** — `ko.json` → `en.json` → `t()` 순서. 하드코딩 금지.
  두 파일의 키 집합은 항상 일치해야 합니다. 키는 화면 문구가 아니라 **의미**로 짓습니다.
- **아이콘**은 `react-icons/md`(Material)를 씁니다.
- **색상**은 `#RRGGBB` / `#RRGGBBAA`.
- 에러 응답은 `{ code, <보간 파라미터…>, error }` — **번역은 프론트 책임**입니다.
  API 계층은 언어중립 오류만 던집니다.

## DB 스키마 (Flyway)

- **스키마 권위는 Flyway 마이그레이션**입니다: `backend/src/main/resources/db/migration/V{n}__*.sql`.
  백엔드 기동 시 자동 적용되며 JPA 는 `ddl-auto: none` 으로 관여하지 않습니다.
- **스키마를 바꿀 때는 기존 파일을 고치지 말고 새 `V{n}__*.sql` 을 추가**합니다.
  적용된 마이그레이션을 수정하면 체크섬 불일치로 기동이 실패합니다.
- 파일명은 `V2__add_spool_table.sql` 처럼 동사구 스네이크케이스. 첫 줄에 목적을 한국어 주석으로 답니다.
- 규약: PK=UUID(`gen_random_uuid`), enum류=VARCHAR+CHECK, 가변 데이터=JSONB, 검색용 GIN/trigram 인덱스.

## 작업 계획

- 계획은 `docs/plans/` 에 둡니다. 마일스톤 진행은 [로드맵](docs/plans/로드맵_수행목록.md) 체크리스트를 갱신합니다.
- 작업을 마치면 실제로 확인한 항목만 체크하고, 미확인 항목은 **사유를 남깁니다**.

## 참고

- 커밋/푸시는 사용자가 명시적으로 요청할 때만 수행합니다.
- 참조용 기존 구현: `D:\Projects\AViewer` (Python PCF/IDF 파서 — **이식 대상**, 재사용 아님).
  실 PCF 코퍼스는 `D:\Projects\AViewer\Projects\*.pcf` 입니다.
- humkyung@atools.co.kr로 커밋합니다.
