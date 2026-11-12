package com.michal.fiszki

import com.michal.fiszki.model.CycleConfig
import com.michal.fiszki.model.ThemeStatus
import com.michal.fiszki.model.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BasketBatchTest {
    @Test
    fun completingAnyBasketKeepsEveryRemainingBasketsWordsTogether() {
        // Includes both small final baskets and final baskets merged into their predecessor.
        for (wordCount in 1..220) {
            val words = (1..wordCount).map {
                Word(id = it, themeId = 1, original = "word $it", translation = "$it")
            }
            val baskets = getBatches("Basket List", words).indices.map {
                getWordsForBatch(it + 1, words)
            }

            baskets.indices.forEach { completedIndex ->
                val completedIds = baskets[completedIndex].map { it.id }.toSet()
                val remainingWords = words.filterNot { it.id in completedIds }
                val remainingBaskets = getBatches("Basket List", remainingWords).indices.map {
                    getWordsForBatch(it + 1, remainingWords)
                }

                assertEquals(
                    "wordCount=$wordCount, completedIndex=$completedIndex",
                    baskets.filterIndexed { index, _ -> index != completedIndex },
                    remainingBaskets
                )
            }
        }
    }

    @Test
    fun firstBasketDisappearsAndSecondBecomesFirst() {
        val words = (1..120).map {
            Word(id = it, themeId = 1, original = "$it", translation = "$it")
        }
        val secondBasket = getWordsForBatch(2, words)
        val remainingWords = words.drop(50)

        assertEquals(secondBasket, getWordsForBatch(1, remainingWords))
        assertEquals(listOf("Basket List (Part 1)", "Basket List (Part 2)"),
            getBatches("Basket List", remainingWords).map { it.name })
    }

    @Test
    fun finalStageAlsoHandlesBasketsPreviouslyAdvancedTooFar() {
        assertFalse(CycleConfig.isFinalBasketStage(ThemeStatus.NOT_STARTED))
        assertFalse(CycleConfig.isFinalBasketStage(ThemeStatus.LEARNING_STAGE_1))
        assertFalse(CycleConfig.isFinalBasketStage(ThemeStatus.LEARNING_STAGE_2))
        assertTrue(CycleConfig.isFinalBasketStage(ThemeStatus.LEARNING_STAGE_3))
        assertTrue(CycleConfig.isFinalBasketStage(ThemeStatus.LEARNING_STAGE_4))
        assertTrue(CycleConfig.isFinalBasketStage(ThemeStatus.MAINTENANCE_STAGE_1))
    }

    @Test
    fun invalidBasketIndicesContainNoWords() {
        assertTrue(getWordsForBatch(0, listOf(1)).isEmpty())
        assertTrue(getWordsForBatch(2, listOf(1)).isEmpty())
        assertTrue(getWordsForBatch(1, emptyList<Int>()).isEmpty())
    }
}
