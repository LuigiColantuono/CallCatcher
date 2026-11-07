package com.callcatcher.core.storage

import android.util.Log
import com.callcatcher.core.capture.CaptureResult
import com.callcatcher.core.capture.RecordingSink
import com.callcatcher.core.db.RecordingDao
import com.callcatcher.core.db.RecordingEntity
import com.callcatcher.core.model.CallMetadata
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.RandomAccessFile
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Default [RecordingSink]: Ogg/Opus files in source-partitioned folders under [baseDir], one Room
 * row per file. Physical separation by source, logical union in the DB.
 *
 * DB writes are synchronous on the session thread on purpose: the hosting service leaves the
 * foreground right after, and the process may then be reclaimed before an async insert lands.
 */
class RecordingStore(
    private val baseDir: File,
    private val dao: RecordingDao,
    private val status: StatusStore,
    private val naming: OutputNaming = OutputNaming(),
    private val extension: String = "oga",
) : RecordingSink {

    private val openFiles = ConcurrentHashMap<String, RandomAccessFile>()

    override fun open(metadata: CallMetadata): RecordingSink.Target? {
        return try {
            val dir = File(baseDir, naming.relativeDir(metadata))
            if (!dir.isDirectory && !dir.mkdirs()) error("Cannot create $dir")
            val file = uniqueFile(dir, naming.fileName(metadata, extension))
            // MediaMuxer needs a seekable, read-write descriptor.
            val raf = RandomAccessFile(file, "rw").apply { setLength(0) }
            openFiles[file.absolutePath] = raf
            RecordingSink.Target(
                fd = raf.fd,
                path = file.absolutePath,
                close = { openFiles.remove(file.absolutePath)?.let { runCatching { it.close() } } },
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to open output", t)
            null
        }
    }

    override fun onCompleted(result: CaptureResult) {
        val file = File(result.outputPath)
        val size = file.length()

        if (size <= EMPTY_FILE_BYTES || result.durationMillis < MIN_DURATION_MS) {
            Log.w(TAG, "Discarding empty recording (${size} B, ${result.durationMillis} ms)")
            file.delete()
            status.recordError("Recording discarded: too short (${result.durationMillis} ms)")
            return
        }

        runBlocking {
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
                    captureSource = result.sourceLabel,
                    // Locale.US: a comma decimal separator would break the comma-joined list.
                    waveform = result.waveform.joinToString(",") { String.format(Locale.US, "%.3f", it) },
                    silenceRatio = result.silenceRatio,
                )
            )
        }

        val silencePct = (result.silenceRatio * 100).toInt()
        val summary = "${result.metadata.source.name} via ${result.sourceLabel} @ ${result.sampleRateHz / 1000} kHz, " +
            "${result.durationMillis / 1000} s, silence $silencePct%" +
            if (result.notes.isEmpty()) "" else " | skipped: ${result.notes.joinToString("; ")}"
        status.recordSaved(summary)
        Log.i(TAG, "Saved ${result.outputPath}: $summary")
    }

    override fun onFailed(metadata: CallMetadata, target: RecordingSink.Target?, reason: String) {
        Log.e(TAG, "Capture failed: $reason")
        target?.let {
            it.close()
            File(it.path).delete()
        }
        status.recordError(reason)
    }

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val stem = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "")
        var n = 1
        while (candidate.exists()) {
            candidate = File(dir, "${stem}_$n.$ext")
            n++
        }
        return candidate
    }

    companion object {
        private const val TAG = "RecordingStore"
        private const val EMPTY_FILE_BYTES = 1024L
        private const val MIN_DURATION_MS = 1_000L
    }
}
