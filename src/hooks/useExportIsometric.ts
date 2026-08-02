// useExportIsometric.ts — 등각도를 DXF/PDF/CSV 로 내려받는다
import { useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { exportIsometric, type ExportFormat } from '@/api/pipelines'
import { ApiError } from '@/api/http'
import { useAppStore } from '@/store/useAppStore'

/** 블롭을 파일로 저장시킨다 */
function saveBlob(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  document.body.appendChild(a)
  a.click()
  a.remove()
  // 즉시 해제하면 일부 브라우저에서 저장이 취소된다 — 한 틱 뒤에 정리한다
  setTimeout(() => URL.revokeObjectURL(url), 0)
}

export function useExportIsometric() {
  const { t } = useTranslation()
  const sourceFile = useAppStore((s) => s.sourceFile)
  const isoStyle = useAppStore((s) => s.isoStyle)
  const setExporting = useAppStore((s) => s.setExporting)
  const setStatusMessage = useAppStore((s) => s.setStatusMessage)

  return useCallback(
    async (format: ExportFormat) => {
      if (!sourceFile) return

      setExporting(true)
      setStatusMessage(t('log.exporting', { format: format.toUpperCase() }))
      try {
        // 화면에서 본 것과 같은 스타일로 내보낸다
        const { blob, fileName } = await exportIsometric(sourceFile, format, isoStyle)
        const name = fileName || `${sourceFile.name.replace(/\.[^.]+$/, '')}.${format}`
        saveBlob(blob, name)
        setStatusMessage(t('log.exported', { name }))
      } catch (e) {
        // API 는 언어중립 코드만 던진다 — 문구는 여기서 만든다
        const code = e instanceof ApiError ? e.code : 'generic'
        const params = e instanceof ApiError ? e.params : {}
        setStatusMessage(
          t('log.exportFailed', {
            format: format.toUpperCase(),
            reason: t(`err.${code}`, { defaultValue: t('err.generic'), ...params }),
          }),
        )
        console.error('[export] 실패', e)
      } finally {
        setExporting(false)
      }
    },
    [sourceFile, isoStyle, t, setExporting, setStatusMessage],
  )
}
