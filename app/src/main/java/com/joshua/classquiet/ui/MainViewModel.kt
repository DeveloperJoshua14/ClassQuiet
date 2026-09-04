package com.joshua.classquiet.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.joshua.classquiet.ClassQuietApplication
import com.joshua.classquiet.location.PermissionSnapshot
import com.joshua.classquiet.location.ResolvedLocation
import com.joshua.classquiet.model.ClassSchedule
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ClassQuietApplication

    val schedules: StateFlow<List<ClassSchedule>> = app.scheduleRepository.schedules
    val runtimeStatus = app.runtimeState.status

    private val mutablePermissions = MutableStateFlow(app.permissionMonitor.snapshot())
    val permissions: StateFlow<PermissionSnapshot> = mutablePermissions.asStateFlow()

    private val mutableMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = mutableMessages.asSharedFlow()

    fun findSchedule(id: String?): ClassSchedule? = id?.let(app.scheduleRepository::get)

    fun refreshAfterResume() {
        mutablePermissions.value = app.permissionMonitor.snapshot()
        app.coordinator.refreshBackgroundRegistrations()
        app.coordinator.enqueueEvaluation("app_resume")
    }

    fun save(schedule: ClassSchedule): Boolean = runCatching {
        app.scheduleRepository.upsert(schedule)
        app.coordinator.refreshBackgroundRegistrations()
        app.coordinator.enqueueEvaluation("schedule_saved")
        mutableMessages.tryEmit("${schedule.name} saved")
    }.onFailure {
        mutableMessages.tryEmit(it.message ?: "Could not save the class")
    }.isSuccess

    fun delete(id: String) {
        val name = app.scheduleRepository.get(id)?.name ?: "Class"
        runCatching {
            app.scheduleRepository.delete(id)
            app.coordinator.refreshBackgroundRegistrations()
            app.coordinator.enqueueEvaluation("schedule_deleted")
            mutableMessages.tryEmit("$name deleted")
        }.onFailure {
            mutableMessages.tryEmit(it.message ?: "Could not delete the class")
        }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        runCatching {
            app.scheduleRepository.setEnabled(id, enabled)
            app.coordinator.refreshBackgroundRegistrations()
            app.coordinator.enqueueEvaluation("schedule_toggled")
        }.onFailure {
            mutableMessages.tryEmit(it.message ?: "Could not update the class")
        }
    }

    fun checkNow() {
        app.coordinator.enqueueEvaluation("manual_check")
        mutableMessages.tryEmit("Checking time and location…")
    }

    suspend fun resolveAddress(query: String): ResolvedLocation? = runCatching {
        app.locationManager.resolveAddress(query)
    }.onFailure {
        mutableMessages.tryEmit(it.message ?: "Address lookup failed")
    }.getOrNull()

    suspend fun useCurrentLocation(): ResolvedLocation? = runCatching {
        app.locationManager.currentResolvedLocation()
    }.onFailure {
        mutableMessages.tryEmit(it.message ?: "Could not get your current location")
    }.getOrNull()

    fun showMessage(message: String) {
        mutableMessages.tryEmit(message)
    }

    fun refreshPermissionsOnly() {
        mutablePermissions.value = app.permissionMonitor.snapshot()
    }
}
