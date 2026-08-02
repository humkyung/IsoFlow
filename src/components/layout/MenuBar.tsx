// MenuBar.tsx — 상단 메뉴 바. 카테고리 선택이 리본 탭과 동기화되고, 테마/언어 전환을 제공한다
import { useTranslation } from 'react-i18next'
import { MdDarkMode, MdLightMode, MdLanguage } from 'react-icons/md'
import { useAppStore, type RibbonTab } from '@/store/useAppStore'
import i18n, { setLanguage } from '@/i18n'

const TABS: RibbonTab[] = ['file', 'home', 'view', 'isometric', 'help']

export default function MenuBar() {
  const { t } = useTranslation()
  const ribbonTab = useAppStore((s) => s.ribbonTab)
  const setRibbonTab = useAppStore((s) => s.setRibbonTab)
  const theme = useAppStore((s) => s.theme)
  const toggleTheme = useAppStore((s) => s.toggleTheme)

  /** 한국어 ↔ 영어를 번갈아 전환한다 */
  const cycleLanguage = () => setLanguage(i18n.language === 'ko' ? 'en' : 'ko')

  return (
    <div className="flex h-9 shrink-0 items-center gap-1 border-b border-slate-200 bg-slate-50 px-2 dark:border-slate-700 dark:bg-slate-800">
      <span className="mr-3 select-none text-sm font-semibold text-sky-700 dark:text-sky-400">
        {t('app.name')}
      </span>

      {TABS.map((tab) => (
        <button
          key={tab}
          onClick={() => setRibbonTab(tab)}
          className={`rounded px-3 py-1 text-sm transition-colors ${
            ribbonTab === tab
              ? 'bg-sky-100 font-medium text-sky-800 dark:bg-sky-900/50 dark:text-sky-200'
              : 'hover:bg-slate-200 dark:hover:bg-slate-700'
          }`}
        >
          {t(`menu.${tab}`)}
        </button>
      ))}

      <div className="flex-1" />

      <button
        onClick={cycleLanguage}
        aria-label={t('status.language')}
        title={t('status.language')}
        className="rounded p-1.5 hover:bg-slate-200 dark:hover:bg-slate-700"
      >
        <MdLanguage size={18} />
      </button>
      <button
        onClick={toggleTheme}
        aria-label={t('ribbon.toggleTheme')}
        title={t('ribbon.toggleTheme')}
        className="rounded p-1.5 hover:bg-slate-200 dark:hover:bg-slate-700"
      >
        {theme === 'dark' ? <MdLightMode size={18} /> : <MdDarkMode size={18} />}
      </button>
    </div>
  )
}
