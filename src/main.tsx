// main.tsx — React 앱 부트스트랩. #root 엘리먼트에 App 을 마운트한다
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import { installSdfWebgl2Compat } from './viewer/sdfWebgl2Compat'
import './i18n'
import './index.css'

// troika 가 첫 SDF 아틀라스를 굽기 전에 적용해야 한다 (Brave 등각도 문자 미표시 대응)
installSdfWebgl2Compat()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
