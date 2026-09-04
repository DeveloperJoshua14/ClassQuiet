package com.joshua.classquiet.background

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.joshua.classquiet.model.ClassSchedule
import com.joshua.classquiet.util.ScheduleEngine
import java.time.ZonedDateTime

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun scheduleNext(schedules: List<ClassSchedule>) {
        val pendingIntent = boundaryPendingIntent()
        alarmManager.cancel(pendingIntent)
        val boundary = ScheduleEngine.nextBoundary(
            schedules = schedules,
            after = ZonedDateTime.now().plusSeconds(1),
        ) ?: return

        val triggerAtMillis = boundary.toInstant().toEpochMilli()
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent,
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent,
                )
            }
        } catch (error: SecurityException) {
            Log.w(TAG, "Exact alarm access unavailable; using an inexact boundary alarm", error)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        }
    }

    fun cancel() {
        alarmManager.cancel(boundaryPendingIntent())
    }

    private fun boundaryPendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        BOUNDARY_REQUEST_CODE,
        Intent(context, AlarmReceiver::class.java).setAction(ACTION_CLASS_BOUNDARY),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION_CLASS_BOUNDARY = "com.joshua.classquiet.action.CLASS_BOUNDARY"
        private const val BOUNDARY_REQUEST_CODE = 4107
        private const val TAG = "ClassQuietAlarm"
    }
}

