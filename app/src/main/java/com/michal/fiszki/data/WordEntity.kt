package com.michal.fiszki.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.michal.fiszki.model.LearningStatus

@Entity(
    tableName = "words",
    foreignKeys = [
        ForeignKey(
            entity = ThemeEntity::class,
            parentColumns = ["id"],
            childColumns = ["themeId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class WordEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val themeId: Int,
    val original: String,
    val translation: String,
    val status: LearningStatus = LearningStatus.UNCHECKED,
    val isInBasket: Boolean = false,
    val basketStatus: LearningStatus = LearningStatus.UNCHECKED,
    val basketAddedDate: Long? = null,
    val subtheme: String? = null
)
