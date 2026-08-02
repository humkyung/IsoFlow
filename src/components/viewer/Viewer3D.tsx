// Viewer3D.tsx — PCF/IDF 배관 모델 3D 뷰어. Scene3DRenderer 를 마운트하고 Scene/선택/테마/투명도를 반영한다
import { useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { MdOpacity } from 'react-icons/md'
import { Scene3DRenderer } from '@/viewer/Scene3DRenderer'
import { useAppStore } from '@/store/useAppStore'

/** 슬라이더가 허용하는 최대 투명도(%). 100% 면 모델이 사라져 버린다 */
const MAX_TRANSPARENCY = 80

export default function Viewer3D() {
  const { t } = useTranslation()
  const hostRef = useRef<HTMLDivElement>(null)
  const rendererRef = useRef<Scene3DRenderer | null>(null)

  const theme = useAppStore((s) => s.theme)
  const scene = useAppStore((s) => s.scene)
  const selected = useAppStore((s) => s.selectedComponentIds)
  const fitRequest = useAppStore((s) => s.fitRequest)
  const setSelection = useAppStore((s) => s.setSelection)
  const modelOpacity = useAppStore((s) => s.modelOpacity)
  const setModelOpacity = useAppStore((s) => s.setModelOpacity)

  // 슬라이더는 투명도(0% = 불투명), 렌더러는 불투명도를 쓴다
  const transparency = Math.round((1 - modelOpacity) * 100)

  useEffect(() => {
    if (!hostRef.current) return
    const r = new Scene3DRenderer(hostRef.current)
    r.onSelect = (id) => setSelection(id ? [id] : [])
    rendererRef.current = r
    return () => {
      r.dispose()
      rendererRef.current = null
    }
  }, [setSelection])

  useEffect(() => {
    rendererRef.current?.setTheme(theme)
  }, [theme])

  useEffect(() => {
    rendererRef.current?.setScene(scene)
  }, [scene])

  useEffect(() => {
    rendererRef.current?.setSelection(selected)
  }, [selected])

  useEffect(() => {
    rendererRef.current?.setModelOpacity(modelOpacity)
  }, [modelOpacity])

  // 화면 맞춤 요청 — 카운터가 바뀔 때만 카메라를 다시 잡는다
  useEffect(() => {
    if (fitRequest > 0 && scene) rendererRef.current?.fitToBounds(scene.bounds)
  }, [fitRequest, scene])

  return (
    <div className="absolute inset-0">
      <div ref={hostRef} className="h-full w-full" />

      {/* 투명도 슬라이더 — 좌상단 플로팅. 배관 속·뒤쪽을 들여다볼 때 쓴다 */}
      <div className="absolute left-3 top-3 z-10 flex items-center gap-2 rounded-lg border border-slate-300 bg-white/90 px-2.5 py-1.5 shadow-sm dark:border-slate-600 dark:bg-slate-800/90">
        <MdOpacity size={16} className="text-slate-500 dark:text-slate-400" />
        <input
          type="range"
          min={0}
          max={MAX_TRANSPARENCY}
          step={5}
          value={transparency}
          onChange={(e) => setModelOpacity(1 - Number(e.target.value) / 100)}
          title={t('viewer.transparency')}
          aria-label={t('viewer.transparency')}
          className="w-24 accent-sky-600"
        />
        <span className="w-8 text-right text-[11px] tabular-nums text-slate-500 dark:text-slate-400">
          {transparency}%
        </span>
      </div>

      {!scene && (
        <p className="pointer-events-none absolute bottom-6 left-1/2 -translate-x-1/2 rounded bg-white/80 px-3 py-1.5 text-xs text-slate-500 dark:bg-slate-800/80 dark:text-slate-400">
          {t('viewer.empty3d')}
        </p>
      )}
    </div>
  )
}
