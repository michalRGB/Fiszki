package com.michal.fiszki.model

import java.time.LocalDate

enum class ThemeStatus {
    NOT_STARTED,
    IN_PROGRESS,
    LEARNING_STAGE_1,
    LEARNING_STAGE_2,
    LEARNING_STAGE_3,
    LEARNING_STAGE_4,
    LEARNING_STAGE_5,
    MAINTENANCE_STAGE_1,
    MAINTENANCE_STAGE_2,
    MAINTENANCE_STAGE_3,
    COMPLETED;

    val displayName: String get() = name.replace("_", " ").lowercase().capitalizeWords()
}

private fun String.capitalizeWords(): String = 
    split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

data class Theme(
    val id: Int,
    val name: String,
    val position: Int = 0,
    val parentId: Int? = null,
    val categoryId: Int = 0,
    val status: ThemeStatus = ThemeStatus.NOT_STARTED,
    val lastCompletedDate: LocalDate? = null,
    val nextAvailableDate: LocalDate? = null,
    val isInProgress: Boolean = false,
    val wordCount: Int = 0,
    val basketCount: Int = 0
)
