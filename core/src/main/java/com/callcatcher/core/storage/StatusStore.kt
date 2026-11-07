package com.callcatcher.core.storage

import android.content.Context

/**
 * Small persistent diagnostics the UI shows in its status panel: the last error, the last saved
 * recording, and when the WhatsApp hook last reached the app. During testing this answers
 * "did anything happen?" without needing logcat.
 */
class StatusStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun recordError(message: String) {
        prefs.edit()
            .putString(KEY_LAST_ERROR, message)
            .putLong(KEY_LAST_ERROR_AT, System.currentTimeMillis())
            .apply()
    }

    fun clearError() {
        prefs.edit().remove(KEY_LAST_ERROR).remove(KEY_LAST_ERROR_AT).apply()
    }

    fun recordSaved(summary: String) {
        prefs.edit()
            .putString(KEY_LAST_SAVED, summary)
            .putLong(KEY_LAST_SAVED_AT, System.currentTimeMillis())
            .apply()
    }

    fun recordTrigger(fromPackage: String) {
        prefs.edit()
            .putString(KEY_LAST_TRIGGER_FROM, fromPackage)
            .putLong(KEY_LAST_TRIGGER_AT, System.currentTimeMillis())
            .apply()
    }

    fun snapshot(): Snapshot = Snapshot(
        lastError = prefs.getString(KEY_LAST_ERROR, null),
        lastErrorAt = prefs.getLong(KEY_LAST_ERROR_AT, 0),
        lastSaved = prefs.getString(KEY_LAST_SAVED, null),
        lastSavedAt = prefs.getLong(KEY_LAST_SAVED_AT, 0),
        lastTriggerFrom = prefs.getString(KEY_LAST_TRIGGER_FROM, null),
        lastTriggerAt = prefs.getLong(KEY_LAST_TRIGGER_AT, 0),
    )

    data class Snapshot(
        val lastError: String?,
        val lastErrorAt: Long,
        val lastSaved: String?,
        val lastSavedAt: Long,
        val lastTriggerFrom: String?,
        val lastTriggerAt: Long,
    )

    private companion object {
        const val NAME = "callcatcher_status"
        const val KEY_LAST_ERROR = "last_error"
        const val KEY_LAST_ERROR_AT = "last_error_at"
        const val KEY_LAST_SAVED = "last_saved"
        const val KEY_LAST_SAVED_AT = "last_saved_at"
        const val KEY_LAST_TRIGGER_FROM = "last_trigger_from"
        const val KEY_LAST_TRIGGER_AT = "last_trigger_at"
    }
}
