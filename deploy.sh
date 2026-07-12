#!/bin/bash
# ==============================================================
# RP-Hub Android 一键部署脚本
# 
# 功能：
#   1. 在 GitHub 上创建你的仓库
#   2. 生成 gradlew
#   3. 推送代码到 GitHub
#   4. 触发首次自动构建
#
# 使用前需要：
#   - 安装 git
#   - 安装 gh (GitHub CLI): https://cli.github.com/
#   - 运行: gh auth login
# ==============================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# ---- 配置 ----
# 改成你想要的仓库名和描述
REPO_NAME="${1:-rp-hub-android}"
REPO_DESC="RP-Hub Android WebView App - 自动构建上游 RP-Hub 为安卓 APK"

echo "==========================================="
echo "  RP-Hub Android 一键部署"
echo "==========================================="
echo ""

# ---- 0. 检查依赖 ----
echo "[0/5] 检查环境..."

if ! command -v gh &> /dev/null; then
    echo "❌ 未安装 GitHub CLI (gh)"
    echo "   安装方法: https://cli.github.com/"
    echo "   macOS: brew install gh"
    echo "   Linux: https://github.com/cli/cli/releases"
    exit 1
fi

if ! command -v git &> /dev/null; then
    echo "❌ 未安装 git"
    exit 1
fi

# 检查 gh 登录状态
if ! gh auth status &> /dev/null; then
    echo "❌ 未登录 GitHub，请先运行: gh auth login"
    exit 1
fi

echo "  ✓ GitHub CLI 已登录: $(gh api user -q '.login')"

# ---- 1. 初始化 git ----
echo ""
echo "[1/5] 初始化 Git..."

if [ ! -d ".git" ]; then
    git init
    git checkout -b main
    echo "  ✓ Git 仓库初始化完成"
else
    echo "  ✓ 已是 Git 仓库"
fi

# ---- 2. 生成 Gradle Wrapper ----
echo ""
echo "[2/5] 生成 Gradle Wrapper..."

if [ ! -f "gradlew" ]; then
    if command -v gradle &> /dev/null; then
        gradle wrapper --gradle-version 8.2
    else
        # 备用：手动下载 gradle wrapper
        mkdir -p gradle/wrapper
        curl -sL -o gradle/wrapper/gradle-wrapper.jar \
            "https://raw.githubusercontent.com/gradle/gradle/master/gradle/wrapper/gradle-wrapper.jar"
        curl -sL -o gradle/wrapper/gradle-wrapper.properties \
            "https://raw.githubusercontent.com/gradle/gradle/v8.2.0/gradle/wrapper/gradle-wrapper.properties"
        curl -sL -o gradlew \
            "https://raw.githubusercontent.com/gradle/gradle/v8.2.0/gradlew"
        chmod +x gradlew
    fi
    echo "  ✓ Gradle Wrapper 生成完成"
else
    echo "  ✓ gradlew 已存在"
fi

# ---- 3. 创建 .gitignore ----
echo ""
echo "[3/5] 配置 .gitignore..."

cat > .gitignore << 'GITIGNORE'
*.iml
.gradle
/local.properties
/.idea
/build
/app/build
/captures
.externalNativeBuild
.cxx
*.apk
*.ap_
*.dex
*.keystore
!debug.keystore
/gradle/wrapper/gradle-wrapper.jar
/app/debug.keystore
.DS_Store
GITIGNORE

echo "  ✓ .gitignore 已创建"

# ---- 4. 创建 GitHub 仓库 ----
echo ""
echo "[4/5] 创建 GitHub 仓库..."

if gh repo view "$REPO_NAME" &> /dev/null; then
    echo "  ✓ 仓库已存在: https://github.com/$(gh repo view $REPO_NAME -q '.full_name')"
else
    gh repo create "$REPO_NAME" \
        --public \
        --description "$REPO_DESC" \
        --source=. \
        --push
    echo "  ✓ 仓库已创建并推送代码"
fi

# 获取远程 URL
REMOTE_URL=$(gh repo view "$REPO_NAME" -q '.sshUrl')

# 确保远程仓已配置
if git remote get-url origin &> /dev/null; then
    git remote set-url origin "$REMOTE_URL"
else
    git remote add origin "$REMOTE_URL"
fi

# ---- 5. 推送并触发构建 ----
echo ""
echo "[5/5] 推送代码..."

git add -A
git commit -m "feat: 初始化 RP-Hub Android WebView 套壳项目

- WebView 全屏沉浸式
- NanoHTTPD 本地 HTTP 服务器
- GitHub Actions 自动构建 & 发布
- 自动检测上游更新" \
    --allow-empty

git push -u origin main --force

echo ""
echo "==========================================="
echo "  ✅ 部署完成！"
echo "==========================================="
echo ""
echo "  仓库地址: https://github.com/$(gh repo view $REPO_NAME -q '.full_name')"
echo ""
echo "  自动构建已触发，请稍等几分钟..."
echo "  查看构建状态:"
echo "  https://github.com/$(gh repo view $REPO_NAME -q '.full_name')/actions"
echo ""
echo "  构建完成后，APK 将在 Releases 页面:"
echo "  https://github.com/$(gh repo view $REPO_NAME -q '.full_name')/releases"
echo ""
echo "  每天零点自动检查上游更新并重新构建"
