package com.llamatik.sdk.model

data class GenerateSettings(
    val temperature: Float = 0.7f,
    val maxTokens: Int = 1024,
    val topP: Float = 0.95f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.1f,
    val contextLength: Int = 4096,
    val numThreads: Int = 4,
    val useMmap: Boolean = true,
    val flashAttention: Boolean = false,
    val batchSize: Int = 512,
)
