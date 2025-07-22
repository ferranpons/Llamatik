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
- ✅ Remote inference via optional HTTP client (e.g. llama-cpp-server)
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

Llamatik provides two core modules:

- `llamatik-core`: Native C++ llama.cpp integration via Kotlin/Native
- `llamatik-client`: Lightweight HTTP client to connect to remote llama.cpp-compatible backends

All backed by a shared Kotlin API so you can switch between local and remote seamlessly.

---

## 📦 Installation

Coming soon — stay tuned for full Gradle instructions and prebuilt binaries.

---

## 🤝 Contributing

Llamatik is 100% open-source and actively developed. Contributions, bug reports, and feature
suggestions are welcome!

---

## 📜 License

[MIT](./LICENSE)

---

Built with ❤️ for the Kotlin community.
