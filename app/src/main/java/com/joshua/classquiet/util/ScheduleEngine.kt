package com.joshua.classquiet.util

import com.joshua.classquiet.model.ClassSchedule
import com.joshua.classquiet.model.DndMode
import com.joshua.classquiet.model.PeopleAudience
import com.joshua.classquiet.model.LocationSnapshot
import com.joshua.classquiet.model.asTime
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class ScheduleWindow(
    val schedule: ClassSchedule,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
)

object ScheduleEngine {
    private const val EARTH_RADIUS_METERS = 6_371_000.0
    const val MAX_LOCATION_AGE_MILLIS = 15 * 60 * 1000L

    fun activeWindows(
        schedules: List<ClassSchedule>,
        now: ZonedDateTime = ZonedDateTime.now(),
    ): List<ScheduleWindow> = schedules
        .asSequence()
        .filter { it.enabled }
        .flatMap { schedule ->
            sequenceOf(now.toLocalDate().minusDays(1), now.toLocalDate())
                .mapNotNull { windowStartingOn(schedule, it, now.zone) }
        }
        .filter { !now.isBefore(it.start) && now.isBefore(it.end) }
        .distinctBy { it.schedule.id }
        .toList()

    fun nextBoundary(
        schedules: List<ClassSchedule>,
        after: ZonedDateTime = ZonedDateTime.now(),
    ): ZonedDateTime? {
        val candidates = mutableListOf<ZonedDateTime>()
        val firstDate = after.toLocalDate().minusDays(1)
        for (offset in 0..9) {
            val date = firstDate.plusDays(offset.toLong())
            schedules.asSequence()
                .filter { it.enabled }
                .mapNotNull { windowStartingOn(it, date, after.zone) }
                .forEach { window ->
                    if (window.start.isAfter(after)) candidates += window.start
                    if (window.end.isAfter(after)) candidates += window.end
                }
        }
        return candidates.minOrNull()
    }

    fun strongestSchedule(schedules: List<ClassSchedule>): ClassSchedule? =
        schedules.maxByOrNull(::restrictionScore)

    fun strongestMode(schedules: List<ClassSchedule>): DndMode? =
        strongestSchedule(schedules)?.dndMode

    fun isInside(
        schedule: ClassSchedule,
        location: LocationSnapshot,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        if (nowMillis - location.capturedAtMillis > MAX_LOCATION_AGE_MILLIS) return false
        val distance = distanceMeters(
            location.latitude,
            location.longitude,
            schedule.latitude,
            schedule.longitude,
        )
        val accuracyAllowance = min(location.accuracyMeters.coerceAtLeast(0f), 30f)
        return distance <= schedule.radiusMeters + accuracyAllowance
    }

    fun distanceMeters(
        firstLatitude: Double,
        firstLongitude: Double,
        secondLatitude: Double,
        secondLongitude: Double,
    ): Double {
        val latitudeDelta = Math.toRadians(secondLatitude - firstLatitude)
        val longitudeDelta = Math.toRadians(secondLongitude - firstLongitude)
        val firstLatitudeRadians = Math.toRadians(firstLatitude)
        val secondLatitudeRadians = Math.toRadians(secondLatitude)
        val haversine = sin(latitudeDelta / 2).let { it * it } +
            cos(firstLatitudeRadians) * cos(secondLatitudeRadians) *
            sin(longitudeDelta / 2).let { it * it }
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
    }

    private fun restrictionScore(schedule: ClassSchedule): Int {
        if (schedule.dndMode != DndMode.CUSTOM) {
            return when (schedule.dndMode) {
                DndMode.VISUAL_ONLY -> 200
                DndMode.ALARMS_ONLY -> 750
                DndMode.TOTAL_SILENCE -> 1_000
                DndMode.CUSTOM -> error("Handled above")
            }
        }
        val custom = schedule.customDndSettings
        var score = 0
        if (!custom.allowAlarms) score += 180
        if (!custom.allowMedia) score += 150
        if (!custom.allowSystemSounds) score += 90
        if (!custom.allowReminders) score += 45
        if (!custom.allowEvents) score += 45
        if (!custom.allowRepeatCallers) score += 35
        if (!custom.allowPriorityChannels) score += 35
        score += when (custom.calls) {
            PeopleAudience.ANYONE -> 0
            PeopleAudience.CONTACTS -> 30
            PeopleAudience.STARRED -> 60
            PeopleAudience.NONE -> 90
        }
        score += when (custom.messages) {
            PeopleAudience.ANYONE -> 0
            PeopleAudience.CONTACTS -> 25
            PeopleAudience.STARRED -> 50
            PeopleAudience.NONE -> 75
        }
        if (!custom.showNotificationList) score += 35
        if (!custom.showStatusBarIcons) score += 20
        if (!custom.showPeeking) score += 15
        if (!custom.showBadges) score += 10
        return score.coerceAtMost(950)
    }

    private fun windowStartingOn(
        schedule: ClassSchedule,
        date: LocalDate,
        zone: ZoneId,
    ): ScheduleWindow? {
        if (date.dayOfWeek !in schedule.days) return null
        val start = date.atTime(schedule.startMinutes.asTime()).atZone(zone)
        val endDate = if (schedule.endMinutes > schedule.startMinutes) date else date.plusDays(1)
        val end = endDate.atTime(schedule.endMinutes.asTime()).atZone(zone)
        return ScheduleWindow(schedule, start, end)
    }
}
