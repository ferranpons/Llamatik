package com.llamatik.app.data.usecases

import com.llamatik.app.common.usecases.UseCase
import com.llamatik.app.data.repositories.FeedItem
import com.llamatik.app.data.repositories.FeedParser
import com.llamatik.app.data.repositories.NewsRepository

class GetAllNewsUseCase(
    private val newsRepository: NewsRepository
) : UseCase() {

    suspend fun invoke(): Result<List<FeedItem>> = runCatching {
        val news = newsRepository.getNews()
        return@runCatching FeedParser().parse(news)
    }
}
