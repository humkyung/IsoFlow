// http.ts — 백엔드 호출 공통 래퍼. 오류는 언어중립 코드로 던지고 문구는 호출측이 i18n 으로 만든다
/**
 * 백엔드 오류 응답 계약: `{ code, <보간 파라미터…>, error }`
 * `code` 는 프론트가 `err.<code>` 키로 번역하고, `error` 는 코드 매핑이 없을 때만 쓰는 폴백이다.
 */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly params: Record<string, unknown>

  constructor(status: number, code: string, params: Record<string, unknown>) {
    // message 는 진단용이다 — 사용자에게 그대로 보여주지 않는다
    super(`${status} ${code}`)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.params = params
  }
}

/** 응답 본문에서 오류 계약을 꺼낸다. 파싱 실패해도 상태 코드는 살린다 */
async function toApiError(res: Response): Promise<ApiError> {
  let code = 'INTERNAL_ERROR'
  let params: Record<string, unknown> = {}
  try {
    const body = (await res.json()) as Record<string, unknown>
    if (typeof body.code === 'string') code = body.code
    const { code: _c, error: _e, ...rest } = body
    params = rest
  } catch {
    // 본문이 JSON 이 아니면 상태 코드만으로 판단한다
  }
  return new ApiError(res.status, code, params)
}

export async function apiFetch<T>(input: string, init?: RequestInit): Promise<T> {
  const res = await fetch(input, init)
  if (!res.ok) throw await toApiError(res)
  return (await res.json()) as T
}

/** 파일 응답을 내려받는다. 오류는 JSON 계약을 그대로 따른다 */
export async function apiDownload(
  input: string,
  init?: RequestInit,
): Promise<{ blob: Blob; fileName: string }> {
  const res = await fetch(input, init)
  if (!res.ok) throw await toApiError(res)

  // Content-Disposition 의 filename 을 쓰고, 없으면 호출측이 정한다
  const disposition = res.headers.get('content-disposition') ?? ''
  const match = /filename="?([^"]+)"?/.exec(disposition)
  return { blob: await res.blob(), fileName: match ? match[1] : '' }
}
