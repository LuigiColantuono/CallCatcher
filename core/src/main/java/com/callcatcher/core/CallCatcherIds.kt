package com.callcatcher.core

/**
 * Identifiers shared between the app process and the hooks that run inside other processes
 * (WhatsApp, system_server). Hooks cannot read the app's BuildConfig, so these are fixed here and
 * must match `applicationId` in :ui.
 */
object CallCatcherIds {
    const val PACKAGE = "com.callcatcher"

    /** Authority of [com.callcatcher.core.service.TriggerProvider]; mirrors `${applicationId}.trigger`. */
    const val TRIGGER_AUTHORITY = "$PACKAGE.trigger"

    const val METHOD_START = "start"
    const val METHOD_STOP = "stop"
    const val RESULT_KEY = "result"

    val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
}
