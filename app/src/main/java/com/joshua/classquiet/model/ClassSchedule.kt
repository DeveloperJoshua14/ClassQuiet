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
        severity = 3,
        displayName = "Alarms only",
        description = "Calls and notifications are silenced. Only alarms are allowed to interrupt you.",
    ),
    TOTAL_SILENCE(
        severity = 4,
        displayName = "Total silence",
        description = "Blocks calls, notifications, alarms, vibration, and general media audio.",
    ),
    CUSTOM(
        severity = 2,
        displayName = "Custom",
        description = "Choose exactly which sounds, people, and visual notification effects are allowed.",
    );

    companion object {
        fun fromStored(value: String?): DndMode =
            entries.firstOrNull { it.name == value } ?: VISUAL_ONLY
    }
}

enum class PeopleAudience(val displayName: String) {
    NONE("Nobody"),
    STARRED("Starred contacts"),
    CONTACTS("Contacts"),
    ANYONE("Anyone");

    companion object {
        fun fromStored(value: String?): PeopleAudience =
            entries.firstOrNull { it.name == value } ?: NONE
    }
}

enum class ConversationAudience(val displayName: String) {
    NONE("None"),
    IMPORTANT("Priority conversations"),
    ANYONE("All conversations");

    companion object {
        fun fromStored(value: String?): ConversationAudience =
            entries.firstOrNull { it.name == value } ?: NONE
    }
}

data class CustomDndSettings(
    val allowAlarms: Boolean = true,
    val allowMedia: Boolean = false,
    val allowSystemSounds: Boolean = false,
    val allowReminders: Boolean = false,
    val allowEvents: Boolean = false,
    val allowRepeatCallers: Boolean = false,
    val allowPriorityChannels: Boolean = false,
    val calls: PeopleAudience = PeopleAudience.NONE,
    val messages: PeopleAudience = PeopleAudience.NONE,
    val conversations: ConversationAudience = ConversationAudience.NONE,
    val showFullScreenIntents: Boolean = false,
    val showLights: Boolean = false,
    val showPeeking: Boolean = false,
    val showStatusBarIcons: Boolean = true,
    val showBadges: Boolean = true,
    val showAmbientDisplay: Boolean = false,
    val showNotificationList: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("allowAlarms", allowAlarms)
        put("allowMedia", allowMedia)
        put("allowSystemSounds", allowSystemSounds)
        put("allowReminders", allowReminders)
        put("allowEvents", allowEvents)
        put("allowRepeatCallers", allowRepeatCallers)
        put("allowPriorityChannels", allowPriorityChannels)
        put("calls", calls.name)
        put("messages", messages.name)
        put("conversations", conversations.name)
        put("showFullScreenIntents", showFullScreenIntents)
        put("showLights", showLights)
        put("showPeeking", showPeeking)
        put("showStatusBarIcons", showStatusBarIcons)
        put("showBadges", showBadges)
        put("showAmbientDisplay", showAmbientDisplay)
        put("showNotificationList", showNotificationList)
    }

    companion object {
        fun fromJson(json: JSONObject?): CustomDndSettings {
            if (json == null) return CustomDndSettings()
            return CustomDndSettings(
                allowAlarms = json.optBoolean("allowAlarms", true),
                allowMedia = json.optBoolean("allowMedia", false),
                allowSystemSounds = json.optBoolean("allowSystemSounds", false),
                allowReminders = json.optBoolean("allowReminders", false),
                allowEvents = json.optBoolean("allowEvents", false),
                allowRepeatCallers = json.optBoolean("allowRepeatCallers", false),
                allowPriorityChannels = json.optBoolean("allowPriorityChannels", false),
                calls = PeopleAudience.fromStored(json.optString("calls")),
                messages = PeopleAudience.fromStored(json.optString("messages")),
                conversations = ConversationAudience.fromStored(json.optString("conversations")),
                showFullScreenIntents = json.optBoolean("showFullScreenIntents", false),
                showLights = json.optBoolean("showLights", false),
                showPeeking = json.optBoolean("showPeeking", false),
                showStatusBarIcons = json.optBoolean("showStatusBarIcons", true),
                showBadges = json.optBoolean("showBadges", true),
                showAmbientDisplay = json.optBoolean("showAmbientDisplay", false),
                showNotificationList = json.optBoolean("showNotificationList", true),
            )
        }
    }
}

data class ClassLocation(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val resolvedAddress: String = "",
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float = 150f,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("label", label)
        put("resolvedAddress", resolvedAddress)
        put("latitude", latitude)
        put("longitude", longitude)
        put("radiusMeters", radiusMeters.toDouble())
    }

    companion object {
        fun fromJson(json: JSONObject): ClassLocation = ClassLocation(
            id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
            label = json.optString("label", "Class location"),
            resolvedAddress = json.optString("resolvedAddress", ""),
            latitude = json.optDouble("latitude", 0.0),
            longitude = json.optDouble("longitude", 0.0),
            radiusMeters = json.optDouble("radiusMeters", 150.0).toFloat(),
        )
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
    val customDndSettings: CustomDndSettings = CustomDndSettings(),
    val enabled: Boolean = true,
    val locationEnabled: Boolean = true,
    val locations: List<ClassLocation> = emptyList(),
    val extendPastEnd: Boolean = false,
) {
    val savedLocations: List<ClassLocation>
        get() = if (locations.isNotEmpty()) {
            locations
        } else if (locationLabel.isNotBlank() && locationLabel != "No location") {
            listOf(
                ClassLocation(
                    id = "${id}-primary",
                    label = locationLabel,
                    resolvedAddress = resolvedAddress,
                    latitude = latitude,
                    longitude = longitude,
                    radiusMeters = radiusMeters,
                ),
            )
        } else emptyList()

    val effectiveLocations: List<ClassLocation>
        get() = if (locationEnabled) savedLocations else emptyList()

    val locationSummary: String
        get() = when {
            !locationEnabled -> "No location required"
            effectiveLocations.isEmpty() -> "No location saved"
            effectiveLocations.size == 1 -> effectiveLocations.first().let {
                "${it.label} · ${it.radiusMeters.toInt()} m radius"
            }
            else -> "${effectiveLocations.size} locations · " +
                effectiveLocations.joinToString { it.label }
        }

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
        put("customDndSettings", customDndSettings.toJson())
        put("enabled", enabled)
        put("locationEnabled", locationEnabled)
        put("locations", JSONArray().apply { savedLocations.forEach { put(it.toJson()) } })
        put("extendPastEnd", extendPastEnd)
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
            val storedLocations = json.optJSONArray("locations")
            val locations = buildList {
                if (storedLocations != null) {
                    for (index in 0 until storedLocations.length()) {
                        storedLocations.optJSONObject(index)?.let {
                            add(ClassLocation.fromJson(it))
                        }
                    }
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
                customDndSettings = CustomDndSettings.fromJson(
                    json.optJSONObject("customDndSettings"),
                ),
                enabled = json.optBoolean("enabled", true),
                locationEnabled = json.optBoolean("locationEnabled", true),
                locations = locations,
                extendPastEnd = json.optBoolean("extendPastEnd", false),
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

fun geofenceScheduleId(requestId: String): String = requestId.substringBefore('|')

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
