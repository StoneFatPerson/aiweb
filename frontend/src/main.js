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
 */
app.config.errorHandler = (err, _instance, info) => {
  console.error('[应用错误]', info, err)
  const { ElMessage } = ElementPlus
  ElMessage({
    type: 'error',
    message: '页面出现了一点问题，其他功能仍可正常使用',
    duration: 4000,
    showClose: true
  })
}

// 未被 catch 的 Promise 拒绝（如接口超时后组件已卸载）也统一提示，避免静默失败
window.addEventListener('unhandledrejection', (event) => {
  console.error('[未处理的 Promise 拒绝]', event.reason)
})

app.mount('#app')
