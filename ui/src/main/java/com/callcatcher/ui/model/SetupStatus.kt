package com.callcatcher.ui.model

/**
 * What the status card shows. The privileged checks come from installing the KernelSU/Magisk
 * module; the rest are ordinary runtime permissions or diagnostics.
 */
data class SetupStatus(
    /** False until the first real refresh, so the UI doesn't react to the all-false default. */
    val loaded: Boolean = false,
    val isSystemApp: Boolean = false,
    val canCaptureCallAudio: Boolean = false,
    val canDetectCalls: Boolean = false,
    val canCapturePlayback: Boolean = false,
    val hasMicrophone: Boolean = false,
    val hasContacts: Boolean = false,
    val hasNotifications: Boolean = false,
    val freeBytes: Long = 0,
    val lastError: String? = null,
    val lastErrorAt: Long = 0,
    val lastSaved: String? = null,
    val lastSavedAt: Long = 0,
    val lastTriggerAt: Long = 0,
) {
    /** Enough to record calls with both sides. */
    val ready: Boolean
        get() = isSystemApp && canCaptureCallAudio && canDetectCalls && canCapturePlayback && hasMicrophone
}

/** Library totals for the top bar. */
data class LibraryStats(val count: Int = 0, val totalDurationMillis: Long = 0)
