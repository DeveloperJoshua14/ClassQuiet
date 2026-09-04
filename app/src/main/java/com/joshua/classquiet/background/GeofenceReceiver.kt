package com.joshua.classquiet.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofenceStatusCodes
import com.joshua.classquiet.ClassQuietApplication
import com.joshua.classquiet.location.GeofenceRegistrar

class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != GeofenceRegistrar.ACTION_GEOFENCE) return
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Log.w(TAG, GeofenceStatusCodes.getStatusCodeString(event.errorCode))
            return
        }

        val ids = event.triggeringGeofences?.mapTo(mutableSetOf()) { it.requestId }.orEmpty()
        if (ids.isEmpty()) return
        val app = context.applicationContext as ClassQuietApplication
        when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                app.runtimeState.updateInsideGeofences(ids, true)
                app.coordinator.fastEvaluateFromGeofenceHints()
                app.coordinator.enqueueEvaluation("geofence_enter")
            }

            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                app.runtimeState.updateInsideGeofences(ids, false)
                app.coordinator.fastEvaluateFromGeofenceHints(definitiveNoMatch = true)
                app.coordinator.enqueueEvaluation("geofence_exit")
            }
        }
    }

    private companion object {
        const val TAG = "ClassQuietGeofence"
    }
}

