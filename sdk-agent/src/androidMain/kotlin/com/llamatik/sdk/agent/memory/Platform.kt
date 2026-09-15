package com.llamatik.sdk.agent.memory

import java.util.UUID

internal actual fun generateId(): String = UUID.randomUUID().toString().take(8)
internal actual fun currentTimeMs(): Long = System.currentTimeMillis()
