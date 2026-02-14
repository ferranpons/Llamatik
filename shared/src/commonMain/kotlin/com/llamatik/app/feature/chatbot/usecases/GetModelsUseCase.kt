package com.llamatik.app.feature.chatbot.usecases

import com.llamatik.app.common.usecases.UseCase
import com.llamatik.app.feature.chatbot.model.LlamaModel
import com.llamatik.app.feature.chatbot.repositories.ModelsRepository
import com.llamatik.app.platform.LlamatikTempFile

class GetModelsUseCase(
    private val modelsRepository: ModelsRepository,
) : UseCase() {
    fun getDefaultEmbedModels(): Result<List<LlamaModel>> = runCatching {
        return@runCatching modelsRepository.getDefaultEmbedModels().map { model ->
            val localFilePath = modelsRepository.getSavedModelPath(modelName = model.name)
            if (localFilePath.isNotEmpty()) {
                model.copy(localPath = localFilePath)
            } else {
                model
            }
        }
    }

    fun getDefaultGenerateModels(): Result<List<LlamaModel>> = runCatching {
        return@runCatching modelsRepository.getDefaultGenerateModels().map { model ->
            val localFilePath = modelsRepository.getSavedModelPath(modelName = model.name)
            if (localFilePath.isNotEmpty()) {
                model.copy(localPath = localFilePath)
            } else {
                model
            }
        }
    }

    fun getDefaultSTTModels(): Result<List<LlamaModel>> = runCatching {
        return@runCatching modelsRepository.getDefaultSTTModel().map { model ->
            val localFilePath = modelsRepository.getSavedModelPath(modelName = model.name)
            if (localFilePath.isNotEmpty()) {
                model.copy(localPath = localFilePath)
            } else {
                model
            }
        }
    }

    fun getDefaultStableDiffusionModels(): Result<List<LlamaModel>> = runCatching {
        return@runCatching modelsRepository.getDefaultStableDiffusionModels().map { model ->
            val localFilePath = modelsRepository.getSavedModelPath(modelName = model.name)
            if (localFilePath.isNotEmpty()) {
                model.copy(localPath = localFilePath)
            } else {
                model
            }
        }
    }

    // --- Legacy-style download returning bytes + base64 (kept as-is) ---

    suspend fun downloadModel(modelUrl: String): Result<Pair<ByteArray?, String>> =
        runCatching {
            val fileName = extractFileName(modelUrl)
            val tempFile = modelsRepository.downloadFileAndSave(
                url = modelUrl,
                fileName = fileName
            )
            val bytes = tempFile.readBytes()
            val base64String = tempFile.readBase64String()
            if (bytes.isNotEmpty()) {
                return@runCatching Pair<ByteArray?, String>(
                    bytes,
                    base64String
                )
            } else {
                return@runCatching Pair<ByteArray?, String>(null, "")
            }
        }

    // --- Existing progress (Int %) API (you can keep this if used somewhere else) ---

    suspend fun downloadModel(
        model: LlamaModel,
        onProgress: (Int) -> Unit
    ): Result<LlamatikTempFile> =
        runCatching {
            val fileName = extractFileName(model.url)
            val file = modelsRepository.downloadFileAndSave(
                url = model.url,
                fileName = fileName
            ) { downloaded, total ->
                if (total > 0) {
                    val pct = ((downloaded.toDouble() / total.toDouble()) * 100.0)
                        .toInt()
                        .coerceIn(0, 100)
                    onProgress(pct)
                } else {
                    onProgress(0)
                }
            }
            return@runCatching file
        }

    // --- NEW: bytes + total version used by ChatBotViewModel.onDownloadModel ---

    suspend fun downloadModel(
        modelUrl: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<LlamatikTempFile> =
        runCatching {
            val fileName = extractFileName(modelUrl)
            val file = modelsRepository.downloadFileAndSave(
                url = modelUrl,
                fileName = fileName
            ) { downloaded, total ->
                // Just forward the raw numbers to the caller (ViewModel)
                onProgress(downloaded, total)
            }
            return@runCatching file
        }

    fun saveModelPath(modelName: String, modelPath: String): Result<Unit> =
        runCatching {
            modelsRepository.saveModelPath(modelName, modelPath)
        }

    fun getSavedModelPath(modelName: String): String = modelsRepository.getSavedModelPath(modelName)

    private fun extractFileName(url: String): String {
        val parts = url.split("/")
        return removeFileExtension(parts.last())
    }

    private fun removeFileExtension(filename: String): String {
        val lastIndex = filename.lastIndexOf(".")
        return if (lastIndex > 0) {
            filename.take(lastIndex)
        } else {
            filename
        }
    }

    fun deleteModelPath(model: LlamaModel) {
        modelsRepository.deleteModelPath(modelName = model.name)
    }
}