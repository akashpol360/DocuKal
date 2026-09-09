package com.docukal.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.docukal.app.MainActivity

/**
 * Fires the local expiry notification and, on tap, deep-links straight back into
 * MainActivity with enough context (kind + id) to open the specific record.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_ID, 0L)
        val kind = intent.getStringExtra(ReminderScheduler.EXTRA_KIND) ?: ReminderKind.DOCUMENT.name
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: "Reminder"

        val channelId = "expiry_reminders"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            notificationManager.createNotificationChannel(
                NotificationChannel(channelId, "Expiry reminders", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_KIND, kind)
            putExtra(MainActivity.EXTRA_OPEN_ID, id)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            id.toInt(),
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Expiry reminder")
            .setContentText("$title is due soon.")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(id.toInt(), notification)
    }
}
