import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// Vite 配置
// 关键点：server.proxy 把前端的 /api 请求转发到后端 8080 端口，
// 这样开发时不会出现跨域问题，也不需要后端额外配置 CORS。
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    open: false,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    // 打包产物输出到 dist，后续可整体拷贝进后端的 static 目录
    outDir: 'dist',
    chunkSizeWarningLimit: 1200
  }
})
