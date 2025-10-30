<p align="center">
  <img src="https://raw.githubusercontent.com/ferranpons/llamatik/main/assets/llamatik-icon-logo.png" alt="Llamatik Logo" width="150"/>
</p>

<h1 align="center">Llamatik</h1>

<p align="center">
  Kotlin-first llama.cpp integration for on-device and remote LLM inference.
</p>

<p align="center"><i>Kotlin. LLMs. On your terms.</i></p>

---

## 🚀 Features

- ✅ Kotlin Multiplatform: shared code across Android, iOS, and desktop  
- ✅ Offline inference via llama.cpp (compiled with Kotlin/Native bindings)  
- ✅ Remote inference via optional HTTP client (e.g. llamatik-server)  
- ✅ Embeddings support for vector search & retrieval  
- ✅ Text generation (non-streaming and streaming)  
- ✅ Context-aware generation (system + conversation history)  
- ✅ Works with GGUF models (e.g. Mistral, Phi, LLaMA)  
- ✅ Lightweight and dependency-free runtime  

---

## 🔧 Use Cases

- 🧠 On-device chatbots  
- 📚 Local RAG systems  
- 🛰️ Hybrid AI apps with fallback to remote LLMs  
- 🎮 Game AI, assistants, and dialogue generators  

---

## 🧱 Architecture

Llamatik provides three core modules:

- `llamatik-core`: Native C++ llama.cpp integration via Kotlin/Native  
- `llamatik-client`: Lightweight HTTP client to connect to remote llama.cpp-compatible backends  
- `llamatik-backend`: Lightweight llama.cpp HTTP server  

All backed by a shared Kotlin API so you can switch between local and remote seamlessly.

---

## 🔧 Requirements

- iOS Deployment Target 16.6 

---

## 📦 Library Installation

Llamatik is published on **Maven Central**.

- Add to your **`settings.gradle.kts`**:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
```

- Add to your `build.gradle.kts`:

```kotlin
commonMain.dependencies {
    implementation("com.llamatik:library:0.8.0")
}
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

    // Convenience streaming overload (callbacks)
    fun generateStreamWithContext(
        system: String,
        context: String,
        user: String,
        onDelta: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    )
}

interface GenStream {
    fun onDelta(text: String)
    fun onComplete()
    fun onError(message: String)
}
```

### Quick start (Android)

```kotlin
// 1) Resolve model paths (place GGUF in androidMain/assets)
val embPath = LlamaBridge.getModelPath("mistral-embed.Q4_0.gguf")
val genPath = LlamaBridge.getModelPath("phi-2.Q4_0.gguf")

// 2) Load models
LlamaBridge.initModel(embPath)
LlamaBridge.initGenerateModel(genPath)

// 3a) Embeddings
val vec: FloatArray = LlamaBridge.embed("Kotlin ❤️ llama.cpp")

// 3b) Non-streamed generation
val reply: String = LlamaBridge.generate("Write a haiku about Kotlin.")

// 3c) Streaming generation with callbacks
LlamaBridge.generateStreamWithContext(
    system = "You are a concise assistant.",
    context = "Project: Llamatik readme refresh.",
    user = "List 3 key features.",
    onDelta = { delta -> /* append to UI */ },
    onDone  = { /* enable send */ },
    onError = { err -> /* show error */ }
)
```

### Notes

- Call `shutdown()` on app teardown to release native resources.  
- `getModelPath()` is `@Composable` to allow platform-specific asset access where needed.  
- Use GGUF models compatible with your build of llama.cpp (quantization, context size, etc.).  

---

## 🧑‍💻 Backend Usage

Please go to the [Backend README.md](./backend/README.md) for more information.

---

## 🤝 Contributing

Llamatik is 100% open-source and actively developed.  
Contributions, bug reports, and feature suggestions are welcome!

---

## 📜 License

[MIT](./LICENSE)

---

Built with ❤️ for the Kotlin community.
