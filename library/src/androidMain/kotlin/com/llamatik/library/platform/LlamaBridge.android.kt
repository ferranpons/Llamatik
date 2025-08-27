package com.llamatik.library.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.io.File

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual object LlamaBridge {
    init {
        System.loadLibrary("llama_jni") // Only here, where System exists
    }

    actual external fun initModel(modelPath: String): Boolean
    actual external fun embed(input: String): FloatArray

    @Composable
    actual fun getModelPath(modelFileName: String): String {
        val context = LocalContext.current
        val outFile = File(context.cacheDir, modelFileName)

        if (!outFile.exists()) {
            context.assets.open(modelFileName).use { inputStream ->
                outFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }

        return outFile.absolutePath
    }

    actual external fun initGenerateModel(modelPath: String): Boolean
    actual external fun generate(prompt: String): String
    actual external fun generateWithContext(systemPrompt: String, contextBlock: String, userPrompt: String): String
}