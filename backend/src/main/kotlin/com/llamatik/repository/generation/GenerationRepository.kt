package com.llamatik.repository.generation

interface GenerationRepository {
    suspend fun generate(prompt: String): Result<String>
}
