package com.dcshub.app.platform

import androidx.compose.runtime.Composable

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object LlamaBridge {
    @Composable
    fun getModelPath(modelFileName: String): String

    fun initModel(modelPath: String): Boolean
    fun embed(input: String): FloatArray
    fun initGenerateModel(modelPath: String): Boolean
    fun generate(prompt: String): String
}
