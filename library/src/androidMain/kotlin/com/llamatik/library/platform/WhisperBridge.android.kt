package com.llamatik.library.platform

actual object WhisperBridge {
    init {
        System.loadLibrary("llama_jni")
    }

    actual fun getModelPath(modelFileName: String): String = modelFileName
    actual external fun initModel(modelPath: String): Boolean
    actual external fun transcribeWav(wavPath: String, language: String?): String
    actual external fun release()
}
