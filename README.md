# 非洲王输入法

[![Build Android IME](https://github.com/wanghongchao321/shurufa/actions/workflows/android-build.yml/badge.svg)](https://github.com/wanghongchao321/shurufa/actions/workflows/android-build.yml)

这是一个不依赖自建后端的 Android IME。APK 录音后直接调用 OpenRouter：

- 功能区共八个按钮、每行四个：第一行“中文、英文、法语、中英”，第二行“中法、删除、清空、发送”。
- 底部大按钮按下开始录音，松开后发送并上屏。
- 录音采用 16 kHz、单声道、32 kbps AAC，约 4 KB/秒，在保持快速上传的同时保留更多口音语音细节。
- 中文、英文、中英、中法使用 OpenRouter 内的 `openai/gpt-transcribe` 转写。
- 音频按 OpenRouter 当前 STT 接口要求以 Base64 JSON 上传；请求失败时按钮会保持橙色并显示原因，按住即可重试。
- 中文模式自动检测输入语言并直接上屏；英文模式固定按英语识别。
- 法语模式把同一段音频并行发送给 `google/chirp-3` 和 `openai/gpt-transcribe`，两路都固定 `language=fr`；随后由 `openai/gpt-5.6-luna` 比较一致与冲突词、结合上下文裁决并只输出最终法语。
- 中英模式固定识别中文后翻译成英文；中法模式固定识别中文后翻译成法语。
- `google/gemini-3.5-flash-lite` 只处理英文语法修正和中英/中法翻译，不直接接收音频；法语校验改由 GPT-5.6 Luna 完成。
- 中文模式只调用一次转写接口；其他模式的 Lite 请求按最低延迟提供商路由，并限制输出长度。
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
OPENROUTER_MODEL=google/gemini-3.5-flash-lite
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
