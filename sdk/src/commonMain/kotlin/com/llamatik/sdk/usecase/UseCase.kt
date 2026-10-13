package com.llamatik.sdk.usecase

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

open class UseCase {
    suspend fun <R> execute(
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
        code: suspend CoroutineScope.() -> R,
    ): R = withContext(dispatcher) { code() }
}
