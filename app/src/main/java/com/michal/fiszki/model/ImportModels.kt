package com.michal.fiszki.model

import kotlinx.serialization.Serializable

@Serializable
data class ImportLanguage(
    val name: String? = null,
    val displayName: String? = null,
    val flag: String? = null,
    val language: ImportLanguageInfo? = null,
    val themes: List<ImportTheme>
)

@Serializable
data class ImportLanguageInfo(
    val name: String,
    val displayName: String,
    val flag: String
)

@Serializable
data class ImportTheme(
    val name: String,
    val words: List<ImportWord>
)

@Serializable
data class ImportWord(
    val original: String,
    val translation: String,
    val subtheme: String? = null
)
