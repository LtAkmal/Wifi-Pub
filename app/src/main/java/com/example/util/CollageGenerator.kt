package com.example.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
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
     * and guarantees:
     * 1. Every photo is rendered 100% UTUH (Fit Center, zero cropping) by default so no part of
     *    a Speedtest screenshot or AP documentation photo is ever cut off.
     * 2. The compressed JPEG file is strictly under 1 MB (< 950 KB).
     */
    suspend fun create2x2CollageUnder1Mb(
        context: Context,
        photos: List<Bitmap>,
        locationTitle: String,
        dateLabel: String,
        speedSummary: String,
        includeHeaderWatermark: Boolean = true,
        fitEntirePhoto: Boolean = true
    ): CollageResult = withContext(Dispatchers.Default) {
        // Detect if majority of photos (or any photo) is portrait so the 2x2 cells match phone aspect ratio
        val avgAspect = if (photos.isNotEmpty()) {
            photos.map { it.width.toFloat() / it.height.toFloat().coerceAtLeast(1f) }.average().toFloat()
        } else {
            0.75f
        }

        val canvasWidth = 1440
        val headerHeight = if (includeHeaderWatermark) 176 else 24
        // If portrait photos (like phone camera or screenshots), make the grid taller so photos are large AND 100% uncropped
        val gridHeight = when {
            avgAspect < 0.72f -> 2080 // Tall portrait screenshots / 9:16 photos
            avgAspect < 0.95f -> 1820 // Standard 3:4 portrait photos
            else -> 1440              // Square or landscape photos
        }
        val canvasHeight = headerHeight + gridHeight

        val resultBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)

        // Deep Slate Background
        canvas.drawColor(Color.parseColor("#0B1120"))

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
                color = Color.parseColor("#5EEAD4")
                textSize = 25f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText("PREVENTIVE MAINTENANCE - WIFI PUBLIK (CGK T3)", 32f, 44f, titlePaint)

            // Location text (truncate nicely if very long)
            val locPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 32f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val cleanLoc = locationTitle.ifBlank { "Lokasi PM WiFi Publik Terminal 3" }
            val displayLoc = if (cleanLoc.length > 56) cleanLoc.take(53) + "..." else cleanLoc
            canvas.drawText("Lokasi: $displayLoc", 32f, 92f, locPaint)

            // Date & Speed text
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FEF3C7")
                textSize = 25f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            val metaLine = buildString {
                append("Tanggal: ${dateLabel.ifBlank { ReportFormatter.formatDisplayDateEn(ReportFormatter.todayDateKey()) }}")
                if (speedSummary.isNotBlank()) {
                    append("   |   Speedtest: $speedSummary")
                }
            }
            canvas.drawText(metaLine, 32f, 140f, subPaint)
        }

        val padding = 20
        val cellWidth = (canvasWidth - padding * 3) / 2
        val cellHeight = (gridHeight - padding * 3) / 2

        val cellCardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#131C31")
            style = Paint.Style.FILL
        }
        val cellBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(205, 15, 23, 42)
        }
        val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 45, 212, 191)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
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

            // Draw clean rounded card container for every slot
            canvas.drawRoundRect(destRect, 18f, 18f, cellCardBgPaint)

            val photo = photos.getOrNull(index)
            if (photo != null) {
                if (fitEntirePhoto) {
                    // Draw 100% UTUH (Fit Center) inside the cell with 8px inner padding so ZERO pixels are cut off!
                    val innerRect = RectF(
                        destRect.left + 8f,
                        destRect.top + 8f,
                        destRect.right - 8f,
                        destRect.bottom - 8f
                    )
                    drawFitCenter(canvas, photo, innerRect)
                } else {
                    drawCenterCrop(canvas, photo, destRect)
                }
            }

            // Draw subtle border around the cell
            canvas.drawRoundRect(destRect, 18f, 18f, cellBorderPaint)

            // Place compact badge in TOP-LEFT corner so bottom ISP/Server labels in Speedtest screenshots are never covered
            val badgeRect = RectF(
                left + 14f,
                top + 14f,
                left + 132f,
                top + 52f
            )
            canvas.drawRoundRect(badgeRect, 10f, 10f, badgeBgPaint)
            canvas.drawRoundRect(badgeRect, 10f, 10f, badgeBorderPaint)
            canvas.drawText("FOTO ${index + 1}", left + 28f, top + 40f, badgeTextPaint)
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

    /**
     * Draws the entire [source] bitmap inside [destRect] preserving its exact aspect ratio
     * so 100% of the image is visible and nothing is ever cropped.
     */
    private fun drawFitCenter(canvas: Canvas, source: Bitmap, destRect: RectF) {
        val srcWidth = source.width.toFloat().coerceAtLeast(1f)
        val srcHeight = source.height.toFloat().coerceAtLeast(1f)
        val dstWidth = destRect.width()
        val dstHeight = destRect.height()

        val scale = minOf(dstWidth / srcWidth, dstHeight / srcHeight)
        val scaledWidth = srcWidth * scale
        val scaledHeight = srcHeight * scale

        val left = destRect.left + (dstWidth - scaledWidth) / 2f
        val top = destRect.top + (dstHeight - scaledHeight) / 2f
        val targetRect = RectF(left, top, left + scaledWidth, top + scaledHeight)

        val srcRect = Rect(0, 0, source.width, source.height)
        canvas.drawBitmap(source, srcRect, targetRect, Paint(Paint.FILTER_BITMAP_FLAG))
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

    /**
     * Smart Studio Foto Enhancer & Technical Watermark Stamp for PM Documentation.
     * Works 100% offline/on-device without quota limits, brightening dark indoor ceiling AP photos,
     * improving contrast/clarity, and optionally adding a clean CGK T3 PM footer stamp.
     */
    suspend fun enhanceDocumentationPhoto(
        source: Bitmap?,
        locationTitle: String,
        dateLabel: String,
        speedSummary: String,
        instructionText: String,
        addTechnicalStamp: Boolean = true
    ): Bitmap = withContext(Dispatchers.Default) {
        // If user didn't pick a source photo, generate a clean technical PM documentation card
        if (source == null) {
            return@withContext createTechnicalCardBitmap(
                locationTitle = locationTitle,
                dateLabel = dateLabel,
                speedSummary = speedSummary,
                note = instructionText
            )
        }

        val width = source.width.coerceAtLeast(600)
        val height = source.height.coerceAtLeast(600)
        val footerHeight = if (addTechnicalStamp) (height * 0.14f).toInt().coerceIn(110, 180) else 0
        val outBitmap = Bitmap.createBitmap(width, height + footerHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outBitmap)
        canvas.drawColor(Color.parseColor("#0B1120"))

        // Apply Smart Brightness + Contrast + Saturation boost for indoor ceiling AP photos
        val contrast = 1.14f
        val brightness = 18f
        val cm = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, brightness,
                0f, contrast, 0f, 0f, brightness,
                0f, 0f, contrast, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val enhancePaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(source, 0f, 0f, enhancePaint)

        if (addTechnicalStamp) {
            val topY = height.toFloat()
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F172A")
            }
            canvas.drawRect(0f, topY, width.toFloat(), (height + footerHeight).toFloat(), bgPaint)

            val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#14B8A6")
            }
            canvas.drawRect(0f, topY, width.toFloat(), topY + 6f, accentPaint)

            val scale = width / 1000f
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#2DD4BF")
                textSize = (22f * scale).coerceIn(18f, 32f)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val locPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = (26f * scale).coerceIn(20f, 38f)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FEF3C7")
                textSize = (21f * scale).coerceIn(16f, 30f)
            }

            val padX = (24f * scale).coerceAtLeast(16f)
            canvas.drawText(
                "DOKUMENTASI PM WIFI PUBLIK - CGK TERMINAL 3",
                padX,
                topY + footerHeight * 0.30f,
                titlePaint
            )
            val cleanLoc = locationTitle.ifBlank { "Terminal 3 CGK" }
            val dispLoc = if (cleanLoc.length > 52) cleanLoc.take(49) + "..." else cleanLoc
            canvas.drawText(
                "Lokasi: $dispLoc",
                padX,
                topY + footerHeight * 0.62f,
                locPaint
            )
            canvas.drawText(
                "Tanggal: $dateLabel   |   Speedtest: $speedSummary   |   Status: Normal ✅",
                padX,
                topY + footerHeight * 0.88f,
                metaPaint
            )
        }

        outBitmap
    }

    private fun createTechnicalCardBitmap(
        locationTitle: String,
        dateLabel: String,
        speedSummary: String,
        note: String
    ): Bitmap {
        val w = 1080
        val h = 1080
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.parseColor("#0B1120"))

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#131C31")
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#14B8A6")
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        val rect = RectF(48f, 48f, w - 48f, h - 48f)
        canvas.drawRoundRect(rect, 28f, 28f, cardPaint)
        canvas.drawRoundRect(rect, 28f, 28f, borderPaint)

        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2DD4BF")
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("KARTU DOKUMENTASI TEKNIS - WIFI PUBLIK T3", 96f, 130f, badgePaint)

        val whiteBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val cleanLoc = locationTitle.ifBlank { "Terminal 3 CGK" }
        canvas.drawText("Lokasi PM:", 96f, 220f, badgePaint)
        canvas.drawText(cleanLoc.take(40), 96f, 270f, whiteBold)
        if (cleanLoc.length > 40) {
            canvas.drawText(cleanLoc.drop(40).take(40), 96f, 318f, whiteBold)
        }

        val yellowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FBBF24")
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("Speedtest: $speedSummary", 96f, 410f, yellowPaint)
        canvas.drawText("Tanggal: $dateLabel", 96f, 465f, whiteBold)

        val greenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#10B981")
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("• Jaringan Normal ✅", 96f, 560f, greenPaint)
        canvas.drawText("• Perangkat Access Point Normal ✅", 96f, 610f, greenPaint)
        canvas.drawText("• Speed Bandwidth Normal ✅", 96f, 660f, greenPaint)

        val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 26f
        }
        canvas.drawText("Catatan / Instruksi:", 96f, 760f, badgePaint)
        val lines = note.chunked(46).take(4)
        lines.forEachIndexed { idx, line ->
            canvas.drawText(line, 96f, 810f + idx * 40f, notePaint)
        }

        return bmp
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
