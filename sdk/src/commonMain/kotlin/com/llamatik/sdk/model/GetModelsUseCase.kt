package com.llamatik.sdk.model

import com.llamatik.sdk.platform.LlamatikFileAccess
import com.llamatik.sdk.usecase.UseCase

class GetModelsUseCase(
    private val modelsRepository: ModelsRepository,
) : UseCase() {

    fun getDefaultEmbedModels(): Result<List<LlamaModel>> = runCatching {
        modelsRepository.getDefaultEmbedModels().map { model ->
            val localFilePath = modelsRepository.getSavedModelPath(model.name)
            if (localFilePath.isNotEmpty()) model.copy(localPath = localFilePath) else model
        }
    }

    fun getDefaultGenerateModels(): Result<List<LlamaModel>> = runCatching {
        modelsRepository.getDefaultGenerateModels().map { model ->
            val localFilePath = modelsRepository.getSavedModelPath(model.name)
            if (localFilePath.isNotEmpty()) model.copy(localPath = localFilePath) else model
        }
    }

    fun getDefaultSTTModels(): Result<List<LlamaModel>> = runCatching {
        modelsRepository.getDefaultSTTModel().map { model ->
            val localFilePath = modelsRepository.getSavedModelPath(model.name)
            if (localFilePath.isNotEmpty()) model.copy(localPath = localFilePath) else model
        }
    }

    fun getDefaultStableDiffusionModels(): Result<List<LlamaModel>> = runCatching {
        modelsRepository.getDefaultStableDiffusionModels().map { model ->
            val localFilePath = modelsRepository.getSavedModelPath(model.name)
            if (localFilePath.isNotEmpty()) model.copy(localPath = localFilePath) else model
        }
    }

    fun getDefaultVlmModels(): Result<List<LlamaModel>> = runCatching {
        modelsRepository.getDefaultVlmModels().map { model ->
            val localFilePath = modelsRepository.getSavedModelPath(model.name)
            val mmprojFilePath = modelsRepository.getSavedModelPath("${model.name}_mmproj")
            model.copy(
                localPath = localFilePath.takeIf { it.isNotEmpty() },
                mmprojLocalPath = mmprojFilePath.takeIf { it.isNotEmpty() },
            )
        }
    }

    suspend fun downloadModel(
        modelUrl: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): Result<LlamatikFileAccess.TempFileHandle> = runCatching {
        val fileName = extractFileName(modelUrl)
        modelsRepository.downloadFileAndSave(url = modelUrl, fileName = fileName) { downloaded, total ->
            onProgress(downloaded, total)
        }
    }

    fun saveModelPath(modelName: String, modelPath: String): Result<Unit> =
        runCatching { modelsRepository.saveModelPath(modelName, modelPath) }

    fun getSavedModelPath(modelName: String): String = modelsRepository.getSavedModelPath(modelName)

    fun deleteModelPath(model: LlamaModel) {
        modelsRepository.deleteModelPath(modelName = model.name)
    }

    fun getUserImportedModels(): Result<List<LlamaModel>> = runCatching {
        modelsRepository.getImportedModels().map { model ->
            val savedPath = modelsRepository.getSavedModelPath(model.name)
            if (savedPath.isNotEmpty()) model.copy(localPath = savedPath) else model
        }
    }

    private fun extractFileName(url: String): String {
        val parts = url.split("/")
        return removeFileExtension(parts.last())
    }

    private fun removeFileExtension(filename: String): String {
        val lastIndex = filename.lastIndexOf(".")
        return if (lastIndex > 0) filename.take(lastIndex) else filename
    }
}
