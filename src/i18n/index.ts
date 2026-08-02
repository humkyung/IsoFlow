// i18n/index.ts — react-i18next 초기화. 한국어/영어 리소스 등록, 선택 언어 로컬스토리지 영속
import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import ko from './locales/ko.json'
import en from './locales/en.json'

/** 지원 언어 */
export type Language = 'ko' | 'en'

const STORAGE_KEY = 'isoflow-lang'

/** 로컬스토리지에 저장된 언어를 초기값으로 사용 (없으면 ko) */
function getInitialLanguage(): Language {
  if (typeof window === 'undefined') return 'ko'
  return window.localStorage.getItem(STORAGE_KEY) === 'en' ? 'en' : 'ko'
}

i18n.use(initReactI18next).init({
  resources: {
    ko: { translation: ko },
    en: { translation: en },
  },
  lng: getInitialLanguage(),
  fallbackLng: 'en',
  supportedLngs: ['ko', 'en'],
  interpolation: { escapeValue: false },
  // 개발 모드에서만 누락 키를 콘솔 경고로 노출한다(운영 콘솔 오염 방지)
  saveMissing: import.meta.env.DEV,
  missingKeyHandler: import.meta.env.DEV
    ? (lngs, _ns, key) => console.warn(`[i18n] 누락된 키: ${key} (${lngs.join(',')})`)
    : undefined,
})

/** 언어를 변경하고 선택을 영속화한다 */
export function setLanguage(lng: Language) {
  i18n.changeLanguage(lng)
  if (typeof window !== 'undefined') {
    window.localStorage.setItem(STORAGE_KEY, lng)
  }
}

export default i18n
