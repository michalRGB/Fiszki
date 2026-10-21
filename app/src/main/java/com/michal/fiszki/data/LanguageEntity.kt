package com.michal.fiszki.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "languages")
data class LanguageEntity(
    @PrimaryKey val name: String,
    val displayName: String,
    val flag: String
)
