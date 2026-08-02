// vite.config.ts — Vite 빌드 설정. React + Tailwind v4 플러그인, @ 경로 별칭, vitest 구성
/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    // 기하 계산은 DOM 이 필요 없다. WebGL 이 필요한 렌더러는 테스트 대상이 아니다
    environment: 'node',
    include: ['src/**/*.test.ts'],
  },
  server: {
    // Verso(9000)와 동시에 띄울 수 있도록 다른 포트를 쓴다
    port: 9100,
    // 백엔드(Spring Boot, 8290)로 API 프록시 — 브라우저는 같은 오리진(9100)만 사용해 쿠키를 1st-party 로 유지
    proxy: {
      '/api': { target: 'http://localhost:8290', changeOrigin: true },
    },
  },
})
