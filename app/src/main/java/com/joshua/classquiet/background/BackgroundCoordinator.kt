package com.joshua.classquiet.background

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.joshua.classquiet.data.AppSettingsRepository
import com.joshua.classquiet.data.RuntimeState
import com.joshua.classquiet.data.RuntimeStateStore
import com.joshua.classquiet.data.RuntimeStatus
import com.joshua.classquiet.data.ScheduleRepository
import com.joshua.classquiet.dnd.DndController
import com.joshua.classquiet.location.ClassLocationManager
import com.joshua.classquiet.location.GeofenceRegistrar
import com.joshua.classquiet.model.ClassSchedule
import com.joshua.classquiet.notification.ActiveModeNotifier
import com.joshua.classquiet.util.ScheduleEngine
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class BackgroundCoordinator(
    private val context: Context,
    private val schedules: ScheduleRepository,
    private val runtime: RuntimeStateStore,
    private val locationManager: ClassLocationManager,
    private val dndController: DndController,
    private val alarmScheduler: AlarmScheduler,
    private val geofenceRegistrar: GeofenceRegistrar,
    private val settings: AppSettingsRepository,
    private val notifier: ActiveModeNotifier,
) {
    private val workManager = WorkManager.getInstance(context)

    fun refreshBackgroundRegistrations() {
        val current = schedules.current()
        alarmScheduler.scheduleNext(current)
        geofenceRegistrar.refresh(current)
        ensurePeriodicEvaluation()
    }

    fun ensurePeriodicEvaluation() {
        val request = PeriodicWorkRequestBuilder<EvaluationWorker>(
            PERIODIC_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        ).addTag(PERIODIC_WORK_NAME).build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun enqueueEvaluation(source: String) {
        val request = OneTimeWorkRequestBuilder<EvaluationWorker>()
            .setInputData(workDataOf(EvaluationWorker.KEY_SOURCE to source))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(IMMEDIATE_WORK_NAME)
            .build()
        workManager.enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun fastEvaluateFromGeofenceHints(definitiveNoMatch: Boolean = false) {
        val active = activeSchedules()
        val insideIds = runtime.insideGeofenceIds()
        val matches = active.filter { it.id in insideIds }
        when {
            matches.isNotEmpty() -> applyMatches(matches, "Confirmed by the class geofence.")
            active.isEmpty() || definitiveNoMatch -> markInactive(
                state = if (active.isEmpty()) RuntimeState.IDLE else RuntimeState.OUTSIDE_CLASS,
                detail = if (active.isEmpty()) {
                    "No class is currently active."
                } else {
                    "A class is in progress, but you are outside its saved location."
                },
            )
        }
    }

    suspend fun performEvaluation(source: String): Boolean {
        if (source.startsWith("system_")) {
            geofenceRegistrar.refreshAndAwait(schedules.current())
        }
        val active = activeSchedules()
        if (active.isEmpty()) {
            markInactive(RuntimeState.IDLE, "No class is currently active.")
            alarmScheduler.scheduleNext(schedules.current())
            return true
        }

        if (!dndController.hasPolicyAccess()) {
            notifier.cancel()
            runtime.updateStatus(
                RuntimeStatus(
                    state = RuntimeState.NEEDS_PERMISSION,
                    headline = "Do Not Disturb access needed",
                    detail = "A scheduled class is active, but Android has not allowed Quiet Classes to control DND.",
                    updatedAtMillis = System.currentTimeMillis(),
                ),
            )
            return false
        }

        val location = locationManager.currentLocation()
        val matches = if (location != null) {
            active.filter { schedule ->
                val inside = ScheduleEngine.isInside(schedule, location)
                runtime.setInsideGeofence(schedule.id, inside)
                inside
            }
        } else {
            val reliableIds = runtime.insideGeofenceIds() + runtime.status.value.activeScheduleIds
            active.filter { it.id in reliableIds }
        }

        when {
            matches.isNotEmpty() -> applyMatches(
                matches,
                if (location != null) "Time and current location both match."
                else "Current GPS fix was unavailable; the last confirmed geofence state was used.",
            )

            location == null -> markInactive(
                RuntimeState.LOCATION_UNAVAILABLE,
                "A class is in progress, but Android could not provide a recent location. Check Location Services and battery settings.",
            )

            else -> markInactive(
                RuntimeState.OUTSIDE_CLASS,
                "A class is in progress, but you are outside its saved location.",
            )
        }
        alarmScheduler.scheduleNext(schedules.current())
        return true
    }

    private fun activeSchedules(): List<ClassSchedule> = ScheduleEngine.activeWindows(
        schedules.current(),
        ZonedDateTime.now(),
    ).map { it.schedule }

    private fun applyMatches(matches: List<ClassSchedule>, detail: String) {
        val controllingSchedule = checkNotNull(ScheduleEngine.strongestSchedule(matches))
        val mode = controllingSchedule.dndMode
        val ruleName = settings.current().dndRuleName
        val result = dndController.apply(controllingSchedule, ruleName)
        if (result.success) {
            notifier.show(ruleName, matches, mode.displayName)
        } else {
            notifier.cancel()
        }
        runtime.updateStatus(
            RuntimeStatus(
                state = if (result.success) RuntimeState.ACTIVE else RuntimeState.ERROR,
                headline = if (result.success) "${mode.displayName} is active" else "DND change failed",
                detail = if (result.success) detail else result.message,
                activeScheduleIds = matches.map { it.id },
                activeScheduleNames = matches.map { it.name },
                appliedMode = if (result.success) mode else null,
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    private fun markInactive(state: RuntimeState, detail: String) {
        val result = dndController.deactivate()
        notifier.cancel()
        runtime.updateStatus(
            RuntimeStatus(
                state = if (result.success) state else RuntimeState.ERROR,
                headline = when {
                    !result.success -> "Could not turn class mode off"
                    state == RuntimeState.OUTSIDE_CLASS -> "Outside the class location"
                    state == RuntimeState.LOCATION_UNAVAILABLE -> "Location unavailable"
                    else -> "Class mode is off"
                },
                detail = if (result.success) detail else result.message,
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    companion object {
        private const val PERIODIC_INTERVAL_MINUTES = 15L
        private const val PERIODIC_WORK_NAME = "classquiet-periodic-evaluation"
        private const val IMMEDIATE_WORK_NAME = "classquiet-immediate-evaluation"
    }
}
