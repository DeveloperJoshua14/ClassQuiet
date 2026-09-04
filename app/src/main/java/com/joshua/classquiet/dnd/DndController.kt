package com.joshua.classquiet.dnd

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.joshua.classquiet.model.DndMode

data class DndResult(
    val success: Boolean,
    val message: String,
)

class DndController(context: Context) {
    private val appContext = context.applicationContext
    private val notificationManager =
        appContext.getSystemService(NotificationManager::class.java)
    private val preferences =
        appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun hasPolicyAccess(): Boolean = notificationManager.isNotificationPolicyAccessGranted

    fun apply(mode: DndMode): DndResult {
        if (!hasPolicyAccess()) {
            return DndResult(false, "Do Not Disturb access has not been granted.")
        }

        return runCatching {
            rememberPreviousFilterIfNecessary()
            when (mode) {
                DndMode.VISUAL_ONLY -> {
                    notificationManager.notificationPolicy = visualOnlyPolicy()
                    notificationManager.setInterruptionFilter(
                        NotificationManager.INTERRUPTION_FILTER_PRIORITY,
                    )
                }

                DndMode.ALARMS_ONLY -> notificationManager.setInterruptionFilter(
                    NotificationManager.INTERRUPTION_FILTER_ALARMS,
                )

                DndMode.TOTAL_SILENCE -> notificationManager.setInterruptionFilter(
                    NotificationManager.INTERRUPTION_FILTER_NONE,
                )
            }
            preferences.edit().putBoolean(KEY_APP_ACTIVE, true).apply()
            DndResult(true, "${mode.displayName} is active.")
        }.getOrElse { error ->
            DndResult(false, error.message ?: "Android rejected the Do Not Disturb change.")
        }
    }

    fun deactivate(): DndResult {
        if (!preferences.getBoolean(KEY_APP_ACTIVE, false)) {
            return DndResult(true, "ClassQuiet is not controlling Do Not Disturb.")
        }
        if (!hasPolicyAccess()) {
            preferences.edit().putBoolean(KEY_APP_ACTIVE, false).apply()
            return DndResult(false, "Do Not Disturb access was removed while class mode was active.")
        }

        return runCatching {
            val targetUsesAppOwnedRule =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM &&
                    appContext.applicationInfo.targetSdkVersion >= Build.VERSION_CODES.VANILLA_ICE_CREAM
            val restoreFilter = if (targetUsesAppOwnedRule) {
                NotificationManager.INTERRUPTION_FILTER_ALL
            } else {
                preferences.getInt(
                    KEY_PREVIOUS_FILTER,
                    NotificationManager.INTERRUPTION_FILTER_ALL,
                )
            }
            notificationManager.setInterruptionFilter(restoreFilter)
            preferences.edit()
                .putBoolean(KEY_APP_ACTIVE, false)
                .remove(KEY_PREVIOUS_FILTER)
                .apply()
            DndResult(true, "Class mode is off.")
        }.getOrElse { error ->
            DndResult(false, error.message ?: "Android rejected the Do Not Disturb change.")
        }
    }

    private fun rememberPreviousFilterIfNecessary() {
        if (!preferences.getBoolean(KEY_APP_ACTIVE, false)) {
            preferences.edit()
                .putInt(KEY_PREVIOUS_FILTER, notificationManager.currentInterruptionFilter)
                .apply()
        }
    }

    private fun visualOnlyPolicy(): NotificationManager.Policy {
        val categories = NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS or
            NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA or
            NotificationManager.Policy.PRIORITY_CATEGORY_SYSTEM
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            NotificationManager.Policy(
                categories,
                NotificationManager.Policy.PRIORITY_SENDERS_ANY,
                NotificationManager.Policy.PRIORITY_SENDERS_ANY,
                0,
                NotificationManager.Policy.CONVERSATION_SENDERS_NONE,
            )
        } else {
            NotificationManager.Policy(
                categories,
                NotificationManager.Policy.PRIORITY_SENDERS_ANY,
                NotificationManager.Policy.PRIORITY_SENDERS_ANY,
                0,
            )
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "classquiet_dnd"
        const val KEY_APP_ACTIVE = "app_active"
        const val KEY_PREVIOUS_FILTER = "previous_filter"
    }
}

