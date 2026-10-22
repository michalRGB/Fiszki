package com.michal.fiszki.data.repository

import com.michal.fiszki.data.ThemeDao
import com.michal.fiszki.data.ThemeEntity
import com.michal.fiszki.data.ThemeProgressEntity
import com.michal.fiszki.model.Theme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ThemeRepository(private val dao: ThemeDao) {

    fun getThemesForLanguage(language: String): Flow<List<Theme>> {
        return combine(
            dao.getThemesForLanguage(language),
            dao.getAllWordCounts()
        ) { themes, counts ->
            val countMap = counts.associate { it.themeId to (it.count to it.basketCount) }
            themes.map { entity ->
                val (total, bCount) = countMap[entity.id] ?: (0 to 0)
                Theme(
                    id = entity.id,
                    name = entity.name,
                    position = entity.position,
                    parentId = entity.parentId,
                    wordCount = total,
                    basketCount = bCount
                )
            }
        }
    }

    suspend fun getThemesForLanguageSuspend(language: String): List<ThemeEntity> {
        return dao.getThemesForLanguageSuspend(language)
    }

    suspend fun getAllThemesSuspend(): List<ThemeEntity> {
        return dao.getAllThemesSuspend()
    }

    fun getProgressForTheme(themeId: Int): Flow<List<ThemeProgressEntity>> {
        return dao.getProgressForTheme(themeId)
    }

    fun getAllProgressForLanguage(language: String): Flow<List<ThemeProgressEntity>> {
        return dao.getAllProgressForLanguage(language)
    }

    suspend fun getProgressForThemeSuspend(themeId: Int): List<ThemeProgressEntity> {
        return dao.getProgressForThemeSuspend(themeId)
    }

    suspend fun getAllProgressSuspend(): List<ThemeProgressEntity> {
        return dao.getAllProgressSuspend()
    }

    suspend fun updateProgress(themeId: Int, partIndex: Int, theme: Theme) {
        dao.updateProgress(
            ThemeProgressEntity(
                themeId = themeId,
                partIndex = partIndex,
                status = theme.status,
                lastCompletedDate = theme.lastCompletedDate,
                nextAvailableDate = theme.nextAvailableDate,
                isInProgress = theme.isInProgress
            )
        )
    }

    suspend fun insertProgressList(progressList: List<ThemeProgressEntity>) {
        dao.insertProgressList(progressList)
    }

    suspend fun insertThemes(themes: List<Theme>, language: String) {
        dao.insertThemes(themes.map {
            ThemeEntity(
                id = it.id,
                name = it.name,
                language = language,
                position = it.position,
                parentId = it.parentId
            )
        })
    }

    suspend fun deleteThemesForLanguage(language: String) {
        dao.deleteThemesForLanguage(language)
    }

    suspend fun deleteProgressForTheme(themeId: Int) {
        dao.deleteProgressForTheme(themeId)
    }

    suspend fun deleteAllBasketProgress() {
        dao.deleteAllBasketProgress()
    }

    suspend fun deleteBasketProgress(partIndex: Int) {
        dao.deleteBasketProgress(partIndex)
    }

    suspend fun shiftBasketProgress(deletedIndex: Int) {
        dao.shiftBasketProgress(deletedIndex)
    }

    suspend fun deleteProgressForLanguage(language: String) {
        dao.deleteProgressForLanguage(language)
    }

    suspend fun clearAllData() {
        dao.deleteAllProgress()
        dao.deleteAllWords()
        dao.deleteAllThemes()
    }

    companion object {
        private var instance: ThemeRepository? = null
        fun getInstance(dao: ThemeDao) = instance ?: ThemeRepository(dao).also { instance = it }
    }
}
