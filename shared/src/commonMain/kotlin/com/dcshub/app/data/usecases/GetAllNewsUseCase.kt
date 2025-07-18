package com.dcshub.app.data.usecases

import com.dcshub.app.common.usecases.UseCase
import com.dcshub.app.data.repositories.FeedParser
import com.dcshub.app.data.repositories.FeedItem
import com.dcshub.app.data.repositories.NewsRepository

class GetAllNewsUseCase(
    private val newsRepository: NewsRepository
) : UseCase() {

    suspend fun invoke(): Result<List<FeedItem>> = runCatching {
        val news = newsRepository.getNews()
        return@runCatching FeedParser().parse(news)
    }
}
