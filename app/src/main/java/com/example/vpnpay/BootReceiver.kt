package com.example.vpnpay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** After a phone restart, bring the background service back if forwarding was on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) ForwarderService.sync(context)
    }
}
