package com.joshua.classquiet

import com.joshua.classquiet.model.ClassSchedule
import com.joshua.classquiet.model.CustomDndSettings
import com.joshua.classquiet.model.DndMode
import com.joshua.classquiet.model.LocationSnapshot
import com.joshua.classquiet.util.ScheduleEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleEngineTest {
    private val zone = ZoneId.of("America/New_York")

    @Test
    fun `class is active only during a selected day and time`() {
        val schedule = sampleSchedule(
            days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            startMinutes = 10 * 60,
            endMinutes = 11 * 60,
        )

        assertTrue(
            ScheduleEngine.activeWindows(
                listOf(schedule),
                ZonedDateTime.of(2026, 9, 2, 10, 30, 0, 0, zone),
            ).isNotEmpty(),
        )
        assertTrue(
            ScheduleEngine.activeWindows(
                listOf(schedule),
                ZonedDateTime.of(2026, 9, 2, 11, 0, 0, 0, zone),
            ).isEmpty(),
        )
        assertTrue(
            ScheduleEngine.activeWindows(
                listOf(schedule),
                ZonedDateTime.of(2026, 9, 3, 10, 30, 0, 0, zone),
            ).isEmpty(),
        )
    }

    @Test
    fun `overnight class remains active after midnight`() {
        val schedule = sampleSchedule(
            days = setOf(DayOfWeek.MONDAY),
            startMinutes = 23 * 60,
            endMinutes = 60,
        )
        val afterMidnight = ZonedDateTime.of(2026, 8, 25, 0, 30, 0, 0, zone)

        assertEquals(1, ScheduleEngine.activeWindows(listOf(schedule), afterMidnight).size)
    }

    @Test
    fun `next boundary returns the next start or end`() {
        val schedule = sampleSchedule(
            days = setOf(DayOfWeek.WEDNESDAY),
            startMinutes = 10 * 60,
            endMinutes = 11 * 60,
        )
        val beforeClass = ZonedDateTime.of(2026, 9, 2, 9, 30, 0, 0, zone)
        val duringClass = ZonedDateTime.of(2026, 9, 2, 10, 30, 0, 0, zone)

        assertEquals(10, ScheduleEngine.nextBoundary(listOf(schedule), beforeClass)?.hour)
        assertEquals(11, ScheduleEngine.nextBoundary(listOf(schedule), duringClass)?.hour)
    }

    @Test
    fun `location must be inside radius and recent`() {
        val schedule = sampleSchedule(
            latitude = 37.2296,
            longitude = -80.4139,
            radiusMeters = 150f,
        )
        val now = 1_800_000_000_000L
        val nearby = LocationSnapshot(37.2297, -80.4140, 5f, now - 30_000)
        val farAway = LocationSnapshot(37.2400, -80.4300, 5f, now - 30_000)
        val stale = nearby.copy(capturedAtMillis = now - ScheduleEngine.MAX_LOCATION_AGE_MILLIS - 1)

        assertTrue(ScheduleEngine.isInside(schedule, nearby, now))
        assertFalse(ScheduleEngine.isInside(schedule, farAway, now))
        assertFalse(ScheduleEngine.isInside(schedule, stale, now))
    }

    @Test
    fun `strictest overlapping mode wins`() {
        val light = sampleSchedule(dndMode = DndMode.VISUAL_ONLY)
        val strict = sampleSchedule(dndMode = DndMode.TOTAL_SILENCE)

        assertEquals(DndMode.TOTAL_SILENCE, ScheduleEngine.strongestMode(listOf(light, strict)))
    }

    @Test
    fun `restrictive custom mode can win an overlap`() {
        val alarmsOnly = sampleSchedule(dndMode = DndMode.ALARMS_ONLY)
        val custom = sampleSchedule(
            dndMode = DndMode.CUSTOM,
            customDndSettings = CustomDndSettings(
                allowAlarms = false,
                showNotificationList = false,
                showStatusBarIcons = false,
                showBadges = false,
            ),
        )

        assertEquals(DndMode.CUSTOM, ScheduleEngine.strongestMode(listOf(alarmsOnly, custom)))
    }

    private fun sampleSchedule(
        days: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY),
        startMinutes: Int = 9 * 60,
        endMinutes: Int = 10 * 60,
        latitude: Double = 37.2296,
        longitude: Double = -80.4139,
        radiusMeters: Float = 150f,
        dndMode: DndMode = DndMode.VISUAL_ONLY,
        customDndSettings: CustomDndSettings = CustomDndSettings(),
    ) = ClassSchedule(
        name = "Test class",
        locationLabel = "Test Hall",
        resolvedAddress = "Blacksburg, VA",
        latitude = latitude,
        longitude = longitude,
        radiusMeters = radiusMeters,
        days = days,
        startMinutes = startMinutes,
        endMinutes = endMinutes,
        dndMode = dndMode,
        customDndSettings = customDndSettings,
    )
}
