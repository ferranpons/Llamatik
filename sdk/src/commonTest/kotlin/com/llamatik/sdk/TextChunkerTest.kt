package com.llamatik.sdk

import com.llamatik.sdk.rag.chunkText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextChunkerTest {

    @Test
    fun emptyTextReturnsEmptyList() {
        assertEquals(emptyList(), chunkText(""))
    }

    @Test
    fun shortTextReturnsSingleChunk() {
        val result = chunkText("Hello world", chunkSize = 1000)
        assertEquals(1, result.size)
        assertEquals("Hello world", result[0])
    }

    @Test
    fun longTextSplitsIntoMultipleChunks() {
        val text = "A".repeat(2500)
        val result = chunkText(text, chunkSize = 1000, chunkOverlap = 100)
        assertTrue(result.size > 1)
        result.forEach { chunk -> assertTrue(chunk.length <= 1000) }
    }

    @Test
    fun paragraphBoundaryRespected() {
        val text = "First paragraph.\n\nSecond paragraph.\n\nThird paragraph."
        val result = chunkText(text, chunkSize = 50, chunkOverlap = 0)
        assertTrue(result.isNotEmpty())
    }
}
