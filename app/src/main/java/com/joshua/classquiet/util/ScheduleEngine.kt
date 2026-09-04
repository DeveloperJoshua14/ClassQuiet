package com.joshua.classquiet.util

import com.joshua.classquiet.model.ClassSchedule
import com.joshua.classquiet.model.DndMode
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

    fun strongestMode(schedules: List<ClassSchedule>): DndMode? =
        schedules.maxByOrNull { it.dndMode.severity }?.dndMode

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
