package com.llamatik.sdk.rag

import kotlinx.serialization.Serializable

@Serializable
data class PersistedRagStore(
    val pdfFileName: String,
    val createdAtEpochMs: Long,
    val vectorStore: VectorStoreData,
)
