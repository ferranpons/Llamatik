package com.llamatik.library.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.io.File

actual object StableDiffusionBridge {
    init {
        // Single native shared library for all JNI bridges (llama/whisper/sd).
        System.loadLibrary("llama_jni")
    }

    @Composable
    actual fun getModelPath(modelFileName: String): String {
        // Same behavior as WhisperBridge: copy model from assets to cache if needed.
        val context = LocalContext.current
        val outFile = File(context.cacheDir, modelFileName)
        if (!outFile.exists()) {
            context.assets.open(modelFileName).use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return outFile.absolutePath
    }

    actual external fun initModel(modelPath: String, threads: Int): Boolean

    actual external fun txt2img(
        prompt: String,
        negativePrompt: String?,
        width: Int,
        height: Int,
        steps: Int,
        cfgScale: Float,
        seed: Long,
    ): ByteArray

    actual external fun release()
}
