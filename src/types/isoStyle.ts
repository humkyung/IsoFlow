// isoStyle.ts — 등각도 생성 설정. 백엔드 engine/style/IsoStyle.java 와 1:1 대응한다
//   값을 바꾸면 반드시 Java 쪽 record 와 함께 고친다 (JSON 그대로 주고받는다)

/** 용지 */
export interface SheetStyle {
  /** A4 / A3 / A2 / A1 */
  size: PaperSize
  /** 직접 지정 폭 (size 보다 우선). null 이면 규격을 따른다 */
  widthMm: number | null
  heightMm: number | null
  marginMm: number
  /**
   * 아래 표 띠 높이. 0 이면 표를 두지 않는다.
   * 우측 세로 칸이 아니라 아래 띠다 — 우측에 두면 도면 영역이 세로로 길어지는데
   * 등각도 내용은 가로로 길어서 세로가 남는다.
   */
  tableBandMm: number
  titleBlockMm: number
  /**
   * 자리를 못 찾아 겹친 채 놓인 라벨 비율이 이 값을 넘으면 시트를 나눈다 (0 이하면 안 나눔).
   * 문자 크기가 아니라 밀도를 본다 — 심볼이 도면 크기에 비례해 커지므로
   * 종이 위 문자 높이는 도면이 커져도 거의 그대로다.
   */
  maxLabelCrowding: number
  /** 나눌 수 있는 최대 장수 */
  maxSheets: number
}

export const PAPER_SIZES = ['A4', 'A3', 'A2', 'A1'] as const
export type PaperSize = (typeof PAPER_SIZES)[number]

/** 심볼 크기 */
export interface SymbolStyle {
  /** 도면 대각선 대비 심볼 크기 비율 */
  unitRatio: number
  minUnitMm: number
}

/** 치수 */
export interface DimensionStyle {
  /** 이보다 짧은 칸은 이웃에 합친다 (절대 하한, mm) */
  minIntervalMm: number
  /** 도면 크기 대비 최소 칸 비율 — 큰 도면의 잔치수를 막는다 */
  minIntervalRatio: number
  /** 치수선을 배관에서 띄우는 거리 (심볼 단위 배수) */
  offsetUnits: number
  stepUnits: number
  textHeightUnits: number
  decimals: number
}

/** 길이 압축(비축척) */
export interface CompressionStyle {
  enabled: boolean
  maxGapMm: number
}

/** 표시 항목 on/off */
export interface DisplayStyle {
  weldNumbers: boolean
  coordinateTags: boolean
  continuations: boolean
  northArrow: boolean
  lineNumber: boolean
  skewTriangles: boolean
  dimensions: boolean
  tables: boolean
  /** 화면에서 겹치는 배관 중 뒤쪽을 끊어 앞뒤를 표시한다 */
  crossingBreaks: boolean
  /** 심볼이 겹친 밀집 구간을 확대 상세도로 따로 그린다 */
  details: boolean
}

export interface IsoStyle {
  sheet: SheetStyle
  symbols: SymbolStyle
  dimensions: DimensionStyle
  compression: CompressionStyle
  display: DisplayStyle
}

/**
 * 기본값 — 백엔드 `IsoStyle.defaults()` 와 같아야 한다.
 * 프론트가 먼저 그릴 수 있도록 복제해 둔다. `GET /api/styles/default` 로 검증할 수 있다.
 */
export const DEFAULT_ISO_STYLE: IsoStyle = {
  sheet: {
    size: 'A3', widthMm: null, heightMm: null,
    marginMm: 10, tableBandMm: 70, titleBlockMm: 34,
    maxLabelCrowding: 0.15, maxSheets: 6,
  },
  symbols: { unitRatio: 0.018, minUnitMm: 1 },
  dimensions: {
    minIntervalMm: 50, minIntervalRatio: 0.004,
    offsetUnits: 3.5, stepUnits: 2.6, textHeightUnits: 1.1, decimals: 0,
  },
  compression: { enabled: true, maxGapMm: 2000 },
  display: {
    weldNumbers: true, coordinateTags: true, continuations: true, northArrow: true,
    lineNumber: true, skewTriangles: true, dimensions: true, tables: true,
    crossingBreaks: true, details: true,
  },
}

/** 표시 항목 키 목록 — 다이얼로그가 순서대로 체크박스를 만든다 */
export const DISPLAY_KEYS = [
  'dimensions', 'weldNumbers', 'coordinateTags', 'continuations',
  'northArrow', 'lineNumber', 'skewTriangles', 'crossingBreaks', 'details', 'tables',
] as const satisfies readonly (keyof DisplayStyle)[]
