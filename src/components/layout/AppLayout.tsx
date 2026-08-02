// AppLayout.tsx — 전체 화면 골격. 상단 메뉴/리본, 좌우 패널, 중앙 뷰어, 하단 상태바를 배치한다
import MenuBar from './MenuBar'
import RibbonBar from './RibbonBar'
import LeftPanel from './LeftPanel'
import RightPanel from './RightPanel'
import MainCanvas from './MainCanvas'
import StatusBar from './StatusBar'
import StyleDialog from '@/components/dialogs/StyleDialog'
import { useAppStore } from '@/store/useAppStore'

export default function AppLayout() {
  const leftOpen = useAppStore((s) => s.leftPanelOpen)
  const rightOpen = useAppStore((s) => s.rightPanelOpen)

  return (
    <div className="flex h-full flex-col bg-white text-slate-800 dark:bg-slate-900 dark:text-slate-100">
      <MenuBar />
      <RibbonBar />
      <div className="flex min-h-0 flex-1">
        {leftOpen && <LeftPanel />}
        <MainCanvas />
        {rightOpen && <RightPanel />}
      </div>
      <StatusBar />
      <StyleDialog />
    </div>
  )
}
