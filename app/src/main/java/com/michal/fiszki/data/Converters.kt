package com.michal.fiszki.data

import androidx.room.TypeConverter
import com.michal.fiszki.model.LearningStatus
import com.michal.fiszki.model.ThemeStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class Converters {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE

    @TypeConverter
    fun fromTimestamp(value: String?): LocalDate? {
        return value?.let { LocalDate.parse(it, formatter) }
    }

    @TypeConverter
    fun dateToTimestamp(date: LocalDate?): String? {
        return date?.format(formatter)
    }

    @TypeConverter
    fun fromThemeStatus(value: String): ThemeStatus {
        return ThemeStatus.valueOf(value)
    }

    @TypeConverter
    fun themeStatusToString(status: ThemeStatus): String {
        return status.name
    }

    @TypeConverter
    fun fromLearningStatus(value: String): LearningStatus {
        return LearningStatus.valueOf(value)
    }

    @TypeConverter
    fun learningStatusToString(status: LearningStatus): String {
        return status.name
    }
}
