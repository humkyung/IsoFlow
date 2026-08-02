// sdfWebgl2Compat.ts — troika SDF 생성기가 만드는 WebGL1 컨텍스트를 WebGL2 로 승격시키는 호환 심
//
// 배경: 등각도 문자는 troika-three-text 의 SDF 아틀라스로 그린다. 아틀라스를 굽는
// webgl-sdf-generator 는 three 와 별개로 자기 캔버스에 `getContext('webgl')` 로
// WebGL1 컨텍스트를 만들고, 인스턴싱에 `ANGLE_instanced_arrays` 확장을 요구한다.
// Brave 는 WebGL1 컨텍스트에 이 확장을 노출하지 않아(확장 목록이 WebGL2 의 것으로 나온다)
// SDF 생성이 실패하고, JS 폴백까지 완료되지 않아 문자가 통째로 사라진다.
//
// webgl-sdf-generator 는 이미 `isWebGL2` 분기(vertexAttribDivisor / drawArraysInstanced / gl.MAX)를
// 갖고 있으므로, 컨텍스트만 WebGL2 로 주면 확장 없이 정상 동작한다.
// three 는 r163 부터 WebGL2 전용이라 이 심의 영향을 받지 않는다.

/** 이 브라우저가 위 문제를 겪는지 판별한다 — WebGL1 에 인스턴싱이 없고 WebGL2 는 되는 경우 */
function needsWebgl2Promotion(): boolean {
  try {
    const canvas = document.createElement('canvas')
    const gl1 = canvas.getContext('webgl') as WebGLRenderingContext | null
    // WebGL1 이 인스턴싱을 노출하면 troika 는 원래 경로로 잘 돈다
    if (gl1?.getExtension('ANGLE_instanced_arrays')) return false
    // WebGL2 조차 안 되면 승격해도 소용없다 — 건드리지 않는다
    return !!document.createElement('canvas').getContext('webgl2')
  } catch {
    return false
  }
}

let installed = false

/**
 * 필요한 브라우저에서만 `getContext('webgl')` 을 WebGL2 로 승격시킨다.
 * troika 가 첫 SDF 아틀라스를 만들기 전에(=앱 부트스트랩 시점) 호출해야 한다.
 */
export function installSdfWebgl2Compat(): void {
  if (installed || typeof document === 'undefined') return
  if (!needsWebgl2Promotion()) return
  installed = true

  const protos: { getContext: (...args: never[]) => unknown }[] = [
    HTMLCanvasElement.prototype as never,
    ...(typeof OffscreenCanvas === 'function' ? [OffscreenCanvas.prototype as never] : []),
  ]

  for (const proto of protos) {
    const original = proto.getContext
    proto.getContext = function (this: unknown, ...args: never[]) {
      // 'webgl' 요청만 가로챈다. 승격이 실패하면 원래 동작으로 되돌린다
      if (args[0] === ('webgl' as never)) {
        const promoted = original.apply(this, ['webgl2' as never, args[1]])
        if (promoted) return promoted
      }
      return original.apply(this, args)
    }
  }
}
