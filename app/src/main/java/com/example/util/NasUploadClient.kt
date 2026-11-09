package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import com.example.BuildConfig
import com.example.data.CorrectiveMaintenanceEntity
import com.example.data.PmReportEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

sealed interface NasUploadResult {
    data class Success(
        val reportId: String,
        val updated: Boolean,
        val photosCount: Int
    ) : NasUploadResult

    data class Failure(
        val errorMessage: String,
        val httpCode: Int = 0
    ) : NasUploadResult
}

object NasUploadClient {

    private const val MAX_SINGLE_PHOTO_BYTES = 20L * 1024L * 1024L // 20 MB per photo

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun getUploadDirForClientRef(context: Context, clientRef: String): File {
        return File(File(context.filesDir, "pm_uploads"), clientRef)
    }

    fun deleteUploadDirForClientRef(context: Context, clientRef: String?) {
        if (clientRef.isNullOrBlank()) return
        try {
            val dir = getUploadDirForClientRef(context, clientRef)
            if (dir.exists()) {
                dir.deleteRecursively()
            }
        } catch (_: Exception) {
        }
    }

    fun hasSavedPhotosForClientRef(context: Context, clientRef: String?): Boolean {
        if (clientRef.isNullOrBlank()) return false
        val dir = getUploadDirForClientRef(context, clientRef)
        if (!dir.exists()) return false
        return (1..4).any { idx ->
            val f = File(dir, "foto$idx.jpg")
            f.exists() && f.length() > 0L
        }
    }

    fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Saves the 4 individual slot photos (NOT the collage) + 480px thumbnails + metadata snapshot
     * into `filesDir/pm_uploads/<clientRef>/foto1.jpg..foto4.jpg` and `thumb1.jpg..thumb4.jpg`.
     *
     * - Slots 0 & 1 (Camera AP & Camera Area): copies the full-resolution original file from URI/File
     *   if available and <= 20 MB; otherwise compresses the slot bitmap at JPEG quality 92.
     * - Slots 2 & 3 (Speedtest & WiFi Analyzer): compresses the slot bitmap at JPEG quality 92.
     * - Thumbnails (`thumb1.jpg..thumb4.jpg`): longest side 480 px, JPEG quality 80.
     */
    suspend fun saveSlotsForNasUpload(
        context: Context,
        clientRef: String,
        slotBitmaps: List<Bitmap?>,
        slotOriginalUris: List<Uri?>,
        slotOriginalFiles: List<File?>,
        pingMs: Int?,
        shiftCode: String,
        saveTimestampMs: Long,
        gpsFix: GpsFix? = null
    ): Int = withContext(Dispatchers.IO) {
        val dir = getUploadDirForClientRef(context, clientRef)
        if (!dir.exists()) {
            dir.mkdirs()
        }

        // Save extra metadata so future retries preserve exact time, shift, pingMs, and gps
        try {
            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(saveTimestampMs))
            val extraJson = JSONObject().apply {
                put("time", timeStr)
                put("shift", shiftCode)
                if (pingMs != null && pingMs > 0) {
                    put("pingMs", pingMs)
                }
                if (gpsFix != null) {
                    put(
                        "gps",
                        JSONObject().apply {
                            put("lat", gpsFix.lat)
                            put("lon", gpsFix.lon)
                            if (gpsFix.accuracyM != null) {
                                put("accuracyM", gpsFix.accuracyM.roundToInt())
                            }
                        }
                    )
                }
            }
            File(dir, "meta_extra.json").writeText(extraJson.toString())
        } catch (_: Exception) {
        }

        var savedCount = 0
        for (slotIdx in 0..3) {
            val slotNum = slotIdx + 1
            val bitmap = slotBitmaps.getOrNull(slotIdx)
            val origUri = slotOriginalUris.getOrNull(slotIdx)
            val origFile = slotOriginalFiles.getOrNull(slotIdx)

            if (bitmap == null && origUri == null && (origFile == null || !origFile.exists())) {
                continue
            }

            val destPhotoFile = File(dir, "foto$slotNum.jpg")
            val destThumbFile = File(dir, "thumb$slotNum.jpg")

            var copiedFullRes = false
            // For Foto 1 (slot 0) and Foto 2 (slot 1), prefer copying the full-resolution original camera file
            if (slotIdx == 0 || slotIdx == 1) {
                if (origFile != null && origFile.exists() && origFile.length() in 1024..MAX_SINGLE_PHOTO_BYTES) {
                    try {
                        origFile.copyTo(destPhotoFile, overwrite = true)
                        copiedFullRes = destPhotoFile.exists() && destPhotoFile.length() > 0L
                    } catch (_: Exception) {
                        copiedFullRes = false
                    }
                }
                if (!copiedFullRes && origUri != null) {
                    try {
                        context.contentResolver.openInputStream(origUri)?.use { input ->
                            FileOutputStream(destPhotoFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (destPhotoFile.exists() && destPhotoFile.length() in 1024..MAX_SINGLE_PHOTO_BYTES) {
                            copiedFullRes = true
                        } else {
                            destPhotoFile.delete()
                        }
                    } catch (_: Exception) {
                        copiedFullRes = false
                    }
                }
            }

            // Fallback (or standard path for Slot 2 Speedtest & Slot 3 WiFi Analyzer): compress bitmap at quality 92
            if (!copiedFullRes && bitmap != null) {
                try {
                    FileOutputStream(destPhotoFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    }
                } catch (_: Exception) {
                }
            }

            if (destPhotoFile.exists() && destPhotoFile.length() > 0L) {
                savedCount++
                // Create thumbnail (longest side 480 px, JPEG quality 80)
                val decodedFromFile = if (bitmap == null) decodeSampledBitmapFromFile(destPhotoFile, 480) else null
                val sourceForThumb = bitmap ?: decodedFromFile
                if (sourceForThumb != null) {
                    saveThumbnail480(sourceForThumb, destThumbFile)
                }
                decodedFromFile?.recycle()
            }
        }
        savedCount
    }

    private fun saveThumbnail480(source: Bitmap, destThumbFile: File) {
        try {
            val w = source.width.coerceAtLeast(1)
            val h = source.height.coerceAtLeast(1)
            val longest = max(w, h)
            val scaled = if (longest > 480) {
                val ratio = 480f / longest.toFloat()
                val targetW = (w * ratio).roundToInt().coerceAtLeast(1)
                val targetH = (h * ratio).roundToInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(source, targetW, targetH, true)
            } else {
                source
            }
            FileOutputStream(destThumbFile).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            if (scaled !== source) {
                scaled.recycle()
            }
        } catch (_: Exception) {
        }
    }

    private fun decodeSampledBitmapFromFile(file: File, maxDim: Int): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            var sample = 1
            while (opts.outWidth / sample > maxDim * 2 || opts.outHeight / sample > maxDim * 2) {
                sample *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Tests NAS connection via `GET {URL}/api/auth/check` with `Authorization: Bearer <token>`.
     * NEVER logs the token.
     */
    suspend fun testConnection(
        serverUrl: String,
        token: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanUrl = serverUrl.trim().trimEnd('/')
        val cleanToken = token.trim()
        if (cleanUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("URL server tidak boleh kosong."))
        }
        if (cleanToken.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Token upload belum diisi."))
        }

        val endpoint = "$cleanUrl/api/auth/check"
        try {
            val request = Request.Builder()
                .url(endpoint)
                .get()
                .header("Authorization", "Bearer $cleanToken")
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                when (response.code) {
                    200 -> Result.success("Token valid, server terhubung")
                    401 -> Result.failure(Exception("Token salah"))
                    else -> {
                        val serverErr = try {
                            JSONObject(bodyStr).optString("error").takeIf { it.isNotBlank() }
                        } catch (_: Exception) {
                            null
                        }
                        val msg = serverErr ?: "HTTP ${response.code}: ${response.message.ifBlank { "Gagal menghubungi server" }}"
                        Result.failure(Exception(msg))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Gagal terhubung: ${e.localizedMessage ?: "Periksa koneksi internet / URL server"}"))
        }
    }

    /**
     * Sends `POST {URL}/api/reports` with `Authorization: Bearer <token>` and `multipart/form-data`:
     * - `meta`: JSON string
     * - `photo1`..`photo4`: full-resolution / quality-92 JPEG per non-empty slot
     * - `thumb1`..`thumb4`: 480px quality-80 JPEG thumbnail per non-empty slot
     */
    suspend fun uploadPmReportToNas(
        context: Context,
        serverUrl: String,
        token: String,
        report: PmReportEntity,
        clientRef: String,
        fallbackShiftLabel: String,
        matchingCm: CorrectiveMaintenanceEntity?,
        fallbackPingMs: Int?
    ): NasUploadResult = withContext(Dispatchers.IO) {
        val cleanUrl = serverUrl.trim().trimEnd('/')
        val cleanToken = token.trim()
        if (cleanUrl.isBlank() || cleanToken.isBlank()) {
            return@withContext NasUploadResult.Failure("URL atau Token NAS belum diatur.")
        }

        val uploadDir = getUploadDirForClientRef(context, clientRef)
        if (!uploadDir.exists()) {
            return@withContext NasUploadResult.Failure("File foto untuk PM ini tidak ditemukan di penyimpanan lokal.")
        }

        // Read saved extra metadata (time, shift, pingMs, gps) if present
        var savedTimeStr: String? = null
        var savedShiftCode: String? = null
        var savedPingMs: Int? = fallbackPingMs
        var savedGpsFix: GpsFix? = null
        val extraFile = File(uploadDir, "meta_extra.json")
        if (extraFile.exists()) {
            try {
                val extraJson = JSONObject(extraFile.readText())
                savedTimeStr = extraJson.optString("time").takeIf { it.isNotBlank() }
                savedShiftCode = extraJson.optString("shift").takeIf { it.isNotBlank() }
                if (extraJson.has("pingMs")) {
                    savedPingMs = extraJson.optInt("pingMs").takeIf { it > 0 }
                }
                val gpsObj = extraJson.optJSONObject("gps")
                if (gpsObj != null && gpsObj.has("lat") && gpsObj.has("lon")) {
                    val lat = gpsObj.optDouble("lat", Double.NaN)
                    val lon = gpsObj.optDouble("lon", Double.NaN)
                    val acc = if (gpsObj.has("accuracyM")) gpsObj.optDouble("accuracyM", Double.NaN) else Double.NaN
                    if (!lat.isNaN() && !lon.isNaN()) {
                        savedGpsFix = GpsFix(
                            lat = lat,
                            lon = lon,
                            accuracyM = if (!acc.isNaN()) acc.toFloat() else null
                        )
                    }
                }
            } catch (_: Exception) {
            }
        }

        val timeFormatted = savedTimeStr
            ?: SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(report.timestamp))

        val shiftCode = savedShiftCode ?: if (
            fallbackShiftLabel.contains("Malam", ignoreCase = true) ||
            fallbackShiftLabel.contains("SH2", ignoreCase = true)
        ) {
            "SH2"
        } else {
            "SH1"
        }

        val finalGpsLat = report.gpsLat ?: savedGpsFix?.lat
        val finalGpsLon = report.gpsLon ?: savedGpsFix?.lon
        val finalGpsAcc = report.gpsAccuracyM ?: savedGpsFix?.accuracyM

        // Build `meta` JSON strictly following server specification
        val metaJson = JSONObject().apply {
            put("clientRef", clientRef)
            put("date", report.dateKey)
            put("time", timeFormatted)
            put("shift", shiftCode)
            put("location", report.location)
            put("equipment", report.equipmentName)
            put("downloadMbps", report.downloadMbps)
            put("uploadMbps", report.uploadMbps)
            if (savedPingMs != null && savedPingMs > 0) {
                put("pingMs", savedPingMs)
            }
            put(
                "status",
                JSONObject().apply {
                    put("network", report.statusNetwork)
                    put("device", report.statusDevice)
                    put("speed", report.statusSpeed)
                }
            )
            if (matchingCm != null) {
                put(
                    "cm",
                    JSONObject().apply {
                        put("kendala", matchingCm.kendala)
                        put("issue", matchingCm.issue)
                        put("action", matchingCm.actionLines)
                        put("status", matchingCm.statusLines)
                    }
                )
            }
            if (finalGpsLat != null && finalGpsLon != null) {
                put(
                    "gps",
                    JSONObject().apply {
                        put("lat", finalGpsLat)
                        put("lon", finalGpsLon)
                        if (finalGpsAcc != null) {
                            put("accuracyM", finalGpsAcc.roundToInt())
                        }
                    }
                )
            }
            put("appVersion", BuildConfig.VERSION_NAME)
        }

        val jpegMediaType = "image/jpeg".toMediaType()
        val multipartBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("meta", metaJson.toString())

        var attachedPhotoCount = 0
        for (slotNum in 1..4) {
            val photoFile = File(uploadDir, "foto$slotNum.jpg")
            if (photoFile.exists() && photoFile.length() > 0L) {
                val thumbFile = File(uploadDir, "thumb$slotNum.jpg")
                if (!thumbFile.exists() || thumbFile.length() == 0L) {
                    val decoded = decodeSampledBitmapFromFile(photoFile, 480)
                    if (decoded != null) {
                        saveThumbnail480(decoded, thumbFile)
                    }
                }
                multipartBuilder.addFormDataPart(
                    "photo$slotNum",
                    "foto$slotNum.jpg",
                    photoFile.asRequestBody(jpegMediaType)
                )
                if (thumbFile.exists() && thumbFile.length() > 0L) {
                    multipartBuilder.addFormDataPart(
                        "thumb$slotNum",
                        "thumb$slotNum.jpg",
                        thumbFile.asRequestBody(jpegMediaType)
                    )
                }
                attachedPhotoCount++
            }
        }

        if (attachedPhotoCount == 0) {
            return@withContext NasUploadResult.Failure("Minimal 1 foto slot harus terisi untuk upload ke NAS.")
        }

        val endpoint = "$cleanUrl/api/reports"
        try {
            val request = Request.Builder()
                .url(endpoint)
                .post(multipartBuilder.build())
                .header("Authorization", "Bearer $cleanToken")
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                val respBody = response.body?.string().orEmpty()
                if (response.code == 200 || response.code == 201) {
                    val json = try {
                        JSONObject(respBody)
                    } catch (_: Exception) {
                        JSONObject()
                    }
                    val reportId = json.optString("id", "PM-OK").ifBlank { "PM-OK" }
                    val updated = json.optBoolean("updated", response.code == 200)
                    val photos = json.optInt("photos", attachedPhotoCount)

                    // Delete local copy folder after successful upload
                    deleteUploadDirForClientRef(context, clientRef)

                    return@withContext NasUploadResult.Success(
                        reportId = reportId,
                        updated = updated,
                        photosCount = photos
                    )
                } else {
                    val parsedError = try {
                        JSONObject(respBody).optString("error").takeIf { it.isNotBlank() }
                    } catch (_: Exception) {
                        null
                    }
                    val friendlyError = when {
                        !parsedError.isNullOrBlank() -> parsedError
                        response.code == 401 -> "Token salah (401)"
                        response.code == 413 -> "Ukuran foto terlalu besar (413)"
                        response.code == 400 || response.code == 415 -> "Data atau format foto tidak valid (${response.code})"
                        else -> "Gagal upload (HTTP ${response.code})"
                    }
                    return@withContext NasUploadResult.Failure(
                        errorMessage = friendlyError,
                        httpCode = response.code
                    )
                }
            }
        } catch (e: Exception) {
            return@withContext NasUploadResult.Failure(
                errorMessage = e.localizedMessage ?: "Gagal terhubung ke server NAS"
            )
        }
    }
}
