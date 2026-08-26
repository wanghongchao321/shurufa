# 非洲王输入法（Xime + OpenRouter AI）

本项目直接以 [Xime](https://github.com/ximeiorg/Xime) 源码为键盘与 Rime 输入引擎基础，并集成原“非洲王输入法”的 OpenRouter 语音逻辑：

- 候选栏空闲时显示“中文、英文、法语、中英、中法”五种 AI 模式；开始文字输入后自动恢复 Xime 候选栏。
- 中文、中文转英文、中文转法语固定使用拼音九宫格；英文、法语固定使用 26 键 QWERTY，其他输入方案已移除。
- 仅发布 `arm64-v8a` APK，不生成 32 位、x86 或 universal 版本。
- 轻触空格正常输入空格；长按空格开始录音，松开后提交 OpenRouter 处理。
- 中文、中英、中法使用 `qwen/qwen3-asr-flash-2026-02-10`；中英/中法随后使用 Gemini 3.5 Flash Lite 翻译。
- 英文、法语并行调用 `google/chirp-3` 与 `openai/gpt-transcribe`，再由 `openai/gpt-5.6-luna` 校验输出。
- 录音为 16 kHz 单声道、32 kbps AAC/M4A，约 4 KB/秒。
- GitHub Actions 从仓库 Secret `SHURUFA` 注入 `OPENROUTER_API_KEY`。

本派生项目遵循上游 Xime 的 GNU GPLv3 许可证；上游版权和许可证文件均予以保留。

以下内容为 Xime 上游项目的原始说明，部分多方案功能在本 AI 精简版中已关闭。

---

<p align="center">
  <img src="docs/logo.jpg" alt="Xime Logo" width="600">
</p>

<h1 align="center">Xime - Wubi / Pinyin Input Method for Android</h1>

<p align="center">
  <a href="README.zh-CN.md">简体中文</a> · <a href="README.zh-TW.md">繁體中文</a>
</p>

[Windows Version](https://github.com/ximeiorg/winxime) | [Linux Version](https://github.com/ximeiorg/xime-wayland) | [Predictive Text Model](https://github.com/ximeiorg/predictive-text) | [Handwriting Model](https://github.com/ximeiorg/ochwpro)

An Android input method built on the [Rime](https://rime.im/) engine, designed for efficient Chinese text input with Wubi (五笔) and Pinyin support.

---

> This input method supports both Wubi (五笔) and Pinyin input. The author primarily uses Wubi with Pinyin as a fallback, so resources lean toward Wubi.

<table align="center">
  <tr>
    <td><img src="docs/Screenshot/full_keyboard_light.jpg" width="180"><br><p align="center">Full Keyboard (Light)</p></td>
    <td><img src="docs/Screenshot/full_keyboard_dark.jpg" width="180"><br><p align="center">Full Keyboard (Dark)</p></td>
    <td><img src="docs/Screenshot/全键盘_下滑_light.jpg" width="180"><br><p align="center">Radical Swipe</p></td>
    <td><img src="docs/Screenshot/shotcut_light.jpg" width="180"><br><p align="center">Quick Actions</p></td>
  </tr>
  <tr>
    <td><img src="docs/Screenshot/floating.jpg" width="180"><br><p align="center">Floating Keyboard</p></td>
    <td><img src="docs/Screenshot/t9_pinyin.jpg" width="180"><br><p align="center">T9 Pinyin</p></td>
    <td><img src="docs/Screenshot/number.jpg" width="180"><br><p align="center">Numpad</p></td>
    <td><img src="docs/Screenshot/symbol.jpg" width="180"><br><p align="center">Symbols</p></td>
  </tr>
  <tr>
    <td><img src="docs/Screenshot/hw.png" width="180"><br><p align="center">Handwriting</p></td>
    <td><img src="docs/Screenshot/hw2.png" width="180"><br><p align="center">Handwriting (Candidates)</p></td>
    <td><img src="docs/Screenshot/voice.jpg" width="180"><br><p align="center">Voice Input</p></td>
    <td><img src="docs/Screenshot/emoji.jpg" width="180"><br><p align="center">Emoji Keyboard</p></td>
  </tr>
  <tr>
    <td><img src="docs/Screenshot/theme_light.jpg" width="180"><br><p align="center">Theme Settings (Light)</p></td>
    <td><img src="docs/Screenshot/theme_dark.jpg" width="180"><br><p align="center">Theme Settings (Dark)</p></td>
    <td><img src="docs/Screenshot/plugin_light.jpg" width="180"><br><p align="center">Plugin Manager</p></td>
    <td><img src="docs/Screenshot/扩展商店.png" width="180"><br><p align="center">Extension Store</p></td>
  </tr>
</table>

## Features

- **Multiple Input Schemas** - Built-in Wubi 86/98, Pinyin, and mixed schemas; supports custom schemas (Shuangpin, Stroke, etc.) via the schema marketplace or wireless import
- **Rime Engine** - Powered by the mature and reliable Rime input method engine for accurate Chinese input
- **Rich Keyboard Layouts** - QWERTY full keyboard, T9 Pinyin, Stroke 9-key, Handwriting, Numpad (with calculator)
- **Floating Keyboard** - Floating card style with drag support, semi-transparent rounded design
- **Voice-to-Text** - Local offline ASR (built-in streaming zipformer2 engine) plus online ASR plugins (FunAsr, Volc, etc.)
- **AI Enhancement** - Transformer-based predictive text for faster input
- **Clean UI** - Material Design 3, light/dark themes with multiple color schemes
- **Keyboard Adjustment** - Adjustable keyboard height and position
- **Toolbar Customization** - Customizable toolbar button layout and functions
- **Haptic Feedback** - Adjustable sound and vibration intensity
- **Swipe Gestures** - Cursor movement, deletion, symbol input via swipe gestures
- **Clipboard Manager** - Clipboard history with quick send and pinning
- **Clipboard Sync** - Bidirectional clipboard sync with remote devices via plugins (WebDAV, ximed, etc.)
- **Candidate Coding Hints** - Display Wubi codes for candidates to aid learning
- **Radical Display** - Swipe down on keys to show Wubi radicals for memory aid
- **Physical Keyboard Support** - Floating candidate bar when using hardware/bluetooth keyboards
- **WebDAV Sync** - Backup and restore schemas and settings via WebDAV
- **Plugin Marketplace** - Extensible Lua plugins (emoji, clipboard sync, online ASR, etc.) via the built-in marketplace

## Requirements

- Android 9.0 (API 28) or later

## Installation

### Download

Choose the APK matching your device architecture:
- **arm64-v8a**: Modern phones (recommended for most users)
- **armeabi-v7a**: Older 32-bit phones
- **x86_64**: Emulators
- **universal**: All architectures (larger file size)

### From Releases

1. Download the latest APK from [Releases](https://github.com/ximeiorg/Xime/releases)
2. Install the application
3. Enable Xime in system input method settings
4. Set Xime as the current input method

### Plugins (Optional)

Plugins are Lua-script plugins (`.xipk` format), installable and enabled from the app's Settings > Extension Store:
- **kaomoji**: Kaomoji text emoticons
- **meme-bunny**: Funny bunny sticker pack (8 stickers)
- **xime-fluent-emoji**: Fluent UI 3D-style emoji plugin (222 curated 3D emojis, 9 categories)
- **funasr-asr**: Alibaba Bailian FunAsr online speech recognition
- **volc-asr**: Volcano Engine online speech recognition
- **webdav-clipboard-sync**: WebDAV-based clipboard sync
- **ximed-clipboard-sync**: ximed-service-based clipboard sync

For the full plugin list, see the [Plugin Center](https://ime.ximei.me/plugin-list.html), or browse and install directly from the app's Settings > Extension Store.

### Build from Source

1. Clone the project and build the APK
2. Install the application
3. Enable Xime in system input method settings
4. Set Xime as the current input method

## Documentation

For detailed documentation, visit [https://ime.ximei.me](https://ime.ximei.me).

## Building

```bash
# Clone with submodules
git clone --recursive https://github.com/ximeiorg/Xime.git

# Or initialize submodules in an existing clone
git submodule update --init --recursive

# Build Release APK
./gradlew assembleRelease
```

### AI Model Download

#### Predictive Text Model

- **Repository**: https://github.com/ximeiorg/predictive-text
- **Model**: https://www.modelscope.cn/models/bikeand/predictive-text-small
- **File**: `model_int8_dynamic.onnx` (~17MB)
- **Vocabulary**: `vocab.json`
- **Location**: `filesDir/` (app private directory root)
- **Function**: Transformer-based Chinese word prediction for intelligent candidate suggestions

#### Speech Recognition Model

- **Model**: https://www.modelscope.cn/models/bikeand/asr
- **File**: `sherpa-onnx-streaming-zipformer-zh-int8-2025-06-30.tar.bz2` (~132MB)
- **Function**: Streaming zipformer2 Chinese speech recognition (offline, on-device)

**Note**: All models can be downloaded directly from within the app (Settings > Smart Prediction / Speech Recognition) — no manual placement required.

## Tech Stack

- Kotlin
- Jetpack Compose
- Material Design 3
- Rime (librime)
- JNI (Native C++)

## Contributing

Contributions welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) before submitting a PR.

Core rules:
- **File an Issue first** — All changes require a prior discussion via an Issue
- **Minimal changes** — PRs must contain only the minimum changes needed
- **GPG signing** — All commits must be GPG-signed

## Acknowledgments

- [Rime](https://rime.im/) - Input method engine
- [Trime](https://github.com/osfans/trime) - Configuration reference
- [fcitx5-android](https://github.com/fcitx5-android/fcitx5-android) - Keyboard layout reference
- [onnxruntime](https://github.com/microsoft/onnxruntime) - ONNX inference runtime for predictive text and speech recognition models

## License

GPLv3 License
