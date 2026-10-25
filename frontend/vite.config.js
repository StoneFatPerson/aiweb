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
    // 文件监视范围收敛：项目在 Desktop（OneDrive 同步目录）下，
    // 最小化窗口期间 OneDrive 同步/杀毒扫描会改文件 mtime → chokidar 报 change →
    // Vite 对没有 HMR acceptor 的模块（main.js/router/api 等）直接下发 full-reload，
    // 表现为「最小化后页面被自动刷新」。显式忽略无关目录可大幅降低误触发。
    watch: {
      ignored: ['**/node_modules/**', '**/.git/**', '**/dist/**', '**/target/**']
    },
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
    chunkSizeWarningLimit: 1200,
    rollupOptions: {
      output: {
        // 把体积大的第三方依赖拆成独立文件：入口包变小、首屏更快；
        // 且这些库很少变，浏览器能长期缓存（改业务代码时用户不必重下整个 vendor）。
        manualChunks: {
          'vendor-vue': ['vue', 'vue-router'],
          'vendor-element': ['element-plus'],
          'vendor-axios': ['axios']
        }
      }
    }
  }
})
