package com.callcatcher.core.storage

import android.util.Log
import com.callcatcher.core.db.RecordingDao
import java.io.File

/**
 * Reconciles the DB against the filesystem at startup so the library never fills with dead rows
 * when the user moves or deletes files by hand (brief). It only prunes here; re-importing orphan
 * files found on disk is intentionally left out until metadata recovery from filenames is wired up.
 */
class LibraryScanner(
    private val baseDir: File,
    private val dao: RecordingDao,
) {
    /** Remove DB rows whose files no longer exist. Returns how many were pruned. */
    suspend fun reconcile(): Int {
        val known = dao.allPaths()
        val missing = known.filter { !File(it).exists() }
        if (missing.isNotEmpty()) {
            dao.deleteByPaths(missing)
            Log.i(TAG, "Pruned ${missing.size} dead recording rows")
        }
        return missing.size
    }

    companion object {
        private const val TAG = "LibraryScanner"
    }
}
