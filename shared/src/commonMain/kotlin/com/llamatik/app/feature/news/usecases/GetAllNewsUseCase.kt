package com.llamatik.app.feature.news.usecases

import com.llamatik.app.common.usecases.UseCase
import com.llamatik.app.feature.news.repositories.FeedItem
import com.llamatik.app.feature.news.repositories.NewsFeedParser
import com.llamatik.app.feature.news.repositories.NewsRepository

class GetAllNewsUseCase(
    private val newsRepository: NewsRepository
) : UseCase() {

    suspend fun invoke(): Result<List<FeedItem>> = runCatching {
        val news = newsRepository.getNews()
        return@runCatching NewsFeedParser().parse(news)
    }
}