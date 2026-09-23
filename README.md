# 布布表情包转换器 (WebP to GIF Converter)

<p align="center">
  <b>一款超萌的布布表情包动图 WebP 转 GIF 转换器，支持动图解析、相册保存与一键快速分享</b>
</p>

---

## 📖 项目简介

**布布表情包转换器** (`webp2gif`) 是一款专为表情包爱好者与社交达人量身打造的 Android 工具应用。针对网络上广泛流行但部分平台兼容性受限的动态 WebP 表情包，应用提供毫秒级、帧准确的 WebP 转 GIF 转换服务，并自动保存至相册，支持直接一键分享至微信、QQ 等聊天应用。

- **应用包名**：`me.marukon.webp2gif`
- **开源仓库**：[https://github.com/Marukon/webp2gif](https://github.com/Marukon/webp2gif)

---

## ✨ 核心功能

1. **帧级 WebP 深度解析**
   - 原生支持 RIFF/WebP 格式二进制数据流解析（VP8X 扩展、ANIM 动画控制头与 ANMF 动画帧块）。
   - 提取各帧独立延迟时间（Duration）、画布混合方式与处置策略（Dispose），确保转换后动画节奏与原动图完全一致。

2. **高质量 GIF 编码拼装**
   - 针对动图多帧透明度与调色板色彩进行优化，支持流畅循环播放。
   - 转换过程后台协程执行，支持实时进度百分比反馈。

3. **实时动图预览**
   - 原 WebP 动画与转换后的 GIF 动图均支持原画实时动图渲染播放，所见即所得。

4. **自动保存与社交分享**
   - 转换成功后自动通过 `MediaStore` 将生成的 GIF 图像安全存入系统相册（DCIM/Pictures 目录）。
   - 集成 AndroidX `FileProvider`，支持直接将表情包一键分享发送至微信、QQ、钉钉等各大应用。

5. **萌系布布风格 UI**
   - 基于 **Jetpack Compose + Material 3** 全新现代声明式界面构建。
   - 采用布布粉嫩温柔色彩体系，支持边缘到边缘（Edge-to-Edge）全屏沉浸式体验。

---

## 🛠️ 技术栈

| 模块 | 技术选型 |
| :--- | :--- |
| **开发语言** | Kotlin 2.2.10 |
| **系统版本** | Min SDK 27 (Android 8.1) / Target SDK 36 (Android 16) |
| **构建工具** | Gradle 9.3.1 + Android Gradle Plugin (AGP) 9.1.1 |
| **JDK 版本** | Java 17 / Java 21 |
| **UI 架构** | Jetpack Compose + Material 3 (Edge-to-Edge) |
| **异步流与状态** | Kotlin Coroutines + StateFlow / ViewModel |
| **核心编解码** | 自研 WebPFrameExtractor (RIFF/VP8X Parser) + Glide AnimatedGifEncoder |
| **自动化集成** | GitHub Actions (CI/CD 自动编译、签名、打包、Telegram 部署) |

---

## 🚀 本地开发与构建

### 1. 环境要求
- [Android Studio](https://developer.android.com/studio) (建议 Ladybug / Meerkat 或更高版本)
- JDK 17 或 JDK 21
- Android SDK Platform 36

### 2. 编译步骤
```bash
# 克隆仓库
git clone https://github.com/Marukon/webp2gif.git
cd webp2gif

# 编译 Debug 版本
./gradlew :app:assembleDebug

# 编译 Release 版本
./gradlew :app:assembleRelease

# 检查项目依赖版本更新 (AGP, Gradle, AndroidX, 第三方库)
./gradlew dependencyUpdates
```

编译生成的 APK 位于：
- `app/build/outputs/apk/release/`

依赖版本检查报告位于：
- `build/dependencyUpdates/report.txt` (亦可通过 GitHub Actions 自动检查并在工作流摘要与 Telegram 中查看)

---

## 🤖 GitHub Actions CI/CD

项目内置了自动化编译工作流 [`.github/workflows/build-release.yml`](.github/workflows/build-release.yml)，每次推送到 `main` 分支或手动触发时，将自动构建 Release APK 并上传至 GitHub Artifacts。

### 可选 Secrets 配置
若需要自动签名及推送至 Telegram 频道/群组，可在 GitHub 仓库的 `Settings -> Secrets and variables -> Actions` 中配置以下变量：

| Secret 名称 | 说明 |
| :--- | :--- |
| `SIGNING_KEY` | Android 签名 keystore 文件的 Base64 编码字符串 |
| `ALIAS` | 签名密钥别名 (Key Alias) |
| `KEY_STORE_PASSWORD` | Keystore 访问密码 |
| `KEY_PASSWORD` | Key 访问密码 |
| `TELEGRAM_BOT_TOKEN` | *(可选)* Telegram 机器人 Token |
| `TELEGRAM_CHAT_ID` | *(可选)* Telegram 接收 APK 的目标 Chat ID |

*注：若未配置签名密钥，CI 仍会成功编译出未签名的标准 Release APK 并归档上传。*

---

## 📄 开源许可

本项目遵循 MIT 许可证开放源代码。
