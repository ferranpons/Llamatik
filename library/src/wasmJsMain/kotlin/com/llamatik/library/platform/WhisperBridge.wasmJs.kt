package com.llamatik.library.platform

import androidx.compose.runtime.Composable

actual object WhisperBridge {
    @Composable
    actual fun getModelPath(modelFileName: String): String = "/models/$modelFileName"
    actual fun initModel(modelPath: String): Boolean = false
    actual fun transcribeWav(wavPath: String, language: String?): String =
        "Whisper WASM not wired yet."
    actual fun release() {}
}
