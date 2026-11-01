package com.example.util

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class AiProviderConfig(
    val providerType: String = "NINE_ROUTER", // "NINE_ROUTER", "GEMINI_DIRECT", "FIREBASE"
    val nineRouterBaseUrl: String = "http://localhost:20128/v1",
    val nineRouterApiKey: String = "",
    val nineRouterModel: String = "gemini/gemini-2.5-flash",
    val geminiDirectApiKey: String = "",
    val geminiDirectModel: String = "gemini-2.5-flash"
) {
    val hasCustomConfig: Boolean
        get() = when (providerType) {
            "NINE_ROUTER" -> nineRouterBaseUrl.isNotBlank() && nineRouterApiKey.isNotBlank()
            "GEMINI_DIRECT" -> geminiDirectApiKey.isNotBlank()
            else -> false
        }
}

object CustomAiClient {

    private fun bitmapToBase64Jpeg(bitmap: Bitmap, maxDim: Int = 1024, quality: Int = 82): String {
        val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val ratio = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt().coerceAtLeast(1),
                (bitmap.height * ratio).toInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }

    private fun normalizeNineRouterBaseUrl(rawUrl: String): String {
        var clean = rawUrl.trim().trimEnd('/')
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "https://$clean"
        }
        if (clean.endsWith("/chat/completions")) {
            clean = clean.removeSuffix("/chat/completions")
        }
        if (!clean.endsWith("/v1")) {
            clean = "$clean/v1"
        }
        return clean
    }

    /**
     * Fetches the list of available models from 9Router (/v1/models) so the user can test connection
     * and pick their configured model ID with 1 tap.
     */
    suspend fun fetchNineRouterModels(baseUrl: String, apiKey: String): Result<List<String>> =
        withContext(Dispatchers.IO) {
            try {
                val normalizedBase = normalizeNineRouterBaseUrl(baseUrl)
                val url = URL("$normalizedBase/models")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15_000
                    readTimeout = 15_000
                    setRequestProperty("Accept", "application/json")
                    if (apiKey.isNotBlank()) {
                        setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
                    }
                }

                val code = conn.responseCode
                val body = readStream(conn, code)
                if (code !in 200..299) {
                    return@withContext Result.failure(
                        IllegalStateException("HTTP $code: ${body.take(180)}")
                    )
                }

                val json = JSONObject(body)
                val dataArr = json.optJSONArray("data") ?: JSONArray()
                val models = mutableListOf<String>()
                for (i in 0 until dataArr.length()) {
                    val item = dataArr.optJSONObject(i)
                    val id = item?.optString("id").orEmpty()
                    if (id.isNotBlank()) {
                        models.add(id)
                    }
                }
                Result.success(models)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Scans a Speedtest screenshot using 9Router (OpenAI-compatible vision endpoint)
     * or Direct Gemini REST API, returning Pair(downloadMbps, uploadMbps).
     */
    suspend fun scanSpeedtestImage(
        config: AiProviderConfig,
        bitmap: Bitmap
    ): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        try {
            val base64Img = bitmapToBase64Jpeg(bitmap)
            val promptText =
                "Read this speedtest screenshot and extract ONLY the numeric Download speed (in Mbps) and Upload speed (in Mbps). " +
                    "Normalize commas to dots (e.g. 66,7 -> 66.7). " +
                    "Respond ONLY with a raw JSON object in this exact format: {\"downloadMbps\": \"66.7\", \"uploadMbps\": \"44.6\"}"

            val rawResponse = if (config.providerType == "GEMINI_DIRECT") {
                callGeminiDirectVision(
                    apiKey = config.geminiDirectApiKey.trim(),
                    model = config.geminiDirectModel.trim().ifEmpty { "gemini-2.5-flash" },
                    prompt = promptText,
                    base64Jpeg = base64Img
                )
            } else {
                callNineRouterVision(
                    baseUrl = config.nineRouterBaseUrl,
                    apiKey = config.nineRouterApiKey.trim(),
                    model = config.nineRouterModel.trim().ifEmpty { "gemini/gemini-2.5-flash" },
                    prompt = promptText,
                    base64Jpeg = base64Img
                )
            }

            val parsed = parseSpeedtestJsonOrRegex(rawResponse)
            Result.success(parsed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sends a multi-turn chat message via 9Router or Direct Gemini API.
     */
    suspend fun sendChatCompletion(
        config: AiProviderConfig,
        systemInstruction: String,
        history: List<Pair<Boolean, String>>, // Pair(isUser, text)
        newMessage: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val reply = if (config.providerType == "GEMINI_DIRECT") {
                callGeminiDirectChat(
                    apiKey = config.geminiDirectApiKey.trim(),
                    model = config.geminiDirectModel.trim().ifEmpty { "gemini-2.5-flash" },
                    systemInstruction = systemInstruction,
                    history = history,
                    newMessage = newMessage
                )
            } else {
                callNineRouterChat(
                    baseUrl = config.nineRouterBaseUrl,
                    apiKey = config.nineRouterApiKey.trim(),
                    model = config.nineRouterModel.trim().ifEmpty { "gemini/gemini-2.5-flash" },
                    systemInstruction = systemInstruction,
                    history = history,
                    newMessage = newMessage
                )
            }
            Result.success(reply)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun callNineRouterVision(
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
        base64Jpeg: String
    ): String {
        val normalizedBase = normalizeNineRouterBaseUrl(baseUrl)
        val url = URL("$normalizedBase/chat/completions")
        val payload = JSONObject().apply {
            put("model", model)
            put("temperature", 0.1)
            val messages = JSONArray()
            val contentArr = JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "text")
                    put("text", prompt)
                })
                put(JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().apply {
                        put("url", "data:image/jpeg;base64,$base64Jpeg")
                    })
                })
            }
            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", contentArr)
            })
            put("messages", messages)
        }

        val responseBody = postJson(url, apiKey, payload.toString())
        val json = JSONObject(responseBody)
        val choices = json.optJSONArray("choices")
        val firstChoice = choices?.optJSONObject(0)
        val messageObj = firstChoice?.optJSONObject("message")
        val content = messageObj?.optString("content").orEmpty()
        if (content.isBlank()) {
            throw IllegalStateException("Respons dari 9Router kosong: ${responseBody.take(150)}")
        }
        return content
    }

    private fun callNineRouterChat(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemInstruction: String,
        history: List<Pair<Boolean, String>>,
        newMessage: String
    ): String {
        val normalizedBase = normalizeNineRouterBaseUrl(baseUrl)
        val url = URL("$normalizedBase/chat/completions")
        val messages = JSONArray()
        messages.put(JSONObject().apply {
            put("role", "system")
            put("content", systemInstruction)
        })
        history.forEach { (isUser, text) ->
            messages.put(JSONObject().apply {
                put("role", if (isUser) "user" else "assistant")
                put("content", text)
            })
        }
        messages.put(JSONObject().apply {
            put("role", "user")
            put("content", newMessage)
        })

        val payload = JSONObject().apply {
            put("model", model)
            put("messages", messages)
        }

        val responseBody = postJson(url, apiKey, payload.toString())
        val json = JSONObject(responseBody)
        val choices = json.optJSONArray("choices")
        val content = choices?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()
        if (content.isBlank()) {
            throw IllegalStateException("Respons chat dari 9Router kosong.")
        }
        return content
    }

    private fun callGeminiDirectVision(
        apiKey: String,
        model: String,
        prompt: String,
        base64Jpeg: String
    ): String {
        val cleanModel = model.removePrefix("models/").removePrefix("gemini/")
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:generateContent?key=$apiKey")
        val parts = JSONArray().apply {
            put(JSONObject().apply { put("text", prompt) })
            put(JSONObject().apply {
                put("inline_data", JSONObject().apply {
                    put("mime_type", "image/jpeg")
                    put("data", base64Jpeg)
                })
            })
        }
        val contents = JSONArray().apply {
            put(JSONObject().apply {
                put("parts", parts)
            })
        }
        val payload = JSONObject().apply {
            put("contents", contents)
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
            })
        }

        val responseBody = postJson(url, null, payload.toString())
        val json = JSONObject(responseBody)
        val text = json.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?.optJSONObject(0)
            ?.optString("text")
            .orEmpty()
        if (text.isBlank()) {
            throw IllegalStateException("Respons dari Gemini API kosong.")
        }
        return text
    }

    private fun callGeminiDirectChat(
        apiKey: String,
        model: String,
        systemInstruction: String,
        history: List<Pair<Boolean, String>>,
        newMessage: String
    ): String {
        val cleanModel = model.removePrefix("models/").removePrefix("gemini/")
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:generateContent?key=$apiKey")
        val contents = JSONArray()
        history.forEach { (isUser, text) ->
            contents.put(JSONObject().apply {
                put("role", if (isUser) "user" else "model")
                put("parts", JSONArray().put(JSONObject().put("text", text)))
            })
        }
        contents.put(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().put(JSONObject().put("text", newMessage)))
        })

        val payload = JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", systemInstruction)))
            })
            put("contents", contents)
        }

        val responseBody = postJson(url, null, payload.toString())
        val json = JSONObject(responseBody)
        val text = json.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?.optJSONObject(0)
            ?.optString("text")
            .orEmpty()
        if (text.isBlank()) {
            throw IllegalStateException("Respons chat Gemini API kosong.")
        }
        return text
    }

    private fun parseSpeedtestJsonOrRegex(raw: String): Pair<String, String> {
        val cleaned = raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        // Try JSON object extraction first
        val jsonStart = cleaned.indexOf('{')
        val jsonEnd = cleaned.lastIndexOf('}')
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            val sub = cleaned.substring(jsonStart, jsonEnd + 1)
            try {
                val obj = JSONObject(sub)
                val down = ReportFormatter.cleanSpeedNumber(
                    obj.optString("downloadMbps", obj.optString("download", "")).replace(",", ".")
                )
                val up = ReportFormatter.cleanSpeedNumber(
                    obj.optString("uploadMbps", obj.optString("upload", "")).replace(",", ".")
                )
                return Pair(down, up)
            } catch (_: Exception) {
            }
        }

        // Fallback regex if model returned plain text numbers
        val numbers = Regex("""\d+(?:[.,]\d+)?""").findAll(cleaned).map { it.value.replace(",", ".") }.toList()
        if (numbers.size >= 2) {
            return Pair(numbers[0], numbers[1])
        }
        throw IllegalStateException("Format angka tidak ditemukan dari respons AI: ${cleaned.take(100)}")
    }

    private fun postJson(url: URL, bearerToken: String?, jsonPayload: String): String {
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20_000
            readTimeout = 45_000
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
            if (!bearerToken.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer $bearerToken")
            }
        }

        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(jsonPayload)
            writer.flush()
        }

        val code = conn.responseCode
        val body = readStream(conn, code)
        if (code !in 200..299) {
            throw IllegalStateException("API Error (HTTP $code): ${body.take(220)}")
        }
        return body
    }

    private fun readStream(conn: HttpURLConnection, responseCode: Int): String {
        val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
        if (stream == null) return ""
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
    }
}
