package com.michal.fiszki.data.repository

import com.michal.fiszki.data.LanguageDao
import com.michal.fiszki.data.LanguageEntity
import com.michal.fiszki.model.Language
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LanguageRepository(private val dao: LanguageDao) {

    fun getAllLanguages(): Flow<List<Language>> {
        return dao.getAllLanguages().map { list ->
            list.map { Language(it.name, it.displayName, it.flag) }
        }
    }

    suspend fun insertLanguages(languages: List<Language>) {
        dao.insertLanguages(languages.map { LanguageEntity(it.name, it.displayName, it.flag) })
    }

    suspend fun deleteAllLanguages() {
        dao.deleteAllLanguages()
    }

    companion object {
        private var instance: LanguageRepository? = null
        fun getInstance(dao: LanguageDao) = instance ?: LanguageRepository(dao).also { instance = it }
    }
}
