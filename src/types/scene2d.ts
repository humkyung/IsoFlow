// scene2d.ts — 등각도 Scene 데이터 스키마. Verso scene2d.ts 의 Element 서브셋을 그대로 쓴다
//
// 단위 규약(Verso 준수): arc 의 startAngle/endAngle 는 **라디안**, ellipse/text 의 rotation 은 **도**.

export type Scene2DUnit = 'mm'

export interface Layer {
  id: string
  name: string
  visible: boolean
  order: number
}

export interface Stroke {
  color?: string
  width?: number
  /** 대시 배열 */
  dash?: number[]
}

export interface Fill {
  color?: string
}

export interface Font {
  family?: string
  size?: number
  align?: string
}

/** 공유 스타일. 심볼의 role 이 여기로 매핑된다 */
export interface Style {
  id: string
  stroke?: Stroke
  fill?: Fill
  font?: Font
}

export interface Point {
  x: number
  y: number
}

interface ElementBase {
  id: string
  layerId?: string
  styleRef?: string
}

export interface LineElement extends ElementBase {
  type: 'line'
  x1: number
  y1: number
  x2: number
  y2: number
}
export interface PolylineElement extends ElementBase {
  type: 'polyline'
  points: Point[]
}
export interface PolygonElement extends ElementBase {
  type: 'polygon'
  points: Point[]
}
export interface CircleElement extends ElementBase {
  type: 'circle'
  cx: number
  cy: number
  r: number
}
/** rotation 은 도(degree) */
export interface EllipseElement extends ElementBase {
  type: 'ellipse'
  cx: number
  cy: number
  rx: number
  ry: number
  rotation?: number
}
/** startAngle/endAngle 는 라디안, rotation 은 도 */
export interface ArcElement extends ElementBase {
  type: 'arc'
  cx: number
  cy: number
  r: number
  rx?: number
  ry?: number
  rotation?: number
  startAngle: number
  endAngle: number
}
/** rotation 은 도. anchor 는 start/middle/end */
export interface TextElement extends ElementBase {
  type: 'text'
  x: number
  y: number
  content: string
  rotation?: number
  anchor?: string
  height?: number
}

export type Element2D =
  | LineElement
  | PolylineElement
  | PolygonElement
  | CircleElement
  | EllipseElement
  | ArcElement
  | TextElement

export interface Scene2D {
  schemaVersion: string
  id: string
  units: Scene2DUnit
  /** 시트 번호 (1-base). 시트 분할 전에는 항상 1 */
  sheet: number
  /** [minX, minY, maxX, maxY] */
  bounds: [number, number, number, number]
  layers: Layer[]
  styles: Style[]
  elements: Element2D[]
}

/** 등각도 생성 API 응답 */
export interface GenerateResult {
  /** 등각도 시트들. 나누지 않으면 1개 */
  scenes: Scene2D[]
  diagnostics: import('./scene3d').Diagnostic3D[]
  fileName: string
}
