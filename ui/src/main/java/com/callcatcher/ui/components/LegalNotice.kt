package com.callcatcher.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * First-run legal notice. Recording your own calls is lawful in many places, but the rules differ
 * by country and by how recordings are used, so we state the principle and disclaim misuse rather
 * than give legal advice.
 */
@Composable
fun LegalNoticeDialog(onAccept: () -> Unit) {
    AlertDialog(
        onDismissRequest = { /* must be acknowledged */ },
        title = { Text("Before you start") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Recording your own conversations is generally lawful, and you have every " +
                        "right to keep a record of your own calls for your needs or as a precaution.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "However, the rules vary by country and by how a recording is used or shared. " +
                        "You are responsible for how you use what you record. The developer accepts " +
                        "no responsibility for any use made of this app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) { Text("I understand") }
        },
    )
}
