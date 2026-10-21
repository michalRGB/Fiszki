package com.michal.fiszki.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ThemeDao {
    @Query("SELECT * FROM themes WHERE language = :language ORDER BY position ASC")
    fun getThemesForLanguage(language: String): Flow<List<ThemeEntity>>

    @Query("SELECT * FROM themes WHERE language = :language ORDER BY position ASC")
    suspend fun getThemesForLanguageSuspend(language: String): List<ThemeEntity>

    @Query("SELECT * FROM themes ORDER BY position ASC")
    suspend fun getAllThemesSuspend(): List<ThemeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThemes(themes: List<ThemeEntity>)

    @Update
    suspend fun updateTheme(theme: ThemeEntity)

    @Query("SELECT * FROM theme_progress WHERE themeId = :themeId")
    fun getProgressForTheme(themeId: Int): Flow<List<ThemeProgressEntity>>

    @Query("SELECT * FROM theme_progress WHERE themeId IN (SELECT id FROM themes WHERE language = :language) OR themeId = -1")
    fun getAllProgressForLanguage(language: String): Flow<List<ThemeProgressEntity>>

    @Query("SELECT * FROM theme_progress WHERE themeId = :themeId")
    suspend fun getProgressForThemeSuspend(themeId: Int): List<ThemeProgressEntity>

    @Query("SELECT * FROM theme_progress")
    suspend fun getAllProgressSuspend(): List<ThemeProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateProgress(progress: ThemeProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgressList(progressList: List<ThemeProgressEntity>)

    @Query("SELECT COUNT(*) FROM words WHERE themeId = :themeId")
    fun getWordCountForTheme(themeId: Int): Flow<Int>

    @Query("SELECT themeId, COUNT(*) as count, SUM(CASE WHEN isInBasket = 1 THEN 1 ELSE 0 END) as basketCount FROM words GROUP BY themeId")
    fun getAllWordCounts(): Flow<List<ThemeWordCount>>

    @Query("DELETE FROM theme_progress WHERE themeId = -1 AND partIndex = :partIndex")
    suspend fun deleteBasketProgress(partIndex: Int)

    @Transaction
    suspend fun shiftBasketProgress(deletedIndex: Int) {
        val toShift = getProgressForThemeSuspend(-1)
            .filter { it.partIndex > deletedIndex }
            .sortedBy { it.partIndex }

        for (progress in toShift) {
            deleteBasketProgress(progress.partIndex)
        }

        val shifted = toShift.map { it.copy(partIndex = it.partIndex - 1) }
        insertProgressList(shifted)
    }

    @Query("DELETE FROM theme_progress WHERE themeId = :themeId")
    suspend fun deleteProgressForTheme(themeId: Int)

    @Query("DELETE FROM themes WHERE language = :language")
    suspend fun deleteThemesForLanguage(language: String)

    @Query("DELETE FROM themes")
    suspend fun deleteAllThemes()

    @Query("DELETE FROM theme_progress")
    suspend fun deleteAllProgress()

    @Query("DELETE FROM words")
    suspend fun deleteAllWords()
}

data class ThemeWordCount(
    val themeId: Int,
    val count: Int,
    val basketCount: Int
)
