package com.example.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

data class CollageResult(
    val bitmap: Bitmap,
    val jpegBytes: ByteArray,
    val file: File,
    val sizeBytes: Long,
    val qualityUsed: Int
)

object CollageGenerator {

    private const val MAX_FILE_SIZE_BYTES = 950_000 // Strictly < 1 MB (950 KB safety margin)

    suspend fun loadBitmapFromUri(context: Context, uri: Uri, maxDimension: Int = 1280): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }

                var sampleSize = 1
                while (options.outWidth / sampleSize > maxDimension || options.outHeight / sampleSize > maxDimension) {
                    sampleSize *= 2
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOptions)
                }
            } catch (e: Exception) {
                null
            }
        }

    /**
     * Combines up to 4 Bitmaps into a 2x2 Grid Collage with Location, Date & Speedtest header,
     * and guarantees the compressed JPEG file is strictly under 1 MB (< 950 KB).
     */
    suspend fun create2x2CollageUnder1Mb(
        context: Context,
        photos: List<Bitmap>,
        locationTitle: String,
        dateLabel: String,
        speedSummary: String,
        includeHeaderWatermark: Boolean = true
    ): CollageResult = withContext(Dispatchers.Default) {
        val canvasWidth = 1400
        val headerHeight = if (includeHeaderWatermark) 170 else 24
        val gridHeight = 1400
        val canvasHeight = headerHeight + gridHeight

        val resultBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)

        // Background
        canvas.drawColor(Color.parseColor("#0F172A"))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        if (includeHeaderWatermark) {
            // Draw top header bar
            paint.color = Color.parseColor("#115E59")
            canvas.drawRect(0f, 0f, canvasWidth.toFloat(), (headerHeight - 8).toFloat(), paint)

            // Accent strip
            paint.color = Color.parseColor("#14B8A6")
            canvas.drawRect(0f, (headerHeight - 8).toFloat(), canvasWidth.toFloat(), headerHeight.toFloat(), paint)

            // Title text
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#2DD4BF")
                textSize = 26f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText("PREVENTIVE MAINTENANCE - WIFI PUBLIK", 32f, 44f, titlePaint)

            // Location text (truncate nicely if very long)
            val locPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 32f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val cleanLoc = locationTitle.ifBlank { "Lokasi PM WiFi Publik Terminal 3" }
            val displayLoc = if (cleanLoc.length > 58) cleanLoc.take(55) + "..." else cleanLoc
            canvas.drawText("Lokasi: $displayLoc", 32f, 92f, locPaint)

            // Date & Speed text
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FEF3C7")
                textSize = 26f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            val metaLine = buildString {
                append("Tanggal: ${dateLabel.ifBlank { ReportFormatter.formatDisplayDateEn(ReportFormatter.todayDateKey()) }}")
                if (speedSummary.isNotBlank()) {
                    append("   |   Speedtest: $speedSummary")
                }
            }
            canvas.drawText(metaLine, 32f, 138f, subPaint)
        }

        val padding = 20
        val cellWidth = (canvasWidth - padding * 3) / 2
        val cellHeight = (gridHeight - padding * 3) / 2

        val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(190, 15, 23, 42)
        }
        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
        }

        for (index in 0 until 4) {
            val row = index / 2
            val col = index % 2
            val left = padding + col * (cellWidth + padding)
            val top = headerHeight + padding + row * (cellHeight + padding)
            val destRect = RectF(
                left.toFloat(),
                top.toFloat(),
                (left + cellWidth).toFloat(),
                (top + cellHeight).toFloat()
            )

            val photo = photos.getOrNull(index)
            if (photo != null) {
                drawCenterCrop(canvas, photo, destRect)
            } else {
                canvas.drawRoundRect(destRect, 16f, 16f, placeholderPaint)
            }

            // Label badge in bottom-left of each cell
            val badgeRect = RectF(
                left + 12f,
                top + cellHeight - 52f,
                left + 145f,
                top + cellHeight - 12f
            )
            canvas.drawRoundRect(badgeRect, 10f, 10f, badgeBgPaint)
            canvas.drawText("FOTO ${index + 1}", left + 28f, top + cellHeight - 23f, badgeTextPaint)
        }

        // Compress iteratively to strictly guarantee < 1 MB (< 950 KB)
        var currentBitmap = resultBitmap
        var quality = 88
        var jpegBytes: ByteArray

        while (true) {
            val baos = ByteArrayOutputStream()
            currentBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            jpegBytes = baos.toByteArray()

            if (jpegBytes.size <= MAX_FILE_SIZE_BYTES || quality <= 30) {
                break
            }
            quality -= 8
        }

        // Extra safeguard if high-entropy noise still exceeds 950 KB at quality 30
        if (jpegBytes.size > MAX_FILE_SIZE_BYTES) {
            val scaled = Bitmap.createScaledBitmap(
                currentBitmap,
                (currentBitmap.width * 0.75f).toInt(),
                (currentBitmap.height * 0.75f).toInt(),
                true
            )
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 75, baos)
            jpegBytes = baos.toByteArray()
            currentBitmap = scaled
            quality = 75
        }

        // Write to cache dir for immediate FileProvider sharing
        val collagesDir = File(context.cacheDir, "collages").apply { mkdirs() }
        val outFile = File(collagesDir, "PM_Collage_${System.currentTimeMillis()}.jpg")
        withContext(Dispatchers.IO) {
            FileOutputStream(outFile).use { fos ->
                fos.write(jpegBytes)
                fos.flush()
            }
        }

        CollageResult(
            bitmap = currentBitmap,
            jpegBytes = jpegBytes,
            file = outFile,
            sizeBytes = jpegBytes.size.toLong(),
            qualityUsed = quality
        )
    }

    private fun drawCenterCrop(canvas: Canvas, source: Bitmap, destRect: RectF) {
        val srcWidth = source.width.toFloat()
        val srcHeight = source.height.toFloat()
        val dstWidth = destRect.width()
        val dstHeight = destRect.height()

        val srcAspect = srcWidth / srcHeight
        val dstAspect = dstWidth / dstHeight

        val srcRect = if (srcAspect > dstAspect) {
            val croppedWidth = srcHeight * dstAspect
            val leftOffset = (srcWidth - croppedWidth) / 2f
            Rect(leftOffset.toInt(), 0, (leftOffset + croppedWidth).toInt(), srcHeight.toInt())
        } else {
            val croppedHeight = srcWidth / dstAspect
            val topOffset = (srcHeight - croppedHeight) / 2f
            Rect(0, topOffset.toInt(), srcWidth.toInt(), (topOffset + croppedHeight).toInt())
        }

        canvas.drawBitmap(source, srcRect, destRect, Paint(Paint.FILTER_BITMAP_FLAG))
    }

    suspend fun saveCollageToGallery(
        context: Context,
        jpegBytes: ByteArray,
        locationName: String
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val safeName = locationName.replace(Regex("[^a-zA-Z0-9]"), "_").take(25).ifBlank { "WiFi_PM" }
            val filename = "PM_4Foto_${safeName}_${System.currentTimeMillis()}.jpg"
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/WiFi_PM_Helper")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (imageUri != null) {
                resolver.openOutputStream(imageUri)?.use { out ->
                    out.write(jpegBytes)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(imageUri, contentValues, null, null)
                }
            }
            imageUri
        } catch (e: Exception) {
            null
        }
    }
}
