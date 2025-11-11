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
}
