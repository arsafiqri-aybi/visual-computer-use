package com.arsafiqri.visualcomputeruse

import android.graphics.Bitmap
import kotlin.math.abs

class FrameDiffer(
    private val sampleSize: Int = 64,
    private val threshold: Double = 0.028
) {
    private var previous: IntArray? = null

    fun shouldSend(bitmap: Bitmap): Boolean {
        val sampled = Bitmap.createScaledBitmap(bitmap, sampleSize, sampleSize, true)
        val pixels = IntArray(sampleSize * sampleSize)
        sampled.getPixels(pixels, 0, sampleSize, 0, 0, sampleSize, sampleSize)
        sampled.recycle()

        val luminance = IntArray(pixels.size)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xff
            val g = (p shr 8) and 0xff
            val b = p and 0xff
            luminance[i] = (r * 30 + g * 59 + b * 11) / 100
        }

        val old = previous
        previous = luminance

        if (old == null) return true

        var delta = 0L
        for (i in luminance.indices) {
            delta += abs(luminance[i] - old[i])
        }

        val normalized = delta.toDouble() / (luminance.size * 255.0)
        return normalized >= threshold
    }
}
