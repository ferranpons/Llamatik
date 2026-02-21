package com.llamatik.app.platform

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Multiplatform dispatcher for "background" work.
 * - JVM/Android: Dispatchers.IO
 * - iOS/Wasm: Dispatchers.Default (or Main if you want strictly single-thread)
 */
expect val AppDispatchersIO: CoroutineDispatcher
