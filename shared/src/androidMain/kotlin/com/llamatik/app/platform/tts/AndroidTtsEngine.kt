package com.llamatik.app.platform.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class AndroidTtsEngine(
    private val appContext: Context,
) : TtsEngine {

    private val ready = CompletableDeferred<Boolean>()
    private val tts: TextToSpeech

    @Volatile
    private var disposed = false

    override val isAvailable: Boolean
        get() = !disposed

    init {
        tts = TextToSpeech(appContext.applicationContext) { status ->
            // TextToSpeech.SUCCESS == 0
            ready.complete(status == TextToSpeech.SUCCESS)
        }.apply {
            // Best-effort default locale
            runCatching { language = Locale.getDefault() }
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = Unit
                @Deprecated("Deprecated in Android")
                override fun onError(utteranceId: String?) = Unit
                override fun onError(utteranceId: String?, errorCode: Int) = Unit
            })
        }
    }

    override suspend fun speak(text: String, interrupt: Boolean) {
        if (disposed) return
        val ok = runCatching { ready.await() }.getOrDefault(false)
        if (!ok) return

        val trimmed = text.trim()
        if (trimmed.isBlank()) return

        withContext(Dispatchers.Main) {
            if (interrupt) {
                runCatching { tts.stop() }
            }

            val utteranceId = "llamatik_${System.currentTimeMillis()}"
            val params = Bundle()

            // Prefer the modern speak() overload.
            runCatching {
                tts.speak(trimmed, TextToSpeech.QUEUE_ADD, params, utteranceId)
            }.onFailure {
                // Fallback to deprecated overload on very old devices.
                @Suppress("DEPRECATION")
                tts.speak(trimmed, if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null)
            }
        }
    }

    override fun stop() {
        if (disposed) return
        runCatching { tts.stop() }
    }

    fun dispose() {
        disposed = true
        runCatching { tts.shutdown() }
    }
}
