import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // 도메인이 하나뿐이라 지금은 경로 기반(/admin/)으로 서빙한다.
  // 서브도메인(admin.<domain>)이 확보되면 '/'로 되돌릴 것.
  base: '/admin/',
})
