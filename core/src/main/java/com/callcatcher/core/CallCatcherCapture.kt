package com.callcatcher.core

import android.content.Context
import com.callcatcher.core.capture.CaptureConfig
import com.callcatcher.core.capture.CaptureEngine
import com.callcatcher.core.capture.PlaybackCapture
import com.callcatcher.core.db.CallCatcherDatabase
import com.callcatcher.core.storage.ContactResolver
import com.callcatcher.core.storage.LibraryScanner
import com.callcatcher.core.storage.RecordingStore
import com.callcatcher.core.storage.StatusStore
import java.io.File

/**
 * Process-wide wiring of engine + store + DB, so every host (InCallService, RecorderService) and
 * the UI share one configured pipeline.
 */
object CallCatcherCapture {

    @Volatile private var engine: CaptureEngine? = null

    fun engine(context: Context): CaptureEngine {
        engine?.let { return it }
        return synchronized(this) {
            engine ?: run {
                val app = context.applicationContext
                val store = RecordingStore(
                    baseDir = defaultBaseDir(app),
                    dao = CallCatcherDatabase.get(app).recordingDao(),
                    status = StatusStore(app),
                )
                val contacts = ContactResolver(app)
                CaptureEngine(
                    config = CaptureConfig(),
                    sink = store,
                    enrich = contacts::enrich,
                    playbackCapture = { sampleRate -> PlaybackCapture.open(app, sampleRate) },
                ).also { engine = it }
            }
        }
    }

    /** Prune DB rows whose files are gone. Call on app start. */
    suspend fun reconcileLibrary(context: Context): Int {
        val app = context.applicationContext
        return LibraryScanner(defaultBaseDir(app), CallCatcherDatabase.get(app).recordingDao()).reconcile()
    }

    /** /sdcard/Android/data/com.callcatcher/files/Recordings — no storage permission needed. */
    fun defaultBaseDir(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "Recordings")
}
