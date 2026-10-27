package com.example.data.repository

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.Manifest
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity

object SchoolNotificationCenter {
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channels = SchoolNotificationCategory.entries.map { category ->
            NotificationChannel(
                category.channelId,
                category.title,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "School ${category.title.lowercase()} for this signed-in account"
                lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            }
        }
        manager.createNotificationChannels(channels)
    }

    fun show(context: Context, notification: NotificationPollingPayload) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        createChannels(context)
        val category = SchoolNotificationCategory.fromKind(notification.kind)
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_NOTIFICATION)
            .putExtra(MainActivity.EXTRA_NOTIFICATION_SCREEN, category.destination.name)
            .putExtra(MainActivity.EXTRA_NOTIFICATION_ID, notification.id)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            context,
            notification.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val built = NotificationCompat.Builder(context, category.channelId)
            .setSmallIcon(com.example.R.drawable.ic_notification)
            .setContentTitle(notification.title.ifBlank { category.title })
            .setContentText(notification.message.ifBlank { category.title })
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        NotificationManagerCompat.from(context).notify(notification.id.hashCode(), built)
    }

    const val ACTION_OPEN_NOTIFICATION = "com.example.action.OPEN_SCHOOL_NOTIFICATION"
}

data class NotificationPollingPayload(
    val id: String,
    val title: String,
    val message: String,
    val kind: String
)
