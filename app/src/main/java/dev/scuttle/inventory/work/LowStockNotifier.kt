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

private const val CHANNEL_ID = "low_stock_reminder"
private const val NOTIFICATION_ID = 1003

fun createLowStockNotificationChannel(context: Context) {
    val channel =
        NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_low_stock_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_low_stock_description)
        }
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(channel)
}

fun postLowStockNotification(
    context: Context,
    count: Int,
) {
    if (count <= 0) return

    val title = context.resources.getQuantityString(R.plurals.notification_low_stock_title, count, count)

    // Plain MainActivity intent, no navigate-to extra: v1 just opens the app.
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
