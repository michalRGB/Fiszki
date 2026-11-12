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
        // Includes emptying a one-word final basket and completing a full basket.
        for (wordCount in 1..220) {
            val words = (1..wordCount).map {
                Word(id = it, themeId = 1, original = "word $it", translation = "$it")
            }
            val baskets = getBasketBatches(words).indices.map {
                getWordsForBasket(it + 1, words)
            }

            baskets.indices.forEach { completedIndex ->
                val completedIds = baskets[completedIndex].map { it.id }.toSet()
                val remainingWords = words.filterNot { it.id in completedIds }
                val remainingBaskets = getBasketBatches(remainingWords).indices.map {
                    getWordsForBasket(it + 1, remainingWords)
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
        val secondBasket = getWordsForBasket(2, words)
        val remainingWords = words.drop(50)

        assertEquals(secondBasket, getWordsForBasket(1, remainingWords))
        assertEquals(listOf("Basket List (Part 1)", "Basket List (Part 2)"),
            getBasketBatches(remainingWords).map { it.name })
    }

    @Test
    fun basketsNeverMergeSmallTails() {
        val words = (1..119).map {
            Word(id = it, themeId = 1, original = "$it", translation = "$it")
        }
        assertEquals(listOf(50, 1), getBasketBatches(words.take(51)).map { it.wordCount })
        assertEquals(listOf(50, 19), getBasketBatches(words.take(69)).map { it.wordCount })
        assertEquals(listOf(50, 50, 19), getBasketBatches(words).map { it.wordCount })
        assertEquals(listOf(50, 69), getBatches("Theme", words).map { it.wordCount })
    }

    @Test
    fun deletingOneWordPullsTheNextBasketForwardAndRemovesEmptyBasket() {
        val words = (1..51).map {
            Word(id = it, themeId = 1, original = "$it", translation = "$it")
        }
        val remainingWords = words.filterNot { it.id == 1 }

        assertEquals(listOf(50), getBasketBatches(remainingWords).map { it.wordCount })
        assertEquals((2..51).toList(), getWordsForBasket(1, remainingWords).map { it.id })
        assertTrue(getWordsForBasket(2, remainingWords).isEmpty())
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
        assertTrue(getWordsForBasket(0, listOf(1)).isEmpty())
        assertTrue(getWordsForBasket(2, listOf(1)).isEmpty())
        assertTrue(getWordsForBasket(1, emptyList<Int>()).isEmpty())
    }
}
