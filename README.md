# 🌌 KrizRP

<div align="center">

![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20%26%20M3-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)
[![Author](https://img.shields.io/badge/Author-altkriz-purple?style=for-the-badge&logo=github)](https://github.com/altkriz)
[![Repository](https://img.shields.io/badge/GitHub-altkriz%2Fkrizrp-blue?style=for-the-badge&logo=github)](https://github.com/altkriz/krizrp)

### *The next-generation, privacy-focused native Android client for AI Roleplay, Character Chat, and Tavern V2 Cards.*

**Built with passion by [@altkriz](https://github.com/altkriz)**

[Features](#-key-features) • [Chub URL Import](#-chub-url-import) • [Providers](#-supported-ai-backends) • [Architecture](#-architecture) • [Getting Started](#-getting-started) • [Author & Socials](#-author--socials)

---

</div>

## 🌟 Overview

**KrizRP** is an ultra-fast, modern, native Android application engineered for AI roleplayers, creative writers, and conversational AI enthusiasts. Designed from the ground up using **Kotlin**, **Jetpack Compose**, and **Material Design 3**, KrizRP connects to your favorite local or cloud language models while maintaining 100% offline data sovereignty.

Whether you want to explore community characters from **Chub.ai**, import standard **Tavern V2 PNG character cards**, or create your own detailed personas with custom system prompts, KrizRP delivers a seamless, beautiful, and feature-packed experience.

---

## ✨ Key Features

### 🔗 Direct Chub.ai Character URL Import
- **Instant 1-Click Import**: Paste any Chub link (e.g., `https://chub.ai/characters/Anonymous/anonymouschar` or `characterhub.org/...`).
- **Resilient Multi-Gateway Fallback**: Queries `gateway.chub.ai`, `chub.ai`, and `ro.chub.ai` automatically to resolve cards even during cloud outages.
- **Full Card Parsing**: Downloads character definition, scenario, greetings, system prompts, mes_examples, and high-res avatar artwork directly into your local database.

### 🎨 Integrated Chub.ai Gallery & Author Following
- **Discover & Trending**: Browse trending community characters directly in-app.
- **Author Profiles & Favorites**: Follow your favorite bot creators (`@username`) and filter characters created by them.
- **Content Filter**: Easy NSFW toggle to curate your browsing experience.

### 🃏 Tavern V2 & Character Card Specification
- **Lossless PNG & JSON Support**: Import and export Character Card V2 PNGs with embedded `chara` base64 chunk metadata.
- **Deep Character Editor**: Customize Name, Personality, Scenario, First Message, Example Dialogue (`<START>`), System Prompt, and Post-History Instructions.
- **Alternate Greetings & Tagging**: Organize your roster with custom genre chips and search across your library instantly.

### 💬 Immersive Chat & Roleplay Engine
- **Rich Markdown & Dialogue Formatting**: Elegantly formatted speech quotes, narrative actions, and syntax-highlighted code blocks.
- **Message Branching & Swipes**: Swipe or regenerate AI responses, edit prior messages, and delete unwanted branches.
- **Multiple Personas**: Create custom user personas (name, avatar, description) and swap between them in any conversation.

### 🔌 Multi-Provider AI Engine (Local & Cloud)
Connect to whatever backend powers your roleplay:
- **Google Gemini**: Gemini 1.5 Flash, 1.5 Pro, 2.0 Flash (with native streaming support)
- **OpenAI**: GPT-4o, GPT-4o-mini, GPT-4-turbo
- **OpenRouter**: Access hundreds of open-source and proprietary models (Claude, Llama 3, Mistral, Command R+, etc.)
- **KoboldCPP & Ollama**: Self-hosted on-device or LAN local inference
- **Text-Generation-WebUI & Oobabooga**: Full API compatibility
- **Generic OpenAI-Compatible Endpoints**: Groq, Together AI, Mistral, DeepSeek, Perplexity, or custom reverse proxies

### 🎛️ Precision Sampler & Instruct Control
- Fine-tune Temperature, Top-P, Top-K, Repetition Penalty, Max Tokens, Frequency & Presence Penalty.
- Configurable Stop Sequences and Chat Instruct templates (Llama-3, ChatML, Alpaca, Mistral).

### 🔒 100% Privacy & Local Storage
- Local **Room SQLite** persistence.
- Zero analytics, zero telemetry tracking.
- Your keys and chats never touch third-party servers outside of your chosen AI provider.

---

## 🚀 Chub URL Import

Importing any character from Chub takes just seconds:

```
https://chub.ai/characters/Anonymous/anonymouschar
```

1. Tap the **Link / Import URL** button on the home screen banner or Top Bar.
2. Paste the link or tap **Paste from Clipboard**.
3. Tap **Import Character** — KrizRP automatically fetches metadata, resolves the card payload, caches the avatar, and opens your chat ready to play!

---

## 🤖 Supported AI Backends

| Provider | Supported Models | Streaming | Connection Mode |
| :--- | :--- | :---: | :--- |
| **Google Gemini** | `gemini-1.5-flash`, `gemini-1.5-pro`, `gemini-2.0-flash` | ✅ | Cloud API Key |
| **OpenAI** | `gpt-4o`, `gpt-4o-mini`, `gpt-4-turbo`, `gpt-3.5-turbo` | ✅ | Cloud API Key |
| **OpenRouter** | Any OpenRouter model ID (`meta-llama/llama-3.3-70b-instruct`, etc.) | ✅ | Cloud API Key |
| **KoboldCPP** | Any loaded GGUF model | ✅ | Local / LAN URL |
| **Ollama** | `llama3`, `mistral`, `gemma2`, `qwen2.5`, etc. | ✅ | Local / LAN URL |
| **Text-Gen WebUI** | Any model loaded in oobabooga | ✅ | Local / LAN URL |
| **Custom OpenAI API** | Groq, DeepSeek, Mistral, Together, vLLM, LM Studio | ✅ | Custom Base URL + Key |

---

## 🛠️ Architecture & Tech Stack

KrizRP is built with modern Android engineering standards:

- **Kotlin 2.0+** with Coroutines & Flows
- **Jetpack Compose** & **Material Design 3 (M3)** with dynamic colors and fluid animations
- **Room Database** for high-performance offline persistence
- **OkHttp 4** & **Retrofit 2** with real-time SSE (Server-Sent Events) streaming
- **Coil 3** for optimized, asynchronous image rendering
- **Kotlinx Serialization** for type-safe JSON decoding and Tavern card payloads
- **MVVM Architecture** with unidirectional data flow and clean separation of concerns

---

## 💻 Getting Started

### Prerequisites
- Android Studio Ladybug (or newer)
- JDK 17 or JDK 21
- Android SDK 35 (minSdk 26 - Android 8.0+)

### Building from Source

```bash
# 1. Clone repository
git clone https://github.com/altkriz/krizrp.git
cd krizrp

# 2. Build debug APK using Gradle
gradle assembleDebug

# 3. The APK will be generated at:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 👤 Author & Socials

Created with ❤️ by **altkriz**

- **GitHub Profile**: [@altkriz](https://github.com/altkriz)
- **Project Repository**: [altkriz/krizrp](https://github.com/altkriz/krizrp)

If you enjoy using **KrizRP**, please consider giving the repository a ⭐ **Star** on GitHub and sharing it with the roleplay community!

---

## 📄 License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
