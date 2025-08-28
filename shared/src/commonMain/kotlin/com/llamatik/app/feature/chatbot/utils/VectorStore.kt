package com.llamatik.app.feature.chatbot.utils

import co.touchlab.kermit.Logger
import com.llamatik.app.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

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

// --- Cleaning & formatting ---
fun cleanPdfText(raw: String): String = raw
    .replace("\r", "")
    .replace(Regex("-\\n"), "")                            // de-hyphenate wrapped words
    .replace(Regex("(?<![.!?:;])\\n(?!\\n)"), " ")         // unwrap single newlines
    .replace(Regex("\\n{3,}"), "\n\n")                     // collapse tall gaps
    .replace(Regex("[ \\t]{2,}"), " ")
    .trim()

fun tidyAnswer(s: String): String = s
    .replace(Regex("\\n{3,}"), "\n\n")
    .replace(Regex("[ \\t]{2,}"), " ")
    .trim()

// --- Query keyword extraction & filtering ---
private val STOPWORDS = setOf(
    "the","a","an","and","or","of","to","for","in","on","at","by","is","are","was","were",
    "be","with","as","that","this","it","from","your","you","we","our","us","can","could",
    "should","would","how","what","why","when","where","which","who","whom"
)

fun extractKeywords(s: String, minLen: Int = 3): Set<String> =
    Regex("[A-Za-z0-9][A-Za-z0-9_-]{${minLen-1},}")
        .findAll(s.lowercase())
        .map { it.value }
        .filterNot { it in STOPWORDS }
        .toSet()

fun containsAnyKeyword(text: String, kws: Set<String>): Boolean {
    if (kws.isEmpty()) return true
    val lower = text.lowercase()
    return kws.any { kw -> lower.contains(kw) }
}

// --- Metadata helpers (your metadata is JsonElement) ---
fun metaString(meta: Map<String, kotlinx.serialization.json.JsonElement>?, key: String): String? =
    meta?.get(key)?.toString()?.trim('"')

// --- Cosine over List<Float> returning Double (good for MMR math) ---
fun cosineD(a: List<Float>, b: List<Float>): Double {
    var dot = 0.0; var na = 0.0; var nb = 0.0
    val n = minOf(a.size, b.size)
    var i = 0
    while (i < n) {
        val x = a[i].toDouble(); val y = b[i].toDouble()
        dot += x * y; na += x * x; nb += y * y
        i++
    }
    return if (na == 0.0 || nb == 0.0) 0.0 else dot / (kotlin.math.sqrt(na) * kotlin.math.sqrt(nb))
}

// --- Scored wrapper + MMR ---
data class Scored(
    val item: VectorStoreItem,
    val initScore: Double,
)

fun mmr(
    queryVec: List<Float>,
    candidates: List<Scored>,
    topK: Int,
    lambda: Double = 0.7
): List<Scored> {
    val selected = mutableListOf<Scored>()
    val remaining = candidates.toMutableList()
    while (selected.size < topK && remaining.isNotEmpty()) {
        var bestIdx = 0
        var bestScore = Double.NEGATIVE_INFINITY
        for (i in remaining.indices) {
            val c = remaining[i]
            val rel = cosineD(queryVec, c.item.vector)
            val div = if (selected.isEmpty()) 0.0 else selected.maxOf { s ->
                cosineD(c.item.vector, s.item.vector)
            }
            val score = lambda * rel - (1 - lambda) * div
            if (score > bestScore) { bestScore = score; bestIdx = i }
        }
        selected += remaining.removeAt(bestIdx)
    }
    return selected
}

/**
 * Retrieve small, relevant, and diverse context chunks.
 *
 * @param queryVector  embedding of the user question
 * @param questionText raw user question (for keyword filtering)
 * @param vectorStore  your loaded VectorStoreData
 * @param poolSize     how many to pull from the store before filtering
 * @param topContext   final number of chunks returned to the prompt
 */
fun retrieveContext(
    queryVector: List<Float>,
    questionText: String,
    vectorStore: VectorStoreData,
    poolSize: Int = 30,
    topContext: Int = 3
): List<VectorStoreItem> {
    val keywordSet = extractKeywords(questionText)

    // 1) wider pool from current store scorer
    val pool: List<Scored> = vectorStore.items
        .asSequence()
        .map { it to cosineD(queryVector, it.vector) }
        .sortedByDescending { it.second }
        .take(poolSize)
        .map { (it, score) ->
            // normalize & trim text early so later prompt is clean
            it.copy(text = cleanPdfText(it.text)) to score
        }
        .map { (item, score) -> Scored(item, score) }
        .toList()

    if (pool.isEmpty()) return emptyList()

    // 2) pre-filter by query keywords (keeps obviously on-topic chunks)
    val filtered = pool.filter { s -> containsAnyKeyword(s.item.text, keywordSet) }
    val safe = if (filtered.isNotEmpty()) filtered else pool  // fallback if too strict

    // 3) MMR for relevance + diversity
    val reranked = mmr(queryVector, safe, topContext, lambda = 0.85)

    // 4) final trim: keep each chunk short so the prompt stays small
    return reranked.map { sc ->
        val t = sc.item.text.let { if (it.length <= 1000) it else it.take(1000) }
        sc.item.copy(text = t)
    }
}

fun buildPromptTight(
    question: String,
    contexts: List<String>
): String {
    val ctx = contexts.joinToString("\n\n---\n\n")
    return """
You are a careful assistant that answers **only** from the provided context.
If the context is insufficient, answer exactly: "I don't have enough information in my sources."

CONTEXT:
$ctx

QUESTION:
$question

INSTRUCTIONS:
- Be concise and factual (3–6 short sentences or up to 5 bullet points).
- Use plain text; avoid markdown lists like "1." unless it’s a real checklist.
- Cite nothing external; do not guess beyond the context.
""".trim()
}



