// useAppStore.ts — 앱 전역 상태(테마 / 뷰 모드 / 리본 탭 / 패널 / 선택 / 가져온 Scene / 진단)
import { create } from 'zustand'
import type { Diagnostic3D, Scene3D } from '@/types/scene3d'
import type { Scene2D } from '@/types/scene2d'
import { DEFAULT_ISO_STYLE, type IsoStyle } from '@/types/isoStyle'

/** 화면에 표시 중인 뷰 — 3D 배관 모델 또는 생성된 등각도 */
export type ViewMode = '3d' | '2d'

/** 리본 탭 = 메뉴 카테고리 */
export type RibbonTab = 'file' | 'home' | 'view' | 'isometric' | 'help'

export type Theme = 'light' | 'dark'

const THEME_KEY = 'isoflow-theme'
const STYLE_KEY = 'isoflow-iso-style'

/**
 * 저장된 등각도 스타일을 읽는다. 항목이 빠졌거나 깨졌으면 기본값으로 메운다 —
 * 스타일이 늘어난 뒤에도 예전 저장값이 앱을 막지 않아야 한다.
 */
function getInitialStyle(): IsoStyle {
  if (typeof window === 'undefined') return DEFAULT_ISO_STYLE
  try {
    const raw = window.localStorage.getItem(STYLE_KEY)
    if (!raw) return DEFAULT_ISO_STYLE
    const saved = JSON.parse(raw) as Partial<IsoStyle>
    return {
      sheet: { ...DEFAULT_ISO_STYLE.sheet, ...saved.sheet },
      symbols: { ...DEFAULT_ISO_STYLE.symbols, ...saved.symbols },
      dimensions: { ...DEFAULT_ISO_STYLE.dimensions, ...saved.dimensions },
      compression: { ...DEFAULT_ISO_STYLE.compression, ...saved.compression },
      display: { ...DEFAULT_ISO_STYLE.display, ...saved.display },
    }
  } catch {
    return DEFAULT_ISO_STYLE
  }
}

/** 로컬스토리지에 저장된 테마를 초기값으로 사용 (없으면 시스템 설정) */
function getInitialTheme(): Theme {
  if (typeof window === 'undefined') return 'light'
  const saved = window.localStorage.getItem(THEME_KEY)
  if (saved === 'light' || saved === 'dark') return saved
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

/** <html> 에 .dark 클래스를 반영한다 (Tailwind v4 custom variant 가 이 클래스를 본다) */
function applyTheme(theme: Theme) {
  if (typeof document === 'undefined') return
  document.documentElement.classList.toggle('dark', theme === 'dark')
}

interface AppState {
  theme: Theme
  viewMode: ViewMode
  ribbonTab: RibbonTab
  leftPanelOpen: boolean
  rightPanelOpen: boolean

  /** 가져온 3D Scene. 없으면 빈 뷰어 */
  scene: Scene3D | null
  /** 원본 파일명 */
  fileName: string | null
  /** 원본 파일 — 등각도 생성 시 다시 보내야 한다(아직 서버가 저장하지 않는다) */
  sourceFile: File | null
  /** 생성된 등각도 시트들 */
  isoScenes: Scene2D[]
  /** 보고 있는 시트 인덱스 (0-based) */
  sheetIndex: number
  /** 등각도 생성 진행 중 */
  generating: boolean
  /** 내보내기 진행 중 */
  exporting: boolean
  /** 파싱·위상 해석 진단 */
  diagnostics: Diagnostic3D[]
  /** 가져오기 진행 중 */
  importing: boolean

  /** 선택된 컴포넌트 id 목록 */
  selectedComponentIds: string[]
  /** 상태바에 표시할 마지막 메시지 */
  statusMessage: string | null
  /** 화면 맞춤 요청 카운터 — 뷰어가 값 변화를 보고 카메라를 다시 잡는다 */
  fitRequest: number
  /** 3D 모델 불투명도(0~1). 일시적인 보기 옵션이라 저장하지 않는다 */
  modelOpacity: number

  /** 등각도 생성 설정 */
  isoStyle: IsoStyle
  /** 스타일 설정 대화상자 열림 여부 */
  styleDialogOpen: boolean

  setTheme: (t: Theme) => void
  toggleTheme: () => void
  setViewMode: (m: ViewMode) => void
  setRibbonTab: (t: RibbonTab) => void
  toggleLeftPanel: () => void
  toggleRightPanel: () => void
  setImporting: (v: boolean) => void
  setImported: (scene: Scene3D, diagnostics: Diagnostic3D[], fileName: string, file: File) => void
  setGenerating: (v: boolean) => void
  setExporting: (v: boolean) => void
  setIsoScenes: (scenes: Scene2D[], diagnostics: Diagnostic3D[]) => void
  setSheetIndex: (i: number) => void
  clearImported: () => void
  setSelection: (ids: string[]) => void
  setStatusMessage: (msg: string | null) => void
  requestFit: () => void
  setModelOpacity: (v: number) => void
  setIsoStyle: (style: IsoStyle) => void
  resetIsoStyle: () => void
  setStyleDialogOpen: (open: boolean) => void
}

export const useAppStore = create<AppState>((set, get) => ({
  theme: getInitialTheme(),
  viewMode: '3d',
  ribbonTab: 'home',
  leftPanelOpen: true,
  rightPanelOpen: true,

  scene: null,
  fileName: null,
  sourceFile: null,
  isoScenes: [],
  sheetIndex: 0,
  generating: false,
  exporting: false,
  diagnostics: [],
  importing: false,

  selectedComponentIds: [],
  statusMessage: null,
  fitRequest: 0,
  modelOpacity: 1,

  isoStyle: getInitialStyle(),
  styleDialogOpen: false,

  setTheme: (theme) => {
    applyTheme(theme)
    if (typeof window !== 'undefined') window.localStorage.setItem(THEME_KEY, theme)
    set({ theme })
  },
  toggleTheme: () => get().setTheme(get().theme === 'dark' ? 'light' : 'dark'),
  setViewMode: (viewMode) => set({ viewMode }),
  setRibbonTab: (ribbonTab) => set({ ribbonTab }),
  toggleLeftPanel: () => set((s) => ({ leftPanelOpen: !s.leftPanelOpen })),
  toggleRightPanel: () => set((s) => ({ rightPanelOpen: !s.rightPanelOpen })),

  setImporting: (importing) => set({ importing }),
  setImported: (scene, diagnostics, fileName, sourceFile) =>
    set({
      scene, diagnostics, fileName, sourceFile,
      // 새 파일을 열면 이전 등각도는 더 이상 유효하지 않다
      isoScenes: [], sheetIndex: 0,
      selectedComponentIds: [], viewMode: '3d',
    }),
  setGenerating: (generating) => set({ generating }),
  setExporting: (exporting) => set({ exporting }),
  setIsoScenes: (isoScenes, diagnostics) =>
    set({ isoScenes, diagnostics, sheetIndex: 0, viewMode: '2d' }),
  // 범위를 벗어난 인덱스는 무시한다 — 시트 수가 줄어든 뒤의 오래된 클릭
  setSheetIndex: (i) =>
    set((s) => (i >= 0 && i < s.isoScenes.length ? { sheetIndex: i } : {})),
  clearImported: () =>
    set({
      scene: null, isoScenes: [], sheetIndex: 0, diagnostics: [], fileName: null,
      sourceFile: null, selectedComponentIds: [],
    }),

  setSelection: (selectedComponentIds) => set({ selectedComponentIds }),
  setStatusMessage: (statusMessage) => set({ statusMessage }),
  requestFit: () => set((s) => ({ fitRequest: s.fitRequest + 1 })),
  // 완전히 사라지면 모델을 잃어버리므로 하한을 둔다
  setModelOpacity: (v) => set({ modelOpacity: Math.min(1, Math.max(0.2, v)) }),

  setIsoStyle: (isoStyle) => {
    // 다음 세션에도 같은 도면 관례를 쓰게 한다
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(STYLE_KEY, JSON.stringify(isoStyle))
    }
    set({ isoStyle })
  },
  resetIsoStyle: () => get().setIsoStyle(DEFAULT_ISO_STYLE),
  setStyleDialogOpen: (styleDialogOpen) => set({ styleDialogOpen }),
}))

// 스토어 생성 시점의 테마를 DOM 에 즉시 반영한다(첫 페인트 전 깜빡임 방지)
applyTheme(useAppStore.getState().theme)
