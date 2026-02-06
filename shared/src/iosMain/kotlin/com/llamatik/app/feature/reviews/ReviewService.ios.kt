package com.llamatik.app.feature.reviews

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.StoreKit.SKStoreReviewController
import platform.UIKit.UIViewController

actual typealias ReviewRequestContext = UIViewController

actual fun createReviewService(): ReviewService = IosReviewService()

private class IosReviewService : ReviewService {
    override suspend fun requestReview(context: ReviewRequestContext): ReviewService.Result {
        // Apple decides whether to show the review dialog.
        return try {
            withContext(Dispatchers.Main) {
                val scene = context.view.window?.windowScene
                if (scene != null) {
                    SKStoreReviewController.requestReviewInScene(scene)
                } else {
                    SKStoreReviewController.requestReview()
                }
            }
            ReviewService.Result.ShownOrRequested
        } catch (t: Throwable) {
            ReviewService.Result.Failed(t.message ?: t.toString())
        }
    }
}