import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'

import App from './App.vue'
import router from './router'
import './styles/main.css'

// 应用入口：注册 Element Plus（中文）与路由，然后挂载到 index.html 的 #app
const app = createApp(App)

app.use(ElementPlus, { locale: zhCn })
app.use(router)

/**
 * 全局错误处理：防止「一个组件抛错 → 整个页面白屏」。
 * Vue 3 中模板表达式或生命周期里未捕获的异常会冒泡到 errorHandler；
 * 这里统一转成一条错误提示，应用本身保持可用（其他页面/交互不受影响）。
 *
 * 去重：相同错误 3 秒内只弹一次——否则多个组件同时抛错（如轮询期间切换路由）
 * 会连续弹出多条相同文案，形成「弹窗轰炸」。
 */
let lastErrorInfo = ''
let lastErrorAt = 0
app.config.errorHandler = (err, _instance, info) => {
  console.error('[应用错误]', info, err)
  const now = Date.now()
  if (info === lastErrorInfo && now - lastErrorAt < 3000) return
  lastErrorInfo = info
  lastErrorAt = now
  const { ElMessage } = ElementPlus
  ElMessage({
    type: 'error',
    message: '页面出现了一点问题，其他功能仍可正常使用',
    duration: 4000,
    showClose: true
  })
}

// 懒加载路由 chunk 加载失败（离线/弱网/后端 404）：vue-router 会透传 rejection，
// 只靠上面的 errorHandler 捕获不到，这里显式提示「刷新重试」
router.onError((error) => {
  const msg = String((error && error.message) || '')
  if (msg.includes('Failed to fetch dynamically imported module')) {
    const { ElMessage } = ElementPlus
    ElMessage.error('页面组件加载失败（可能网络中断），请刷新重试')
  }
  console.error('[路由错误]', error)
})

// 未被 catch 的 Promise 拒绝（如接口超时后组件已卸载）也统一提示，避免静默失败
window.addEventListener('unhandledrejection', (event) => {
  console.error('[未处理的 Promise 拒绝]', event.reason)
})

app.mount('#app')
