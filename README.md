<p align="center">
  <img src="https://raw.githubusercontent.com/ferranpons/llamatik/main/assets/llamatik-logo.png" alt="Llamatik Logo" width="200"/>
</p>

<h1 align="center">🦙 Llamatik</h1>

<p align="center">
  Kotlin-first llama.cpp integration for on-device and remote LLM inference.
</p>

<p align="center"><i>Kotlin. LLMs. On your terms.</i></p>

---

## 🚀 Features

- ✅ Kotlin Multiplatform: shared code across Android, iOS, and desktop
- ✅ Offline inference via llama.cpp (compiled with Kotlin/Native bindings)
- ✅ Remote inference via optional HTTP client (e.g. llamatik-server)
- ✅ Embeddings and text generation support
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

Llamatik will provide three core modules:

- `llamatik-core`: Native C++ llama.cpp integration via Kotlin/Native
- `llamatik-client`: Lightweight HTTP client to connect to remote llama.cpp-compatible backends
- `llamatik-backend`: Lightweight llama.cpp HTTP server

All backed by a shared Kotlin API so you can switch between local and remote seamlessly.

---

## 📦 Installation

- Add to your settings.gradle.kts

```Kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()

        val gprUser = providers.gradleProperty("GH_USER").orNull
        val gprKey = providers.gradleProperty("GH_TOKEN").orNull
        maven {
            url = uri("https://maven.pkg.github.com/ferranpons/llamatik")
            credentials {
                username = gprUser
                password = gprKey
            }
        }
    }
}
```

- Add to your build.gradle.kts

```Kotlin
commonMain.dependencies {
    implementation("com.llamatik.library:llamatik:0.1.0")
}
```

## 🧑‍💻 Usage

*NOTE:* This is work in progress, and may change during development.

```Kotlin

// Creates a copy for model from a file and returns the path
// models should be in the "assets" folder (androidMain/assets)
fun getModelPath(modelFileName: String): String

// Embedding

// Initializes the model from the path
fun initModel(modelPath: String): Boolean

// Generates embeddings for a given input
fun embed(input: String): FloatArray

// Text generation

// Initializes the generate model from the path
fun initGenerateModel(modelPath: String): Boolean

// Generates text for a given prompt
fun generate(prompt: String): String
```

---

## 🤝 Contributing

Llamatik is 100% open-source and actively developed. Contributions, bug reports, and feature
suggestions are welcome!

---

## 📜 License

[MIT](./LICENSE)

---

Built with ❤️ for the Kotlin community.
