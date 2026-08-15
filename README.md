# 非洲王输入法

[![Build Android IME](https://github.com/wanghongchao321/shurufa/actions/workflows/android-build.yml/badge.svg)](https://github.com/wanghongchao321/shurufa/actions/workflows/android-build.yml)

这是一个不依赖自建后端的 Android IME。APK 录音后直接调用 OpenRouter：

- 上方三个按钮选择：中文、英文、法语；中→法独立模式已移除。
- 底部大按钮按下开始录音，松开后发送并上屏。
- 录音采用 16 kHz、单声道、24 kbps AAC，减少慢速网络下的上传等待。
- 三种模式的音频全部先由 OpenRouter 内的 `openai/gpt-transcribe` 自动识别语言并转写。
- 中文模式保留语音原语言，因此也可以显示英文、法语或其他语言；英文模式无论输入何种语言都返回英文；法语模式无论输入何种语言都返回法语。
- `openai/gpt-5.6-luna` 处理转写后的语法修正、翻译和上下文消歧，不直接接收音频。
- 发送按钮兼容普通输入框，并针对微信、WhatsApp 和 WhatsApp Business 处理发送动作。

## 安全说明

独立 APK 必须在客户端持有 OpenRouter API Key。即使使用混淆，攻击者仍可能从 APK 或运行时流量中提取 Key。建议：

- 为本应用创建独立 Key，不要复用主账号 Key。
- 在 OpenRouter 设置严格的额度和速率限制。
- 定期轮换 Key。
- 不要把 Key 提交到 Git 仓库。

## GitHub Actions 编译

在仓库中添加 Actions Secret：

1. 打开 **Settings → Secrets and variables → Actions**。
2. 新建 Repository secret，名称为 `SHURUFA`。
3. 值填写单独为本应用创建的 OpenRouter Key。
4. 打开 **Actions → Build Android IME → Run workflow**。
5. 构建成功后下载 `FeizhouWangIme-debug-apk`。

工作流在编译时通过 Gradle 属性把 Secret 写入 `BuildConfig.OPENROUTER_API_KEY`，Key 不会进入 Git 源码，但会存在于最终 APK 中。

## 本地编译

需要 Android SDK 35、JDK 17 和 Gradle 8.9：

```bash
gradle \
  -POPENROUTER_API_KEY="你的_OpenRouter_Key" \
  :app:assembleDebug
```

也可以在用户级 `~/.gradle/gradle.properties` 中设置：

```properties
OPENROUTER_API_KEY=你的_OpenRouter_Key
OPENROUTER_MODEL=openai/gpt-5.6-luna
```

不要把 Key 写入项目目录内受 Git 跟踪的 `gradle.properties`。

## 安装与启用

1. 安装生成的 Debug APK。
2. 打开“非洲王输入法”。
3. 授予麦克风权限。
4. 点击“启用输入法”，在系统设置中启用。
5. 点击“选择输入法”，切换到本输入法。
6. 点击上方模式按钮，再按住底部大按钮讲话，松开后等待上屏。

## 项目结构

```text
app/src/main/java/com/example/voicetranslateime/
├── VoiceImeService.kt   IME 界面、录音和上屏
├── OpenRouterApi.kt     手机直连 OpenRouter
├── ImeAudioRecorder.kt  M4A 录音
├── ImeModeStore.kt      模式持久化
└── PermissionActivity.kt 权限和输入法启用页
```
