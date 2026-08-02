<!-- PCF/IDF를 입력으로 배관 등각도(Isometric Drawing)를 생성하는 시스템의 요구사항·아키텍처 정리 문서 -->

# IsoFlow — PCF/IDF 기반 배관 등각도 생성 시스템 요구사항

## 0. 목적

3D 배관 설계 시스템이 내보낸 **PCF(Piping Component File)** 또는 **IDF(Isogen Data File)** 를 읽어,
제작/시공에 사용 가능한 **배관 등각도(Piping Isometric Drawing)** 를 자동 생성한다.
즉, ISOGEN 계열 엔진이 수행하는 일을 자체 구현하는 것이 범위다.

---

## 1. 전체 처리 파이프라인

```
[PCF / IDF]
   │  ① 입력 파서 (Parser / Adapter)
   ▼
[중립 도메인 모델 (IR)]  ── Pipeline / Component / Port / Material
   │  ② 위상 해석 (Topology Resolver)
   ▼
[연결 그래프]  ── 노드=조인트, 간선=배관, 분기=Tee/Branch
   │  ③ 기하 해석 (Geometry Engine) — 축 판정, skew/offset, 등각 투영
   ▼
[2D 등각 좌표계 도형]
   │  ④ 심볼 배치 (Symbol Library / SKEY 매핑)
   │  ⑤ 치수·주석 엔진 (Dimension & Annotation)
   │  ⑥ 시트 레이아웃 (Sheet Splitting, Title Block, BOM)
   ▼
[출력]  DXF / DWG / PDF / SVG  +  BOM·Cut list·Weld list (CSV/XLSX)
```

---

## 2. 입력 포맷 처리

### 2.1 PCF (우선 구현 대상)

- 순수 **텍스트 파일**, 라인 기반. 파싱 난이도가 낮아 1차 타깃으로 적합.
- **헤더 영역** (반드시 컬럼 1에서 시작, 컴포넌트 데이터보다 먼저 위치)
  - `ISOGEN-FILES`, `UNITS-BORE`, `UNITS-CO-ORDS`, `UNITS-WEIGHT`, `UNITS-BOLT-DIA`, `UNITS-BOLT-LENGTH`
  - `PIPELINE-REFERENCE` (파이프라인 식별자), `REVISION`, `AREA`, `PIPING-SPEC`,
    `NOMINAL-CLASS`, `DATE-DMY`, `PIPELINE-TEMP`, `PLANT-AREA`
- **컴포넌트 레코드** — 타입 키워드 + 들여쓴 속성 라인
  - 타입: `PIPE`, `ELBOW`, `BEND`, `TEE`, `CROSS`, `FLANGE`, `VALVE`, `GASKET`, `BOLT`,
    `REDUCER-CONCENTRIC`, `REDUCER-ECCENTRIC`, `CAP`, `COUPLING`, `UNION`, `SUPPORT`,
    `INSTRUMENT`, `FILTER`, `TRAP`, `MISC-COMPONENT` 등
  - 속성: `END-POINT x y z bore`, `CENTRE-POINT x y z`, `BRANCH1-POINT x y z bore`,
    `SKEY`, `ITEM-CODE`, `WEIGHT`, `SPINDLE-DIRECTION`, `COMPONENT-ATTRIBUTEn`,
    `UCI`, `WELD`, `INSULATION`, `TAG` 등
- **MATERIALS 섹션** — `ITEM-CODE` ↔ 자재 설명/스펙 매핑 테이블 (BOM 생성의 원천)
- 파서 요구사항
  - 컬럼 위치·들여쓰기 규칙 준수, 대소문자·공백 관용 처리
  - 미지의 키워드는 **손실 없이 보존**(passthrough) — 벤더별 확장 속성 대응
  - 단위 정규화(inch/mm) 레이어 필수

### 2.2 IDF

- PDS, PDMS, PASCE, Tribon 등이 내보내던 **원조 포맷**. PCF보다 구조가 복잡하고 고정 레코드 성격.
- 실무상 `PCF + 스타일 → IDF → Isogen 엔진` 순으로 쓰이던 중간 파일이기도 함.
- **어댑터 계층으로 분리**해서 IDF → 동일한 중립 도메인 모델로 흡수하는 구조 권장.
  (엔진 본체는 입력 포맷을 몰라야 함)

### 2.3 필요한 산출물

- 포맷별 파서 + 검증기(validator)
- 샘플 PCF/IDF **테스트 코퍼스** (실제 프로젝트 파일 다수 확보가 품질을 좌우)

---

## 3. 중립 도메인 모델 (IR)

| 엔티티 | 핵심 필드 |
|---|---|
| `Pipeline` | line number, spec, class, area, temp, insulation, revision |
| `Component` | type, skey, item-code, ports[], weight, tag, attributes |
| `Port(EndPoint)` | position(x,y,z), bore, end-type(BW/SW/SC/FL/PL), male/female |
| `Joint` | 좌표 tolerance로 병합된 연결점, 용접/플랜지/나사 구분 |
| `Weld` | shop/field 구분(FW/SW), 번호, 위치 |
| `Spool` | 제작 단위 분할, 스풀 번호 |
| `MaterialItem` | item-code, description, qty, size, spec |

---

## 4. 위상 해석 (Topology Resolver)

- 좌표 **허용오차 기반 노드 병합** (부동소수/단위 반올림 오차 흡수)
- 컴포넌트-포트를 정점/간선으로 하는 **그래프 구축** (networkx 류)
- 검출 항목
  - 미연결(dangling) 포트, 중복 컴포넌트, 보어 불일치, 끊긴 라인
  - 분기(Tee/Branch) 및 Branch line 연결
  - 루프/순환 경로
- **주 경로(main run) 결정** — 등각도에서 어떤 경로를 주축으로 그릴지 결정하는 핵심 로직

---

## 5. 기하 엔진 (Geometry Engine)

- **축 방향 판정**: N/S/E/W/U/D 6방향 분류
- **Skew / Rolling offset 처리**
  - 수직 오프셋, 수평 오프셋, 복합 오프셋
  - 비축 방향 배관은 **해칭 삼각형(hatched triangle)** 으로 평면을 표기 (참조 링크 ①)
- **등각 투영 변환**: 3D(x,y,z) → 2D. 표준 30°/30° 축측 투영 행렬
- **가시성·겹침 처리**
  - 투영 후 선/심볼 겹침 검출
  - 자동 회피: 경로 오프셋(break), 축소 표기, 상세도(detail) 분리
- **비축척(not to scale)** 표현 — 등각도는 축척 도면이 아니므로 길이 압축 규칙 필요

---

## 6. 심볼 라이브러리 (SKEY 매핑)

- ISOGEN의 **SKEY(Symbol Key)** 체계를 따르는 것이 상호운용성 측면에서 유리
  - 통상 4자 코드: **앞 2자 = 컴포넌트 형상**, **뒤 2자 = 엔드 타입**
    (예: `FLWN` weld-neck flange, `GTBW` butt-weld gate valve, `EL90`, `TESW`)
  - 심볼 키가 2자이면 endtype 문자열을 덧붙이고, `**` 가 있으면 endtype으로 치환
- 필요한 것
  - **SKEY → 벡터 심볼** 정의 파일 (SVG path 또는 DXF block)
  - 6방향 × 회전에 대한 **심볼 변형(orientation) 규칙**
  - 사용자 정의 심볼 확장 메커니즘 (프로젝트별 특수 부품)
  - 조인트 표기: 용접(●), 나사, 플랜지, 소켓 구분 (참조 링크 ③)

---

## 7. 치수·주석 엔진

등각도에 반드시 들어가야 하는 항목:

- **치수(Dimension)**: 축별 러닝/체인 치수, 치수선·연장선 자동 배치, 각도 치수
- **좌표 태그**: 시작/끝점 절대좌표(N/E/EL)
- **노스 애로우(North Arrow)** — 도면 방향 기준
- **라인 번호 / 스풀 번호** — 스풀은 제작·운반 단위 분할 (참조 링크 ①)
- **용접점 번호** (shop weld / field weld 구분)
- **연속 표기(Continuation)**: 다른 라인/시트로 이어지는 화살표 + 참조번호
- **기타 태그**: 밸브 태그, 계기 태그, 서포트 번호, 경사(slope), 보온/도장, 유동 방향 화살표
- **라벨 충돌 회피** — 자동 배치 최적화(이것이 자동 생성 품질의 체감 차이를 만듦)

---

## 8. 시트 레이아웃

- **도면 프레임 + 타이틀 블록** (프로젝트/사내 표준 템플릿화)
- **시트 자동 분할**: 한 라인이 한 장에 안 들어갈 때 분할 + 연속 심볼 연결
- **테이블 영역**
  - Bill of Materials (자재 집계표)
  - Cut Pipe List (파이프 절단 리스트)
  - Weld List (용접 리스트)
  - Notes / Revision block
- 용지 규격(A1/A2/A3), 여백, 폰트, 선 굵기 설정

---

## 9. 출력

- **도면**: DXF (1차), DWG, PDF, SVG (웹 뷰어용)
- **데이터**: BOM / Cut list / Weld list → CSV, XLSX
- (선택) 웹 기반 등각도 뷰어 — 심볼 클릭 시 PCF 원본 속성 조회

---

## 10. 설정 / 스타일 시스템

ISOGEN의 "Isometric Style"에 해당하는 **프로젝트별 설정 계층**이 필요하다.

- 단위계, 치수 정밀도/반올림
- 심볼 세트 선택, 선 종류/굵기, 폰트
- 표시 항목 on/off (좌표, 용접번호, 보온, 서포트 …)
- 시트 크기·템플릿, BOM 컬럼 구성·정렬·집계 규칙
- 스풀 분할 규칙 (최대 길이/무게 기준)

---

## 11. 검증 / QA

- 파서 단위 테스트 + **라운드트립 테스트**(PCF → IR → PCF)
- 기하 무결성 검사 리포트 (미연결/중복/보어 불일치)
- **골든 이미지 회귀 테스트** — 샘플 PCF의 출력 도면 스냅샷 비교
- 상용 ISOGEN 출력과의 대조 검증 (가능한 경우)

---

## 12. 단계별 로드맵

| 단계 | 범위 |
|---|---|
| **M1** | PCF 파서 + 도메인 모델 + 연결 그래프 + 콘솔 리포트 |
| **M2** | 등각 투영 + 기본 심볼(엘보/티/플랜지/밸브/리듀서) → SVG 출력 |
| **M3** | 치수·라인번호·노스애로우·좌표 태그 |
| **M4** | BOM/Cut list/Weld list + 타이틀블록 + DXF/PDF 출력 |
| **M5** | 시트 자동 분할, 겹침 회피, 라벨 배치 최적화 |
| **M6** | IDF 어댑터, 스타일 설정 시스템, 사용자 심볼 확장 |

---

## 13. 기술 스택 (확정 — Verso 준용)

`D:\Projects\00 Verso\Verso` 와 동일한 스택을 사용한다.

| 영역 | 기술 |
|------|------|
| 빌드/프레임워크 | Vite 6 + React 19 + TypeScript |
| 스타일 | Tailwind CSS v4 (`@tailwindcss/vite`), Dark/Light 테마 |
| 상태 | Zustand |
| 3D 렌더 (PCF/IDF 모델) | three.js 0.171 (PerspectiveCamera) |
| 2D 렌더 (등각도) | three.js 0.171 (OrthographicCamera) + troika-three-text(SDF) + Line2(fat line) |
| 아이콘 | react-icons (`react-icons/md`) |
| 다국어 | react-i18next (ko 기본 / en) — **하드코딩 금지, 키 먼저 등록** |
| 백엔드 | Spring Boot (Java 21) + PostgreSQL + Flyway 마이그레이션 |
| **엔진(파서·등각도·출력)** | **Java 단일** — Spring Boot 내 `engine` 패키지. JGraphT(그래프) + JTS(기하) + PDFBox(PDF) + 자체 DXF Writer |

### 화면 구성

- **3D 뷰어** — PCF/IDF를 읽어 배관 모델을 3D로 표시 (파이프=Cylinder, 엘보=Torus, 부품=SKEY 심볼/박스)
- **2D 뷰어** — 생성된 등각도를 표시 (Verso `Scene2DRenderer` 구조 재사용)
- **출력** — DXF, PDF

---

## 13-1. 기존 자산 조사 결과 (`D:\Projects\AViewer`)

기존 Python/VTK 구현이 있다. **Java로 이식**한다(재사용 아님 — 레코드 규칙만 참고).

### PCF 파서 (`AViewer/PCF/`)

- `PCFImporter.py` — 라인 단위 스캔. **첫 글자가 공백이 아니면 새 레코드 시작**, 들여쓰기 라인은 속성.
  이 규칙 하나로 블록 분리가 되므로 TS 이식이 단순함.
- 구현된 레코드: `PipeRecord`, `ElbowRecord`(END-POINT×2 + CENTRE-POINT + ANGLE, 각도는 ×0.01), `TeeRecord`(+ BRANCH1-POINT)
- **미구현**: FLANGE, VALVE, REDUCER, GASKET, BOLT, SUPPORT, INSTRUMENT, CAP, WELD, MATERIALS 섹션 → 신규 작성 필요
- 출력은 VTK actor(`Primitives/Cylinder`, `Primitives/CTorus`) → 백엔드는 **Scene3D JSON**만 생성하고,
  실제 형상은 프론트가 three.js `CylinderGeometry` / `TorusGeometry` 로 만든다 (형상 생성 책임이 프론트로 이동)

### IDF 파서 (`AViewer/IDF/`)

- PDS 계열 **고정 컬럼 수치 레코드** 포맷. 텍스트지만 PCF와 성격이 완전히 다름.
- 라인 앞 4자 = 레코드 코드
  - 파일 선두: 옵션 플래그 블록(14열 정수 행 다수)
  - **음수 코드** = 파일 참조/라벨 (`-102` 도면명, `-103` 심볼 라이브러리 경로, `-104` BOM, `-207` NPD, `-222` SLOPE …)
  - **양수 코드** = 컴포넌트: `100` PIPE, `35/36` ELBOW, `45/46/47` TERF(tee), `105` FLWN, `107` FLBL, `300/301` 좌표 오프셋(metric/imperial), `999` EOF
- `IDFRecord` 는 컬럼 위치 `[7,20,33]`에서 좌표 3개를 잘라 읽고, `IDFPipeRecord` 는 공백 분할로 start/end/bore 7개를 읽음 → **두 방식이 혼재**. 실제 IDF 스펙 컬럼 맵을 정리해 일관되게 재작성 필요.
- 샘플 파일 2개 보유: `AViewer/IDF/79qcd01br014*.idf`

### 샘플 데이터

- **PCF 실샘플 다수**: `AViewer/Projects/SC2851__100__P1001__MEG-100-PI-ISO-P1001-*.pcf` → 회귀 테스트 코퍼스로 사용
- 실샘플에서 확인된 추가 키워드: `ATTRIBUTE20~54`(라인 속성 다수), `HIGHEST-WELD-NUMBER`,
  `WELD`(SKEY `WW`, `MASTER-COMPONENT-IDENTIFIER`, `WELD-ATTRIBUTE1`), `CUT-PIECE-LENGTH`,
  `UCI`(GUID), `ITEM-CODE` / `ITEM-DESCRIPTION`, `FABRICATION-ITEM`, `INSULATION ON`

### ⚠ 이식 시 주의사항 (실샘플에서 확인)

1. **단위 혼재** — `UNITS-BORE INCH` 인데 `UNITS-CO-ORDS MM`. 보어와 좌표의 단위계가 다르므로
   파서 단계에서 반드시 정규화(내부 표준: 좌표 mm, 보어 mm)해야 한다.
2. **대좌표 정밀도** — 좌표가 `5650130.600` 처럼 절대 플랜트 좌표다.
   three.js는 **float32**이므로 그대로 넣으면 z-fighting·떨림이 발생한다.
   → 파이프라인 bounding box 중심을 원점으로 **리베이스(offset)** 하고, 오프셋 값을 별도 보관해 표시용 좌표로 환산.
3. **IDF 좌표 오프셋** — 레코드 `300`(metric)의 값에 스케일(`×100000`, `×1000000`)이 걸려 있음. 실제 스펙 확인 필요.
4. **`CENTRE-POINT` 는 이름과 달리 "호의 중심"이 아니다** — 컴포넌트마다 뜻이 다르다.
   코퍼스 실측으로 확인한 내용:

   | 컴포넌트 | CENTRE-POINT 의 의미 | 보어 |
   |---|---|---|
   | ELBOW / BEND | 두 배관 축이 만나는 **모서리점** | 없음 |
   | TEE / CROSS | 런 위의 분기 교차점 (두 END 의 중점) | 없음 |
   | OLET | 모재 배관 접속점 | **있음** |

   엘보 예시 — CENTRE 가 한쪽 END 와 X·Y 가 같고 다른 END 와 Z 가 같다(= 축 교점):
   ```
   END-POINT    5650130.600  1993150.000  -344.800
   END-POINT    5649753.712  1992773.112  -877.800
   CENTRE-POINT 5650130.600  1993150.000  -877.800
   ```
   이걸 호의 중심으로 쓰면 3D 엘보가 모서리 바깥으로 부풀어 **방향이 반대로** 보인다.
   실제 호의 중심은 두 접선의 수직선 교점으로 파생해야 한다.
   등각도(2D)는 엘보를 `END→CENTRE→END` 꺾은선으로 그리므로 이 값을 그대로 쓰는 것이 맞다.

---

## 14. 참고 자료

- [Pipe Line Isometric Drawings and P&ID Drawings — AQC Inspection](https://aqcinspection.com/pipe-line-isometric-drawings-and-p-id-drawings/)
- [Pipe Symbols — LibreTexts (Interpretation of Metal Fab Drawings)](https://workforce.libretexts.org/Bookshelves/Manufacturing/Interpretation_of_Metal_Fab_Drawings_%28Moran%29/01%3A_Chapters/1.12%3A_Pipe_Symbols)
- [Isometrics — Siemens COMOS (PDF)](https://cache.industry.siemens.com/dl/files/459/60593459/att_104732/v1/Isometrics_enUS_en-US.pdf)
- [PCF Reference Guide — Hexagon PPM](https://docs.hexagonppm.com/r/2016PCFReference)
- [PCF 문법 요약 — Far East Piper Reference](https://sites.google.com/site/fareastpiperreference/pcf)
- [PCF Export — Cadmatic](https://docs.cadmatic.com/plant/Content/Piping%20Isometrics/Application/Manage/link_pcf.htm)
- [ISOGEN Symbol Map File (SKEY) — PTC](https://support.ptc.com/help/creo/creo_pma/r11.0/usascii/piping/piping/ISOGEN_Symbol_Map_File__Spec-Driven_.html)
- [Isogen Symbol Key (SKEY) Definitions Reference — Hexagon](https://docs.hexagonppm.com/r/en-US/Isogen-Symbol-Key-SKEY-Definitions-Reference/Version-2016-14.0-UPDATED/282147)
- [Piping Design (PDF)](https://kh.aquaenergyexpo.com/wp-content/uploads/2023/02/Piping-Design.pdf)
- [Piping Isometrics — SlideShare](https://www.slideshare.net/slideshow/piping-isometricspdf/253123693)
