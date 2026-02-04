<p align="center">
  <img src="https://raw.githubusercontent.com/ferranpons/llamatik/main/assets/llamatik-new-logo.png" alt="Llamatik Logo" width="150"/>
</p>

<h1 align="center">Llamatik</h1>

<p align="center">
  <b>Run LLMs locally on Android, iOS, and Desktop — using a single Kotlin API.</b>
</p>

<p align="center">
  Offline-first · Privacy-preserving · Kotlin Multiplatform
</p>

<p align="center">
  <a href="https://central.sonatype.com/artifact/com.llamatik/library"><img src="https://img.shields.io/maven-central/v/com.llamatik/library.svg" alt="maven central badge"/></a>
  <img src="https://img.shields.io/badge/Kotlin-Multiplatform-blueviolet" alt="kmp badge"/>
  <img src="https://img.shields.io/badge/Platforms-Android%20%7C%20iOS%20%7C%20Desktop-green" alt="platforms badge"/>
  <img src="https://img.shields.io/badge/LLM-llama.cpp-orange" alt="llama.cpp badge"/>
  <img src="https://img.shields.io/badge/License-MIT-lightgrey" alt="license badge"/>
</p>

---

## ✨ What is Llamatik?

**Llamatik** is a **Kotlin Multiplatform library** that lets you run **large language models locally**
using **llama.cpp**, with optional remote inference — all behind a **unified Kotlin API**.

No Python.  
No mandatory servers.  
Your models, your data, your device.

Designed for **privacy-first**, **offline-capable**, and **cross-platform** AI applications.

---

## 🚀 Features

<img align="right" width="0" height="368px" hspace="20"/>
<img src="assets/androidScreenshots/phoneScreenshot3.png" height="368px" align="right" />

### 🔐 On-device & Private
- ✅ Fully offline inference via **llama.cpp**
- ✅ No network, no data exfiltration
- ✅ Works with **GGUF** models (Mistral, Phi, LLaMA, etc.)

### 🧩 Kotlin Multiplatform
- ✅ Shared Kotlin API across **Android, iOS, Desktop**
- ✅ Native performance via Kotlin/Native + C++
- ✅ Lightweight, dependency-free runtime

### 🧠 LLM Capabilities
- ✅ Text generation (non-streaming & streaming)
- ✅ Context-aware generation (system + history)
- ✅ **Schema-constrained JSON generation**
- ✅ Embeddings for vector search & RAG

### 🌐 Hybrid & Remote
- ✅ Optional HTTP client for remote inference
- ✅ Drop-in backend server (`llamatik-backend`)
- ✅ Seamlessly switch between local and remote inference

---

## 📱 Try it now (No setup required)

Want to see Llamatik in action before integrating it?

The **Llamatik App** showcases:
- On-device inference
- Streaming generation
- Privacy-first AI (no cloud required)

<a href="https://play.google.com/store/apps/details?id=com.llamatik.app.android"><img src="assets/google-play-button.png" width="200px"/></a>
<a href="https://apple.co/3Md7EIh"><img src="assets/app-store-button.png" width="200px"/></a>

---

## 🔧 Use Cases

- 🧠 On-device chatbots & assistants
- 📚 Local RAG systems
- 🛰️ Hybrid AI apps (offline-first, online fallback)
- 🎮 Game AI & procedural dialogue

---

## 🧱 Architecture

```
Your App
│
▼
LlamaBridge (shared Kotlin API)
│
├─ llamatik-core     → Native llama.cpp (on-device)
├─ llamatik-client   → Remote HTTP inference
└─ llamatik-backend  → llama.cpp-compatible server
```

Switching between **local and remote inference requires no API changes** —
only configuration.

---

## 🔧 Requirements

- iOS Deployment Target: **16.6+**
- Android MinSDK API: **26**

## 📦 Current Versions

- llama.cpp version: [b7815](https://github.com/ggml-org/llama.cpp/releases/tag/b7815)

---

## 📦 Installation

Llamatik is published on **Maven Central** and follows **semantic versioning**.

- No custom Gradle plugins
- No manual native toolchain setup
- Works with standard Kotlin Multiplatform projects

### Repository setup

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

commonMain.dependencies {
    implementation("com.llamatik:library:0.15.0")
}
```

---

## ⚡ Quick Start

```kotlin
// Resolve model path (place GGUF in assets / bundle)
val modelPath = LlamaBridge.getModelPath("phi-2.Q4_0.gguf")

// Load model
LlamaBridge.initGenerateModel(modelPath)

// Generate text
val output = LlamaBridge.generate(
"Explain Kotlin Multiplatform in one sentence."
)
```

---

## 🧑‍💻 Library Usage

The public Kotlin API is defined in `LlamaBridge` (an `expect object` with platform-specific `actual` implementations).

### API surface

```kotlin
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object LlamaBridge {
    // Utilities
    @Composable
    fun getModelPath(modelFileName: String): String   // copy asset/bundle model to app files dir and return absolute path
    fun shutdown()                                    // free native resources

    // Embeddings
    fun initModel(modelPath: String): Boolean         // load embeddings model
    fun embed(input: String): FloatArray              // return embedding vector

    // Text generation (non-streaming)
    fun initGenerateModel(modelPath: String): Boolean // load generation model
    fun generate(prompt: String): String
    fun generateWithContext(
        systemPrompt: String,
        contextBlock: String,
        userPrompt: String
    ): String

    // Text generation (streaming)
    fun generateStream(prompt: String, callback: GenStream)
    fun generateStreamWithContext(
        systemPrompt: String,
        contextBlock: String,
        userPrompt: String,
        callback: GenStream
    )

    // Text generation with JSON schema (non-streaming)
    fun generateJson(prompt: String, jsonSchema: String? = null): String
    fun generateJsonWithContext(
        systemPrompt: String,
        contextBlock: String,
        userPrompt: String,
        jsonSchema: String? = null
    ): String

    // Convenience streaming overload (callbacks)
    fun generateStream(prompt: String, callback: GenStream)
    fun generateStreamWithContext(
        system: String,
        context: String,
        user: String,
        onDelta: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    )
    
    // Text generation with JSON schema (streaming)
    fun generateJsonStream(prompt: String, jsonSchema: String? = null, callback: GenStream)
    fun generateJsonStreamWithContext(
        systemPrompt: String,
        contextBlock: String,
        userPrompt: String,
        jsonSchema: String? = null,
        callback: GenStream
    )

    fun nativeCancelGenerate()                        // cancel generation
}

interface GenStream {
    fun onDelta(text: String)
    fun onComplete()
    fun onError(message: String)
}
```

## 🧑‍💻 Backend Usage

Please go to the [Backend README.md](./backend/README.md) for more information.

---

## 🔍 Why Llamatik?

- ✅ Built directly on llama.cpp
- ✅ Offline-first & privacy-preserving
- ✅ No runtime dependencies
- ✅ Open-source (MIT)
- ✅ Used by real Android & iOS apps
- ✅ Designed for long-term Kotlin Multiplatform support

---

## 📦 Apps using Llamatik

Llamatik is already used in production apps on Google Play and App Store.

Want to showcase your app here?
Open a PR and add it to the list 🚀

---

## 🤝 Contributing

Llamatik is 100% open-source and actively developed.
- Bug reports
- Feature requests
- Documentation improvements
- Platform extensions

All contributions are welcome!

---

## 📜 License

This project is licensed under the MIT License.<br>
See [LICENSE](./LICENSE) for details.

---

Built with ❤️ for the Kotlin community.
