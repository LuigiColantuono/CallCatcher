package com.callcatcher.core.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.callcatcher.core.CallCatcherCapture
import com.callcatcher.core.model.CallMetadata
import com.callcatcher.core.model.CallSource

/**
 * Foreground host for recordings that don't come from Telecom: WhatsApp calls signalled by the
 * hook (via [TriggerProvider]) and manual test recordings from the UI. Phone calls are hosted by
 * the InCallService itself, which Telecom keeps bound for the duration of the call.
 */
class RecorderService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val engine by lazy { CallCatcherCapture.engine(applicationContext) }

    /** True while the running session is one this service started (and so must clean up). */
    private var ownsSession = false
    private var sessionToken = 0

    private var autoStop: Runnable? = null
    private var watchdog: Runnable? = null
    private var idleChecks = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START) {
            finishIfIdle()
            return START_NOT_STICKY
        }

        val metadata = CallMetadata.fromBundle(intent.getBundleExtra(EXTRA_METADATA))
        // Required within seconds of startForegroundService(), even if we end up not recording.
        RecordingNotifications.startForeground(this, metadata)

        if (ownsSession && engine.isRecording) {
            Log.i(TAG, "Already recording; duplicate start ignored")
            return START_NOT_STICKY
        }

        val token = ++sessionToken
        val started = engine.start(metadata) {
            handler.post { if (token == sessionToken) onSessionFinished() }
        }
        if (!started) {
            // Another host (the InCallService) owns the current session.
            ownsSession = false
            finishIfIdle()
            return START_NOT_STICKY
        }
        ownsSession = true

        val autoStopMs = intent.getLongExtra(EXTRA_AUTO_STOP_MS, 0)
        if (autoStopMs > 0) {
            autoStop = Runnable { engine.onStreamEnd() }.also { handler.postDelayed(it, autoStopMs) }
        }
        if (metadata.source == CallSource.WHATSAPP) startModeWatchdog()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        clearTimers()
        if (ownsSession) engine.onStreamEnd()
        super.onDestroy()
    }

    private fun onSessionFinished() {
        ownsSession = false
        clearTimers()
        finishIfIdle()
    }

    private fun finishIfIdle() {
        if (ownsSession) return
        RecordingNotifications.stopForeground(this)
        stopSelf()
    }

    /**
     * Backstop for WhatsApp: if the hook's end-of-call signal never arrives, stop once the audio
     * mode has left communication/call mode for two consecutive checks.
     */
    private fun startModeWatchdog() {
        val audioManager = getSystemService(AudioManager::class.java)
        idleChecks = 0
        val check = object : Runnable {
            override fun run() {
                val mode = audioManager.mode
                val inCall = mode == AudioManager.MODE_IN_COMMUNICATION || mode == AudioManager.MODE_IN_CALL
                idleChecks = if (inCall) 0 else idleChecks + 1
                if (idleChecks >= 2) {
                    Log.i(TAG, "Audio mode left call mode; stopping WhatsApp recording")
                    engine.onStreamEnd()
                } else {
                    handler.postDelayed(this, WATCHDOG_INTERVAL_MS)
                }
            }
        }
        watchdog = check
        handler.postDelayed(check, WATCHDOG_GRACE_MS)
    }

    private fun clearTimers() {
        autoStop?.let { handler.removeCallbacks(it) }
        watchdog?.let { handler.removeCallbacks(it) }
        autoStop = null
        watchdog = null
    }

    companion object {
        private const val TAG = "RecorderService"
        private const val ACTION_START = "com.callcatcher.action.START_RECORDING"
        private const val EXTRA_METADATA = "metadata"
        private const val EXTRA_AUTO_STOP_MS = "autoStopMs"
        private const val WATCHDOG_GRACE_MS = 10_000L
        private const val WATCHDOG_INTERVAL_MS = 2_000L

        fun startIntent(context: Context, metadata: CallMetadata, autoStopMillis: Long = 0): Intent =
            Intent(context, RecorderService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_METADATA, metadata.toBundle())
                .putExtra(EXTRA_AUTO_STOP_MS, autoStopMillis)
    }
}
