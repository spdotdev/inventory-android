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

private const val CHANNEL_ID_HOUSEHOLD_EVENTS = "household_events"
private const val CHANNEL_ID_HOUSEHOLD_ACTIVITY = "household_activity"
private const val CHANNEL_ID_WEEKLY_SUMMARY = "weekly_summary"

private const val NOTIFICATION_ID_EVENT_BASE = 2000
private const val NOTIFICATION_ID_DIGEST_BASE = 3000
private const val NOTIFICATION_ID_MODULUS = 1000
private const val NOTIFICATION_ID_WEEKLY_SUMMARY = 1005

fun createFeedNotificationChannels(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
        NotificationChannel(
            CHANNEL_ID_HOUSEHOLD_EVENTS,
            context.getString(R.string.notification_channel_household_events_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_household_events_description)
        },
    )
    manager.createNotificationChannel(
        NotificationChannel(
            CHANNEL_ID_HOUSEHOLD_ACTIVITY,
            context.getString(R.string.notification_channel_household_activity_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_household_activity_description)
        },
    )
    manager.createNotificationChannel(
        NotificationChannel(
            CHANNEL_ID_WEEKLY_SUMMARY,
            context.getString(R.string.notification_channel_weekly_summary_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_weekly_summary_description)
        },
    )
}

fun postPlanned(
    context: Context,
    planned: PlannedNotification,
) {
    when (planned) {
        is PlannedNotification.MemberJoined -> {
            val memberName = planned.memberName ?: return
            val householdName = planned.householdName ?: context.getString(R.string.notification_fallback_household)
            val title = context.getString(R.string.notification_member_joined_title, memberName, householdName)
            postNotification(
                context,
                CHANNEL_ID_HOUSEHOLD_EVENTS,
                notificationIdForEvent(planned.eventId),
                title,
            )
        }
        is PlannedNotification.RoleChanged -> {
            val newRole = planned.newRole ?: return
            val householdName = planned.householdName ?: context.getString(R.string.notification_fallback_household)
            val title = context.getString(R.string.notification_role_changed_title, householdName, newRole)
            postNotification(
                context,
                CHANNEL_ID_HOUSEHOLD_EVENTS,
                notificationIdForEvent(planned.eventId),
                title,
            )
        }
        is PlannedNotification.ActivityDigest -> {
            val householdName = planned.householdName ?: context.getString(R.string.notification_fallback_household)
            val title =
                context.resources.getQuantityString(
                    R.plurals.notification_activity_digest_title,
                    planned.changeCount,
                    planned.changeCount,
                    householdName,
                )
            postNotification(
                context,
                CHANNEL_ID_HOUSEHOLD_ACTIVITY,
                notificationIdForDigest(planned.householdId),
                title,
            )
        }
    }
}

fun postWeeklySummary(
    context: Context,
    missing: Int,
    lowStock: Int,
) {
    val title = context.getString(R.string.notification_weekly_summary_title)
    val body = context.getString(R.string.notification_weekly_summary_body, missing, lowStock)
    postNotification(context, CHANNEL_ID_WEEKLY_SUMMARY, NOTIFICATION_ID_WEEKLY_SUMMARY, title, body)
}

private fun notificationIdForEvent(eventId: Long): Int =
    (NOTIFICATION_ID_EVENT_BASE + eventId % NOTIFICATION_ID_MODULUS).toInt()

private fun notificationIdForDigest(householdId: Long): Int =
    (NOTIFICATION_ID_DIGEST_BASE + householdId % NOTIFICATION_ID_MODULUS).toInt()

private fun postNotification(
    context: Context,
    channelId: String,
    notificationId: Int,
    title: String,
    body: String? = null,
) {
    // Plain MainActivity intent, no extras: v1 just opens the app.
    val intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    val pendingIntent =
        PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    val builder =
        NotificationCompat
            .Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
    if (body != null) {
        builder.setContentText(body)
    }

    if (ActivityCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }
    context.getSystemService(NotificationManager::class.java).notify(notificationId, builder.build())
}
