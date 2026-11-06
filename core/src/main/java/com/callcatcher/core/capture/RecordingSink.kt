package com.callcatcher.core.capture

import com.callcatcher.core.model.CallMetadata
import java.io.FileDescriptor

/**
 * Supplies the engine with a place to write, and receives the finished result.
 * Keeps [CaptureEngine] free of storage/DB knowledge so the same engine serves every trigger.
 */
interface RecordingSink {

    /** An open output destination for one recording. */
    class Target(
        val fd: FileDescriptor,
        /** Logical path for the DB/UI (the engine does not resolve paths itself). */
        val path: String,
        /** Called by the engine to release the underlying stream after muxing completes. */
        val close: () -> Unit,
    )

    /** Create and open the output file for [metadata]. Return null to abort capture. */
    fun open(metadata: CallMetadata): Target?

    /** The recording finished and the file is closed. Persist it. */
    fun onCompleted(result: CaptureResult)

    /** Capture failed or produced nothing usable. [target] may be null if opening failed. */
    fun onFailed(metadata: CallMetadata, target: Target?, reason: String)
}
