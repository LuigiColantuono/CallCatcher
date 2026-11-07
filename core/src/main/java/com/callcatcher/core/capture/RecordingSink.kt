package com.callcatcher.core.capture

import com.callcatcher.core.model.CallMetadata
import java.io.FileDescriptor

/**
 * Supplies the engine with a place to write and receives the finished result, keeping
 * [CaptureEngine] free of storage and DB knowledge.
 */
interface RecordingSink {

    /** An open, seekable, read-write output (MediaMuxer requires both). */
    class Target(
        val fd: FileDescriptor,
        /** Absolute path, used by the DB and the player. */
        val path: String,
        /** Releases the underlying file. Idempotent. */
        val close: () -> Unit,
    )

    /** Create the output file for [metadata]. Null aborts the capture. */
    fun open(metadata: CallMetadata): Target?

    /** The file is closed. Persist it. Called on the session thread. */
    fun onCompleted(result: CaptureResult)

    /** Capture failed. [target] (already closed) should be deleted if non-null. */
    fun onFailed(metadata: CallMetadata, target: Target?, reason: String)
}
