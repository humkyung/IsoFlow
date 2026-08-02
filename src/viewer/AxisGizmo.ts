// AxisGizmo.ts — 뷰포트 좌하단에 그리는 좌표축 기즈모. 카메라 방향을 보여주고, 원을 클릭하면 그 축 정면을 판정해 준다
import * as THREE from 'three'

/** 기즈모가 차지하는 정사각 영역 한 변(CSS px) */
const SIZE = 96
/** 뷰포트 왼쪽·아래 가장자리에서 띄우는 여백(CSS px) */
const MARGIN = 10
/**
 * 기즈모 전용 직교 카메라의 반높이(월드 단위).
 * 축 막대 끝(1.0)에 놓인 원의 반지름까지 들어가도록 여유를 둔다.
 */
const HALF = 1.6
/** 축 색 — three.js ViewHelper 관례(X 적 / Y 녹 / Z 청)를 따른다 */
const AXIS_COLOR = { X: 0xff3653, Y: 0x8adb00, Z: 0x2c8fff } as const
/** 음의 방향 점은 같은 색을 어둡게 써서 앞뒤를 구분한다 */
const NEGATIVE_DIM = 0.5
/** 라벨이 붙는 양방향 원의 스프라이트 크기(월드 단위). 그려지는 원은 이 값의 절반이다 */
const POSITIVE_SCALE = 1.34
/** 라벨이 없는 음방향 점 크기 */
const NEGATIVE_SCALE = 0.9
/** 축 막대 굵기 */
const BAR_THICKNESS = 0.07

/** 클릭 대상 원 하나 — 스프라이트와 그 원이 뜻하는 월드 축 방향 */
interface Handle {
  sprite: THREE.Sprite
  /** 이 원을 클릭했을 때 카메라가 놓일 방향(월드 단위벡터) */
  direction: THREE.Vector3
}

/**
 * 원(+라벨) 하나를 캔버스 텍스처로 만든다.
 * 캔버스 64px 안에 반지름 16px 원을 그리므로 실제 보이는 원은 스프라이트 크기의 절반이다.
 */
function circleMaterial(color: THREE.Color, label: string | null): THREE.SpriteMaterial {
  const canvas = document.createElement('canvas')
  canvas.width = 64
  canvas.height = 64
  const ctx = canvas.getContext('2d')!

  ctx.beginPath()
  ctx.arc(32, 32, 16, 0, Math.PI * 2)
  ctx.closePath()
  ctx.fillStyle = color.getStyle()
  ctx.fill()

  if (label !== null) {
    ctx.font = 'bold 22px sans-serif'
    ctx.textAlign = 'center'
    ctx.fillStyle = '#111111'
    ctx.fillText(label, 32, 40)
  }

  const texture = new THREE.CanvasTexture(canvas)
  texture.colorSpace = THREE.SRGBColorSpace
  // toneMapped=false — 기즈모 색은 씬 조명·톤매핑과 무관하게 지정한 그대로 보여야 한다
  return new THREE.SpriteMaterial({ map: texture, toneMapped: false })
}

export class AxisGizmo {
  private scene = new THREE.Scene()
  /** 기즈모 전용 직교 카메라 — +Z 에서 원점을 본다 */
  private camera = new THREE.OrthographicCamera(-HALF, HALF, HALF, -HALF, 0, 4)
  /** 축 막대·원을 담는 루트. 매 프레임 본 카메라 회전의 역으로 돌린다 */
  private root = new THREE.Group()
  private handles: Handle[] = []
  /** 뷰포트를 되돌리기 위한 보관용 — 매 프레임 할당하지 않는다 */
  private savedViewport = new THREE.Vector4()

  constructor() {
    this.camera.position.set(0, 0, 2)
    this.scene.add(this.root)

    // 축 막대 — +X 방향으로 뻗는 상자를 만들고 축마다 돌려 쓴다
    const bar = new THREE.BoxGeometry(1, BAR_THICKNESS, BAR_THICKNESS).translate(0.5, 0, 0)
    const axes: [keyof typeof AXIS_COLOR, THREE.Vector3, THREE.Euler][] = [
      ['X', new THREE.Vector3(1, 0, 0), new THREE.Euler(0, 0, 0)],
      ['Y', new THREE.Vector3(0, 1, 0), new THREE.Euler(0, 0, Math.PI / 2)],
      ['Z', new THREE.Vector3(0, 0, 1), new THREE.Euler(0, -Math.PI / 2, 0)],
    ]

    for (const [label, direction, rotation] of axes) {
      const color = new THREE.Color(AXIS_COLOR[label])
      const mesh = new THREE.Mesh(bar, new THREE.MeshBasicMaterial({ color, toneMapped: false }))
      mesh.rotation.copy(rotation)
      this.root.add(mesh)

      this.addHandle(direction, color, label, POSITIVE_SCALE)
      // 음의 방향은 라벨 없이 어두운 점만 — 뒤를 향한 축이라는 표시다
      const dim = color.clone().multiplyScalar(NEGATIVE_DIM)
      this.addHandle(direction.clone().negate(), dim, null, NEGATIVE_SCALE)
    }
  }

  /** 축 끝에 원 하나를 놓고 클릭 대상으로 등록한다 */
  private addHandle(direction: THREE.Vector3, color: THREE.Color, label: string | null, scale: number) {
    const sprite = new THREE.Sprite(circleMaterial(color, label))
    sprite.position.copy(direction)
    sprite.scale.setScalar(scale)
    this.root.add(sprite)
    this.handles.push({ sprite, direction })
  }

  /**
   * 본 씬을 그린 뒤에 호출한다. 좌하단 뷰포트에만 기즈모를 덧그린다.
   * `autoClear` 를 잠시 끄는 이유 — 켜진 채로 두면 두 번째 렌더가 화면 전체를 지운다.
   */
  render(renderer: THREE.WebGLRenderer, viewCamera: THREE.Camera) {
    this.root.quaternion.copy(viewCamera.quaternion).invert()

    const autoClear = renderer.autoClear
    renderer.getViewport(this.savedViewport)
    renderer.autoClear = false
    renderer.clearDepth()
    renderer.setViewport(MARGIN, MARGIN, SIZE, SIZE)
    renderer.render(this.scene, this.camera)
    renderer.setViewport(this.savedViewport)
    renderer.autoClear = autoClear
  }

  /**
   * 포인터가 기즈모의 원 위에 있는지 본다. 있으면 그 원이 뜻하는 월드 축 방향을 준다.
   *
   * 스프라이트 레이캐스트 대신 직교 투영을 직접 px 로 풀어 쓴다 — 루트는 회전만 하고
   * 카메라도 고정이라 계산이 한 줄로 떨어지고, 겹친 원의 앞뒤도 z 로 바로 가른다.
   *
   * @param px 캔버스 좌상단 기준 x (CSS px)
   * @param py 캔버스 좌상단 기준 y (CSS px)
   * @param height 캔버스 높이 (CSS px) — 아래 기준 좌표로 뒤집는 데 쓴다
   */
  hitTest(px: number, py: number, height: number): THREE.Vector3 | null {
    const x = px - MARGIN
    const y = height - py - MARGIN
    if (x < 0 || y < 0 || x > SIZE || y > SIZE) return null

    let best: Handle | null = null
    let bestDepth = -Infinity
    for (const handle of this.handles) {
      const p = handle.sprite.position.clone().applyQuaternion(this.root.quaternion)
      const sx = (p.x / (2 * HALF) + 0.5) * SIZE
      const sy = (p.y / (2 * HALF) + 0.5) * SIZE
      // 캔버스에 그린 원이 스프라이트 크기의 절반이라 반지름도 1/4 배다
      const radius = ((handle.sprite.scale.x / 4) / (2 * HALF)) * SIZE
      if (Math.hypot(x - sx, y - sy) > radius) continue
      // 겹치면 카메라(+Z)에 가까운 쪽을 고른다
      if (p.z > bestDepth) {
        bestDepth = p.z
        best = handle
      }
    }
    return best ? best.direction.clone() : null
  }

  /** 지오메트리·재질·텍스처를 해제한다 */
  dispose() {
    this.root.traverse((o) => {
      if (o instanceof THREE.Mesh) {
        o.geometry.dispose()
        ;(o.material as THREE.Material).dispose()
      }
      if (o instanceof THREE.Sprite) {
        o.material.map?.dispose()
        o.material.dispose()
      }
    })
    this.root.clear()
    this.handles = []
  }
}
