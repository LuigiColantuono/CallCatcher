package com.callcatcher.core.storage

import android.util.Log
import com.callcatcher.core.capture.CaptureResult
import com.callcatcher.core.capture.RecordingSink
import com.callcatcher.core.db.RecordingDao
import com.callcatcher.core.db.RecordingEntity
import com.callcatcher.core.model.CallMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

/**
 * Default [RecordingSink]: writes OGG/Opus files into source-partitioned folders under [baseDir]
 * and records each one in Room. Physical separation by source, logical union in the DB (brief).
 *
 * [baseDir] is the app's own storage by default (no root needed to write). Pointing it at a
 * user-visible folder is where the privileged bridge would come in; the engine is unaffected.
 */
class RecordingStore(
    private val baseDir: File,
    private val dao: RecordingDao,
    private val scope: CoroutineScope,
    private val naming: OutputNaming = OutputNaming(),
    private val extension: String = "oga",
) : RecordingSink {

    private val openStreams = HashMap<String, FileOutputStream>()

    override fun open(metadata: CallMetadata): RecordingSink.Target? {
        return try {
            val dir = File(baseDir, naming.relativeDir(metadata)).apply { mkdirs() }
            val file = File(dir, naming.fileName(metadata, extension))
            val stream = FileOutputStream(file)
            openStreams[file.absolutePath] = stream
            RecordingSink.Target(
                fd = stream.fd,
                path = file.absolutePath,
                close = { closeStream(file.absolutePath) },
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to open output", t)
            null
        }
    }

    override fun onCompleted(result: CaptureResult) {
        val file = File(result.outputPath)
        val size = file.length()

        if (size <= EMPTY_FILE_BYTES) {
            Log.w(TAG, "Discarding empty recording: ${result.outputPath}")
            file.delete()
            return
        }

        scope.launch(Dispatchers.IO) {
            dao.upsert(
                RecordingEntity(
                    path = result.outputPath,
                    source = result.metadata.source.name,
                    direction = result.metadata.direction.name,
                    address = result.metadata.address,
                    contactName = result.metadata.contactName,
                    startedAtMillis = result.metadata.startedAtMillis,
                    durationMillis = result.durationMillis,
                    sizeBytes = size,
                    sampleRateHz = result.sampleRateHz,
                    captureSource = result.source.name,
                    waveform = result.waveform.joinToString(",") { "%.3f".format(it) },
                    silenceRatio = result.silenceRatio,
                )
            )
            Log.i(TAG, "Saved ${result.outputPath} (${size} bytes, ${result.durationMillis}ms, " +
                "source=${result.source}, silence=${"%.0f".format(result.silenceRatio * 100)}%)")
        }
    }

    override fun onFailed(metadata: CallMetadata, target: RecordingSink.Target?, reason: String) {
        Log.e(TAG, "Capture failed: $reason")
        target?.let { File(it.path).delete() }
    }

    private fun closeStream(path: String) {
        openStreams.remove(path)?.let { runCatching { it.flush(); it.close() } }
    }

    companion object {
        private const val TAG = "RecordingStore"
        private const val EMPTY_FILE_BYTES = 1024L
    }
}
