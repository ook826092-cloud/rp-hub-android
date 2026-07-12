@echo off
REM ==============================================================
REM RP-Hub 资源下载脚本 (Windows)
REM 用途：将 GitHub 上的 RP-Hub 前端文件下载到 Android 项目的 assets 目录
REM ==============================================================

setlocal enabledelayedexpansion

set ASSETS_DIR=%~dp0app\src\main\assets\rp-hub-web
set GITHUB_REPO=STA1N156/RP-Hub
set DOWNLOAD_URL=https://github.com/%GITHUB_REPO%/archive/refs/heads/main.zip
set TEMP_ZIP=%TEMP%\rphub-download.zip
set TEMP_DIR=%TEMP%\rphub-extract

echo ^>^^^> RP-Hub 资源下载工具
echo.

REM 清理旧资源
if exist "%ASSETS_DIR%" (
    echo [1/4] 清理旧资源...
    rmdir /s /q "%ASSETS_DIR%"
)

REM 下载（需要 curl 或手动下载）
echo [2/4] 从 GitHub 下载 RP-Hub...
where curl >nul 2>nul
if %errorlevel% == 0 (
    curl -L -o "%TEMP_ZIP%" "%DOWNLOAD_URL%"
) else (
    echo 请手动下载 RP-Hub: %DOWNLOAD_URL%
    echo 解压后把内容放到: %ASSETS_DIR%
    pause
    exit /b 1
)

REM 解压
echo [3/4] 解压...
if exist "%TEMP_DIR%" rmdir /s /q "%TEMP_DIR%"
mkdir "%TEMP_DIR%"
tar -xf "%TEMP_ZIP%" -C "%TEMP_DIR%"

REM 复制文件
echo [4/4] 复制到 assets...
mkdir "%ASSETS_DIR%"

set EXTRACT_DIR=%TEMP_DIR%\RP-Hub-main
copy "%EXTRACT_DIR%\index.html" "%ASSETS_DIR%\" >nul
xcopy "%EXTRACT_DIR%\assets" "%ASSETS_DIR%\assets" /E /I /Q >nul
xcopy "%EXTRACT_DIR%\character" "%ASSETS_DIR%\character" /E /I /Q >nul

REM 清理
del "%TEMP_ZIP%" >nul 2>nul
rmdir /s /q "%TEMP_DIR%" >nul

echo.
echo === 完成 ===
echo 资源已放置到: %ASSETS_DIR%
echo.
echo 下一步:
echo   1. cd rp-hub-android
echo   2. gradlew.bat assembleRelease
echo   3. APK: app\build\outputs\apk\release\
pause
