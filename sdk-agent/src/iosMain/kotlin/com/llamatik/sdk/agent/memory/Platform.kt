package com.llamatik.sdk.agent.memory

import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

internal actual fun generateId(): String {
    val bytes = Random.nextBytes(4)
    return bytes.joinToString("") { b ->
        val v = b.toInt() and 0xFF
        val hi = v shr 4
        val lo = v and 0xF
        val hex = "0123456789abcdef"
        "${hex[hi]}${hex[lo]}"
    }
}

@OptIn(ExperimentalTime::class)
internal actual fun currentTimeMs(): Long = Clock.System.now().toEpochMilliseconds()
