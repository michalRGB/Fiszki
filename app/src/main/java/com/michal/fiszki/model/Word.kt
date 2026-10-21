package com.michal.fiszki.model

enum class LearningStatus {
    UNCHECKED,
    LEARNED,
    NOT_LEARNED
}

data class Word(
    val id: Int = 0,
    val themeId: Int,
    val original: String,
    val translation: String,
    val status: LearningStatus = LearningStatus.UNCHECKED,
    val isInBasket: Boolean = false,
    val basketStatus: LearningStatus = LearningStatus.UNCHECKED,
    val basketAddedDate: Long? = null,
    val subtheme: String? = null
)
