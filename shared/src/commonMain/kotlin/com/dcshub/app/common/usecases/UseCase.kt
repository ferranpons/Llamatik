package com.dcshub.app.common.usecases

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

open class UseCase {

    /**
     * This function is responsible for running the Use Case's code block into a Background Thread
     * [Documentation](https://developer.android.com/kotlin/coroutines/coroutines-best-practices#main-safe)
     */
    suspend fun <R> execute(
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
        code: suspend CoroutineScope.() -> R
    ): R = withContext(dispatcher) { code() }
}
