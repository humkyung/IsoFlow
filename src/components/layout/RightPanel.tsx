// RightPanel.tsx — 우측 속성 패널. 선택된 컴포넌트의 PCF 속성과 포트를 그대로 보여준다
import { useTranslation } from 'react-i18next'
import { useAppStore } from '@/store/useAppStore'

export default function RightPanel() {
  const { t } = useTranslation()
  const scene = useAppStore((s) => s.scene)
  const selected = useAppStore((s) => s.selectedComponentIds)

  const component = scene?.components.find((c) => c.id === selected[0]) ?? null

  return (
    <aside className="flex w-80 shrink-0 flex-col border-l border-slate-200 bg-slate-50 dark:border-slate-700 dark:bg-slate-800/40">
      <div className="border-b border-slate-200 px-3 py-2 text-xs font-semibold uppercase tracking-wide text-slate-500 dark:border-slate-700 dark:text-slate-400">
        {t('panel.properties')}
      </div>

      {!component ? (
        <p className="p-3 text-sm text-slate-400 dark:text-slate-500">{t('panel.noSelection')}</p>
      ) : (
        <div className="flex-1 space-y-3 overflow-auto p-3 text-xs">
          <dl className="space-y-1">
            <Row label={t('panel.components')} value={component.rawKeyword} strong />
            <Row label={t('panel.shape')} value={component.shape} />
            <Row label={t('panel.skey')} value={component.skey} />
            <Row label={t('panel.itemCode')} value={component.itemCode} />
            <Row label={t('panel.description')} value={component.description} />
            <Row
              label={t('panel.weight')}
              value={component.weight != null ? `${component.weight} kg` : undefined}
            />
            <Row
              label={t('panel.angle')}
              value={component.angleDeg != null ? `${component.angleDeg}°` : undefined}
            />
          </dl>

          <section>
            <h3 className="mb-1 font-semibold text-slate-500 dark:text-slate-400">{t('panel.ports')}</h3>
            <table className="w-full border-collapse font-mono text-[10.5px]">
              <tbody>
                {component.ports.map((p, i) => (
                  <tr key={i} className="border-b border-slate-200 last:border-0 dark:border-slate-700">
                    <td className="py-1 pr-2 align-top text-slate-500">{p.kind}</td>
                    <td className="py-1 pr-2 align-top">
                      {p.p.map((n) => n.toFixed(1)).join(', ')}
                      <div className="text-slate-400">
                        {p.bore != null && `${t('panel.bore')} ${p.bore.toFixed(1)}mm`}
                        {p.endType && ` · ${p.endType}`}
                        {p.joint && ` · ${p.joint}`}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </section>

          {component.attrs && Object.keys(component.attrs).length > 0 && (
            <section>
              <h3 className="mb-1 font-semibold text-slate-500 dark:text-slate-400">
                {t('panel.rawAttributes')}
              </h3>
              <dl className="space-y-0.5 font-mono text-[10.5px]">
                {Object.entries(component.attrs).map(([k, v]) => (
                  <div key={k} className="flex gap-2">
                    <dt className="w-32 shrink-0 truncate text-slate-400" title={k}>
                      {k}
                    </dt>
                    <dd className="min-w-0 break-all">{v || '—'}</dd>
                  </div>
                ))}
              </dl>
            </section>
          )}
        </div>
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
