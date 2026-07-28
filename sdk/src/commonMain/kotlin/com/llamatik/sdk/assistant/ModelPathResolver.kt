package com.llamatik.sdk.assistant

import com.llamatik.sdk.model.LlamaModel

interface ModelPathResolver {
    fun resolve(model: LlamaModel): String?
    fun resolveMmproj(model: LlamaModel): String?
    fun persistDownloaded(model: LlamaModel, downloadedLocalPath: String): String
    fun saveModelPath(modelName: String, path: String)
    fun clearSavedPath(modelName: String)
    fun deleteFile(modelName: String, path: String)
}
