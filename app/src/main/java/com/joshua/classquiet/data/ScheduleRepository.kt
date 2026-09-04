package com.joshua.classquiet.data

import android.content.Context
import com.joshua.classquiet.model.ClassSchedule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

class ScheduleRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val lock = Any()
    private val mutableSchedules = MutableStateFlow(readSchedules())

    val schedules: StateFlow<List<ClassSchedule>> = mutableSchedules.asStateFlow()

    fun current(): List<ClassSchedule> = mutableSchedules.value

    fun get(id: String): ClassSchedule? = current().firstOrNull { it.id == id }

    fun upsert(schedule: ClassSchedule) = synchronized(lock) {
        val updated = current().toMutableList()
        val index = updated.indexOfFirst { it.id == schedule.id }
        if (index >= 0) updated[index] = schedule else updated += schedule
        persist(updated)
    }

    fun delete(id: String) = synchronized(lock) {
        persist(current().filterNot { it.id == id })
    }

    fun setEnabled(id: String, enabled: Boolean) = synchronized(lock) {
        persist(current().map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    private fun readSchedules(): List<ClassSchedule> {
        val raw = preferences.getString(KEY_SCHEDULES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    runCatching { ClassSchedule.fromJson(array.getJSONObject(index)) }
                        .getOrNull()
                        ?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persist(schedules: List<ClassSchedule>) {
        val sorted = schedules.sortedWith(
            compareBy<ClassSchedule> { it.days.minOfOrNull { day -> day.value } ?: 8 }
                .thenBy { it.startMinutes }
                .thenBy { it.name.lowercase() },
        )
        val array = JSONArray().apply { sorted.forEach { put(it.toJson()) } }
        check(preferences.edit().putString(KEY_SCHEDULES, array.toString()).commit()) {
            "Unable to save class schedules"
        }
        mutableSchedules.value = sorted
    }

    private companion object {
        const val PREFERENCES_NAME = "classquiet_schedules"
        const val KEY_SCHEDULES = "schedules"
    }
}

