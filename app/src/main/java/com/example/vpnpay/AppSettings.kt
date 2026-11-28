package com.example.vpnpay

import android.content.Context

object AppSettings {
    private const val PREFS = "vpn_pay_settings"
    private const val URL = "server_url"
    private const val SECRET = "secret"
    private const val SENDERS = "senders"
    private const val ENABLED = "enabled"
    private const val LANG = "language"

    fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun serverUrl(context: Context) = prefs(context).getString(URL, "") ?: ""
    fun secret(context: Context) = prefs(context).getString(SECRET, "") ?: ""
    fun senders(context: Context) = prefs(context).getString(SENDERS, "") ?: ""
    fun enabled(context: Context) = prefs(context).getBoolean(ENABLED, false)

    /** “fa” or “en”. Without a choice, the device language decides (Persian if it is Persian, else English). */
    fun language(context: Context): String =
        prefs(context).getString(LANG, null)
            ?: if (java.util.Locale.getDefault().language == "fa") "fa" else "en"

    fun setLanguage(context: Context, code: String) {
        prefs(context).edit().putString(LANG, code).apply()
    }

    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(ENABLED, value).apply()
    }

    fun allowedSenders(context: Context): Set<String> =
        senders(context).split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    fun save(context: Context, url: String, secret: String, senders: String, enabled: Boolean) {
        prefs(context).edit()
            .putString(URL, url.trim())
            .putString(SECRET, secret.trim())
            .putString(SENDERS, senders.trim())
            .putBoolean(ENABLED, enabled)
            .apply()
    }
}
