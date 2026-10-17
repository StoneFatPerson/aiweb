@echo off
REM ============================================================
REM  Download the local embedding model (e5-small-v2, ~134MB)
REM
REM  Run this ONCE on a new machine, before start-backend.bat.
REM  Why: application.yml now reads the model from a local file
REM  (file:///%USERPROFILE%/.aiweb/models/e5/), because the
REM  hf-mirror CDN redirect for model.onnx is very slow in China
REM  (measured: ~1KB in 20s), which hangs every backend startup.
REM
REM  If download fails on your network, copy the two files from
REM  another machine to:  %USERPROFILE%\.aiweb\models\e5\
REM ============================================================

set MODEL_DIR=%USERPROFILE%\.aiweb\models\e5

if exist "%MODEL_DIR%\model.onnx" (
    echo [OK] Model already exists: %MODEL_DIR%\model.onnx
    echo You can start the backend now ^(scripts\start-backend.bat^).
    pause
    exit /b 0
)

echo [Model] Downloading to %MODEL_DIR% ...
echo [Hint] model.onnx is ~134MB, this may take a few minutes.
echo [Hint] If it stalls for a long time, your network may not
echo        reach the mirror; copy the files from another machine.
echo.

if not exist "%MODEL_DIR%" mkdir "%MODEL_DIR%"

curl -L --retry 3 --connect-timeout 20 -o "%MODEL_DIR%\tokenizer.json" ^
    "https://hf-mirror.com/intfloat/e5-small-v2/raw/main/tokenizer.json"
if errorlevel 1 (
    echo [ERROR] tokenizer.json download failed.
    pause
    exit /b 1
)

curl -L --retry 3 --connect-timeout 20 -o "%MODEL_DIR%\model.onnx" ^
    "https://hf-mirror.com/intfloat/e5-small-v2/resolve/main/model.onnx"
if errorlevel 1 (
    echo [ERROR] model.onnx download failed.
    echo Please copy the two files from another machine to:
    echo   %MODEL_DIR%
    pause
    exit /b 1
)

echo.
echo [OK] Model downloaded to: %MODEL_DIR%
echo Now start the backend: scripts\start-backend.bat
pause
