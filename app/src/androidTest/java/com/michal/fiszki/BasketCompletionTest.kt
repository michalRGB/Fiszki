package com.michal.fiszki

import androidx.room.Room
import androidx.room.withTransaction
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.michal.fiszki.data.AppDatabase
import com.michal.fiszki.data.ThemeEntity
import com.michal.fiszki.data.ThemeProgressEntity
import com.michal.fiszki.data.WordEntity
import com.michal.fiszki.data.repository.ThemeRepository
import com.michal.fiszki.data.repository.WordRepository
import com.michal.fiszki.data.repository.LanguageRepository
import com.michal.fiszki.model.Language
import com.michal.fiszki.model.LearningStatus
import com.michal.fiszki.model.ThemeStatus
import com.michal.fiszki.ui.MainViewModel
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BasketCompletionTest {
    private lateinit var database: AppDatabase
    private lateinit var wordRepository: WordRepository
    private lateinit var themeRepository: ThemeRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
        ).build()
        wordRepository = WordRepository(database.wordDao())
        themeRepository = ThemeRepository(database.themeDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun completingFirstBasketPreservesAllOtherWordsAndProgress() = runBlocking {
        val words = seedWords(120)
        val first = progress(0, ThemeStatus.LEARNING_STAGE_3)
        val second = progress(1, ThemeStatus.LEARNING_STAGE_1).copy(isInProgress = true)
        val third = progress(2, ThemeStatus.LEARNING_STAGE_2)
        val regular = progress(0, ThemeStatus.MAINTENANCE_STAGE_1).copy(themeId = 1)
        database.themeDao().insertProgressList(listOf(first, second, third, regular))

        // The final retry only contains one missed word. Completion must still clear all 50.
        database.wordDao().updateBasketWordStatus(50, LearningStatus.LEARNED)
        completeBasket(0)

        assertEquals(words.drop(50), database.wordDao().getAllWordsSuspend().filter { it.isInBasket })
        assertEquals(
            listOf(second.copy(partIndex = 0), third.copy(partIndex = 1)),
            database.themeDao().getProgressForThemeSuspend(-1).sortedBy { it.partIndex }
        )
        assertEquals(listOf(regular), database.themeDao().getProgressForThemeSuspend(1))
        val completedWords = database.wordDao().getAllWordsSuspend().filterNot { it.isInBasket }
        assertEquals(words.take(50).map {
            it.copy(isInBasket = false, basketStatus = LearningStatus.UNCHECKED, basketAddedDate = null)
        }, completedWords)
    }

    @Test
    fun completingMiddleBasketPreservesEarlierBasketAndShiftsLaterProgress() = runBlocking {
        val words = seedWords(170)
        val progress = (0..3).map { progress(it, ThemeStatus.LEARNING_STAGE_2) }
        database.themeDao().insertProgressList(progress)

        completeBasket(1)

        assertEquals(words.take(50) + words.drop(100),
            database.wordDao().getAllWordsSuspend().filter { it.isInBasket })
        assertEquals(listOf(progress[0], progress[2].copy(partIndex = 1), progress[3].copy(partIndex = 2)),
            database.themeDao().getProgressForThemeSuspend(-1).sortedBy { it.partIndex })
    }

    @Test
    fun completingMergedFinalBasketAndThenLastBasketRemovesAllBasketProgress() = runBlocking {
        seedWords(119)
        database.themeDao().insertProgressList((0..1).map { progress(it, ThemeStatus.LEARNING_STAGE_3) })

        completeBasket(1)
        assertEquals((1..50).toList(), database.wordDao().getAllBasketWordIds())
        assertEquals(listOf(0), database.themeDao().getProgressForThemeSuspend(-1).map { it.partIndex })

        completeBasket(0)
        assertTrue(database.wordDao().getAllBasketWordIds().isEmpty())
        assertTrue(database.themeDao().getAllProgressForLanguage("test").first().isEmpty())
        assertFalse(wordRepository.clearBasketPart(0))
    }

    @Test
    fun delayedAnswerCannotPutCompletedWordBackInBasketOrOverwriteRegularProgress() = runBlocking {
        seedWords(1)
        val staleWord = wordRepository.getAllBasketWords().first().single()
        completeBasket(0)
        database.wordDao().updateWordStatus(staleWord.id, LearningStatus.NOT_LEARNED)

        wordRepository.updateWordStatus(staleWord.copy(basketStatus = LearningStatus.LEARNED), isBasket = true)

        val word = database.wordDao().getAllWordsSuspend().single()
        assertFalse(word.isInBasket)
        assertEquals(LearningStatus.UNCHECKED, word.basketStatus)
        assertEquals(LearningStatus.NOT_LEARNED, word.status)
    }

    @Test
    fun completingLastBasketClearsViewModelProgressBeforeNextBasketIsAdded() = runBlocking {
        val word = seedWords(1).single()
        database.themeDao().insertProgressList(listOf(progress(0, ThemeStatus.LEARNING_STAGE_3)))
        val store = ViewModelStore()
        val viewModel = withContext(Dispatchers.Main) {
            MainViewModel(database, themeRepository, wordRepository,
                LanguageRepository(database.languageDao())).also {
                store.put("main", it)
                it.selectLanguage(Language("test", "Test", ""))
            }
        }
        try {
            withTimeout(5_000) {
                viewModel.progress.first { it.containsKey(-1) }
                viewModel.basketWords.first { it.isNotEmpty() }
                withContext(Dispatchers.Main) { viewModel.completeBasketPart(0) }
                viewModel.basketWords.first { it.isEmpty() }
                viewModel.progress.first { !it.containsKey(-1) }
            }

            database.wordDao().insertWords(listOf(word.copy(id = 2)))
            withTimeout(5_000) { viewModel.basketWords.first { it.singleOrNull()?.id == 2 } }
            assertFalse(viewModel.progress.value.containsKey(-1))
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
        }
    }

    private suspend fun completeBasket(partIndex: Int) {
        database.withTransaction {
            assertTrue(wordRepository.clearBasketPart(partIndex))
            themeRepository.deleteBasketProgress(partIndex)
            themeRepository.shiftBasketProgress(partIndex)
        }
    }

    private suspend fun seedWords(count: Int): List<WordEntity> {
        database.themeDao().insertThemes(listOf(ThemeEntity(1, "Theme", "test", 0)))
        val words = (1..count).map {
            WordEntity(
                id = it, themeId = 1, original = "word $it", translation = "translation $it",
                status = LearningStatus.LEARNED, isInBasket = true,
                basketStatus = LearningStatus.NOT_LEARNED,
                basketAddedDate = 1000L
            )
        }
        database.wordDao().insertWords(words)
        return words
    }

    private fun progress(index: Int, status: ThemeStatus) = ThemeProgressEntity(
        themeId = -1, partIndex = index, status = status,
        lastCompletedDate = LocalDate.of(2026, 11, 1).plusDays(index.toLong()),
        nextAvailableDate = LocalDate.of(2026, 11, 15).plusDays(index.toLong()),
        isInProgress = false
    )
}
