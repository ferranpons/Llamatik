package com.llamatik.sdk.agent.memory

import kotlin.random.Random

private fun jsDateNow(): Double = js("Date.now()")

internal actual fun generateId(): String {
    val bytes = Random.nextBytes(4)
    return bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}

internal actual fun currentTimeMs(): Long = jsDateNow().toLong()
