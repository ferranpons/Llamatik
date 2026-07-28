package com.llamatik.sdk.model

import com.llamatik.sdk.chat.Plain
import com.llamatik.sdk.chat.PromptTemplate

enum class ModelSource {
    BundledCatalog,
    DownloadedCatalog,
    UserImported,
}

data class LlamaModel(
    val name: String,
    val url: String,
    val sizeMb: Int,
    val fileName: String? = null,
    val localPath: String? = null,
    val template: PromptTemplate = Plain,
    val systemPrompt: String? = null,
    val mmprojUrl: String? = null,
    val mmprojFileName: String? = null,
    val mmprojSizeMb: Int = 0,
    val mmprojLocalPath: String? = null,
    val source: ModelSource = ModelSource.BundledCatalog,
    val displayName: String? = null,
    val sizeBytes: Long? = null,
    val quantization: String? = null,
    val importedContextLength: Int? = null,
    val createdAtEpochMs: Long? = null,
)

val LlamaModel.isVlm: Boolean get() = mmprojUrl != null
val LlamaModel.isUserImported: Boolean get() = source == ModelSource.UserImported
