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

    fun getDefaultGenerateModels(): List<LlamaModel> {
        return listOf(
            LlamaModel(
                name = "Gemma 3 270M Q8_0",
                fileName = "gemma-3-270m-Q8_0.gguf",
                sizeMb = 292,
                url = "https://huggingface.co/ggml-org/gemma-3-270m-GGUF/resolve/main/gemma-3-270m-Q8_0.gguf?download=true"
            ),
            LlamaModel(
                name = "SmolVLM 256M Instruct",
                fileName = "SmolVLM-256M-Instruct-Q8_0.gguf",
                sizeMb = 175,
                url = "https://huggingface.co/ggml-org/SmolVLM-256M-Instruct-GGUF/resolve/main/SmolVLM-256M-Instruct-Q8_0.gguf?download=true"
            ),
            LlamaModel(
                name = "SmolVLM 500M Instruct",
                fileName = "SmolVLM-500M-Instruct-Q8_0.gguf",
                sizeMb = 437,
                url = "https://huggingface.co/ggml-org/SmolVLM-500M-Instruct-GGUF/resolve/main/SmolVLM-500M-Instruct-Q8_0.gguf?download=true"
            ),
            LlamaModel(
                name = "Qwen 2.5 5B Instruct",
                fileName = "qwen2.5-1.5b-instruct-q2_k.gguf",
                sizeMb = 753,
                url = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q2_k.gguf?download=true"
            ),
            LlamaModel(
                name = "Phi-1_5 Q2 K",
                fileName = "phi-1_5-Q2_K.gguf",
                sizeMb = 613,
                url = "https://huggingface.co/TKDKid1000/phi-1_5-GGUF/resolve/main/phi-1_5-Q2_K.gguf?download=true"
            ),
            LlamaModel(
                name = "Llama 3.2 1B Instruct Q2 K",
                fileName = "Llama-3.2-1B-Instruct-Q2_K.gguf",
                sizeMb = 581,
                url = "https://huggingface.co/unsloth/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q2_K.gguf?download=true"
            ),
        )
    }

    fun getDefaultEmbedModels(): List<LlamaModel> {
        return listOf(
            LlamaModel(
                name = "Nomic Embed Text v1.5 Q4",
                fileName = "nomic-embed-text-v1.5.Q4_0.gguf",
                sizeMb = 77,
                url = "https://huggingface.co/nomic-ai/nomic-embed-text-v1.5-GGUF/resolve/main/nomic-embed-text-v1.5.Q4_0.gguf?download=true"
            ),
        )
    }
}
