package com.llamatik.library.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.llamatik.library.platform.sd.*
import kotlinx.cinterop.*
import platform.Foundation.NSLog
import platform.posix.free

actual object StableDiffusionBridge {
    private var ctx: CPointer<sd_ctx_t>? = null

    @Composable
    actual fun getModelPath(modelFileName: String): String {
        // iOS: the app code already manages model downloads and paths.
        // Keep it consistent with llama/whisper: return the provided filename/path.
        return remember(modelFileName) { modelFileName }
    }

    actual fun initModel(modelPath: String, threads: Int): Boolean {
        release()

        memScoped {
            val params = alloc<sd_ctx_params_t>()
            sd_ctx_params_init(params.ptr)

            // Minimal init: single model path + threads.
            params.model_path = modelPath.cstr.ptr
            params.n_threads = threads

            // Reasonable defaults for mobile
            params.enable_mmap = true
            params.offload_params_to_cpu = true
            params.free_params_immediately = true

            val created = new_sd_ctx(params.ptr)
            if (created == null) {
                NSLog("[StableDiffusionBridge] new_sd_ctx FAILED path=$modelPath")
                return false
            }
            ctx = created
            NSLog("[StableDiffusionBridge] new_sd_ctx OK")
            return true
        }
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
        val c = ctx ?: return ByteArray(0)

        return memScoped {
            val gen = alloc<sd_img_gen_params_t>()
            sd_img_gen_params_init(gen.ptr)

            gen.prompt = prompt.cstr.ptr
            gen.negative_prompt = (negativePrompt ?: "").cstr.ptr
            gen.width = width
            gen.height = height
            gen.seed = seed

            // sampling
            gen.sample_params.sample_steps = steps
            gen.sample_params.guidance.txt_cfg = cfgScale

            val imgPtr = generate_image(c, gen.ptr) ?: return@memScoped ByteArray(0)

            val img = imgPtr.pointed
            val w = img.width.toInt()
            val h = img.height.toInt()
            val ch = img.channel.toInt()

            val pixelCount = w * h
            val out = ByteArray(pixelCount * 4)

            val src = img.data ?: run {
                free(imgPtr)
                return@memScoped ByteArray(0)
            }

            // Convert to RGBA
            when (ch) {
                4 -> src.readBytes(out.size).copyInto(out)
                3 -> {
                    val srcBytes = src.readBytes(pixelCount * 3)
                    var si = 0
                    var di = 0
                    repeat(pixelCount) {
                        out[di++] = srcBytes[si++]
                        out[di++] = srcBytes[si++]
                        out[di++] = srcBytes[si++]
                        out[di++] = 0xFF.toByte()
                    }
                }
                1 -> {
                    val srcBytes = src.readBytes(pixelCount)
                    var si = 0
                    var di = 0
                    repeat(pixelCount) {
                        val v = srcBytes[si++]
                        out[di++] = v
                        out[di++] = v
                        out[di++] = v
                        out[di++] = 0xFF.toByte()
                    }
                }
                else -> {
                    free(imgPtr)
                    return@memScoped ByteArray(0)
                }
            }

            // stable-diffusion.cpp returns heap-allocated image + data (malloc/free).
            // The public C API does not expose a dedicated free; freeing both is expected.
            free(img.data)
            free(imgPtr)

            out
        }
    }

    actual fun release() {
        ctx?.let { free_sd_ctx(it) }
        ctx = null
    }
}
