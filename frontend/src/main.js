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
app.mount('#app')
