@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.llamatik.library.platform

import com.llamatik.library.platform.vlm.vlm_analyze_image_bytes_stream
import com.llamatik.library.platform.vlm.vlm_cancel
import com.llamatik.library.platform.vlm.vlm_init
import com.llamatik.library.platform.vlm.vlm_release
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual object MultimodalBridge {

    actual fun initModel(modelPath: String, mmprojPath: String): Boolean =
        vlm_init(modelPath, mmprojPath)

    @OptIn(BetaInteropApi::class)
    actual fun analyzeImageBytesStream(imageBytes: ByteArray, prompt: String, callback: GenStream) {
        imageBytes.usePinned { pinned ->
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
                    vlm_analyze_image_bytes_stream(
                        image_bytes = pinned.addressOf(0).reinterpret(),
                        image_len   = imageBytes.size.toULong(),
                        prompt      = prompt,
                        on_delta    = onDelta,
                        on_done     = onDone,
                        on_error    = onError,
                        user_data   = ref.asCPointer()
                    )
                } finally {
                    ref.dispose()
                }
            }
        }
    }

    actual fun cancelAnalysis() = vlm_cancel()

    actual fun release() = vlm_release()
}
