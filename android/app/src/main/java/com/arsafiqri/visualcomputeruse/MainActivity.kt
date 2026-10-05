package com.arsafiqri.visualcomputeruse

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var projectionManager: MediaProjectionManager
    private lateinit var endpointEdit: EditText
    private lateinit var sessionEdit: EditText
    private lateinit var statusText: TextView

    private val requestCaptureCode = 4012

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        projectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val prefs = getSharedPreferences("observer", MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 56, 40, 40)
        }

        val title = TextView(this).apply {
            text = "Visual Computer Use"
            textSize = 26f
        }

        val subtitle = TextView(this).apply {
            text = "Read-only AI screen observer. Capture starts only after Android's consent dialog."
            textSize = 15f
            setPadding(0, 12, 0, 28)
        }

        endpointEdit = EditText(this).apply {
            hint = "Observer URL, e.g. http://192.168.1.20:8787"
            setText(prefs.getString("endpoint", "http://192.168.1.20:8787"))
            isSingleLine = true
        }

        sessionEdit = EditText(this).apply {
            hint = "Session ID"
            setText(prefs.getString("session", "my-phone"))
            isSingleLine = true
        }

        val start = Button(this).apply {
            text = "Start observing"
            setOnClickListener {
                val endpoint = endpointEdit.text.toString().trim().trimEnd('/')
                val session = sessionEdit.text.toString().trim()

                if (endpoint.isBlank() || session.isBlank()) {
                    statusText.text = "Endpoint and session ID are required."
                    return@setOnClickListener
                }

                prefs.edit()
                    .putString("endpoint", endpoint)
                    .putString("session", session)
                    .apply()

                statusText.text = "Waiting for Android screen-capture permission…"
                startActivityForResult(
                    projectionManager.createScreenCaptureIntent(),
                    requestCaptureCode
                )
            }
        }

        val stop = Button(this).apply {
            text = "Stop observing"
            setOnClickListener {
                stopService(Intent(this@MainActivity, ScreenCaptureService::class.java))
                statusText.text = "Observer stopped."
            }
        }

        statusText = TextView(this).apply {
            text = "Not observing."
            textSize = 15f
            setPadding(0, 28, 0, 0)
        }

        listOf(title, subtitle, endpointEdit, sessionEdit, start, stop, statusText).forEach {
            root.addView(
                it,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        setContentView(root)
    }

    @Deprecated("Kept for broad Android compatibility in this MVP.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != requestCaptureCode) return

        if (resultCode != RESULT_OK || data == null) {
            statusText.text = "Screen capture was not granted."
            return
        }

        val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
            putExtra(
                ScreenCaptureService.EXTRA_ENDPOINT,
                endpointEdit.text.toString().trim().trimEnd('/')
            )
            putExtra(
                ScreenCaptureService.EXTRA_SESSION,
                sessionEdit.text.toString().trim()
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        statusText.text = "Observing. A foreground notification stays visible while capture is active."
    }
}
