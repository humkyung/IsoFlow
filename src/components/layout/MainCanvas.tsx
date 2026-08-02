// MainCanvas.tsx — 중앙 뷰어 영역. 뷰 모드 전환과 PCF 드래그&드롭 가져오기를 담당한다
import { useState, type DragEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { MdViewInAr, MdGridOn, MdFileUpload } from 'react-icons/md'
import { useAppStore } from '@/store/useAppStore'
import { useImportFile } from '@/hooks/useImportPipeline'
import Viewer3D from '@/components/viewer/Viewer3D'
import Viewer2D from '@/components/viewer/Viewer2D'

export default function MainCanvas() {
  const { t } = useTranslation()
  const viewMode = useAppStore((s) => s.viewMode)
  const setViewMode = useAppStore((s) => s.setViewMode)
  const importing = useAppStore((s) => s.importing)
  const importFile = useImportFile()
  const [dragOver, setDragOver] = useState(false)

  const handleDrop = (e: DragEvent<HTMLElement>) => {
    e.preventDefault()
    setDragOver(false)
    const file = e.dataTransfer?.files?.[0]
    if (file) void importFile(file)
  }

  return (
    <main
      className="relative min-w-0 flex-1 bg-slate-100 dark:bg-slate-900"
      onDragOver={(e) => {
        e.preventDefault()
        setDragOver(true)
      }}
      onDragLeave={() => setDragOver(false)}
      onDrop={handleDrop}
    >
      {/* 뷰 전환 토글 — 상단 중앙 플로팅 */}
      <div className="absolute left-1/2 top-3 z-10 flex -translate-x-1/2 gap-0.5 rounded-lg border border-slate-300 bg-white p-0.5 shadow-sm dark:border-slate-600 dark:bg-slate-800">
        {(
          [
            ['3d', <MdViewInAr key="i3" size={16} />],
            ['2d', <MdGridOn key="i2" size={16} />],
          ] as const
        ).map(([mode, icon]) => (
          <button
            key={mode}
            onClick={() => setViewMode(mode)}
            className={`flex items-center gap-1.5 rounded px-3 py-1 text-xs transition-colors ${
              viewMode === mode
                ? 'bg-sky-600 text-white'
                : 'hover:bg-slate-100 dark:hover:bg-slate-700'
            }`}
          >
            {icon}
            {t(`viewer.mode${mode === '3d' ? '3d' : '2d'}`)}
          </button>
        ))}
      </div>

      {viewMode === '3d' ? <Viewer3D /> : <Viewer2D />}

      {dragOver && (
        <div className="pointer-events-none absolute inset-3 z-20 flex flex-col items-center justify-center gap-2 rounded-lg border-2 border-dashed border-sky-500 bg-sky-50/80 text-sky-700 dark:bg-sky-950/70 dark:text-sky-300">
          <MdFileUpload size={32} />
          <span className="text-sm">{t('viewer.dropHint')}</span>
        </div>
      )}

      {importing && (
        <div className="absolute inset-0 z-30 flex items-center justify-center bg-white/60 dark:bg-slate-900/60">
          <span className="rounded bg-white px-4 py-2 text-sm shadow dark:bg-slate-800">
            {t('viewer.importing')}
          </span>
        </div>
      )}
    </main>
  )
}
