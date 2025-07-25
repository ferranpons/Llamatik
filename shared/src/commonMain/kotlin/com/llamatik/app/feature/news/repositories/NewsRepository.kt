package com.llamatik.app.feature.news.repositories

import com.llamatik.app.extensions.requestAndCatch
import com.llamatik.app.platform.ServiceClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode

private const val NEWS_RSS_PATH = "https://techcrunch.com/category/artificial-intelligence/feed/"

class NewsRepository(private val service: ServiceClient) {

    suspend fun getNews(): String {
        return service.httpClient.requestAndCatch(
            {
                this.get(NEWS_RSS_PATH).body()
            },
            {
                when (response.status) {
                    HttpStatusCode.BadRequest -> {
                        throw BadRequestException()
                    }

                    HttpStatusCode.Conflict -> {
                        throw ConflictException()
                    }

                    else -> throw this
                }
            }
        )
    }
}

class BadRequestException : Throwable()
class ConflictException : Throwable()
