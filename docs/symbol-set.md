<!-- 등각도 2D 심볼 세트 — Verso 프리미티브 형식, 등각 평면 배치 수식, SKEY 확장 규칙, 커버리지 -->

# IsoFlow 등각도 심볼 세트

| 파일 | 역할 |
|---|---|
| `backend/src/main/resources/engine/symbols-2d.json` | 형상 라이브러리 — **Verso Scene2D Element** 형식 |
| `backend/src/main/resources/engine/skey-table.json` | SKEY → 형상 매핑 + 엔드 타입 확장 규칙 |
| `backend/src/test/resources/tools/validate_symbol_set.py` | 참조 무결성 + 프리미티브 형식 검사 |
| `backend/src/test/resources/tools/render_symbol_sheet.py` | 등각 배치 미리보기 생성 (**배치 수식의 참조 구현**) |
| `docs/symbol-sheet.svg` | 미리보기 시트 |

**규모** — 형상 78개, 엔드 트리트먼트 15개, SKEY 매핑 142개. `schema version 2.0`.

```bash
python backend/src/test/resources/tools/validate_symbol_set.py
```

---

## 1. 심볼은 3가지 성격으로 나뉜다

### A. 절차적(Procedural) — 좌표에서 작도
**엘보 · 밴드 · 티 · 크로스 · 파이프**는 고정 심볼이 아니다. PCF 의 `END-POINT` / `CENTRE-POINT` /
`BRANCH1-POINT` / `ANGLE` 에서 형상을 계산해 그린다. 심볼 라이브러리는 **엔드 트리트먼트 마크만** 제공한다.
→ `skey-table.json` 의 `procedural` 섹션 참조.

### B. 인라인 심볼 — 배관 축에 놓이는 고정 형상
밸브 · 플랜지 · 리듀서 · 캡 · 유니온 · 스트레이너 · 트랩 · 계기 · 블라인드. 대부분의 SKEY 가 여기 속한다.

### C. 주석 마크
용접점 · 유동 화살표 · 노스 애로우 · 연속 화살표 · 보온 표기.
SKEY 로 오는 것(`WW`, `FLOW`, `INPP`…)과 엔진이 직접 꽂는 것(`NORTH_ARROW`, `CONTINUATION`…)이 섞여 있다.
후자는 `symbols-2d.json` 의 `engineInvoked` 목록에 있어 SKEY 미연결이 정상이다.

---

## 2. 프리미티브 — Verso Scene2D Element 서브셋

심볼 요소는 Verso `src/types/scene2d.ts` 의 `Element` 타입을 **그대로** 쓴다.
따라서 2D 뷰어가 별도 변환 없이 렌더할 수 있다.

사용 타입: `line` · `polyline` · `polygon` · `rect` · `circle` · `ellipse` · `arc` · `text`

```json
{ "type": "polygon", "points": [{"x":-0.55,"y":-0.42},{"x":-0.55,"y":0.42},{"x":0,"y":0}],
  "role": "outline" }
{ "type": "circle", "cx": 0, "cy": 0, "r": 0.15, "role": "solid", "plane": "screen" }
{ "type": "arc", "cx": 0, "cy": -0.10, "r": 0.55, "startAngle": 0.436332, "endAngle": 2.705260,
  "role": "outline" }
```

### 단위 규약 (Verso 준수)

- `arc` 의 `startAngle` / `endAngle` 는 **라디안**
- `ellipse` / `text` 의 `rotation` 은 **도(degree)**
- 검증 스크립트가 라디안 자리에 도 단위를 쓴 경우를 잡아낸다

### `role` — 공유 스타일 매핑

라이브러리에는 색·선굵기를 넣지 않는다. 그건 도면 스타일 설정이 정한다.
빌더가 `role` 을 `Scene2D.styles[]` 공유 스타일로 변환한다.

| role | 의미 |
|---|---|
| `outline` | 윤곽선만 (stroke) |
| `solid` | 윤곽선 + 채움 (stroke + fill) |
| `hidden` | 파선 (stroke.dash) |
| `text` | 문자 (font) |

### `plane` — 등각 배치 여부

| plane | 동작 |
|---|---|
| `iso` (기본) | 배관 축을 포함하는 등각 평면에 놓고 투영 → **기울어짐(shear)** |
| `screen` | 화면 기준 유지 — 용접 마커, 태그 문자, 노스 애로우 |

---

## 3. 등각 평면 배치 — 이 세트의 핵심

> **심볼을 평면 좌표에서 회전만 시키면 "평면도를 등각도에 붙여놓은 것"처럼 보인다.**
> 실제 등각도에서 심볼은 배관 축을 포함하는 3D 평면에 놓인 뒤 투영되므로 **기울어져야** 한다.
> (Ez-ISO 실제 출력에서도 밸브 보타이와 플랜지 바가 등각 평면으로 기울어져 있다.)

### 좌표계

```
        +Y (심볼 평면의 up)
         │
   ──────┼──────▶ +X  (배관 진행 방향)
         │
       원점 = 컴포넌트 중심
```
단위 `1.0` = 1 `symbolUnit` (스타일 설정, 기본 3.5mm). 인라인 심볼은 `X ∈ [-1, +1]` 로 정규화.

### 배치 수식

투영이 선형이므로 **로컬 2D → 도면 2D 전체가 하나의 affine** 으로 떨어진다.

```
1. u = 배관 진행 방향 3D 단위벡터
2. v = 심볼 평면의 up 3D 단위벡터           (아래 선택 규칙)
3. ex = P(u),  ey = P(v)                    P = 등각 투영
4. affine = [ ex.x·s, ex.y·s, ey.x·s, ey.y·s, P(C).x, P(C).y ]
                                            s = symbolUnit, C = 컴포넌트 중심
```

Verso `Affine` 규약과 동일한 `[a,b,c,d,e,f]` 순서다 (`x' = a·x + c·y + e`).
`ex` / `ey` 는 투영 후 **단위길이도 아니고 직교하지도 않는다** — 그 비직교성이 곧 우리가 원하는 shear다.

세계 축의 등각 투영(기본값, 화면 y-up. 프로젝트 설정으로 교체 가능):

| 축 | 화면 방향 |
|---|---|
| E (+X) | `( 0.8660, -0.5)` |
| N (+Y) | `(-0.8660, -0.5)` |
| U (+Z) | `( 0, 1)` |

### `v` 선택 규칙 — 잘못 고르면 심볼이 누워 보인다

1. **`SPINDLE-DIRECTION` 우선** — PCF 에 있으면 `u` 에 직교화해서 쓴다.
   밸브 스템의 실제 방향이 곧 심볼 평면이다. 기본 규칙으로 덮어쓰면 안 된다.
2. **수평 런** (`|u·Z| < 0.999`) — `v = normalize(Z − (Z·u)u)`.
   가장 수직에 가까운 직교축이라 심볼 횡방향이 화면 수직이 된다.
3. **수직 런** — 화면에서 위로 읽히는 수평축. 기본 `−N`(화면 우상향).
   여기서 `+E` 를 쓰면 액추에이터가 화면 아래로 향해 읽기 어려워진다.

### 굽기(baking) 규칙

**Verso 렌더러는 임의 affine 을 적용하지 않는다.** 회전조차 기하에 구워 넣는다
(`Scene2DRenderer` 가 `rect` 를 `polygon` 으로 바꾸는 것이 그 증거).
따라서 **엔진이 직접 변환해서 내보내야 한다.**

| 요소 | 처리 |
|---|---|
| `line` / `polyline` / `polygon` | 각 점을 affine 변환 |
| `rect` | `polygon` 으로 바꾼 뒤 각 점 변환 |
| `circle` (iso) | **`ellipse` 로 변환** — 2×2 행렬 `M=[[a,c],[b,d]]` 의 SVD 로 `rx`/`ry`/`rotation` 산출 |
| `arc` (iso) | 같은 SVD 로 `rx`/`ry`/`rotation`, 각도는 `t' = t − θ` 로 재매개화 |
| `circle` / `arc` (screen) | 중심만 변환, 반지름·각도 유지 |
| `text` | 앵커 점만 변환. `rotation`=0(태그) 또는 치수선 정렬각 |

`M = R(φ)·diag(σ₁,σ₂)·R(θ)ᵀ` 이므로 `rotation = φ`, `rx = σ₁·r`, `ry = σ₂·r`,
그리고 원 파라미터 `t` 는 타원 파라미터 `t − θ` 로 정확히 대응된다(근사 아님).

> 참조 구현은 `render_symbol_sheet.py` 의 `project()` / `plane_up()` / `affine_for()` / `svd2()` 에 있다.
> Java `Symbol2dLibrary` 구현 시 이 함수들을 그대로 옮기면 된다.

---

## 4. SKEY 확장 규칙

SKEY 는 2~4자. **앞 2자 = 형상**, **뒤 2자 = 접합 방식**. `**` 는 접합 방식 자리 와일드카드다
(실제 PCF 코퍼스에서 `EL**`, `TE**`, `RC**`, `RE**` 로 확인됨).

1. 알려진 endType 으로 끝나면 뒤 2자 분리 → base + endType
2. **정확히 일치하는 항목 우선** (`FLWN`, `WTBW` 같은 통짜 코드)
3. 없으면 `base + "**"` 패턴 조회 (`ELBW` → `EL**` + `BW`)
4. 없으면 `fallbackByPcfType` 으로 최소 표현 보장
5. 실패 시 **UNKNOWN 처리 + 진단 리포트 기록** — 조용히 버리지 않는다

**지원 endType (15종)**
`BW` 맞대기용접 · `SW` 소켓용접 · `SC` 나사 · `FL` 플랜지 · `PL` 평단면 · `CP` 압축 · `GL` 접착 ·
`PF` 밀어끼움 · `FA` 플레어 · `CL` 클램프 · `BS` 볼&소켓 · `LN` 라이너/너트 · `LC` 라이너/클램프 ·
`LR` 리듀싱라이너 · `MP` 수형접속부

---

## 5. 커버리지

### 실 코퍼스 검증

`D:\Projects\AViewer\Projects\*.pcf` (22개)에 등장하는 SKEY 전량 커버.

| SKEY | 출현 | 매핑 |
|---|---:|---|
| `WW` | 113 | 공장 용접 |
| `EL**` | 37 | 엘보 (절차적) |
| `FLOW` | 27 | 유동 화살표 |
| `TE**` | 17 | 티 (절차적) |
| `RC**` | 9 | 동심 리듀서 |
| `FLWN` | 5 | 웰드넥 플랜지 |
| `RE**` | 3 | 편심 리듀서 |
| `WTBW` | 2 | 웰돌렛 |
| `WM` | 2 | 마이터 용접 |

> 이 코퍼스는 냉각수 라인 위주라 밸브·계기가 거의 없다.
> **밸브 포함 PCF 샘플을 확보해 재검증해야 한다.**

### 카테고리별
플랜지 23 · 밸브 17 · 계기/제어밸브 26 · 피팅 7 · 올렛 12 · 용접 19 · 서포트 6 · 필터/트랩 8 · 기타 10 · 주석 8

### 표준 SKEY 가 없어 사내 배정이 필요한 형상
`VALVE_YTYPE` · `SPACER` · `HAMMER_BLIND` · `OLET_FLANGOLET` · `STRAINER_CONE`
형상은 정의되어 있으니 `skey-table.json` 에 한 줄 추가하면 된다.

---

## 6. 출처

| 자료 | 사용 범위 |
|---|---|
| [LibreTexts — Pipe Symbols](https://workforce.libretexts.org/Bookshelves/Manufacturing/Interpretation_of_Metal_Fab_Drawings_%28Moran%29/01%3A_Chapters/1.12%3A_Pipe_Symbols) (wermac 계열 도표 4종) | **형상 작도의 주 근거** |
| ISOGEN Symbol Keys (Alias Ltd.) | **SKEY 코드 체계** — 코드값, 엔드타입 치환, PCF/IDF 대응 |
| [HUEN SYSTEM Ez-ISO](https://eziso.huensystem.com/) 출력 예시 | **등각 평면 배치 검증** — 보타이/플랜지 바의 shear, 태그 문자 수평 유지 확인 |
| [AQC Inspection](https://aqcinspection.com/pipe-line-isometric-drawings-and-p-id-drawings/) | 스풀·방향 표기·롤링 오프셋 관례 |
| Verso `src/types/scene2d.ts` | 프리미티브 형식·단위 규약 |
| `AViewer/Projects/*.pcf` | 실제 사용 SKEY 검증 |

> Ez-ISO 는 심볼 라이브러리를 공개하지 않는다(자체 ISO 엔진, 심볼 커스터마이징 가능하다는 설명만 있음).
> 공개된 **출력 도면 이미지**를 배치 방식 검증용으로만 사용했다.

> 기하 정의는 관례를 참고해 직접 작성했다. 벤더 심볼 파일을 복제하지 않았으므로 자유롭게 수정 가능하다.

---

## 7. 사용자 정의 심볼 (오버레이)

기본 세트를 고치지 않고 **위에 덮는** 방식이다. 같은 이름이면 교체, 새 이름이면 추가한다.
부분 병합은 하지 않는다 — 형상의 일부 요소만 덮으면 무엇이 그려질지 예측할 수 없다.

파일 구조는 `symbols-2d.json` / `skey-table.json` 과 같다. 있는 블록만 반영된다.

```json
{
  "shapes": {
    "my-gate": {
      "name": "사내 표준 게이트 밸브",
      "elements": [
        {"type": "circle", "role": "outline", "plane": "iso", "cx": 0, "cy": 0, "r": 0.5},
        {"type": "line",   "role": "outline", "plane": "iso",
         "x1": -0.5, "y1": 0, "x2": 0.5, "y2": 0}
      ]
    }
  },
  "skeys": {
    "VGBW": {"shape": "my-gate", "pcfType": "VALVE", "category": "valve", "desc": "사내 게이트"}
  }
}
```

업로드하면 서버가 먼저 검사한다. **통과하지 못하면 저장하지 않는다** —
잘못된 세트를 받아 두면 도면에서 심볼이 조용히 빠지고, 그때는 원인을 찾기 어렵다.

| 검사 | 실패 예 |
|---|---|
| 요소 `type` 이 지원 목록에 있는가 | `bezier` → 거절 (`line`/`polyline`/`polygon`/`rect`/`circle`/`arc`/`text` 만) |
| SKEY 가 실재하는 형상을 가리키는가 | 없는 이름 → 거절. 같은 오버레이 안에서 정의한 형상은 허용 |
| `elements` 가 비어 있지 않은가 | 빈 배열 → 거절 |
| 최상위가 객체이고 블록이 하나라도 있는가 | `{}` / `[]` → 거절 |

```bash
curl -F "file=@my-symbols.json" -F "name=사내표준" http://localhost:8290/api/symbol-sets
```

생성·내보내기에 `symbolSetId` 를 함께 보내면 그 세트로 그린다.
`arc` 각도는 라디안, `rotation` 은 도 — 기본 세트와 같은 규약이다.

---

## 8. 다음 작업

- [ ] 밸브·계기 포함 PCF 샘플 확보 → 커버리지 재검증
- [ ] 사내 표준 등각도와 대조해 심볼 크기 비율·선 굵기 조정
- [ ] 표준 SKEY 없는 5개 형상에 사내 SKEY 배정
- [x] Java `Symbol2dLibrary` 구현 — `render_symbol_sheet.py` 의 배치 수식 이식
- [ ] `validate_symbol_set.py` 검사를 JUnit 테스트로 이식
      (오버레이 검증 `SymbolSet.validateOverlay` 는 이식 완료, 기본 세트 검사는 아직)
