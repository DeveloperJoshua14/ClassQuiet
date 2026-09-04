package com.joshua.classquiet.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.joshua.classquiet.ClassQuietApplication

class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as ClassQuietApplication
        app.coordinator.refreshBackgroundRegistrations()
        app.coordinator.enqueueEvaluation("system_${intent.action.orEmpty()}")
    }
}
