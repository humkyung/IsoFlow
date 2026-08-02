// RibbonBar.tsx — 선택된 메뉴 카테고리의 리본 그룹/버튼을 표시한다 (M0 단계에서는 동작 없이 자리만 잡는다)
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import {
  MdFolderOpen, MdFactCheck, MdAutoAwesome,
  MdTune, MdTableChart, MdSave, MdPictureAsPdf, MdFitScreen, MdViewInAr, MdGridOn,
  MdContentCut, MdJoinInner,
} from 'react-icons/md'
import { useAppStore, type RibbonTab } from '@/store/useAppStore'
import { useImportPipeline } from '@/hooks/useImportPipeline'
import { useGenerateIsometric } from '@/hooks/useGenerateIsometric'
import { useExportIsometric } from '@/hooks/useExportIsometric'

interface Button {
  /** i18n 키 (ribbon.*) */
  key: string
  icon: ReactNode
  onClick?: () => void
  /** 비활성 조건 */
  disabled?: boolean
}
interface Group {
  key: string
  buttons: Button[]
}

/** 탭별 리본 구성을 만든다. 아직 붙지 않은 동작은 disabled 로 둔다(눌러도 아무 일 없는 버튼을 만들지 않는다). */
function useGroups(tab: RibbonTab): Group[] {
  const setViewMode = useAppStore((s) => s.setViewMode)
  const requestFit = useAppStore((s) => s.requestFit)
  const importing = useAppStore((s) => s.importing)
  const generating = useAppStore((s) => s.generating)
  const hasScene = useAppStore((s) => s.scene !== null)
  const canGenerate = useAppStore((s) => s.sourceFile !== null)
  const exporting = useAppStore((s) => s.exporting)
  const openStyleDialog = useAppStore((s) => s.setStyleDialogOpen)
  const importFile = useImportPipeline()
  const generate = useGenerateIsometric()
  const exportFile = useExportIsometric()
  const busy = generating || exporting

  switch (tab) {
    case 'file':
    case 'home':
      return [
        {
          key: 'groupInput',
          // PCF·IDF 를 한 버튼에서 받는다 — 형식은 파일 선택 대화상자에서 가른다
          buttons: [
            {
              key: 'importModel',
              icon: <MdFolderOpen size={22} />,
              onClick: () => void importFile(),
              disabled: importing,
            },
          ],
        },
        {
          key: 'groupModel',
          buttons: [{ key: 'diagnostics', icon: <MdFactCheck size={22} />, disabled: true }],
        },
        {
          key: 'groupIsometric',
          buttons: [
            {
              key: 'generate',
              icon: <MdAutoAwesome size={22} />,
              onClick: () => void generate(),
              disabled: !canGenerate || busy,
            },
            {
              key: 'style',
              icon: <MdTune size={22} />,
              onClick: () => openStyleDialog(true),
              disabled: busy,
            },
            {
              key: 'bom',
              icon: <MdTableChart size={22} />,
              onClick: () => void exportFile('bom'),
              disabled: !canGenerate || busy,
            },
          ],
        },
        {
          key: 'groupExport',
          buttons: [
            {
              key: 'exportDxf',
              icon: <MdSave size={22} />,
              onClick: () => void exportFile('dxf'),
              disabled: !canGenerate || busy,
            },
            {
              key: 'exportPdf',
              icon: <MdPictureAsPdf size={22} />,
              onClick: () => void exportFile('pdf'),
              disabled: !canGenerate || busy,
            },
            {
              key: 'exportCutList',
              icon: <MdContentCut size={22} />,
              onClick: () => void exportFile('cutlist'),
              disabled: !canGenerate || busy,
            },
            {
              key: 'exportWeldList',
              icon: <MdJoinInner size={22} />,
              onClick: () => void exportFile('weldlist'),
              disabled: !canGenerate || busy,
            },
          ],
        },
      ]
    case 'view':
      return [
        {
          key: 'groupDisplay',
          buttons: [
            { key: 'mode3d', icon: <MdViewInAr size={22} />, onClick: () => setViewMode('3d') },
            { key: 'mode2d', icon: <MdGridOn size={22} />, onClick: () => setViewMode('2d') },
            { key: 'fitView', icon: <MdFitScreen size={22} />, onClick: requestFit, disabled: !hasScene },
          ],
        },
      ]
    default:
      return []
  }
}

export default function RibbonBar() {
  const { t } = useTranslation()
  const tab = useAppStore((s) => s.ribbonTab)
  const groups = useGroups(tab)

  return (
    <div className="flex h-[84px] shrink-0 items-stretch gap-0 overflow-x-auto border-b border-slate-200 bg-white px-2 dark:border-slate-700 dark:bg-slate-800/50">
      {groups.map((g) => (
        <div key={g.key} className="flex flex-col border-r border-slate-200 px-3 py-1 dark:border-slate-700">
          <div className="flex flex-1 items-start gap-1">
            {g.buttons.map((b) => (
              <button
                key={b.key}
                onClick={b.onClick}
                disabled={b.disabled}
                className="flex w-[74px] flex-col items-center gap-1 rounded px-1 py-1.5 text-[11px] leading-tight enabled:hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-40 dark:enabled:hover:bg-slate-700"
              >
                <span className="text-sky-700 dark:text-sky-400">{b.icon}</span>
                <span className="text-center">
                  {t(b.key.startsWith('mode') ? `viewer.${b.key}` : `ribbon.${b.key}`)}
                </span>
              </button>
            ))}
          </div>
          <div className="pt-0.5 text-center text-[10px] text-slate-400 dark:text-slate-500">
            {t(`ribbon.${g.key}`)}
          </div>
        </div>
      ))}
    </div>
  )
}
