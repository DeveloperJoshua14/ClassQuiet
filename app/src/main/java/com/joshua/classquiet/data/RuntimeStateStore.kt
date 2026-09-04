package com.joshua.classquiet.data

import android.content.Context
import com.joshua.classquiet.model.DndMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RuntimeState {
    IDLE,
    ACTIVE,
    OUTSIDE_CLASS,
    NEEDS_PERMISSION,
    LOCATION_UNAVAILABLE,
    ERROR,
}

data class RuntimeStatus(
    val state: RuntimeState = RuntimeState.IDLE,
    val headline: String = "Quiet Classes is ready",
    val detail: String = "No class is currently active.",
    val activeScheduleIds: List<String> = emptyList(),
    val activeScheduleNames: List<String> = emptyList(),
    val appliedMode: DndMode? = null,
    val updatedAtMillis: Long = 0L,
)

class RuntimeStateStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val lock = Any()
    private val mutableStatus = MutableStateFlow(readStatus())

    val status: StateFlow<RuntimeStatus> = mutableStatus.asStateFlow()

    fun insideGeofenceIds(): Set<String> =
        preferences.getStringSet(KEY_INSIDE_IDS, emptySet())?.toSet().orEmpty()

    fun replaceInsideGeofences(validIds: Set<String>) = synchronized(lock) {
        val retained = insideGeofenceIds().intersect(validIds)
        preferences.edit().putStringSet(KEY_INSIDE_IDS, retained).apply()
    }

    fun updateInsideGeofences(ids: Set<String>, inside: Boolean) = synchronized(lock) {
        val updated = insideGeofenceIds().toMutableSet()
        if (inside) updated += ids else updated -= ids
        preferences.edit().putStringSet(KEY_INSIDE_IDS, updated).apply()
    }

    fun setInsideGeofence(id: String, inside: Boolean) =
        updateInsideGeofences(setOf(id), inside)

    fun updateStatus(status: RuntimeStatus) = synchronized(lock) {
        preferences.edit()
            .putString(KEY_STATE, status.state.name)
            .putString(KEY_HEADLINE, status.headline)
            .putString(KEY_DETAIL, status.detail)
            .putStringSet(KEY_ACTIVE_IDS, status.activeScheduleIds.toSet())
            .putStringSet(KEY_ACTIVE_NAMES, status.activeScheduleNames.toSet())
            .putString(KEY_APPLIED_MODE, status.appliedMode?.name)
            .putLong(KEY_UPDATED_AT, status.updatedAtMillis)
            .apply()
        mutableStatus.value = status
    }

    private fun readStatus(): RuntimeStatus = RuntimeStatus(
        state = runCatching {
            RuntimeState.valueOf(preferences.getString(KEY_STATE, null) ?: RuntimeState.IDLE.name)
        }.getOrDefault(RuntimeState.IDLE),
        headline = preferences.getString(KEY_HEADLINE, null) ?: "Quiet Classes is ready",
        detail = preferences.getString(KEY_DETAIL, null) ?: "No class is currently active.",
        activeScheduleIds = preferences.getStringSet(KEY_ACTIVE_IDS, emptySet())?.toList().orEmpty(),
        activeScheduleNames = preferences.getStringSet(KEY_ACTIVE_NAMES, emptySet())?.toList().orEmpty(),
        appliedMode = preferences.getString(KEY_APPLIED_MODE, null)?.let(DndMode::fromStored),
        updatedAtMillis = preferences.getLong(KEY_UPDATED_AT, 0L),
    )

    private companion object {
        const val PREFERENCES_NAME = "classquiet_runtime"
        const val KEY_INSIDE_IDS = "inside_geofence_ids"
        const val KEY_STATE = "state"
        const val KEY_HEADLINE = "headline"
        const val KEY_DETAIL = "detail"
        const val KEY_ACTIVE_IDS = "active_ids"
        const val KEY_ACTIVE_NAMES = "active_names"
        const val KEY_APPLIED_MODE = "applied_mode"
        const val KEY_UPDATED_AT = "updated_at"
    }
}
