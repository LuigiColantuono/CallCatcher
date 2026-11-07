package com.callcatcher.capture.phone

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log
import com.callcatcher.core.CallCatcherCapture
import com.callcatcher.core.CallCatcherIds
import com.callcatcher.core.model.CallDirection
import com.callcatcher.core.model.CallMetadata
import com.callcatcher.core.model.CallSource
import com.callcatcher.core.service.RecordingNotifications

/**
 * Phone trigger and foreground host, modelled on BCR's RecorderInCallService.
 *
 * Telecom binds this service for every call because the app holds CONTROL_INCALL_EXPERIENCE (as a
 * privileged system app). When a call goes ACTIVE it goes foreground (microphone type) and starts
 * the shared engine; when that call disconnects it stops it.
 *
 * Self-managed calls are included: if WhatsApp registers its calls with Telecom on this device,
 * they arrive here too and are recorded with the VoIP profile, with no hook needed.
 */
class PhoneCallRecordingService : InCallService() {

    private val engine by lazy { CallCatcherCapture.engine(applicationContext) }
    private val handler = Handler(Looper.getMainLooper())
    private val callbacks = HashMap<Call, Call.Callback>()

    private var recordingCall: Call? = null
    private var sessionToken = 0

    override fun onCallAdded(call: Call) {
        val callback = object : Call.Callback() {
            override fun onStateChanged(c: Call, state: Int) = onState(c, state)
        }
        callbacks[call] = callback
        call.registerCallback(callback)
        onState(call, stateOf(call))
    }

    override fun onCallRemoved(call: Call) {
        callbacks.remove(call)?.let { call.unregisterCallback(it) }
        if (call === recordingCall) stopRecording()
    }

    private fun onState(call: Call, state: Int) {
        when (state) {
            Call.STATE_ACTIVE -> if (recordingCall == null) startRecording(call)
            Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED -> if (call === recordingCall) stopRecording()
        }
    }

    private fun startRecording(call: Call) {
        val metadata = metadataFor(call) ?: return
        RecordingNotifications.startForeground(this, metadata)
        recordingCall = call

        val token = ++sessionToken
        val started = engine.start(metadata) {
            handler.post {
                // Only leave the foreground if no newer session has started meanwhile.
                if (token == sessionToken && recordingCall == null) RecordingNotifications.stopForeground(this)
            }
        }
        if (!started) {
            Log.i(TAG, "Engine busy (another host is recording); not recording this call")
            recordingCall = null
            RecordingNotifications.stopForeground(this)
        } else {
            Log.i(TAG, "Recording ${metadata.source} ${metadata.direction}")
        }
    }

    private fun stopRecording() {
        recordingCall = null
        engine.onStreamEnd() // foreground is dropped by the session's onFinished
    }

    /** Null for self-managed calls from apps we don't record. */
    private fun metadataFor(call: Call): CallMetadata? {
        val details = call.details
        val selfManaged = details.hasProperty(Call.Details.PROPERTY_SELF_MANAGED)
        val accountPackage = details.accountHandle?.componentName?.packageName

        val source = when {
            accountPackage in CallCatcherIds.WHATSAPP_PACKAGES -> CallSource.WHATSAPP
            selfManaged -> {
                Log.i(TAG, "Ignoring self-managed call from $accountPackage")
                return null
            }
            else -> CallSource.PHONE
        }

        val name = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> details.contactDisplayName
            else -> null
        } ?: details.callerDisplayName

        return CallMetadata(
            source = source,
            direction = when (details.callDirection) {
                Call.Details.DIRECTION_INCOMING -> CallDirection.INCOMING
                Call.Details.DIRECTION_OUTGOING -> CallDirection.OUTGOING
                else -> CallDirection.UNKNOWN
            },
            address = details.handle?.schemeSpecificPart,
            contactName = name?.takeIf { it.isNotBlank() },
        )
    }

    @Suppress("DEPRECATION")
    private fun stateOf(call: Call): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) call.details.state else call.state

    companion object {
        private const val TAG = "PhoneTrigger"
    }
}
