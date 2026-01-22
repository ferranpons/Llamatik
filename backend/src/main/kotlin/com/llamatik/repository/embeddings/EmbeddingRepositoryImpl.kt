package com.llamatik.repository.embeddings

import com.llamatik.llama.LlamaService

class EmbeddingRepositoryImpl : EmbeddingRepository {
    override suspend fun getEmbedding(input: String): Result<FloatArray> {
        return LlamaService.embed(input)
    }
}
