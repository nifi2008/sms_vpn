package com.example.vpnpay

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Applies the language chosen inside the app (Persian or English) to any Context. */
object Lang {
    fun wrap(base: Context): Context {
        val locale = Locale(AppSettings.language(base))
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.locale = locale
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}
