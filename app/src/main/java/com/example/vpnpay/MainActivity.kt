package com.example.vpnpay

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private lateinit var urlInput: EditText
    private lateinit var secretInput: EditText
    private lateinit var sendersInput: EditText
    private lateinit var enabledSwitch: Switch
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        loadSettings()
        updatePermissionStatus()
    }

    private fun buildUi() {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(28))
            setBackgroundColor(Color.rgb(245, 248, 252))
        }
        scroll.addView(root)
        setContentView(scroll)

        val title = TextView(this).apply {
            text = "VPN Pay"
            textSize = 29f
            setTextColor(Color.rgb(20, 43, 75))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        root.addView(title)
        root.addView(label("مدیریت دریافت و ارسال پیامک بانکی"))
        root.addView(space(18))

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(Color.WHITE)
        }
        root.addView(card, LinearLayout.LayoutParams(-1, -2))
        card.addView(section("تنظیمات اتصال"))

        urlInput = edit("آدرس سرور، مانند https://example.com/sms", false)
        card.addView(field("Server URL", urlInput))
        secretInput = edit("کلید محرمانه X-Secret", true)
        card.addView(field("SMS Secret", secretInput))
        sendersInput = edit("مثلاً BANK1, 30001234", false)
        card.addView(field("فرستنده‌های مجاز", sendersInput))
        card.addView(label("چند فرستنده را با کاما جدا کنید. فقط پیامک همین فرستنده‌ها ارسال می‌شود."))

        enabledSwitch = Switch(this).apply {
            text = "فعال بودن ارسال پیامک"
            textSize = 15f
            setTextColor(Color.rgb(35, 50, 70))
            setPadding(0, dp(14), 0, dp(14))
        }
        card.addView(enabledSwitch)

        val save = Button(this).apply {
            text = "ذخیره تنظیمات"
            isAllCaps = false
            setOnClickListener { saveSettings() }
        }
        card.addView(save)

        val permission = Button(this).apply {
            text = "اعطای مجوز دریافت پیامک"
            isAllCaps = false
            setOnClickListener { requestSmsPermission() }
        }
        root.addView(permission, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })

        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            setBackgroundColor(Color.WHITE)
        }
        root.addView(statusCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        statusCard.addView(section("وضعیت دستگاه"))
        statusText = label("")
        statusCard.addView(statusText)
        root.addView(label("نکته امنیتی: از HTTPS استفاده کنید. کلید محرمانه را در اختیار دیگران قرار ندهید. این برنامه پیامک‌های فرستنده‌های مجاز را به سرور تنظیم‌شده ارسال می‌کند." ).apply {
            textSize = 12f
            setPadding(0, dp(16), 0, 0)
        })
    }

    private fun loadSettings() {
        urlInput.setText(AppSettings.serverUrl(this))
        secretInput.setText(AppSettings.secret(this))
        sendersInput.setText(AppSettings.senders(this))
        enabledSwitch.isChecked = AppSettings.enabled(this)
    }

    private fun saveSettings() {
        val url = urlInput.text.toString().trim()
        val secret = secretInput.text.toString().trim()
        val senders = sendersInput.text.toString().trim()
        if (url.isNotEmpty() && !url.startsWith("https://")) {
            Toast.makeText(this, "برای امنیت، آدرس سرور باید با https:// شروع شود.", Toast.LENGTH_LONG).show()
            return
        }
        if (enabledSwitch.isChecked && (url.isBlank() || secret.isBlank() || senders.isBlank())) {
            Toast.makeText(this, "برای فعال‌سازی، آدرس، کلید و حداقل یک فرستنده را وارد کنید.", Toast.LENGTH_LONG).show()
            return
        }
        AppSettings.save(this, url, secret, senders, enabledSwitch.isChecked)
        Toast.makeText(this, "تنظیمات ذخیره شد.", Toast.LENGTH_SHORT).show()
        updatePermissionStatus()
    }

    private fun requestSmsPermission() {
        if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "مجوز دریافت پیامک قبلاً داده شده است.", Toast.LENGTH_SHORT).show()
        } else {
            requestPermissions(arrayOf(Manifest.permission.RECEIVE_SMS), 100)
        }
    }

    private fun updatePermissionStatus() {
        val granted = checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        val enabled = AppSettings.enabled(this)
        statusText.text = "دریافت پیامک: ${if (granted) "مجاز" else "نیازمند مجوز"}\n" +
            "ارسال به سرور: ${if (enabled) "فعال" else "غیرفعال"}\n" +
            "فرستنده‌های مجاز: ${AppSettings.allowedSenders(this).size}"
    }

    override fun onResume() {
        super.onResume()
        if (::statusText.isInitialized) updatePermissionStatus()
    }

    private fun field(caption: String, input: EditText): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(label(caption))
        box.addView(input)
        return box
    }

    private fun edit(hintText: String, password: Boolean) = EditText(this).apply {
        hint = hintText
        textSize = 14f
        singleLine = true
        inputType = if (password) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    private fun label(value: String) = TextView(this).apply {
        text = value
        textSize = 14f
        setTextColor(Color.rgb(65, 78, 96))
        setPadding(0, dp(6), 0, dp(6))
    }

    private fun section(value: String) = TextView(this).apply {
        text = value
        textSize = 18f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setTextColor(Color.rgb(25, 55, 90))
        setPadding(0, 0, 0, dp(10))
    }

    private fun space(h: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(h))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
