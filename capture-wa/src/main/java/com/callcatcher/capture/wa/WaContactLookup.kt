package com.callcatcher.capture.wa

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import de.robv.android.xposed.XposedBridge

/** Who is on the other end of a WhatsApp call, as far as WhatsApp itself knows. */
internal data class WaPeer(
    /** "+<digits>" when the phone number is known, else the raw JID (e.g. "…@lid"). */
    val address: String,
    /** Address-book name as WhatsApp synced it, else the WhatsApp profile name; null if unknown. */
    val name: String?,
)

/**
 * Resolves a call peer JID to a number and a name using WhatsApp's own databases. This runs inside
 * WhatsApp (hook), so its private files are readable; they are opened read-only and never written.
 *
 * Newer WhatsApp versions identify call peers by LID ("26701862027329@lid"), an opaque id that is
 * not a phone number. WhatsApp keeps the LID → phone mapping in msgstore.db:
 *   jid(_id, user, server, …) and jid_map(lid_row_id → jid_row_id).
 * Names are in wa.db: wa_contacts(jid, display_name = address-book name, wa_name = profile name).
 *
 * The schema is not public: every step is optional and falls back to what is already known.
 */
internal object WaContactLookup {

    private const val PHONE_SERVER = "s.whatsapp.net"
    private const val LID_SERVER = "lid"

    fun resolve(context: Context, rawJid: String): WaPeer {
        val user = rawJid.substringBefore('@').substringBefore(':').substringBefore('.')
        val server = rawJid.substringAfter('@', "")

        val phoneUser = when (server) {
            PHONE_SERVER -> user
            LID_SERVER -> runCatching { phoneUserForLid(context, user) }
                .onFailure { XposedBridge.log("CallCatcher: LID lookup failed: $it") }
                .getOrNull()
            else -> null
        }?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }

        val candidates = listOfNotNull(
            phoneUser?.let { "$it@$PHONE_SERVER" },
            "$user@$server".takeIf { server.isNotEmpty() },
        ).distinct()
        val name = runCatching { nameFor(context, candidates) }
            .onFailure { XposedBridge.log("CallCatcher: contact name lookup failed: $it") }
            .getOrNull()

        XposedBridge.log(
            "CallCatcher: peer server=$server, number ${if (phoneUser != null) "found" else "unknown"}, " +
                "name ${if (name != null) "found" else "unknown"}"
        )
        return WaPeer(address = phoneUser?.let { "+$it" } ?: rawJid, name = name)
    }

    private fun phoneUserForLid(context: Context, lidUser: String): String? =
        openReadOnly(context, "msgstore.db")?.use { db ->
            db.rawQuery(
                """
                SELECT p.user FROM jid l
                JOIN jid_map m ON m.lid_row_id = l._id
                JOIN jid p ON p._id = m.jid_row_id
                WHERE l.user = ? AND l.server = ? AND p.server = ?
                LIMIT 1
                """.trimIndent(),
                arrayOf(lidUser, LID_SERVER, PHONE_SERVER),
            ).use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }

    private fun nameFor(context: Context, jids: List<String>): String? {
        if (jids.isEmpty()) return null
        return openReadOnly(context, "wa.db")?.use { db ->
            val placeholders = jids.joinToString(",") { "?" }
            db.rawQuery("SELECT * FROM wa_contacts WHERE jid IN ($placeholders)", jids.toTypedArray()).use { c ->
                fun text(column: String): String? {
                    val i = c.getColumnIndex(column)
                    return if (i >= 0) c.getString(i)?.trim()?.takeIf { it.isNotEmpty() } else null
                }
                // Prefer the address-book name over the self-chosen profile name, across all rows.
                val rows = ArrayList<Pair<String?, String?>>()
                while (c.moveToNext()) rows += text("display_name") to text("wa_name")
                rows.firstNotNullOfOrNull { it.first } ?: rows.firstNotNullOfOrNull { it.second }
            }
        }
    }

    private fun openReadOnly(context: Context, name: String): SQLiteDatabase? {
        val file = context.getDatabasePath(name)
        if (!file.exists()) return null
        return SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
    }
}
