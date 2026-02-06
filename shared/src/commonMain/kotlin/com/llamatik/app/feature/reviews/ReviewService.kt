package com.llamatik.app.feature.reviews

/**
 * Platform-independent entry point for requesting an in-app review.
 *
 * Notes:
 * - Android uses Play In-App Review and requires an [ReviewRequestContext] that is an Activity.
 * - iOS uses SKStoreReviewController. Apple decides whether the prompt is actually shown.
 * - Desktop is a no-op.
 */
interface ReviewService {

    sealed class Result {
        data object ShownOrRequested : Result()
        data class NotAvailable(val reason: String) : Result()
        data class Failed(val throwableMessage: String) : Result()
    }

    suspend fun requestReview(context: ReviewRequestContext): Result
}

/**
 * An opaque platform type required to request reviews.
 *
 * Android: Activity
 * iOS: UIViewController
 * Desktop: Any
 */
expect class ReviewRequestContext

/** Platform factory for dependency injection. */
expect fun createReviewService(): ReviewService