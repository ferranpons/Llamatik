package com.llamatik.repository.generation

import com.llamatik.llama.LlamaService

class GenerationRepositoryImpl : GenerationRepository {
    override suspend fun generate(prompt: String): Result<String> {
        return LlamaService.generate(prompt)
    }
}
