package com.llamatik.library.platform

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object WhisperBridge {
    fun getModelPath(modelFileName: String): String

    fun initModel(modelPath: String): Boolean
    fun transcribeWav(wavPath: String, language: String? = null): String
    fun release()
}
