package com.llamatik.app.feature.chatbot.usecases

import com.llamatik.app.common.usecases.UseCase
import com.llamatik.app.feature.chatbot.model.LlamaModel
import com.llamatik.app.feature.chatbot.repositories.ModelsRepository

class GetModelsUseCase(
    private val modelsRepository: ModelsRepository,
) : UseCase() {
    fun getDefaultEmbedModels(): Result<List<LlamaModel>> = runCatching {
        val models = modelsRepository.getDefaultEmbedModels()
        return@runCatching models
    }

    fun getDefaultGenerateModels(): Result<List<LlamaModel>> = runCatching {
        val models = modelsRepository.getDefaultGenerateModels()
        return@runCatching models
    }

    suspend fun downloadModel(modelUrl: String): Result<Pair<ByteArray?, String>> =
        runCatching {
            val fileName = extractFileName(modelUrl)
            val tempFile = modelsRepository.downloadFileAndSave(url = modelUrl, fileName = fileName)
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

    suspend fun downloadModel(
        model: LlamaModel,
        onProgress: (Int) -> Unit
    ): Result<Pair<ByteArray?, String>> =
        runCatching {
            val fileName = extractFileName(model.url)
            val tempFile = modelsRepository.downloadFileAndSave(
                url = model.url,
                fileName = fileName
            ) { downloaded, total ->
                if (total > 0) {
                    val pct = ((downloaded.toDouble() / total.toDouble()) * 100.0)
                        .toInt()
                        .coerceIn(0, 100)
                    onProgress(pct)
                } else {
                    // Unknown total; keep as 0 (indeterminate)
                    onProgress(0)
                }
            }
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

    private fun extractFileName(url: String): String {
        val parts = url.split("/")
        return removeFileExtension(parts.last())
    }

    private fun removeFileExtension(filename: String): String {
        val lastIndex = filename.lastIndexOf(".")
        return if (lastIndex > 0) {
            filename.substring(0, lastIndex)
        } else {
            filename
        }
    }
}
