package com.llamatik.app.feature.chatbot.model

import com.llamatik.app.feature.chatbot.utils.Plain
import com.llamatik.app.feature.chatbot.utils.PromptTemplate

data class LlamaModel(
    val name: String,
    val url: String,
    val sizeMb: Int,
    val fileName: String? = null,
    val localPath: String? = null,
    val template: PromptTemplate = Plain,
    val systemPrompt: String? = null,
    // VLM-specific: companion mmproj model required for multimodal inference
    val mmprojUrl: String? = null,
    val mmprojFileName: String? = null,
    val mmprojSizeMb: Int = 0,
    val mmprojLocalPath: String? = null,
)

val LlamaModel.isVlm: Boolean get() = mmprojUrl != null
