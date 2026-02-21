package com.llamatik.app.feature.chatbot.utils

/**
 * Lightweight text chunker similar to LangChain's RecursiveCharacterTextSplitter.
 *
 * - chunkSize / chunkOverlap are in characters (not tokens).
 * - It tries to split on paragraph boundaries first, then falls back to hard cuts.
 */
fun chunkText(
    text: String,
    chunkSize: Int = 1000,
    chunkOverlap: Int = 200
): List<String> {
    val clean = text.replace("\r\n", "\n").trim()
    if (clean.isEmpty()) return emptyList()
    if (chunkSize <= 0) return listOf(clean)
    val overlap = chunkOverlap.coerceIn(0, chunkSize / 2)

    // Split into paragraphs first
    val paragraphs = clean
        .split(Regex("\n{2,}"))
        .map { it.trim() }
        .filter { it.isNotBlank() }

    val chunks = mutableListOf<String>()
    var buffer = StringBuilder()

    fun flushBuffer() {
        val s = buffer.toString().trim()
        buffer = StringBuilder()
        if (s.isNotBlank()) chunks.add(s)
    }

    for (p in paragraphs) {
        if (buffer.isEmpty()) {
            buffer.append(p)
        } else {
            // If adding this paragraph stays under limit, add it.
            if (buffer.length + 2 + p.length <= chunkSize) {
                buffer.append("\n\n").append(p)
            } else {
                // Flush current chunk, then start new with paragraph
                flushBuffer()
                // Paragraph can be larger than chunkSize -> hard split it.
                if (p.length <= chunkSize) {
                    buffer.append(p)
                } else {
                    chunks += hardSplitWithOverlap(p, chunkSize, overlap)
                }
            }
        }
    }
    flushBuffer()

    // Final safety: ensure no chunk exceeds chunkSize by hard splitting any large ones
    val finalChunks = mutableListOf<String>()
    for (c in chunks) {
        if (c.length <= chunkSize) {
            finalChunks += c
        } else {
            finalChunks += hardSplitWithOverlap(c, chunkSize, overlap)
        }
    }

    return finalChunks
}

private fun hardSplitWithOverlap(
    s: String,
    chunkSize: Int,
    overlap: Int
): List<String> {
    if (s.isEmpty()) return emptyList()
    if (s.length <= chunkSize) return listOf(s)

    val out = ArrayList<String>()
    var start = 0
    while (start < s.length) {
        val end = (start + chunkSize).coerceAtMost(s.length)
        val piece = s.substring(start, end).trim()
        if (piece.isNotBlank()) out.add(piece)
        if (end >= s.length) break
        start = (end - overlap).coerceAtLeast(0)
    }
    return out
}
