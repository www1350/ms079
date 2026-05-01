@echo off
@title MapleStory_079
setlocal enabledelayedexpansion

echo ========================================
echo   MapleStory v079 服务端 命令行模式
echo ========================================

REM 杀掉已有的服务端进程
echo [1/2] 清理旧进程...
for /f "tokens=1" %%i in ('jps -l 2^>nul ^| findstr "GUIApplication MapleStoryApplication"') do (
    echo   发现进程 %%i，正在终止...
    taskkill /F /PID %%i >nul 2>&1
)
taskkill /FI "WINDOWTITLE eq MapleStory_079" /F >nul 2>&1

REM 等待进程完全退出
timeout /t 1 /nobreak >nul

echo [2/2] 启动服务端...
set PATH=%PATH%;%JAVA_HOME%\bin
set JRE_HOME=%JAVA_HOME%\jre
set CLASSPATH=%CLASSPATH%;./*;./lib/*

java -server -Dwzpath=wz com.github.mrzhqiang.maplestory.MapleStoryApplication
pause
