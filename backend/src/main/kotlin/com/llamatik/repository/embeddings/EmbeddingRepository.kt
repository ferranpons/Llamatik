package com.llamatik.repository.embeddings

interface EmbeddingRepository {
    suspend fun getEmbedding(input: String): Result<FloatArray>
}