package com.llamatik.app.feature.chatbot.repositories

import co.touchlab.kermit.Logger
import com.llamatik.app.feature.chatbot.model.LlamaModel
import com.llamatik.app.feature.chatbot.utils.Gemma3
import com.llamatik.app.feature.chatbot.utils.Llama3Instruct
import com.llamatik.app.feature.chatbot.utils.Plain
import com.llamatik.app.feature.chatbot.utils.QwenChat
import com.llamatik.app.localization.getCurrentLocalization
import com.llamatik.app.platform.LlamatikTempFile
import com.llamatik.app.platform.ServiceClient
import com.russhwolf.settings.Settings
import io.ktor.client.call.body
import io.ktor.client.request.prepareGet
import io.ktor.http.contentLength
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.io.readByteArray

private const val DEFAULT_BUFFER_SIZE: Int = 64 * 1024

class ModelsRepository(private val service: ServiceClient) {
    val localization = getCurrentLocalization()

    suspend fun downloadFileAndSave(
        url: String,
        fileName: String,
        onProgress: ((downloaded: Long, total: Long) -> Unit)? = null
    ): LlamatikTempFile {
        val file = LlamatikTempFile(fileName)
        val ctx = currentCoroutineContext()

        try {
            service.httpClient.prepareGet(url).execute { httpResponse ->
                val channel: ByteReadChannel = httpResponse.body()
                val totalBytes = httpResponse.contentLength() ?: -1
                var downloaded: Long = 0
                Logger.d("${localization.downloading} ${httpResponse.contentLength()} bytes")

                while (!channel.isClosedForRead) {
                    ctx.ensureActive()

                    val packet = channel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
                    while (!packet.exhausted()) {
                        ctx.ensureActive()

                        val bytes = packet.readByteArray()
                        if (bytes.isEmpty()) break

                        downloaded += bytes.size
                        file.appendBytes(bytes)
                        onProgress?.invoke(downloaded, totalBytes)
                    }
                }

                file.close()
                Logger.d(localization.downloadFinished)
            }

            return file
        } catch (e: CancellationException) {
            Logger.d(e) { "Download cancelled for $url" }
            runCatching { LlamatikTempFile(fileName).delete(file.absolutePath()) }
            throw e
        } catch (t: Throwable) {
            Logger.e(t) { "Download failed for $url" }
            runCatching { LlamatikTempFile(fileName).delete(file.absolutePath()) }
            throw t
        }
    }

    fun getDefaultGenerateModels(): List<LlamaModel> {
        return listOf(
            LlamaModel(
                name = "Gemma 3 270M Instruct Q8_0",
                sizeMb = 292,
                url = "https://huggingface.co/ggml-org/gemma-3-270m-it-GGUF/resolve/main/gemma-3-270m-it-Q8_0.gguf?download=true",
                template = Gemma3,
                systemPrompt = localization.gemma3SystemPrompt.trimIndent()
            ),
            LlamaModel(
                name = "SmolVLM 256M Instruct",
                sizeMb = 175,
                url = "https://huggingface.co/ggml-org/SmolVLM-256M-Instruct-GGUF/resolve/main/SmolVLM-256M-Instruct-Q8_0.gguf?download=true",
                template = Plain,
                systemPrompt = localization.smolVLM256SystemPrompt.trimIndent()
            ),
            LlamaModel(
                name = "SmolVLM 500M Instruct",
                sizeMb = 437,
                url = "https://huggingface.co/ggml-org/SmolVLM-500M-Instruct-GGUF/resolve/main/SmolVLM-500M-Instruct-Q8_0.gguf?download=true",
                template = Plain,
                systemPrompt = localization.smolVLM500SystemPrompt.trimIndent()
            ),
            LlamaModel(
                name = "Qwen 2.5 5B Instruct",
                sizeMb = 753,
                url = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q2_k.gguf?download=true",
                template = QwenChat,
                systemPrompt = localization.qwen25BSystemPrompt.trimIndent()
            ),
            LlamaModel(
                name = "Phi-1_5 Q2 K",
                sizeMb = 613,
                url = "https://huggingface.co/TKDKid1000/phi-1_5-GGUF/resolve/main/phi-1_5-Q2_K.gguf?download=true",
                template = Plain,
                systemPrompt = localization.phi15SystemPrompt.trimIndent()
            ),
            LlamaModel(
                name = "Llama 3.2 1B Instruct Q2 K",
                sizeMb = 581,
                url = "https://huggingface.co/unsloth/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q2_K.gguf?download=true",
                template = Llama3Instruct,
                systemPrompt = localization.llama32SystemPrompt.trimIndent()
            ),
        )
    }

    fun getDefaultEmbedModels(): List<LlamaModel> {
        return listOf(
            LlamaModel(
                name = "Nomic Embed Text v1.5 Q4",
                sizeMb = 77,
                url = "https://huggingface.co/nomic-ai/nomic-embed-text-v1.5-GGUF/resolve/main/nomic-embed-text-v1.5.Q4_0.gguf?download=true",
                template = Plain,
                systemPrompt = localization.defaultSystemPrompt.trimIndent()
            ),
        )
    }

    fun getSavedModelPath(modelName: String): String {
        return Settings().getString(modelName, "")
    }

    fun saveModelPath(modelName: String, modelPath: String) {
        Settings().putString(modelName, modelPath)
    }

    fun deleteModelPath(modelName: String) {
        Settings().remove(modelName)
    }
}
