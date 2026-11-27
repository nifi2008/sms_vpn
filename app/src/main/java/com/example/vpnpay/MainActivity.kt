package com.example.vpnpay

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var urlInput: EditText
    private lateinit var secretInput: EditText
    private lateinit var sendersInput: EditText
    private lateinit var enabledSwitch: Switch
    private lateinit var testButton: Button
    private lateinit var testResult: TextView
    private lateinit var statusText: TextView
    private lateinit var grantButton: Button

    private val teal = Color.rgb(14, 124, 134)
    private val ink = Color.rgb(24, 40, 62)
    private val muted = Color.rgb(90, 104, 122)
    private val surface = Color.WHITE
    private val page = Color.rgb(242, 246, 250)

    companion object {
        private const val REQ_SMS = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        loadSettings()
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::statusText.isInitialized) refreshStatus()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_SMS) return
        refreshStatus()
        // Android 13+ often refuses silently for sideloaded apps: explain the exact setting the user must change.
        if (!smsGranted()) showRestrictedDialog()
    }

    // ───────── UI ─────────

    private fun buildUi() {
        val scroll = ScrollView(this).apply { setBackgroundColor(page) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(28))
        }
        scroll.addView(root)
        setContentView(scroll)

        // Header with logo
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_logo)
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
        })
        header.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
            addView(text(getString(R.string.app_name), 26f, ink, bold = true))
            addView(text(getString(R.string.subtitle), 13f, muted))
        })
        root.addView(header)

        // Connection
        val connection = card()
        connection.addView(section(getString(R.string.section_connection)))
        urlInput = edit(getString(R.string.hint_url), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        connection.addView(field(getString(R.string.label_url), urlInput))
        secretInput = edit(getString(R.string.hint_secret), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        connection.addView(field(getString(R.string.label_secret), secretInput))
        sendersInput = edit(getString(R.string.hint_senders), InputType.TYPE_CLASS_TEXT)
        connection.addView(field(getString(R.string.label_senders), sendersInput))
        connection.addView(text(getString(R.string.senders_help), 12f, muted).apply { setPadding(0, dp(4), 0, dp(6)) })
        enabledSwitch = Switch(this).apply {
            text = getString(R.string.switch_enabled)
            textSize = 15f
            setTextColor(ink)
            setPadding(0, dp(10), 0, dp(10))
        }
        connection.addView(enabledSwitch)
        root.addView(connection, cardParams())

        // Actions: save and test
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
        }
        actions.addView(button(getString(R.string.btn_save), filled = true) { saveSettings() }, buttonParams(0, 8))
        testButton = button(getString(R.string.btn_test), filled = false) { testConnection() }
        actions.addView(testButton, buttonParams(8, 0))
        root.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        testResult = text("", 13f, muted).apply {
            setPadding(dp(4), dp(10), dp(4), 0)
            visibility = View.GONE
        }
        root.addView(testResult)

        // SMS permission
        val permission = card()
        permission.addView(section(getString(R.string.section_permission)))
        permission.addView(text(getString(R.string.permission_help), 13f, muted).apply { setPadding(0, 0, 0, dp(10)) })
        grantButton = button(getString(R.string.btn_grant), filled = true) { requestSmsPermission() }
        permission.addView(grantButton)
        root.addView(permission, cardParams())

        // Device status
        val status = card()
        status.addView(section(getString(R.string.section_status)))
        statusText = text("", 14f, ink)
        status.addView(statusText)
        root.addView(status, cardParams())

        root.addView(text(getString(R.string.security_note), 12f, muted).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(8), dp(16), dp(8), 0)
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
            toast(R.string.toast_https)
            return
        }
        if (enabledSwitch.isChecked && (url.isBlank() || secret.isBlank() || senders.isBlank())) {
            toast(R.string.toast_missing)
            return
        }
        AppSettings.save(this, url, secret, senders, enabledSwitch.isChecked)
        toast(R.string.toast_saved)
        refreshStatus()
    }

    // ───────── connection test ─────────

    private fun testConnection() {
        val url = urlInput.text.toString().trim()
        val secret = secretInput.text.toString().trim()
        if (url.isBlank()) { toast(R.string.test_need_url); return }
        if (!url.startsWith("https://")) { toast(R.string.toast_https); return }

        testButton.isEnabled = false
        showTestResult(getString(R.string.testing), muted)
        thread(name = "vpn-pay-test") {
            // A harmless message from a sender that is not in the list: the server checks the key and ignores it.
            val payload = JSONObject()
                .put("sender", "VPN-PAY-TEST")
                .put("body", "connection test")
                .toString().toByteArray(Charsets.UTF_8)
            var connection: HttpURLConnection? = null
            val result: Pair<String, Int> = try {
                connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("X-Secret", secret)
                connection.outputStream.use { it.write(payload) }
                val code = connection.responseCode
                when (code) {
                    in 200..299 -> getString(R.string.test_ok) to Color.rgb(22, 128, 61)
                    401 -> getString(R.string.test_bad_key) to Color.rgb(190, 40, 40)
                    else -> getString(R.string.test_http, code) to Color.rgb(190, 40, 40)
                }
            } catch (e: Exception) {
                getString(R.string.test_error, e.javaClass.simpleName) to Color.rgb(190, 40, 40)
            } finally {
                connection?.disconnect()
            }
            runOnUiThread {
                testButton.isEnabled = true
                showTestResult(result.first, result.second)
            }
        }
    }

    private fun showTestResult(message: String, color: Int) {
        testResult.text = message
        testResult.setTextColor(color)
        testResult.visibility = View.VISIBLE
    }

    // ───────── SMS permission ─────────

    private fun smsGranted() =
        checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

    private fun requestSmsPermission() {
        if (smsGranted()) {
            toast(R.string.permission_already)
            return
        }
        requestPermissions(arrayOf(Manifest.permission.RECEIVE_SMS), REQ_SMS)
    }

    private fun showRestrictedDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.restricted_title)
            .setMessage(R.string.restricted_body)
            .setPositiveButton(R.string.btn_open_settings) { _, _ -> openAppSettings() }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    // ───────── status ─────────

    private fun refreshStatus() {
        val granted = smsGranted()
        grantButton.isEnabled = !granted
        grantButton.alpha = if (granted) 0.5f else 1f
        statusText.text = listOf(
            getString(R.string.status_sms, getString(if (granted) R.string.granted else R.string.required)),
            getString(R.string.status_forward, getString(if (AppSettings.enabled(this)) R.string.on else R.string.off)),
            getString(R.string.status_senders, AppSettings.allowedSenders(this).size)
        ).joinToString("\n")
    }

    // ───────── small helpers ─────────

    private fun toast(id: Int) = Toast.makeText(this, id, Toast.LENGTH_LONG).show()

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = rounded(surface, 16)
    }

    private fun cardParams() = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) }

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun section(value: String) = text(value, 17f, ink, bold = true).apply {
        setPadding(0, 0, 0, dp(10))
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private fun field(caption: String, input: EditText) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(6), 0, dp(6))
        addView(text(caption, 13f, muted))
        addView(input)
    }

    private fun edit(hintText: String, type: Int) = EditText(this).apply {
        hint = hintText
        textSize = 14f
        singleLine = true
        inputType = type
        setTextColor(ink)
        setHintTextColor(Color.rgb(150, 160, 175))
        background = rounded(page, 10)
        setPadding(dp(12), dp(11), dp(12), dp(11))
    }

    private fun button(label: String, filled: Boolean, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 15f
        setTextColor(if (filled) Color.WHITE else teal)
        background = if (filled) rounded(teal, 12) else GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            setStroke(dp(2), teal)
            cornerRadius = dp(12).toFloat()
        }
        setOnClickListener { onClick() }
    }

    private fun buttonParams(startDp: Int, endDp: Int) = LinearLayout.LayoutParams(0, -2, 1f).apply {
        marginStart = dp(startDp)
        marginEnd = dp(endDp)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
