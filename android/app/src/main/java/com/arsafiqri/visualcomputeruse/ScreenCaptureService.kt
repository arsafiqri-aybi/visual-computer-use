package com.arsafiqri.visualcomputeruse

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

class ScreenCaptureService : Service() {

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_ENDPOINT = "endpoint"
        const val EXTRA_SESSION = "session"

        private const val channelId = "visual_observer"
        private const val notificationId = 9102
        private const val tag = "ScreenCaptureService"
    }

    private val worker = Executors.newSingleThreadExecutor()
    private val processing = AtomicBoolean(false)
    private val differ = FrameDiffer()

    private var projection: MediaProjection? = null
    private var imageReader: ImageReader? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startAsForeground()

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, ActivityResultCodes.CANCELED)
        val resultData = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        val endpoint = intent.getStringExtra(EXTRA_ENDPOINT).orEmpty()
        val sessionId = intent.getStringExtra(EXTRA_SESSION).orEmpty()

        if (resultCode != ActivityResultCodes.OK || resultData == null ||
            endpoint.isBlank() || sessionId.isBlank()
        ) {
            Log.w(tag, "Missing capture token or observer configuration.")
            stopSelf()
            return START_NOT_STICKY
        }

        if (projection != null) {
            return START_STICKY
        }

        val manager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        projection = manager.getMediaProjection(resultCode, resultData)

        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopSelf()
            }
        }, null)

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(
            width,
            height,
            PixelFormat.RGBA_8888,
            2
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            if (!processing.compareAndSet(false, true)) {
                reader.acquireLatestImage()?.close()
                return@setOnImageAvailableListener
            }

            val image = reader.acquireLatestImage()
            if (image == null) {
                processing.set(false)
                return@setOnImageAvailableListener
            }

            worker.execute {
                try {
                    val plane = image.planes[0]
                    val buffer = plane.buffer
                    val pixelStride = plane.pixelStride
                    val rowStride = plane.rowStride
                    val rowPadding = rowStride - pixelStride * width
                    val paddedWidth = width + rowPadding / pixelStride

                    val padded = Bitmap.createBitmap(
                        paddedWidth,
                        height,
                        Bitmap.Config.ARGB_8888
                    )
                    padded.copyPixelsFromBuffer(buffer)

                    val cropped = Bitmap.createBitmap(padded, 0, 0, width, height)
                    padded.recycle()

                    val targetWidth = minOf(720, cropped.width)
                    val scale = targetWidth.toDouble() / cropped.width.toDouble()
                    val targetHeight = (cropped.height * scale).roundToInt().coerceAtLeast(1)

                    val normalized =
                        if (targetWidth == cropped.width) cropped
                        else Bitmap.createScaledBitmap(
                            cropped,
                            targetWidth,
                            targetHeight,
                            true
                        )

                    if (normalized !== cropped) cropped.recycle()

                    if (differ.shouldSend(normalized)) {
                        val stream = ByteArrayOutputStream()
                        normalized.compress(Bitmap.CompressFormat.JPEG, 58, stream)

                        ObserverClient.postFrame(
                            endpoint = endpoint,
                            sessionId = sessionId,
                            jpeg = stream.toByteArray(),
                            width = normalized.width,
                            height = normalized.height,
                            capturedAt = System.currentTimeMillis()
                        )
                    }

                    normalized.recycle()
                } catch (t: Throwable) {
                    Log.w(tag, "Frame processing failed", t)
                } finally {
                    image.close()
                    processing.set(false)
                }
            }
        }, null)

        projection?.createVirtualDisplay(
            "VisualComputerUseCapture",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )

        return START_STICKY
    }

    private fun startAsForeground() {
        val manager = getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "Screen observer",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }

        val notification =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, channelId)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("Visual Computer Use")
                .setContentText("Screen observation is active")
                .setOngoing(true)
                .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(notificationId, notification)
        }
    }

    override fun onDestroy() {
        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
        imageReader = null

        projection?.stop()
        projection = null

        worker.shutdownNow()
        processing.set(false)

        super.onDestroy()
    }

    private object ActivityResultCodes {
        const val OK = -1
        const val CANCELED = 0
    }
}
