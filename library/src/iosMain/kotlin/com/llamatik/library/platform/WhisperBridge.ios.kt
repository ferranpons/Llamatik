@file:OptIn(ExperimentalForeignApi::class)

package com.llamatik.library.platform

import androidx.compose.runtime.Composable
import com.llamatik.library.platform.whisper.whisper_stt_init
import com.llamatik.library.platform.whisper.whisper_stt_release
import com.llamatik.library.platform.whisper.whisper_stt_transcribe_wav
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString

actual object WhisperBridge {
    @Composable
    actual fun getModelPath(modelFileName: String): String {
        // iOS: models should be available in app documents/cache after download.
        return modelFileName
    }

    actual fun initModel(modelPath: String): Boolean =
        whisper_stt_init(modelPath) != 0

    actual fun transcribeWav(wavPath: String, language: String?): String {
        val ptr = whisper_stt_transcribe_wav(wavPath, language)
        return ptr?.toKString() ?: ""
    }

    actual fun release() = whisper_stt_release()
}
