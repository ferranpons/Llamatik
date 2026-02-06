package com.llamatik.app.feature.reviews

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

/**
 * Simple interop entry points callable from Android/iOS app shells.
 *
 * We avoid Koin global lookups here to stay compatible with Koin 4.x
 * (GlobalContext is no longer available).
 */
object ReviewEntryPoint {
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile
    private var manager: ReviewRequestManager? = null

    /**
     * Provide the shared [ReviewRequestManager] after DI is initialized.
     *
     * Call this once:
     * - Android: in Application.onCreate() after DependencyContainer.initialize(...)
     * - iOS: after doInitKoin()
     */
    fun setManager(reviewRequestManager: ReviewRequestManager) {
        manager = reviewRequestManager
    }

    /** Store the platform context used to show an in-app review later. */
    fun setContext(context: ReviewRequestContext) {
        ReviewContextHolder.set(context)
    }

    /** Clear the stored platform context (recommended on Android Activity destroy). */
    fun clearContext() {
        ReviewContextHolder.clear()
    }

    /**
     * Marks an app launch. Safe to call without a context.
     *
     * The actual prompt will only be attempted when a "happy moment" is reported
     * (e.g., model successfully loaded, chat completed) and a context is available.
     */
    fun notifyAppLaunched() {
        val m = manager ?: return
        mainScope.launch {
            runCatching { m.onAppLaunched() }
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
