package com.callcatcher.core.model

/**
 * Where a recording originated. The capture engine is deliberately agnostic to this;
 * it is carried only for storage layout, naming and UI badges.
 */
enum class CallSource {
    PHONE,
    WHATSAPP,
    UNKNOWN;

    companion object {
        fun fromName(value: String?): CallSource =
            entries.firstOrNull { it.name == value } ?: UNKNOWN
    }
}

/** Direction of the call relative to the device owner. */
enum class CallDirection {
    INCOMING,
    OUTGOING,
    UNKNOWN;

    /** Short token used in filenames, matching BCR's convention. */
    val token: String
        get() = when (this) {
            INCOMING -> "in"
            OUTGOING -> "out"
            UNKNOWN -> "unknown"
        }

    companion object {
        fun fromName(value: String?): CallDirection =
            entries.firstOrNull { it.name == value } ?: UNKNOWN
    }
}

/**
 * Everything a trigger knows about a call at the moment capture starts.
 * A trigger ([com.callcatcher.core.capture.AudioSource] caller) fills this in and hands it
 * to the engine; the engine never inspects it beyond passing it through to storage.
 */
data class CallMetadata(
    val source: CallSource,
    val direction: CallDirection = CallDirection.UNKNOWN,
    /** Raw phone number, JID or any source-specific identifier. May be null if withheld. */
    val address: String? = null,
    /** Human-readable contact name if the trigger could resolve one. */
    val contactName: String? = null,
    /** Call start, epoch millis. Defaults to now. */
    val startedAtMillis: Long = System.currentTimeMillis(),
    /** SIM slot for phone calls, when known. */
    val simSlot: Int? = null,
)
