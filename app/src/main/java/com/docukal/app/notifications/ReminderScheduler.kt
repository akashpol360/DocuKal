package com.docukal.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** What kind of record a scheduled reminder points back to. */
enum class ReminderKind(val requestCodeOffset: Int) {
    DOCUMENT(0),
    WARRANTY(2_000_000_000 / 3),
    IMPORTANT_DATE(2 * (2_000_000_000 / 3))
}

/**
 * Schedules and cancels local (no server, no push) expiry reminders for documents,
 * warranties and important dates via AlarmManager. Uses setAndAllowWhileIdle rather
 * than the exact variant so no SCHEDULE_EXACT_ALARM permission is required - a
 * reminder firing within the OS's normal Doze batching window is acceptable for a
 * "days before expiry" notice.
 */
class ReminderScheduler(private val context: Context) {

    fun schedule(kind: ReminderKind, id: Long, title: String, targetDate: Long?, reminderDays: Int) {
        cancel(kind, id)
        val target = targetDate ?: return
        val at = target - reminderDays * 86_400_000L
        if (at <= System.currentTimeMillis()) return

        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(EXTRA_ID, id)
            .putExtra(EXTRA_KIND, kind.name)
            .putExtra(EXTRA_TITLE, title)
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode(kind, id),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    fun cancel(kind: ReminderKind, id: Long) {
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode(kind, id),
            Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        if (pi != null) {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi)
            pi.cancel()
        }
    }

    companion object {
        const val EXTRA_ID = "reminder_id"
        const val EXTRA_KIND = "reminder_kind"
        const val EXTRA_TITLE = "reminder_title"

        /** Keeps documents/warranties/important-dates from colliding on the same numeric id. */
        private fun requestCode(kind: ReminderKind, id: Long): Int =
            kind.requestCodeOffset + (id % 1_000_000L).toInt()
    }
}
