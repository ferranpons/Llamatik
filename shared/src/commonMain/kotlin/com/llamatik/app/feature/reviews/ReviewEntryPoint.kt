package com.llamatik.app.feature.reviews

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatformTools

/**
 * Simple interop entry points callable from Android/iOS app shells.
 *
 * This avoids leaking platform types into most of the shared UI/business logic.
 */
object ReviewEntryPoint {
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Store the platform context used to show an in-app review later. */
    fun setContext(context: ReviewRequestContext) {
        ReviewContextHolder.set(context)
    }

    /** Clear the stored platform context (recommended on Android Activity destroy). */
    fun clearContext() {
        ReviewContextHolder.clear()
    }

    /**
     * Marks an app launch. This is safe to call without a context.
     *
     * The actual prompt will only be attempted when a "happy moment" is reported
     * (e.g., model successfully loaded, chat completed) and a context is available.
     */
    fun notifyAppLaunched() {
        mainScope.launch {
            runCatching {
                KoinPlatformTools.defaultContext().get().get<ReviewRequestManager>().onAppLaunched()
            }
        }
    }
}

internal object ReviewContextHolder {
    private var context: ReviewRequestContext? = null

    fun set(value: ReviewRequestContext) {
        context = value
    }

    fun get(): ReviewRequestContext? = context

    fun clear() {
        context = null
    }
}
