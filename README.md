# 安卓大模型语音与翻译输入法

[![Build Android IME](https://github.com/wanghongchao321/shurufa/actions/workflows/android-build.yml/badge.svg)](https://github.com/wanghongchao321/shurufa/actions/workflows/android-build.yml)

这是一个可导入 Android Studio 的 MVP：

- 键盘界面只有一个按钮。
- 短按依次切换：中文 → 英文 → 法语 → 中译法。
- 长按开始录音，松开后上传并上屏。
- 中文、英文、法语模式让同一个多模态模型按指定语言转写。
- 中译法模式在一次请求中直接把中文语音输出为法语。
- OpenRouter API Key 只保存在后端。

## 目录

```text
app/       Android IME 客户端
backend/   Node.js API 服务
```

## 1. 启动后端

需要 Node.js 20 或更高版本。

```bash
cd backend
npm install
cp .env.example .env
```

编辑 `.env`：

```dotenv
OPENROUTER_API_KEY=你的_OpenRouter_API_Key
OPENROUTER_MODEL=google/gemini-3.5-flash
OPENROUTER_SITE_URL=https://你的站点.example
OPENROUTER_APP_NAME=Voice Translate IME
IME_SHARED_TOKEN=请替换为随机长字符串
PORT=8787
```

然后运行：

```bash
npm test
npm start
```

健康检查：

```bash
curl http://localhost:8787/health
```

## 2. 配置 Android 客户端

编辑项目根目录的 `gradle.properties`：

```properties
IME_BACKEND_BASE_URL=http://10.0.2.2:8787/
IME_SHARED_TOKEN=与后端相同的随机字符串
```

`10.0.2.2` 是 Android Emulator 访问宿主机的地址。真机必须换成手机可访问的 HTTPS 地址。

Debug 构建允许 HTTP，便于本地模拟器调试；Release 构建禁止明文网络。

> `IME_SHARED_TOKEN` 只用于 MVP 的基本防滥用，APK 中的值可以被提取。生产环境应改成用户登录、设备证明、短期令牌、限流和服务端配额。

## 3. 构建与启用输入法

1. 用 Android Studio 打开本目录。
2. 安装 Android SDK 35 和 JDK 17。
3. 构建并安装 `app`。
4. 打开“语音翻译输入法”应用。
5. 授予麦克风权限。
6. 点击“启用输入法”，在系统设置中启用本输入法。
7. 点击“选择输入法”，切换到本输入法。

也可以在 GitHub 仓库的 **Actions → Build Android IME** 中手动运行构建，并从运行页面的 Artifacts 下载 `VoiceTranslateIme-debug-apk`。

使用方式：

- 短按按钮切换模式。
- 长按按钮说话。
- 松开按钮后等待处理并自动上屏。

## 4. API 流程

普通模式：

```text
Android M4A → POST /v1/ime/process → OpenRouter 音频模型 → commitText
```

翻译模式：

```text
中文 M4A → OpenRouter 单次音频请求直接输出法语 → commitText
```

默认模型为 `google/gemini-3.5-flash`。它支持音频输入，可用一个模型覆盖三语转写和中法翻译。若只追求纯转写准确率，可另行测试 `openai/gpt-4o-transcribe`，但翻译模式仍需要第二个文本模型。

后端在请求结束后删除临时音频。Android 客户端也会在成功或失败后删除本地缓存文件。

## 5. 上线前检查

- 使用 HTTPS，Release 构建不会接受 HTTP。
- 用正式用户认证替换共享令牌。
- 对上传大小、请求频率和单用户费用设置限制。
- 明确告知用户语音会发送到云端，并提供隐私政策。
- 用真实的中、英、法口音测试人名、数字、日期和中法翻译质量。
- API 返回期间若用户切换输入框，客户端会丢弃迟到结果，避免写入错误应用。

## 6. 后续升级为真正实时翻译

MVP 采用“松开后上传文件”。后续可以将 `MediaRecorder` 替换为 `AudioRecord`，把 24 kHz PCM16 音频流发送到 Realtime Translation，并用 `setComposingText()` 展示法语增量，结束时再 `finishComposingText()`。
