@file:OptIn(ExperimentalWasmJsInterop::class)

package com.llamatik.app.platform.tts

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class WasmTtsEngine : TtsEngine {

    private var activeCompletion: (() -> Unit)? = null

    override val isAvailable: Boolean
        get() = webSpeechSupported()

    override suspend fun speak(text: String, interrupt: Boolean) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        if (!isAvailable) return

        if (interrupt) {
            stop()
        }

        val lang = browserLanguage()

        suspendCancellableCoroutine<Unit> { cont ->
            var completed = false

            fun finish() {
                if (completed) return
                completed = true
                if (activeCompletion != null) {
                    activeCompletion = null
                }
                if (cont.isActive) {
                    cont.resume(Unit)
                }
            }

            activeCompletion = ::finish

            cont.invokeOnCancellation {
                if (activeCompletion === ::finish) {
                    activeCompletion = null
                }
                runCatching { webSpeechCancel() }
            }

            webSpeechSpeak(
                text = trimmed,
                lang = lang,
                onEnd = { finish() },
                onError = { _ -> finish() }
            )
        }
    }

    override fun stop() {
        val completion = activeCompletion
        activeCompletion = null
        runCatching { webSpeechCancel() }
        completion?.invoke()
    }
}

@JsFun(
    "() => typeof globalThis !== 'undefined' && !!globalThis.speechSynthesis && typeof globalThis.SpeechSynthesisUtterance !== 'undefined'"
)
private external fun webSpeechSupported(): Boolean

@JsFun(
    "() => { const nav = globalThis.navigator; return (nav && nav.language) ? String(nav.language) : 'en-US'; }"
)
private external fun browserLanguage(): String

@JsFun(
    "() => { try { globalThis.speechSynthesis.cancel(); } catch (e) {} }"
)
private external fun webSpeechCancel()

@JsFun(
    """
    (text, lang, onEnd, onError) => {
      try {
        const synth = globalThis.speechSynthesis;
        if (!synth || typeof globalThis.SpeechSynthesisUtterance === 'undefined') {
          onError('Web Speech API unavailable');
          return;
        }

        const utterance = new globalThis.SpeechSynthesisUtterance(text);
        if (lang) utterance.lang = lang;

        const pickVoice = () => {
          const voices = (typeof synth.getVoices === 'function') ? synth.getVoices() : [];
          if (!voices || !voices.length || !lang) return;

          const wanted = String(lang).toLowerCase();
          const base = wanted.split('-')[0];

          const exact = voices.find(v =>
            v && v.lang && String(v.lang).toLowerCase() === wanted
          );

          const partial = exact || voices.find(v =>
            v && v.lang && String(v.lang).toLowerCase().startsWith(base)
          );

          if (partial) utterance.voice = partial;
        };

        pickVoice();

        if (typeof synth.onvoiceschanged !== 'undefined') {
          const previous = synth.onvoiceschanged;
          synth.onvoiceschanged = () => {
            try { pickVoice(); } catch (_) {}
            if (typeof previous === 'function') {
              try { previous(); } catch (_) {}
            }
          };
        }

        let settled = false;

        const finishEnd = () => {
          if (settled) return;
          settled = true;
          onEnd();
        };

        const finishErr = (message) => {
          if (settled) return;
          settled = true;
          onError(String(message || 'speech error'));
        };

        utterance.onend = () => finishEnd();
        utterance.onerror = (event) => {
          finishErr(event && event.error ? event.error : 'speech error');
        };

        synth.speak(utterance);
      } catch (e) {
        onError(String(e && e.message ? e.message : e));
      }
    }
    """
)
private external fun webSpeechSpeak(
    text: String,
    lang: String,
    onEnd: () -> Unit,
    onError: (String) -> Unit
)
