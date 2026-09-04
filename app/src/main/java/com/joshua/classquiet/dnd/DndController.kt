package com.joshua.classquiet.dnd

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Build
import android.service.notification.Condition
import android.service.notification.ZenPolicy
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import com.joshua.classquiet.MainActivity
import com.joshua.classquiet.R
import com.joshua.classquiet.model.ClassSchedule
import com.joshua.classquiet.model.ConversationAudience
import com.joshua.classquiet.model.CustomDndSettings
import com.joshua.classquiet.model.DndMode
import com.joshua.classquiet.model.PeopleAudience

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
    private val conditionId = Uri.Builder()
        .scheme(Condition.SCHEME)
        .authority(appContext.packageName)
        .appendPath("class-mode")
        .build()
    private val configurationActivity = ComponentName(appContext, MainActivity::class.java)

    fun hasPolicyAccess(): Boolean = notificationManager.isNotificationPolicyAccessGranted

    fun migrateRuleIconIfNeeded() {
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM ||
            !hasPolicyAccess() ||
            preferences.getInt(KEY_RULE_SCHEMA_VERSION, 0) >= CURRENT_RULE_SCHEMA_VERSION
        ) {
            return
        }
        val storedId = preferences.getString(KEY_RULE_ID, null) ?: return
        val existing = notificationManager.getAutomaticZenRule(storedId) ?: return
        runCatching {
            val replacement = AutomaticZenRule.Builder(existing)
                .setIconResId(R.drawable.ic_school_mode)
                .build()
            check(notificationManager.removeAutomaticZenRule(storedId)) {
                "Android did not allow the old Quiet Classes Mode to be replaced."
            }
            val replacementId = notificationManager.addAutomaticZenRule(replacement)
            if (replacementId == null) {
                preferences.edit(commit = true) {
                    remove(KEY_RULE_ID)
                    remove(KEY_RULE_SCHEMA_VERSION)
                }
            } else {
                preferences.edit(commit = true) {
                    putString(KEY_RULE_ID, replacementId)
                    putInt(KEY_RULE_SCHEMA_VERSION, CURRENT_RULE_SCHEMA_VERSION)
                }
            }
        }
    }

    fun apply(schedule: ClassSchedule, ruleName: String): DndResult {
        if (!hasPolicyAccess()) {
            return DndResult(false, "Do Not Disturb access has not been granted.")
        }

        return runCatching {
            val profile = profileFor(schedule)
            val ruleId = ensureRule(ruleName = ruleName, profile = profile)
            notificationManager.setAutomaticZenRuleState(
                ruleId,
                Condition(
                    conditionId,
                    "Active for ${schedule.name}",
                    Condition.STATE_TRUE,
                ),
            )
            preferences.edit {
                putBoolean(KEY_APP_ACTIVE, true)
                remove(KEY_PREVIOUS_FILTER)
            }
            DndResult(true, "${schedule.dndMode.displayName} is active.")
        }.getOrElse { error ->
            DndResult(false, error.message ?: "Android rejected the Do Not Disturb change.")
        }
    }

    fun updateRuleName(ruleName: String): DndResult {
        if (!hasPolicyAccess()) {
            return DndResult(false, "Grant Do Not Disturb access before renaming the Android Mode.")
        }
        val ruleId = preferences.getString(KEY_RULE_ID, null)
            ?: return DndResult(true, "The Android Mode will use this name when first activated.")

        return runCatching {
            val existing = notificationManager.getAutomaticZenRule(ruleId)
                ?: return@runCatching DndResult(
                    true,
                    "The Android Mode will use this name when it is recreated.",
                )
            val renamed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                AutomaticZenRule.Builder(existing)
                    .setName(ruleName)
                    .build()
            } else {
                existing.apply { name = ruleName }
            }
            check(notificationManager.updateAutomaticZenRule(ruleId, renamed)) {
                "Android did not allow this Mode to be renamed."
            }
            DndResult(true, "Android Mode renamed to $ruleName.")
        }.getOrElse { error ->
            DndResult(false, error.message ?: "Android rejected the Mode name change.")
        }
    }

    fun deactivate(): DndResult {
        if (!preferences.getBoolean(KEY_APP_ACTIVE, false)) {
            return DndResult(true, "Quiet Classes is not controlling Do Not Disturb.")
        }
        if (!hasPolicyAccess()) {
            preferences.edit { putBoolean(KEY_APP_ACTIVE, false) }
            return DndResult(false, "Do Not Disturb access was removed while class mode was active.")
        }

        return runCatching {
            val ruleId = preferences.getString(KEY_RULE_ID, null)
            val ruleExists = ruleId?.let(notificationManager::getAutomaticZenRule) != null
            if (ruleId != null && ruleExists) {
                notificationManager.setAutomaticZenRuleState(
                    ruleId,
                    Condition(conditionId, "No qualifying class is active", Condition.STATE_FALSE),
                )
            } else {
                restoreLegacyInterruptionFilter()
                preferences.edit { remove(KEY_RULE_ID) }
            }
            preferences.edit {
                putBoolean(KEY_APP_ACTIVE, false)
                remove(KEY_PREVIOUS_FILTER)
            }
            DndResult(true, "Class mode is off.")
        }.getOrElse { error ->
            DndResult(false, error.message ?: "Android rejected the Do Not Disturb change.")
        }
    }

    private fun ensureRule(ruleName: String, profile: DndProfile): String {
        val storedId = preferences.getString(KEY_RULE_ID, null)
        val existing = storedId?.let(notificationManager::getAutomaticZenRule)
        val desired = buildRule(ruleName, profile)

        if (storedId != null && existing != null) {
            val needsIconMigration = preferences.getInt(KEY_RULE_SCHEMA_VERSION, 0) <
                CURRENT_RULE_SCHEMA_VERSION
            if (needsIconMigration && notificationManager.removeAutomaticZenRule(storedId)) {
                preferences.edit(commit = true) { remove(KEY_RULE_ID) }
            } else {
                check(notificationManager.updateAutomaticZenRule(storedId, desired)) {
                    "Android did not allow Quiet Classes to update its Mode. " +
                        "Open Android Modes settings and make sure the Mode is enabled."
                }
                preferences.edit {
                    putInt(KEY_RULE_SCHEMA_VERSION, CURRENT_RULE_SCHEMA_VERSION)
                }
                return storedId
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // Version 1 used Android's implicit app rule. This safely deactivates only that
            // app-owned implicit rule before the named explicit rule is created.
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }
        val createdId = checkNotNull(notificationManager.addAutomaticZenRule(desired)) {
            "Android could not create the Quiet Classes Mode."
        }
        preferences.edit(commit = true) {
            putString(KEY_RULE_ID, createdId)
            putInt(KEY_RULE_SCHEMA_VERSION, CURRENT_RULE_SCHEMA_VERSION)
        }
        return createdId
    }

    @Suppress("DEPRECATION")
    private fun buildRule(ruleName: String, profile: DndProfile): AutomaticZenRule =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            AutomaticZenRule.Builder(ruleName, conditionId)
                .setConfigurationActivity(configurationActivity)
                .setEnabled(true)
                .setIconResId(R.drawable.ic_school_mode)
                .setInterruptionFilter(profile.interruptionFilter)
                .setZenPolicy(profile.policy)
                .setManualInvocationAllowed(false)
                .setTriggerDescription("During a configured class schedule")
                .setType(AutomaticZenRule.TYPE_OTHER)
                .build()
        } else {
            AutomaticZenRule(
                ruleName,
                null,
                configurationActivity,
                conditionId,
                profile.policy,
                profile.interruptionFilter,
                true,
            )
        }

    private fun profileFor(schedule: ClassSchedule): DndProfile = when (schedule.dndMode) {
        DndMode.VISUAL_ONLY -> DndProfile(
            interruptionFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY,
            policy = ZenPolicy.Builder()
                .disallowAllSounds()
                .allowAlarms(true)
                .allowMedia(true)
                .allowSystem(true)
                .showAllVisualEffects()
                .build(),
        )

        DndMode.ALARMS_ONLY -> DndProfile(
            interruptionFilter = NotificationManager.INTERRUPTION_FILTER_ALARMS,
            policy = null,
        )

        DndMode.TOTAL_SILENCE -> DndProfile(
            interruptionFilter = NotificationManager.INTERRUPTION_FILTER_NONE,
            policy = null,
        )

        DndMode.CUSTOM -> DndProfile(
            interruptionFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY,
            policy = customPolicy(schedule.customDndSettings),
        )
    }

    private fun customPolicy(custom: CustomDndSettings): ZenPolicy {
        val builder = ZenPolicy.Builder()
            .disallowAllSounds()
            .allowAlarms(custom.allowAlarms)
            .allowMedia(custom.allowMedia)
            .allowSystem(custom.allowSystemSounds)
            .allowReminders(custom.allowReminders)
            .allowEvents(custom.allowEvents)
            .allowRepeatCallers(custom.allowRepeatCallers)
            .allowCalls(custom.calls.asZenPeopleType())
            .allowMessages(custom.messages.asZenPeopleType())
            .showFullScreenIntent(custom.showFullScreenIntents)
            .showLights(custom.showLights)
            .showPeeking(custom.showPeeking)
            .showStatusBarIcons(custom.showStatusBarIcons)
            .showBadges(custom.showBadges)
            .showInAmbientDisplay(custom.showAmbientDisplay)
            .showInNotificationList(custom.showNotificationList)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.allowConversations(custom.conversations.asZenConversationType())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            builder.allowPriorityChannels(custom.allowPriorityChannels)
        }
        return builder.build()
    }

    private fun PeopleAudience.asZenPeopleType(): Int = when (this) {
        PeopleAudience.NONE -> ZenPolicy.PEOPLE_TYPE_NONE
        PeopleAudience.STARRED -> ZenPolicy.PEOPLE_TYPE_STARRED
        PeopleAudience.CONTACTS -> ZenPolicy.PEOPLE_TYPE_CONTACTS
        PeopleAudience.ANYONE -> ZenPolicy.PEOPLE_TYPE_ANYONE
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun ConversationAudience.asZenConversationType(): Int = when (this) {
        ConversationAudience.NONE -> ZenPolicy.CONVERSATION_SENDERS_NONE
        ConversationAudience.IMPORTANT -> ZenPolicy.CONVERSATION_SENDERS_IMPORTANT
        ConversationAudience.ANYONE -> ZenPolicy.CONVERSATION_SENDERS_ANYONE
    }

    private fun restoreLegacyInterruptionFilter() {
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
    }

    private data class DndProfile(
        val interruptionFilter: Int,
        val policy: ZenPolicy?,
    )

    private companion object {
        const val PREFERENCES_NAME = "classquiet_dnd"
        const val KEY_APP_ACTIVE = "app_active"
        const val KEY_PREVIOUS_FILTER = "previous_filter"
        const val KEY_RULE_ID = "automatic_rule_id"
        const val KEY_RULE_SCHEMA_VERSION = "automatic_rule_schema_version"
        const val CURRENT_RULE_SCHEMA_VERSION = 2
    }
}
