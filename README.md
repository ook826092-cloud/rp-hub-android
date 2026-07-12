# RP-Hub Android

将 [RP-Hub](https://github.com/STA1N156/RP-Hub) 自动打包为安卓 APK，基于 WebView + NanoHTTPD 本地服务器方案。

**零手动操作 — 上游有提交就自动构建发 APK。**

## 工作原理

```
每天零点 UTC ──→ 检测上游 STA1N156/RP-Hub 是否有新提交
                    │
                    ├── 有更新 → 拉取前端代码 → 编译 APK → 发布 GitHub Release
                    │
                    └── 无更新 → 跳过
```

前端代码直接打包进 APK，不需要运行时下载。APK 打开后通过内置 NanoHTTPD 在 `localhost:8080` 提供服务。

## 一键部署

只需要你的电脑上有 `git` 和 `gh`（GitHub CLI）：

```bash
# 1. 安装 GitHub CLI（如果没有）
# macOS
brew install gh
# Windows
winget install GitHub.cli

# 2. 登录 GitHub
gh auth login

# 3. 运行部署脚本
cd rp-hub-android
./deploy.sh          # Linux / macOS
deploy.bat           # Windows
```

脚本会自动：
- 创建你的 GitHub 仓库
- 推送所有代码
- 触发首次自动构建

## 手动触发构建

在仓库页面 → Actions → Build RP-Hub Android → Run workflow

可以勾选「强制构建」跳过上游检测直接编译。

## APK 位置

构建完成后，APK 在仓库的 **Releases** 页面，直接下载安装即可。

Release 自动清理，只保留最近 5 个版本。

## 更新频率

- **自动**：每天零点（UTC）检查上游，24 小时内有提交就自动构建
- **手动**：GitHub Actions 页面手动触发

## 项目结构

```
rp-hub-android/
├── .github/workflows/build.yml   # 自动构建 workflow
├── app/src/main/
│   ├── java/com/rphub/android/
│   │   ├── MainActivity.kt       # WebView 全屏 + 沉浸式
│   │   └── LocalWebServer.kt    # NanoHTTPD 本地服务器
│   ├── assets/rp-hub-web/       # RP-Hub 前端（构建时自动拉取）
│   ├── AndroidManifest.xml
│   └── res/values/styles.xml
├── app/build.gradle              # App 级构建配置
├── deploy.sh                     # 一键部署（Linux/Mac）
├── deploy.bat                    # 一键部署（Windows）
└── download-rphub.sh             # 手动下载前端（调试用）
```

## 技术细节

| 特性 | 说明 |
|------|------|
| 最低系统 | Android 7.0 (API 24) |
| WebView | 全屏沉浸式，DOM Storage 启用 |
| HTTP 服务 | NanoHTTPD，localhost:8080 |
| 签名 | CI 自动生成 debug 签名 |
| APK 体积 | 约 10-15 MB（含前端） |
| ProGuard | Release 开启混淆压缩 |

## 许可

本项目套壳代码可自由使用。RP-Hub 本体遵循 [CC BY-NC 4.0](https://creativecommons.org/licenses/by-nc/4.0/)。
