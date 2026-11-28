package com.example.vpnpay

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
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
    private lateinit var testButton: Button
    private lateinit var testResult: TextView
    private lateinit var sendLastButton: Button
    private lateinit var sendLastResult: TextView
    private lateinit var masterToggle: AnimatedToggle
    private lateinit var toggleLabel: TextView
    private lateinit var homeStatus: TextView
    private lateinit var homeGrant: Button
    private lateinit var content: FrameLayout
    private lateinit var pages: Array<View>
    private val tabs = mutableListOf<TextView>()

    private val teal = Color.rgb(14, 124, 134)
    private val ink = Color.rgb(24, 40, 62)
    private val muted = Color.rgb(90, 104, 122)
    private val surface = Color.WHITE
    private val pageBg = Color.rgb(242, 246, 250)

    companion object {
        private const val REQ_SMS = 100
        private const val REQ_READ_SMS = 101
        private const val REQ_READ_SMS_LIST = 102
        private const val REQ_NOTIF = 103
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(Lang.wrap(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        loadSettings()
        refreshStatus()
        showTab(0)
        if (AppSettings.enabled(this)) {
            requestNotifPermission()
            ForwarderService.sync(this)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::homeStatus.isInitialized) refreshStatus()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIF) {
            refreshStatus()
            return
        }
        if (requestCode == REQ_READ_SMS_LIST) {
            if (readSmsGranted()) pickSenders() else showRestrictedDialog()
            return
        }
        if (requestCode == REQ_READ_SMS) {
            if (readSmsGranted()) sendLastSms() else showRestrictedDialog()
            return
        }
        if (requestCode != REQ_SMS) return
        refreshStatus()
        // Android 13+ often refuses silently for sideloaded apps: explain the exact setting the user must change.
        if (!smsGranted()) showRestrictedDialog()
    }

    // ───────── UI ─────────

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pageBg)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(8))
        }
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_logo)
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
        })
        header.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
            addView(text(getString(R.string.app_name), 24f, ink, bold = true))
            addView(text(getString(R.string.subtitle), 13f, muted))
        })
        root.addView(header)

        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(buildTabBar(), LinearLayout.LayoutParams(-1, -2))
        setContentView(root)

        pages = arrayOf<View>(homePage(), serverPage(), messagesPage(), settingsPage())
        pages.forEach {
            content.addView(it)
            it.visibility = View.GONE
        }
    }

    /** One screen: a scrollable column that the page builder fills. */
    private fun page(build: LinearLayout.() -> Unit): ScrollView {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(24))
            build()
        }
        return ScrollView(this).apply { addView(column) }
    }

    private fun homePage() = page {
        val toggleCard = card()
        val row = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val labels = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(text(getString(R.string.home_forward_title), 17f, ink, bold = true))
        toggleLabel = text("", 13f, muted).apply { setPadding(0, dp(4), 0, 0) }
        labels.addView(toggleLabel)
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        masterToggle = AnimatedToggle(this@MainActivity).apply {
            onToggle = { handleToggle(it) }
        }
        row.addView(masterToggle)
        toggleCard.addView(row)
        addView(toggleCard, cardParams())

        val statusCard = card()
        statusCard.addView(section(getString(R.string.section_status)))
        homeStatus = text("", 14f, ink)
        statusCard.addView(homeStatus)
        addView(statusCard, cardParams())

        homeGrant = button(getString(R.string.btn_grant), filled = true) { requestSmsPermission() }
        addView(homeGrant, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })

        addView(text(getString(R.string.security_note), 12f, muted).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(8), dp(16), dp(8), 0)
        })
    }

    private fun serverPage() = page {
        val connection = card()
        connection.addView(section(getString(R.string.section_connection)))
        urlInput = edit(getString(R.string.hint_url), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        connection.addView(field(getString(R.string.label_url), urlInput))
        secretInput = edit(getString(R.string.hint_secret), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        connection.addView(field(getString(R.string.label_secret), secretInput))
        sendersInput = edit(getString(R.string.hint_senders), InputType.TYPE_CLASS_TEXT)
        connection.addView(field(getString(R.string.label_senders), sendersInput))
        connection.addView(
            button(getString(R.string.btn_pick_senders), filled = false) { pickSenders() },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) }
        )
        connection.addView(text(getString(R.string.senders_help), 12f, muted).apply { setPadding(0, dp(4), 0, dp(6)) })
        addView(connection, cardParams())

        val actions = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
        }
        actions.addView(button(getString(R.string.btn_save), filled = true) { saveSettings() }, buttonParams(0, 8))
        testButton = button(getString(R.string.btn_test), filled = false) { testConnection() }
        actions.addView(testButton, buttonParams(8, 0))
        addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        testResult = text("", 13f, muted).apply {
            setPadding(dp(4), dp(10), dp(4), 0)
            visibility = View.GONE
        }
        addView(testResult)
    }

    private fun messagesPage() = page {
        val sendLast = card()
        sendLast.addView(section(getString(R.string.section_send_last)))
        sendLast.addView(text(getString(R.string.send_last_help), 13f, muted).apply { setPadding(0, 0, 0, dp(10)) })
        sendLastButton = button(getString(R.string.btn_send_last), filled = false) { sendLastSms() }
        sendLast.addView(sendLastButton)
        sendLastResult = text("", 13f, muted).apply {
            setPadding(dp(4), dp(10), dp(4), 0)
            visibility = View.GONE
        }
        sendLast.addView(sendLastResult)
        addView(sendLast, cardParams())
    }

    private fun settingsPage() = page {
        val language = card()
        language.addView(section(getString(R.string.section_language)))
        language.addView(text(getString(R.string.language_help), 13f, muted).apply { setPadding(0, 0, 0, dp(10)) })
        val current = AppSettings.language(this@MainActivity)
        val row = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
        }
        row.addView(button("فارسی", filled = current == "fa") { setLanguage("fa") }, buttonParams(0, 8))
        row.addView(button("English", filled = current == "en") { setLanguage("en") }, buttonParams(8, 0))
        language.addView(row)
        addView(language, cardParams())
    }

    private fun buildTabBar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(surface)
            setPadding(dp(6), dp(6), dp(6), dp(8))
            elevation = dp(8).toFloat()
        }
        val names = listOf(R.string.tab_home, R.string.tab_server, R.string.tab_messages, R.string.tab_settings)
        names.forEachIndexed { i, res ->
            val tab = TextView(this).apply {
                text = getString(res)
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(0, dp(10), 0, dp(10))
                setOnClickListener { showTab(i) }
            }
            tabs.add(tab)
            bar.addView(tab, LinearLayout.LayoutParams(0, -2, 1f))
        }
        return bar
    }

    private fun showTab(index: Int) {
        pages.forEachIndexed { i, v -> v.visibility = if (i == index) View.VISIBLE else View.GONE }
        tabs.forEachIndexed { i, t ->
            val on = i == index
            t.setTextColor(if (on) teal else muted)
            t.typeface = if (on) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            t.background = if (on) rounded(Color.rgb(224, 242, 241), 14) else null
        }
    }

    private fun loadSettings() {
        urlInput.setText(AppSettings.serverUrl(this))
        secretInput.setText(AppSettings.secret(this))
        sendersInput.setText(AppSettings.senders(this))
        masterToggle.setChecked(AppSettings.enabled(this), animate = false)
    }

    /** Saves the server screen. Forwarding stays on only while the settings are complete. */
    private fun saveSettings() {
        val url = urlInput.text.toString().trim()
        val secret = secretInput.text.toString().trim()
        val senders = sendersInput.text.toString().trim()
        if (url.isNotEmpty() && !url.startsWith("https://")) {
            toast(R.string.toast_https)
            return
        }
        val complete = url.isNotBlank() && secret.isNotBlank() && senders.isNotBlank()
        val wasOn = AppSettings.enabled(this)
        val on = wasOn && complete
        AppSettings.save(this, url, secret, senders, on)
        toast(if (wasOn && !complete) R.string.toast_missing else R.string.toast_saved)
        masterToggle.setChecked(on, animate = true)
        if (on) requestNotifPermission()
        ForwarderService.sync(this)
        refreshStatus()
    }

    /** The big switch on the home screen. Turning it on needs a complete server setup. */
    private fun handleToggle(value: Boolean) {
        if (value) {
            val complete = AppSettings.serverUrl(this).isNotBlank() &&
                AppSettings.secret(this).isNotBlank() &&
                AppSettings.allowedSenders(this).isNotEmpty()
            if (!complete) {
                toast(R.string.toast_missing)
                masterToggle.setChecked(false, animate = true)
                return
            }
            AppSettings.setEnabled(this, true)
            requestNotifPermission()
        } else {
            AppSettings.setEnabled(this, false)
        }
        ForwarderService.sync(this)
        refreshStatus()
    }

    private fun setLanguage(code: String) {
        if (AppSettings.language(this) == code) return
        AppSettings.setLanguage(this, code)
        recreate()
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

    // ───────── send the latest SMS (manual test) ─────────

    private data class SmsItem(val sender: String, val body: String, val date: Long)

    private fun readSmsGranted() =
        checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    /** Newest inbox message whose sender is exactly one of the allowed senders. */
    private fun findLatestSms(senders: Set<String>): SmsItem? {
        return try {
            contentResolver.query(
                Uri.parse("content://sms/inbox"),
                arrayOf("address", "body", "date"),
                null, null, "date DESC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val address = c.getString(0) ?: continue
                    if (address in senders) return SmsItem(address, c.getString(1) ?: "", c.getLong(2))
                }
                null
            }
        } catch (e: SecurityException) {
            null
        }
    }

    /** Distinct sender names in the newest inbox messages, so the user can copy the exact one. */
    private fun recentInboxSenders(): List<String> = try {
        contentResolver.query(
            Uri.parse("content://sms/inbox"),
            arrayOf("address"),
            null, null, "date DESC"
        )?.use { c ->
            val seen = LinkedHashSet<String>()
            var n = 0
            while (c.moveToNext() && n < 500 && seen.size < 40) {
                c.getString(0)?.let { seen.add(it) }
                n++
            }
            seen.toList()
        } ?: emptyList()
    } catch (e: SecurityException) {
        emptyList()
    }

    /** Lets the user tick the senders they want from the inbox; the choice fills the Allowed senders field. */
    private fun pickSenders() {
        if (!readSmsGranted()) {
            requestPermissions(arrayOf(Manifest.permission.READ_SMS), REQ_READ_SMS_LIST)
            return
        }
        val list = recentInboxSenders()
        if (list.isEmpty()) {
            toast(R.string.pick_senders_empty)
            return
        }
        val current = sendersInput.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val checked = BooleanArray(list.size) { list[it] in current }
        AlertDialog.Builder(this)
            .setTitle(R.string.pick_senders_title)
            .setMultiChoiceItems(list.toTypedArray<CharSequence>(), checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton(R.string.btn_ok) { _, _ ->
                sendersInput.setText(list.filterIndexed { i, _ -> checked[i] }.joinToString(", "))
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun sendLastSms() {
        val url = urlInput.text.toString().trim()
        val secret = secretInput.text.toString().trim()
        val senders = AppSettings.allowedSenders(this)
        if (url.isBlank()) { toast(R.string.test_need_url); return }
        if (!url.startsWith("https://")) { toast(R.string.toast_https); return }
        if (senders.isEmpty()) { toast(R.string.send_last_need_senders); return }
        if (!readSmsGranted()) {
            requestPermissions(arrayOf(Manifest.permission.READ_SMS), REQ_READ_SMS)
            return
        }
        val latest = findLatestSms(senders)
        if (latest == null) {
            val seen = recentInboxSenders()
            val hint = if (seen.isEmpty()) "" else "\n" + getString(R.string.send_last_seen, seen.take(6).joinToString(", "))
            showSendLast(getString(R.string.send_last_none) + hint, muted)
            return
        }

        sendLastButton.isEnabled = false
        showSendLast(getString(R.string.testing), muted)
        thread(name = "vpn-pay-send-last") {
            val outcome: Pair<String, Int> = try {
                val r = SmsForwarder.post(url, secret, latest.sender, latest.body, latest.date)
                when {
                    r.error != null -> getString(R.string.test_error, r.error) to Color.rgb(190, 40, 40)
                    r.code == 401 -> getString(R.string.test_bad_key) to Color.rgb(190, 40, 40)
                    r.code in 200..299 -> getString(
                        R.string.send_last_sent,
                        r.result ?: "-",
                        getString(if (r.approved) R.string.yes_text else R.string.no_text)
                    ) to Color.rgb(22, 128, 61)
                    else -> getString(R.string.test_http, r.code) to Color.rgb(190, 40, 40)
                }
            } catch (e: Exception) {
                getString(R.string.test_error, e.javaClass.simpleName) to Color.rgb(190, 40, 40)
            }
            runOnUiThread {
                sendLastButton.isEnabled = true
                showSendLast(outcome.first, outcome.second)
            }
        }
    }

    private fun showSendLast(message: String, color: Int) {
        sendLastResult.text = message
        sendLastResult.setTextColor(color)
        sendLastResult.visibility = View.VISIBLE
    }

    // ───────── background service ─────────

    /** Android 13+ needs permission to show the “online” notification. */
    private fun requestNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
        }
    }

    // ───────── status ─────────

    private fun refreshStatus() {
        val granted = smsGranted()
        val on = AppSettings.enabled(this)
        if (masterToggle.checked != on) masterToggle.setChecked(on, animate = true)
        toggleLabel.text = getString(if (on) R.string.home_state_on else R.string.home_state_off)
        homeStatus.text = listOf(
            getString(R.string.status_sms, getString(if (granted) R.string.granted else R.string.required)),
            getString(R.string.status_forward, getString(if (on) R.string.on else R.string.off)),
            getString(R.string.status_service, getString(if (on) R.string.on else R.string.off)),
            getString(R.string.status_senders, AppSettings.allowedSenders(this).size)
        ).joinToString("\n")
        homeGrant.visibility = if (granted) View.GONE else View.VISIBLE
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
        setSingleLine(true)
        inputType = type
        setTextColor(ink)
        setHintTextColor(Color.rgb(150, 160, 175))
        background = rounded(pageBg, 10)
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
