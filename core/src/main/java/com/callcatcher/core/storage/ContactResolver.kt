package com.callcatcher.core.storage

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract.PhoneLookup
import android.util.Log
import com.callcatcher.core.model.CallMetadata

/**
 * Resolves a phone number to a contact name for file naming and the list. Works for both sources:
 * WhatsApp JIDs on s.whatsapp.net are converted to +E.164 numbers by the hook before they get here.
 * Silently does nothing without READ_CONTACTS.
 */
class ContactResolver(context: Context) {

    private val appContext = context.applicationContext

    fun enrich(metadata: CallMetadata): CallMetadata {
        if (!metadata.contactName.isNullOrBlank()) return metadata
        val number = metadata.address?.takeIf { it.isNotBlank() && it.any(Char::isDigit) } ?: return metadata
        if (number.contains('@')) return metadata // e.g. a WhatsApp @lid identifier, not a number
        return nameFor(number)?.let { metadata.copy(contactName = it) } ?: metadata
    }

    private fun nameFor(number: String): String? {
        if (appContext.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        return try {
            val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            appContext.contentResolver.query(uri, arrayOf(PhoneLookup.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Contact lookup failed", t)
            null
        }
    }

    private companion object {
        const val TAG = "ContactResolver"
    }
}
