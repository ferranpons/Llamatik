package com.llamatik.library.platform

import com.llamatik.library.platform.whisper.whisper_stt_free_string
import com.llamatik.library.platform.whisper.whisper_stt_init
import com.llamatik.library.platform.whisper.whisper_stt_release
import com.llamatik.library.platform.whisper.whisper_stt_transcribe_wav
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.Foundation.NSFileManager
import platform.Foundation.NSLog

actual object WhisperBridge {

    actual fun getModelPath(modelFileName: String): String {
        // Not really used anymore now that models are downloaded,
        // but keep the signature for API symmetry.
        return modelFileName
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun initModel(modelPath: String): Boolean {
        // ✅ Avoid NSLog varargs formatting (%@) to prevent EXC_BAD_ACCESS
        NSLog("[WhisperBridge] initModel path=$modelPath")

        val fm = NSFileManager.defaultManager()
        val exists = fm.fileExistsAtPath(modelPath)

        if (!exists) {
            NSLog("[WhisperBridge] file does not exist at path=$modelPath")
            return false
        }

        val ok = whisper_stt_init(modelPath)
        if (ok == 0) {
            NSLog("[WhisperBridge] whisper_stt_init FAILED for path=$modelPath")
            return false
        }

        NSLog("[WhisperBridge] whisper_stt_init OK")
        return true
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun transcribeWav(wavPath: String, language: String?): String {
        val ptr = whisper_stt_transcribe_wav(wavPath, language) ?: return ""

        return try {
            ptr.toKString()
        } finally {
            whisper_stt_free_string(ptr)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun release() {
        whisper_stt_release()
    }
}
