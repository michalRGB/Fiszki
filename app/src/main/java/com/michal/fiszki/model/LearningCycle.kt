package com.michal.fiszki.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Period

object CycleConfig {
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

fun LocalDate.isAvailableNow(): Boolean {
    val now = LocalDateTime.now()
    val unlockTime = this.atTime(LocalTime.of(5, 30))
    return !now.isBefore(unlockTime)
}
