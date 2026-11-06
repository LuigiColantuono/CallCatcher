package com.callcatcher.core.capture

import com.callcatcher.core.model.CallMetadata

/**
 * The single seam between a *trigger* (phone call-state, WhatsApp hook, future Telegram/Signal…)
 * and the shared capture engine.
 *
 * A trigger decides *when* a call is live and *who* it is with; it does not know how audio is
 * captured or encoded. Adding a new recordable app therefore means implementing a new trigger
 * that drives these three callbacks — nothing in [com.callcatcher.core.capture.CaptureEngine]
 * needs to change.
 *
 * Contract:
 *  - [onStreamStart] is called exactly once when the call becomes active.
 *  - [onStreamEnd] is called exactly once when the call ends (or is cancelled). It must be safe
 *    to call even if [onStreamStart] failed, so triggers can call it unconditionally on teardown.
 */
interface AudioSource {

    /** The call went active. Returns true if capture actually started. */
    fun onStreamStart(metadata: CallMetadata): Boolean

    /** The call ended. Finalises and flushes the recording. Idempotent. */
    fun onStreamEnd()
}
