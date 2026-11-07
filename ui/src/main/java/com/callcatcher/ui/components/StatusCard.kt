package com.callcatcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.callcatcher.ui.model.SetupStatus
import com.callcatcher.ui.theme.CC
import com.callcatcher.ui.util.formatDateTime

/** Setup checklist and diagnostics, opened from the status pill. */
@Composable
fun StatusCard(status: SetupStatus, onClearError: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(CC.Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Setup", style = MaterialTheme.typography.titleSmall, color = CC.TextPrimary)
        Check("Privileged system app", status.isSystemApp)
        Check("Phone call audio", status.canCaptureCallAudio)
        Check("Call detection", status.canDetectCalls)
        Check("WhatsApp audio (playback capture)", status.canCapturePlayback)
        Check("Microphone", status.hasMicrophone)
        Check("Contacts, for names", status.hasContacts)
        Check("Notifications", status.hasNotifications)

        when {
            !status.isSystemApp -> Hint("Flash the CallCatcher module in KernelSU and reboot. A plain APK install can't reach call audio.")
            !status.canCapturePlayback -> Hint("This build needs new system permissions: flash the latest module zip and reboot.")
        }

        Spacer(Modifier.height(4.dp))
        Text("Diagnostics", style = MaterialTheme.typography.titleSmall, color = CC.TextPrimary)
        Info(
            "WhatsApp trigger",
            if (status.lastTriggerAt > 0) "last ${formatDateTime(status.lastTriggerAt)}" else "never received",
        )
        status.lastSaved?.let { Info("Last saved", "$it · ${formatDateTime(status.lastSavedAt)}") }
        status.lastError?.let {
            Info("Last error", "$it · ${formatDateTime(status.lastErrorAt)}", warn = true)
            Text(
                "Clear error",
                style = MaterialTheme.typography.labelMedium,
                color = CC.Accent,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClearError).padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun Check(label: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (ok) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = if (ok) "OK" else "Missing",
            tint = if (ok) CC.Ok else CC.TextTertiary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = if (ok) CC.TextPrimary else CC.TextSecondary)
    }
}

@Composable
private fun Info(label: String, value: String, warn: Boolean = false) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = CC.TextTertiary)
        Text(value, style = MaterialTheme.typography.bodySmall, color = if (warn) CC.Warn else CC.TextSecondary)
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = CC.Accent)
}
