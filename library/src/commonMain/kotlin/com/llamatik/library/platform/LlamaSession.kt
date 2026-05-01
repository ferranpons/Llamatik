package com.llamatik.library.platform

/**
 * An isolated inference session with its own KV cache and cancel flag.
 *
 * Create via [LlamaBridge.createSession]. Dispose with [close] when done.
 * Multiple sessions may stream concurrently on separate threads.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class LlamaSession {
    /** Stream tokens for [prompt] into [callback]. Blocking — call from a background thread. */
    fun stream(prompt: String, callback: GenStream)

    /** Cancel an in-progress [stream] call for this session. */
    fun cancel()

    /** Release native resources. Do not call while [stream] is running. */
    fun close()
}
