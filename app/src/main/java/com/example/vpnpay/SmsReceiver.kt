package com.example.vpnpay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.concurrent.thread

/**
 * Forwards only SMS messages from explicitly configured sender IDs.
 * Message content is not written to logs or local storage.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!AppSettings.enabled(context)) return
        if (context.checkSelfPermission(android.Manifest.permission.RECEIVE_SMS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val sender = messages.firstOrNull()?.originatingAddress ?: return
        val allowed = AppSettings.allowedSenders(context)
        if (allowed.isEmpty() || sender !in allowed) return

        val serverUrl = AppSettings.serverUrl(context)
        val secret = AppSettings.secret(context)
        if (!serverUrl.startsWith("https://") || secret.isBlank()) return

        val body = messages.joinToString("") { it.messageBody ?: "" }
        val pending = goAsync()

        thread(name = "vpn-pay-sms-forward") {
            try {
                val payload = JSONObject().put("sender", sender).put("body", body)
                    .put("timestamp", isoUtc(messages.first().timestampMillis))
                    .toString().toByteArray(Charsets.UTF_8)

                for (attempt in 0 until 5) {
                    var connection: HttpURLConnection? = null
                    try {
                        connection = URL(serverUrl).openConnection() as HttpURLConnection
                        connection.requestMethod = "POST"
                        connection.connectTimeout = 8000
                        connection.readTimeout = 8000
                        connection.doOutput = true
                        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        connection.setRequestProperty("X-Secret", secret)
                        connection.outputStream.use { it.write(payload) }
                        val code = connection.responseCode
                        if (code in 200..299) break
                    } catch (e: Exception) {
                        Log.w("VPNPay", "SMS forwarding attempt failed (${attempt + 1})")
                    } finally {
                        connection?.disconnect()
                    }
                    if (attempt < 4) Thread.sleep(2000L * (attempt + 1))
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun isoUtc(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(millis))
}
