---
title: "Introducing the Llamatik App an Offline AI Chat on Your Mobile Device"
date: 2025-11-25T09:00:00-05:00
description: "We are excited to announce the launch of the Llamatik App, a fully offline AI chatbot designed to showcase the power and versatility of the Llamatik Kotlin Multiplatform library. Whether you are a developer curious about on-device AI or a user who wants a private, fast, and modern chatbot experience, the Llamatik app brings the full potential of lightweight LLMs directly to your mobile device."
tags: ["Kotlin", "Multiplatform", "LLM", "llama.cpp", "Offline AI", "App"]
showDate: true
draft: false
---

🚀 Introducing the Llamatik App: Offline AI Chat on Your Mobile Device

We’re excited to announce the launch of the Llamatik App, a fully offline AI chatbot designed to showcase the power and versatility of the Llamatik Kotlin Multiplatform library.
Whether you’re a developer curious about on-device AI or a user who wants a private, fast, and modern chatbot experience, the Llamatik app brings the full potential of lightweight LLMs directly to your mobile device.

⸻

🔍 What Is Llamatik?

Llamatik is a Kotlin Multiplatform library that allows developers to run local Large Language Models (LLMs) and vector embeddings across Android, iOS, Desktop, and Web platforms.

With the release of the Llamatik app, you can now experience everything the library can do — without writing a single line of code.

The app serves as a live demo, a sandbox, and a reference implementation for developers building local AI experiences.

⸻

⚡ What the Llamatik App Can Do

🧠 Fully Local AI Chat

All text generation runs 100% on your device using quantized open-source models.
There is no cloud, no server, and no data sent anywhere.
This makes Llamatik:
•	Private
•	Fast
•	Always available
•	Compliant with strict privacy environments

The chat interface is simple, clean, and built with Jetpack Compose Multiplatform.

⸻

📦 Built-In Model Downloader

The app includes direct download support for several optimized models, including:
•	Gemma 3 270M
•	SmolVLM 256M / 500M (Vision + Language)
•	Phi-1.5
•	Qwen 2.5 5B (quantized)
•	Llama 3.2 1B (quantized)

All models come in GGUF format and are compatible with the llama.cpp backend integrated in Llamatik.

You can download, switch, and manage models directly from the app.

⸻

🎯 Smart Suggestions & Quick Actions

To make the experience more intuitive, the app provides:
•	Suggested prompts
•	Quick actions
•	Clean and responsive UI
•	A selectable model pill over the input box
•	Built-in onboarding

These small touches make the app great for both beginners and power users.

⸻

🔐 Privacy First

Every generation happens locally.
Your conversations:
•	Are not collected
•	Are not stored externally
•	Are not logged or sent to a server

Even model inference is done offline using llama.cpp compiled for mobile.

The only telemetry in the app is Firebase Analytics and Crashlytics, used strictly for error tracking and anonymous usage insights.

⸻

🧩 A Showcase of Llamatik Library Features

The app demonstrates several key capabilities of the underlying library:

✔ Kotlin Multiplatform support

Build once, run on Android, iOS, Desktop, Web.

✔ On-device LLM inference

Through a custom C/C++ bridge to llama.cpp.

✔ Embedding generation

Compatible with models like nomic-embed-text-v1.5.

✔ Vector store support

Create local semantic search or RAG-like features with lightweight vector stores.

✔ Model management

Download, load, unload, switch, and generate across multiple quantized models.

✔ High-level ChatRunner utilities

Easy to stream, generate, and handle AI conversation logic.

For developers reading this: the Llamatik app is built entirely on top of the open-source library — and its codebase is a practical example of real-world KMP local AI integration.

⸻

📱 Why an App?

While the Llamatik library is already powerful, we wanted a way for:
•	Developers to see the library in action
•	Users to experiment with local AI
•	The community to test models easily
•	Anyone to experience offline AI assistants without setup
•	Newcomers to understand how a cross-platform AI app is structured

The Llamatik app gives you a complete, polished environment to explore what local LLMs can do on a phone.

⸻

🗺 What’s Coming Next?

We’re already working on:
•	🔍 Integrated vector search for PDFs and documents
•	📁 Local model import from file storage
•	🌐 More model providers and sizes
•	🎛 Advanced chat settings (temperature, top-k, etc.)
•	🧱 On-device RAG pipelines
•	🛠 Developer samples & tutorials

Your feedback will help shape the roadmap — so please share your thoughts!

⸻

🐪 Try Llamatik Today

You can now download the [Llamatik app on Google Play](https://play.google.com/store/apps/details?id=com.llamatik.app.android) and soon on the iOS App Store.

It’s free, open, offline, and built with privacy and performance in mind.

Whether you’re a developer building new AI-powered features or a user wanting a fast, local AI assistant — Llamatik is here for you.

👉 Explore. Learn. Build.
👉 Experience what’s possible when AI runs entirely on your device.