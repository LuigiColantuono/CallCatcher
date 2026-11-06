package com.callcatcher.core.root

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Minimal privileged-shell helper. Everything that needs root funnels through here so there is
 * exactly one place that spawns `su` and one place to audit.
 *
 * The two grants the engine depends on (brief): CAPTURE_AUDIO_OUTPUT, which is a signature/
 * privileged permission the installer can't hold, and the RECORD_AUDIO appop, which some ROMs
 * gate separately from the runtime permission.
 */
object RootShell {

    private const val TAG = "RootShell"

    data class Result(val exitCode: Int, val stdout: String, val stderr: String) {
        val ok get() = exitCode == 0
    }

    /** True if `su` is present and grants a root uid. Cheap enough to call at startup. */
    fun isAvailable(): Boolean = run("id -u").let { it.ok && it.stdout.trim() == "0" }

    /** Run one shell line as root. Blocking; call off the main thread. */
    fun run(command: String): Result {
        return try {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(false)
                .start()
            val out = process.inputStream.readTextAndClose()
            val err = process.errorStream.readTextAndClose()
            val code = process.waitFor()
            Result(code, out, err)
        } catch (t: Throwable) {
            Log.w(TAG, "root command failed: $command", t)
            Result(-1, "", t.message ?: "exception")
        }
    }

    /**
     * Grant the privileged permission + appop the capture engine needs. Safe to call repeatedly;
     * Android treats re-grants as no-ops.
     */
    fun grantCapturePermissions(packageName: String): Boolean {
        val grant = run("pm grant $packageName android.permission.CAPTURE_AUDIO_OUTPUT")
        val appop = run("appops set $packageName RECORD_AUDIO allow")
        if (!grant.ok) Log.w(TAG, "pm grant failed: ${grant.stderr}")
        if (!appop.ok) Log.w(TAG, "appops set failed: ${appop.stderr}")
        return grant.ok && appop.ok
    }

    private fun java.io.InputStream.readTextAndClose(): String =
        BufferedReader(InputStreamReader(this)).use { it.readText() }
}
