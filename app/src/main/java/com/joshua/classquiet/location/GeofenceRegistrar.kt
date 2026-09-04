package com.joshua.classquiet.location

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import com.joshua.classquiet.background.GeofenceReceiver
import com.joshua.classquiet.data.RuntimeStateStore
import com.joshua.classquiet.model.ClassSchedule
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeofenceRegistrar(
    private val context: Context,
    private val runtimeState: RuntimeStateStore,
) {
    private val client = LocationServices.getGeofencingClient(context)
    private val refreshGeneration = AtomicInteger(0)

    fun refresh(schedules: List<ClassSchedule>) {
        val enabled = schedules.filter { it.enabled }.take(MAX_GEOFENCES)
        runtimeState.replaceInsideGeofences(enabled.mapTo(mutableSetOf()) { it.id })
        val generation = refreshGeneration.incrementAndGet()
        val pendingIntent = geofencePendingIntent()

        client.removeGeofences(pendingIntent).addOnCompleteListener {
            if (generation != refreshGeneration.get() || enabled.isEmpty()) return@addOnCompleteListener
            if (!hasRequiredLocationPermissions()) return@addOnCompleteListener

            addGeofences(buildRequest(enabled), pendingIntent)
        }
    }

    suspend fun refreshAndAwait(schedules: List<ClassSchedule>): Boolean = withContext(Dispatchers.IO) {
        val enabled = schedules.filter { it.enabled }.take(MAX_GEOFENCES)
        runtimeState.replaceInsideGeofences(enabled.mapTo(mutableSetOf()) { it.id })
        val pendingIntent = geofencePendingIntent()
        runCatching {
            Tasks.await(client.removeGeofences(pendingIntent))
            if (enabled.isNotEmpty() && hasRequiredLocationPermissions()) {
                Tasks.await(client.addGeofences(buildRequest(enabled), pendingIntent))
            }
            true
        }.onFailure { Log.w(TAG, "Unable to refresh class geofences", it) }
            .getOrDefault(false)
    }

    fun clear() {
        refreshGeneration.incrementAndGet()
        client.removeGeofences(geofencePendingIntent())
        runtimeState.replaceInsideGeofences(emptySet())
    }

    @Suppress("MissingPermission")
    private fun addGeofences(request: GeofencingRequest, pendingIntent: PendingIntent) {
        try {
            client.addGeofences(request, pendingIntent)
                .addOnFailureListener { Log.w(TAG, "Unable to register class geofences", it) }
        } catch (error: SecurityException) {
            Log.w(TAG, "Location permission was removed while registering geofences", error)
        }
    }

    private fun hasRequiredLocationPermissions(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val background = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        return fine && background
    }

    private fun buildRequest(schedules: List<ClassSchedule>): GeofencingRequest {
        val geofences = schedules.map { schedule ->
            Geofence.Builder()
                .setRequestId(schedule.id)
                .setCircularRegion(
                    schedule.latitude,
                    schedule.longitude,
                    schedule.radiusMeters.coerceIn(MIN_RADIUS_METERS, MAX_RADIUS_METERS),
                )
                .setTransitionTypes(
                    Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT,
                )
                .setNotificationResponsiveness(60_000)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .build()
        }
        return GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(geofences)
            .build()
    }

    private fun geofencePendingIntent(): PendingIntent {
        val mutability = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        return PendingIntent.getBroadcast(
            context,
            GEOFENCE_REQUEST_CODE,
            Intent(context, GeofenceReceiver::class.java).setAction(ACTION_GEOFENCE),
            PendingIntent.FLAG_UPDATE_CURRENT or mutability,
        )
    }

    companion object {
        const val ACTION_GEOFENCE = "com.joshua.classquiet.action.GEOFENCE"
        private const val GEOFENCE_REQUEST_CODE = 4108
        private const val MAX_GEOFENCES = 100
        private const val MIN_RADIUS_METERS = 50f
        private const val MAX_RADIUS_METERS = 2_000f
        private const val TAG = "ClassQuietGeofence"
    }
}
