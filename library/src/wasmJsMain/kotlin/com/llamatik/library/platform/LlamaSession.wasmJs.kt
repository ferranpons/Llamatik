package com.llamatik.library.platform

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class LlamaSession(private val handle: Long) {
    actual fun stream(prompt: String, callback: GenStream) {
        callback.onError("WASM: concurrent sessions not supported; use LlamaBridge.generateStream()")
    }
    actual fun cancel() {}
    actual fun close() {}
}
