package com.joshua.classquiet.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

data class AppSettings(
    val dndRuleName: String = DEFAULT_DND_RULE_NAME,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("dndRuleName", dndRuleName)
    }

    companion object {
        const val DEFAULT_DND_RULE_NAME = "Quiet Classes"

        fun fromJson(json: JSONObject?): AppSettings = AppSettings(
            dndRuleName = normalizeRuleName(
                json?.optString("dndRuleName", DEFAULT_DND_RULE_NAME),
            ),
        )

        fun normalizeRuleName(value: String?): String = value
            ?.trim()
            ?.take(MAX_RULE_NAME_LENGTH)
            ?.ifBlank { DEFAULT_DND_RULE_NAME }
            ?: DEFAULT_DND_RULE_NAME

        const val MAX_RULE_NAME_LENGTH = 50
    }
}

class AppSettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableSettings = MutableStateFlow(readSettings())

    val settings: StateFlow<AppSettings> = mutableSettings.asStateFlow()

    fun current(): AppSettings = mutableSettings.value

    fun setDndRuleName(name: String) {
        replace(current().copy(dndRuleName = AppSettings.normalizeRuleName(name)))
    }

    fun replace(settings: AppSettings) {
        val normalized = settings.copy(
            dndRuleName = AppSettings.normalizeRuleName(settings.dndRuleName),
        )
        check(
            preferences.edit()
                .putString(KEY_DND_RULE_NAME, normalized.dndRuleName)
                .commit(),
        ) { "Unable to save app settings" }
        mutableSettings.value = normalized
    }

    private fun readSettings(): AppSettings = AppSettings(
        dndRuleName = AppSettings.normalizeRuleName(
            preferences.getString(KEY_DND_RULE_NAME, AppSettings.DEFAULT_DND_RULE_NAME),
        ),
    )

    private companion object {
        const val PREFERENCES_NAME = "classquiet_settings"
        const val KEY_DND_RULE_NAME = "dnd_rule_name"
    }
}
