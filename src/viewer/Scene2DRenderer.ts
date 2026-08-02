// Scene2DRenderer.ts — 등각도 2D 렌더러 (three.js OrthographicCamera, y-up CAD 좌표계)
import * as THREE from 'three'
import { LineSegments2 } from 'three/examples/jsm/lines/LineSegments2.js'
import { LineMaterial } from 'three/examples/jsm/lines/LineMaterial.js'
import { LineSegmentsGeometry } from 'three/examples/jsm/lines/LineSegmentsGeometry.js'
import { Text } from 'troika-three-text'
import type { Element2D, Scene2D, Style } from '@/types/scene2d'

/** 곡선을 선분으로 쪼갤 때의 최소 분할 수 */
const CURVE_MIN_SEGMENTS = 12
/** 라디안당 분할 수 — 큰 호일수록 촘촘해진다 */
const CURVE_SEGMENTS_PER_RAD = 8

export class Scene2DRenderer {
  private renderer: THREE.WebGLRenderer
  private scene = new THREE.Scene()
  private camera: THREE.OrthographicCamera
  private resizeObserver: ResizeObserver
  private frameId = 0

  /** 도면 요소 루트 */
  private sheetRoot = new THREE.Group()
  private lineMaterials: LineMaterial[] = []
  private fillMaterials: THREE.MeshBasicMaterial[] = []
  /** 테마가 바뀌면 색을 다시 칠해야 하는 문자들 */
  private texts: Text[] = []
  private disposables: { dispose(): void }[] = []
  private theme: 'light' | 'dark' = 'light'

  /** 화면에 보이는 도면 폭(도면 단위). 줌 상태를 이 값으로 관리한다 */
  private viewWidth = 1000
  private center = new THREE.Vector2(0, 0)

  constructor(private container: HTMLElement) {
    this.renderer = new THREE.WebGLRenderer({ antialias: true })
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
    container.appendChild(this.renderer.domElement)

    this.camera = new THREE.OrthographicCamera(-1, 1, 1, -1, -1000, 1000)
    this.camera.position.set(0, 0, 10)
    this.scene.add(this.sheetRoot)

    this.attachPanZoom()
    this.resizeObserver = new ResizeObserver(() => this.resize())
    this.resizeObserver.observe(container)
    this.resize()
    this.animate()
  }

  setTheme(theme: 'light' | 'dark') {
    this.theme = theme
    this.scene.background = new THREE.Color(theme === 'dark' ? 0x0f172a : 0xffffff)
    // 선과 문자 모두 배경 대비를 유지해야 읽힌다. 문자를 빼먹으면 다크 모드에서 사라진다
    const ink = Scene2DRenderer.inkColor(theme)
    for (const m of this.lineMaterials) m.color.setHex(ink)
    for (const m of this.fillMaterials) m.color.setHex(ink)
    for (const t of this.texts) {
      t.color = ink
      t.sync()
    }
  }

  /** 배경과 대비되는 잉크 색 */
  private static inkColor(theme: 'light' | 'dark'): number {
    return theme === 'dark' ? 0xe2e8f0 : 0x111111
  }

  /** Scene2D 를 그린다. 이전 도면은 해제한다 */
  setScene(scene: Scene2D | null, theme: 'light' | 'dark' = 'light') {
    this.clear()
    // 요소를 만들기 전에 테마를 확정해야 선·문자·채움이 처음부터 올바른 색으로 생성된다
    this.theme = theme
    if (!scene) return

    const styles = new Map(scene.styles.map((s) => [s.id, s]))
    // 스타일별로 선분을 모아 한 번에 그린다 — 요소마다 객체를 만들면 수천 개가 된다
    const strokeBatches = new Map<string, number[]>()

    for (const el of scene.elements) {
      const styleId = el.styleRef ?? 'default'
      const push = (x1: number, y1: number, x2: number, y2: number) => {
        let arr = strokeBatches.get(styleId)
        if (!arr) strokeBatches.set(styleId, (arr = []))
        arr.push(x1, y1, 0, x2, y2, 0)
      }
      this.emit(el, styles.get(styleId), push)
    }

    for (const [styleId, positions] of strokeBatches) {
      if (positions.length === 0) continue
      this.addLineBatch(positions, styles.get(styleId), theme)
    }

    this.fitToBounds(scene.bounds)
  }

  /** 요소 하나를 선분/채움/문자로 변환한다 */
  private emit(
    el: Element2D,
    style: Style | undefined,
    push: (x1: number, y1: number, x2: number, y2: number) => void,
  ) {
    switch (el.type) {
      case 'line':
        push(el.x1, el.y1, el.x2, el.y2)
        break
      case 'polyline':
        for (let i = 1; i < el.points.length; i++) {
          push(el.points[i - 1].x, el.points[i - 1].y, el.points[i].x, el.points[i].y)
        }
        break
      case 'polygon': {
        const pts = el.points
        for (let i = 0; i < pts.length; i++) {
          const a = pts[i]
          const b = pts[(i + 1) % pts.length]
          push(a.x, a.y, b.x, b.y)
        }
        // 채움 색도 백엔드 값 대신 테마 잉크를 쓴다 — 스타일에 박힌 검정을 그대로 쓰면
        // 다크 모드에서 채워진 심볼(용접점·화살표)이 배경에 묻힌다
        if (style?.fill?.color) this.addFill(pts)
        break
      }
      case 'circle':
        this.emitEllipse(el.cx, el.cy, el.r, el.r, 0, 0, Math.PI * 2, push)
        break
      case 'ellipse':
        this.emitEllipse(
          el.cx, el.cy, el.rx, el.ry,
          THREE.MathUtils.degToRad(el.rotation ?? 0), 0, Math.PI * 2, push,
        )
        break
      case 'arc':
        this.emitEllipse(
          el.cx, el.cy, el.rx ?? el.r, el.ry ?? el.r,
          THREE.MathUtils.degToRad(el.rotation ?? 0), el.startAngle, el.endAngle, push,
        )
        break
      case 'text':
        this.addText(el.x, el.y, el.content, el.height ?? 10, el.anchor ?? 'middle', el.rotation ?? 0)
        break
    }
  }

  /** 타원/호를 선분으로 쪼갠다. rotation 은 타원 축의 회전(라디안) */
  private emitEllipse(
    cx: number, cy: number, rx: number, ry: number, rotation: number,
    start: number, end: number,
    push: (x1: number, y1: number, x2: number, y2: number) => void,
  ) {
    const sweep = Math.abs(end - start)
    const n = Math.max(CURVE_MIN_SEGMENTS, Math.ceil(sweep * CURVE_SEGMENTS_PER_RAD))
    const cos = Math.cos(rotation)
    const sin = Math.sin(rotation)

    let px = 0
    let py = 0
    for (let i = 0; i <= n; i++) {
      const t = start + ((end - start) * i) / n
      const ex = rx * Math.cos(t)
      const ey = ry * Math.sin(t)
      const x = cx + ex * cos - ey * sin
      const y = cy + ex * sin + ey * cos
      if (i > 0) push(px, py, x, y)
      px = x
      py = y
    }
  }

  /** 스타일 하나에 해당하는 선분 묶음을 추가한다 */
  private addLineBatch(positions: number[], style: Style | undefined, theme: 'light' | 'dark') {
    const geom = new LineSegmentsGeometry()
    geom.setPositions(positions)

    const material = new LineMaterial({
      color: theme === 'dark' ? 0xe2e8f0 : 0x111111,
      linewidth: style?.stroke?.width ?? 1,
      dashed: !!style?.stroke?.dash,
      dashSize: style?.stroke?.dash?.[0] ?? 1,
      gapSize: style?.stroke?.dash?.[1] ?? 1,
    })
    // LineMaterial 은 픽셀 단위로 두께를 계산하므로 해상도를 알려줘야 한다
    material.resolution.set(this.container.clientWidth, this.container.clientHeight)
    this.lineMaterials.push(material)

    // LineSegments2 는 독립 선분 묶음용이다 (Line2 는 연결된 폴리라인용)
    const line = new LineSegments2(geom, material)
    if (style?.stroke?.dash) line.computeLineDistances()
    this.sheetRoot.add(line)
    this.disposables.push(geom, material)
  }

  /** 채워진 다각형 */
  private addFill(points: { x: number; y: number }[]) {
    const shape = new THREE.Shape(points.map((p) => new THREE.Vector2(p.x, p.y)))
    const geom = new THREE.ShapeGeometry(shape)
    const mat = new THREE.MeshBasicMaterial({
      color: Scene2DRenderer.inkColor(this.theme),
      side: THREE.DoubleSide,
    })
    this.fillMaterials.push(mat)
    const mesh = new THREE.Mesh(geom, mat)
    mesh.position.z = -0.1 // 외곽선 아래로
    this.sheetRoot.add(mesh)
    this.disposables.push(geom, mat)
  }

  /** SDF 문자 (troika) */
  private addText(x: number, y: number, content: string, height: number, anchor: string, rotationDeg: number) {
    const t = new Text()
    t.text = content
    t.fontSize = height
    t.color = Scene2DRenderer.inkColor(this.theme)
    t.anchorX = anchor === 'start' ? 'left' : anchor === 'end' ? 'right' : 'center'
    t.anchorY = 'middle'
    t.position.set(x, y, 0.1)
    t.rotation.z = THREE.MathUtils.degToRad(rotationDeg)
    t.sync()
    this.sheetRoot.add(t)
    this.texts.push(t)
    this.disposables.push(t)
  }

  /** 도면이 화면에 들어오도록 맞춘다 */
  fitToBounds(bounds: Scene2D['bounds']) {
    const [minX, minY, maxX, maxY] = bounds
    this.center.set((minX + maxX) / 2, (minY + maxY) / 2)
    const w = Math.max(maxX - minX, 1)
    const h = Math.max(maxY - minY, 1)
    const aspect = Math.max(this.container.clientWidth, 1) / Math.max(this.container.clientHeight, 1)
    // 세로가 더 빡빡하면 세로 기준으로 폭을 역산한다
    this.viewWidth = Math.max(w, h * aspect) * 1.12
    this.resize()
  }

  private clear() {
    for (const d of this.disposables) d.dispose()
    this.disposables = []
    this.lineMaterials = []
    this.fillMaterials = []
    this.texts = []
    this.sheetRoot.clear()
  }

  /** 휠 줌 + 드래그 팬 */
  private attachPanZoom() {
    const el = this.renderer.domElement
    el.addEventListener(
      'wheel',
      (e) => {
        e.preventDefault()
        this.viewWidth *= e.deltaY > 0 ? 1.1 : 1 / 1.1
        this.resize()
      },
      { passive: false },
    )

    let dragging = false
    let last = { x: 0, y: 0 }
    el.addEventListener('pointerdown', (e) => {
      dragging = true
      last = { x: e.clientX, y: e.clientY }
      el.setPointerCapture(e.pointerId)
    })
    el.addEventListener('pointermove', (e) => {
      if (!dragging) return
      const scale = this.viewWidth / Math.max(1, this.container.clientWidth)
      this.center.x -= (e.clientX - last.x) * scale
      this.center.y += (e.clientY - last.y) * scale
      last = { x: e.clientX, y: e.clientY }
      this.resize()
    })
    el.addEventListener('pointerup', (e) => {
      dragging = false
      el.releasePointerCapture(e.pointerId)
    })
  }

  /** 컨테이너 크기와 줌/팬 상태로 직교 카메라 절두체를 다시 만든다 */
  private resize() {
    const { clientWidth: w, clientHeight: h } = this.container
    if (w === 0 || h === 0) return
    const halfW = this.viewWidth / 2
    const halfH = (halfW * h) / w
    this.camera.left = this.center.x - halfW
    this.camera.right = this.center.x + halfW
    this.camera.top = this.center.y + halfH
    this.camera.bottom = this.center.y - halfH
    this.camera.updateProjectionMatrix()
    this.renderer.setSize(w, h, false)
    for (const m of this.lineMaterials) m.resolution.set(w, h)
  }

  private animate = () => {
    this.frameId = requestAnimationFrame(this.animate)
    this.renderer.render(this.scene, this.camera)
  }

  dispose() {
    cancelAnimationFrame(this.frameId)
    this.resizeObserver.disconnect()
    this.clear()
    this.renderer.dispose()
    this.renderer.domElement.remove()
  }
}
