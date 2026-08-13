# Contributing to Llamatik

Thank you for your interest in contributing! Llamatik is a community-driven open-source project and all contributions are welcome.

## Ways to Contribute

- **Bug reports** — open a GitHub issue with steps to reproduce, platform details, and the version affected
- **Feature requests** — open a GitHub issue describing the use case and expected behavior
- **Documentation** — fix typos, clarify examples, or improve the README
- **Code** — bug fixes, new features, platform extensions, or performance improvements

## Before You Start

For non-trivial changes, open an issue first to discuss the approach. This avoids duplicate effort and ensures the change aligns with the project direction.

## Development Setup

### Prerequisites

- Android Studio (latest stable)
- Xcode (for iOS targets)
- JDK 21+
- CMake and NDK (for Android native builds)
- A device or emulator for testing on-device inference

### Build

```bash
# Clone with submodules (llama.cpp, whisper.cpp, stable-diffusion.cpp)
git clone --recurse-submodules https://github.com/ferranpons/llamatik.git

# Open in Android Studio and sync Gradle
```

Each native dependency (`llama.cpp`, `whisper.cpp`, `stable-diffusion.cpp`) is included as a Git submodule. Run `git submodule update --init --recursive` if you cloned without `--recurse-submodules`.

### Extra CMake flags

The native builds (Apple, desktop JNI, Android) accept additional CMake configure flags through the `llamatik.cmake.args` Gradle property, with the `LLAMATIK_CMAKE_ARGS` environment variable as a fallback. Values are split on whitespace and appended to the CMake command line, so flags containing spaces are not supported. This lets you enable GPU backends without editing the build script:

```bash
# Build with the Vulkan backend enabled
./gradlew :core:build -Pllamatik.cmake.args="-DGGML_VULKAN=ON"

# Or via the environment
LLAMATIK_CMAKE_ARGS="-DGGML_CUDA=ON" ./gradlew :core:build
```

The WASM build does not receive these flags since GPU backends do not apply to the Emscripten target.

## Pull Request Guidelines

1. **Branch from `main`** — use a descriptive branch name, e.g. `fix/session-leak` or `feat/wasm-streaming`
2. **One concern per PR** — keep changes focused; separate refactors from features
3. **Test your change** — run the sample app on at least one platform (Android or Desktop) to verify the behavior
4. **Update the README** if your change adds or modifies public API
5. **Follow existing code style** — Kotlin standard style, no unused imports, no commented-out code

## Reporting Security Issues

Do **not** open a public issue for security vulnerabilities. See [SECURITY.md](./SECURITY.md) for the responsible disclosure process.

## Code of Conduct

All contributors are expected to follow the [Code of Conduct](./CODE_OF_CONDUCT.md).
