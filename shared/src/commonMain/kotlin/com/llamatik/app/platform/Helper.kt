package com.llamatik.app.platform

import com.llamatik.app.di.appModule
import com.llamatik.app.feature.reviews.ReviewEntryPoint
import com.llamatik.app.feature.reviews.ReviewRequestManager
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatformTools

fun initKoin() {
    startKoin {
        modules(appModule())
    }

    // Bind ReviewRequestManager into ReviewEntryPoint after Koin is started.
    bindReviewEntryPointToKoin()
}

private fun bindReviewEntryPointToKoin() {
    runCatching {
        val koin = KoinPlatformTools.defaultContext().get()
        val manager = koin.get<ReviewRequestManager>()
        ReviewEntryPoint.setManager(manager)
    }
}
