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
  <a href="https://plugins.jetbrains.com/plugin/31304"><img src="https://img.shields.io/jetbrains/plugin/v/31304" alt="jetbrains plugin badge"/></a>
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
- ✅ Configurable context length, threads, mmap, Flash Attention
- ✅ KV cache session save / load / continue
- ✅ **Concurrent sessions** — run multiple independent inference contexts simultaneously via `LlamaSession`
- ✅ Model metadata introspection (`getModelFinetuneType` — detect base vs instruction-tuned)
- ✅ Chat template introspection and rendering (`getModelChatTemplate` / `applyChatTemplate`)
- ✅ Fine-grained sampling controls (temperature, top-k, top-p, repeat penalty, max tokens)
- ✅ **Multi-Token Prediction (MTP)** — speculative drafting for faster generation on supported models (Qwen3.5, GLM-4)

### 🎙 Speech-to-Text (whisper.cpp)
- ✅ On-device transcription
- ✅ Works fully offline
- ✅ 16kHz mono WAV support
- ✅ Selectable Whisper models
- ✅ Integrated model download + management
- ✅ **Segment-aware transcription** — per-segment text, timestamps, detected language, and speaker-turn boundaries (`transcribeWavSegments`)
- ✅ Optional **translation to English** (whisper's built-in translate task)
- ✅ Optional **tinydiarize speaker-turn detection** (`…-tdrz` models)

### 🎨 Image Generation (stable-diffusion.cpp)

- ✅ On-device Stable Diffusion inference
- ✅ Text-to-image generation (`txt2img`)
- ✅ Image-to-image generation (`img2img`) with configurable `strength`
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

- llama.cpp version: [b9574](https://github.com/ggml-org/llama.cpp/releases/tag/b9574)
- whisper.cpp version [v1.8.4](https://github.com/ggml-org/whisper.cpp/releases/tag/v1.8.4)
- stablediffusion.cpp version [master-596-90e87bc](https://github.com/leejet/stable-diffusion.cpp/releases/tag/master-596-90e87bc)

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
    implementation("com.llamatik:library:1.7.0")
}
```

---

## ⚡ Quick Start

```kotlin
// Resolve model path (place GGUF in assets / bundle)
val modelPath = LlamaBridge.getModelPath("phi-2.Q4_0.gguf")

// (Optional) tune parameters before loading — contextLength/useMmap/flashAttention/gpuLayers
// take effect at model init time; the others can be changed at any time
LlamaBridge.updateGenerateParams(
    temperature    = 0.7f,
    maxTokens      = 512,
    topP           = 0.95f,
    topK           = 40,
    repeatPenalty  = 1.1f,
    contextLength  = 4096,
    numThreads     = 4,
    useMmap        = true,
    flashAttention = false,
    gpuLayers      = 0,
)

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
    fun getModelPath(modelFileName: String): String   // copy asset/bundle model to app files dir and return absolute path
    fun shutdown()                                    // free native resources

    // Embeddings
    fun initEmbedModel(modelPath: String): Boolean    // load embeddings model
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

    // Convenience streaming overload (lambda callbacks)
    fun generateWithContextStream(
        system: String,
        context: String,
        user: String,
        onDelta: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    )

    // Text generation with JSON schema (non-streaming)
    fun generateJson(prompt: String, jsonSchema: String? = null): String
    fun generateJsonWithContext(
        systemPrompt: String,
        contextBlock: String,
        userPrompt: String,
        jsonSchema: String? = null
    ): String

    // Text generation with JSON schema (streaming)
    fun generateJsonStream(prompt: String, jsonSchema: String? = null, callback: GenStream)
    fun generateJsonStreamWithContext(
        systemPrompt: String,
        contextBlock: String,
        userPrompt: String,
        jsonSchema: String? = null,
        callback: GenStream
    )

    // Model metadata
    fun getModelFinetuneType(): String?               // "general.finetune" GGUF key — e.g. "instruct", "chat"; null means base model

    // Chat template support
    fun getModelChatTemplate(): String?               // returns the chat template embedded in the loaded GGUF, or null
    fun applyChatTemplate(
        messages: List<Pair<String, String>>,         // list of (role, content) pairs
        addAssistantPrefix: Boolean                   // true to append the assistant turn prefix
    ): String?                                        // rendered prompt string, or null if model/template unavailable

    // KV cache session support
    fun sessionReset(): Boolean                       // clear KV state, keep model loaded
    fun sessionSave(path: String): Boolean            // persist KV state to file
    fun sessionLoad(path: String): Boolean            // restore KV state from file
    fun generateContinue(prompt: String): String      // generate using existing KV cache

    // Concurrent sessions — each session owns an isolated KV cache; model weights are shared
    fun createSession(name: String = ""): LlamaSession? // null on WASM (not supported)

    // Generation parameters (applied on next generate call)
    fun updateGenerateParams(
        temperature: Float,       // randomness (0.0–2.0)
        maxTokens: Int,           // max output tokens
        topP: Float,              // nucleus sampling threshold
        topK: Int,                // top-k sampling
        repeatPenalty: Float,     // penalty for repeated tokens
        contextLength: Int,       // KV context window size (requires model reload)
        numThreads: Int,          // CPU threads for inference
        useMmap: Boolean,         // memory-map model weights (requires model reload)
        flashAttention: Boolean,  // enable Flash Attention (requires model reload)
        batchSize: Int,           // token batch size for prompt processing (requires model reload)
        gpuLayers: Int = 0,       // layers to offload to GPU: 0 = CPU only, -1 = all layers, N = exactly N layers (requires model reload)
    )

    fun nativeCancelGenerate()                        // cancel ongoing generation

    // Multi-Token Prediction (MTP) — speculative drafting
    fun initMtp(modelPath: String, draftLen: Int = 3): Boolean  // enable MTP; same GGUF as generation model
    fun shutdownMtp()                                 // disable MTP; generation continues without it
}

interface GenStream {
    fun onDelta(text: String)
    fun onComplete()
    fun onError(message: String)
}

// Concurrent session handle — created via LlamaBridge.createSession()
expect class LlamaSession {
    val name: String                                  // human-readable label assigned at creation
    fun stream(prompt: String, callback: GenStream)  // run inference in this session's context
    fun cancel()                                     // cancel the in-progress stream
    fun close()                                      // release native KV cache resources
}
```

### Generation Parameters

All sampling and hardware parameters are set via `updateGenerateParams`. Parameters that affect model loading (`contextLength`, `useMmap`, `flashAttention`, `numThreads`) must be set **before** calling `initGenerateModel` to take effect — the others can be updated at any time.

| Parameter | Default | Description |
|---|---|---|
| `temperature` | `0.7` | Randomness of outputs (0 = deterministic, 2 = very random) |
| `maxTokens` | `256` | Maximum number of tokens to generate |
| `topP` | `0.95` | Nucleus sampling: keep tokens covering this probability mass |
| `topK` | `40` | Only sample from the top-K most likely tokens |
| `repeatPenalty` | `1.1` | Penalty multiplier for recently generated tokens |
| `contextLength` | `4096` | KV cache window size in tokens *(reload required)* |
| `numThreads` | `4` | CPU threads used for inference *(reload required)* |
| `useMmap` | `true` | Memory-map model weights instead of loading into RAM *(reload required)* |
| `flashAttention` | `false` | Enable Flash Attention for faster, more memory-efficient attention *(reload required)* |
| `batchSize` | `512` | Token batch size for prompt processing — larger = faster prefill, more RAM *(reload required)* |
| `gpuLayers` | `0` | Transformer layers to offload to GPU. `0` = CPU only, `-1` = all layers (Metal / CUDA), `N` = exactly N layers *(reload required, ignored on WASM)* |

### KV Cache Sessions

Use the session API to persist and resume conversation state across calls without re-feeding the full prompt:

```kotlin
// Generate and keep the KV state in memory
LlamaBridge.generate("Tell me about Kotlin.")

// Save the KV state to disk
LlamaBridge.sessionSave("/path/to/session.bin")

// ... later or in a new process ...

// Restore state and continue from where you left off
LlamaBridge.sessionLoad("/path/to/session.bin")
val continuation = LlamaBridge.generateContinue("What about multiplatform support?")

// Reset state without unloading the model
LlamaBridge.sessionReset()
```

### Concurrent Sessions

`LlamaBridge.createSession()` returns a `LlamaSession` handle that owns an **isolated KV cache context**. The model weights (`gen_model`) are shared across all sessions, so loading the model once is sufficient regardless of how many sessions you create. Each session can run inference independently and concurrently on any thread.

```kotlin
// Load the model once
LlamaBridge.initGenerateModel(modelPath)

// Create two independent sessions with human-readable names
val sessionA = LlamaBridge.createSession(name = "Agent A") ?: error("Session creation failed")
val sessionB = LlamaBridge.createSession(name = "Agent B") ?: error("Session creation failed")

// Run them concurrently (e.g. launch in separate coroutines)
launch { sessionA.stream("Tell me about Kotlin.", callback = agentACallback) }
launch { sessionB.stream("Explain coroutines.", callback = agentBCallback) }

// Cancel an in-progress session
sessionA.cancel()

// Always close sessions when done to free native KV cache memory
sessionA.close()
sessionB.close()
```

`createSession()` returns `null` on WASM (concurrent sessions are not supported in the single-threaded WebAssembly environment — use `LlamaBridge.generateStream()` there instead).

### Multi-Token Prediction (MTP)

MTP is a speculative decoding technique where a lightweight **draft head** — embedded directly in the same GGUF file — predicts several tokens ahead. The trunk model then verifies all drafts in a single batched forward pass and accepts the ones that match. This delivers a throughput boost (typically 1.5–2.5× on CPU) with **no change in output quality**.

Supported model families: **Qwen3.5**, **Qwen3.5-MoE**, **GLM-4** (and others with `nextn_predict_layers` in their GGUF metadata).

```kotlin
val modelPath = LlamaBridge.getModelPath("Qwen3.5-1.7B-Instruct-Q4_K_M.gguf")

// 1. Load the trunk model as usual
LlamaBridge.initGenerateModel(modelPath)

// 2. Enable MTP — pass the same GGUF, the library loads only the MTP layers
//    draftLen: max speculative tokens per step (1–8 recommended, default 3)
val mtpReady = LlamaBridge.initMtp(modelPath, draftLen = 3)
if (!mtpReady) {
    println("Model does not contain MTP layers — running without speculative drafting.")
}

// 3. Use the API exactly as before — MTP is transparent
LlamaBridge.generateStream(
    prompt = prompt,
    callback = object : GenStream {
        override fun onDelta(text: String) { print(text) }
        override fun onComplete()          { println("\n[done]") }
        override fun onError(msg: String)  { println("Error: $msg") }
    }
)

// 4. Optionally disable MTP at runtime (trunk model stays loaded)
LlamaBridge.shutdownMtp()
```

**Notes:**
- `initMtp` must be called **after** `initGenerateModel` because the trunk context must already exist.
- MTP is active for streaming generation only (`generateStream` / `generateStreamWithContext` / `generateWithContextStream`). Non-streaming and JSON-constrained paths run the standard decode loop.
- `shutdown()` also releases MTP resources automatically.
- On WASM, `initMtp` always returns `false` (not supported).

### Model Metadata

`getModelFinetuneType()` reads the `general.finetune` key from the loaded GGUF's metadata. Use it after `initGenerateModel` to check whether the model is instruction-tuned before sending it chat-style prompts or tool-call XML.

| Return value | Meaning |
|---|---|
| `"instruct"` / `"chat"` | Instruction-tuned — suitable for chat, tool calls, structured output |
| `"base"` | Base model — will complete text but does not reliably follow instructions |
| `null` | Key absent in the GGUF — treat as base model |

```kotlin
LlamaBridge.initGenerateModel(modelPath)

when (LlamaBridge.getModelFinetuneType()?.lowercase()) {
    "instruct", "chat" -> { /* proceed normally */ }
    else -> showWarning("This appears to be a base model. For best results, use an instruction-tuned model.")
}
```

### Chat Templates

Most modern GGUF models ship with an embedded chat template (a Jinja-style string that describes how to format conversation turns for that model family). The two template helpers give you direct access to it:

| Method | Description |
|---|---|
| `getModelChatTemplate()` | Returns the raw template string from the loaded model, or `null` if the model is not loaded or has no embedded template. |
| `applyChatTemplate(messages, addAssistantPrefix)` | Renders a list of `(role, content)` pairs into a single prompt string using the model's own template. Pass `addAssistantPrefix = true` when you want the model to begin generating the next assistant turn. Returns `null` when the model is not loaded. |

```kotlin
// Build a multi-turn conversation prompt using the model's own template
val prompt = LlamaBridge.applyChatTemplate(
    messages = listOf(
        "system" to "You are a helpful assistant.",
        "user"   to "What is Kotlin Multiplatform?",
    ),
    addAssistantPrefix = true          // appends the assistant-turn prefix so the model starts generating
)

if (prompt != null) {
    val response = LlamaBridge.generate(prompt)
    println(response)
}

// Inspect the raw template if needed (e.g. for debugging or custom rendering)
val templateString = LlamaBridge.getModelChatTemplate()
println(templateString)
```

**Note**: `applyChatTemplate` relies on the template embedded in the GGUF file, which is model-specific. If the model has no embedded template (older GGUF files), both helpers return `null` and you should format the prompt manually.

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
     *
     * @param initialPrompt Optional text prepended to the decoder input (up to 224 tokens).
     *   Use it to bias transcription toward domain-specific vocabulary (e.g. medical terms).
     */
    fun transcribeWav(wavPath: String, language: String? = null, initialPrompt: String? = null): String

    /**
     * Segment-aware transcription. Returns a JSON document with per-segment text,
     * start/end timestamps (milliseconds), speaker-turn flag, and the detected
     * language code. Shape:
     *
     * ```json
     * {"language":"de","segments":[
     *     {"text":"Guten Morgen.","t0":0,"t1":1200,"speaker_turn_next":false}
     * ]}
     * ```
     *
     * @param translate  `true` = translate audio to English (whisper's built-in
     *                   translate task). Default `false` = language-preserving.
     * @param diarize    `true` = enable tinydiarize speaker-turn detection. Only
     *                   meaningful with a `…-tdrz` model; leave `false` otherwise.
     */
    fun transcribeWavSegments(
        wavPath: String,
        language: String? = null,
        initialPrompt: String? = null,
        translate: Boolean = false,
        diarize: Boolean = false,
    ): String

    /** Frees native resources. */
    fun release()
}
```

#### Example

```kotlin
import com.llamatik.core.platform.WhisperBridge

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

#### Segment-aware transcription

`transcribeWavSegments` returns a JSON document that exposes everything `transcribeWav` discards — per-segment timestamps, the auto-detected language, and (with a `-tdrz` model) speaker-turn boundaries.

```kotlin
import com.llamatik.core.platform.WhisperBridge
import kotlinx.serialization.json.*

WhisperBridge.initModel(modelPath)

val json = WhisperBridge.transcribeWavSegments(
    wavPath  = "/path/to/recording.wav",
    language = null,           // auto-detect
)
// {"language":"de","segments":[{"text":"Guten Morgen.","t0":0,"t1":1200,"speaker_turn_next":false},...]}

// Parse with kotlinx.serialization or any JSON library
val root     = Json.parseToJsonElement(json).jsonObject
val language = root["language"]?.jsonPrimitive?.content
val segments = root["segments"]!!.jsonArray
for (seg in segments) {
    val obj   = seg.jsonObject
    val text  = obj["text"]?.jsonPrimitive?.content
    val t0Ms  = obj["t0"]?.jsonPrimitive?.long   // start in milliseconds
    val t1Ms  = obj["t1"]?.jsonPrimitive?.long   // end in milliseconds
    val turn  = obj["speaker_turn_next"]?.jsonPrimitive?.boolean
    println("[$t0Ms–$t1Ms ms] $text  (speaker_turn=$turn)")
}
```

**Translate to English** — pass `translate = true` to get the English translation of the audio regardless of the spoken language:

```kotlin
val json = WhisperBridge.transcribeWavSegments(wavPath, translate = true)
```

**Speaker diarization** — pass `diarize = true` **only** with a tinydiarize (`…-tdrz`) model to get real `speaker_turn_next` boundaries:

```kotlin
val json = WhisperBridge.transcribeWavSegments(
    wavPath = wavPath,
    diarize = true,   // requires a …-tdrz model (e.g. small.en-tdrz)
)
```


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

    /**
     * Loads the Stable Diffusion model.
     * @param threads CPU threads to use; -1 lets the backend decide.
     */
    fun initModel(modelPath: String, threads: Int = -1): Boolean

    /**
     * Text-to-image generation. Returns raw RGBA pixels (width * height * 4 bytes).
     * Returns an empty array on failure.
     */
    fun txt2img(
        prompt: String,
        negativePrompt: String? = null,
        width: Int = 512,
        height: Int = 512,
        steps: Int = 20,
        cfgScale: Float = 7.0f,
        seed: Long = -1L,
    ): ByteArray

    /**
     * Image-to-image generation. Starts from [initImageRgba] and steers it with [prompt].
     * [strength] controls how much the source image is preserved (0.0 = unchanged, 1.0 = ignored).
     * Returns raw RGBA pixels (width * height * 4 bytes). Returns an empty array on failure.
     */
    fun img2img(
        initImageRgba: ByteArray,
        initImageW: Int,
        initImageH: Int,
        prompt: String,
        negativePrompt: String? = null,
        width: Int = 512,
        height: Int = 512,
        steps: Int = 20,
        cfgScale: Float = 7.0f,
        strength: Float = 0.75f,
        seed: Long = -1L,
    ): ByteArray

    /** Releases native resources. */
    fun release()
}
```

#### txt2img example

```kotlin
import com.llamatik.core.platform.StableDiffusionBridge

val modelPath = StableDiffusionBridge.getModelPath("dreamshaper.safetensors")
StableDiffusionBridge.initModel(modelPath, threads = 4)

val rgba = StableDiffusionBridge.txt2img(
    prompt = "A cyberpunk llama in neon Tokyo",
    negativePrompt = "blurry, low quality",
    width = 512,
    height = 512,
    steps = 20,
    cfgScale = 7.0f,
    seed = 42L,
)
// Convert rgba (ByteArray, width*height*4) to a platform Bitmap / UIImage / BufferedImage
```

#### img2img example

```kotlin
val sourceRgba: ByteArray = /* existing RGBA image bytes */

val rgba = StableDiffusionBridge.img2img(
    initImageRgba = sourceRgba,
    initImageW = 512,
    initImageH = 512,
    prompt = "The same scene as a watercolor painting",
    negativePrompt = "low quality",
    strength = 0.75f,
    seed = 42L,
)
```

### 👁️ Vision / Multimodal (MultimodalBridge)

MultimodalBridge wraps llama.cpp's multimodal (VLM) support for on-device image analysis using vision-language models such as SmolVLM.

The workflow is:
1. Download a VLM GGUF model and its matching mmproj GGUF file to local storage.
2. Initialize the bridge once with both file paths.
3. Pass image bytes (JPEG/PNG/BMP) and a text prompt to receive a streamed response.

### MultimodalBridge API surface

```kotlin
object MultimodalBridge {
    /**
     * Load the vision model and its multimodal projector (mmproj) side-by-side.
     * Both files must be available on disk before calling this.
     *
     * @param modelPath  Absolute path to the GGUF vision model.
     * @param mmprojPath Absolute path to the GGUF mmproj file.
     * @return true on success.
     */
    fun initModel(modelPath: String, mmprojPath: String): Boolean

    /**
     * Analyze an image given as raw bytes (JPEG/PNG/BMP), streaming the response
     * token by token via [callback].
     *
     * Must be called from a background thread/coroutine; blocks until generation completes.
     */
    fun analyzeImageBytesStream(imageBytes: ByteArray, prompt: String, callback: GenStream)

    /** Cancel an in-progress analyzeImageBytesStream call. */
    fun cancelAnalysis()

    /** Free all native resources (model, mmproj context, llama context). */
    fun release()
}
```

#### Example

```kotlin
import com.llamatik.core.platform.MultimodalBridge

// 1) Init once — both model and mmproj must be downloaded first
val loaded = MultimodalBridge.initModel(
    modelPath  = "/path/to/SmolVLM-256M-Instruct-Q8_0.gguf",
    mmprojPath = "/path/to/mmproj-SmolVLM-256M-Instruct-f16.gguf"
)

// 2) Analyze an image (e.g. loaded from disk or camera)
val imageBytes: ByteArray = File("/path/to/photo.jpg").readBytes()

MultimodalBridge.analyzeImageBytesStream(
    imageBytes = imageBytes,
    prompt     = "Describe what you see in this image.",
    callback   = object : GenStream {
        override fun onDelta(text: String)   { print(text) }
        override fun onComplete()            { println("\n[done]") }
        override fun onError(message: String){ println("Error: $message") }
    }
)

// 3) Optional: cancel mid-stream
MultimodalBridge.cancelAnalysis()

// 4) Optional: release on app shutdown
MultimodalBridge.release()
```

**Note**: MultimodalBridge requires both a vision model GGUF **and** a matching mmproj GGUF. Llamatik's app downloads both automatically when you select a VLM model.

---

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

Llamatik is already used in production apps.

<table>
  <tr>
    <td width="80" align="center">
      <a href="https://plugins.jetbrains.com/plugin/31304">
        <img src="https://plugins.jetbrains.com/files/31304/1043300/icon/default.svg" width="64" alt="Llamatik Code icon"/>
      </a>
    </td>
    <td>
      <b><a href="https://plugins.jetbrains.com/plugin/31304">Llamatik Code</a></b><br/>
      Local-first AI coding assistant for IntelliJ-based IDEs. Project-aware chat, on-device inference, AI agent with diff previews, and MCP server support — your code never leaves your machine.
    </td>
  </tr>
</table>

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
