package com.michal.fiszki.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Period

object CycleConfig {
    private val dayStart = LocalTime.of(5, 30)

    fun learningDate(now: LocalDateTime = LocalDateTime.now()): LocalDate =
        if (now.toLocalTime().isBefore(dayStart)) now.toLocalDate().minusDays(1) else now.toLocalDate()

    fun nextReviewDate(interval: Period, now: LocalDateTime = LocalDateTime.now()): LocalDate =
        learningDate(now).plus(interval)

    fun isFinalBasketStage(status: ThemeStatus): Boolean =
        status.ordinal >= ThemeStatus.LEARNING_STAGE_3.ordinal

    /**
     * Given the status (which represents the LAST COMPLETED stage),
     * returns the next stage to be performed and the wait period AFTER that next stage is done.
     */
    fun getNextStage(currentStatus: ThemeStatus): Pair<ThemeStatus, Period> {
        return when (currentStatus) {
            ThemeStatus.NOT_STARTED -> Pair(ThemeStatus.LEARNING_STAGE_1, Period.ofDays(1))
            ThemeStatus.IN_PROGRESS -> Pair(ThemeStatus.LEARNING_STAGE_1, Period.ofDays(1))
            ThemeStatus.LEARNING_STAGE_1 -> Pair(ThemeStatus.LEARNING_STAGE_2, Period.ofDays(3))
            ThemeStatus.LEARNING_STAGE_2 -> Pair(ThemeStatus.LEARNING_STAGE_3, Period.ofDays(7))
            ThemeStatus.LEARNING_STAGE_3 -> Pair(ThemeStatus.LEARNING_STAGE_4, Period.ofDays(21))
            ThemeStatus.LEARNING_STAGE_4 -> Pair(ThemeStatus.MAINTENANCE_STAGE_1, Period.ofMonths(1))
            ThemeStatus.MAINTENANCE_STAGE_1 -> Pair(ThemeStatus.MAINTENANCE_STAGE_2, Period.ofMonths(2))
            ThemeStatus.MAINTENANCE_STAGE_2 -> Pair(ThemeStatus.MAINTENANCE_STAGE_3, Period.ofMonths(3))
            ThemeStatus.MAINTENANCE_STAGE_3 -> Pair(ThemeStatus.COMPLETED, Period.ofMonths(3))
            ThemeStatus.COMPLETED -> Pair(ThemeStatus.COMPLETED, Period.ofMonths(3))
        }
    }
}

fun LocalDate.isAvailableNow(now: LocalDateTime = LocalDateTime.now()): Boolean =
    !isAfter(CycleConfig.learningDate(now))
