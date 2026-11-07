package com.callcatcher.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.callcatcher.ui.model.SetupStatus
import com.callcatcher.ui.util.formatDateTime

/**
 * Collapsible setup/diagnostics panel. Starts expanded while setup is incomplete, so the next step
 * is visible without hunting; once everything is in place it collapses to one line.
 */
@Composable
fun StatusPanel(status: SetupStatus, onClearError: () -> Unit, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable(status.ready) { mutableStateOf(!status.ready) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (status.ready) "Ready to record calls" else "Setup incomplete",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Check("Privileged system app (KernelSU module)", status.isSystemApp)
                    Check("Call audio access (CAPTURE_AUDIO_OUTPUT)", status.canCaptureCallAudio)
                    Check("Call detection (CONTROL_INCALL_EXPERIENCE)", status.canDetectCalls)
                    Check("Microphone", status.hasMicrophone)
                    Check("Contacts (names)", status.hasContacts)
                    Check("Notifications", status.hasNotifications)

                    if (!status.isSystemApp) {
                        Hint("Flash the CallCatcher KernelSU module and reboot. A plain APK install can't get call audio.")
                    }

                    Info(
                        "WhatsApp trigger",
                        if (status.lastTriggerAt > 0) "last received ${formatDateTime(status.lastTriggerAt)}" else "never received",
                    )
                    status.lastSaved?.let { Info("Last saved", "$it (${formatDateTime(status.lastSavedAt)})") }
                    status.lastError?.let {
                        Info("Last error", "$it (${formatDateTime(status.lastErrorAt)})")
                        TextButton(onClick = onClearError) { Text("Clear error") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Check(label: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            imageVector = if (ok) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = if (ok) "OK" else "Missing",
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Info(label: String, value: String) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
}
