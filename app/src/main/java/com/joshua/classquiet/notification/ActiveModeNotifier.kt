package com.joshua.classquiet.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.joshua.classquiet.MainActivity
import com.joshua.classquiet.R
import com.joshua.classquiet.model.ClassSchedule

class ActiveModeNotifier(context: Context) {
    private val appContext = context.applicationContext
    private val notificationManager = appContext.getSystemService(NotificationManager::class.java)

    init {
        createChannel()
    }

    fun show(ruleName: String, activeClasses: List<ClassSchedule>, modeLabel: String) {
        if (!canPostNotifications()) return
        val classNames = activeClasses.joinToString { it.name }
        val text = "$classNames • $modeLabel"
        val contentIntent = PendingIntent.getActivity(
            appContext,
            CONTENT_REQUEST_CODE,
            Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$ruleName active")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
        NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
    }

    fun cancel() {
        NotificationManagerCompat.from(appContext).cancel(NOTIFICATION_ID)
    }

    private fun canPostNotifications(): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(appContext).areNotificationsEnabled()

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Active class mode",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows which class-based Do Not Disturb mode is currently active."
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "active_class_mode"
        const val NOTIFICATION_ID = 4108
        const val CONTENT_REQUEST_CODE = 4109
    }
}
