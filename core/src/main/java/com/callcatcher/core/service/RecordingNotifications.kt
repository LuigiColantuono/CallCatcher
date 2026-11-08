package com.callcatcher.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.callcatcher.core.R
import com.callcatcher.core.model.CallMetadata
import com.callcatcher.core.model.CallSource

/**
 * The single "recording" notification, shared by every hosting service (InCallService for phone
 * calls, [RecorderService] for WhatsApp and tests). A microphone-type foreground service is what
 * lets the app keep capturing while it is in the background.
 */
object RecordingNotifications {

    private const val CHANNEL_ID = "recording"
    const val NOTIFICATION_ID = 1001

    fun startForeground(service: Service, metadata: CallMetadata) {
        ensureChannel(service)
        ServiceCompat.startForeground(
            service,
            NOTIFICATION_ID,
            build(service, metadata),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )
    }

    fun stopForeground(service: Service) {
        ServiceCompat.stopForeground(service, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Recording", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while a call is being recorded"
                setShowBadge(false)
            }
        )
    }

    private fun build(context: Context, metadata: CallMetadata): Notification {
        val who = metadata.contactName ?: metadata.address
        val what = when (metadata.source) {
            CallSource.PHONE -> "Phone call"
            CallSource.WHATSAPP -> "WhatsApp call"
            CallSource.TEST -> "Voice recording"
            CallSource.UNKNOWN -> "Call"
        }
        val openApp = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_record)
            .setContentTitle("Recording")
            .setContentText(if (who.isNullOrBlank()) what else "$what · $who")
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp)
            .build()
    }
}
