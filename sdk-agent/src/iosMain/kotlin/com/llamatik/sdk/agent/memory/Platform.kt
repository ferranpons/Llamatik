package com.llamatik.sdk.agent.memory

import kotlin.random.Random
import kotlin.time.ExperimentalTime
import kotlin.time.Clock

internal actual fun generateId(): String {
    val bytes = Random.nextBytes(4)
    return bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}

@OptIn(ExperimentalTime::class)
internal actual fun currentTimeMs(): Long = Clock.System.now().toEpochMilliseconds()
