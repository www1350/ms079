@echo off
@title MapleStory_079
setlocal enabledelayedexpansion

echo ========================================
echo   MapleStory v079 Server (CLI Mode)
echo ========================================

REM Kill existing server processes
echo [1/2] Cleaning old processes...
for /f "tokens=1" %%i in ('jps -l 2^>nul ^| findstr "GUIApplication MapleStoryApplication"') do (
    echo   Found process %%i, terminating...
    taskkill /F /PID %%i >nul 2>&1
)
taskkill /FI "WINDOWTITLE eq MapleStory_079" /F >nul 2>&1

REM Wait for processes to exit
timeout /t 1 /nobreak >nul

echo [2/2] Starting server...
set PATH=%PATH%;%JAVA_HOME%\bin
set JRE_HOME=%JAVA_HOME%\jre
set CLASSPATH=%CLASSPATH%;target/ms079.jar;lib/*

java -server -Dwzpath=wz com.github.mrzhqiang.maplestory.MapleStoryApplication
pause
