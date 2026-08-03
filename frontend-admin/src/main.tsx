import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { USE_MOCK_API } from './api/mockMode'

// 목 모드 개발 중에만 콘솔용 실시간 이벤트 도구(window.safyMock)를 붙인다.
if (import.meta.env.DEV && USE_MOCK_API) {
  void import('./mocks/mockRealtimeEvents').then((module) => module.registerMockRealtimeHelpers())
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
