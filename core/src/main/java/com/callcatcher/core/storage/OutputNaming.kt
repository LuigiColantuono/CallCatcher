package com.callcatcher.core.storage

import com.callcatcher.core.model.CallMetadata
import com.callcatcher.core.model.CallSource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds file and directory names from a token template. ISO date first so a plain alphabetical
 * sort is also chronological; no spaces; illegal characters stripped.
 *
 * Supported tokens: {YYYYMMDD} {HHmm} {date} {time} {direction} {contact} {source} {ext}
 * Default template: {YYYYMMDD}_{HHmm}_{direction}_{contact}
 */
class OutputNaming(
    private val template: String = DEFAULT_TEMPLATE,
) {
    private val dayFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
    private val timeFormat = SimpleDateFormat("HHmm", Locale.US)

    fun fileName(metadata: CallMetadata, extension: String): String {
        val date = Date(metadata.startedAtMillis)
        val contact = metadata.contactName?.takeIf { it.isNotBlank() }
            ?: metadata.address?.takeIf { it.isNotBlank() }
            ?: "unknown"

        val body = template
            .replace("{YYYYMMDD}", dayFormat.format(date))
            .replace("{date}", dayFormat.format(date))
            .replace("{HHmm}", timeFormat.format(date))
            .replace("{time}", timeFormat.format(date))
            .replace("{direction}", metadata.direction.token)
            .replace("{contact}", sanitize(contact))
            .replace("{source}", metadata.source.name.lowercase(Locale.US))
            .replace("{ext}", extension)

        return "${sanitize(body)}.$extension"
    }

    /** Relative directory for a source: e.g. WhatsApp/2026/11-November. */
    fun relativeDir(metadata: CallMetadata): String {
        val date = Date(metadata.startedAtMillis)
        val year = SimpleDateFormat("yyyy", Locale.US).format(date)
        val month = SimpleDateFormat("MM-MMMM", Locale.US).format(date)
        val top = when (metadata.source) {
            CallSource.PHONE -> "Phone"
            CallSource.WHATSAPP -> "WhatsApp"
            CallSource.UNKNOWN -> "Other"
        }
        return "$top/$year/$month"
    }

    private fun sanitize(raw: String): String =
        raw.replace(ILLEGAL, "_").replace(WHITESPACE, "_").trim('_')

    companion object {
        const val DEFAULT_TEMPLATE = "{YYYYMMDD}_{HHmm}_{direction}_{contact}"
        private val ILLEGAL = Regex("[/\\\\:*?\"<>|]")
        private val WHITESPACE = Regex("\\s+")
    }
}
