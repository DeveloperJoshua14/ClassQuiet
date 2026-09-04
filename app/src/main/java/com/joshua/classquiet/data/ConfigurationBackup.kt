package com.joshua.classquiet.data

import com.joshua.classquiet.model.ClassSchedule
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

data class ImportedConfiguration(
    val schedules: List<ClassSchedule>,
    val settings: AppSettings,
)

object ConfigurationBackup {
    private const val FORMAT = "quiet-classes-backup"
    private const val CURRENT_VERSION = 1
    private const val MAX_SCHEDULES = 1_000

    fun encode(schedules: List<ClassSchedule>, settings: AppSettings): String =
        JSONObject().apply {
            put("format", FORMAT)
            put("version", CURRENT_VERSION)
            put("exportedAt", Instant.now().toString())
            put("settings", settings.toJson())
            put("classes", JSONArray().apply { schedules.forEach { put(it.toJson()) } })
        }.toString(2)

    fun decode(raw: String): ImportedConfiguration {
        require(raw.isNotBlank()) { "The selected backup is empty." }
        val root = runCatching { JSONObject(raw) }.getOrElse {
            throw IllegalArgumentException("The selected file is not valid JSON.")
        }
        require(root.optString("format") == FORMAT) {
            "This is not a Quiet Classes backup file."
        }
        val version = root.optInt("version", -1)
        require(version in 1..CURRENT_VERSION) {
            "This backup uses an unsupported format version."
        }

        val array = root.optJSONArray("classes")
            ?: throw IllegalArgumentException("The backup does not contain a class list.")
        require(array.length() <= MAX_SCHEDULES) { "The backup contains too many classes." }

        val schedules = buildList {
            for (index in 0 until array.length()) {
                val schedule = runCatching {
                    ClassSchedule.fromJson(array.getJSONObject(index))
                }.getOrElse {
                    throw IllegalArgumentException("Class ${index + 1} in the backup is invalid.")
                }
                validate(schedule, index)
                add(schedule)
            }
        }.distinctBy { it.id }

        return ImportedConfiguration(
            schedules = schedules,
            settings = AppSettings.fromJson(root.optJSONObject("settings")),
        )
    }

    private fun validate(schedule: ClassSchedule, index: Int) {
        val label = "Class ${index + 1}"
        require(schedule.name.isNotBlank()) { "$label has no name." }
        require(schedule.locationLabel.isNotBlank()) { "$label has no location label." }
        require(schedule.latitude in -90.0..90.0) { "$label has an invalid latitude." }
        require(schedule.longitude in -180.0..180.0) { "$label has an invalid longitude." }
        require(schedule.radiusMeters in 25f..5_000f) { "$label has an invalid location radius." }
        require(schedule.days.isNotEmpty()) { "$label has no scheduled days." }
        require(schedule.startMinutes in 0..1_439 && schedule.endMinutes in 0..1_439) {
            "$label has an invalid time."
        }
        require(schedule.startMinutes != schedule.endMinutes) {
            "$label has identical start and end times."
        }
    }
}
