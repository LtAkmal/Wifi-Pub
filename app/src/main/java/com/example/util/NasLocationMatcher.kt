package com.example.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

data class GpsFix(
    val lat: Double,
    val lon: Double,
    val accuracyM: Float? = null
)

data class NasLocationRefItem(
    val id: String,
    val date: String,
    val shift: String,
    val thumb: String?,
    val photo: String?,
    val gps: GpsFix?
)

data class NasLocationEntry(
    val location: String,
    val count: Int,
    val lastDate: String,
    val refs: List<NasLocationRefItem>
)

data class LocationCandidateInput(
    val candidateNumber: Int, // 1..N within current batch
    val location: String,
    val refId: String,
    val refDate: String,
    val refShift: String,
    val thumbFile: File
)

data class LocationMatch(
    val match: Int, // 1..N or 0 if none
    val confidence: Double,
    val reason: String,
    val matchedCandidate: LocationCandidateInput? = null
)

object NasLocationMatcher {

    private const val CACHE_FILE_NAME = "location_refs.json"
    private const val CACHE_TTL_MS = 10L * 60L * 1000L // 10 minutes
    private const val LAST_KNOWN_MAX_AGE_MS = 2L * 60L * 1000L // 2 minutes

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /**
     * Captures a single GPS position using Android's built-in [LocationManager] with a 4-second timeout.
     * Uses `getCurrentLocation` on API 30+, falling back to `getLastKnownLocation` (< 2 minutes old).
     */
    @Suppress("MissingPermission")
    suspend fun captureSingleGpsFix(
        context: Context,
        timeoutMs: Long = 4_000L
    ): GpsFix? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission(context)) return@withContext null
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext null

        val freshLastKnown = getRecentLastKnownLocation(locationManager)

        val currentLoc = withTimeoutOrNull(timeoutMs) {
            requestSingleLocation(context, locationManager)
        }

        val chosen = currentLoc ?: freshLastKnown ?: return@withContext null
        GpsFix(
            lat = chosen.latitude,
            lon = chosen.longitude,
            accuracyM = if (chosen.hasAccuracy()) chosen.accuracy else null
        )
    }

    /**
     * Reads GPS coordinates from the original gallery image's EXIF metadata if present.
     * Never calls LocationManager (which would record the phone's current location instead of where the photo was taken).
     */
    @Suppress("DEPRECATION")
    suspend fun extractGpsFromExifUri(
        context: Context,
        uri: Uri?
    ): GpsFix? = withContext(Dispatchers.IO) {
        if (uri == null) return@withContext null
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                val latLong = FloatArray(2)
                if (exif.getLatLong(latLong)) {
                    val lat = latLong[0].toDouble()
                    val lon = latLong[1].toDouble()
                    val isValidRange = !lat.isNaN() && !lon.isNaN() &&
                        lat in -90.0..90.0 &&
                        lon in -180.0..180.0 &&
                        !(abs(lat) < 1e-6 && abs(lon) < 1e-6)
                    if (isValidRange) {
                        return@withContext GpsFix(lat = lat, lon = lon, accuracyM = null)
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("MissingPermission")
    private fun getRecentLastKnownLocation(locationManager: LocationManager): Location? {
        val now = System.currentTimeMillis()
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(LocationManager.FUSED_PROVIDER)
            }
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }
        var best: Location? = null
        for (provider in providers) {
            val loc = try {
                locationManager.getLastKnownLocation(provider)
            } catch (_: Exception) {
                null
            } ?: continue
            val ageMs = now - loc.time
            if (ageMs in 0..LAST_KNOWN_MAX_AGE_MS) {
                if (best == null || (loc.hasAccuracy() && (!best.hasAccuracy() || loc.accuracy < best.accuracy))) {
                    best = loc
                }
            }
        }
        return best
    }

    @Suppress("MissingPermission", "DEPRECATION")
    private suspend fun requestSingleLocation(
        context: Context,
        locationManager: LocationManager
    ): Location? = suspendCancellableCoroutine { cont ->
        val provider = try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    locationManager.isProviderEnabled(LocationManager.FUSED_PROVIDER) ->
                    LocationManager.FUSED_PROVIDER
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                    LocationManager.GPS_PROVIDER
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                    LocationManager.NETWORK_PROVIDER
                else -> null
            }
        } catch (_: Exception) {
            null
        }

        if (provider == null) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val cancelSignal = CancellationSignal()
            cont.invokeOnCancellation {
                try {
                    cancelSignal.cancel()
                } catch (_: Exception) {
                }
            }
            try {
                locationManager.getCurrentLocation(
                    provider,
                    cancelSignal,
                    ContextCompat.getMainExecutor(context)
                ) { location ->
                    if (cont.isActive) {
                        cont.resume(location)
                    }
                }
            } catch (_: Exception) {
                if (cont.isActive) cont.resume(null)
            }
        } else {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    try {
                        locationManager.removeUpdates(this)
                    } catch (_: Exception) {
                    }
                    if (cont.isActive) {
                        cont.resume(location)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            cont.invokeOnCancellation {
                try {
                    locationManager.removeUpdates(listener)
                } catch (_: Exception) {
                }
            }
            try {
                locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            } catch (_: Exception) {
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    /**
     * Fetches or reads cached `/api/locations` from NAS.
     * - Cache file: `filesDir/location_refs.json` (stores `etag`, `fetchedAtMs`, and `data` JSON).
     * - Sends `Authorization: Bearer <token>` and `If-None-Match: <etag>`.
     * - Never logs the token.
     */
    suspend fun getOrFetchLocationRefs(
        context: Context,
        serverUrl: String,
        token: String,
        forceRefresh: Boolean = false
    ): Result<List<NasLocationEntry>> = withContext(Dispatchers.IO) {
        val cleanUrl = serverUrl.trim().trimEnd('/')
        val cleanToken = token.trim()
        if (cleanUrl.isBlank() || cleanToken.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Token NAS belum diisi"))
        }

        val cacheFile = File(context.filesDir, CACHE_FILE_NAME)
        var cachedEtag: String? = null
        var cachedFetchedAt = 0L
        var cachedEntries: List<NasLocationEntry>? = null

        if (cacheFile.exists()) {
            try {
                val wrapper = JSONObject(cacheFile.readText())
                cachedEtag = wrapper.optString("etag").takeIf { it.isNotBlank() }
                cachedFetchedAt = wrapper.optLong("fetchedAtMs", 0L)
                val dataObj = wrapper.optJSONObject("data") ?: wrapper
                cachedEntries = parseLocationsJson(dataObj)
            } catch (_: Exception) {
            }
        }

        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedEntries != null && (now - cachedFetchedAt) in 0..CACHE_TTL_MS) {
            return@withContext Result.success(cachedEntries)
        }

        if (!NasUploadClient.isNetworkAvailable(context)) {
            return@withContext if (cachedEntries != null) {
                Result.success(cachedEntries)
            } else {
                Result.failure(Exception("Offline, deteksi dilewati"))
            }
        }

        val endpoint = "$cleanUrl/api/locations"
        try {
            val reqBuilder = Request.Builder()
                .url(endpoint)
                .get()
                .header("Authorization", "Bearer $cleanToken")
                .header("Accept", "application/json")

            if (!cachedEtag.isNullOrBlank()) {
                reqBuilder.header("If-None-Match", cachedEtag)
            }

            httpClient.newCall(reqBuilder.build()).execute().use { response ->
                when (response.code) {
                    304 -> {
                        if (cachedEntries != null) {
                            // Update fetchedAtMs so we don't re-hit for another 10 minutes
                            try {
                                val wrapper = JSONObject(cacheFile.readText())
                                wrapper.put("fetchedAtMs", System.currentTimeMillis())
                                cacheFile.writeText(wrapper.toString())
                            } catch (_: Exception) {
                            }
                            return@withContext Result.success(cachedEntries)
                        }
                    }
                    200 -> {
                        val bodyStr = response.body?.string().orEmpty()
                        val dataJson = JSONObject(bodyStr)
                        val newEtag = response.header("ETag").orEmpty()
                        val parsed = parseLocationsJson(dataJson)
                        try {
                            val wrapper = JSONObject().apply {
                                put("etag", newEtag)
                                put("fetchedAtMs", System.currentTimeMillis())
                                put("data", dataJson)
                            }
                            cacheFile.writeText(wrapper.toString())
                        } catch (_: Exception) {
                        }
                        pruneRefThumbnailCache(context, parsed)
                        return@withContext Result.success(parsed)
                    }
                    401 -> {
                        return@withContext Result.failure(Exception("Token NAS tidak valid (401)"))
                    }
                }
                return@withContext if (cachedEntries != null) {
                    Result.success(cachedEntries)
                } else {
                    Result.failure(Exception("Gagal memuat lokasi NAS (HTTP ${response.code})"))
                }
            }
        } catch (e: Exception) {
            return@withContext if (cachedEntries != null) {
                Result.success(cachedEntries)
            } else {
                Result.failure(Exception("Gagal memuat lokasi referensi NAS"))
            }
        }
    }

    private fun parseLocationsJson(root: JSONObject): List<NasLocationEntry> {
        val arr = root.optJSONArray("locations") ?: JSONArray()
        val result = mutableListOf<NasLocationEntry>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val locName = item.optString("location").trim()
            if (locName.isBlank() || locName == "-") continue
            val count = item.optInt("count", 1)
            val lastDate = item.optString("lastDate", "")
            val refsArr = item.optJSONArray("refs") ?: JSONArray()
            val refsList = mutableListOf<NasLocationRefItem>()
            for (j in 0 until refsArr.length()) {
                val rObj = refsArr.optJSONObject(j) ?: continue
                val id = rObj.optString("id").trim()
                if (id.isBlank()) continue
                val date = rObj.optString("date", "")
                val shift = rObj.optString("shift", "")
                val thumb = rObj.optString("thumb").trim().takeIf { it.isNotBlank() && it != "null" }
                val photo = rObj.optString("photo").trim().takeIf { it.isNotBlank() && it != "null" }
                val gpsObj = rObj.optJSONObject("gps")
                val gps = if (gpsObj != null && gpsObj.has("lat") && gpsObj.has("lon")) {
                    val lat = gpsObj.optDouble("lat", Double.NaN)
                    val lon = gpsObj.optDouble("lon", Double.NaN)
                    val acc = if (gpsObj.has("accuracyM")) gpsObj.optDouble("accuracyM", Double.NaN) else Double.NaN
                    if (!lat.isNaN() && !lon.isNaN()) {
                        GpsFix(
                            lat = lat,
                            lon = lon,
                            accuracyM = if (!acc.isNaN()) acc.toFloat() else null
                        )
                    } else null
                } else null
                refsList.add(
                    NasLocationRefItem(
                        id = id,
                        date = date,
                        shift = shift,
                        thumb = thumb,
                        photo = photo,
                        gps = gps
                    )
                )
            }
            if (refsList.isNotEmpty()) {
                result.add(
                    NasLocationEntry(
                        location = locName,
                        count = count,
                        lastDate = lastDate,
                        refs = refsList
                    )
                )
            }
        }
        return result
    }

    /**
     * Cleans up `cacheDir/loc_refs/` whenever `/api/locations` returns HTTP 200:
     * - Deletes files whose ID is no longer in the latest locations list.
     * - Deletes files whose `lastModified` is older than 7 days so updated thumbnails are re-downloaded.
     */
    private fun pruneRefThumbnailCache(context: Context, entries: List<NasLocationEntry>) {
        try {
            val dir = File(context.cacheDir, "loc_refs")
            if (!dir.exists()) return
            val validSafeIds = entries
                .asSequence()
                .flatMap { it.refs.asSequence() }
                .map { it.id.replace(Regex("[^a-zA-Z0-9_-]"), "_") }
                .toSet()
            val sevenDaysMs = 7L * 24L * 60L * 60L * 1000L
            val now = System.currentTimeMillis()
            dir.listFiles()?.forEach { file ->
                if (!file.isFile) return@forEach
                val idPart = file.nameWithoutExtension
                val isRemovedId = idPart !in validSafeIds
                val isOlderThan7Days = (now - file.lastModified()) > sevenDaysMs
                if (isRemovedId || isOlderThan7Days) {
                    file.delete()
                }
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Downloads or retrieves cached reference thumbnail (`cacheDir/loc_refs/<id>.jpg`).
     * Image URL = `{serverUrl}/{thumb ?: photo}` (no token needed).
     * If `thumb` is null and `photo` is used, downscales longest side to 480 px.
     */
    suspend fun ensureRefThumbnailFile(
        context: Context,
        serverUrl: String,
        ref: NasLocationRefItem
    ): File? = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "loc_refs")
        if (!dir.exists()) dir.mkdirs()
        val safeId = ref.id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val targetFile = File(dir, "$safeId.jpg")
        if (targetFile.exists() && targetFile.length() > 256L) {
            return@withContext targetFile
        }

        val relativePath = (ref.thumb ?: ref.photo)?.trim()?.trimStart('/')
            ?: return@withContext null
        if (relativePath.isBlank()) return@withContext null

        val cleanBase = serverUrl.trim().trimEnd('/')
        val fullUrl = "$cleanBase/$relativePath"
        val isFullPhotoFallback = ref.thumb.isNullOrBlank()

        val tempFile = File(dir, "${safeId}_tmp.jpg")
        try {
            val request = Request.Builder()
                .url(fullUrl)
                .get()
                .build()
            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body ?: return@withContext null
                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            if (!tempFile.exists() || tempFile.length() <= 256L) {
                tempFile.delete()
                return@withContext null
            }

            if (isFullPhotoFallback) {
                // Downscale full photo to longest side 480 px
                val decoded = decodeSampledBitmap(tempFile, 480)
                tempFile.delete()
                if (decoded != null) {
                    val scaled = scaleDownBitmap(decoded, maxLongestSide = 480)
                    FileOutputStream(targetFile).use { out ->
                        scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
                    }
                    if (scaled !== decoded) scaled.recycle()
                    decoded.recycle()
                }
            } else {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            if (targetFile.exists() && targetFile.length() > 256L) targetFile else null
        } catch (_: Exception) {
            tempFile.delete()
            null
        }
    }

    /**
     * Splits location entries into:
     * - `nearbyEntries`: if `newGps != null` and `accuracyM <= 60f`, locations with a ref within
     *   `100m + newGps.accuracyM + ref.gps.accuracyM`, sorted by ascending distance.
     * - `allByDateEntries`: all locations sorted by `lastDate` descending.
     */
    fun partitionCandidatesByGpsAndDate(
        entries: List<NasLocationEntry>,
        newGps: GpsFix?
    ): Pair<List<NasLocationEntry>, List<NasLocationEntry>> {
        val sortedByDate = entries.sortedByDescending { it.lastDate }
        val newAcc = newGps?.accuracyM
        if (newGps == null || newAcc == null || newAcc > 60f) {
            return emptyList<NasLocationEntry>() to sortedByDate
        }

        val nearbyWithDist = mutableListOf<Pair<NasLocationEntry, Float>>()
        for (entry in entries) {
            var minDistance: Float? = null
            for (ref in entry.refs) {
                val rGps = ref.gps ?: continue
                val dist = distanceMeters(newGps.lat, newGps.lon, rGps.lat, rGps.lon)
                val maxAllowed = 100f + newAcc + (rGps.accuracyM ?: 0f)
                if (dist <= maxAllowed) {
                    if (minDistance == null || dist < minDistance) {
                        minDistance = dist
                    }
                }
            }
            if (minDistance != null) {
                nearbyWithDist.add(entry to minDistance)
            }
        }

        val nearbySorted = nearbyWithDist.sortedBy { it.second }.map { it.first }
        return nearbySorted to sortedByDate
    }

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    fun decodeSampledBitmap(file: File, maxDim: Int): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
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

    fun scaleDownBitmap(source: Bitmap, maxLongestSide: Int): Bitmap {
        val w = source.width.coerceAtLeast(1)
        val h = source.height.coerceAtLeast(1)
        val longest = max(w, h)
        if (longest <= maxLongestSide) return source
        val ratio = maxLongestSide.toFloat() / longest.toFloat()
        val targetW = (w * ratio).roundToInt().coerceAtLeast(1)
        val targetH = (h * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, targetW, targetH, true)
    }
}
