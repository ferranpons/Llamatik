package com.llamatik.app.platform

import com.llamatik.sdk.assistant.ModelPathResolver
import com.llamatik.sdk.model.GetModelsUseCase
import com.llamatik.sdk.model.LlamaModel

class ModelPathResolverAdapter(
    private val getModelsUseCase: GetModelsUseCase,
) : ModelPathResolver {

    override fun resolve(model: LlamaModel): String? {
        val pathFromState = model.localPath
        val pathFromStorage = getModelsUseCase.getSavedModelPath(model.name).takeIf { it.isNotEmpty() }
        val rawPath = pathFromState ?: pathFromStorage
        if (rawPath.isNullOrBlank()) return null

        if (PlatformInfo.isWasm) return rawPath

        val migrated = migrateModelPathIfNeeded(
            modelNameOrFileName = model.name,
            savedPath = rawPath,
        )

        if (migrated.isBlank()) {
            runCatching { getModelsUseCase.clearSavedPath(model.name) }
            return null
        }

        if (migrated != rawPath) {
            runCatching { getModelsUseCase.saveModelPath(model.name, migrated) }
        }

        return migrated
    }

    override fun resolveMmproj(model: LlamaModel): String? {
        val mmprojKey = "${model.name}_mmproj"
        val rawPath = (model.mmprojLocalPath?.takeIf { it.isNotEmpty() }
            ?: getModelsUseCase.getSavedModelPath(mmprojKey).takeIf { it.isNotEmpty() })
            ?: return null

        if (PlatformInfo.isWasm) return rawPath

        val migrated = migrateModelPathIfNeeded(
            modelNameOrFileName = mmprojKey,
            savedPath = rawPath,
        )

        if (migrated.isNotBlank() && migrated != rawPath) {
            runCatching { getModelsUseCase.saveModelPath(mmprojKey, migrated) }
        }

        return migrated.takeIf { it.isNotBlank() }
    }

    override fun persistDownloaded(model: LlamaModel, downloadedLocalPath: String): String {
        return if (PlatformInfo.isWasm) {
            model.url.urlToFileName()
        } else {
            downloadedLocalPath
        }
    }

    override fun saveModelPath(modelName: String, path: String) {
        getModelsUseCase.saveModelPath(modelName, path)
    }

    override fun clearSavedPath(modelName: String) {
        getModelsUseCase.clearSavedPath(modelName)
    }

    override fun deleteFile(modelName: String, path: String) {
        LlamatikTempFile(modelName).delete(path)
    }

    private fun String.urlToFileName(): String {
        val filename = this.substring(this.lastIndexOf("/") + 1).removeSuffix()
        return net.thauvin.erik.urlencoder.UrlEncoderUtil.decode(filename)
    }

    private fun String.removeSuffix(): String {
        val lastIndex = this.lastIndexOf('.')
        return if (lastIndex != -1) this.substring(0, lastIndex) else this
    }
}
