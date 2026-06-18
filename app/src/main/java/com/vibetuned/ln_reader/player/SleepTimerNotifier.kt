package com.vibetuned.ln_reader.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.vibetuned.ln_reader.MainActivity
import com.vibetuned.ln_reader.R

class SleepTimerNotifier(private val context: Context) {

    private val manager = context.getSystemService<NotificationManager>()!!

    fun postExpired(config: SleepTimerConfig) {
        ensureChannel()

        val postponeIntent = PendingIntent.getBroadcast(
            context,
            REQ_POSTPONE,
            Intent(context, PostponeReceiver::class.java).setAction(ACTION_POSTPONE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dismissIntent = PendingIntent.getBroadcast(
            context,
            REQ_DISMISS,
            Intent(context, PostponeReceiver::class.java).setAction(ACTION_DISMISS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val tapIntent = PendingIntent.getActivity(
            context,
            REQ_TAP,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_sleep_timer)
            .setContentTitle("Sleep timer ended")
            .setContentText(describe(config))
            .setStyle(NotificationCompat.BigTextStyle().bigText(describe(config)))
            .setContentIntent(tapIntent)
            .setDeleteIntent(dismissIntent)
            .addAction(R.drawable.ic_sleep_timer, "Postpone", postponeIntent)
            .addAction(R.drawable.ic_sleep_timer, "Dismiss", dismissIntent)
            .setAutoCancel(false)
            .setOngoing(false)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    fun cancel() {
        manager.cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel() {
        // The sleep timer fires while the user is trying to fall asleep, so the notification must
        // be silent. Earlier builds used IMPORTANCE_DEFAULT, which plays a sound. A channel's
        // importance is immutable once created, so we delete that legacy channel and use a new id
        // to guarantee the silent (IMPORTANCE_LOW) behavior even on upgraded installs.
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Sleep timer",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Silently notes when the sleep timer ends and offers to continue listening."
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun describe(config: SleepTimerConfig): String = when (config) {
        is SleepTimerConfig.TimeBased -> {
            val minutes = (config.totalMs / 60_000L).toInt()
            "Shake the phone or tap Postpone to listen for another $minutes minutes."
        }
        is SleepTimerConfig.ChapterBased -> {
            val n = config.chapterCount
            val noun = if (n == 1) "chapter" else "chapters"
            "Shake the phone or tap Postpone to listen for $n more $noun."
        }
        is SleepTimerConfig.EndOfChapter ->
            "Shake the phone or tap Postpone to keep going to the end of the next chapter."
    }

    companion object {
        const val CHANNEL_ID = "sleep_timer_silent"
        private const val LEGACY_CHANNEL_ID = "sleep_timer"
        const val NOTIFICATION_ID = 4242
        const val ACTION_POSTPONE = "com.vibetuned.ln_reader.action.SLEEP_TIMER_POSTPONE"
        const val ACTION_DISMISS = "com.vibetuned.ln_reader.action.SLEEP_TIMER_DISMISS"
        private const val REQ_POSTPONE = 1
        private const val REQ_DISMISS = 2
        private const val REQ_TAP = 3
    }
}
