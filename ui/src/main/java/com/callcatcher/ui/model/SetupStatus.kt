package com.callcatcher.ui.model

/**
 * What the status panel shows. The first three come from installing the KernelSU/Magisk module;
 * the rest are ordinary runtime permissions or diagnostics.
 */
data class SetupStatus(
    val isSystemApp: Boolean = false,
    val canCaptureCallAudio: Boolean = false,
    val canDetectCalls: Boolean = false,
    val hasMicrophone: Boolean = false,
    val hasContacts: Boolean = false,
    val hasNotifications: Boolean = false,
    val lastError: String? = null,
    val lastErrorAt: Long = 0,
    val lastSaved: String? = null,
    val lastSavedAt: Long = 0,
    val lastTriggerAt: Long = 0,
) {
    /** Enough to record phone calls with both sides. */
    val ready: Boolean get() = isSystemApp && canCaptureCallAudio && canDetectCalls && hasMicrophone
}
