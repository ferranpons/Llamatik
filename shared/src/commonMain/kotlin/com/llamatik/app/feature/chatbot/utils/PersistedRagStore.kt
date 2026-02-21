package com.llamatik.app.feature.chatbot.utils

import kotlinx.serialization.Serializable

/**
 * Persisted PDF RAG store saved on-device.
 *
 * We keep it intentionally small (we cap chunks during indexing) so that JSON persistence
 * remains fast and portable across KMP targets.
 */
@Serializable
data class PersistedRagStore(
    val pdfFileName: String,
    val createdAtEpochMs: Long,
    val vectorStore: VectorStoreData,
)
