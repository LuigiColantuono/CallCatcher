package com.callcatcher.core

import android.content.Context
import com.callcatcher.core.capture.AudioSource
import com.callcatcher.core.capture.CaptureConfig
import com.callcatcher.core.capture.CaptureEngine
import com.callcatcher.core.db.CallCatcherDatabase
import com.callcatcher.core.storage.LibraryScanner
import com.callcatcher.core.storage.OutputNaming
import com.callcatcher.core.storage.RecordingStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import java.io.File

/**
 * One-call wiring of engine + store + DB, so every trigger ([capture-phone], [capture-wa], …)
 * shares the same configured pipeline instead of re-assembling it. A trigger obtains the
 * [AudioSource] and calls onStreamStart/onStreamEnd — nothing else.
 */
object CallCatcherCapture {

    private val scope = CoroutineScope(SupervisorJob())

    @Volatile private var engine: CaptureEngine? = null

    /** Lazily build the shared engine. [baseDir] defaults to the app's external files dir. */
    fun engine(
        context: Context,
        baseDir: File = defaultBaseDir(context),
        config: CaptureConfig = CaptureConfig(),
        namingTemplate: String = OutputNaming.DEFAULT_TEMPLATE,
    ): AudioSource {
        return engine ?: synchronized(this) {
            engine ?: run {
                val dao = CallCatcherDatabase.get(context).recordingDao()
                val store = RecordingStore(
                    baseDir = baseDir,
                    dao = dao,
                    scope = scope,
                    naming = OutputNaming(namingTemplate),
                )
                CaptureEngine(config = config, sink = store).also { engine = it }
            }
        }
    }

    /** Prune dead DB rows. Call once on app/service startup. */
    suspend fun reconcileLibrary(context: Context, baseDir: File = defaultBaseDir(context)): Int {
        val dao = CallCatcherDatabase.get(context).recordingDao()
        return LibraryScanner(baseDir, dao).reconcile()
    }

    fun defaultBaseDir(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "Recordings")
}
