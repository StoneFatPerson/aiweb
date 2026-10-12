# 智研 · AI 考研备考助手 —— 前端

Vue 3 + Vite + Element Plus 构建的前端工程，与后端 `aiweb`（Spring Boot）配合使用。

## 一、技术选型

| 项 | 选型 | 说明 |
|---|---|---|
| 框架 | Vue 3 | 组合式 API（`<script setup>`），代码更简洁 |
| 构建 | Vite 6 | 开发时秒级热更新，打包快 |
| UI 组件 | Element Plus | 表格、表单、抽屉等组件开箱即用，界面专业且统一 |
| 路由 | Vue Router 4 | 页面切换，非首屏页面按需加载 |
| 请求 | Axios | 统一封装在 `src/api/index.js` |

全部为开源免费方案，**不需要注册账号、不需要付费**。

## 二、目录结构

```
aiweb-frontend/
├── index.html                 页面模板
├── vite.config.js             构建与开发代理配置
├── package.json               依赖与脚本
└── src/
    ├── main.js                应用入口
    ├── App.vue                整体布局（顶部导航 + 路由出口 + 页脚）
    ├── router/index.js        路由表
    ├── api/index.js           后端接口封装
    ├── styles/main.css        全局样式
    └── views/
        ├── HomeView.vue       首页
        ├── CollegeView.vue    院校专业库
        ├── PlanView.vue       备考计划
        └── ChatView.vue       AI 问答
```

## 三、安装与运行

前置条件：Node.js 18+（本机为 22）。

```bash
# 1. 安装依赖（国内建议用 npmmirror 镜像，快很多）
npm install --registry=https://registry.npmmirror.com

# 2. 启动开发服务器
npm run dev
```

启动后访问 http://localhost:5173

> **必须先启动后端**（`aiweb` 项目的 8080 端口），否则页面右上角会显示「后端未连接」。

## 四、前后端如何连通

开发时不需要后端配置跨域——Vite 的代理已经处理好了。

`vite.config.js` 中的关键配置：

```js
server: {
  port: 5173,
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true
    }
  }
}
```

意思是：前端发出的 `/api/xxx` 请求，会被 Vite 自动转发到 `http://localhost:8080/api/xxx`。

因此 `src/api/index.js` 里的 baseURL 只写 `/api`，**代码里不出现后端地址**，部署时也不用改。

## 五、打包

```bash
npm run build
```

产物输出到 `dist/`。如需整体托管到后端，把 `dist/` 里的内容复制到后端的
`src/main/resources/static/` 即可（后端会自动把 `static/index.html` 作为欢迎页）。

## 六、常见问题

| 现象 | 原因 | 解决 |
|---|---|---|
| 右上角显示「后端未连接」 | 后端没启动 | 先启动 `aiweb` 后端 |
| 页面空白、控制台报接口错误 | 代理没生效 | 确认是通过 `npm run dev` 打开的（直接双击 `dist/index.html` 不行） |
| 安装依赖时报证书/权限错误 | 网络代理或沙箱限制 | 换自己的终端执行，或加 `--registry` 指定镜像 |

## 七、无障碍与响应式

- 页面容器、导航、表格均做了窄屏适配（720px 以下自动调整布局）
- 顶部导航使用语义化 `nav` 标签并带 `aria-label`
- 对话区使用 `role="log"` 与 `aria-live="polite"`，屏幕阅读器能播报新消息
- 全局样式尊重系统的「减少动态效果」设置
