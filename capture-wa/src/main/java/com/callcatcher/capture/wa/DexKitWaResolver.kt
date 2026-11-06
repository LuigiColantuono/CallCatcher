package com.callcatcher.capture.wa

import android.util.Log
import java.lang.reflect.Method

/**
 * Resolves [WaSignatures] against the loaded WhatsApp APK using DexKit, the same deobfuscation
 * approach WAE's Unobfuscator uses. Kept intentionally thin and defensive: a resolution failure
 * must leave [WaSignatures.isValid] false rather than crash WhatsApp.
 *
 * The actual DexKit queries (string references like "sound_port", "fieldstats", method shapes)
 * are the part that needs re-tuning per WhatsApp version. They are the *only* thing that needs
 * re-tuning, which is the whole design goal.
 */
class DexKitWaResolver(
    private val classLoader: ClassLoader,
    private val apkPath: String,
) {

    fun resolve(): WaSignatures {
        return try {
            // DexKitBridge.create(apkPath).use { bridge -> … queries … }
            // Deliberately not hard-coding brittle names here; the resolver is wired so the
            // queries can be filled/updated in one place when tested against a real build.
            val result = runDexKitQueries()
            if (result == null) {
                Log.w(TAG, "DexKit could not resolve WhatsApp voice-call signatures")
                Invalid
            } else {
                result
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Signature resolution threw; recording disabled for this build", t)
            Invalid
        }
    }

    /**
     * Placeholder for the DexKit query block. Returns null until wired against a real build so the
     * module fails safe. Wiring these is a device-side test task, not something to guess blind.
     */
    private fun runDexKitQueries(): WaSignatures? = null

    /** A WaSignatures that reports itself unusable, so the hook simply does nothing. */
    private object Invalid : WaSignatures {
        override val voiceServiceEventClass: Class<*> get() = error("unresolved")
        override val soundPortCreatedMethod: Method get() = error("unresolved")
        override val fieldStatsReadyMethod: Method get() = error("unresolved")
        override val voipActivityClass: Class<*> get() = error("unresolved")
        override fun extractPeerJid(callObject: Any?): String? = null
        override val isValid: Boolean = false
    }

    companion object {
        private const val TAG = "DexKitWaResolver"
    }
}
