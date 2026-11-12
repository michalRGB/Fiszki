package com.michal.fiszki

import com.michal.fiszki.model.Theme
import com.michal.fiszki.model.Word

fun getBatches(themeName: String, words: List<Word>): List<Theme> {
    val chunks = getChunkedWords(words)
    return chunks.mapIndexed { index, batch ->
        Theme(
            id = 0,
            name = if (chunks.size == 1) themeName else "$themeName (Part ${index + 1})",
            wordCount = batch.size,
            basketCount = batch.count { it.isInBasket }
        )
    }
}

fun <T> getWordsForBatch(batchIndex: Int, words: List<T>): List<T> {
    val chunks = getChunkedWords(words)
    return chunks.getOrNull(batchIndex - 1) ?: emptyList()
}

private fun <T> getChunkedWords(words: List<T>): List<List<T>> {
    if (words.isEmpty()) return emptyList()
    val chunks = words.chunked(50).toMutableList()
    if (chunks.size > 1 && chunks.last().size < 20) {
        val last = chunks.removeAt(chunks.size - 1)
        val prev = chunks.removeAt(chunks.size - 1)
        chunks.add(prev + last)
    }
    return chunks
}
