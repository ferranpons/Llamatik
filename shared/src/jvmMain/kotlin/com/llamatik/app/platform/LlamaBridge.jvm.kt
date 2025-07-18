@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.llamatik.app.platform

import androidx.compose.runtime.Composable

actual object LlamaBridge {
    actual fun initModel(modelPath: String): Boolean {
        return false
    }

    actual fun embed(input: String): FloatArray {
        return floatArrayOf()
    }

    @Composable
    actual fun getModelPath(modelFileName: String): String {
        return ""
    }

    actual fun initGenerateModel(modelPath: String): Boolean {
        return false
    }

    actual fun generate(prompt: String): String {
        return ""
    }
}