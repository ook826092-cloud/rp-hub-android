@echo off
REM ==============================================================
REM RP-Hub Android 一键部署脚本 (Windows)
REM ==============================================================

echo ===========================================
echo   RP-Hub Android 一键部署
echo ===========================================
echo.

REM 检查依赖
where gh >nul 2>nul
if %errorlevel% neq 0 (
    echo ❌ 未安装 GitHub CLI (gh)
    echo    安装方法: https://cli.github.com/
    echo    winget install GitHub.cli
    pause
    exit /b 1
)

where git >nul 2>nul
if %errorlevel% neq 0 (
    echo ❌ 未安装 git
    pause
    exit /b 1
)

gh auth status >nul 2>nul
if %errorlevel% neq 0 (
    echo ❌ 未登录 GitHub，请先运行: gh auth login
    pause
    exit /b 1
)

set REPO_NAME=rp-hub-android
set REPO_DESC=RP-Hub Android WebView App

REM 初始化 Git
echo [1/5] 初始化 Git...
if not exist ".git" (
    git init
    git checkout -b main
)
echo   ✓ Done

REM 创建 .gitignore
echo [2/5] 配置 .gitignore...
(
echo *.iml
echo .gradle
echo /local.properties
echo /.idea
echo /build
echo /app/build
echo *.apk
echo *.keystore
echo !debug.keystore
echo .DS_Store
) > .gitignore
echo   ✓ Done

REM 创建仓库
echo [3/5] 创建 GitHub 仓库...
gh repo view %REPO_NAME% >nul 2>nul
if %errorlevel% neq 0 (
    gh repo create %REPO_NAME% --public --description "%REPO_DESC%" --source=. --push
    echo   ✓ 仓库已创建
) else (
    echo   ✓ 仓库已存在
)
echo   ✓ Done

REM 获取远程 URL
for /f "tokens=*" %%i in ('gh repo view %REPO_NAME% -q ".sshUrl"') do set REMOTE_URL=%%i
git remote get-url origin >nul 2>nul
if %errorlevel% neq 0 (
    git remote add origin %REMOTE_URL%
) else (
    git remote set-url origin %REMOTE_URL%
)

REM 推送
echo [4/5] 推送代码...
git add -A
git commit -m "feat: 初始化 RP-Hub Android WebView 套壳项目" --allow-empty
git push -u origin main --force
echo   ✓ Done

echo.
echo ===========================================
echo   ✅ 部署完成！
echo ===========================================
echo.
echo   查看构建状态:
for /f "tokens=*" %%i in ('gh repo view %REPO_NAME% -q ".htmlUrl"') do echo   %%i/actions
echo.
pause
