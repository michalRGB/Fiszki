package com.michal.fiszki.model

import kotlinx.serialization.Serializable

@Serializable
data class BackupArchive(
    val formatVersion: Int,
    val languages: List<BackupLanguage>,
    val themes: List<BackupTheme>,
    val words: List<BackupWord>,
    val progress: List<BackupProgress>
)

@Serializable
data class BackupLanguage(
    val name: String,
    val displayName: String,
    val flag: String
)

@Serializable
data class BackupTheme(
    val id: Int,
    val name: String,
    val language: String,
    val position: Int,
    val parentId: Int?
)

@Serializable
data class BackupWord(
    val id: Int,
    val themeId: Int,
    val original: String,
    val translation: String,
    val status: String,
    val isInBasket: Boolean,
    val basketStatus: String,
    val basketAddedDate: Long?,
    val subtheme: String?
)

@Serializable
data class BackupProgress(
    val themeId: Int,
    val partIndex: Int,
    val status: String,
    val lastCompletedDate: String?,
    val nextAvailableDate: String?,
    val isInProgress: Boolean
)