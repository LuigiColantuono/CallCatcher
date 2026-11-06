package com.callcatcher.capture.wa

import java.lang.reflect.Method

/**
 * THE FRAGILE SEAM.
 *
 * WhatsApp ships stripped/obfuscated: class and method names change every update. Everything that
 * depends on those names lives behind this interface and nowhere else. When an update breaks
 * recording, only the resolver that produces a [WaSignatures] needs fixing — the hook logic, the
 * engine, storage and UI are untouched.
 *
 * The anchors we need (same ones WAE relies on):
 *  - the voice-service event callback whose "sound port created" event means the call connected,
 *    and whose "fieldstats ready" event means it ended;
 *  - the VoIP activity, as a backstop end signal via onDestroy.
 */
interface WaSignatures {

    /** Obfuscated class that declares the voice-service event callback. */
    val voiceServiceEventClass: Class<*>

    /** Method invoked when the audio sound port is created (call is connected). */
    val soundPortCreatedMethod: Method

    /** Method invoked when field stats are ready (call has ended). */
    val fieldStatsReadyMethod: Method

    /** The VoIP activity class, for an onDestroy backstop. */
    val voipActivityClass: Class<*>

    /** Best-effort extraction of the remote party JID from a live call object. */
    fun extractPeerJid(callObject: Any?): String?

    /** True if these signatures were resolved against the currently loaded WhatsApp build. */
    val isValid: Boolean
}
