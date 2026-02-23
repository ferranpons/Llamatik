package com.llamatik.app.platform

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual val AppDispatchersIO: CoroutineDispatcher = Dispatchers.Default
