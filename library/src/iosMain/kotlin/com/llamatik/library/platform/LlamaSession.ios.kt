@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.llamatik.library.platform

import com.llamatik.library.platform.llama.llama_session_cancel
import com.llamatik.library.platform.llama.llama_session_close
import com.llamatik.library.platform.llama.llama_session_stream
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@OptIn(BetaInteropApi::class)
actual class LlamaSession(private val handle: Long) {

    actual fun stream(prompt: String, callback: GenStream) {
        memScoped {
            val ref = StableRef.create(callback)
            val onDelta = staticCFunction { cstr: CPointer<ByteVar>?, ud: COpaquePointer? ->
                val cb = ud!!.asStableRef<GenStream>().get()
                val s = cstr?.toKString() ?: return@staticCFunction
                cb.onDelta(s)
            }
            val onDone = staticCFunction { ud: COpaquePointer? ->
                val cb = ud!!.asStableRef<GenStream>().get()
                cb.onComplete()
            }
            val onError = staticCFunction { cstr: CPointer<ByteVar>?, ud: COpaquePointer? ->
                val cb = ud!!.asStableRef<GenStream>().get()
                val msg = cstr?.toKString() ?: "unknown error"
                cb.onError(msg)
            }
            try {
                llama_session_stream(handle, prompt, onDelta, onDone, onError, ref.asCPointer())
            } finally {
                ref.dispose()
            }
        }
    }

    actual fun cancel() {
        llama_session_cancel(handle)
    }

    actual fun close() {
        llama_session_close(handle)
    }
}
