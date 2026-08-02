// scene3d.ts — 3D 배관 Scene 데이터 스키마 (PCF/IDF → 3D 뷰어 계약). schemas/scene3d.schema.json 과 동기화한다
//
// 설계 원칙: 백엔드는 **형상 종류(shape)와 좌표만** 내려보내고, 실제 지오메트리(Cylinder/Torus/Cone)는
// 프론트가 three.js 로 만든다. 메시를 직렬화하면 payload 가 수십 배로 커지고 LOD 조정도 불가능해진다.

/** 좌표 단위 — 엔진 내부 표준은 mm 고정 */
export type Scene3DUnit = 'mm'

/**
 * 프론트가 만들 지오메트리 종류. **엔진이 결정한다** — 프론트가 컴포넌트 타입에서 다시 추론하지 않는다.
 * 새 컴포넌트를 지원할 때 규칙이 두 곳으로 갈라지는 것을 막기 위한 계약이다.
 */
export type Shape3D =
  | 'PIPE' // 두 END 를 잇는 원기둥
  // CENTRE 는 **모서리점**(두 축의 교점)이다. 호의 중심은 여기서 파생해 계산한다
  | 'ELBOW'
  | 'TEE' // 런(END↔END) + 분기(CENTRE→BRANCH1)
  | 'CROSS' // 런 + 분기 2개
  | 'OLET' // CENTRE(모재)→BRANCH1(분기) 스터브
  // 두 END 사이의 원뿔대(양 끝 반지름이 다르다). flatDirection 이 있으면 편심(한쪽 모선이 평평)
  | 'REDUCER'
  | 'BODY' // 밸브/플랜지/커플링 등 두 END 사이의 굵은 원기둥
  // 앵글 밸브 — 두 END 에서 굵고 CENTRE(모서리)로 좁아지는 원뿔 2개
  | 'VALVE_ANGLE'
  // 버터플라이 밸브 — 웨이퍼 몸통 + spindleDirection 방향 스템 + 기어박스 + 핸드휠
  | 'VALVE_BUTTERFLY'
  // 게이트 밸브 — 보타이 몸통 + 보닛 + 라이징 스템 + 핸드휠
  | 'VALVE_GATE'
  // 글로브 밸브 — 게이트와 같되 가운데에 구(球)가 하나 더 (2D 심볼의 채운 원과 같은 규칙)
  | 'VALVE_GLOBE'
  // 볼 밸브 — 보타이 + 구 + 레버. 90° 회전이라 핸드휠이 아니라 레버가 달린다
  | 'VALVE_BALL'
  // 체크 밸브(스윙형) — 배럴 몸통 + 볼트 보닛(캡). flowToEnd 를 알면 캡이 상류로 밀린다
  | 'VALVE_CHECK'
  // 플러그 밸브 — 보타이 + 스핀들 축 테이퍼 플러그 + 레버 (볼과 같은 90° 조작)
  | 'VALVE_PLUG'
  | 'NONE' // 3D 에 그리지 않음 (용접·유동화살표·종단 표시)

/**
 * 편심 리듀서의 평평한 면이 향하는 방향 (PCF `FLAT-DIRECTION`). 배관 좌표계는 Z-up.
 *
 * PCF 의 END-POINT 좌표에는 편심량이 **이미 반영**되어 있다(작은쪽 중심이 평평한 면 쪽으로
 * `(OD_large − OD_small)/2` 만큼 어긋나 있다). 따라서 이 값으로 중심을 옮기는 것이 아니라,
 * 중심선 기울기에서 편심 성분을 빼내 **런 축**을 되찾는 데 쓴다.
 */
export type FlatDirection3D = 'UP' | 'DOWN'

/**
 * 밸브 스템(스핀들)이 향하는 방위 (PCF `SPINDLE-DIRECTION`).
 * 플랜트 좌표계는 X=동 / Y=북 / Z=위 이므로 EAST=+X, NORTH=+Y, UP=+Z 다.
 */
export type SpindleDirection3D = 'NORTH' | 'SOUTH' | 'EAST' | 'WEST' | 'UP' | 'DOWN'

/** 포트 종류 — 백엔드 PortKind 와 1:1 */
export type PortKind3D = 'END' | 'CENTRE' | 'BRANCH1' | 'BRANCH2' | 'COORD'

export interface Port3D {
  kind: PortKind3D
  ordinal: number
  /** 리베이스된 로컬 좌표 [x, y, z] (mm) */
  p: [number, number, number]
  /** 보어(mm). 없으면 생략 */
  bore?: number
  /** 접합 방식 (BW/SW/SC/FL…) */
  endType?: string
  /** 위상 해석이 매긴 접합점 id. 같으면 같은 접합점이다 */
  joint?: string
}

export interface Component3D {
  /** Scene 안에서 유일한 id (예: "c12") */
  id: string
  /** 엔진 ComponentType 이름 (PIPE, ELBOW, REDUCER_CONCENTRIC …) */
  type: string
  /** PCF 원문 키워드 — UNKNOWN 타입의 원본을 잃지 않기 위해 */
  rawKeyword: string
  shape: Shape3D
  ports: Port3D[]
  skey?: string
  itemCode?: string
  description?: string
  weight?: number
  /** 엘보/밴드 각도(도) */
  angleDeg?: number
  /** 편심 리듀서의 평평한 면 방향. 없으면 동심 원뿔대로 그린다 */
  flatDirection?: FlatDirection3D
  /** 밸브 스템이 향하는 방위. 버터플라이 밸브의 스템·기어박스를 이 방향으로 뻗는다 */
  spindleDirection?: SpindleDirection3D
  /**
   * 유동이 향하는 END 포트의 `ordinal`. 체크 밸브의 원뿔·시트가 이 방향을 향한다.
   * PCF 의 `FLOW` 규약은 엔진이 흡수하므로 프론트는 **포트 번호만** 본다.
   */
  flowToEnd?: number
  /** PCF 원문 속성 — 우측 속성 패널이 그대로 보여준다 */
  attrs?: Record<string, string>
}

/** 진단 한 건 — 문구가 아니라 코드 + 보간 파라미터로 온다 (번역은 프론트 책임) */
export interface Diagnostic3D {
  severity: 'INFO' | 'WARNING' | 'ERROR'
  /** i18n 키는 `diag.<code>` */
  code: string
  params: Record<string, unknown>
  /** 원본 파일 줄 번호. 0 이면 없음 */
  lineNo: number
}

export interface PipelineInfo {
  lineNumber?: string
  pipingSpec?: string
  nominalClass?: string
  area?: string
  revision?: string
  /** 파이프라인 수준 원문 속성 (ATTRIBUTEnn 등) */
  attrs?: Record<string, string>
}

export interface Scene3D {
  /** 스키마 버전 (semver) */
  schemaVersion: string
  id: string
  units: Scene3DUnit
  /**
   * 리베이스 오프셋 (mm). `로컬좌표 + origin = 원본 플랜트 좌표`.
   * 좌표를 절대값으로 보내면 three.js 의 float32 정밀도가 부족해 떨림이 생긴다.
   */
  origin: [number, number, number]
  /** 로컬 좌표 기준 경계 상자 [minX, minY, minZ, maxX, maxY, maxZ] */
  bounds: [number, number, number, number, number, number]
  pipeline: PipelineInfo
  components: Component3D[]
  /** 자재 항목 (item-code → 설명) */
  materials?: Record<string, string>
}

/** import API 응답 — Scene 과 진단을 함께 받는다 */
export interface ImportResult {
  scene: Scene3D
  diagnostics: Diagnostic3D[]
  /** 원본 파일명 */
  fileName: string
}
