package com.example.vpnpay

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Sends one SMS to the server the same way the live receiver does, and reads the server's answer. */
object SmsForwarder {
    data class Result(val code: Int, val result: String?, val approved: Boolean, val error: String?)

    fun post(serverUrl: String, secret: String, sender: String, body: String, timestampMillis: Long): Result {
        var connection: HttpURLConnection? = null
        return try {
            val payload = JSONObject()
                .put("sender", sender)
                .put("body", body)
                .put("timestamp", isoUtc(timestampMillis))
                .toString()
                .toByteArray(Charsets.UTF_8)
            connection = URL(serverUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("X-Secret", secret)
            connection.outputStream.use { it.write(payload) }
            val code = connection.responseCode
            val text = try {
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                stream?.bufferedReader()?.use { it.readText() } ?: ""
            } catch (e: Exception) {
                ""
            }
            val json = try { JSONObject(text) } catch (e: Exception) { null }
            Result(
                code = code,
                result = json?.optString("result")?.takeIf { it.isNotBlank() },
                approved = json?.optBoolean("approved", false) ?: false,
                error = null
            )
        } catch (e: Exception) {
            Result(-1, null, false, e.javaClass.simpleName)
        } finally {
            connection?.disconnect()
        }
    }

    private fun isoUtc(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(millis))
}
