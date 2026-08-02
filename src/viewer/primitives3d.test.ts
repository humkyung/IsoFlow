// primitives3d.test.ts — 3D 형상 생성, 특히 엘보 호의 기하를 검증한다
import * as THREE from 'three'
import { describe, expect, it } from 'vitest'
import { buildScene, disposeGroup } from './primitives3d'
import type { Component3D, Port3D, Scene3D } from '@/types/scene3d'

function port(
  kind: Port3D['kind'],
  p: [number, number, number],
  bore?: number,
  ordinal = 0,
): Port3D {
  return { kind, ordinal, p, bore }
}

function scene(...components: Component3D[]): Scene3D {
  return {
    schemaVersion: '1.0.0',
    id: 'test',
    units: 'mm',
    origin: [0, 0, 0],
    bounds: [-1000, -1000, -1000, 1000, 1000, 1000],
    pipeline: {},
    components,
  }
}

function component(
  shape: Component3D['shape'],
  ports: Port3D[],
  id = 'c0',
  flatDirection?: Component3D['flatDirection'],
  spindleDirection?: Component3D['spindleDirection'],
  flowToEnd?: number,
): Component3D {
  return { id, type: shape, rawKeyword: shape, shape, ports, flatDirection, spindleDirection, flowToEnd }
}

/**
 * 메시들의 정점을 **월드 좌표로** 모은다.
 * `CylinderGeometry` 는 원점 기준 로컬 좌표라 메시 변환을 적용하지 않으면 엉뚱한 값을 본다
 * (`TubeGeometry`·직접 만든 BufferGeometry 는 이미 월드 좌표라 변환이 항등이다).
 */
function vertices(meshes: THREE.Mesh[]): THREE.Vector3[] {
  const out: THREE.Vector3[] = []
  for (const m of meshes) {
    m.updateMatrixWorld(true)
    const pos = m.geometry.attributes.position
    for (let i = 0; i < pos.count; i++) {
      out.push(new THREE.Vector3(pos.getX(i), pos.getY(i), pos.getZ(i)).applyMatrix4(m.matrixWorld))
    }
  }
  return out
}

/** 메시들의 정점 평균 — 형상의 대략적인 무게중심 */
function centroid(meshes: THREE.Mesh[]): THREE.Vector3 {
  const vs = vertices(meshes)
  const sum = new THREE.Vector3()
  for (const v of vs) sum.add(v)
  return vs.length === 0 ? sum : sum.divideScalar(vs.length)
}

describe('buildScene — 형상별 메시 생성', () => {
  it('shape 마다 정해진 개수의 메시를 만든다', () => {
    const s = scene(
      component('PIPE', [port('END', [0, 0, 0], 100), port('END', [1000, 0, 0], 100)], 'p'),
      component(
        'TEE',
        [
          port('END', [0, 0, 0], 100),
          port('END', [1000, 0, 0], 100),
          port('CENTRE', [500, 0, 0]),
          port('BRANCH1', [500, 0, 500], 50),
        ],
        't',
      ),
      component('OLET', [port('CENTRE', [0, 0, 0], 100), port('BRANCH1', [0, 0, 300], 50)], 'o'),
      component('REDUCER', [port('END', [0, 0, 0], 100), port('END', [400, 0, 0], 50)], 'r'),
      component('NONE', [port('END', [0, 0, 0], 100)], 'n'),
    )

    const { pickables } = buildScene(s)
    const byComponent = new Map<string, number>()
    for (const m of pickables) {
      const id = m.userData.componentId as string
      byComponent.set(id, (byComponent.get(id) ?? 0) + 1)
    }

    expect(byComponent.get('p')).toBe(1)
    expect(byComponent.get('t')).toBe(2) // 런 + 분기
    expect(byComponent.get('o')).toBe(1)
    expect(byComponent.get('r')).toBe(1)
    expect(byComponent.has('n')).toBe(false) // NONE 은 그리지 않는다
  })

  it('모든 메시에 componentId 가 붙어 선택이 가능하다', () => {
    const s = scene(component('PIPE', [port('END', [0, 0, 0], 100), port('END', [1000, 0, 0], 100)], 'x'))
    const { pickables } = buildScene(s)

    expect(pickables).not.toHaveLength(0)
    expect(pickables.every((m) => m.userData.componentId === 'x')).toBe(true)
  })

  it('리듀서는 양 끝 반지름이 다른 원뿔대로 만들어진다', () => {
    const s = scene(component('REDUCER', [port('END', [0, 0, 0], 200), port('END', [400, 0, 0], 100)]))
    const { pickables } = buildScene(s)
    const geom = pickables[0].geometry as THREE.CylinderGeometry

    expect(geom.parameters.radiusTop).not.toBeCloseTo(geom.parameters.radiusBottom)
    expect(Math.max(geom.parameters.radiusTop, geom.parameters.radiusBottom)).toBeCloseTo(100)
    expect(Math.min(geom.parameters.radiusTop, geom.parameters.radiusBottom)).toBeCloseTo(50)
  })

  it('길이 0 인 컴포넌트는 메시를 만들지 않는다', () => {
    const s = scene(component('PIPE', [port('END', [0, 0, 0], 100), port('END', [0, 0, 0], 100)]))
    expect(buildScene(s).pickables).toHaveLength(0)
  })

  it('disposeGroup 은 그룹을 비운다', () => {
    const s = scene(component('PIPE', [port('END', [0, 0, 0], 100), port('END', [1000, 0, 0], 100)]))
    const { root } = buildScene(s)
    expect(root.children.length).toBeGreaterThan(0)

    disposeGroup(root)
    expect(root.children).toHaveLength(0)
  })
})

describe('게이트 밸브 — 보타이 몸통 + 조작부', () => {
  /**
   * 실 코퍼스(P1101-064)의 2" 게이트 밸브를 밸브 중심 기준으로 옮긴 값.
   * 배관 축은 남북(Y), 스템은 위(Z) — SPINDLE-DIRECTION UP.
   */
  const E0: [number, number, number] = [0, 146, 0]
  const E1: [number, number, number] = [0, -146, 0]
  const HUB: [number, number, number] = [0, 0, 0]
  const BORE = 50.8
  const R_BODY = (BORE / 2) * 1.35

  function valve(spindle?: Component3D['spindleDirection']) {
    const ports = [port('END', E0, BORE), port('END', E1, BORE), port('CENTRE', HUB)]
    return buildScene(scene(component('VALVE_GATE', ports, 'g', undefined, spindle))).pickables
  }

  it('보타이 2개 + 조작부 3개로 만들어진다', () => {
    expect(valve('UP')).toHaveLength(5)
  })

  it('몸통이 END 에서 굵고 CENTRE 로 좁아진다', () => {
    // 앞 2개가 몸통(END→CENTRE 원뿔대)이다
    for (const m of valve('UP').slice(0, 2)) {
      const g = m.geometry as THREE.CylinderGeometry
      const wide = Math.max(g.parameters.radiusTop, g.parameters.radiusBottom)
      const narrow = Math.min(g.parameters.radiusTop, g.parameters.radiusBottom)

      expect(wide).toBeCloseTo(R_BODY, 3)
      expect(narrow).toBeCloseTo(R_BODY * 0.45, 3)
    }
  })

  it('스템이 SPINDLE-DIRECTION 으로 뻗는다 — UP 은 +Z', () => {
    const vs = vertices(valve('UP'))

    // 핸드휠은 몸통 반지름의 6배쯤 위로 올라간다
    expect(Math.max(...vs.map((p) => p.z))).toBeGreaterThan(R_BODY * 5.5)
    // 아래로는 몸통 반지름을 넘지 않아야 한다
    expect(Math.min(...vs.map((p) => p.z))).toBeGreaterThan(-R_BODY - 1)
  })

  it('SPINDLE-DIRECTION 이 없어도 보타이 몸통은 그린다 — BODY 와 다르다', () => {
    const meshes = valve()

    expect(meshes).toHaveLength(2)
    const g = meshes[0].geometry as THREE.CylinderGeometry
    expect(g.parameters.radiusTop).not.toBeCloseTo(g.parameters.radiusBottom)
  })
})

describe('글로브 밸브 — 게이트 보타이 + 가운데 구', () => {
  /** 실 코퍼스(P1101-054)의 3" 글로브 밸브. 배관 축은 수직(Z), 스템은 서쪽(-X) */
  const E0: [number, number, number] = [0, 0, 178]
  const E1: [number, number, number] = [0, 0, -178]
  const HUB: [number, number, number] = [0, 0, 0]
  const BORE = 76.2
  const R_BODY = (BORE / 2) * 1.35

  function valve(shape: Component3D['shape'], spindle?: Component3D['spindleDirection']) {
    const ports = [port('END', E0, BORE), port('END', E1, BORE), port('CENTRE', HUB)]
    return buildScene(scene(component(shape, ports, 'gv', undefined, spindle))).pickables
  }

  it('보타이 2개 + 구 1개 + 조작부 3개로 만들어진다', () => {
    expect(valve('VALVE_GLOBE', 'WEST')).toHaveLength(6)
  })

  it('가운데 구가 있다 — 게이트와 구별되는 유일한 표시다', () => {
    const globe = valve('VALVE_GLOBE', 'WEST')
    const gate = valve('VALVE_GATE', 'WEST')

    expect(globe.filter((m) => m.geometry.type === 'SphereGeometry')).toHaveLength(1)
    expect(gate.filter((m) => m.geometry.type === 'SphereGeometry')).toHaveLength(0)
    // 구 말고는 게이트와 같은 구성이어야 한다
    expect(globe).toHaveLength(gate.length + 1)
  })

  it('구는 허리보다 크고 END 보다 작다 — 몸통 밖으로 튀어나오지 않는다', () => {
    const sphere = valve('VALVE_GLOBE', 'WEST').find((m) => m.geometry.type === 'SphereGeometry')!
    const r = (sphere.geometry as THREE.SphereGeometry).parameters.radius

    expect(r).toBeGreaterThan(R_BODY * 0.45) // 허리
    expect(r).toBeLessThan(R_BODY) // END
  })

  it('구가 밸브 중심에 놓인다', () => {
    const sphere = valve('VALVE_GLOBE', 'WEST').find((m) => m.geometry.type === 'SphereGeometry')!

    expect(sphere.position.distanceTo(new THREE.Vector3(...HUB))).toBeCloseTo(0, 6)
  })

  it('스템이 서쪽(-X)으로 뻗는다', () => {
    const vs = vertices(valve('VALVE_GLOBE', 'WEST'))

    expect(Math.min(...vs.map((p) => p.x))).toBeLessThan(-R_BODY * 5.5)
    expect(Math.max(...vs.map((p) => p.x))).toBeLessThanOrEqual(R_BODY + 1)
  })

  it('SPINDLE-DIRECTION 이 없어도 보타이와 구는 그린다', () => {
    expect(valve('VALVE_GLOBE')).toHaveLength(3)
  })
})

describe('체크 밸브 — 배럴 몸통 + 볼트 캡 (스윙형)', () => {
  /**
   * 실 코퍼스(P1102-205)의 1" 스윙 체크(SKEY CK**, BC.SWING)를 밸브 중심 기준으로 옮긴 값.
   * 배관은 X 축을 따라가고 END0 이 +X(ordinal 0), END1 이 -X(ordinal 1) 다.
   */
  const E0: [number, number, number] = [35.5, 0, 0]
  const E1: [number, number, number] = [-35.5, 0, 0]
  const HUB: [number, number, number] = [0, 0, 0]
  const BORE = 25.4
  const R_BODY = (BORE / 2) * 1.35

  function valve(flowToEnd?: number) {
    const ports = [port('END', E0, BORE, 0), port('END', E1, BORE, 1), port('CENTRE', HUB)]
    return buildScene(scene(component('VALVE_CHECK', ports, 'cv', undefined, undefined, flowToEnd)))
      .pickables
  }

  it('배럴 몸통 + 캡 기둥 + 덮개판 3개로 만들어진다', () => {
    expect(valve(0)).toHaveLength(3)
  })

  it('몸통은 배관보다 굵은 배럴이다 — 스템·핸드휠은 없다', () => {
    const barrel = valve(0)[0].geometry as THREE.CylinderGeometry

    expect(barrel.parameters.radiusTop).toBeCloseTo(R_BODY, 3)
    expect(barrel.parameters.radiusTop).toBeCloseTo(barrel.parameters.radiusBottom, 6)
    // 조작부가 없으므로 위로 뻗는 것은 캡뿐이다
    expect(Math.max(...vertices(valve(0)).map((p) => p.z))).toBeLessThan(R_BODY * 1.7)
  })

  it('캡이 위(+Z)를 향한다 — 보닛은 실제로도 위에 달린다', () => {
    const vs = vertices(valve(0).slice(1)) // 캡 + 덮개판

    expect(Math.min(...vs.map((p) => p.z))).toBeGreaterThan(0)
    expect(Math.max(...vs.map((p) => p.z))).toBeCloseTo(R_BODY * 1.62, 3)
  })

  it('유동을 알면 캡이 상류 쪽으로 밀린다', () => {
    // flowToEnd=0 이면 +X 로 흐르므로 캡은 -X(상류) 쪽에 있어야 한다
    const capX = (flowToEnd?: number) => {
      const vs = vertices(valve(flowToEnd).slice(1))
      return vs.reduce((s, p) => s + p.x, 0) / vs.length
    }

    expect(capX(0)).toBeLessThan(0)
    expect(capX(1)).toBeGreaterThan(0)
    expect(capX(0)).toBeCloseTo(-capX(1), 6)
  })

  it('유동을 모르면 캡을 가운데 둔다 — 방향을 지어내지 않는다', () => {
    const meshes = valve()
    const vs = vertices(meshes.slice(1))

    expect(meshes).toHaveLength(3) // 형상 자체는 유동을 몰라도 성립한다
    expect(vs.reduce((s, p) => s + p.x, 0) / vs.length).toBeCloseTo(0, 6)
  })
})

describe('플러그 밸브 — 보타이 + 테이퍼 플러그 + 레버', () => {
  /** 실 코퍼스(P1102-205)의 1" 플러그(SKEY VP**, SPINDLE NORTH). 배관은 X, 스핀들은 +Y */
  const E0: [number, number, number] = [-82.5, 0, 0]
  const E1: [number, number, number] = [82.5, 0, 0]
  const HUB: [number, number, number] = [0, 0, 0]
  const BORE = 25.4
  const R_BODY = (BORE / 2) * 1.35

  function valve(shape: Component3D['shape'], spindle?: Component3D['spindleDirection']) {
    const ports = [port('END', E0, BORE, 0), port('END', E1, BORE, 1), port('CENTRE', HUB)]
    return buildScene(scene(component(shape, ports, 'pv', undefined, spindle))).pickables
  }

  it('보타이 2개 + 플러그 1개 + 스템 1개 + 레버 1개로 만들어진다', () => {
    expect(valve('VALVE_PLUG', 'NORTH')).toHaveLength(5)
  })

  it('가운데가 구가 아니라 테이퍼 원뿔대다 — 볼과 구별되는 지점', () => {
    const plug = valve('VALVE_PLUG', 'NORTH')[2]
    const ball = valve('VALVE_BALL', 'NORTH')[2]

    expect(ball.geometry.type).toBe('SphereGeometry')
    expect(plug.geometry.type).toBe('CylinderGeometry')
    const g = plug.geometry as THREE.CylinderGeometry
    expect(g.parameters.radiusTop).not.toBeCloseTo(g.parameters.radiusBottom) // 테이퍼
  })

  it('플러그가 스핀들 축(+Y)을 따라 몸통을 관통한다', () => {
    const vs = vertices([valve('VALVE_PLUG', 'NORTH')[2]])

    expect(Math.min(...vs.map((p) => p.y))).toBeCloseTo(-R_BODY * 0.7, 3)
    expect(Math.max(...vs.map((p) => p.y))).toBeCloseTo(R_BODY * 1.0, 3)
  })

  it('볼과 같은 레버가 달린다 — 둘 다 90° 회전이다', () => {
    const lever = valve('VALVE_PLUG', 'NORTH').at(-1)!
    lever.updateMatrixWorld(true)
    const axis = new THREE.Vector3(0, 1, 0).applyQuaternion(lever.quaternion).normalize()

    expect(Math.abs(axis.dot(new THREE.Vector3(0, 1, 0)))).toBeLessThan(1e-6) // 스핀들과 수직
    expect(Math.abs(axis.dot(new THREE.Vector3(1, 0, 0)))).toBeCloseTo(1, 6) // 배관 축과 나란
  })

  it('SPINDLE-DIRECTION 이 없으면 보타이만 그린다', () => {
    expect(valve('VALVE_PLUG')).toHaveLength(2)
  })
})

describe('볼 밸브 — 보타이 + 구 + 레버', () => {
  /** 배관 축은 수직(Z), 스핀들은 서쪽(-X). 3" 글로브와 같은 조건으로 둔다 */
  const E0: [number, number, number] = [0, 0, 178]
  const E1: [number, number, number] = [0, 0, -178]
  const HUB: [number, number, number] = [0, 0, 0]
  const BORE = 76.2
  const R_BODY = (BORE / 2) * 1.35

  function valve(shape: Component3D['shape'], spindle?: Component3D['spindleDirection']) {
    const ports = [port('END', E0, BORE), port('END', E1, BORE), port('CENTRE', HUB)]
    return buildScene(scene(component(shape, ports, 'bv', undefined, spindle))).pickables
  }

  it('보타이 2개 + 구 1개 + 스템 1개 + 레버 1개로 만들어진다', () => {
    expect(valve('VALVE_BALL', 'WEST')).toHaveLength(5)
  })

  it('핸드휠 대신 레버다 — 글로브와 조작부로 구별된다', () => {
    const ball = valve('VALVE_BALL', 'WEST')
    const globe = valve('VALVE_GLOBE', 'WEST')

    // 글로브는 요크(보닛·스템·핸드휠) 3개, 볼은 스템 + 레버 2개
    expect(globe).toHaveLength(6)
    expect(ball).toHaveLength(5)
    // 볼의 조작부는 배관 축(Z)보다 넓게 퍼지지 않고, 스핀들 방향으로도 훨씬 낮다
    const ballX = Math.min(...vertices(ball).map((p) => p.x))
    const globeX = Math.min(...vertices(globe).map((p) => p.x))
    expect(ballX).toBeGreaterThan(globeX)
  })

  it('레버가 스핀들에 수직이고 배관 축과 나란하다', () => {
    const lever = valve('VALVE_BALL', 'WEST').at(-1)!
    lever.updateMatrixWorld(true)
    // 원기둥의 로컬 +Y 가 축 방향이다
    const axis = new THREE.Vector3(0, 1, 0).applyQuaternion(lever.quaternion).normalize()

    expect(Math.abs(axis.dot(new THREE.Vector3(-1, 0, 0)))).toBeLessThan(1e-6) // 스핀들과 수직
    expect(Math.abs(axis.dot(new THREE.Vector3(0, 0, 1)))).toBeCloseTo(1, 6) // 배관 축과 나란
  })

  it('구가 글로브보다 크다 — 2D 심볼의 원 반지름 차이를 따른다', () => {
    const radiusOfSphere = (shape: Component3D['shape']) => {
      const s = valve(shape, 'WEST').find((m) => m.geometry.type === 'SphereGeometry')!
      return (s.geometry as THREE.SphereGeometry).parameters.radius
    }

    expect(radiusOfSphere('VALVE_BALL')).toBeGreaterThan(radiusOfSphere('VALVE_GLOBE'))
    expect(radiusOfSphere('VALVE_BALL')).toBeLessThan(R_BODY)
  })

  it('SPINDLE-DIRECTION 이 없으면 보타이와 구만 그린다', () => {
    expect(valve('VALVE_BALL')).toHaveLength(3)
  })
})

describe('버터플라이 밸브 — 웨이퍼 몸통 + 조작부', () => {
  /** 실 코퍼스(P1201-001)의 14" 웨이퍼 버터플라이를 밸브 중심 기준으로 옮긴 값 */
  const E0: [number, number, number] = [0, 0, 39] // z 586.5
  const E1: [number, number, number] = [0, 0, -39] // z 508.5
  const HUB: [number, number, number] = [0, 0, 0]
  const BORE = 355.6
  /** BODY_RADIUS_FACTOR 1.35 를 적용한 몸통 반지름 */
  const R_BODY = (BORE / 2) * 1.35

  function valve(spindle?: Component3D['spindleDirection']) {
    const ports = [port('END', E0, BORE), port('END', E1, BORE), port('CENTRE', HUB)]
    return buildScene(scene(component('VALVE_BUTTERFLY', ports, 'v', undefined, spindle))).pickables
  }

  it('몸통·스템·기어박스·핸드휠 4개로 만들어진다', () => {
    expect(valve('NORTH')).toHaveLength(4)
  })

  it('스템이 SPINDLE-DIRECTION 으로 뻗는다 — NORTH 는 +Y', () => {
    const vs = vertices(valve('NORTH'))
    const maxY = Math.max(...vs.map((p) => p.y))
    const maxX = Math.max(...vs.map((p) => Math.abs(p.x)))

    // 조작부 끝(핸드휠)은 몸통 반지름의 2.4배쯤 북쪽으로 나간다
    expect(maxY).toBeGreaterThan(R_BODY * 2)
    // 북쪽으로만 뻗어야 한다 — 몸통 반지름을 넘는 X 성분이 있으면 방향이 틀린 것이다
    expect(maxX).toBeLessThanOrEqual(R_BODY + 1)
  })

  it.each([
    ['EAST', 'x', 1],
    ['WEST', 'x', -1],
    ['UP', 'z', 1],
    ['DOWN', 'z', -1],
  ] as const)('%s 방향도 축과 부호가 맞는다', (dir, axis, sign) => {
    const vs = vertices(valve(dir))
    const far = Math.max(...vs.map((p) => p[axis] * sign))

    expect(far).toBeGreaterThan(R_BODY * 2)
  })

  it('SPINDLE-DIRECTION 이 없으면 몸통만 그린다 — 방향을 지어내지 않는다', () => {
    const meshes = valve()

    expect(meshes).toHaveLength(1)
    expect(meshes[0].geometry.type).toBe('CylinderGeometry')
  })
})

describe('앵글 밸브 — CENTRE 는 두 축이 만나는 모서리점', () => {
  /**
   * 실 코퍼스(P1201-001)의 PSV-1707A 를 CENTRE 기준으로 옮긴 값.
   * CENTRE 는 END2 와 X·Y 가 같고 END1 과 Z 가 같다 = 90° 모서리.
   */
  const CORNER: [number, number, number] = [0, 0, 0]
  const E1: [number, number, number] = [67.882, 67.882, 0] // 1"
  const E2: [number, number, number] = [0, 0, -87] // 0.75"

  function valve(withCentre = true) {
    const ports = [port('END', E1, 25.4), port('END', E2, 19.05)]
    if (withCentre) ports.push(port('CENTRE', CORNER))
    return buildScene(scene(component('VALVE_ANGLE', ports, 'v'))).pickables
  }

  it('원뿔 2개로 모서리를 거쳐 간다 — 직선 하나면 경로를 벗어난다', () => {
    expect(valve()).toHaveLength(2)
  })

  it('모서리를 가로지르지 않는다 — 두 END 를 잇는 현에서 멀리 떨어져 있다', () => {
    const vs = vertices(valve())
    // 직선으로 이었다면 정점들이 E1↔E2 현 근처에 몰린다. 모서리를 거치면 현에서 벗어난다
    const a = new THREE.Vector3(...E1)
    const b = new THREE.Vector3(...E2)
    const chordDir = new THREE.Vector3().subVectors(b, a).normalize()
    const far = vs.filter((p) => {
      const rel = new THREE.Vector3().subVectors(p, a)
      return rel.clone().sub(chordDir.clone().multiplyScalar(rel.dot(chordDir))).length() > 30
    })

    expect(far.length).toBeGreaterThan(0)
  })

  it('END 에서 굵고 CENTRE 로 좁아진다 — 보타이', () => {
    const meshes = valve()
    for (const m of meshes) {
      const g = m.geometry as THREE.CylinderGeometry
      const wide = Math.max(g.parameters.radiusTop, g.parameters.radiusBottom)
      const narrow = Math.min(g.parameters.radiusTop, g.parameters.radiusBottom)
      expect(narrow).toBeLessThan(wide * 0.5)
    }
    // 다리마다 자기 END 의 보어를 쓴다 — 1" 쪽이 0.75" 쪽보다 굵다
    const radii = meshes
      .map((m) => {
        const g = m.geometry as THREE.CylinderGeometry
        return Math.max(g.parameters.radiusTop, g.parameters.radiusBottom)
      })
      .sort((x, y) => x - y)
    expect(radii[1]).toBeGreaterThan(radii[0])
  })

  it('CENTRE 가 없으면 기존 BODY 처럼 직선 하나로 낮춘다', () => {
    const meshes = valve(false)

    expect(meshes).toHaveLength(1)
    expect(meshes[0].geometry.type).toBe('CylinderGeometry')
  })
})

describe('편심 리듀서 — FLAT-DIRECTION', () => {
  /**
   * 실 코퍼스(P1201-001, FLAT-DIRECTION UP)의 20"×14" 리듀서를 큰쪽 END 기준으로 옮긴 값.
   * 작은쪽 중심이 위로 76.2mm(= (508−355.6)/2) 어긋나 있다 — 편심량이 좌표에 이미 들어 있다.
   */
  const P_LARGE: [number, number, number] = [0, 0, 0]
  const P_SMALL: [number, number, number] = [-508, 0, 76.2]

  function eccentric(flat: Component3D['flatDirection']) {
    const s = scene(
      component('REDUCER', [port('END', P_LARGE, 508), port('END', P_SMALL, 355.6)], 'r', flat),
    )
    return buildScene(s).pickables
  }

  it('양 끝면이 런 축에 수직인 평행면이다 — 기울면 접속 배관과 틈이 벌어진다', () => {
    // 런 축은 −X. 중심선(−508, 0, +76.2)을 그대로 쓰면 끝면이 8.5° 기울어진다
    for (const v of vertices(eccentric('UP'))) {
      const onLarge = Math.abs(v.x - 0) < 1e-3
      const onSmall = Math.abs(v.x + 508) < 1e-3
      expect(onLarge || onSmall).toBe(true)
    }
  })

  it('UP 이면 윗면이 평평하다 — 두 링의 최상단이 같은 높이', () => {
    const vs = vertices(eccentric('UP'))
    const topLarge = Math.max(...vs.filter((v) => v.x > -1).map((v) => v.z))
    const topSmall = Math.max(...vs.filter((v) => v.x < -507).map((v) => v.z))

    expect(topLarge).toBeCloseTo(254, 3) // 508/2
    expect(topSmall).toBeCloseTo(254, 3) // 76.2 + 355.6/2
  })

  it('UP 이면 아랫면은 평평하지 않다 — 반지름 차만큼 단이 진다', () => {
    const vs = vertices(eccentric('UP'))
    const bottomLarge = Math.min(...vs.filter((v) => v.x > -1).map((v) => v.z))
    const bottomSmall = Math.min(...vs.filter((v) => v.x < -507).map((v) => v.z))

    expect(bottomLarge).toBeCloseTo(-254, 3)
    expect(bottomSmall).toBeCloseTo(-101.6, 3) // 76.2 − 177.8
  })

  it('DOWN 이면 아랫면이 평평하다', () => {
    // 아래로 어긋난 좌표 — 6"×4" 편심 리듀서를 단순화한 값
    const s = scene(
      component(
        'REDUCER',
        [port('END', [0, 0, 0], 200), port('END', [400, 0, -50], 100)],
        'r',
        'DOWN',
      ),
    )
    const vs = vertices(buildScene(s).pickables)
    const bottomLarge = Math.min(...vs.filter((v) => v.x < 1).map((v) => v.z))
    const bottomSmall = Math.min(...vs.filter((v) => v.x > 399).map((v) => v.z))

    expect(bottomLarge).toBeCloseTo(-100, 3)
    expect(bottomSmall).toBeCloseTo(-100, 3)
  })

  it('FLAT-DIRECTION 이 없으면 지금까지처럼 동심 원뿔대로 그린다', () => {
    const s = scene(component('REDUCER', [port('END', [0, 0, 0], 200), port('END', [400, 0, 0], 100)]))
    const { pickables } = buildScene(s)

    expect(pickables[0].geometry.type).toBe('CylinderGeometry')
  })

  it('수직 배관이면 편심 방향을 정할 수 없어 동심으로 낮춘다', () => {
    // 런이 flat 방향(Z)과 나란하다
    const s = scene(
      component('REDUCER', [port('END', [0, 0, 0], 200), port('END', [0, 0, 400], 100)], 'r', 'UP'),
    )
    const { pickables } = buildScene(s)

    expect(pickables).toHaveLength(1)
    expect(pickables[0].geometry.type).toBe('CylinderGeometry')
  })
})

describe('엘보 호 — CENTRE-POINT 는 모서리점이지 호의 중심이 아니다', () => {
  /**
   * 실 코퍼스의 90° 엘보를 원점 기준으로 옮긴 값.
   * CENTRE 는 한쪽 END 와 X·Y 가 같고 다른 END 와 Z 가 같다 = 두 축의 교점(모서리).
   */
  const A: [number, number, number] = [0, 0, 533]
  const B: [number, number, number] = [-376.888, -376.888, 0]
  const CORNER: [number, number, number] = [0, 0, 0]

  function elbowMeshes() {
    const s = scene(
      component('ELBOW', [port('END', A, 355.6), port('END', B, 355.6), port('CENTRE', CORNER)]),
    )
    return buildScene(s).pickables
  }

  it('호가 모서리를 가로지른다 — 바깥으로 부풀면 방향이 반대로 보인다', () => {
    const centre = centroid(elbowMeshes())
    const distanceFromCorner = centre.length()

    // 올바른 호의 중점은 모서리에서 약 221mm.
    // CENTRE 를 호의 중심으로 착각하면 반대편 533mm 지점으로 부푼다.
    expect(distanceFromCorner).toBeLessThan(400)
    expect(distanceFromCorner).toBeGreaterThan(80)
  })

  it('호의 양 끝이 END-POINT 와 만난다 — 배관과 벌어지면 안 된다', () => {
    const meshes = elbowMeshes()
    const pos = meshes[0].geometry.attributes.position

    const endA = new THREE.Vector3(...A)
    const endB = new THREE.Vector3(...B)
    let nearA = Infinity
    let nearB = Infinity
    for (let i = 0; i < pos.count; i++) {
      const v = new THREE.Vector3(pos.getX(i), pos.getY(i), pos.getZ(i))
      nearA = Math.min(nearA, v.distanceTo(endA))
      nearB = Math.min(nearB, v.distanceTo(endB))
    }
    // 튜브 표면이므로 반지름(177.8) 만큼은 떨어져 있는 것이 정상이다
    expect(nearA).toBeLessThan(200)
    expect(nearB).toBeLessThan(200)
  })

  it('호는 모서리 반대편으로 부풀지 않는다', () => {
    const centre = centroid(elbowMeshes())
    // 올바른 호는 두 END 를 잇는 현(chord)의 바깥이 아니라 모서리 쪽에 있다.
    // 현의 중점보다 모서리에서 더 먼 곳에 있으면 반대로 그린 것이다
    const chordMid = new THREE.Vector3(...A).add(new THREE.Vector3(...B)).multiplyScalar(0.5)
    expect(centre.length()).toBeLessThan(chordMid.length() * 1.6)
  })

  it('두 다리가 일직선이면 호 대신 직선으로 낮춘다', () => {
    const s = scene(
      component('ELBOW', [
        port('END', [-500, 0, 0], 100),
        port('END', [500, 0, 0], 100),
        port('CENTRE', [0, 0, 0]),
      ]),
    )
    const { pickables } = buildScene(s)

    expect(pickables).toHaveLength(1)
    // 직선 대체 시에는 원기둥(CylinderGeometry)이 나온다
    expect(pickables[0].geometry.type).toBe('CylinderGeometry')
  })

  it('CENTRE 가 없으면 두 END 를 잇는 직선으로 낮춘다', () => {
    const s = scene(component('ELBOW', [port('END', [0, 0, 0], 100), port('END', [500, 500, 0], 100)]))
    const { pickables } = buildScene(s)

    // 엔진이 CENTRE 없는 엘보를 PIPE 로 낮춰 보내므로 여기서는 shape=ELBOW 라도 안전해야 한다
    expect(pickables.length).toBeLessThanOrEqual(1)
  })
})

describe('엘보 접합부 — 끝면이 배관 축에 수직이어야 한다', () => {
  /**
   * 화면에서 배관과 엘보가 딱 맞아떨어지지 않던 문제의 회귀 테스트.
   *
   * `CatmullRomCurve3` 로 호를 근사하면 첫 점의 접선이 참 접선이 아니라 **첫 현 방향**이 되어
   * 튜브 끝면이 호 분할각의 절반만큼 기운다. 실측으로 반지름 177.8mm · 90°/16분할 기준
   * 접합면에서 ±8.71mm(2.81°) 어긋났다 — 배관과 엘보 사이에 쐐기 모양 틈이 보였다.
   */
  const A: [number, number, number] = [0, 0, 533]
  const B: [number, number, number] = [-376.888, -376.888, 0]
  const CORNER: [number, number, number] = [0, 0, 0]
  const BORE = 355.6
  const R = BORE / 2

  const elbow = () =>
    component('ELBOW', [port('END', A, BORE), port('END', B, BORE), port('CENTRE', CORNER)], 'e')

  /** 접합점 `at` 에서 축 `axis` 방향으로 뻗는 배관 */
  function pipeFrom(at: [number, number, number], axis: THREE.Vector3, id: string) {
    const far = new THREE.Vector3(...at).addScaledVector(axis, 800)
    return component('PIPE', [port('END', at, BORE), port('END', [far.x, far.y, far.z], BORE)], id)
  }

  /** 엘보와 배관을 함께 만들고 각각의 월드 정점을 돌려준다 */
  function joint(at: [number, number, number], axis: THREE.Vector3) {
    const { pickables } = buildScene(scene(elbow(), pipeFrom(at, axis, 'p')))
    return {
      elbow: vertices(pickables.filter((m) => m.userData.componentId === 'e')),
      pipe: vertices(pickables.filter((m) => m.userData.componentId === 'p')),
    }
  }

  // A 에서 배관은 +Z 로, B 에서는 모서리 반대편(-X-Y 대각선)으로 뻗는다
  const CASES: [string, [number, number, number], THREE.Vector3][] = [
    ['END A (수직)', A, new THREE.Vector3(0, 0, 1)],
    ['END B (수평 대각)', B, new THREE.Vector3(-1, -1, 0).normalize()],
  ]

  it.each(CASES)('%s — 엘보 끝 링이 접합면 위에 평평하게 놓인다', (_name, at, axis) => {
    const p = new THREE.Vector3(...at)
    const { elbow: ev } = joint(at, axis)

    // 접합면(축방향 offset 0) 위에 있는 엘보 정점 개수.
    // 끝면이 기울면 링 전체가 평면을 벗어나 2개까지 줄어든다
    const onPlane = ev.filter((v) => Math.abs(new THREE.Vector3().subVectors(v, p).dot(axis)) < 0.01)

    expect(onPlane.length).toBeGreaterThanOrEqual(16)
    // 그 정점들은 모두 정확히 반지름만큼 떨어져 있어야 한다 (스플라인 근사는 반지름도 갉아먹었다)
    for (const v of onPlane) expect(v.distanceTo(p)).toBeCloseTo(R, 3)
  })

  it.each(CASES)('%s — 배관 테두리와 엘보 정점이 어긋나지 않는다', (_name, at, axis) => {
    const p = new THREE.Vector3(...at)
    const { elbow: ev, pipe: pv } = joint(at, axis)

    // 배관 테두리 = 접합면 위의 옆면 정점 (뚜껑 중심은 반지름이 0 이라 뺀다)
    const rim = pv.filter(
      (v) =>
        Math.abs(new THREE.Vector3().subVectors(v, p).dot(axis)) < 0.01 && v.distanceTo(p) > R * 0.5,
    )
    expect(rim).not.toHaveLength(0)

    const worst = Math.max(...rim.map((v) => Math.min(...ev.map((e) => e.distanceTo(v)))))
    expect(worst).toBeLessThan(0.5) // 고치기 전에는 8.71mm 였다
  })
})
