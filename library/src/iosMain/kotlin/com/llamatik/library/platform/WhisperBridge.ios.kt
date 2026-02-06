package com.llamatik.library.platform

import androidx.compose.runtime.Composable

actual object WhisperBridge {
    @Composable
    actual fun getModelPath(modelFileName: String): String {
        // iOS: models should be available in app documents/cache after download.
        return modelFileName
    }

    actual fun initModel(modelPath: String): Boolean =
        whisper_stt_init(modelPath) != 0

    actual fun transcribeWav(wavPath: String, language: String?): String =
        whisper_stt_transcribe_wav(wavPath, language)

    actual fun release() = whisper_stt_release()
}
