// pipelines.ts — 파이프라인(PCF/IDF) 관련 백엔드 API 클라이언트
import { apiDownload, apiFetch } from './http'
import type { ImportResult } from '@/types/scene3d'
import type { GenerateResult } from '@/types/scene2d'
import type { IsoStyle } from '@/types/isoStyle'

/** 내보내기 형식 */
export type ExportFormat = 'dxf' | 'pdf' | 'bom' | 'cutlist' | 'weldlist'

/** PCF/IDF 파일을 올려 3D Scene 과 진단을 받는다 */
export async function importPipelineFile(file: File): Promise<ImportResult> {
  const form = new FormData()
  form.append('file', file)
  return apiFetch<ImportResult>('/api/pipelines/import', { method: 'POST', body: form })
}

/**
 * 등각도를 생성한다.
 * 서버가 아직 원본을 저장하지 않으므로 파일을 다시 보낸다 — DB 가 붙으면 id 만 보내면 된다.
 *
 * @param style 등각도 생성 설정. 생략하면 서버 기본값
 */
export async function generateIsometric(file: File, style?: IsoStyle): Promise<GenerateResult> {
  const form = new FormData()
  form.append('file', file)
  if (style) form.append('style', JSON.stringify(style))
  return apiFetch<GenerateResult>('/api/isometrics/generate', { method: 'POST', body: form })
}

/** 등각도를 파일로 내려받는다 — 화면과 같은 스타일로 그려야 하므로 설정을 함께 보낸다 */
export async function exportIsometric(file: File, format: ExportFormat, style?: IsoStyle) {
  const form = new FormData()
  form.append('file', file)
  form.append('format', format)
  if (style) form.append('style', JSON.stringify(style))
  return apiDownload('/api/isometrics/export', { method: 'POST', body: form })
}

/** 서버 기본 스타일 — 프론트 복제본이 어긋났는지 확인하거나 초기화할 때 쓴다 */
export async function fetchDefaultStyle(): Promise<IsoStyle> {
  return apiFetch<IsoStyle>('/api/styles/default')
}
