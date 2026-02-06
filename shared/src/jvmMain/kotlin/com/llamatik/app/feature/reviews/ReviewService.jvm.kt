package com.llamatik.app.feature.reviews

actual typealias ReviewRequestContext = Any

actual fun createReviewService(): ReviewService = NoOpReviewService()

private class NoOpReviewService : ReviewService {
    override suspend fun requestReview(context: ReviewRequestContext): ReviewService.Result {
        return ReviewService.Result.NotAvailable("In-app review not supported on JVM target")
    }
}