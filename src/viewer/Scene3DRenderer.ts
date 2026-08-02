// Scene3DRenderer.ts — PCF/IDF 배관 모델용 three.js 3D 렌더러 (Z-up, 배관 좌표계 관례)
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import type { Scene3D } from '@/types/scene3d'
import { buildScene, disposeGroup } from './primitives3d'
import { AxisGizmo } from './AxisGizmo'

/** 선택 강조 색 */
const HIGHLIGHT = 0xffb703
/** 기즈모 클릭 시 축 정면으로 돌아가는 시간(초) */
const SNAP_SECONDS = 0.35

export class Scene3DRenderer {
  private renderer: THREE.WebGLRenderer
  private scene = new THREE.Scene()
  private camera: THREE.PerspectiveCamera
  private controls: OrbitControls
  private resizeObserver: ResizeObserver
  private frameId = 0

  /** 파이프라인 지오메트리 루트 — 다시 그릴 때 이 그룹만 비운다 */
  private modelRoot = new THREE.Group()
  /** 좌하단 좌표축 기즈모 */
  private gizmo = new AxisGizmo()
  /** 축 정렬 애니메이션 상태 — null 이면 정렬 중이 아니다 */
  private snap: { from: THREE.Vector3; rotation: THREE.Quaternion; t: number } | null = null
  private clock = new THREE.Clock()
  private pickables: THREE.Mesh[] = []
  private raycaster = new THREE.Raycaster()
  /** 선택 전 원래 재질을 되돌리기 위해 보관한다 */
  private originalMaterials = new Map<THREE.Mesh, THREE.Material>()
  /** 모델 재질 목록 — 투명도를 한 번에 바꾸기 위해 모아둔다 */
  private modelMaterials: THREE.Material[] = []
  /** 현재 모델 불투명도(0~1). 1 이면 불투명 */
  private opacity = 1
  private highlightMaterial = new THREE.MeshStandardMaterial({
    color: HIGHLIGHT,
    roughness: 0.4,
    metalness: 0.3,
    emissive: new THREE.Color(0x5a3d00),
  })

  /** 컴포넌트를 클릭했을 때 호출된다. null 이면 빈 곳 클릭 */
  onSelect?: (componentId: string | null) => void

  constructor(private container: HTMLElement) {
    this.renderer = new THREE.WebGLRenderer({ antialias: true })
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
    container.appendChild(this.renderer.domElement)

    this.camera = new THREE.PerspectiveCamera(50, 1, 1, 1_000_000)
    this.camera.up.set(0, 0, 1) // 배관 좌표계는 Z-up
    this.camera.position.set(4000, -6000, 4000)

    this.controls = new OrbitControls(this.camera, this.renderer.domElement)
    this.controls.enableDamping = true

    this.scene.add(this.modelRoot)
    this.scene.add(new THREE.AmbientLight(0xffffff, 0.75))
    const dir = new THREE.DirectionalLight(0xffffff, 1.1)
    dir.position.set(1, -1, 1.4)
    this.scene.add(dir)
    // 바닥 그리드·월드 축은 두지 않는다 — 방향은 좌하단 기즈모가 알려 준다
    // 테마가 정해지기 전(첫 프레임)에도 배경색을 갖게 한다
    this.setTheme('light')

    this.renderer.domElement.addEventListener('pointerdown', this.handlePointerDown)
    this.renderer.domElement.addEventListener('pointermove', this.handlePointerMove)
    this.resizeObserver = new ResizeObserver(() => this.resize())
    this.resizeObserver.observe(container)
    this.resize()
    this.animate()
  }

  /** 배경색을 테마에 맞춘다 */
  setTheme(theme: 'light' | 'dark') {
    this.scene.background = new THREE.Color(theme === 'dark' ? 0x0f172a : 0xf1f5f9)
  }

  /** Scene3D 를 지오메트리로 만들어 표시한다. 이전 모델은 해제한다 */
  setScene(scene: Scene3D | null) {
    disposeGroup(this.modelRoot)
    this.pickables = []
    this.originalMaterials.clear()
    this.modelMaterials = []
    if (!scene) return

    const { root, pickables } = buildScene(scene)
    // 만든 그룹의 자식을 modelRoot 로 옮긴다(modelRoot 참조를 유지하기 위해)
    while (root.children.length) this.modelRoot.add(root.children[0])
    this.pickables = pickables

    // 재질은 형상별로 공유되므로 중복을 걸러 모은다
    const seen = new Set<THREE.Material>()
    for (const mesh of pickables) {
      const material = mesh.material as THREE.Material
      if (!seen.has(material)) {
        seen.add(material)
        this.modelMaterials.push(material)
      }
    }
    // 새 모델에도 현재 투명도를 이어서 적용한다
    this.applyOpacity()

    this.fitToBounds(scene.bounds)
  }

  /** 모델 불투명도를 바꾼다 (0~1, 1 이면 불투명) */
  setModelOpacity(opacity: number) {
    this.opacity = THREE.MathUtils.clamp(opacity, 0, 1)
    this.applyOpacity()
  }

  /**
   * 현재 불투명도를 모델·강조 재질에 반영한다.
   * 반투명일 때 깊이 버퍼를 쓰면 뒤쪽 배관이 그려지지 않아 속을 들여다볼 수 없다 — depthWrite 를 끈다.
   */
  private applyOpacity() {
    const transparent = this.opacity < 1
    for (const material of [...this.modelMaterials, this.highlightMaterial]) {
      material.transparent = transparent
      material.opacity = this.opacity
      material.depthWrite = !transparent
      material.needsUpdate = true
    }
  }

  /** 선택된 컴포넌트를 강조한다 */
  setSelection(componentIds: string[]) {
    const selected = new Set(componentIds)
    for (const mesh of this.pickables) {
      const id = mesh.userData.componentId as string
      if (selected.has(id)) {
        if (!this.originalMaterials.has(mesh)) {
          this.originalMaterials.set(mesh, mesh.material as THREE.Material)
        }
        mesh.material = this.highlightMaterial
      } else {
        const original = this.originalMaterials.get(mesh)
        if (original) {
          mesh.material = original
          this.originalMaterials.delete(mesh)
        }
      }
    }
  }

  /** 경계 상자가 화면에 들어오도록 카메라를 옮긴다 */
  fitToBounds(bounds: Scene3D['bounds']) {
    const [minX, minY, minZ, maxX, maxY, maxZ] = bounds
    const centre = new THREE.Vector3((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2)
    const size = Math.max(maxX - minX, maxY - minY, maxZ - minZ, 1000)
    // 시야각을 고려한 거리 + 여유 20%
    const distance = (size / 2 / Math.tan((this.camera.fov * Math.PI) / 360)) * 1.2

    this.controls.target.copy(centre)
    this.camera.position.copy(centre).add(
      new THREE.Vector3(0.6, -0.8, 0.5).normalize().multiplyScalar(distance),
    )
    this.camera.near = Math.max(distance / 1000, 0.1)
    this.camera.far = distance * 100
    this.camera.updateProjectionMatrix()
    this.controls.update()
  }

  /**
   * 카메라를 지정한 축 정면으로 옮긴다. 타깃과 거리는 그대로 두고 방향만 돌린다 —
   * 직선 보간은 180° 뒤집을 때 타깃을 관통하므로 방향 회전을 slerp 한다.
   */
  private alignTo(direction: THREE.Vector3) {
    const from = this.camera.position.clone().sub(this.controls.target).normalize()
    this.snap = {
      from,
      rotation: new THREE.Quaternion().setFromUnitVectors(from, direction),
      t: 0,
    }
  }

  /** 정렬 애니메이션을 한 프레임 진행한다. controls.update() 뒤에 호출해 결과를 덮어쓴다 */
  private stepSnap(delta: number) {
    const snap = this.snap
    if (!snap) return
    snap.t = Math.min(1, snap.t + delta / SNAP_SECONDS)
    // 시작·끝에서 느려지는 ease-in-out
    const e = snap.t < 0.5 ? 2 * snap.t * snap.t : 1 - 2 * (1 - snap.t) ** 2
    const step = new THREE.Quaternion().slerpQuaternions(new THREE.Quaternion(), snap.rotation, e)
    // 거리는 매 프레임 다시 재 휠 확대·축소와 부딪히지 않게 한다
    const distance = this.camera.position.distanceTo(this.controls.target)
    this.camera.position
      .copy(this.controls.target)
      .addScaledVector(snap.from.clone().applyQuaternion(step), distance)
    this.camera.lookAt(this.controls.target)
    if (snap.t >= 1) this.snap = null
  }

  /** 기즈모 위에서는 커서를 손가락으로 바꿔 클릭할 수 있음을 알린다 */
  private handlePointerMove = (e: PointerEvent) => {
    const rect = this.renderer.domElement.getBoundingClientRect()
    const hit = this.gizmo.hitTest(e.clientX - rect.left, e.clientY - rect.top, rect.height)
    this.renderer.domElement.style.cursor = hit ? 'pointer' : ''
  }

  /** 클릭 지점에서 레이캐스트해 컴포넌트를 고른다. 기즈모를 눌렀으면 축 정렬이 먼저다 */
  private handlePointerDown = (e: PointerEvent) => {
    if (e.button !== 0) return
    const rect = this.renderer.domElement.getBoundingClientRect()
    const x = e.clientX - rect.left
    const y = e.clientY - rect.top

    const axis = this.gizmo.hitTest(x, y, rect.height)
    if (axis) {
      this.alignTo(axis)
      return
    }
    // 빈 곳·모델을 누르면 진행 중인 정렬을 멈춘다 — 사용자 조작이 우선이다
    this.snap = null

    if (!this.onSelect) return
    const ndc = new THREE.Vector2((x / rect.width) * 2 - 1, -(y / rect.height) * 2 + 1)
    this.raycaster.setFromCamera(ndc, this.camera)
    const hits = this.raycaster.intersectObjects(this.pickables, false)
    this.onSelect(hits.length ? (hits[0].object.userData.componentId as string) : null)
  }

  /** 컨테이너 크기에 맞춰 카메라/렌더러를 갱신한다 */
  private resize() {
    const { clientWidth: w, clientHeight: h } = this.container
    if (w === 0 || h === 0) return
    this.camera.aspect = w / h
    this.camera.updateProjectionMatrix()
    this.renderer.setSize(w, h, false)
  }

  private animate = () => {
    this.frameId = requestAnimationFrame(this.animate)
    const delta = this.clock.getDelta()
    this.controls.update()
    this.stepSnap(delta)
    this.renderer.render(this.scene, this.camera)
    // 기즈모는 본 씬 위에 덧그린다 — 좌하단 뷰포트만 다시 그린다
    this.gizmo.render(this.renderer, this.camera)
  }

  /** 렌더 루프를 멈추고 GPU 자원을 해제한다 */
  dispose() {
    cancelAnimationFrame(this.frameId)
    this.renderer.domElement.removeEventListener('pointerdown', this.handlePointerDown)
    this.renderer.domElement.removeEventListener('pointermove', this.handlePointerMove)
    this.resizeObserver.disconnect()
    disposeGroup(this.modelRoot)
    this.gizmo.dispose()
    this.highlightMaterial.dispose()
    this.controls.dispose()
    this.renderer.dispose()
    this.renderer.domElement.remove()
  }
}
