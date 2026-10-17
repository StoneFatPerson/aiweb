@echo off
REM ============================================================
REM  One-click frontend launcher (Vue 3 + Vite, port 5173)
REM
REM  Vite proxies /api requests to the backend on 8080
REM  (see vite.config.js). You MUST start the backend first
REM  (scripts\start-backend.bat), otherwise the page shows
REM  "backend not connected".
REM
REM  Chinese guide: see the docs folder in this project
REM  (local startup and testing guide).
REM ============================================================
cd /d %~dp0..\frontend

where node >nul 2>nul
if errorlevel 1 (
    echo [ERROR] node / npm was not found in your PATH.
    echo Please install Node.js 18+ LTS from https://nodejs.org
    echo and re-open this window after installation.
    pause
    exit /b 1
)

echo [Frontend] Starting on port 5173 ...
if not exist node_modules (
    echo [Frontend] First run: installing dependencies via npmmirror...
    echo [Hint] This takes 1-2 minutes.
    npm install --registry=https://registry.npmmirror.com
)
echo [Hint] Open http://localhost:5173 in your browser.
echo.
npm run dev
pause
