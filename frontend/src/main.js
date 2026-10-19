import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'

import App from './App.vue'
import router from './router'
import './styles/main.css'

// ==================== dev 模式专用：拦截 Vite 客户端的自动整页刷新 ====================
// 【问题】用户反复反馈「最小化窗口再还原，页面像自动重新打开、表单状态全丢」。
// 【根因（源码级定位）】Vite dev 客户端（node_modules/vite/dist/client/client.mjs）在
//   WebSocket 断线（笔记本休眠/锁屏/网络瞬断/重启 dev server）后有一个「轮询等待服务器
//   重启」的流程：窗口隐藏期间挂起等待，一旦还原为可见且 ping 成功，立即执行
//   location.reload() 整页刷新——这是 Vite 为「改完代码自动重启 dev server」设计的
//   既有行为，但它把「用户最小化窗口去干别的事」也当成了重启场景。
//   项目在 Desktop（OneDrive 同步目录）下，最小化期间文件 mtime 变动还会额外触发
//   Vite 的 full-reload。
// 【为什么这样修】node_modules 里的 Vite 源码不能改（装依赖即被覆盖），但 reload 是
//   它未来的某个时刻调的——我们可以在自己的入口（早于任何断线发生）把 location.reload
//   包装一层：非用户意图的刷新一律拦下，只在控制台留一条说明。
//   HMR（组件热更新）走的是 update 消息，不走 reload，所以改代码热更新不受影响；
//   只有「改了 main.js/router 等无 acceptor 的模块」这种本来就该整页刷新的场景，
//   需要手动按 F5——用一条控制台日志提示，换来「绝不无故丢状态」。
// 【生效范围】仅 dev 模式（import.meta.hot 只在 dev server 存在），生产构建会被
//   tree-shake 剔除，对 8080 生产模式零影响。
if (import.meta.hot) {
  try {
    location.reload = function patchedReload() {
      console.info(
        '[aiweb] 已拦截一次自动刷新（来自 Vite dev 的断线重连/文件监视行为）。' +
          '如果刚修改了代码，请手动按 F5 刷新；最小化窗口不会再导致页面重载。'
      )
    }
  } catch (e) {
    // 个别浏览器不允许覆盖 location.reload：不影响功能，只是拦不住，控制台留证
    console.warn('[aiweb] 当前浏览器不允许覆盖 location.reload，自动刷新拦截未生效', e)
  }
}

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
