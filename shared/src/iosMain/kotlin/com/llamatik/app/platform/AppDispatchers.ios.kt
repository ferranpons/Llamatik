package com.llamatik.app.platform

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

actual val AppDispatchersIO: CoroutineDispatcher = Dispatchers.IO