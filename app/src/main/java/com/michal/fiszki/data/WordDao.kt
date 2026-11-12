package com.michal.fiszki.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.michal.fiszki.model.LearningStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface WordDao {
    @Query("SELECT * FROM words WHERE themeId = :themeId")
    fun getWordsForTheme(themeId: Int): Flow<List<WordEntity>>

    @Query("SELECT * FROM words WHERE themeId IN (SELECT id FROM themes WHERE language = :language)")
    fun getAllWordsForLanguage(language: String): Flow<List<WordEntity>>

    @Query("SELECT * FROM words WHERE themeId = :themeId")
    suspend fun getWordsForThemeSuspend(themeId: Int): List<WordEntity>

    @Query("SELECT * FROM words")
    suspend fun getAllWordsSuspend(): List<WordEntity>

    @Query("SELECT * FROM words WHERE themeId = :themeId AND (subtheme IS NULL OR subtheme = :themeName)")
    fun getDirectWordsForTheme(themeId: Int, themeName: String): Flow<List<WordEntity>>

    @Query("SELECT * FROM words WHERE isInBasket = 1 ORDER BY basketAddedDate ASC, id ASC")
    fun getAllBasketWords(): Flow<List<WordEntity>>

    @Query("SELECT id FROM words WHERE isInBasket = 1 ORDER BY basketAddedDate ASC, id ASC")
    suspend fun getAllBasketWordIds(): List<Int>

    @Query("UPDATE words SET isInBasket = 0, basketStatus = 'UNCHECKED', basketAddedDate = NULL WHERE id IN (:wordIds)")
    suspend fun removeWordsFromBasket(wordIds: List<Int>)

    @Query("UPDATE words SET status = :status WHERE id = :wordId")
    suspend fun updateWordStatus(wordId: Int, status: LearningStatus)

    @Query("UPDATE words SET basketStatus = :status WHERE id = :wordId AND isInBasket = 1")
    suspend fun updateBasketWordStatus(wordId: Int, status: LearningStatus)

    @Update
    suspend fun updateWord(word: WordEntity)

    @Update
    suspend fun updateWords(words: List<WordEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWords(words: List<WordEntity>)

    @Query("DELETE FROM words WHERE themeId = :themeId")
    suspend fun deleteWordsForTheme(themeId: Int)

    @Query("DELETE FROM words WHERE themeId IN (SELECT id FROM themes WHERE language = :language)")
    suspend fun deleteWordsForLanguage(language: String)
}
