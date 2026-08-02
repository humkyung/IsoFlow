// primitives3d.ts — Scene3D 의 컴포넌트를 three.js 지오메트리로 만든다.
// 형상 종류(shape)는 백엔드가 정해서 내려준다 — 여기서 컴포넌트 타입을 다시 해석하지 않는다.
import * as THREE from 'three'
import type { Component3D, Port3D, Scene3D } from '@/types/scene3d'

/** 보어 정보가 없는 컴포넌트에 쓸 기본 반지름(mm) */
const FALLBACK_RADIUS = 25
/** 밸브·플랜지 같은 본체는 배관보다 굵게 그려야 눈에 띈다 */
const BODY_RADIUS_FACTOR = 1.35
/** 원기둥 둘레 분할 수 — 라인 하나에 수백 개가 생기므로 과하게 올리지 않는다 */
const RADIAL_SEGMENTS = 16
/** 엘보 호를 샘플링할 점 개수 */
const ELBOW_SAMPLES = 16
/**
 * 앵글 밸브 허리(CENTRE 쪽)를 END 반지름의 몇 배로 좁힐지.
 * 0 으로 만들면 뾰족해져 면이 사라지므로 가늘게만 만든다
 */
const VALVE_WAIST_FACTOR = 0.2
/**
 * 밸브 조작부 한 토막. 모든 값은 **몸통 반지름 기준 배율**이라
 * 보어가 커지면 조작부도 같이 커진다 — 도면 어디서든 비율이 유지된다.
 *
 * @property from 몸통 중심에서 시작 위치 (스핀들 방향)
 * @property to   끝 위치
 */
interface OperatorPart {
  from: number
  to: number
  radius: number
}

/** 버터플라이 — 스템 · 기어박스 · 핸드휠. 스템이 기어박스 안까지 들어가 이가 빠져 보이지 않는다 */
const BUTTERFLY_OPERATOR: readonly OperatorPart[] = [
  { from: 0, to: 1.9, radius: 0.12 },
  { from: 1.8, to: 2.35, radius: 0.42 },
  { from: 2.35, to: 2.42, radius: 0.62 },
]

/**
 * BB.OS&Y(볼트 보닛 + 외부나사 요크) — 보닛 · 라이징 스템 · 핸드휠. 게이트와 글로브가 함께 쓴다.
 * 이 계열은 몸통이 가늘고 길어(2" 게이트 기준 길이 292mm · 지름 69mm) 버터플라이보다 조작부를
 * 훨씬 높이 뽑아야 실물 비율에 가깝다.
 */
const OSY_OPERATOR: readonly OperatorPart[] = [
  { from: 0, to: 2.2, radius: 0.6 },
  { from: 0, to: 6.0, radius: 0.16 },
  { from: 6.0, to: 6.15, radius: 2.0 },
]

/** 보타이 몸통의 허리 굵기 (END 반지름 대비). 앵글 밸브보다 두껍게 둔다 — 몸통이 길어서 */
const BOWTIE_WAIST_FACTOR = 0.45

/**
 * 글로브 몸통 가운데 구의 반지름 (END 반지름 대비).
 * 허리(0.45)보다 커야 밖으로 드러나고 END(1.0)보다 작아야 몸통을 벗어나지 않는다 —
 * 2D 심볼이 보타이 안에 원을 넣는 것과 같은 규칙이다.
 */
const GLOBE_BULB_FACTOR = 0.75

/** 볼 밸브의 구 — 2D 심볼이 글로브(0.19)보다 큰 원(0.27)을 쓰는 것을 따라 조금 키운다 */
const BALL_BULB_FACTOR = 0.85

/** 볼 밸브 조작부 — 90° 회전이라 요크·핸드휠 없이 짧은 스템만 올라간다 */
const BALL_OPERATOR: readonly OperatorPart[] = [{ from: 0, to: 1.8, radius: 0.14 }]

/**
 * 볼 밸브 레버 — 스템 끝에서 **배관 축 방향으로 누운 막대**.
 * 핸드휠(원반)과 실루엣이 확연히 달라 글로브·게이트와 한눈에 구별된다.
 */
const BALL_LEVER = { at: 1.8, halfLength: 1.6, radius: 0.13 } as const

/**
 * 플러그 밸브의 테이퍼 플러그 — 스핀들 축을 따라 놓인 원뿔대. 몸통 반지름 대비 배율이다.
 * 2D 가 볼(원) 자리에 사각형을 넣어 가르는 것을, 3D 에서는 구 대신 이 원뿔대로 가른다.
 */
const PLUG_BODY = { from: -0.7, to: 1.0, radiusFrom: 0.45, radiusTo: 0.62 } as const

/**
 * 체크 밸브(스윙형) — **배럴 몸통 + 볼트 보닛(캡)**.
 *
 * 2D 심볼(유동 방향 원뿔 + 시트 선)을 그대로 3D 로 밀어내면 깔때기에 목걸이를 끼운 모양이 되어
 * 실물과 딴판이다. 실제 스윙 체크는 둥근 통에 볼트로 캡을 얹은 형태이고 스템·핸드휠이 없다.
 *
 * `capOffset` 만 **밸브 반길이** 대비, 나머지는 **몸통 반지름** 대비 배율이다.
 */
const CHECK = {
  /** 캡 기둥 — 몸통 안에서 시작해 밖으로 나온다 */
  capFrom: 0.4,
  capTo: 1.45,
  capRadius: 0.6,
  /** 볼트로 조인 덮개판 */
  plateTo: 1.62,
  plateRadius: 0.85,
  /** 캡을 상류 쪽으로 민다 — 스윙 체크의 힌지가 입구 쪽에 있고, 유동 방향 단서도 된다 */
  capOffset: 0.3,
} as const

export interface BuildResult {
  /** 컴포넌트별 메시를 담은 그룹 */
  root: THREE.Group
  /** 레이캐스트 대상 메시 목록 */
  pickables: THREE.Mesh[]
}

function v(p: [number, number, number]): THREE.Vector3 {
  return new THREE.Vector3(p[0], p[1], p[2])
}

function portsOf(c: Component3D, kind: Port3D['kind']): Port3D[] {
  return c.ports.filter((p) => p.kind === kind)
}

/** 포트들의 보어에서 반지름(mm)을 정한다. 없으면 기본값 */
function radiusOf(ports: Port3D[], factor = 1): number {
  const bores = ports.map((p) => p.bore).filter((b): b is number => typeof b === 'number' && b > 0)
  const bore = bores.length ? Math.min(...bores) : FALLBACK_RADIUS * 2
  return (bore / 2) * factor
}

/**
 * 두 점을 잇는 원기둥(또는 원뿔대)을 만든다.
 * three.js 의 CylinderGeometry 는 +Y 축을 따라 생기므로 방향을 맞춰 회전시킨다.
 */
function cylinderBetween(
  a: THREE.Vector3,
  b: THREE.Vector3,
  radiusStart: number,
  radiusEnd: number,
  material: THREE.Material,
): THREE.Mesh | null {
  const dir = new THREE.Vector3().subVectors(b, a)
  const len = dir.length()
  if (len < 1e-6) return null

  const geom = new THREE.CylinderGeometry(radiusEnd, radiusStart, len, RADIAL_SEGMENTS, 1, false)
  const mesh = new THREE.Mesh(geom, material)
  mesh.position.copy(a).addScaledVector(dir, 0.5)
  mesh.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), dir.clone().normalize())
  return mesh
}

/**
 * 정확한 원호 커브 — 중심 `centre` 를 기준으로 `start` 를 `axis` 둘레로 `sweep` 만큼 돌린다.
 *
 * **`CatmullRomCurve3` 로 호를 근사하면 안 된다.** 닫히지 않은 스플라인은 끝점을 반사해 보정하므로
 * 첫 점에서의 접선이 참 접선이 아니라 **첫 현(chord) 방향**이 된다 — 호 분할각의 절반만큼 어긋난다.
 * `TubeGeometry` 는 그 접선에 수직으로 끝면을 만들기 때문에 끝면이 그대로 기운다.
 * (실측: 반지름 177.8mm · 90° 를 16 분할하면 접합면에서 ±8.71mm, 즉 2.81° 어긋났다 —
 *  배관과 엘보 사이에 쐐기 모양 틈이 보인다.)
 */
class ArcCurve3 extends THREE.Curve<THREE.Vector3> {
  constructor(
    private readonly centre: THREE.Vector3,
    /** 중심에서 시작점으로 향하는 반지름 벡터 */
    private readonly start: THREE.Vector3,
    private readonly axis: THREE.Vector3,
    private readonly sweep: number,
  ) {
    super()
  }

  override getPoint(t: number, target = new THREE.Vector3()): THREE.Vector3 {
    return target.copy(this.start).applyAxisAngle(this.axis, this.sweep * t).add(this.centre)
  }

  /**
   * 접선을 해석적으로 준다. 기본 구현은 수치 미분이라 끝점에서 한쪽만 보게 되어 오차가 남는다.
   * 원호의 속도 벡터는 `sweep · (axis × r(t))` 이므로 방향은 `axis × r(t)` 다.
   */
  override getTangent(t: number, target = new THREE.Vector3()): THREE.Vector3 {
    const radial = this.start.clone().applyAxisAngle(this.axis, this.sweep * t)
    return target.crossVectors(this.axis, radial).normalize()
  }
}

/**
 * 엘보/밴드의 호를 만든다.
 *
 * **PCF 의 CENTRE-POINT 는 호의 중심이 아니라 두 배관 축이 만나는 모서리점이다.**
 * (코퍼스 확인: CENTRE 는 한쪽 END 와 X·Y 가 같고 다른 END 와 Z 가 같다 = 축 교점)
 * 이걸 호의 중심으로 쓰면 원호가 모서리 바깥으로 부풀어 방향이 반대로 보인다.
 *
 * 실제 호의 중심 O 는 두 접선에 각각 수직인 선들의 교점이다:
 * ```
 *   t1 = normalize(A - C),  t2 = normalize(B - C),  L = |A - C|
 *   half = angle(t1, t2) / 2
 *   O = C + normalize(t1 + t2) · (L / cos(half))
 *   R = L · tan(half)
 * ```
 */
function elbowTube(
  end1: THREE.Vector3,
  end2: THREE.Vector3,
  corner: THREE.Vector3,
  radius: number,
  material: THREE.Material,
): THREE.Mesh | null {
  const t1 = new THREE.Vector3().subVectors(end1, corner)
  const t2 = new THREE.Vector3().subVectors(end2, corner)
  const legLength = t1.length()
  if (legLength < 1e-6 || t2.length() < 1e-6) return null
  t1.normalize()
  t2.normalize()

  const half = t1.angleTo(t2) / 2
  const bisector = new THREE.Vector3().addVectors(t1, t2)
  // 두 다리가 일직선(꺾임 없음)이면 호가 성립하지 않는다
  if (bisector.length() < 1e-9 || half < 1e-6 || Math.abs(Math.cos(half)) < 1e-9) {
    return cylinderBetween(end1, end2, radius, radius, material)
  }
  bisector.normalize()

  const centre = bisector.clone().multiplyScalar(legLength / Math.cos(half)).add(corner)
  const r1 = new THREE.Vector3().subVectors(end1, centre)
  const r2 = new THREE.Vector3().subVectors(end2, centre)

  const axis = new THREE.Vector3().crossVectors(r1, r2)
  if (axis.length() < 1e-9) return cylinderBetween(end1, end2, radius, radius, material)
  axis.normalize()

  const curve = new ArcCurve3(centre, r1, axis, r1.angleTo(r2))
  const geom = new THREE.TubeGeometry(curve, ELBOW_SAMPLES * 2, radius, RADIAL_SEGMENTS, false)
  return new THREE.Mesh(geom, material)
}

/** FLAT-DIRECTION → 평평한 면이 향하는 단위벡터. 배관 좌표계는 Z-up */
function flatVector(dir: Component3D['flatDirection']): THREE.Vector3 | null {
  if (dir === 'UP') return new THREE.Vector3(0, 0, 1)
  if (dir === 'DOWN') return new THREE.Vector3(0, 0, -1)
  return null
}

/**
 * 편심 리듀서를 만든다 — 한쪽 모선이 평평한 원뿔대.
 *
 * **PCF 의 END-POINT 에는 편심량이 이미 반영되어 있다.** 실 코퍼스 확인 결과 작은쪽 중심이
 * 평평한 면 방향으로 `(OD_large − OD_small)/2` 만큼 어긋나 있다. 그래서 두 중심을 그냥 잇는
 * 원기둥/원뿔대를 만들면 **축과 양 끝면이 함께 기울어져**(20"×14" 기준 8.5°) 접속 배관과
 * 쐐기 모양 틈이 생기고, 평평한 면이 없어 "돌려놓은 동심 리듀서"처럼 보인다.
 *
 * 실제 편심 리듀서는 **양 끝면이 런 축에 수직인 평행면**이고 중심만 옆으로 어긋나 있다.
 * 그래서 중심선에서 편심 성분을 빼 런 축을 되찾는다:
 * ```
 *   a = (P_small − P_large) − u · (r_large − r_small)     // u = 평평한 면 방향
 * ```
 * 렌더 반지름(보어 기준)으로 계산하므로 `u` 쪽 모선은 정확히 평평하게 떨어진다
 * (u ⟂ a 일 때 `(P_small + u·r_small) − (P_large + u·r_large) = a`).
 */
function eccentricReducer(
  pLarge: THREE.Vector3,
  pSmall: THREE.Vector3,
  rLarge: number,
  rSmall: number,
  flat: THREE.Vector3,
  material: THREE.Material,
): THREE.Mesh | null {
  const axis = new THREE.Vector3().subVectors(pSmall, pLarge).addScaledVector(flat, -(rLarge - rSmall))
  const len = axis.length()
  if (len < 1e-6) return null
  axis.divideScalar(len)

  // 평평한 면 방향을 축에 수직하게 투영한 것이 링의 기준축(e1)이 된다.
  // 런이 flat 방향과 나란하면(수직 배관) 편심 방향을 정할 수 없다 — 동심으로 낮춘다
  const e1 = flat.clone().addScaledVector(axis, -flat.dot(axis))
  if (e1.lengthSq() < 1e-12) return cylinderBetween(pLarge, pSmall, rLarge, rSmall, material)
  e1.normalize()
  const e2 = new THREE.Vector3().crossVectors(axis, e1).normalize()

  const n = RADIAL_SEGMENTS
  const positions: number[] = []
  // 0..n-1 = 큰쪽 링, n..2n-1 = 작은쪽 링, 2n / 2n+1 = 양 끝 뚜껑 중심
  for (const [centre, radius] of [
    [pLarge, rLarge],
    [pSmall, rSmall],
  ] as const) {
    for (let i = 0; i < n; i++) {
      const th = (i / n) * Math.PI * 2
      const c = Math.cos(th)
      const s = Math.sin(th)
      positions.push(
        centre.x + (e1.x * c + e2.x * s) * radius,
        centre.y + (e1.y * c + e2.y * s) * radius,
        centre.z + (e1.z * c + e2.z * s) * radius,
      )
    }
  }
  positions.push(pLarge.x, pLarge.y, pLarge.z, pSmall.x, pSmall.y, pSmall.z)

  const indices: number[] = []
  for (let i = 0; i < n; i++) {
    const a = i
    const b = (i + 1) % n
    // 옆면 — 바깥을 향하도록 감는다
    indices.push(a, b, n + b, a, n + b, n + a)
    // 뚜껑 (큰쪽은 -axis, 작은쪽은 +axis 를 향한다)
    indices.push(2 * n, b, a)
    indices.push(2 * n + 1, n + a, n + b)
  }

  const geom = new THREE.BufferGeometry()
  geom.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3))
  geom.setIndex(indices)
  geom.computeVertexNormals()
  return new THREE.Mesh(geom, material)
}

/**
 * 볼 밸브 레버를 만든다. 배관 축 방향으로 눕히되 **스핀들에 수직**이어야 하므로
 * 배관 축에서 스핀들 성분을 빼고 쓴다 (스핀들이 배관과 직각이 아닌 경우 대비).
 * 배관 축과 스핀들이 나란하면 방향이 정해지지 않아 레버를 그리지 않는다.
 */
function ballLever(
  hub: THREE.Vector3,
  spindle: THREE.Vector3,
  end0: THREE.Vector3,
  end1: THREE.Vector3,
  bodyRadius: number,
  material: THREE.Material,
): THREE.Mesh | null {
  const along = new THREE.Vector3().subVectors(end1, end0)
  along.addScaledVector(spindle, -along.dot(spindle))
  if (along.lengthSq() < 1e-12) return null
  along.normalize()

  const at = hub.clone().addScaledVector(spindle, bodyRadius * BALL_LEVER.at)
  const half = bodyRadius * BALL_LEVER.halfLength
  const r = bodyRadius * BALL_LEVER.radius
  return cylinderBetween(
    at.clone().addScaledVector(along, -half),
    at.clone().addScaledVector(along, half),
    r, r, material,
  )
}

/**
 * 축에 수직인 "위쪽" 방향. 보닛·캡은 실제로도 위를 향해 달린다.
 * 배관이 수직이면 +Z 성분이 사라지므로 +X 로 넘어간다.
 */
function perpendicularUp(axis: THREE.Vector3): THREE.Vector3 {
  const up = new THREE.Vector3(0, 0, 1)
  up.addScaledVector(axis, -up.dot(axis))
  if (up.lengthSq() < 1e-9) {
    up.set(1, 0, 0).addScaledVector(axis, -axis.x)
  }
  return up.normalize()
}

/** 중심 `at` 에 놓인 구 — 글로브·볼 밸브의 몸통 불룩한 부분 */
function sphereAt(at: THREE.Vector3, radius: number, material: THREE.Material): THREE.Mesh {
  const geom = new THREE.SphereGeometry(radius, RADIAL_SEGMENTS, Math.round(RADIAL_SEGMENTS / 2))
  const mesh = new THREE.Mesh(geom, material)
  mesh.position.copy(at)
  return mesh
}

/** SPINDLE-DIRECTION → 스템이 향하는 단위벡터. 플랜트 좌표계는 X=동 / Y=북 / Z=위 */
function spindleVector(dir: Component3D['spindleDirection']): THREE.Vector3 | null {
  switch (dir) {
    case 'EAST':
      return new THREE.Vector3(1, 0, 0)
    case 'WEST':
      return new THREE.Vector3(-1, 0, 0)
    case 'NORTH':
      return new THREE.Vector3(0, 1, 0)
    case 'SOUTH':
      return new THREE.Vector3(0, -1, 0)
    case 'UP':
      return new THREE.Vector3(0, 0, 1)
    case 'DOWN':
      return new THREE.Vector3(0, 0, -1)
    default:
      return null
  }
}

/**
 * 밸브 조작부를 스핀들 방향으로 쌓는다 — 보닛·스템·기어박스·핸드휠은 모두 축이 같은 원기둥이다.
 *
 * 몸통만 그리면 밸브인지 플랜지인지 구분되지 않고 조작 방향도 알 수 없다.
 * 방향은 PCF 의 `SPINDLE-DIRECTION` 이 알려 준다 — 없으면 호출부가 몸통만 그린다.
 */
function valveOperator(
  hub: THREE.Vector3,
  spindle: THREE.Vector3,
  bodyRadius: number,
  parts: readonly OperatorPart[],
  material: THREE.Material,
): (THREE.Mesh | null)[] {
  const at = (factor: number) => hub.clone().addScaledVector(spindle, bodyRadius * factor)
  return parts.map((p) =>
    cylinderBetween(at(p.from), at(p.to), bodyRadius * p.radius, bodyRadius * p.radius, material),
  )
}

/** 컴포넌트 하나가 만드는 메시들 */
function meshesFor(c: Component3D, material: THREE.Material): THREE.Mesh[] {
  const ends = portsOf(c, 'END')
  // CENTRE-POINT 의 의미는 컴포넌트마다 다르다:
  //  · ELBOW/BEND → 두 축이 만나는 **모서리점** (호의 중심이 아니다)
  //  · TEE/CROSS  → 런 위의 분기 교차점
  //  · OLET       → 모재 배관 접속점
  const centre = portsOf(c, 'CENTRE')[0]
  const b1 = portsOf(c, 'BRANCH1')[0]
  const b2 = portsOf(c, 'BRANCH2')[0]
  const out: (THREE.Mesh | null)[] = []

  switch (c.shape) {
    case 'PIPE': {
      if (ends.length < 2) break
      const r = radiusOf(ends)
      out.push(cylinderBetween(v(ends[0].p), v(ends[1].p), r, r, material))
      break
    }
    case 'ELBOW': {
      if (ends.length < 2) break
      const r = radiusOf(ends)
      // CENTRE 가 없으면 호를 만들 수 없다 — 직선으로 낮춘다.
      // (엔진이 보통 PIPE 로 낮춰 보내지만, 뷰어가 한 컴포넌트 때문에 통째로 죽으면 안 된다)
      out.push(
        centre
          ? elbowTube(v(ends[0].p), v(ends[1].p), v(centre.p), r, material)
          : cylinderBetween(v(ends[0].p), v(ends[1].p), r, r, material),
      )
      break
    }
    case 'TEE':
    case 'CROSS': {
      if (ends.length < 2) break
      const rRun = radiusOf(ends)
      out.push(cylinderBetween(v(ends[0].p), v(ends[1].p), rRun, rRun, material))
      // 분기는 런 중심에서 뻗는다. CENTRE 가 없으면 런 중점을 쓴다
      const hub = centre ? v(centre.p) : v(ends[0].p).lerp(v(ends[1].p), 0.5)
      if (b1) {
        const rb = radiusOf([b1])
        out.push(cylinderBetween(hub, v(b1.p), rb, rb, material))
      }
      if (b2) {
        const rb = radiusOf([b2])
        out.push(cylinderBetween(hub, v(b2.p), rb, rb, material))
      }
      break
    }
    case 'OLET': {
      if (!centre || !b1) break
      const rb = radiusOf([b1])
      out.push(cylinderBetween(v(centre.p), v(b1.p), rb, rb, material))
      break
    }
    case 'REDUCER': {
      if (ends.length < 2) break
      // 양 끝 보어가 다르다 — 원뿔대로 그린다
      const r0 = radiusOf([ends[0]])
      const r1 = radiusOf([ends[1]])
      // FLAT-DIRECTION 이 오면 편심 — 한쪽 모선이 평평하고 끝면이 런 축에 수직이다
      const flat = flatVector(c.flatDirection)
      if (flat) {
        const large = r0 >= r1 ? 0 : 1
        out.push(
          eccentricReducer(
            v(ends[large].p),
            v(ends[1 - large].p),
            Math.max(r0, r1),
            Math.min(r0, r1),
            flat,
            material,
          ),
        )
      } else {
        out.push(cylinderBetween(v(ends[0].p), v(ends[1].p), r0, r1, material))
      }
      break
    }
    case 'VALVE_CHECK': {
      if (ends.length < 2) break
      const r = radiusOf(ends, BODY_RADIUS_FACTOR)
      // 배럴 몸통 — 스윙 체크는 둥근 통이다
      out.push(cylinderBetween(v(ends[0].p), v(ends[1].p), r, r, material))

      const hub = centre ? v(centre.p) : v(ends[0].p).lerp(v(ends[1].p), 0.5)
      const axis = new THREE.Vector3().subVectors(v(ends[1].p), v(ends[0].p))
      const half = axis.length() / 2
      if (half < 1e-6) break
      axis.normalize()

      // 유동을 알면 캡을 상류 쪽으로 민다. 모르면 가운데 둔다 — 방향을 지어내지 않는다
      const outlet = ends.find((e) => e.ordinal === c.flowToEnd)
      const base = hub.clone()
      if (outlet) {
        const flow = new THREE.Vector3().subVectors(v(outlet.p), hub).normalize()
        base.addScaledVector(flow, -half * CHECK.capOffset)
      }

      const up = perpendicularUp(axis)
      const at = (factor: number) => base.clone().addScaledVector(up, r * factor)
      out.push(
        cylinderBetween(at(CHECK.capFrom), at(CHECK.capTo), r * CHECK.capRadius, r * CHECK.capRadius, material),
      )
      const plate = r * CHECK.plateRadius
      out.push(cylinderBetween(at(CHECK.capTo), at(CHECK.plateTo), plate, plate, material))
      break
    }
    case 'BODY': {
      if (ends.length < 2) break
      const r = radiusOf(ends, BODY_RADIUS_FACTOR)
      out.push(cylinderBetween(v(ends[0].p), v(ends[1].p), r, r, material))
      break
    }
    case 'VALVE_BUTTERFLY': {
      if (ends.length < 2) break
      const r = radiusOf(ends, BODY_RADIUS_FACTOR)
      // 웨이퍼 몸통 — 두 END 사이의 납작한 원기둥
      out.push(cylinderBetween(v(ends[0].p), v(ends[1].p), r, r, material))
      const spindle = spindleVector(c.spindleDirection)
      if (spindle) {
        // 조작부는 밸브 중심에서 뻗는다. CENTRE 가 없으면 두 END 의 중점을 쓴다
        const hub = centre ? v(centre.p) : v(ends[0].p).lerp(v(ends[1].p), 0.5)
        out.push(...valveOperator(hub, spindle, r, BUTTERFLY_OPERATOR, material))
      }
      break
    }
    // 보타이 몸통을 공유하는 세 밸브. 2D 심볼도 같은 규칙이다 —
    // 게이트=보타이, 글로브=+채운 원, 볼=+빈 원. 3D 에는 채움/윤곽 구분이 없어
    // 글로브와 볼은 **조작부**(핸드휠 vs 레버)로 가른다.
    case 'VALVE_GATE':
    case 'VALVE_GLOBE':
    case 'VALVE_BALL':
    case 'VALVE_PLUG': {
      if (ends.length < 2) break
      // 세 밸브 모두 CENTRE 가 두 END 의 중점이다(앵글과 달리 꺾이지 않는다) — 없으면 중점을 쓴다
      const hub = centre ? v(centre.p) : v(ends[0].p).lerp(v(ends[1].p), 0.5)
      const r0 = radiusOf([ends[0]], BODY_RADIUS_FACTOR)
      const r1 = radiusOf([ends[1]], BODY_RADIUS_FACTOR)
      const rMax = Math.max(r0, r1)
      const waist = Math.min(r0, r1) * BOWTIE_WAIST_FACTOR
      out.push(cylinderBetween(v(ends[0].p), hub, r0, waist, material))
      out.push(cylinderBetween(v(ends[1].p), hub, r1, waist, material))

      const bulb =
        c.shape === 'VALVE_GLOBE' ? GLOBE_BULB_FACTOR : c.shape === 'VALVE_BALL' ? BALL_BULB_FACTOR : 0
      if (bulb > 0) out.push(sphereAt(hub, rMax * bulb, material))

      const spindle = spindleVector(c.spindleDirection)
      if (!spindle) break
      // 볼·플러그는 90° 회전이라 요크·핸드휠 대신 레버가 달린다
      if (c.shape === 'VALVE_BALL' || c.shape === 'VALVE_PLUG') {
        if (c.shape === 'VALVE_PLUG') {
          // 테이퍼 플러그 — 스핀들 축을 따라 몸통을 관통한다
          const at = (f: number) => hub.clone().addScaledVector(spindle, rMax * f)
          out.push(
            cylinderBetween(
              at(PLUG_BODY.from), at(PLUG_BODY.to),
              rMax * PLUG_BODY.radiusFrom, rMax * PLUG_BODY.radiusTo, material,
            ),
          )
        }
        out.push(...valveOperator(hub, spindle, rMax, BALL_OPERATOR, material))
        out.push(ballLever(hub, spindle, v(ends[0].p), v(ends[1].p), rMax, material))
      } else {
        out.push(...valveOperator(hub, spindle, rMax, OSY_OPERATOR, material))
      }
      break
    }
    case 'VALVE_ANGLE': {
      if (ends.length < 2) break
      // 앵글 밸브의 CENTRE 는 두 배관 축이 만나는 **모서리점**이다.
      // 두 END 를 직선으로 이으면 모서리를 가로질러 경로를 벗어난다 — 모서리를 거쳐 간다.
      if (!centre) {
        const r = radiusOf(ends, BODY_RADIUS_FACTOR)
        out.push(cylinderBetween(v(ends[0].p), v(ends[1].p), r, r, material))
        break
      }
      const corner = v(centre.p)
      // 다리마다 자기 END 의 보어를 쓴다 — 1"×0.75" 처럼 양쪽 구경이 다른 밸브가 있다
      const r0 = radiusOf([ends[0]], BODY_RADIUS_FACTOR)
      const r1 = radiusOf([ends[1]], BODY_RADIUS_FACTOR)
      // 허리는 양쪽이 같아야 모서리에서 두 원뿔이 이어져 보인다
      const waist = Math.min(r0, r1) * VALVE_WAIST_FACTOR
      out.push(cylinderBetween(v(ends[0].p), corner, r0, waist, material))
      out.push(cylinderBetween(v(ends[1].p), corner, r1, waist, material))
      break
    }
    case 'NONE':
    default:
      break
  }
  return out.filter((m): m is THREE.Mesh => m !== null)
}

/** 컴포넌트 종류별 색. 배관은 차분하게, 부품은 눈에 띄게 */
function colorFor(shape: Component3D['shape']): number {
  switch (shape) {
    case 'PIPE':
      return 0x8fa3b8
    case 'ELBOW':
      return 0x7d93aa
    case 'TEE':
    case 'CROSS':
    case 'OLET':
      return 0x5b9bd5
    case 'REDUCER':
      return 0x6aa84f
    case 'BODY':
    case 'VALVE_ANGLE':
    case 'VALVE_BUTTERFLY':
    case 'VALVE_GATE':
    case 'VALVE_GLOBE':
    case 'VALVE_BALL':
    case 'VALVE_CHECK':
    case 'VALVE_PLUG':
      return 0xe07b39
    default:
      return 0x999999
  }
}

/**
 * Scene3D 전체를 three.js 그룹으로 만든다.
 * 각 메시의 `userData.componentId` 로 선택 시 원본 컴포넌트를 되찾는다.
 */
export function buildScene(scene: Scene3D): BuildResult {
  const root = new THREE.Group()
  const pickables: THREE.Mesh[] = []
  // 형상별로 재질을 공유해 드로우콜과 GPU 메모리를 줄인다
  const materials = new Map<number, THREE.Material>()

  for (const c of scene.components) {
    if (c.shape === 'NONE') continue
    const color = colorFor(c.shape)
    let mat = materials.get(color)
    if (!mat) {
      mat = new THREE.MeshStandardMaterial({ color, roughness: 0.55, metalness: 0.25 })
      materials.set(color, mat)
    }
    for (const mesh of meshesFor(c, mat)) {
      mesh.userData.componentId = c.id
      root.add(mesh)
      pickables.push(mesh)
    }
  }
  return { root, pickables }
}

/** buildScene 이 만든 그룹의 지오메트리/재질을 해제한다 */
export function disposeGroup(group: THREE.Group) {
  const seen = new Set<THREE.Material>()
  group.traverse((o) => {
    if (o instanceof THREE.Mesh) {
      o.geometry.dispose()
      const m = o.material as THREE.Material
      if (!seen.has(m)) {
        seen.add(m)
        m.dispose()
      }
    }
  })
  group.clear()
}
