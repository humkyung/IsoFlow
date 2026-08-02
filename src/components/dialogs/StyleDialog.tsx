// StyleDialog.tsx — 등각도 생성 설정(용지/심볼/치수/압축/표시항목) 편집 대화상자
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { MdClose, MdRestartAlt } from 'react-icons/md'
import { useAppStore } from '@/store/useAppStore'
import { useGenerateIsometric } from '@/hooks/useGenerateIsometric'
import {
  DEFAULT_ISO_STYLE, DISPLAY_KEYS, PAPER_SIZES,
  type DimensionStyle, type DisplayStyle, type IsoStyle, type SheetStyle, type SymbolStyle,
} from '@/types/isoStyle'

/** 숫자 입력 한 줄 */
function NumberField({
  label, value, step, min, onChange,
}: {
  label: string
  value: number
  step?: number
  min?: number
  onChange: (v: number) => void
}) {
  return (
    <label className="flex items-center justify-between gap-3 py-1 text-xs">
      <span className="text-slate-600 dark:text-slate-300">{label}</span>
      <input
        type="number"
        value={value}
        step={step ?? 1}
        min={min}
        onChange={(e) => {
          const n = Number(e.target.value)
          // 빈 칸이나 문자 입력으로 NaN 이 스타일에 새어 들어가면 서버가 NaN 좌표를 만든다
          if (Number.isFinite(n)) onChange(n)
        }}
        className="w-24 rounded border border-slate-300 bg-white px-2 py-1 text-right tabular-nums dark:border-slate-600 dark:bg-slate-900"
      />
    </label>
  )
}

/** 체크박스 한 줄 */
function CheckField({
  label, checked, onChange,
}: {
  label: string
  checked: boolean
  onChange: (v: boolean) => void
}) {
  return (
    <label className="flex items-center gap-2 py-1 text-xs">
      <input
        type="checkbox"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
        className="size-3.5 accent-sky-600"
      />
      <span className="text-slate-600 dark:text-slate-300">{label}</span>
    </label>
  )
}

/** 설정 묶음 하나 */
function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="rounded border border-slate-200 p-3 dark:border-slate-700">
      <h3 className="mb-1.5 text-[11px] font-semibold uppercase tracking-wide text-slate-400 dark:text-slate-500">
        {title}
      </h3>
      {children}
    </section>
  )
}

export default function StyleDialog() {
  const { t } = useTranslation()
  const open = useAppStore((s) => s.styleDialogOpen)
  const setOpen = useAppStore((s) => s.setStyleDialogOpen)
  const saved = useAppStore((s) => s.isoStyle)
  const setIsoStyle = useAppStore((s) => s.setIsoStyle)
  const canGenerate = useAppStore((s) => s.sourceFile !== null)
  const generate = useGenerateIsometric()

  // 편집 중인 사본 — 취소하면 버린다
  const [draft, setDraft] = useState<IsoStyle>(saved)

  useEffect(() => {
    if (open) setDraft(saved)
  }, [open, saved])

  // Esc 로 닫는다
  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, setOpen])

  if (!open) return null

  const patch = <K extends keyof IsoStyle>(key: K, value: Partial<IsoStyle[K]>) =>
    setDraft((d) => ({ ...d, [key]: { ...d[key], ...value } }))
  const sheet = (v: Partial<SheetStyle>) => patch('sheet', v)
  const symbols = (v: Partial<SymbolStyle>) => patch('symbols', v)
  const dims = (v: Partial<DimensionStyle>) => patch('dimensions', v)
  const display = (v: Partial<DisplayStyle>) => patch('display', v)

  /** 저장하고 닫는다. 이미 연 파일이 있으면 바로 다시 그린다 */
  const apply = () => {
    setIsoStyle(draft)
    setOpen(false)
    if (canGenerate) void generate()
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      onClick={() => setOpen(false)}
    >
      <div
        className="flex max-h-full w-[640px] flex-col rounded-lg bg-white shadow-xl dark:bg-slate-800"
        onClick={(e) => e.stopPropagation()}
      >
        <header className="flex items-center justify-between border-b border-slate-200 px-4 py-2.5 dark:border-slate-700">
          <h2 className="text-sm font-semibold">{t('style.title')}</h2>
          <button
            onClick={() => setOpen(false)}
            className="rounded p-1 hover:bg-slate-100 dark:hover:bg-slate-700"
            aria-label={t('style.cancel')}
          >
            <MdClose size={18} />
          </button>
        </header>

        <div className="grid grid-cols-2 gap-3 overflow-y-auto p-4">
          <Section title={t('style.sheet')}>
            <label className="flex items-center justify-between gap-3 py-1 text-xs">
              <span className="text-slate-600 dark:text-slate-300">{t('style.paperSize')}</span>
              <select
                value={draft.sheet.size}
                onChange={(e) => sheet({ size: e.target.value as SheetStyle['size'] })}
                className="w-24 rounded border border-slate-300 bg-white px-2 py-1 dark:border-slate-600 dark:bg-slate-900"
              >
                {PAPER_SIZES.map((s) => (
                  <option key={s} value={s}>{s}</option>
                ))}
              </select>
            </label>
            <NumberField
              label={t('style.marginMm')} value={draft.sheet.marginMm} min={0}
              onChange={(v) => sheet({ marginMm: v })}
            />
            <NumberField
              label={t('style.tableBandMm')} value={draft.sheet.tableBandMm} min={0} step={5}
              onChange={(v) => sheet({ tableBandMm: v })}
            />
            <NumberField
              label={t('style.titleBlockMm')} value={draft.sheet.titleBlockMm} min={0}
              onChange={(v) => sheet({ titleBlockMm: v })}
            />
            <NumberField
              label={t('style.maxLabelCrowding')} value={draft.sheet.maxLabelCrowding}
              step={0.05} min={0}
              onChange={(v) => sheet({ maxLabelCrowding: v })}
            />
            <NumberField
              label={t('style.maxSheets')} value={draft.sheet.maxSheets} min={1}
              onChange={(v) => sheet({ maxSheets: Math.max(1, Math.round(v)) })}
            />
            <p className="pt-1 text-[10px] leading-snug text-slate-400 dark:text-slate-500">
              {t('style.crowdingHint')}
            </p>
          </Section>

          <Section title={t('style.symbols')}>
            <NumberField
              label={t('style.unitRatio')} value={draft.symbols.unitRatio} step={0.002} min={0.001}
              onChange={(v) => symbols({ unitRatio: v })}
            />
            <NumberField
              label={t('style.minUnitMm')} value={draft.symbols.minUnitMm} step={0.5} min={0}
              onChange={(v) => symbols({ minUnitMm: v })}
            />
            <p className="pt-1 text-[10px] leading-snug text-slate-400 dark:text-slate-500">
              {t('style.unitRatioHint')}
            </p>
          </Section>

          <Section title={t('style.dimensions')}>
            <NumberField
              label={t('style.decimals')} value={draft.dimensions.decimals} min={0}
              onChange={(v) => dims({ decimals: Math.max(0, Math.round(v)) })}
            />
            <NumberField
              label={t('style.minIntervalMm')} value={draft.dimensions.minIntervalMm} step={10} min={0}
              onChange={(v) => dims({ minIntervalMm: v })}
            />
            <NumberField
              label={t('style.minIntervalRatio')} value={draft.dimensions.minIntervalRatio}
              step={0.001} min={0}
              onChange={(v) => dims({ minIntervalRatio: v })}
            />
            <NumberField
              label={t('style.offsetUnits')} value={draft.dimensions.offsetUnits} step={0.5} min={0}
              onChange={(v) => dims({ offsetUnits: v })}
            />
            <NumberField
              label={t('style.stepUnits')} value={draft.dimensions.stepUnits} step={0.2} min={0}
              onChange={(v) => dims({ stepUnits: v })}
            />
            <NumberField
              label={t('style.textHeightUnits')} value={draft.dimensions.textHeightUnits}
              step={0.1} min={0.1}
              onChange={(v) => dims({ textHeightUnits: v })}
            />
            <p className="pt-1 text-[10px] leading-snug text-slate-400 dark:text-slate-500">
              {t('style.minIntervalHint')}
            </p>
          </Section>

          <div className="flex flex-col gap-3">
            <Section title={t('style.compression')}>
              <CheckField
                label={t('style.compressionEnabled')} checked={draft.compression.enabled}
                onChange={(v) => patch('compression', { enabled: v })}
              />
              <NumberField
                label={t('style.maxGapMm')} value={draft.compression.maxGapMm} step={100} min={1}
                onChange={(v) => patch('compression', { maxGapMm: v })}
              />
              <p className="pt-1 text-[10px] leading-snug text-slate-400 dark:text-slate-500">
                {t('style.compressionHint')}
              </p>
            </Section>

            <Section title={t('style.display')}>
              {DISPLAY_KEYS.map((k) => (
                <CheckField
                  key={k}
                  label={t(`style.show.${k}`)}
                  checked={draft.display[k]}
                  onChange={(v) => display({ [k]: v } as Partial<DisplayStyle>)}
                />
              ))}
            </Section>
          </div>
        </div>

        <footer className="flex items-center justify-between border-t border-slate-200 px-4 py-2.5 dark:border-slate-700">
          <button
            onClick={() => setDraft(DEFAULT_ISO_STYLE)}
            className="flex items-center gap-1 rounded px-2 py-1 text-xs text-slate-500 hover:bg-slate-100 dark:text-slate-400 dark:hover:bg-slate-700"
          >
            <MdRestartAlt size={16} />
            {t('style.reset')}
          </button>
          <div className="flex gap-2">
            <button
              onClick={() => setOpen(false)}
              className="rounded border border-slate-300 px-3 py-1 text-xs hover:bg-slate-100 dark:border-slate-600 dark:hover:bg-slate-700"
            >
              {t('style.cancel')}
            </button>
            <button
              onClick={apply}
              className="rounded bg-sky-600 px-3 py-1 text-xs font-medium text-white hover:bg-sky-500"
            >
              {canGenerate ? t('style.applyAndGenerate') : t('style.apply')}
            </button>
          </div>
        </footer>
      </div>
    </div>
  )
}
