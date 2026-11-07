package com.callcatcher.core.model

import android.os.Bundle

/**
 * Where a recording originated. The capture engine only uses this to pick a capture profile;
 * storage uses it for folders and the UI for badges.
 */
enum class CallSource {
    PHONE,
    WHATSAPP,

    /** Manual test recording from the app (microphone only). */
    TEST,
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

    /** Short token used in filenames. */
    val token: String
        get() = when (this) {
            INCOMING -> "in"
            OUTGOING -> "out"
            UNKNOWN -> "call"
        }

    companion object {
        fun fromName(value: String?): CallDirection =
            entries.firstOrNull { it.name == value } ?: UNKNOWN
    }
}

/**
 * Everything a trigger knows about a call when capture starts. Triggers fill this in; the engine
 * passes it through to storage untouched (apart from contact-name enrichment).
 */
data class CallMetadata(
    val source: CallSource,
    val direction: CallDirection = CallDirection.UNKNOWN,
    /** Phone number, WhatsApp JID or other identifier. Null if withheld. */
    val address: String? = null,
    /** Human-readable contact name, if known. */
    val contactName: String? = null,
    /** Call start, epoch millis. */
    val startedAtMillis: Long = System.currentTimeMillis(),
    val simSlot: Int? = null,
) {
    /** Serialises for IPC (WhatsApp hook → app) and service intents. */
    fun toBundle(): Bundle = Bundle().apply {
        putString(KEY_SOURCE, source.name)
        putString(KEY_DIRECTION, direction.name)
        putString(KEY_ADDRESS, address)
        putString(KEY_CONTACT, contactName)
        putLong(KEY_STARTED_AT, startedAtMillis)
        simSlot?.let { putInt(KEY_SIM_SLOT, it) }
    }

    companion object {
        private const val KEY_SOURCE = "source"
        private const val KEY_DIRECTION = "direction"
        private const val KEY_ADDRESS = "address"
        private const val KEY_CONTACT = "contactName"
        private const val KEY_STARTED_AT = "startedAt"
        private const val KEY_SIM_SLOT = "simSlot"

        fun fromBundle(bundle: Bundle?): CallMetadata {
            if (bundle == null) return CallMetadata(source = CallSource.UNKNOWN)
            return CallMetadata(
                source = CallSource.fromName(bundle.getString(KEY_SOURCE)),
                direction = CallDirection.fromName(bundle.getString(KEY_DIRECTION)),
                address = bundle.getString(KEY_ADDRESS),
                contactName = bundle.getString(KEY_CONTACT),
                startedAtMillis = bundle.getLong(KEY_STARTED_AT, System.currentTimeMillis()),
                simSlot = if (bundle.containsKey(KEY_SIM_SLOT)) bundle.getInt(KEY_SIM_SLOT) else null,
            )
        }
    }
}
