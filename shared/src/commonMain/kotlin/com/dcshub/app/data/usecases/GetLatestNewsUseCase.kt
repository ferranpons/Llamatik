package com.dcshub.app.data.usecases

import com.dcshub.app.common.model.NewsModel
import com.dcshub.app.common.usecases.UseCase
import com.dcshub.app.data.repositories.LatestNewsMockRepository
import com.dcshub.app.feature.debugmenu.repositories.GlobalAppSettingsRepository

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
