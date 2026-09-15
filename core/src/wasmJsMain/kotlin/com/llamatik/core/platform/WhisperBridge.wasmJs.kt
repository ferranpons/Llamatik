package com.llamatik.core.platform

actual object WhisperBridge {
    actual fun getModelPath(modelFileName: String): String = "/models/$modelFileName"
    actual fun initModel(modelPath: String): Boolean = false
    actual fun transcribeWav(wavPath: String, language: String?, initialPrompt: String?): String = ""
    actual fun transcribeWavSegments(wavPath: String, language: String?, initialPrompt: String?, translate: Boolean, diarize: Boolean): String =
        "{\"language\":\"\",\"segments\":[]}"
    actual fun release() {}
}
