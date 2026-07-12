#!/bin/bash
# ==============================================================
# RP-Hub 资源下载脚本
# 用途：将 GitHub 上的 RP-Hub 前端文件下载到 Android 项目的 assets 目录
# ==============================================================

set -e

ASSETS_DIR="$(cd "$(dirname "$0")/app/src/main/assets/rp-hub-web" && pwd)"
GITHUB_REPO="STA1N156/RP-Hub"
DOWNLOAD_URL="https://github.com/${GITHUB_REPO}/archive/refs/heads/main.zip"
TEMP_ZIP="/tmp/rp-hub-download.zip"
TEMP_DIR="/tmp/rp-hub-extract"

echo ">>> RP-Hub 资源下载工具"
echo ""

# 清理旧资源
if [ -d "$ASSETS_DIR" ]; then
    echo "[1/4] 清理旧资源..."
    rm -rf "$ASSETS_DIR"
fi

# 下载最新版本
echo "[2/4] 从 GitHub 下载 RP-Hub..."
curl -L -o "$TEMP_ZIP" "$DOWNLOAD_URL"

# 解压
echo "[3/4] 解压..."
rm -rf "$TEMP_DIR"
mkdir -p "$TEMP_DIR"
unzip -q "$TEMP_ZIP" -d "$TEMP_DIR"

# 复制核心文件到 assets（排除 README、LICENSE 等非必要文件）
echo "[4/4] 复制到 assets..."
mkdir -p "$ASSETS_DIR"

EXTRACT_DIR="${TEMP_DIR}/RP-Hub-main"

# 复制 index.html
cp "$EXTRACT_DIR/index.html" "$ASSETS_DIR/"

# 复制 assets 目录（CSS、JS）
if [ -d "$EXTRACT_DIR/assets" ]; then
    cp -r "$EXTRACT_DIR/assets" "$ASSETS_DIR/"
fi

# 复制 character 目录
if [ -d "$EXTRACT_DIR/character" ]; then
    cp -r "$EXTRACT_DIR/character" "$ASSETS_DIR/"
fi

# 清理临时文件
rm -rf "$TEMP_ZIP" "$TEMP_DIR"

echo ""
echo "=== 完成 ==="
echo "资源已放置到: $ASSETS_DIR"
echo "文件列表:"
ls -la "$ASSETS_DIR"
echo ""
echo "下一步："
echo "  1. cd rp-hub-android"
echo "  2. ./gradlew assembleRelease"
echo "  3. APK 位置: app/build/outputs/apk/release/"
