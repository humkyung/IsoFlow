// LeftPanel.tsx — 좌측 패널. 가져온 라인 정보와 컴포넌트 목록을 보여주고 선택을 연동한다
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAppStore } from '@/store/useAppStore'

/** 형상 종류별 표시 색 — 3D 뷰어 색과 맞춘다 */
const SHAPE_COLOR: Record<string, string> = {
  PIPE: '#8fa3b8',
  ELBOW: '#7d93aa',
  TEE: '#5b9bd5',
  CROSS: '#5b9bd5',
  OLET: '#5b9bd5',
  REDUCER: '#6aa84f',
  BODY: '#e07b39',
  VALVE_ANGLE: '#e07b39',
  VALVE_BUTTERFLY: '#e07b39',
  VALVE_GATE: '#e07b39',
  VALVE_GLOBE: '#e07b39',
  VALVE_BALL: '#e07b39',
  VALVE_CHECK: '#e07b39',
  VALVE_PLUG: '#e07b39',
  NONE: '#b0b7c0',
}

export default function LeftPanel() {
  const { t } = useTranslation()
  const scene = useAppStore((s) => s.scene)
  const selected = useAppStore((s) => s.selectedComponentIds)
  const setSelection = useAppStore((s) => s.setSelection)
  const [query, setQuery] = useState('')

  const components = useMemo(() => {
    if (!scene) return []
    const q = query.trim().toLowerCase()
    if (!q) return scene.components
    return scene.components.filter(
      (c) =>
        c.rawKeyword.toLowerCase().includes(q) ||
        (c.skey ?? '').toLowerCase().includes(q) ||
        (c.itemCode ?? '').toLowerCase().includes(q),
    )
  }, [scene, query])

  return (
    <aside className="flex w-72 shrink-0 flex-col border-r border-slate-200 bg-slate-50 dark:border-slate-700 dark:bg-slate-800/40">
      <div className="border-b border-slate-200 px-3 py-2 text-xs font-semibold uppercase tracking-wide text-slate-500 dark:border-slate-700 dark:text-slate-400">
        {t('panel.lines')}
      </div>

      {!scene ? (
        <p className="p-3 text-sm text-slate-400 dark:text-slate-500">{t('panel.noLines')}</p>
      ) : (
        <>
          <dl className="space-y-1 border-b border-slate-200 px-3 py-2 text-xs dark:border-slate-700">
            <Row label={t('panel.lineNumber')} value={scene.pipeline.lineNumber} strong />
            <Row label={t('panel.pipingSpec')} value={scene.pipeline.pipingSpec} />
            <Row label={t('panel.area')} value={scene.pipeline.area} />
            <Row label={t('panel.componentCount')} value={String(scene.components.length)} />
          </dl>

          <div className="border-b border-slate-200 p-2 dark:border-slate-700">
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder={t('panel.search')}
              aria-label={t('panel.search')}
              className="w-full rounded border border-slate-300 bg-white px-2 py-1 text-xs outline-none focus:border-sky-500 dark:border-slate-600 dark:bg-slate-900"
            />
          </div>

          <ul className="flex-1 overflow-auto py-1 text-xs">
            {components.map((c) => {
              const isSelected = selected.includes(c.id)
              return (
                <li key={c.id}>
                  <button
                    onClick={() => setSelection([c.id])}
                    className={`flex w-full items-center gap-2 px-3 py-1 text-left transition-colors ${
                      isSelected
                        ? 'bg-sky-100 text-sky-900 dark:bg-sky-900/50 dark:text-sky-100'
                        : 'hover:bg-slate-200/70 dark:hover:bg-slate-700/60'
                    }`}
                  >
                    <span
                      className="h-2.5 w-2.5 shrink-0 rounded-sm"
                      style={{ backgroundColor: SHAPE_COLOR[c.shape] ?? SHAPE_COLOR.NONE }}
                    />
                    <span className="truncate font-mono">{c.rawKeyword}</span>
                    {c.skey && (
                      <span className="ml-auto shrink-0 font-mono text-[10px] text-slate-400">{c.skey}</span>
                    )}
                  </button>
                </li>
              )
            })}
          </ul>
        </>
      )}
    </aside>
  )
}

/** 라벨-값 한 줄. 값이 없으면 그리지 않는다 */
function Row({ label, value, strong }: { label: string; value?: string; strong?: boolean }) {
  if (!value) return null
  return (
    <div className="flex gap-2">
      <dt className="w-20 shrink-0 text-slate-400 dark:text-slate-500">{label}</dt>
      <dd className={`min-w-0 break-all ${strong ? 'font-semibold' : ''}`}>{value}</dd>
    </div>
  )
}
