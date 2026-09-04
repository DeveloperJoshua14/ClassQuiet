package com.joshua.classquiet.location

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat

data class PermissionSnapshot(
    val fineLocation: Boolean,
    val backgroundLocation: Boolean,
    val dndAccess: Boolean,
    val exactAlarms: Boolean,
    val locationServices: Boolean,
) {
    val ready: Boolean
        get() = fineLocation && backgroundLocation && dndAccess && exactAlarms && locationServices

    val completedCount: Int
        get() = listOf(fineLocation, backgroundLocation, dndAccess, exactAlarms, locationServices)
            .count { it }
}

class PermissionMonitor(private val context: Context) {
    fun snapshot(): PermissionSnapshot {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val background = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            fine
        }
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val locationManager = context.getSystemService(LocationManager::class.java)
        return PermissionSnapshot(
            fineLocation = fine,
            backgroundLocation = background,
            dndAccess = notificationManager.isNotificationPolicyAccessGranted,
            exactAlarms = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms(),
            locationServices = locationManager.isLocationEnabled,
        )
    }
}

