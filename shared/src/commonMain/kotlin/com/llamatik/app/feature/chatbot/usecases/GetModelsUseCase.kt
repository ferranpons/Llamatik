package com.llamatik.app.feature.chatbot.usecases

import com.llamatik.app.common.usecases.UseCase
import com.llamatik.app.feature.chatbot.model.LlamaModel
import com.llamatik.app.feature.chatbot.repositories.ModelsRepository

class GetModelsUseCase(
    private val modelsRepository: ModelsRepository,
) : UseCase() {
    suspend fun invoke(): Result<List<LlamaModel>> = runCatching {
        val models = modelsRepository.getLocalModels()
        return@runCatching models
    }
}
