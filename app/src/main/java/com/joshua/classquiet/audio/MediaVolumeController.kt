package com.joshua.classquiet.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.core.content.ContextCompat

class MediaVolumeController(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val preferences =
        appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    private val volumeChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_VOLUME_CHANGED) noteUserOverrideIfNeeded()
        }
    }

    init {
        ContextCompat.registerReceiver(
            appContext,
            volumeChangedReceiver,
            IntentFilter(ACTION_VOLUME_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    fun startClassSession() = synchronized(lock) {
        if (preferences.getBoolean(KEY_SESSION_ACTIVE, false)) {
            noteUserOverrideIfNeededLocked()
            return@synchronized
        }

        preferences.edit()
            .putBoolean(KEY_SESSION_ACTIVE, true)
            .putBoolean(KEY_MUTED_BY_APP, false)
            .putBoolean(KEY_USER_OVERRIDE, false)
            .remove(KEY_ORIGINAL_VOLUME)
            .commit()

        if (audioManager.isVolumeFixed || audioManager.isMusicActive) return@synchronized

        val originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (originalVolume <= 0) return@synchronized

        preferences.edit()
            .putBoolean(KEY_MUTED_BY_APP, true)
            .putInt(KEY_ORIGINAL_VOLUME, originalVolume)
            .commit()
        runCatching {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        }.onFailure {
            preferences.edit()
                .putBoolean(KEY_MUTED_BY_APP, false)
                .remove(KEY_ORIGINAL_VOLUME)
                .apply()
        }
    }

    fun finishClassSession() = synchronized(lock) {
        if (!preferences.getBoolean(KEY_SESSION_ACTIVE, false)) return@synchronized

        noteUserOverrideIfNeededLocked()
        val originalVolume = preferences.getInt(KEY_ORIGINAL_VOLUME, -1)
        val shouldRestore =
            preferences.getBoolean(KEY_MUTED_BY_APP, false) &&
                !preferences.getBoolean(KEY_USER_OVERRIDE, false) &&
                audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0 &&
                originalVolume >= 0

        preferences.edit().clear().commit()
        if (shouldRestore && !audioManager.isVolumeFixed) {
            runCatching {
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    originalVolume.coerceAtMost(
                        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                    ),
                    0,
                )
            }
        }
    }

    fun noteUserOverrideIfNeeded() = synchronized(lock) {
        noteUserOverrideIfNeededLocked()
    }

    private fun noteUserOverrideIfNeededLocked() {
        val wasMutedByApp = preferences.getBoolean(KEY_MUTED_BY_APP, false)
        val sessionActive = preferences.getBoolean(KEY_SESSION_ACTIVE, false)
        if (
            sessionActive &&
            wasMutedByApp &&
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) > 0
        ) {
            preferences.edit().putBoolean(KEY_USER_OVERRIDE, true).apply()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "classquiet_media_volume"
        const val KEY_SESSION_ACTIVE = "session_active"
        const val KEY_MUTED_BY_APP = "muted_by_app"
        const val KEY_USER_OVERRIDE = "user_override"
        const val KEY_ORIGINAL_VOLUME = "original_volume"
        const val ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
    }
}
