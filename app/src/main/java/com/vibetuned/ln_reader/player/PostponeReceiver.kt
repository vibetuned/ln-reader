package com.vibetuned.ln_reader.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vibetuned.ln_reader.LnReaderApplication

class PostponeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as LnReaderApplication).container
        when (intent.action) {
            SleepTimerNotifier.ACTION_POSTPONE -> container.sleepTimerController.postpone()
            SleepTimerNotifier.ACTION_DISMISS -> container.sleepTimerController.dismissExpired()
        }
    }
}
