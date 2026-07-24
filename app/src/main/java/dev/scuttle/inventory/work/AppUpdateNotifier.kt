package dev.scuttle.inventory.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import dev.scuttle.inventory.MainActivity
import dev.scuttle.inventory.R
import dev.scuttle.inventory.data.appupdate.UpdateStatus

private const val CHANNEL_ID = "app_updates"
private const val NOTIFICATION_ID = 1001
private const val NOTIFICATION_BODY_MAX_LENGTH = 100

fun createAppUpdatesNotificationChannel(context: Context) {
    val channel =
        NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_app_updates_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_app_updates_description)
        }
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(channel)
}

fun postAppUpdateNotification(
    context: Context,
    status: UpdateStatus,
) {
    val release =
        when (status) {
            is UpdateStatus.Optional -> status.release
            is UpdateStatus.Breaking -> status.release
            UpdateStatus.None -> return
        }
    val isBreaking = status is UpdateStatus.Breaking
    val title =
        context.getString(
            if (isBreaking) {
                R.string.notification_app_update_required_title
            } else {
                R.string.notification_app_update_available_title
            },
        )
    val body =
        release.changelog
            .lineSequence()
            .first()
            .take(NOTIFICATION_BODY_MAX_LENGTH)

    val intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    val pendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    val notification =
        NotificationCompat
            .Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

    if (ActivityCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }
    context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
}
