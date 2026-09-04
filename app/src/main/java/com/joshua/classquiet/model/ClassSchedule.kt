package com.joshua.classquiet.model

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

enum class DndMode(
    val severity: Int,
    val displayName: String,
    val description: String,
) {
    VISUAL_ONLY(
        severity = 1,
        displayName = "Visual only",
        description = "Notifications stay visible, but notification sounds and vibration are blocked. Alarms and media may play.",
    ),
    ALARMS_ONLY(
        severity = 2,
        displayName = "Alarms only",
        description = "Calls and notifications are silenced. Only alarms are allowed to interrupt you.",
    ),
    TOTAL_SILENCE(
        severity = 3,
        displayName = "Total silence",
        description = "Blocks calls, notifications, alarms, vibration, and general media audio.",
    );

    companion object {
        fun fromStored(value: String?): DndMode =
            entries.firstOrNull { it.name == value } ?: VISUAL_ONLY
    }
}

data class ClassSchedule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val locationLabel: String,
    val resolvedAddress: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float = 150f,
    val days: Set<DayOfWeek>,
    val startMinutes: Int,
    val endMinutes: Int,
    val dndMode: DndMode,
    val enabled: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("locationLabel", locationLabel)
        put("resolvedAddress", resolvedAddress)
        put("latitude", latitude)
        put("longitude", longitude)
        put("radiusMeters", radiusMeters.toDouble())
        put("days", JSONArray(days.sortedBy { it.value }.map { it.value }))
        put("startMinutes", startMinutes)
        put("endMinutes", endMinutes)
        put("dndMode", dndMode.name)
        put("enabled", enabled)
    }

    companion object {
        fun fromJson(json: JSONObject): ClassSchedule {
            val storedDays = json.optJSONArray("days") ?: JSONArray()
            val days = buildSet {
                for (index in 0 until storedDays.length()) {
                    storedDays.optInt(index, -1)
                        .takeIf { it in 1..7 }
                        ?.let { add(DayOfWeek.of(it)) }
                }
            }
            return ClassSchedule(
                id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
                name = json.optString("name", "Class"),
                locationLabel = json.optString("locationLabel", "Class location"),
                resolvedAddress = json.optString("resolvedAddress", ""),
                latitude = json.optDouble("latitude", 0.0),
                longitude = json.optDouble("longitude", 0.0),
                radiusMeters = json.optDouble("radiusMeters", 150.0).toFloat(),
                days = days,
                startMinutes = json.optInt("startMinutes", 9 * 60),
                endMinutes = json.optInt("endMinutes", 10 * 60),
                dndMode = DndMode.fromStored(json.optString("dndMode")),
                enabled = json.optBoolean("enabled", true),
            )
        }
    }
}

data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val capturedAtMillis: Long,
)

fun Int.asTime(): LocalTime {
    val normalized = coerceIn(0, 23 * 60 + 59)
    return LocalTime.of(normalized / 60, normalized % 60)
}

fun Int.formatAsTime(): String = asTime().format(
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT),
)

fun Set<DayOfWeek>.formatDays(): String {
    if (size == 7) return "Every day"
    if (this == DayOfWeek.values().take(5).toSet()) return "Weekdays"
    if (this == setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) return "Weekends"
    return sortedBy { it.value }.joinToString(" · ") {
        it.name.lowercase().replaceFirstChar { character -> character.uppercase() }.take(3)
    }
}
