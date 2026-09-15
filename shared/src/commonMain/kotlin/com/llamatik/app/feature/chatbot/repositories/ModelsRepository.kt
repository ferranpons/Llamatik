package com.llamatik.app.feature.chatbot.repositories

import co.touchlab.kermit.Logger
import com.llamatik.app.feature.chatbot.model.LlamaModel
import com.llamatik.app.feature.chatbot.model.ModelCategory
import com.llamatik.app.feature.chatbot.model.ModelSource
import com.llamatik.app.feature.chatbot.utils.Gemma3
import com.llamatik.app.feature.chatbot.utils.Llama3Instruct
import com.llamatik.app.feature.chatbot.utils.Plain
import com.llamatik.app.feature.chatbot.utils.QwenChat
import com.llamatik.app.localization.getCurrentLocalization
import com.llamatik.app.platform.LlamatikTempFile
import com.llamatik.app.platform.PlatformInfo
import com.llamatik.app.platform.ServiceClient
import com.llamatik.app.platform.addBytesToFile
import com.llamatik.app.platform.writeToFile
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val DEFAULT_BUFFER_SIZE: Int = 64 * 1024
private const val USER_IMPORTED_MODELS_KEY = "llamatik_user_imported_models_v1"
private const val USER_CUSTOM_URL_MODELS_KEY = "llamatik_custom_url_models_v1"

@Serializable
private data class PersistedImportedModel(
    val name: String,
    @SerialName("local_path") val localPath: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("size_bytes") val sizeBytes: Long? = null,
    val quantization: String? = null,
    @SerialName("context_length") val contextLength: Int? = null,
    @SerialName("created_at") val createdAtEpochMs: Long? = null,
)

@Serializable
private data class ImportedModelStore(
    val models: List<PersistedImportedModel> = emptyList(),
)

@Serializable
private data class PersistedCustomUrlModel(
    val name: String,
    val url: String,
    val category: String,
    @SerialName("local_path") val localPath: String? = null,
)

@Serializable
private data class CustomUrlModelStore(
    val models: List<PersistedCustomUrlModel> = emptyList(),
)

class ModelsRepository(private val service: ServiceClient) {
    val localization = getCurrentLocalization()

    /**
     * Downloads [url] into a persistent local store.
     *
     * - Native targets: uses LlamatikTempFile appendBytes() (filesystem-backed).
     * - Web/WASM: writes directly to IndexedDB via suspend addBytesToFile(), awaiting each chunk.
     *
     * NOTE:
     * On WASM, returning LlamatikTempFile is mostly for API compatibility. The persisted content
     * is written under [fileName] key (your wasm writeToFile/addBytesToFile actuals).
     */
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
                val totalBytes = httpResponse.contentLength() ?: -1L
                var downloaded = 0L

                Logger.d("${localization.downloading} ${if (totalBytes > 0) "$totalBytes bytes" else "unknown size"}")

                if (PlatformInfo.isWasm) {
                    // overwrite/reset any previous partial file for this name
                    ByteArray(0).writeToFile(fileName)

                    while (!channel.isClosedForRead) {
                        ctx.ensureActive()

                        val packet = channel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
                        while (!packet.exhausted()) {
                            ctx.ensureActive()

                            val bytes = packet.readByteArray()
                            if (bytes.isEmpty()) break

                            downloaded += bytes.size
                            // IMPORTANT: suspend + await IndexedDB write
                            bytes.addBytesToFile(fileName)

                            onProgress?.invoke(downloaded, totalBytes)
                        }
                    }

                    Logger.d(localization.downloadFinished)
                    return@execute
                }

                // ---- Native path (existing behavior) ----
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
            runCatching { file.delete(file.absolutePath()) }
            throw e
        } catch (t: Throwable) {
            Logger.e(t) { "Download failed for $url" }
            runCatching { file.delete(file.absolutePath()) }
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
                systemPrompt = localization.defaultSystemPrompt.trimIndent()
            ),
            LlamaModel(
                name = "Gemma 3 1B Instruct Q4 KM",
                sizeMb = 806,
                url = "https://huggingface.co/ggml-org/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q4_K_M.gguf?download=true",
                template = Gemma3,
                systemPrompt = localization.defaultSystemPrompt.trimIndent()
            ),
            LlamaModel(
                name = "Qwen 3 1.7B Q8",
                sizeMb = 1830,
                url = "https://huggingface.co/Qwen/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q8_0.gguf?download=true",
                template = QwenChat,
                systemPrompt = localization.defaultSystemPrompt.trimIndent()
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
                systemPrompt = localization.defaultSystemPrompt.trimIndent()
            ),
            LlamaModel(
                name = "Phi-1_5 Q2 K",
                sizeMb = 613,
                url = "https://huggingface.co/TKDKid1000/phi-1_5-GGUF/resolve/main/phi-1_5-Q2_K.gguf?download=true",
                template = Plain,
                systemPrompt = localization.defaultSystemPrompt.trimIndent()
            ),
            LlamaModel(
                name = "Llama 3.2 1B Instruct Q2 K",
                sizeMb = 581,
                url = "https://huggingface.co/unsloth/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q2_K.gguf?download=true",
                template = Llama3Instruct,
                systemPrompt = localization.defaultSystemPrompt.trimIndent()
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

    fun getDefaultSTTModel(): List<LlamaModel> {
        return listOf(
            LlamaModel(
                name = "Whisper Base q8_0",
                sizeMb = 82,
                url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q8_0.bin?download=true",
                template = Plain,
                systemPrompt = localization.defaultSystemPrompt.trimIndent()
            ),
            LlamaModel(
                name = "Whisper Base",
                sizeMb = 148,
                url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin?download=true",
                template = Plain,
                systemPrompt = localization.defaultSystemPrompt.trimIndent()
            ),
            LlamaModel(
                name = "Whisper Tiny",
                sizeMb = 78,
                url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin?download=true",
                template = Plain,
                systemPrompt = localization.defaultSystemPrompt.trimIndent()
            ),
            LlamaModel(
                name = "Whisper Tiny q8_0",
                sizeMb = 44,
                url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny-q8_0.bin?download=true",
                template = Plain,
                systemPrompt = localization.defaultSystemPrompt.trimIndent()
            ),
            LlamaModel(
                name = "Whisper Small q8_0",
                sizeMb = 264,
                url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q8_0.bin?download=true",
                template = Plain,
                systemPrompt = localization.defaultSystemPrompt.trimIndent()
            ),
            LlamaModel(
                name = "Whisper Medium q8_0",
                sizeMb = 823,
                url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-medium-q8_0.bin?download=true",
                template = Plain,
                systemPrompt = localization.defaultSystemPrompt.trimIndent()
            ),
        )
    }

    fun getDefaultVlmModels(): List<LlamaModel> {
        return listOf(
            LlamaModel(
                name = "SmolVLM 256M Instruct (Vision)",
                sizeMb = 175,
                url = "https://huggingface.co/ggml-org/SmolVLM-256M-Instruct-GGUF/resolve/main/SmolVLM-256M-Instruct-Q8_0.gguf?download=true",
                template = Plain,
                systemPrompt = localization.smolVLM256SystemPrompt.trimIndent(),
                mmprojUrl = "https://huggingface.co/ggml-org/SmolVLM-256M-Instruct-GGUF/resolve/main/mmproj-SmolVLM-256M-Instruct-f16.gguf?download=true",
                mmprojSizeMb = 90,
            ),
            LlamaModel(
                name = "SmolVLM 500M Instruct (Vision)",
                sizeMb = 437,
                url = "https://huggingface.co/ggml-org/SmolVLM-500M-Instruct-GGUF/resolve/main/SmolVLM-500M-Instruct-Q8_0.gguf?download=true",
                template = Plain,
                systemPrompt = localization.smolVLM500SystemPrompt.trimIndent(),
                mmprojUrl = "https://huggingface.co/ggml-org/SmolVLM-500M-Instruct-GGUF/resolve/main/mmproj-SmolVLM-500M-Instruct-f16.gguf?download=true",
                mmprojSizeMb = 170,
            ),
        )
    }

    fun getDefaultStableDiffusionModels(): List<LlamaModel> {
        return listOf(
            LlamaModel(
                name = "Stable Diffusion v1.5 Q4_0",
                sizeMb = 1750,
                url = "https://huggingface.co/gpustack/stable-diffusion-v1-5-GGUF/resolve/main/stable-diffusion-v1-5-Q4_0.gguf?download=true",
                template = Plain,
                systemPrompt = localization.defaultSystemPrompt.trimIndent()
            ),
            LlamaModel(
                name = "SD Turbo v2.1 Q4_0",
                sizeMb = 2190,
                url = "https://huggingface.co/gpustack/stable-diffusion-v2-1-turbo-GGUF/resolve/main/stable-diffusion-v2-1-turbo_Q4_0.gguf?download=true",
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

    // ---- User-imported model persistence ----

    private val importedJson = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun getImportedModels(): List<LlamaModel> {
        val raw = Settings().getString(USER_IMPORTED_MODELS_KEY, "")
        if (raw.isBlank()) return emptyList()
        return runCatching {
            importedJson.decodeFromString(ImportedModelStore.serializer(), raw).models
                .map { it.toLlamaModel() }
        }.getOrElse { emptyList() }
    }

    fun saveImportedModel(model: LlamaModel) {
        require(model.source == ModelSource.UserImported) { "Only UserImported models may be saved here." }
        val store = readImportedStore()
        val updated = store.models
            .filterNot { it.name == model.name }
            .toMutableList()
            .apply { add(model.toPersistedImportedModel()) }
        writeImportedStore(ImportedModelStore(updated))
        saveModelPath(model.name, model.localPath ?: "")
    }

    fun deleteImportedModel(modelName: String) {
        val store = readImportedStore()
        writeImportedStore(ImportedModelStore(store.models.filterNot { it.name == modelName }))
        deleteModelPath(modelName)
    }

    private fun readImportedStore(): ImportedModelStore {
        val raw = Settings().getString(USER_IMPORTED_MODELS_KEY, "")
        if (raw.isBlank()) return ImportedModelStore()
        return runCatching {
            importedJson.decodeFromString(ImportedModelStore.serializer(), raw)
        }.getOrElse { ImportedModelStore() }
    }

    private fun writeImportedStore(store: ImportedModelStore) {
        Settings().putString(USER_IMPORTED_MODELS_KEY, importedJson.encodeToString(ImportedModelStore.serializer(), store))
    }

    // ---- Custom URL model persistence ----

    fun getCustomUrlModels(): List<Pair<LlamaModel, ModelCategory>> {
        val raw = Settings().getString(USER_CUSTOM_URL_MODELS_KEY, "")
        if (raw.isBlank()) return emptyList()
        return runCatching {
            importedJson.decodeFromString(CustomUrlModelStore.serializer(), raw).models
                .mapNotNull { persisted ->
                    val category = ModelCategory.fromKey(persisted.category) ?: return@mapNotNull null
                    val model = LlamaModel(
                        name = persisted.name,
                        url = persisted.url,
                        sizeMb = 0,
                        localPath = persisted.localPath,
                        source = ModelSource.CustomUrlDownload,
                    )
                    model to category
                }
        }.getOrElse { emptyList() }
    }

    fun saveCustomUrlModel(model: LlamaModel, category: ModelCategory) {
        val store = readCustomUrlStore()
        val updated = store.models
            .filterNot { it.name == model.name }
            .toMutableList()
            .apply {
                add(PersistedCustomUrlModel(name = model.name, url = model.url, category = category.key, localPath = model.localPath))
            }
        writeCustomUrlStore(CustomUrlModelStore(updated))
    }

    fun updateCustomUrlModelPath(modelName: String, localPath: String?) {
        val store = readCustomUrlStore()
        val updated = store.models.map { persisted ->
            if (persisted.name == modelName) persisted.copy(localPath = localPath) else persisted
        }
        writeCustomUrlStore(CustomUrlModelStore(updated))
    }

    fun deleteCustomUrlModel(modelName: String) {
        val store = readCustomUrlStore()
        writeCustomUrlStore(CustomUrlModelStore(store.models.filterNot { it.name == modelName }))
        deleteModelPath(modelName)
    }

    private fun readCustomUrlStore(): CustomUrlModelStore {
        val raw = Settings().getString(USER_CUSTOM_URL_MODELS_KEY, "")
        if (raw.isBlank()) return CustomUrlModelStore()
        return runCatching {
            importedJson.decodeFromString(CustomUrlModelStore.serializer(), raw)
        }.getOrElse { CustomUrlModelStore() }
    }

    private fun writeCustomUrlStore(store: CustomUrlModelStore) {
        Settings().putString(USER_CUSTOM_URL_MODELS_KEY, importedJson.encodeToString(CustomUrlModelStore.serializer(), store))
    }
}

private fun PersistedImportedModel.toLlamaModel() = LlamaModel(
    name = name,
    url = "",
    sizeMb = sizeBytes?.let { (it / 1_048_576).toInt() } ?: 0,
    localPath = localPath,
    source = ModelSource.UserImported,
    displayName = displayName,
    sizeBytes = sizeBytes,
    quantization = quantization,
    importedContextLength = contextLength,
    createdAtEpochMs = createdAtEpochMs,
)

private fun LlamaModel.toPersistedImportedModel() = PersistedImportedModel(
    name = name,
    localPath = localPath ?: "",
    displayName = displayName,
    sizeBytes = sizeBytes,
    quantization = quantization,
    contextLength = importedContextLength,
    createdAtEpochMs = createdAtEpochMs,
)
