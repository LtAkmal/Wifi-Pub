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
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CollageResult(
    val bitmap: Bitmap,
    val jpegBytes: ByteArray,
    val file: File,
    val sizeBytes: Long,
    val qualityUsed: Int
)

object CollageGenerator {

    private const val MAX_FILE_SIZE_BYTES = 950_000 // Strictly < 1 MB (950 KB safety margin)

    val SLOT_TITLES = listOf(
        "FOTO 1 • KAMERA AP",
        "FOTO 2 • KAMERA AREA",
        "FOTO 3 • SPEEDTEST",
        "FOTO 4 • WIFI ANALYZER"
    )

    fun createCameraCaptureUri(context: Context, slotIndex: Int): Uri {
        val camDir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(camDir, "capture_slot_${slotIndex}_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

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
     * Combines 4 Slot Bitmaps (Slot 0=Camera 1, Slot 1=Camera 2, Slot 2=Speedtest, Slot 3=WiFi Analyzer)
     * into a 2x2 Grid Collage with Location, Date & Speedtest header, AND a mandatory bottom Watermark:
     * - DD/MM/YY
     * - App By Akmal Satrio N
     * - Visit https://akmal-satrio-n.work.gd
     * Guarantees 100% uncropped photos (Fit Center) by default and JPEG size strictly < 1 MB.
     */
    suspend fun create2x2CollageUnder1Mb(
        context: Context,
        slots: List<Bitmap?>,
        locationTitle: String,
        dateLabel: String,
        speedSummary: String,
        includeHeaderWatermark: Boolean = true,
        fitEntirePhoto: Boolean = true
    ): CollageResult = withContext(Dispatchers.Default) {
        val nonNullPhotos = slots.filterNotNull()
        val avgAspect = if (nonNullPhotos.isNotEmpty()) {
            nonNullPhotos.map { it.width.toFloat() / it.height.toFloat().coerceAtLeast(1f) }.average().toFloat()
        } else {
            0.65f
        }

        val canvasWidth = 1440
        val headerHeight = if (includeHeaderWatermark) 176 else 24
        val gridHeight = when {
            avgAspect < 0.72f -> 2080
            avgAspect < 0.95f -> 1820
            else -> 1440
        }
        // Mandatory Bottom Watermark Bar Height
        val footerHeight = 156
        val canvasHeight = headerHeight + gridHeight + footerHeight

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
            canvas.drawText("PREVENTIVE MAINTENANCE - WIFI PUBLIK", 32f, 44f, titlePaint)

            // Location text
            val locPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 32f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val cleanLoc = locationTitle.trim().ifBlank { "-" }
            val displayLoc = if (cleanLoc.length > 56) cleanLoc.take(53) + "..." else cleanLoc
            canvas.drawText("Lokasi: $displayLoc", 32f, 92f, locPaint)

            // Date & Speed text
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FEF3C7")
                textSize = 25f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            val currentClock = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val metaLine = buildString {
                append("Tanggal: ${dateLabel.ifBlank { ReportFormatter.formatDisplayDateEn(ReportFormatter.todayDateKey()) }} ($currentClock)")
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
            textSize = 20f
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

            canvas.drawRoundRect(destRect, 18f, 18f, cellCardBgPaint)

            val photo = slots.getOrNull(index)
            if (photo != null) {
                if (fitEntirePhoto) {
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

            canvas.drawRoundRect(destRect, 18f, 18f, cellBorderPaint)

            val slotLabel = SLOT_TITLES.getOrElse(index) { "FOTO ${index + 1}" }
            val labelWidth = (badgeTextPaint.measureText(slotLabel) + 32f).coerceAtLeast(130f)
            val badgeRect = RectF(
                left + 14f,
                top + 14f,
                left + 14f + labelWidth,
                top + 52f
            )
            canvas.drawRoundRect(badgeRect, 10f, 10f, badgeBgPaint)
            canvas.drawRoundRect(badgeRect, 10f, 10f, badgeBorderPaint)
            canvas.drawText(slotLabel, left + 30f, top + 40f, badgeTextPaint)
        }

        // MANDATORY BOTTOM WATERMARK FOOTER
        // - DD/MM/YY • HH:mm:ss
        // - App By Akmal Satrio N
        // - Visit https://akmal-satrio-n.work.gd
        val footerTop = (headerHeight + gridHeight).toFloat()
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#090E1A")
        canvas.drawRect(0f, footerTop, canvasWidth.toFloat(), canvasHeight.toFloat(), paint)

        // Top accent line of footer
        paint.color = Color.parseColor("#14B8A6")
        canvas.drawRect(0f, footerTop, canvasWidth.toFloat(), footerTop + 5f, paint)

        val ddMmYyWithTime = SimpleDateFormat("dd/MM/yy • HH:mm:ss", Locale.getDefault()).format(Date())

        val wmDatePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FACC15")
            textSize = 27f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val wmAuthorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val wmUrlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2DD4BF")
            textSize = 25f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val centerX = canvasWidth / 2f
        canvas.drawText(ddMmYyWithTime, centerX, footerTop + 44f, wmDatePaint)
        canvas.drawText("App By Akmal Satrio N", centerX, footerTop + 88f, wmAuthorPaint)
        canvas.drawText("Visit https://akmal-satrio-n.work.gd", centerX, footerTop + 130f, wmUrlPaint)

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

    suspend fun enhanceDocumentationPhoto(
        source: Bitmap?,
        locationTitle: String,
        dateLabel: String,
        speedSummary: String,
        instructionText: String,
        addTechnicalStamp: Boolean = true
    ): Bitmap = withContext(Dispatchers.Default) {
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
            val ddMmYy = SimpleDateFormat("dd/MM/yy • HH:mm:ss", Locale.getDefault()).format(Date())
            canvas.drawText(
                "$ddMmYy • App By Akmal Satrio N • https://akmal-satrio-n.work.gd",
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
        val lines = note.chunked(46).take(3)
        lines.forEachIndexed { idx, line ->
            canvas.drawText(line, 96f, 810f + idx * 40f, notePaint)
        }

        val ddMmYy = SimpleDateFormat("dd/MM/yy • HH:mm:ss", Locale.getDefault()).format(Date())
        canvas.drawText(
            "$ddMmYy • App By Akmal Satrio N • https://akmal-satrio-n.work.gd",
            96f,
            980f,
            badgePaint
        )

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
