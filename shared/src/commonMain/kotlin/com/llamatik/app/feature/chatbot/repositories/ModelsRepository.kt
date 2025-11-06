package com.llamatik.app.feature.chatbot.repositories

import com.llamatik.app.extensions.requestAndCatch
import com.llamatik.app.feature.chatbot.model.LlamaModel
import com.llamatik.app.feature.news.repositories.BadRequestException
import com.llamatik.app.feature.news.repositories.ConflictException
import com.llamatik.app.platform.ServiceClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode

private const val MODELS_PATH = "https://www.llamatik.com/blog/index.xml"

class ModelsRepository(private val service: ServiceClient) {

    suspend fun getModel(): LlamaModel  {
        return service.httpClient.requestAndCatch(
            {
                this.get(MODELS_PATH).body()
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

    suspend fun getLocalModels(): List<LlamaModel> {
        return listOf(
            LlamaModel(
                name = "Gemma 3 270M Q8_0",
                fileName = "gemma_3_270m_Q8_0.gguf",
                sizeMb = 430,
                url = "https://your.cdn/models/gemma_3_270m_Q8_0.gguf"
            ),
            LlamaModel(
                name = "Llama 3.1 8B Q4_0",
                fileName = "llama-3.1-8b-instruct.Q4_0.gguf",
                sizeMb = 4100,
                url = "https://your.cdn/models/llama-3.1-8b-instruct.Q4_0.gguf"
            ),
            LlamaModel(
                name = "Phi-3 mini Q4_0",
                fileName = "phi-3-mini-4k-instruct.Q4_0.gguf",
                sizeMb = 1100,
                url = "https://your.cdn/models/phi-3-mini-4k-instruct.Q4_0.gguf"
            )
        )
    }
}
