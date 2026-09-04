package com.joshua.classquiet.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.joshua.classquiet.ClassQuietApplication

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_CLASS_BOUNDARY) return
        val app = context.applicationContext as ClassQuietApplication
        app.alarmScheduler.scheduleNext(app.scheduleRepository.current())
        app.coordinator.fastEvaluateFromGeofenceHints()
        app.coordinator.enqueueEvaluation("class_boundary")
    }
}

