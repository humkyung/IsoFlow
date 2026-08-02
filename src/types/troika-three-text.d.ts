// troika-three-text.d.ts — troika-three-text 모듈의 최소 타입 선언 (공식 .d.ts 미제공)
declare module 'troika-three-text' {
  import { Mesh } from 'three'

  /** SDF 기반 텍스트 메시 */
  export class Text extends Mesh {
    text: string
    fontSize: number
    color: number | string
    anchorX: number | 'left' | 'center' | 'right' | string
    anchorY: number | 'top' | 'top-baseline' | 'middle' | 'bottom-baseline' | 'bottom' | string
    font: string | null
    /** 글리프 외곽선(faux-bold 에 사용) */
    outlineWidth: number | string
    outlineColor: number | string
    /** 여러 줄 정렬(left/right/center/justify) */
    textAlign: string
    /** sync 완료 후 채워지는 렌더 정보 */
    textRenderInfo?: { blockBounds: number[]; visibleBounds: number[]; glyphBounds?: Float32Array }
    /** SDF 비동기 갱신 */
    sync(callback?: () => void): void
    dispose(): void
  }
}
