// StatusBar.tsx — 하단 상태바. 진행 메시지, 진단 건수(펼치기), 테마/언어를 표시한다
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { MdErrorOutline, MdWarningAmber, MdInfoOutline } from 'react-icons/md'
import i18n from '@/i18n'
import { useAppStore } from '@/store/useAppStore'
import type { Diagnostic3D } from '@/types/scene3d'

const SEVERITY_ICON = {
  ERROR: <MdErrorOutline className="text-red-500" size={14} />,
  WARNING: <MdWarningAmber className="text-amber-500" size={14} />,
  INFO: <MdInfoOutline className="text-sky-500" size={14} />,
}

export default function StatusBar() {
  const { t } = useTranslation()

  /** 진단 코드 + 파라미터를 사람이 읽는 문구로 바꾼다. 번역이 없으면 코드를 그대로 보인다 */
  const describe = (d: Diagnostic3D) => t(`diag.${d.code}`, { defaultValue: d.code, ...d.params })

  const message = useAppStore((s) => s.statusMessage)
  const theme = useAppStore((s) => s.theme)
  const diagnostics = useAppStore((s) => s.diagnostics)
  const [open, setOpen] = useState(false)

  const errors = diagnostics.filter((d) => d.severity === 'ERROR').length
  const warnings = diagnostics.filter((d) => d.severity === 'WARNING').length

  return (
    <div className="relative shrink-0">
      {open && diagnostics.length > 0 && (
        <div className="absolute bottom-6 right-0 z-20 max-h-72 w-[520px] overflow-auto border border-slate-300 bg-white text-xs shadow-lg dark:border-slate-600 dark:bg-slate-800">
          <ul>
            {diagnostics.map((d, i) => (
              <li
                key={i}
                className="flex items-start gap-2 border-b border-slate-100 px-3 py-1.5 last:border-0 dark:border-slate-700"
              >
                <span className="mt-0.5 shrink-0">{SEVERITY_ICON[d.severity]}</span>
                <span className="min-w-0">
                  {describe(d)}
                  {d.lineNo > 0 && <span className="ml-1 text-slate-400">(line {d.lineNo})</span>}
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}

      <div className="flex h-6 items-center gap-4 border-t border-slate-200 bg-slate-50 px-3 text-[11px] text-slate-500 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-400">
        <span className="truncate">{message ?? t('status.ready')}</span>
        <div className="flex-1" />

        <button
          onClick={() => setOpen((v) => !v)}
          disabled={diagnostics.length === 0}
          className="flex items-center gap-1.5 rounded px-1.5 enabled:hover:bg-slate-200 disabled:cursor-default dark:enabled:hover:bg-slate-700"
        >
          {errors > 0 && SEVERITY_ICON.ERROR}
          {errors === 0 && warnings > 0 && SEVERITY_ICON.WARNING}
          {t('status.diagnostics', { count: diagnostics.length })}
        </button>

        <span>
          {t('status.theme')}: {t(`theme.${theme}`)}
        </span>
        <span>
          {t('status.language')}: {t(`lang.${i18n.language === 'en' ? 'en' : 'ko'}`)}
        </span>
      </div>
    </div>
  )
}
