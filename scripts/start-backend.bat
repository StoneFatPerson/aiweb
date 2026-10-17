@echo off
REM ============================================================
REM  One-click backend launcher (Spring Boot, fixed port 8080)
REM
REM  Why fixed port: some environments inject SERVER_PORT /
REM  SERVER__PORT variables, which Spring uses to override
REM  application.yml (env vars beat config files). The command
REM  line arg --server.port=8080 has the HIGHEST priority and
REM  always wins, so the backend really binds 8080 and the
REM  Vite proxy can reach it.
REM
REM  Chinese guide: see the docs folder in this project
REM  (local startup and testing guide).
REM ============================================================
cd /d %~dp0..

where mvn >nul 2>nul
if errorlevel 1 (
    echo [ERROR] mvn was not found in your PATH.
    echo Please install Maven 3.9+ and add it to PATH,
    echo or simply start the backend from IntelliJ IDEA:
    echo open AiwebApplication.java, right click, Run.
    echo Full guide: see the docs folder in this project.
    pause
    exit /b 1
)

echo [Backend] Starting, port fixed to 8080 ...
echo [Hint] Success = the log shows:
echo   "Started AiwebApplication" and "Tomcat started on port 8080"
echo [Hint] First run downloads the e5 embedding model ^(~100MB^),
echo   please wait 1-3 minutes and keep internet connected.
echo.
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8080
pause
