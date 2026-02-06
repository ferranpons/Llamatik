package com.llamatik.library.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.io.File

actual object WhisperBridge {
    init {
        System.loadLibrary("llama_jni")
    }

    @Composable
    actual fun getModelPath(modelFileName: String): String {
        val context = LocalContext.current
        val outFile = File(context.cacheDir, modelFileName)
        if (!outFile.exists()) {
            context.assets.open(modelFileName).use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return outFile.absolutePath
    }

    actual external fun initModel(modelPath: String): Boolean
    actual external fun transcribeWav(wavPath: String, language: String?): String
    actual external fun release()
}
