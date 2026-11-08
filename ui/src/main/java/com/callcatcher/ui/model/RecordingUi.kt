package com.callcatcher.ui.model

import com.callcatcher.core.db.RecordingEntity
import com.callcatcher.core.model.CallDirection
import com.callcatcher.core.model.CallSource

/** View-ready recording. Waveform parsed once here, not in the row composable. */
data class RecordingUi(
    val id: Long,
    val path: String,
    val source: CallSource,
    val direction: CallDirection,
    val title: String,
    val address: String?,
    val startedAtMillis: Long,
    val durationMillis: Long,
    val sizeBytes: Long,
    val sampleRateHz: Int,
    /** Which system audio source won the cascade, e.g. VOICE_CALL. Shown while testing. */
    val captureSource: String,
    val waveform: FloatArray,
    val silencePercent: Int,
    val lowQuality: Boolean,
) {
    override fun equals(other: Any?) = other is RecordingUi && other.id == id
    override fun hashCode() = id.hashCode()
}

fun RecordingEntity.toUi(): RecordingUi = RecordingUi(
    id = id,
    path = path,
    source = CallSource.fromName(source),
    direction = CallDirection.fromName(direction),
    title = contactName?.takeIf { it.isNotBlank() }
        // A WhatsApp LID ("…@lid") is an opaque id, not something to show.
        ?: address?.takeIf { it.isNotBlank() && !it.contains('@') }
        ?: if (address?.contains('@') == true) "WhatsApp contact" else "Unknown",
    address = address,
    startedAtMillis = startedAtMillis,
    durationMillis = durationMillis,
    sizeBytes = sizeBytes,
    sampleRateHz = sampleRateHz,
    captureSource = captureSource,
    waveform = waveform.split(",").mapNotNull { it.toFloatOrNull() }.toFloatArray(),
    silencePercent = (silenceRatio * 100).toInt(),
    // Mostly-silent files are the WAE failure mode; flag them so the user isn't surprised.
    lowQuality = silenceRatio > 0.85f,
)

/** The single list's filter: one screen, not one per source. */
enum class SourceFilter(val label: String) {
    ALL("All"),
    PHONE("Phone"),
    WHATSAPP("WhatsApp");

    fun matches(source: CallSource): Boolean = when (this) {
        ALL -> true
        PHONE -> source == CallSource.PHONE
        WHATSAPP -> source == CallSource.WHATSAPP
    }
}
