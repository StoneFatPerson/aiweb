@echo off
chcp 65001 >nul
REM ============================================================
REM  一键启动后端（Spring Boot），固定使用 8080 端口
REM
REM  为什么固定端口：
REM    有些环境会注入 SERVER_PORT / SERVER__PORT 之类的变量，
REM    Spring 会拿它覆盖 application.yml 里的 8080，前端就连不上后端。
REM    命令行参数 --server.port=8080 优先级最高，能压过这些环境变量。
REM ============================================================
cd /d %~dp0..
echo [后端] 启动中，端口固定 8080 ...
echo [提示] 日志出现 "Started AiwebApplication" 和 "Tomcat started on port 8080" 即启动成功。
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8080
pause
