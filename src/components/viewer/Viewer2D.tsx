// Viewer2D.tsx — 생성된 등각도 2D 뷰어. Scene2DRenderer 를 마운트하고 Scene/테마/시트를 반영한다
import { useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { MdChevronLeft, MdChevronRight } from 'react-icons/md'
import { Scene2DRenderer } from '@/viewer/Scene2DRenderer'
import { useAppStore } from '@/store/useAppStore'

export default function Viewer2D() {
  const { t } = useTranslation()
  const hostRef = useRef<HTMLDivElement>(null)
  const rendererRef = useRef<Scene2DRenderer | null>(null)

  const theme = useAppStore((s) => s.theme)
  const scenes = useAppStore((s) => s.isoScenes)
  const sheetIndex = useAppStore((s) => s.sheetIndex)
  const setSheetIndex = useAppStore((s) => s.setSheetIndex)
  const fitRequest = useAppStore((s) => s.fitRequest)

  const isoScene = scenes[sheetIndex] ?? null

  useEffect(() => {
    if (!hostRef.current) return
    const r = new Scene2DRenderer(hostRef.current)
    rendererRef.current = r
    return () => {
      r.dispose()
      rendererRef.current = null
    }
  }, [])

  useEffect(() => {
    rendererRef.current?.setTheme(theme)
  }, [theme])

  // 선 색이 테마에 따라 달라지므로 Scene 을 그릴 때 테마를 함께 넘긴다
  useEffect(() => {
    rendererRef.current?.setScene(isoScene, theme)
    rendererRef.current?.setTheme(theme)
  }, [isoScene, theme])

  useEffect(() => {
    if (fitRequest > 0 && isoScene) rendererRef.current?.fitToBounds(isoScene.bounds)
  }, [fitRequest, isoScene])

  return (
    <div className="absolute inset-0">
      <div ref={hostRef} className="h-full w-full" />

      {/* 시트가 한 장뿐이면 넘길 것이 없다 */}
      {scenes.length > 1 && (
        <div className="absolute bottom-4 left-1/2 flex -translate-x-1/2 items-center gap-1 rounded-full bg-white/90 px-1.5 py-1 shadow dark:bg-slate-800/90">
          <button
            onClick={() => setSheetIndex(sheetIndex - 1)}
            disabled={sheetIndex === 0}
            aria-label={t('viewer.prevSheet')}
            className="rounded-full p-1 enabled:hover:bg-slate-100 disabled:opacity-30 dark:enabled:hover:bg-slate-700"
          >
            <MdChevronLeft size={18} />
          </button>
          <span className="px-1 text-xs tabular-nums">
            {t('viewer.sheetOf', { current: sheetIndex + 1, total: scenes.length })}
          </span>
          <button
            onClick={() => setSheetIndex(sheetIndex + 1)}
            disabled={sheetIndex >= scenes.length - 1}
            aria-label={t('viewer.nextSheet')}
            className="rounded-full p-1 enabled:hover:bg-slate-100 disabled:opacity-30 dark:enabled:hover:bg-slate-700"
          >
            <MdChevronRight size={18} />
          </button>
        </div>
      )}

      {!isoScene && (
        <p className="pointer-events-none absolute bottom-6 left-1/2 -translate-x-1/2 rounded bg-white/80 px-3 py-1.5 text-xs text-slate-500 dark:bg-slate-800/80 dark:text-slate-400">
          {t('viewer.empty2d')}
        </p>
      )}
    </div>
  )
}
