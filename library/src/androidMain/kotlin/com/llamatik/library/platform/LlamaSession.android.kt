package com.llamatik.library.platform

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class LlamaSession(private val handle: Long) {

    actual fun stream(prompt: String, callback: GenStream) {
        LlamaBridge.nativeSessionStream(handle, prompt, callback)
    }

    actual fun cancel() {
        LlamaBridge.nativeSessionCancel(handle)
    }

    actual fun close() {
        LlamaBridge.nativeCloseSession(handle)
    }
}
