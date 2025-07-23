package com.llamatik.app.feature.chatbot.utils

import co.touchlab.kermit.Logger
import com.llamatik.app.platform.readResourceFile
import com.llamatik.app.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.math.sqrt

@Serializable
data class VectorStoreItem(
    val id: String,
    val text: String,
    val vector: List<Float>,
    val metadata: Map<String, JsonElement>? = null
)

@Serializable
data class VectorStoreData(
    val items: List<VectorStoreItem>
)

suspend fun loadVectorStoreData(): VectorStoreData {
    return withContext(Dispatchers.IO) {
        val jsonString = readResourceFile("vector_store_export_general.json")
        Json.decodeFromString<VectorStoreData>(jsonString)
    }
}

suspend fun loadVectorStoreEntries(): VectorStoreData = withContext(Dispatchers.Default) {
    val byteArray = Res.readBytes("files/vector_store_export_general.json")
    val jsonString = byteArray.decodeToString()

    val json = Json { ignoreUnknownKeys = true }
    val root = json.parseToJsonElement(jsonString)

    require(root is JsonArray) { "Expected a JSON array at the top level." }

    val items = mutableListOf<VectorStoreItem>()
    for (element in root) {
        try {
            val item = json.decodeFromJsonElement<VectorStoreItem>(element)
            items.add(item)
        } catch (e: Exception) {
            Logger.e("Error decoding item: ${e.message}")
        }
    }

    VectorStoreData(items)
}

fun cosineSimilarity(a: List<Float>, b: List<Float>): Float {
    val dot = a.zip(b).map { it.first * it.second }.sum()
    val normA = sqrt(a.map { it * it }.sum())
    val normB = sqrt(b.map { it * it }.sum())
    return if (normA == 0f || normB == 0f) 0f else dot / (normA * normB)
}

fun findTopKRelevantDocuments(
    queryVector: List<Float>,
    vectorStoreData: VectorStoreData,
    k: Int
): List<VectorStoreItem> {
    return vectorStoreData.items
        .map { item -> item to cosineSimilarity(queryVector, item.vector) }
        .sortedByDescending { it.second }
        .take(k)
        .map { it.first }
}

fun findTopKRelevantDocumentsDebug(
    queryVector: List<Float>,
    vectorStoreData: VectorStoreData,
    k: Int
): List<Pair<VectorStoreItem, Float>> {
    val similarities = vectorStoreData.items.map { item ->
        val similarity = cosineSimilarity(queryVector, item.vector)
        item to similarity
    }

    val topK = similarities
        .sortedByDescending { it.second }
        .take(k)

    println("🔍 Top $k similar documents:")
    topK.forEachIndexed { index, (item, score) ->
        println("${index + 1}. Score: $score | Metadata: ${item.metadata}")
        println(item.text)
    }

    return topK
}
