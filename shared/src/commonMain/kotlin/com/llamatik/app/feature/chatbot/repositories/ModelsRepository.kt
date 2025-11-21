package com.llamatik.app.feature.chatbot.repositories

import co.touchlab.kermit.Logger
import com.llamatik.app.feature.chatbot.model.LlamaModel
import com.llamatik.app.platform.LlamatikTempFile
import com.llamatik.app.platform.ServiceClient
import com.russhwolf.settings.Settings
import io.ktor.client.call.body
import io.ktor.client.request.prepareGet
import io.ktor.http.contentLength
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.isEmpty
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining

private const val DEFAULT_BUFFER_SIZE: Int = 8 * 1024

class ModelsRepository(private val service: ServiceClient) {

    suspend fun downloadFileAndSave(
        url: String,
        fileName: String,
        onProgress: ((downloaded: Long, total: Long) -> Unit)? = null
    ): LlamatikTempFile {
        val file = LlamatikTempFile(fileName)
        service.httpClient.prepareGet(url).execute { httpResponse ->
            val channel: ByteReadChannel = httpResponse.body()
            val totalBytes = httpResponse.contentLength() ?: -1
            var downloaded: Long = 0
            Logger.d("Downloading ${httpResponse.contentLength()} bytes")
            while (!channel.isClosedForRead) {
                val packet = channel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
                while (!packet.isEmpty) {
                    val bytes = packet.readBytes()
                    downloaded += bytes.size
                    file.appendBytes(bytes)
                    onProgress?.invoke(downloaded, totalBytes)
                }
            }
            file.close()
            Logger.d("Download Finished")
        }
        return file
    }

    fun getDefaultGenerateModels(): List<LlamaModel> {
        return listOf(
            LlamaModel(
                name = "Gemma 3 270M Q8_0",
                sizeMb = 292,
                url = "https://huggingface.co/ggml-org/gemma-3-270m-GGUF/resolve/main/gemma-3-270m-Q8_0.gguf?download=true"
            ),
            LlamaModel(
                name = "SmolVLM 256M Instruct",
                sizeMb = 175,
                url = "https://huggingface.co/ggml-org/SmolVLM-256M-Instruct-GGUF/resolve/main/SmolVLM-256M-Instruct-Q8_0.gguf?download=true"
            ),
            LlamaModel(
                name = "SmolVLM 500M Instruct",
                sizeMb = 437,
                url = "https://huggingface.co/ggml-org/SmolVLM-500M-Instruct-GGUF/resolve/main/SmolVLM-500M-Instruct-Q8_0.gguf?download=true"
            ),
            LlamaModel(
                name = "Qwen 2.5 5B Instruct",
                sizeMb = 753,
                url = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q2_k.gguf?download=true"
            ),
            LlamaModel(
                name = "Phi-1_5 Q2 K",
                sizeMb = 613,
                url = "https://huggingface.co/TKDKid1000/phi-1_5-GGUF/resolve/main/phi-1_5-Q2_K.gguf?download=true"
            ),
            LlamaModel(
                name = "Llama 3.2 1B Instruct Q2 K",
                sizeMb = 581,
                url = "https://huggingface.co/unsloth/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q2_K.gguf?download=true"
            ),
        ) 
    }

    fun getDefaultEmbedModels(): List<LlamaModel> {
        return listOf(
            LlamaModel(
                name = "Nomic Embed Text v1.5 Q4",
                sizeMb = 77,
                url = "https://huggingface.co/nomic-ai/nomic-embed-text-v1.5-GGUF/resolve/main/nomic-embed-text-v1.5.Q4_0.gguf?download=true"
            ),
        )
    }

    fun getSavedModelPath(modelName: String): String {
        return Settings().getString(modelName, "")
    }

    fun saveModelPath(modelName: String, modelPath: String) {
        Settings().putString(modelName, modelPath)
    }
}
