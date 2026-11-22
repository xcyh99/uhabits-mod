package org.isoron.uhabits.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class StatusBarDaysReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        StatusBarDaysNotifier.update(context)
    }
}
