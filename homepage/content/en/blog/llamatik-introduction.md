---
title: "Introducing Llamatik Offline LLMs for Kotlin Multiplatform"
date: 2025-07-22T09:00:00-05:00
description: "Meet Llamatik — a multiplatform Kotlin library to run llama.cpp locally on Android, iOS, and desktop, complete with an HTTP server."
tags: ["Kotlin", "Multiplatform", "LLM", "llama.cpp", "Offline AI"]
showDate: true
draft: false
---

🦙 Introducing Llamatik: Offline LLMs for Kotlin Multiplatform

We’re thrilled to introduce Llamatik — an open-source Kotlin Multiplatform library that brings local
Large Language Models (LLMs) to Android, iOS, desktop, and beyond using the power of llama.cpp.

Llamatik makes it simple and efficient to integrate offline, on-device inference and embeddings into
your KMP apps, whether you’re building an AI assistant, a RAG chatbot, or an edge intelligence tool.

⸻

✨ Why Llamatik?

While the AI ecosystem is rich with APIs, most Kotlin developers are still tied to cloud-based
models. That means latency, privacy risks, and ongoing costs.

We believe the future of AI is:

• 🔐 Private: Your data stays on-device.

• 📱 Multiplatform: One codebase for Android, iOS, macOS, Linux, and Windows.

• ⚡ Performant: Built for small, quantized models that run fast on consumer hardware.

Llamatik bridges the gap between llama.cpp’s C++ backend and Kotlin’s modern multiplatform tooling —
giving you total control over your models and your data.

⸻

💡 What Can You Do with It?

With Llamatik, you can:

• 🧠 Run quantized LLMs like Phi-2, Mistral, or TinyLlama completely offline.

• 🔍 Generate embeddings using models like nomic-embed-text or bge-small locally.

• ⚡ Launch your own HTTP inference server using Ktor — fully self-contained, built into Llamatik,
and powered by llama.cpp.

• 🌐 Connect to remote llama.cpp endpoints (like llama-cpp-python, llama-server, or Ollama) with the
bundled HTTP client.

• 🔁 Use the same API across Android, iOS, and other native platforms — no platform-specific code
needed.

⸻

🌐 Built-in HTTP Inference Server

Llamatik includes a ready-to-use Ktor-based HTTP server that wraps your local llama.cpp models. You
can spin it up with a single call and expose endpoints like:

POST /v1/chat/completions

POST /v1/embeddings

GET /v1/models

This makes it easy to:

• 🔗 Connect your own apps (or other devices) to a shared local model

• 🧪 Use OpenAI-compatible tooling (e.g. LangChain, LlamaIndex) with your local server

• ⚙️ Integrate into your own edge deployments or experiments

No Python needed. No Docker required. Just Kotlin + Llamatik.

⸻

📦 What’s Inside?

• 🦙 Native bindings to llama.cpp for Kotlin/Native targets (no JNI or JNI-only limitations).

• 🧠 Multi-model context manager (for simultaneous generation + embeddings).

• 🛰️ Optional HTTP client and server.

• 🧱 Model loading, prompt building, and memory-efficient execution tailored for mobile.

• 🛠️ Simple, extensible API — built for Kotlin, not adapted from Python.

⸻

📢 Get Involved

Llamatik is open-source and community-driven. Whether you’re building AI-first apps or simply
exploring what’s possible offline, we’d love to hear from you.

🔗 [GitHub Repo](https://github.com/ferranpons/Llamatik)