package com.llamatik.core.platform

/**
 * An isolated inference session with its own KV cache and cancel flag.
 *
 * Create via [LlamaBridge.createSession]. Dispose with [close] when done.
 * Multiple sessions may stream concurrently on separate threads.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class LlamaSession {
    /** Human-readable name assigned at creation time. */
    val name: String

    /** Stream tokens for [prompt] into [callback]. Blocking — call from a background thread. */
    fun stream(prompt: String, callback: GenStream)

    /**
     * Reset the KV cache for this session so the next [stream] call starts a fresh context.
     * Safe to call between turns; do not call while [stream] is running.
     */
    fun reset()

    /** Cancel an in-progress [stream] call for this session. */
    fun cancel()

    /** Release native resources. Do not call while [stream] is running. */
    fun close()
}
