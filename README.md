# 安卓定位跟踪客户端

轻量化安卓 APP，支持后台定位和自动上报。

## 功能特性

- 📍 高精度 GPS 定位
- ⏰ 定时上报模式（可选 10秒/30秒/1分钟/5分钟）
- 📏 位移触发模式（可选 10m/30m/50m/100m）
- 💾 离线缓存（最多 100 条）
- 🔄 断网自动补发
- 🔐 HTTPS + 签名验证
- 🔋 前台服务保活

## 系统要求

- Android 8.0 (API 26) 及以上
- 需要位置权限（前台 + 后台）

## 在线编译方法

由于您没有本地 Android 开发环境，推荐使用以下在线编译服务：

### 方法一：使用 GitHub Actions（推荐）

1. 将 `android` 目录上传到 GitHub 仓库
2. 创建 `.github/workflows/build.yml` 文件：

```yaml
name: Build APK

on:
  push:
    branches: [ main ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          
      - name: Build APK
        run: ./gradlew assembleRelease
        
      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: app-release
          path: app/build/outputs/apk/release/*.apk
```

3. 推送代码后，在 GitHub Actions 页面下载编译好的 APK

### 方法二：使用在线 IDE

1. 访问 [Gitpod](https://gitpod.io/) 或 [GitHub Codespaces](https://github.com/codespaces)
2. 导入项目后运行：
   ```bash
   ./gradlew assembleDebug
   ```
3. 下载 `app/build/outputs/apk/debug/app-debug.apk`

## 配置说明

### 服务器地址

在应用设置中修改，或在代码中修改默认值：

```kotlin
// data/SettingsManager.kt
const val DEFAULT_SERVER_URL = "http://您的服务器IP:8000"
```

### API 签名密钥

需要与服务端配置保持一致：

```kotlin
// data/SettingsManager.kt 中的 apiSecret
// 或在应用中通过设置修改
```

## 项目结构

```
android/
├── app/
│   ├── src/main/
│   │   ├── java/com/tracker/location/
│   │   │   ├── MainActivity.kt          # 主界面
│   │   │   ├── TrackerApplication.kt    # 应用类
│   │   │   ├── data/
│   │   │   │   ├── SettingsManager.kt   # 设置管理
│   │   │   │   ├── LocationRecord.kt    # 数据模型
│   │   │   │   └── OfflineCache.kt      # 离线缓存
│   │   │   ├── network/
│   │   │   │   └── LocationUploader.kt  # 网络上报
│   │   │   ├── service/
│   │   │   │   └── LocationService.kt   # 定位服务
│   │   │   └── receiver/
│   │   │       └── BootReceiver.kt      # 开机启动
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   ├── values/
│   │   │   └── drawable/
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
└── settings.gradle.kts
```

## 权限说明

| 权限 | 用途 |
|------|------|
| ACCESS_FINE_LOCATION | 获取精确位置 |
| ACCESS_BACKGROUND_LOCATION | 后台持续定位 |
| FOREGROUND_SERVICE | 前台服务保活 |
| INTERNET | 网络通信 |
| RECEIVE_BOOT_COMPLETED | 开机自启动 |
