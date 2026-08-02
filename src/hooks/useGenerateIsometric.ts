// useGenerateIsometric.ts — 등각도 생성 요청과 상태 반영
import { useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { generateIsometric } from '@/api/pipelines'
import { ApiError } from '@/api/http'
import { useAppStore } from '@/store/useAppStore'

export function useGenerateIsometric() {
  const { t } = useTranslation()
  const sourceFile = useAppStore((s) => s.sourceFile)
  const isoStyle = useAppStore((s) => s.isoStyle)
  const setGenerating = useAppStore((s) => s.setGenerating)
  const setIsoScenes = useAppStore((s) => s.setIsoScenes)
  const setStatusMessage = useAppStore((s) => s.setStatusMessage)

  return useCallback(async () => {
    if (!sourceFile) return

    setGenerating(true)
    setStatusMessage(t('log.generating', { name: sourceFile.name }))
    try {
      const result = await generateIsometric(sourceFile, isoStyle)
      setIsoScenes(result.scenes, result.diagnostics)
      const elements = result.scenes.reduce((n, s) => n + s.elements.length, 0)
      setStatusMessage(
        result.scenes.length > 1
          ? t('log.generatedSheets', {
              name: result.fileName, sheets: result.scenes.length, count: elements,
            })
          : t('log.generated', { name: result.fileName, count: elements }),
      )
    } catch (e) {
      // API 는 언어중립 코드만 던진다 — 문구는 여기서 만든다
      const code = e instanceof ApiError ? e.code : 'generic'
      const params = e instanceof ApiError ? e.params : {}
      setStatusMessage(
        t('log.generateFailed', {
          name: sourceFile.name,
          reason: t(`err.${code}`, { defaultValue: t('err.generic'), ...params }),
        }),
      )
      console.error('[isometric] 생성 실패', e)
    } finally {
      setGenerating(false)
    }
  }, [sourceFile, isoStyle, t, setGenerating, setIsoScenes, setStatusMessage])
}
