// useImportPipeline.ts — 파일 선택 → 업로드 → Scene 반영까지의 흐름을 한 곳에 모은다
import { useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { importPipelineFile } from '@/api/pipelines'
import { ApiError } from '@/api/http'
import { useAppStore } from '@/store/useAppStore'

/** 가져올 수 있는 확장자 — 파일 선택 대화상자의 필터로 쓴다 */
const SUPPORTED_ACCEPT = '.pcf,.idf'

/** 확장자별 파일 선택 대화상자를 띄우고 하나를 고른다 */
function pickFile(accept: string): Promise<File | null> {
  return new Promise((resolve) => {
    const input = document.createElement('input')
    input.type = 'file'
    input.accept = accept
    input.onchange = () => resolve(input.files?.[0] ?? null)
    // 취소를 눌러도 change 가 오지 않으므로 포커스 복귀로 정리한다
    input.oncancel = () => resolve(null)
    input.click()
  })
}

/** 이미 손에 든 File 을 업로드한다 (드래그&드롭 등) */
export function useImportFile() {
  const { t } = useTranslation()
  const setImporting = useAppStore((s) => s.setImporting)
  const setImported = useAppStore((s) => s.setImported)
  const setStatusMessage = useAppStore((s) => s.setStatusMessage)

  return useCallback(
    async (file: File) => {
      setImporting(true)
      setStatusMessage(t('log.importing', { name: file.name }))
      try {
        const result = await importPipelineFile(file)
        setImported(result.scene, result.diagnostics, result.fileName, file)
        setStatusMessage(
          t('log.imported', {
            name: result.fileName,
            count: result.scene.components.length,
          }),
        )
      } catch (e) {
        // API 는 언어중립 코드만 던진다 — 문구는 여기서 만든다
        const code = e instanceof ApiError ? e.code : 'generic'
        const params = e instanceof ApiError ? e.params : {}
        setStatusMessage(
          t('log.importFailed', {
            name: file.name,
            reason: t(`err.${code}`, { defaultValue: t('err.generic'), ...params }),
          }),
        )
        console.error('[import] 실패', e)
      } finally {
        setImporting(false)
      }
    },
    [t, setImporting, setImported, setStatusMessage],
  )
}

/**
 * 파일 선택 대화상자를 띄워 하나를 고르고 업로드한다.
 * PCF·IDF 를 한 대화상자에서 받는다 — 형식은 파서가 확장자로 가른다.
 */
export function useImportPipeline() {
  const importFile = useImportFile()

  return useCallback(async () => {
    const file = await pickFile(SUPPORTED_ACCEPT)
    if (file) await importFile(file)
  }, [importFile])
}
