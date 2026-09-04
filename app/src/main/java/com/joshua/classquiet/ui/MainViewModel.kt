package com.joshua.classquiet.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.joshua.classquiet.ClassQuietApplication
import com.joshua.classquiet.data.AppSettings
import com.joshua.classquiet.data.ConfigurationBackup
import com.joshua.classquiet.location.PermissionSnapshot
import com.joshua.classquiet.location.ResolvedLocation
import com.joshua.classquiet.model.ClassSchedule
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ClassQuietApplication

    val schedules: StateFlow<List<ClassSchedule>> = app.scheduleRepository.schedules
    val runtimeStatus = app.runtimeState.status
    val appSettings = app.appSettings.settings

    private val mutablePermissions = MutableStateFlow(app.permissionMonitor.snapshot())
    val permissions: StateFlow<PermissionSnapshot> = mutablePermissions.asStateFlow()

    private val mutableMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = mutableMessages.asSharedFlow()

    fun refreshAfterResume() {
        mutablePermissions.value = app.permissionMonitor.snapshot()
        app.dndController.migrateRuleIconIfNeeded()
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

    fun duplicate(id: String): Boolean {
        val original = app.scheduleRepository.get(id) ?: return false
        return runCatching {
            val duplicate = original.copy(
                id = UUID.randomUUID().toString(),
                name = "${original.name} copy",
                locations = original.savedLocations.map {
                    it.copy(id = UUID.randomUUID().toString())
                },
            )
            app.scheduleRepository.upsert(duplicate)
            app.coordinator.refreshBackgroundRegistrations()
            app.coordinator.enqueueEvaluation("schedule_duplicated")
            mutableMessages.tryEmit("${duplicate.name} created")
        }.onFailure {
            mutableMessages.tryEmit(it.message ?: "Could not duplicate the class")
        }.isSuccess
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

    fun setDndRuleName(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            mutableMessages.tryEmit("Enter a name for the Android Mode.")
            return false
        }
        if (trimmed.length > AppSettings.MAX_RULE_NAME_LENGTH) {
            mutableMessages.tryEmit(
                "Mode names can be at most ${AppSettings.MAX_RULE_NAME_LENGTH} characters.",
            )
            return false
        }
        return runCatching {
            app.appSettings.setDndRuleName(trimmed)
            val renameResult = app.dndController.updateRuleName(trimmed)
            mutableMessages.tryEmit(renameResult.message)
            if (app.runtimeState.status.value.state == com.joshua.classquiet.data.RuntimeState.ACTIVE) {
                app.coordinator.enqueueEvaluation("mode_name_changed")
            }
        }.onFailure {
            mutableMessages.tryEmit(it.message ?: "Could not save the Android Mode name.")
        }.isSuccess
    }

    fun exportConfiguration(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val json = ConfigurationBackup.encode(
                    schedules = app.scheduleRepository.current(),
                    settings = app.appSettings.current(),
                )
                val stream = checkNotNull(
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt"),
                ) { "Android could not open the selected file." }
                stream.bufferedWriter(Charsets.UTF_8).use { it.write(json) }
            }.onSuccess {
                mutableMessages.tryEmit("Backup exported successfully.")
            }.onFailure {
                mutableMessages.tryEmit(it.message ?: "Could not export the backup.")
            }
        }
    }

    fun importConfiguration(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val stream = checkNotNull(
                    getApplication<Application>().contentResolver.openInputStream(uri),
                ) { "Android could not open the selected file." }
                val raw = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val imported = ConfigurationBackup.decode(raw)
                app.scheduleRepository.replaceAll(imported.schedules)
                app.appSettings.replace(imported.settings)
                if (app.dndController.hasPolicyAccess()) {
                    app.dndController.updateRuleName(imported.settings.dndRuleName)
                }
                app.coordinator.refreshBackgroundRegistrations()
                app.coordinator.enqueueEvaluation("backup_imported")
                "${imported.schedules.size} classes imported."
            }.onSuccess {
                mutableMessages.tryEmit(it)
            }.onFailure {
                mutableMessages.tryEmit(it.message ?: "Could not import the backup.")
            }
        }
    }
}
