package com.llamatik.app.feature.reviews

import android.app.Activity
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.tasks.await

actual typealias ReviewRequestContext = Activity

actual fun createReviewService(): ReviewService = AndroidReviewService()

private class AndroidReviewService : ReviewService {
    override suspend fun requestReview(context: ReviewRequestContext): ReviewService.Result {
        return try {
            val manager = ReviewManagerFactory.create(context)
            val reviewInfo = manager.requestReviewFlow().await()
            // launchReviewFlow returns a Task<Void>. We don't need its value, just await completion.
            manager.launchReviewFlow(context, reviewInfo).await()
            ReviewService.Result.ShownOrRequested
        } catch (t: Throwable) {
            ReviewService.Result.Failed(t.message ?: t.toString())
        }
    }
}