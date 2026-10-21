package com.michal.fiszki.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "themes")
data class ThemeEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val language: String,
    val position: Int,
    val parentId: Int? = null
)
