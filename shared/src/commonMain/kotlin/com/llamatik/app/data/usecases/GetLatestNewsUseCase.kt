package com.llamatik.app.data.usecases

import com.llamatik.app.common.model.NewsModel
import com.llamatik.app.common.usecases.UseCase
import com.llamatik.app.data.repositories.LatestNewsMockRepository
import com.llamatik.app.feature.debugmenu.repositories.GlobalAppSettingsRepository

class GetLatestNewsUseCase(
    private val latestNewsMockRepository: LatestNewsMockRepository,
    private val globalAppSettingsRepository: GlobalAppSettingsRepository
) : UseCase() {

    suspend fun invoke(): Result<List<NewsModel>> = runCatching {
        return@runCatching if (globalAppSettingsRepository.isMockedContentEnabled()) {
            latestNewsMockRepository.getAds().getOrDefault(emptyList())
        } else {
            emptyList()
        }
    }
}
