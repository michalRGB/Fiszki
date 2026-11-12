package com.michal.fiszki

import com.michal.fiszki.model.Theme
import com.michal.fiszki.model.Word

private const val BATCH_SIZE = 50

fun getBatches(themeName: String, words: List<Word>): List<Theme> =
    createBatchThemes(themeName, getChunkedWords(words))

fun getBasketBatches(words: List<Word>): List<Theme> =
    createBatchThemes("Basket List", words.chunked(BATCH_SIZE))

private fun createBatchThemes(themeName: String, chunks: List<List<Word>>): List<Theme> {
    return chunks.mapIndexed { index, batch ->
        Theme(
            id = 0,
            name = if (chunks.size == 1) themeName else "$themeName (Part ${index + 1})",
            wordCount = batch.size,
            basketCount = batch.count { it.isInBasket }
        )
    }
}

fun <T> getWordsForBasket(batchIndex: Int, words: List<T>): List<T> =
    words.chunked(BATCH_SIZE).getOrNull(batchIndex - 1) ?: emptyList()

fun <T> getWordsForBatch(batchIndex: Int, words: List<T>): List<T> {
    val chunks = getChunkedWords(words)
    return chunks.getOrNull(batchIndex - 1) ?: emptyList()
}

private fun <T> getChunkedWords(words: List<T>): List<List<T>> {
    if (words.isEmpty()) return emptyList()
    val chunks = words.chunked(BATCH_SIZE).toMutableList()
    if (chunks.size > 1 && chunks.last().size < 20) {
        val last = chunks.removeAt(chunks.size - 1)
        val prev = chunks.removeAt(chunks.size - 1)
        chunks.add(prev + last)
    }
    return chunks
}
