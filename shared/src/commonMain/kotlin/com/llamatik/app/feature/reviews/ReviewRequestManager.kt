package com.llamatik.app.feature.reviews

import com.russhwolf.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Heuristics-based manager that decides when to show the in-app review prompt.
 *
 * Goals:
 * - Ask at a "happy moment" (user feels the app works).
 * - Ask rarely; never spam.
 * - Keep logic in shared code (Compose Multiplatform).
 */
@OptIn(ExperimentalTime::class)
class ReviewRequestManager(
    private val settings: Settings,
    private val reviewService: ReviewService,
) {

    suspend fun onAppLaunched() {
        val now = nowMillis()
        if (!settings.hasKey(KEY_FIRST_LAUNCH_MS)) {
            settings.putLong(KEY_FIRST_LAUNCH_MS, now)
        }
        val launches = settings.getInt(KEY_LAUNCH_COUNT, 0) + 1
        settings.putInt(KEY_LAUNCH_COUNT, launches)
    }

    /** Call when a generate model is successfully loaded (good success milestone). */
    suspend fun onGenerateModelLoaded() {
        val count = settings.getInt(KEY_MODEL_READY_COUNT, 0) + 1
        settings.putInt(KEY_MODEL_READY_COUNT, count)
        tryRequest(reason = "model_loaded")
    }

    /** Call when a response finishes successfully (another "happy moment"). */
    suspend fun onChatCompleted() {
        val count = settings.getInt(KEY_CHAT_SUCCESS_COUNT, 0) + 1
        settings.putInt(KEY_CHAT_SUCCESS_COUNT, count)
        tryRequest(reason = "chat_completed")
    }

    private suspend fun tryRequest(reason: String) {
        val context = ReviewContextHolder.get() ?: return
        if (!shouldAskNow()) return

        // Request MUST happen on main on both Android & iOS.
        val result = withContext(Dispatchers.Main) {
            reviewService.requestReview(context)
        }
        when (result) {
            is ReviewService.Result.ShownOrRequested -> {
                settings.putLong(KEY_LAST_REQUEST_MS, nowMillis())
                val asked = settings.getInt(KEY_ASKED_COUNT, 0) + 1
                settings.putInt(KEY_ASKED_COUNT, asked)
                settings.putString(KEY_LAST_REASON, reason)
            }

            else -> {
                // Don't update lastRequest on failures; we can try again later at a future happy moment.
            }
        }
    }

    private fun shouldAskNow(): Boolean {
        val now = nowMillis()
        val firstLaunch = settings.getLong(KEY_FIRST_LAUNCH_MS, 0L)
        val launches = settings.getInt(KEY_LAUNCH_COUNT, 0)
        val chats = settings.getInt(KEY_CHAT_SUCCESS_COUNT, 0)
        val modelReady = settings.getInt(KEY_MODEL_READY_COUNT, 0)
        val askedCount = settings.getInt(KEY_ASKED_COUNT, 0)
        val lastRequest = settings.getLong(KEY_LAST_REQUEST_MS, 0L)

        // Safety: never ask too frequently.
        if (askedCount >= MAX_TOTAL_REQUESTS) return false
        if (lastRequest != 0L && now - lastRequest < MIN_INTERVAL_MS) return false

        // Wait until the user has had time to try the app.
        if (firstLaunch != 0L && now - firstLaunch < MIN_FIRST_LAUNCH_AGE_MS) return false

        // Engagement thresholds.
        if (launches < MIN_LAUNCHES) return false
        if (chats < MIN_SUCCESSFUL_CHATS && modelReady < 1) return false

        return true
    }

    private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    private companion object {
        // Settings keys
        const val KEY_FIRST_LAUNCH_MS = "review.first_launch_ms"
        const val KEY_LAUNCH_COUNT = "review.launch_count"
        const val KEY_CHAT_SUCCESS_COUNT = "review.chat_success_count"
        const val KEY_MODEL_READY_COUNT = "review.model_ready_count"
        const val KEY_LAST_REQUEST_MS = "review.last_request_ms"
        const val KEY_ASKED_COUNT = "review.asked_count"
        const val KEY_LAST_REASON = "review.last_reason"

        // Heuristics
        const val MIN_LAUNCHES = 5
        const val MIN_SUCCESSFUL_CHATS = 3

        // Wait at least 2 days after first launch.
        const val MIN_FIRST_LAUNCH_AGE_MS = 2L * 24L * 60L * 60L * 1000L

        // Ask at most once every 60 days.
        const val MIN_INTERVAL_MS = 60L * 24L * 60L * 60L * 1000L

        // Ask at most 2 times lifetime.
        const val MAX_TOTAL_REQUESTS = 2
    }
}
