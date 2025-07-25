package com.llamatik.library.platform

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
actual object LlamaBridge {
    @androidx.compose.runtime.Composable
    actual fun getModelPath(modelFileName: String): String {
        TODO("Not yet implemented")
    }

    actual fun initModel(modelPath: String): Boolean {
        TODO("Not yet implemented")
    }

    actual fun embed(input: String): FloatArray {
        TODO("Not yet implemented")
    }

    actual fun initGenerateModel(modelPath: String): Boolean {
        TODO("Not yet implemented")
    }

    actual fun generate(prompt: String): String {
        TODO("Not yet implemented")
    }
}