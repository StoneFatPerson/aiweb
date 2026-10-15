@echo off
chcp 65001 >nul
REM ============================================================
REM  一键启动前端（Vue 3 + Vite），默认 5173 端口
REM  前端会把 /api 请求代理到后端 8080（见 vite.config.js）
REM  需先运行 start-backend.bat 启动后端，否则页面会提示"后端未连接"
REM ============================================================
cd /d %~dp0..\frontend
echo [前端] 启动中，端口 5173 ...
if not exist node_modules (
  echo [前端] 首次运行，正在用国内镜像安装依赖...
  npm install --registry=https://registry.npmmirror.com
)
npm run dev
pause
