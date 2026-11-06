package com.callcatcher.capture.phone

import android.telecom.Call
import android.telecom.InCallService
import android.util.Log
import com.callcatcher.core.CallCatcherCapture
import com.callcatcher.core.capture.AudioSource
import com.callcatcher.core.model.CallDirection
import com.callcatcher.core.model.CallMetadata
import com.callcatcher.core.model.CallSource

/**
 * Phone trigger. Watches Telecom call state and drives the shared capture engine:
 * active → [AudioSource.onStreamStart], disconnected → [AudioSource.onStreamEnd].
 *
 * It owns none of the recording logic — it only decides *when* and supplies *who/which direction*.
 * That is the whole point of the AudioSource seam: WhatsApp's trigger looks completely different
 * but speaks to the same engine.
 */
class PhoneCallRecordingService : InCallService() {

    private val engine: AudioSource by lazy { CallCatcherCapture.engine(applicationContext) }
    private val tracked = HashMap<Call, Call.Callback>()

    override fun onCallAdded(call: Call) {
        val callback = object : Call.Callback() {
            override fun onStateChanged(c: Call, state: Int) = handleState(c, state)
        }
        tracked[call] = callback
        call.registerCallback(callback)
        handleState(call, call.details.state)
    }

    override fun onCallRemoved(call: Call) {
        tracked.remove(call)?.let { call.unregisterCallback(it) }
        engine.onStreamEnd()
    }

    private fun handleState(call: Call, state: Int) {
        when (state) {
            Call.STATE_ACTIVE -> {
                val metadata = CallMetadata(
                    source = CallSource.PHONE,
                    direction = directionOf(call),
                    address = addressOf(call),
                    startedAtMillis = System.currentTimeMillis(),
                )
                Log.i(TAG, "Call active: ${metadata.direction} ${metadata.address?.take(3)}…")
                engine.onStreamStart(metadata)
            }
            Call.STATE_DISCONNECTED -> engine.onStreamEnd()
        }
    }

    private fun directionOf(call: Call): CallDirection = when (call.details.callDirection) {
        Call.Details.DIRECTION_INCOMING -> CallDirection.INCOMING
        Call.Details.DIRECTION_OUTGOING -> CallDirection.OUTGOING
        else -> CallDirection.UNKNOWN
    }

    private fun addressOf(call: Call): String? =
        call.details.handle?.schemeSpecificPart

    companion object {
        private const val TAG = "PhoneTrigger"
    }
}
