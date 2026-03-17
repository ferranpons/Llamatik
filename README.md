<p align="center">
  <img src="https://raw.githubusercontent.com/ferranpons/llamatik/main/assets/llamatik-new-logo.png" alt="Llamatik Logo" width="150"/>
</p>

<h1 align="center">Llamatik</h1>

<p align="center">
  <b>Run AI locally on Android, iOS, Desktop and WASM — using a single Kotlin API.</b>
</p>

<p align="center">
  Offline-first · Privacy-preserving · True Kotlin Multiplatform
</p>

<p align="center">
  <a href="https://central.sonatype.com/artifact/com.llamatik/library"><img src="https://img.shields.io/maven-central/v/com.llamatik/library.svg" alt="maven central badge"/></a>
  <img src="https://img.shields.io/badge/Kotlin-Multiplatform-blueviolet" alt="kmp badge"/>
  <img src="https://img.shields.io/badge/Platforms-Android%20%7C%20iOS%20%7C%20Desktop%20%7C%20WASM-green" alt="platforms badge"/>
  <img src="https://img.shields.io/badge/LLM-llama.cpp-orange" alt="llama.cpp badge"/>
  <img src="https://img.shields.io/badge/STT-whisper.cpp-blue" alt="whisper.cpp badge"/>
  <img src="https://img.shields.io/badge/Image-stablediffusion.cpp-purple" alt="stablediffusion badge"/>
  <img src="https://img.shields.io/badge/License-MIT-lightgrey" alt="license badge"/>
</p>

---

## ✨ What is Llamatik?

**Llamatik** is a true Kotlin Multiplatform AI library that lets you run:

- 🧠 **Large Language Models (LLMs)** via `llama.cpp`
- 🎙 **Speech-to-Text (STT)** via `whisper.cpp`
- 🎨 **Image Generation** via `stable-diffusion.cpp`

Fully **on-device**, optionally remote — all behind a **unified Kotlin API**.

No Python.  
No required servers.  
Your models, your data, your device.

Designed for **privacy-first**, **offline-capable**, and **cross-platform** AI applications.

---

## 🚀 Features

<img align="right" width="0" height="368px" hspace="20"/>
<img src="assets/androidScreenshots/phoneScreenshot3.png" height="368px" align="right" />

### 🔐 On-device & Private
- ✅ Fully offline inference via **llama.cpp**
- ✅ On-device speech recognition via **whisper.cpp**
- ✅ No network required
- ✅ No data exfiltration
- ✅ Works with **GGUF** (LLMs) and **BIN** (Whisper) models

### 🧠 LLM (llama.cpp)
- ✅ Text generation (non-streaming & streaming)
- ✅ Context-aware generation (system + history)
- ✅ **Schema-constrained JSON generation**
- ✅ Embeddings for vector search & RAG

### 🎙 Speech-to-Text (whisper.cpp)
- ✅ On-device transcription
- ✅ Works fully offline
- ✅ 16kHz mono WAV support
- ✅ Selectable Whisper models
- ✅ Integrated model download + management

### 🎨 Image Generation (stable-diffusion.cpp)

- ✅ On-device Stable Diffusion inference
- ✅ Text-to-image generation
- ✅ Fully offline
- ✅ Works with optimized SD models
- ✅ Native C++ integration

### 🧩 Kotlin Multiplatform
- ✅ Shared API across **Android, iOS, Desktop**
- ✅ Native C++ integration via Kotlin/Native
- ✅ Static frameworks for iOS
- ✅ JNI for Desktop

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
- Speech-to-text (Whisper)
- Privacy-first AI (no cloud required)
- Downloadable models

<a href="https://play.google.com/store/apps/details?id=com.llamatik.app.android"><img src="assets/google-play-button.png" width="200px"/></a>
<a href="https://apple.co/3Md7EIh"><img src="assets/app-store-button.png" width="200px"/></a>

---

## 🔧 Use Cases

- 🧠 On-device chatbots & assistants
- 📚 Local RAG systems
- 🛰️ Hybrid AI apps (offline-first, online fallback)
- 🎮 Game AI & procedural dialogue

---

## 🧱 Architecture (WIP)

```
Your App
│
▼
LlamaBridge (shared Kotlin API)
│
├─ llamatik-core     → Native llama.cpp, whisper.cpp and stablediffusion.cpp (on-device)
├─ llamatik-client   → Remote HTTP inference
└─ llamatik-backend  → llama.cpp-compatible server
```

Switching between **local and remote inference requires no API changes** —
only configuration.

---

## 🔧 Requirements

- iOS Deployment Target: **16.6+**
- Android MinSDK API: **26**
- Desktop: JVM 21+
- WASM: Modern browser with WebAssembly support

## 📦 Current Versions

- llama.cpp version: [b7815](https://github.com/ggml-org/llama.cpp/releases/tag/b7815)
- whisper.cpp version [v1.8.3](https://github.com/ggml-org/whisper.cpp/releases/tag/v1.8.3)
- stablediffusion.cpp version [master-504-636d3cb](https://github.com/leejet/stable-diffusion.cpp/releases/tag/master-504-636d3cb)

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
    implementation("com.llamatik:library:0.17.0")
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

### API surface (LlamaBridge)

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

### Speech-to-Text (WhisperBridge)

WhisperBridge exposes a small, platform-friendly wrapper around whisper.cpp for on-device speech-to-text.

The workflow is:
1.	Download a Whisper ggml model (e.g. ggml-tiny-q8_0.bin) to local storage (the app does this for you).
2.	Initialize Whisper once with the local model path.
3.	Record audio to a WAV file and transcribe it.

### Whisper API surface

```kotlin
object WhisperBridge {
    /** Returns a platform-specific absolute path for the model filename. */
    fun getModelPath(modelFileName: String): String

    /** Loads the model at [modelPath]. Returns true if loaded. */
    fun initModel(modelPath: String): Boolean

    /**
     * Transcribes a WAV file and returns text.
     * Tip: record WAV as 16 kHz, mono, 16-bit PCM for best compatibility.
     */
    fun transcribeWav(wavPath: String, language: String? = null): String

    /** Frees native resources. */
    fun release()
}
```

#### Example

```kotlin
import com.llamatik.library.platform.WhisperBridge

val modelPath = WhisperBridge.getModelPath("ggml-tiny-q8_0.bin")

// 1) Init once (e.g. app start)
WhisperBridge.initModel(modelPath)

// 2) Record to a WAV file (16kHz mono PCM16) using your own recorder
val wavPath: String = "/path/to/recording.wav"

// 3) Transcribe
val text = WhisperBridge.transcribeWav(wavPath, language = null).trim()
println(text)

// 4) Optional: release on app shutdown
WhisperBridge.release()
```

**Note**: WhisperBridge expects a WAV file path. Llamatik’s app uses AudioRecorder + AudioPaths.tempWavPath() to generate the WAV before calling transcribeWav(...).


### 🎨 Image Generation (StableDiffusionBridge)

Llamatik exposes Stable Diffusion through StableDiffusionBridge.

Workflow
1.	Download or bundle a Stable Diffusion model.
2.	Initialize once.
3.	Generate images from text prompts.

### Stable-Diffusion API surface

```kotlin
object StableDiffusionBridge {

    /** Returns absolute model path (copied from assets/bundle if needed). */
    fun getModelPath(modelFileName: String): String

    /** Loads the Stable Diffusion model. */
    fun initModel(modelPath: String): Boolean

    /**
     * Generates an image from a prompt.
     *
     * @param prompt Text prompt
     * @param width Output width
     * @param height Output height
     * @param steps Inference steps
     * @param cfgScale Guidance scale
     * @return PNG image as ByteArray
     */
    fun generateImage(
        prompt: String,
        width: Int = 512,
        height: Int = 512,
        steps: Int = 20,
        cfgScale: Float = 7.5f
    ): ByteArray

    /** Releases native resources */
    fun release()
}
```

#### Example

```kotlin
import com.llamatik.library.platform.StableDiffusionBridge

val modelPath = StableDiffusionBridge.getModelPath("sd-model.bin")

StableDiffusionBridge.initModel(modelPath)

val imageBytes = StableDiffusionBridge.generateImage(
    prompt = "A cyberpunk llama in neon Tokyo",
    width = 512,
    height = 512
)

// Save imageBytes as PNG file
```

## 🧑‍💻 Backend Usage

The Llamatik backend server is now maintained in a dedicated repository.

👉 Llamatik Server Repository
[https://github.com/ferranpons/Llamatik-Server](https://github.com/ferranpons/Llamatik-Server)

Visit the repository for full setup instructions, configuration options, and usage details.

---

## 🔍 Why Llamatik?

- ✅ Built directly on llama.cpp, whisper.cpp and stable-diffusion.cpp
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
