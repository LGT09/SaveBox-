package com.traxxiontech.savebox.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

data class ExtractedImageTheme(
    val primaryArgb: Long,
    val secondaryArgb: Long,
    val surfaceArgb: Long,
    val backgroundArgb: Long
)

object ImageColorExtractor {

    suspend fun saveBackgroundAndExtractTheme(
        context: Context,
        sourceUri: Uri
    ): Pair<String, ExtractedImageTheme>? = withContext(Dispatchers.IO) {
        val bgDir = File(context.filesDir, "backgrounds").apply {
            if (!exists()) mkdirs()
        }
        val destFile = File(bgDir, "custom_bg_${System.currentTimeMillis()}.jpg")
        // Clean previous custom background files
        bgDir.listFiles()?.forEach { old ->
            if (old.name.startsWith("custom_bg_")) old.delete()
        }

        val copied = runCatching {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            destFile.exists() && destFile.length() > 0L
        }.getOrDefault(false)

        if (!copied) return@withContext null

        val extracted = extractThemeFromFile(destFile)
        destFile.absolutePath to extracted
    }

    fun extractThemeFromFile(file: File): ExtractedImageTheme {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
        val maxDim = max(boundsOpts.outWidth, boundsOpts.outHeight).coerceAtLeast(1)
        val sampleSize = (maxDim / 96).coerceAtLeast(1)

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
            ?: return defaultExtractedTheme()

        return try {
            extractThemeFromBitmap(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    fun extractThemeFromBitmap(bitmap: Bitmap): ExtractedImageTheme {
        // Bucket colors by quantized 12-hue + saturation bins
        val hueCounts = IntArray(12)
        val hueRgbSums = Array(12) { LongArray(3) }
        var totalR = 0L
        var totalG = 0L
        var totalB = 0L
        var totalCount = 0L

        val hsv = FloatArray(3)
        val stepX = max(1, bitmap.width / 48)
        val stepY = max(1, bitmap.height / 48)

        for (y in 0 until bitmap.height step stepY) {
            for (x in 0 until bitmap.width step stepX) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = (pixel ushr 24) and 0xFF
                if (alpha < 128) continue

                val r = (pixel ushr 16) and 0xFF
                val g = (pixel ushr 8) and 0xFF
                val b = pixel and 0xFF
                totalR += r
                totalG += g
                totalB += b
                totalCount++

                android.graphics.Color.RGBToHSV(r, g, b, hsv)
                val sat = hsv[1]
                val value = hsv[2]
                // Prioritize chromatic, mid-bright tones for accent buckets
                if (sat >= 0.16f && value in 0.15f..0.95f) {
                    val bucket = ((hsv[0] / 30f).toInt()).coerceIn(0, 11)
                    hueCounts[bucket] += 1
                    hueRgbSums[bucket][0] += r
                    hueRgbSums[bucket][1] += g
                    hueRgbSums[bucket][2] += b
                }
            }
        }

        if (totalCount == 0L) return defaultExtractedTheme()

        val avgR = (totalR / totalCount).toInt().coerceIn(0, 255)
        val avgG = (totalG / totalCount).toInt().coerceIn(0, 255)
        val avgB = (totalB / totalCount).toInt().coerceIn(0, 255)
        val avgColorInt = android.graphics.Color.rgb(avgR, avgG, avgB)

        val sortedBuckets = hueCounts.indices
            .filter { hueCounts[it] > 0 }
            .sortedByDescending { hueCounts[it] }

        val dominantAccent = if (sortedBuckets.isNotEmpty()) {
            val idx = sortedBuckets[0]
            val c = hueCounts[idx]
            android.graphics.Color.rgb(
                (hueRgbSums[idx][0] / c).toInt(),
                (hueRgbSums[idx][1] / c).toInt(),
                (hueRgbSums[idx][2] / c).toInt()
            )
        } else {
            avgColorInt
        }

        val secondaryRaw = if (sortedBuckets.size >= 2) {
            // Pick a secondary bucket that isn't identical to primary
            val idx = sortedBuckets.drop(1).firstOrNull { candidate ->
                val diff = kotlin.math.abs(candidate - sortedBuckets[0])
                min(diff, 12 - diff) >= 2
            } ?: sortedBuckets[1]
            val c = hueCounts[idx]
            android.graphics.Color.rgb(
                (hueRgbSums[idx][0] / c).toInt(),
                (hueRgbSums[idx][1] / c).toInt(),
                (hueRgbSums[idx][2] / c).toInt()
            )
        } else {
            shiftHue(dominantAccent, 38f)
        }

        val primaryColor = shapeAccentForGlass(Color(dominantAccent), minVal = 0.56f, maxVal = 0.82f)
        val secondaryColor = shapeAccentForGlass(Color(secondaryRaw), minVal = 0.48f, maxVal = 0.74f)
        val deepBackground = shapeBackdropColor(Color(avgColorInt), targetValue = 0.08f)
        val deepSurface = shapeBackdropColor(Color(dominantAccent), targetValue = 0.14f)

        return ExtractedImageTheme(
            primaryArgb = primaryColor.toArgb().toLong() and 0xFFFFFFFFL,
            secondaryArgb = secondaryColor.toArgb().toLong() and 0xFFFFFFFFL,
            surfaceArgb = deepSurface.toArgb().toLong() and 0xFFFFFFFFL,
            backgroundArgb = deepBackground.toArgb().toLong() and 0xFFFFFFFFL
        )
    }

    private fun shapeAccentForGlass(color: Color, minVal: Float, maxVal: Float): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        hsv[1] = hsv[1].coerceIn(0.28f, 0.76f)
        hsv[2] = hsv[2].coerceIn(minVal, maxVal)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    private fun shapeBackdropColor(color: Color, targetValue: Float): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        hsv[1] = hsv[1].coerceIn(0.18f, 0.48f)
        hsv[2] = targetValue
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    private fun shiftHue(rgb: Int, deltaDeg: Float): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(rgb, hsv)
        hsv[0] = (hsv[0] + deltaDeg) % 360f
        return android.graphics.Color.HSVToColor(hsv)
    }

    private fun defaultExtractedTheme(): ExtractedImageTheme {
        return ExtractedImageTheme(
            primaryArgb = 0xFF4A78A8L,
            secondaryArgb = 0xFF566885L,
            surfaceArgb = 0xFF101722L,
            backgroundArgb = 0xFF070B12L
        )
    }
}
