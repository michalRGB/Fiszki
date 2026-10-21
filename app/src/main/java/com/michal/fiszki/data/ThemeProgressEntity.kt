package com.michal.fiszki.data

import androidx.room.Entity
import com.michal.fiszki.model.ThemeStatus
import java.time.LocalDate

@Entity(tableName = "theme_progress", primaryKeys = ["themeId", "partIndex"])
data class ThemeProgressEntity(
    val themeId: Int,
    val partIndex: Int,
    val status: ThemeStatus,
    val lastCompletedDate: LocalDate?,
    val nextAvailableDate: LocalDate?,
    val isInProgress: Boolean
)
