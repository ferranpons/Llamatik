---
title: "Introducing Llamatik Code: A Privacy-First AI Coding Assistant for IntelliJ and Android Studio"
date: 2026-05-14T00:00:00-05:00
description: "Llamatik Code is a local-first AI coding assistant for IntelliJ-based IDEs. All inference runs on your machine using GGUF models — no cloud, no API keys, no data leaving your device."
image: "images/blog/llamatik_code_landing_image.png"
tags: ["Llamatik Code", "IntelliJ", "Android Studio", "AI", "Privacy", "llama.cpp", "GGUF", "Local AI"]
showDate: true
draft: false
---

We have been building Llamatik as a platform for private, on-device AI since day one. Today we are bringing that same philosophy to your IDE.

**Llamatik Code** is now available on the [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/31304-llamatik-code) — a fully local, privacy-first AI coding assistant for IntelliJ IDEA, Android Studio, and the entire JetBrains IDE family.

No cloud. No API keys. No data leaving your machine. Just your local GGUF model, running inside your IDE.

---

## Why local AI in your IDE?

Most AI coding assistants work the same way: your code is sent to a remote server, a cloud model processes it, and the result comes back. That works — but it comes with trade-offs:

- Your code and context leave your machine on every request
- You depend on network connectivity and third-party uptime
- Subscription costs scale with usage
- Enterprise and regulated environments often can't use cloud AI at all

Llamatik Code takes a different approach. Everything runs on-device using [llama.cpp](https://github.com/ggerganov/llama.cpp) — the same battle-tested runtime powering the Llamatik mobile and desktop apps. You load a GGUF model once, and from that point your IDE has a fully capable AI assistant that never phones home.

---

## What Llamatik Code can do

### Ask Mode — chat about anything, no file changes

Ask questions, explore APIs, get explanations, generate snippets. Ask Mode is a full conversational interface with persistent multi-session history and awareness of your current files, selected text, and project structure.

Ask Mode is **unlimited in the free tier** — no daily cap, no credits.

### Agent Mode — let the model drive code changes

Agent Mode goes further. You describe what you want in natural language, and the agent proposes code edits, shows you a diff, and waits for your approval before touching any file.

The agent is project-aware: it understands your file structure, can navigate across files, and chains multiple tool calls to complete multi-step tasks. The free tier includes single-session agent tasks. Pro unlocks parallel multi-task execution and **Aggressive Mode** — where the agent can make autonomous decisions without prompting for approval at each step.

### Multi-file editing (Pro)

Apply code changes across multiple files at once. Useful for refactors, API migrations, and anything that spans more than one file.

### MCP Server Integration

Llamatik Code supports the [Model Context Protocol](https://modelcontextprotocol.io/), letting you connect the agent to external tools and services. The free tier supports one MCP server with manual action approval. Pro unlocks unlimited MCP servers and auto-approval.

### External Knowledge — web search built in

Since version 1.5.0, Llamatik Code includes an External Knowledge system that lets the model search the web and read documentation without any API key.

It uses DuckDuckGo under the hood and understands developer intent:

- Questions about Kotlin or Coroutines → searches kotlinlang.org
- Questions about Android or Compose → searches developer.android.com
- Error messages → searches GitHub Issues
- Library questions → searches package registries and changelogs

Each result comes with structured citations — source type, confidence level, and direct URL. The model plans multi-step research workflows automatically and synthesizes findings into a coherent answer.

The free tier includes 5 searches per day (5 results each). Pro raises this to 50 searches per day with 10 results each and up to 10 private documentation sources.

### Code Health Monitor

A background analysis mode that surfaces code quality issues. Free users can run it manually; Pro unlocks advanced automated modes.

---

## Free vs Pro

| Feature | Free | Pro |
|---|---|---|
| Ask Mode (chat) | Unlimited | Unlimited |
| Agent sessions | Single session | Multi-task, parallel |
| Aggressive agent mode | — | ✓ |
| Multi-file code application | — | ✓ |
| MCP servers | 1, manual approval | Unlimited, auto-approval |
| External Knowledge searches/day | 5 | 50 |
| Results per search | 5 | 10 |
| Private docs sources | 1 | 10 |
| Code Health Monitor | Manual | Advanced automated |

Pro subscriptions are available at [llamatik.com](https://www.llamatik.com).

---

## Getting started

**Requirements:**
- IntelliJ IDEA 2026.1 or any JetBrains IDE on platform build 252+
- macOS, Linux, or Windows (64-bit, JDK 21+)
- A GGUF model file stored locally

**Installation:**
1. Open your IDE → Settings → Plugins → Marketplace
2. Search for **Llamatik Code** and install it
3. Restart the IDE

**Model setup:**
1. Go to Settings → Llamatik Code → Inference
2. Point it at your local `.gguf` file
3. Adjust context size and thread count for your hardware

Any llama.cpp-compatible GGUF model works. For most development tasks, a 7B Q4_K_M or Q5_K_M quantized instruct model is a good starting point. For heavier agent work or complex codebases, a 13B–14B model offers noticeably better reasoning.

Full setup instructions and model recommendations are in the [documentation](https://docs.llamatik.com/llamatik-code/).

---

## Built on the Llamatik engine

Llamatik Code is powered by the same Kotlin-first llama.cpp integration that drives the Llamatik mobile and desktop apps. That means the same inference quality, the same GGUF compatibility, and the same privacy guarantees — whether you are chatting on your phone or editing code in your IDE.

External network access only ever happens through explicitly configured MCP servers or External Knowledge searches. Nothing else leaves your machine.

---

## Install it today

Llamatik Code is available now on the JetBrains Marketplace.

→ [Install Llamatik Code](https://plugins.jetbrains.com/plugin/31304-llamatik-code)  
→ [Read the documentation](https://docs.llamatik.com/llamatik-code/)
