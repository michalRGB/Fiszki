package com.michal.fiszki.data.repository

import com.michal.fiszki.data.WordDao
import com.michal.fiszki.data.WordEntity
import com.michal.fiszki.model.Word
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WordRepository(private val dao: WordDao) {

    fun getWordsForTheme(themeId: Int): Flow<List<Word>> {
        return dao.getWordsForTheme(themeId).map { list ->
            list.map { entity ->
                Word(
                    id = entity.id,
                    themeId = entity.themeId,
                    original = entity.original,
                    translation = entity.translation,
                    status = entity.status,
                    isInBasket = entity.isInBasket,
                    basketStatus = entity.basketStatus,
                    basketAddedDate = entity.basketAddedDate,
                    subtheme = entity.subtheme
                )
            }
        }
    }

    fun getAllWordsForLanguage(language: String): Flow<List<Word>> {
        return dao.getAllWordsForLanguage(language).map { list ->
            list.map { entity ->
                Word(
                    id = entity.id,
                    themeId = entity.themeId,
                    original = entity.original,
                    translation = entity.translation,
                    status = entity.status,
                    isInBasket = entity.isInBasket,
                    basketStatus = entity.basketStatus,
                    basketAddedDate = entity.basketAddedDate,
                    subtheme = entity.subtheme
                )
            }
        }
    }

    fun getAllBasketWords(): Flow<List<Word>> {
        return dao.getAllBasketWords().map { list ->
            list.map { entity ->
                Word(
                    id = entity.id,
                    themeId = entity.themeId,
                    original = entity.original,
                    translation = entity.translation,
                    status = entity.status,
                    isInBasket = entity.isInBasket,
                    basketStatus = entity.basketStatus,
                    basketAddedDate = entity.basketAddedDate,
                    subtheme = entity.subtheme
                )
            }
        }
    }

    suspend fun getWordsForThemeSuspend(themeId: Int): List<Word> {
        return dao.getWordsForThemeSuspend(themeId).map { entity ->
            Word(
                id = entity.id,
                themeId = entity.themeId,
                original = entity.original,
                translation = entity.translation,
                status = entity.status,
                isInBasket = entity.isInBasket,
                basketStatus = entity.basketStatus,
                basketAddedDate = entity.basketAddedDate,
                subtheme = entity.subtheme
            )
        }
    }

    suspend fun updateWord(word: Word) {
        dao.updateWord(
            WordEntity(
                id = word.id,
                themeId = word.themeId,
                original = word.original,
                translation = word.translation,
                status = word.status,
                isInBasket = word.isInBasket,
                basketStatus = word.basketStatus,
                basketAddedDate = word.basketAddedDate,
                subtheme = word.subtheme
            )
        )
    }

    suspend fun updateWords(words: List<Word>) {
        dao.updateWords(words.map { word ->
            WordEntity(
                id = word.id,
                themeId = word.themeId,
                original = word.original,
                translation = word.translation,
                status = word.status,
                isInBasket = word.isInBasket,
                basketStatus = word.basketStatus,
                basketAddedDate = word.basketAddedDate,
                subtheme = word.subtheme
            )
        })
    }

    suspend fun insertWords(words: List<Word>, themeId: Int) {
        dao.insertWords(words.map { 
            WordEntity(
                themeId = themeId,
                original = it.original,
                translation = it.translation,
                status = it.status,
                isInBasket = it.isInBasket,
                basketStatus = it.basketStatus,
                basketAddedDate = it.basketAddedDate,
                subtheme = it.subtheme
            )
        })
    }

    suspend fun deleteWordsForLanguage(language: String) {
        dao.deleteWordsForLanguage(language)
    }

    companion object {
        private var instance: WordRepository? = null
        fun getInstance(dao: WordDao) = instance ?: WordRepository(dao).also { instance = it }
    }
}
