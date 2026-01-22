package com.llamatik.util

import com.llamatik.library.platform.GenStream
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import java.io.Flushable
import java.io.Writer

/**
 * Minimal utilities for Server-Sent Events (SSE) streaming.
 *
 * We emit JSON lines as SSE data events:
 *   data: {...}
 *
 * The client can reassemble deltas until it receives a "done" event.
 */
object Sse {

    fun genStreamCallback(
        onDelta: (String) -> Boolean,
        onDone: () -> Boolean,
        onError: (String) -> Boolean
    ): GenStream = object : GenStream {
        override fun onDelta(text: String) {
            onDelta(text)
        }

        override fun onComplete() {
            onDone()
        }

        override fun onError(message: String) {
            onError(message)
        }
    }

    suspend fun pipe(
        writer: Writer,
        deltas: Channel<String>,
        done: Channel<Unit>,
        errors: Channel<String>
    ) {
        fun Writer.writeEvent(json: String) {
            write("data: ")
            write(json)
            write("\n\n")
            (this as? Flushable)?.flush()
        }

        while (true) {
            val outcome = select<String?> {
                deltas.onReceiveCatching { it.getOrNull() }
                errors.onReceiveCatching { err ->
                    val msg = err.getOrNull()
                    if (msg != null) "{\"event\":\"error\",\"message\":${jsonString(msg)}}" else null
                }
                done.onReceiveCatching {
                    "{\"event\":\"done\"}"
                }
            }

            if (outcome == null) break

            if (outcome.startsWith("{\"event\":\"error\"")) {
                writer.writeEvent(outcome)
                break
            }

            if (outcome == "{\"event\":\"done\"}") {
                writer.writeEvent(outcome)
                break
            }

            // delta
            writer.writeEvent("{\"event\":\"delta\",\"text\":${jsonString(outcome)}}")
        }

        deltas.close()
        done.close()
        errors.close()
    }

    private fun jsonString(value: String): String {
        // Minimal JSON string escaping (enough for SSE payloads).
        val escaped = buildString {
            for (c in value) {
                when (c) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(c)
                }
            }
        }
        return "\"$escaped\""
    }
}
