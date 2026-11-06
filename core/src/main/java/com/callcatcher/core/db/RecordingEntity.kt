package com.callcatcher.core.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per recording. [path] is the source of truth on disk; the rescan on startup
 * reconciles this table against the filesystem so moved/renamed/deleted files don't leave
 * dead rows (brief).
 */
@Entity(
    tableName = "recordings",
    indices = [
        Index(value = ["path"], unique = true),
        Index(value = ["source"]),
        Index(value = ["startedAtMillis"]),
        Index(value = ["address"]),
    ],
)
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val path: String,
    val source: String,
    val direction: String,
    val address: String?,
    val contactName: String?,
    val startedAtMillis: Long,
    val durationMillis: Long,
    val sizeBytes: Long,
    val sampleRateHz: Int,
    val captureSource: String,
    /** Comma-separated peak envelope, 0..1, for the waveform. Compact enough to inline. */
    val waveform: String,
    /** 0..1; high values flag a probably-bad (silent) recording for the UI to warn about. */
    val silenceRatio: Float,
)
