package com.arsafiqri.visualcomputeruse

import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ObserverClient {
    private const val tag = "ObserverClient"

    fun postFrame(
        endpoint: String,
        sessionId: String,
        jpeg: ByteArray,
        width: Int,
        height: Int,
        capturedAt: Long
    ) {
        val connection = (URL("$endpoint/observe").openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 8_000
            connection.readTimeout = 45_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")

            val payload = JSONObject()
                .put("sessionId", sessionId)
                .put("capturedAt", capturedAt)
                .put("width", width)
                .put("height", height)
                .put("imageBase64", Base64.encodeToString(jpeg, Base64.NO_WRAP))
                .toString()

            connection.outputStream.use {
                it.write(payload.toByteArray(Charsets.UTF_8))
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (code !in 200..299) {
                Log.w(tag, "Observer returned HTTP $code: $body")
            } else {
                Log.d(tag, "Observation accepted: $body")
            }
        } catch (t: Throwable) {
            Log.w(tag, "Frame upload failed", t)
        } finally {
            connection.disconnect()
        }
    }
}
