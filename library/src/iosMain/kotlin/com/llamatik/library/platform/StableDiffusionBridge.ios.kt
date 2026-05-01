@file:OptIn(ExperimentalForeignApi::class)

package com.llamatik.library.platform

import com.llamatik.library.platform.sd.sd_free_bytes
import com.llamatik.library.platform.sd.sd_img2img_rgba
import com.llamatik.library.platform.sd.sd_init
import com.llamatik.library.platform.sd.sd_release
import com.llamatik.library.platform.sd.sd_txt2img_rgba
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pin
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import platform.Foundation.NSLog

actual object StableDiffusionBridge {

    private var isInitialized = false

    actual fun getModelPath(modelFileName: String): String {
        // iOS app manages download/path. Keep consistent with your other bridges.
        return modelFileName
    }

    actual fun initModel(modelPath: String, threads: Int): Boolean {
        release()

        val ok = sd_init(modelPath, threads) != 0
        isInitialized = ok

        if (!ok) {
            NSLog("[StableDiffusionBridge] sd_init FAILED path=$modelPath")
        } else {
            NSLog("[StableDiffusionBridge] sd_init OK")
        }
        return ok
    }

    actual fun txt2img(
        prompt: String,
        negativePrompt: String?,
        width: Int,
        height: Int,
        steps: Int,
        cfgScale: Float,
        seed: Long,
    ): ByteArray {
        if (!isInitialized) return ByteArray(0)
        if (prompt.isBlank()) return ByteArray(0)

        return memScoped {
            val outW = alloc<IntVar>()
            val outH = alloc<IntVar>()
            val outSize = alloc<IntVar>()

            val bytesPtr = sd_txt2img_rgba(
                prompt = prompt,
                negative_prompt = (negativePrompt ?: ""),
                width = width,
                height = height,
                steps = steps,
                cfg_scale = cfgScale,
                seed = seed,
                out_w = outW.ptr,
                out_h = outH.ptr,
                out_size_bytes = outSize.ptr
            ) ?: return@memScoped ByteArray(0)

            val size = outSize.value
            if (size <= 0) {
                sd_free_bytes(bytesPtr)
                return@memScoped ByteArray(0)
            }

            val out = bytesPtr.readBytes(size)
            sd_free_bytes(bytesPtr)
            out
        }
    }

    actual fun img2img(
        initImageRgba: ByteArray,
        initImageW: Int,
        initImageH: Int,
        prompt: String,
        negativePrompt: String?,
        width: Int,
        height: Int,
        steps: Int,
        cfgScale: Float,
        strength: Float,
        seed: Long,
    ): ByteArray {
        if (!isInitialized) return ByteArray(0)
        if (prompt.isBlank()) return ByteArray(0)
        if (initImageRgba.isEmpty() || initImageW <= 0 || initImageH <= 0) return ByteArray(0)

        val pinned = initImageRgba.pin()
        return try {
            memScoped {
                val outW = alloc<IntVar>()
                val outH = alloc<IntVar>()
                val outSize = alloc<IntVar>()

                val bytesPtr = sd_img2img_rgba(
                    init_image_rgba = pinned.addressOf(0).reinterpret(),
                    init_image_w = initImageW,
                    init_image_h = initImageH,
                    prompt = prompt,
                    negative_prompt = (negativePrompt ?: ""),
                    width = width,
                    height = height,
                    steps = steps,
                    cfg_scale = cfgScale,
                    strength = strength,
                    seed = seed,
                    out_w = outW.ptr,
                    out_h = outH.ptr,
                    out_size_bytes = outSize.ptr
                ) ?: return@memScoped ByteArray(0)

                val size = outSize.value
                if (size <= 0) {
                    sd_free_bytes(bytesPtr)
                    return@memScoped ByteArray(0)
                }

                val out = bytesPtr.readBytes(size)
                sd_free_bytes(bytesPtr)
                out
            }
        } finally {
            pinned.unpin()
        }
    }

    actual fun release() {
        if (isInitialized) {
            sd_release()
        }
        isInitialized = false
    }
}
