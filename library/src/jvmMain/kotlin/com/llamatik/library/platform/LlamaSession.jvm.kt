package com.llamatik.library.platform

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class LlamaSession(private val handle: Long, actual val name: String) {

    actual fun stream(prompt: String, callback: GenStream) {
        LlamaBridge.sessionStreamBridge(handle, prompt, callback)
    }

    actual fun cancel() {
        LlamaBridge.sessionCancelBridge(handle)
    }

    actual fun close() {
        LlamaBridge.sessionCloseBridge(handle)
    }
}
