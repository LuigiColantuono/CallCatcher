package com.callcatcher.core.service

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.util.Log
import com.callcatcher.core.CallCatcherCapture
import com.callcatcher.core.CallCatcherIds
import com.callcatcher.core.model.CallMetadata
import com.callcatcher.core.model.CallSource
import com.callcatcher.core.storage.StatusStore

/**
 * IPC entry point for the WhatsApp hook, which runs inside WhatsApp's process and cannot record
 * call audio itself (WhatsApp lacks CAPTURE_AUDIO_OUTPUT). The hook only says "started"/"ended";
 * the recording happens here, in the privileged app process.
 *
 * `call()` exposes no data and accepts only WhatsApp (and the app itself) as callers.
 */
class TriggerProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val context = context ?: return result("no context")
        val caller = callingPackage
        if (caller == null || (caller !in CallCatcherIds.WHATSAPP_PACKAGES && caller != context.packageName)) {
            Log.w(TAG, "Rejected trigger '$method' from $caller")
            return result("denied")
        }

        val status = StatusStore(context)
        return when (method) {
            CallCatcherIds.METHOD_START -> {
                status.recordTrigger(caller)
                val metadata = CallMetadata.fromBundle(extras).copy(source = CallSource.WHATSAPP)
                val identity = Binder.clearCallingIdentity()
                try {
                    context.startForegroundService(RecorderService.startIntent(context, metadata))
                    result("started")
                } catch (t: Throwable) {
                    Log.e(TAG, "Could not start RecorderService", t)
                    status.recordError("WhatsApp trigger received, but the recorder could not start: ${t.javaClass.simpleName}: ${t.message}")
                    result("error: ${t.javaClass.simpleName}")
                } finally {
                    Binder.restoreCallingIdentity(identity)
                }
            }
            CallCatcherIds.METHOD_STOP -> {
                CallCatcherCapture.engine(context).onStreamEnd()
                result("stopped")
            }
            else -> result("unknown method")
        }
    }

    private fun result(value: String) = Bundle().apply { putString(CallCatcherIds.RESULT_KEY, value) }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private companion object {
        const val TAG = "TriggerProvider"
    }
}
