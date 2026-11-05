package com.example.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.CorrectiveMaintenanceEntity
import com.example.data.PmReportEntity
import com.example.data.PmRepository
import com.example.data.TodayReportConfig
import com.example.util.AiProviderConfig
import com.example.util.CollageGenerator
import com.example.util.CollageResult
import com.example.util.CustomAiClient
import com.example.util.LiveSpeedtestResult
import com.example.util.NetworkTelemetryEngine
import com.example.util.ReportFormatter
import com.example.util.WifiApItem
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.Locale

private const val TAG = "PmMainViewModel"

sealed interface SpeedtestScanState {
    data object Idle : SpeedtestScanState
    data object Scanning : SpeedtestScanState
    data class Success(val downloadMbps: String, val uploadMbps: String, val providerLabel: String = "AI") : SpeedtestScanState
    data class Error(val message: String, val suggestApiConfig: Boolean = false) : SpeedtestScanState
}

data class BuiltInSpeedtestUiState(
    val isRunning: Boolean = false,
    val phase: String = "IDLE", // "IDLE", "PING", "DOWNLOAD", "UPLOAD", "DONE"
    val currentGaugeMbps: Double = 0.0,
    val pingMs: Int = 0,
    val jitterMs: Int = 0,
    val downloadMbps: Double = 0.0,
    val uploadMbps: Double = 0.0,
    val progress: Float = 0f,
    val ispName: String = "Memuat Jaringan...",
    val deviceName: String = Build.MODEL ?: "Android Device",
    val autoCapturedToSlot3: Boolean = false
)

sealed interface CollageUiState {
    data object Idle : CollageUiState
    data object Generating : CollageUiState
    data class Ready(val result: CollageResult) : CollageUiState
    data class Error(val message: String) : CollageUiState
}

sealed interface AiImageUiState {
    data object Idle : AiImageUiState
    data object Loading : AiImageUiState
    data class Success(
        val bitmap: Bitmap,
        val aiInspectionNote: String = "",
        val modeInfo: String = ""
    ) : AiImageUiState
    data class Error(val message: String) : AiImageUiState
}

enum class ChatModelOption(val modelId: String, val label: String, val description: String) {
    FLASH("gemini-3.8-flash", "Gemini 3.8 Flash", "Standar & seimbang untuk tugas harian"),
    ARGON("gemini-4-argon", "Gemini 4 Argon", "Penalaran kompleks & analisis mendalam"),
    FLASH_LITE("gemini-3.1-flash-lite", "Gemini 3.1 Flash-Lite", "Ultra cepat untuk respon kilat")
}

data class ChatMessageUi(
    val id: Long = System.currentTimeMillis(),
    val isUser: Boolean,
    val text: String,
    val modelUsed: String = "",
    val isStreaming: Boolean = false
)

class PmMainViewModel(
    private val repository: PmRepository
) : ViewModel() {

    private val _selectedDateKey = MutableStateFlow(ReportFormatter.todayDateKey())
    val selectedDateKey: StateFlow<String> = _selectedDateKey.asStateFlow()

    // Current Daily PM Form State (Starts empty so user types or selects from their own saved locations)
    private val _locationInput = MutableStateFlow("")
    val locationInput: StateFlow<String> = _locationInput.asStateFlow()

    private val _equipmentNameInput = MutableStateFlow("Access Point Wifi Publik")
    val equipmentNameInput: StateFlow<String> = _equipmentNameInput.asStateFlow()

    private val _uploadMbpsInput = MutableStateFlow("0.0")
    val uploadMbpsInput: StateFlow<String> = _uploadMbpsInput.asStateFlow()

    private val _downloadMbpsInput = MutableStateFlow("0.0")
    val downloadMbpsInput: StateFlow<String> = _downloadMbpsInput.asStateFlow()

    private val _displayDateEnInput = MutableStateFlow(
        ReportFormatter.formatDisplayDateEn(ReportFormatter.todayDateKey())
    )
    val displayDateEnInput: StateFlow<String> = _displayDateEnInput.asStateFlow()

    private val _statusNetwork = MutableStateFlow(true)
    val statusNetwork: StateFlow<Boolean> = _statusNetwork.asStateFlow()

    private val _statusDevice = MutableStateFlow(true)
    val statusDevice: StateFlow<Boolean> = _statusDevice.asStateFlow()

    private val _statusSpeed = MutableStateFlow(true)
    val statusSpeed: StateFlow<Boolean> = _statusSpeed.asStateFlow()

    // Speedtest Screenshot & Scan State
    private val _speedtestBitmap = MutableStateFlow<Bitmap?>(null)
    val speedtestBitmap: StateFlow<Bitmap?> = _speedtestBitmap.asStateFlow()

    private val _scanState = MutableStateFlow<SpeedtestScanState>(SpeedtestScanState.Idle)
    val scanState: StateFlow<SpeedtestScanState> = _scanState.asStateFlow()

    // Built-In Live Speedtest State
    private val _builtInSpeedtestState = MutableStateFlow(BuiltInSpeedtestUiState())
    val builtInSpeedtestState: StateFlow<BuiltInSpeedtestUiState> = _builtInSpeedtestState.asStateFlow()

    // Built-In Real WiFi Analyzer State
    private val _wifiAccessPoints = MutableStateFlow<List<WifiApItem>>(emptyList())
    val wifiAccessPoints: StateFlow<List<WifiApItem>> = _wifiAccessPoints.asStateFlow()

    private val _selectedWifiBand = MutableStateFlow("5") // "2.4", "5", "6"
    val selectedWifiBand: StateFlow<String> = _selectedWifiBand.asStateFlow()

    private val _isWifiScanning = MutableStateFlow(false)
    val isWifiScanning: StateFlow<Boolean> = _isWifiScanning.asStateFlow()

    private val _wifiAnalyzerCapturedToSlot4 = MutableStateFlow(false)
    val wifiAnalyzerCapturedToSlot4: StateFlow<Boolean> = _wifiAnalyzerCapturedToSlot4.asStateFlow()

    // 4 Fixed Collage Slots:
    // Slot 0 = Foto 1 (Kamera 1)
    // Slot 1 = Foto 2 (Kamera 2)
    // Slot 2 = Foto 3 (Speedtest Auto-Screenshot)
    // Slot 3 = Foto 4 (WiFi Analyzer Auto-Screenshot)
    private val _collageSlots = MutableStateFlow<List<Bitmap?>>(listOf(null, null, null, null))
    val collageSlots: StateFlow<List<Bitmap?>> = _collageSlots.asStateFlow()

    val collageBitmaps: StateFlow<List<Bitmap>> = _collageSlots
        .map { list -> list.filterNotNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _includeCollageHeader = MutableStateFlow(true)
    val includeCollageHeader: StateFlow<Boolean> = _includeCollageHeader.asStateFlow()

    private val _fitEntirePhoto = MutableStateFlow(true)
    val fitEntirePhoto: StateFlow<Boolean> = _fitEntirePhoto.asStateFlow()

    private val _collageState = MutableStateFlow<CollageUiState>(CollageUiState.Idle)
    val collageState: StateFlow<CollageUiState> = _collageState.asStateFlow()

    // Saved Locations (User-typed only), Report Config & AI Provider Config
    val savedLocations: StateFlow<List<String>> = repository.savedLocationsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reportConfig: StateFlow<TodayReportConfig> = repository.reportConfigFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TodayReportConfig())

    val aiProviderConfig: StateFlow<AiProviderConfig> = repository.aiProviderConfigFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiProviderConfig())

    private val _fetchedNineRouterModels = MutableStateFlow<List<String>>(emptyList())
    val fetchedNineRouterModels: StateFlow<List<String>> = _fetchedNineRouterModels.asStateFlow()

    private val _nineRouterTestStatus = MutableStateFlow<String?>(null)
    val nineRouterTestStatus: StateFlow<String?> = _nineRouterTestStatus.asStateFlow()

    private val _isTestingNineRouter = MutableStateFlow(false)
    val isTestingNineRouter: StateFlow<Boolean> = _isTestingNineRouter.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val pmReportsForSelectedDate: StateFlow<List<PmReportEntity>> = _selectedDateKey
        .flatMapLatest { dateKey -> repository.getPmReportsForDate(dateKey) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val cmReportsForSelectedDate: StateFlow<List<CorrectiveMaintenanceEntity>> = _selectedDateKey
        .flatMapLatest { dateKey -> repository.getCmForDate(dateKey) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // AI Image Studio State
    private val _aiImageInputBitmap = MutableStateFlow<Bitmap?>(null)
    val aiImageInputBitmap: StateFlow<Bitmap?> = _aiImageInputBitmap.asStateFlow()

    private val _aiImageState = MutableStateFlow<AiImageUiState>(AiImageUiState.Idle)
    val aiImageState: StateFlow<AiImageUiState> = _aiImageState.asStateFlow()

    // Multi-turn Chatbot State
    private val _selectedChatModel = MutableStateFlow(ChatModelOption.FLASH)
    val selectedChatModel: StateFlow<ChatModelOption> = _selectedChatModel.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessageUi>>(
        listOf(
            ChatMessageUi(
                isUser = false,
                text = "Halo! Saya Asisten AI ICT Operation & PM WiFi Publik. Anda bisa bertanya soal troubleshooting Access Point, merapikan catatan Corrective Maintenance, atau menganalisis hasil Speedtest.",
                modelUsed = ChatModelOption.FLASH.label
            )
        )
    )
    val chatMessages: StateFlow<List<ChatMessageUi>> = _chatMessages.asStateFlow()

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    private val _chatError = MutableStateFlow<String?>(null)
    val chatError: StateFlow<String?> = _chatError.asStateFlow()

    private val chatSystemPromptText =
        "Anda adalah Asisten Teknis Senior ICT Operation & Preventive Maintenance (PM) WiFi Publik Bandara (CGK Terminal 3). " +
            "Bantu teknisi dalam menganalisis kualitas bandwidth/speedtest WiFi, menyusun laporan Corrective Maintenance (CM) dan Preventive Maintenance (PM), " +
            "serta memberikan solusi cepat kendala perangkat Access Point dengan bahasa Indonesia yang ringkas, jelas, dan profesional."

    private val speedtestSchema = Schema.obj(
        properties = mapOf(
            "downloadMbps" to Schema.string(
                description = "Numeric value of Download speed in Mbps extracted from the speedtest image (e.g. '66.7'). Only numbers and decimal point."
            ),
            "uploadMbps" to Schema.string(
                description = "Numeric value of Upload speed in Mbps extracted from the speedtest image (e.g. '44.6'). Only numbers and decimal point."
            )
        )
    )

    private val speedtestExtractorModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = speedtestSchema
            }
        )
    }

    private val nanoBananaImageModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    private val chatSystemInstruction = content {
        text(chatSystemPromptText)
    }

    private val flashChatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = chatSystemInstruction
        )
    }

    private val argonChatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-4-argon",
            systemInstruction = chatSystemInstruction
        )
    }

    private val flashLiteChatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.1-flash-lite",
            systemInstruction = chatSystemInstruction
        )
    }

    // --- Form Setters ---
    fun updateLocation(value: String) {
        _locationInput.value = value
    }

    fun saveLocationIfNotBlank(location: String = _locationInput.value) {
        val clean = location.trim()
        if (clean.length >= 2) {
            viewModelScope.launch {
                repository.addCustomLocation(clean)
            }
        }
    }

    fun removeSavedLocation(location: String) {
        viewModelScope.launch {
            repository.removeCustomLocation(location)
        }
    }

    fun updateEquipmentName(value: String) {
        _equipmentNameInput.value = value
    }

    fun updateUploadMbps(value: String) {
        _uploadMbpsInput.value = value
    }

    fun updateDownloadMbps(value: String) {
        _downloadMbpsInput.value = value
    }

    fun updateDisplayDateEn(value: String) {
        _displayDateEnInput.value = value
    }

    fun toggleStatusNetwork(value: Boolean) {
        _statusNetwork.value = value
    }

    fun toggleStatusDevice(value: Boolean) {
        _statusDevice.value = value
    }

    fun toggleStatusSpeed(value: Boolean) {
        _statusSpeed.value = value
    }

    fun setSelectedDateKey(dateKey: String) {
        _selectedDateKey.value = dateKey
        _displayDateEnInput.value = ReportFormatter.formatDisplayDateEn(dateKey)
    }

    fun updateReportConfig(config: TodayReportConfig) {
        viewModelScope.launch {
            repository.saveReportConfig(config)
        }
    }

    /**
     * 1-Tap Shift Toggle between "Pagi(SH1)" and "Malam(SH2)" without manual typing.
     */
    fun selectShiftOption(isPagiSh1: Boolean) {
        val current = reportConfig.value
        val updated = if (isPagiSh1) {
            current.copy(
                shift = "Pagi(SH1)",
                aplusanShiftLabel = "Tim Shift Malam (SH2)"
            )
        } else {
            current.copy(
                shift = "Malam(SH2)",
                aplusanShiftLabel = "Tim Shift Pagi (SH1)"
            )
        }
        updateReportConfig(updated)
    }

    // --- Built-In Real Speedtest Engine + Auto-Input + Auto-Screenshot to Slot 3 (index 2) ---
    fun initNetworkInfo(context: Context) {
        viewModelScope.launch {
            val (isp, device) = NetworkTelemetryEngine.detectIspAndDevice(context)
            _builtInSpeedtestState.value = _builtInSpeedtestState.value.copy(
                ispName = isp,
                deviceName = device
            )
        }
    }

    fun runBuiltInSpeedtest(context: Context, onCompleted: (LiveSpeedtestResult) -> Unit = {}) {
        if (_builtInSpeedtestState.value.isRunning) return
        viewModelScope.launch {
            _builtInSpeedtestState.value = _builtInSpeedtestState.value.copy(
                isRunning = true,
                phase = "PING",
                currentGaugeMbps = 0.0,
                downloadMbps = 0.0,
                uploadMbps = 0.0,
                progress = 0.02f,
                autoCapturedToSlot3 = false
            )

            val result = NetworkTelemetryEngine.runRealSpeedtest(context) { phase, currentMbps, ping, jitter, down, up, prog ->
                _builtInSpeedtestState.value = _builtInSpeedtestState.value.copy(
                    isRunning = phase != "DONE",
                    phase = phase,
                    currentGaugeMbps = currentMbps,
                    pingMs = ping,
                    jitterMs = jitter,
                    downloadMbps = down,
                    uploadMbps = up,
                    progress = prog
                )
            }

            // 1. Automatically populate Download & Upload Mbps in the PM Report form!
            val downStr = String.format(Locale.US, "%.1f", result.downloadMbps)
            val upStr = String.format(Locale.US, "%.1f", result.uploadMbps)
            _downloadMbpsInput.value = downStr
            _uploadMbpsInput.value = upStr

            // 2. Automatically take screenshot of the Speedtest result and place it in Slot 3 (index 2 = FOTO 3)!
            val screenshotBmp = NetworkTelemetryEngine.renderSpeedtestScreenshotBitmap(result)
            _speedtestBitmap.value = screenshotBmp
            _scanState.value = SpeedtestScanState.Success(
                downloadMbps = downStr,
                uploadMbps = upStr,
                providerLabel = "Built-in Speedtest"
            )
            setCollageSlotBitmap(context, slotIndex = 2, bitmap = screenshotBmp)

            _builtInSpeedtestState.value = _builtInSpeedtestState.value.copy(
                isRunning = false,
                phase = "DONE",
                downloadMbps = result.downloadMbps,
                uploadMbps = result.uploadMbps,
                pingMs = result.pingMs,
                jitterMs = result.jitterMs,
                ispName = result.ispName,
                deviceName = result.deviceName,
                autoCapturedToSlot3 = true
            )
            onCompleted(result)
        }
    }

    // --- Built-In Real WiFi Analyzer + Auto-Screenshot to Slot 4 (index 3) ---
    fun setSelectedWifiBand(band: String) {
        _selectedWifiBand.value = band
    }

    fun scanWifiAnalyzer(
        context: Context,
        autoCaptureToSlot4: Boolean = true,
        onCaptured: () -> Unit = {}
    ) {
        if (_isWifiScanning.value) return
        viewModelScope.launch {
            _isWifiScanning.value = true
            val list = NetworkTelemetryEngine.scanWifiAccessPoints(context)
            _wifiAccessPoints.value = list
            _isWifiScanning.value = false

            if (autoCaptureToSlot4 && list.isNotEmpty()) {
                captureWifiAnalyzerToSlot4(context, onCaptured)
            }
        }
    }

    fun captureWifiAnalyzerToSlot4(context: Context, onCaptured: () -> Unit = {}) {
        viewModelScope.launch {
            val currentBand = _selectedWifiBand.value
            val allAps = _wifiAccessPoints.value
            val filtered = allAps.filter { it.bandGhz == currentBand }.ifEmpty { allAps }
            val bmp = NetworkTelemetryEngine.renderWifiAnalyzerScreenshotBitmap(
                accessPoints = filtered,
                selectedBand = currentBand
            )
            // Automatically place in Slot 4 (index 3 = FOTO 4 • WIFI ANALYZER)
            setCollageSlotBitmap(context, slotIndex = 3, bitmap = bmp)
            _wifiAnalyzerCapturedToSlot4.value = true
            onCaptured()
        }
    }

    // --- Camera Capture directly into Slot 1 (index 0) and Slot 2 (index 1) ---
    fun nextAvailableCameraSlot(): Int {
        val current = _collageSlots.value
        return when {
            current[0] == null -> 0
            current[1] == null -> 1
            else -> 0
        }
    }

    fun onCameraPhotoCaptured(context: Context, slotIndex: Int, uri: Uri, onLoaded: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val bmp = CollageGenerator.loadBitmapFromUri(context, uri, maxDimension = 1280)
            if (bmp != null) {
                val targetSlot = slotIndex.coerceIn(0, 3)
                setCollageSlotBitmap(context, targetSlot, bmp)
                onLoaded(targetSlot)
            }
        }
    }

    fun setCollageSlotBitmap(context: Context, slotIndex: Int, bitmap: Bitmap?) {
        val updated = _collageSlots.value.toMutableList()
        if (slotIndex in 0..3) {
            updated[slotIndex] = bitmap
            _collageSlots.value = updated
            if (updated.any { it != null }) {
                generateCollage(context)
            } else {
                _collageState.value = CollageUiState.Idle
            }
        }
    }

    fun saveAiProviderConfig(config: AiProviderConfig) {
        viewModelScope.launch {
            repository.saveAiProviderConfig(config)
            _nineRouterTestStatus.value = null
            if (_speedtestBitmap.value != null && config.hasCustomConfig) {
                scanSpeedtestBitmap(_speedtestBitmap.value, overrideConfig = config)
            }
        }
    }

    fun testAndFetchNineRouterModels(baseUrl: String, apiKey: String) {
        viewModelScope.launch {
            _isTestingNineRouter.value = true
            _nineRouterTestStatus.value = "Menghubungi server 9Router..."
            val res = CustomAiClient.fetchNineRouterModels(baseUrl, apiKey)
            _isTestingNineRouter.value = false
            res.onSuccess { models ->
                _fetchedNineRouterModels.value = models
                _nineRouterTestStatus.value = if (models.isNotEmpty()) {
                    "Terhubung! Ditemukan ${models.size} model (ketuk model di bawah untuk memilih)."
                } else {
                    "Terhubung ke server, namun daftar /v1/models kosong. Silakan ketik nama model manual."
                }
            }.onFailure { err ->
                _nineRouterTestStatus.value = "Gagal terhubung: ${err.localizedMessage ?: "Periksa URL & API Key"}"
            }
        }
    }

    // --- Manual Screenshot Speedtest Multimodal Extraction (also puts screenshot into Slot 3) ---
    fun onSpeedtestImageSelected(context: Context, uri: Uri, autoScan: Boolean = true) {
        viewModelScope.launch {
            val bitmap = CollageGenerator.loadBitmapFromUri(context, uri, maxDimension = 1280)
            if (bitmap != null) {
                _speedtestBitmap.value = bitmap
                setCollageSlotBitmap(context, slotIndex = 2, bitmap = bitmap)
                if (autoScan) {
                    scanSpeedtestBitmap(bitmap)
                }
            } else {
                _scanState.value = SpeedtestScanState.Error("Gagal membaca gambar dari perangkat.")
            }
        }
    }

    fun scanSpeedtestBitmap(
        bitmap: Bitmap? = _speedtestBitmap.value,
        overrideConfig: AiProviderConfig? = null
    ) {
        if (bitmap == null) {
            _scanState.value = SpeedtestScanState.Error("Pilih screenshot Speedtest terlebih dahulu.")
            return
        }
        val activeConfig = overrideConfig ?: aiProviderConfig.value

        viewModelScope.launch {
            _scanState.value = SpeedtestScanState.Scanning

            if (activeConfig.hasCustomConfig) {
                val providerLabel = if (activeConfig.providerType == "NINE_ROUTER") {
                    "9Router (${activeConfig.nineRouterModel.substringAfterLast('/')})"
                } else {
                    "Gemini API"
                }
                val result = CustomAiClient.scanSpeedtestImage(activeConfig, bitmap)
                result.onSuccess { (downClean, upClean) ->
                    _downloadMbpsInput.value = downClean
                    _uploadMbpsInput.value = upClean
                    _scanState.value = SpeedtestScanState.Success(
                        downloadMbps = downClean,
                        uploadMbps = upClean,
                        providerLabel = providerLabel
                    )
                }.onFailure { err ->
                    Log.e(TAG, "Custom AI speedtest scan failed", err)
                    _scanState.value = SpeedtestScanState.Error(
                        message = err.localizedMessage ?: "Gagal membaca Speedtest lewat $providerLabel.",
                        suggestApiConfig = true
                    )
                }
                return@launch
            }

            try {
                val response = withTimeout(20_000L) {
                    speedtestExtractorModel.generateContent(
                        content {
                            image(bitmap)
                            text(
                                "Read this speedtest screenshot and extract ONLY the numeric Download speed (in Mbps) and Upload speed (in Mbps). " +
                                    "Normalize commas to dots (e.g. 66,7 -> 66.7). Do not include 'Mbps' text."
                            )
                        }
                    )
                }
                val jsonText = response.text
                if (jsonText.isNullOrBlank()) {
                    _scanState.value = SpeedtestScanState.Error(
                        message = "AI mengembalikan hasil kosong.",
                        suggestApiConfig = true
                    )
                    return@launch
                }

                val json = JSONObject(jsonText)
                val downRaw = json.optString("downloadMbps", "").replace(",", ".").trim()
                val upRaw = json.optString("uploadMbps", "").replace(",", ".").trim()

                val downClean = ReportFormatter.cleanSpeedNumber(downRaw)
                val upClean = ReportFormatter.cleanSpeedNumber(upRaw)

                _downloadMbpsInput.value = downClean
                _uploadMbpsInput.value = upClean
                _scanState.value = SpeedtestScanState.Success(
                    downloadMbps = downClean,
                    uploadMbps = upClean,
                    providerLabel = "Firebase AI"
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Speedtest scan timed out", e)
                _scanState.value = SpeedtestScanState.Error(
                    message = "Gunakan fitur Speedtest bawaan di atas atau hubungkan API 9Router Anda lewat tombol 'Atur API'.",
                    suggestApiConfig = true
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Speedtest scan failed", e)
                _scanState.value = SpeedtestScanState.Error(
                    message = "Perlu API 9Router / Gemini untuk scan manual: ${e.localizedMessage ?: "Gagal"}",
                    suggestApiConfig = true
                )
            }
        }
    }

    // --- Save Daily PM Entry to Today Report & Auto-Save Typed Location ---
    fun saveCurrentPmToTodayReport(onSaved: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val currentCollage = (_collageState.value as? CollageUiState.Ready)?.result
            val cleanLoc = _locationInput.value.trim().ifBlank { "Terminal 3 CGK" }
            val entity = PmReportEntity(
                dateKey = _selectedDateKey.value,
                displayDateEn = _displayDateEnInput.value,
                location = cleanLoc,
                equipmentName = _equipmentNameInput.value.trim(),
                uploadMbps = ReportFormatter.cleanSpeedNumber(_uploadMbpsInput.value),
                downloadMbps = ReportFormatter.cleanSpeedNumber(_downloadMbpsInput.value),
                statusNetwork = _statusNetwork.value,
                statusDevice = _statusDevice.value,
                statusSpeed = _statusSpeed.value,
                onDutyText = reportConfig.value.onDutyDailyPm,
                collageFilePath = currentCollage?.file?.absolutePath,
                collageSizeBytes = currentCollage?.sizeBytes ?: 0L
            )
            // Automatically remember this location so the user doesn't need to re-type next time!
            if (_locationInput.value.isNotBlank()) {
                repository.addCustomLocation(_locationInput.value.trim())
            }
            repository.insertPmReport(entity)
            val nextIndex = pmReportsForSelectedDate.value.size + 1
            onSaved(nextIndex)
        }
    }

    fun deletePmReport(id: Int) {
        viewModelScope.launch {
            repository.deletePmReport(id)
        }
    }

    // --- Corrective Maintenance CRUD (Add, Edit & Delete) ---
    fun addCorrectiveMaintenance(
        activityTitle: String,
        location: String,
        equipmentName: String,
        kendala: String,
        issue: String,
        actionLines: String,
        statusLines: String
    ) {
        viewModelScope.launch {
            if (location.isNotBlank()) {
                repository.addCustomLocation(location.trim())
            }
            repository.insertCm(
                CorrectiveMaintenanceEntity(
                    dateKey = _selectedDateKey.value,
                    activityTitle = activityTitle,
                    location = location,
                    equipmentName = equipmentName,
                    kendala = kendala,
                    issue = issue,
                    actionLines = actionLines,
                    statusLines = statusLines
                )
            )
        }
    }

    fun updateCorrectiveMaintenance(
        existing: CorrectiveMaintenanceEntity,
        activityTitle: String,
        location: String,
        equipmentName: String,
        kendala: String,
        issue: String,
        actionLines: String,
        statusLines: String
    ) {
        viewModelScope.launch {
            if (location.isNotBlank()) {
                repository.addCustomLocation(location.trim())
            }
            repository.updateCm(
                existing.copy(
                    activityTitle = activityTitle,
                    location = location,
                    equipmentName = equipmentName,
                    kendala = kendala,
                    issue = issue,
                    actionLines = actionLines,
                    statusLines = statusLines
                )
            )
        }
    }

    fun deleteCorrectiveMaintenance(id: Int) {
        viewModelScope.launch {
            repository.deleteCm(id)
        }
    }

    // --- 4-Photo Collage (< 1 MB) Operations ---
    fun onCollagePhotosSelected(context: Context, uris: List<Uri>) {
        viewModelScope.launch {
            val loaded = uris.take(4).mapNotNull { uri ->
                CollageGenerator.loadBitmapFromUri(context, uri, maxDimension = 1280)
            }
            if (loaded.isNotEmpty()) {
                val updated = _collageSlots.value.toMutableList()
                var loadIdx = 0
                for (i in 0..3) {
                    if (updated[i] == null && loadIdx < loaded.size) {
                        updated[i] = loaded[loadIdx++]
                    }
                }
                var overwriteIdx = 0
                while (loadIdx < loaded.size && overwriteIdx < 4) {
                    updated[overwriteIdx++] = loaded[loadIdx++]
                }
                _collageSlots.value = updated
                generateCollage(context)
            }
        }
    }

    fun replaceCollagePhotoAt(context: Context, index: Int, uri: Uri) {
        viewModelScope.launch {
            val bmp = CollageGenerator.loadBitmapFromUri(context, uri, maxDimension = 1280) ?: return@launch
            setCollageSlotBitmap(context, index.coerceIn(0, 3), bmp)
        }
    }

    fun removeCollagePhotoAt(context: Context, index: Int) {
        setCollageSlotBitmap(context, index.coerceIn(0, 3), null)
    }

    fun clearCollagePhotos() {
        _collageSlots.value = listOf(null, null, null, null)
        _collageState.value = CollageUiState.Idle
    }

    fun setIncludeCollageHeader(context: Context, include: Boolean) {
        _includeCollageHeader.value = include
        if (_collageSlots.value.any { it != null }) {
            generateCollage(context)
        }
    }

    fun setFitEntirePhoto(context: Context, fit: Boolean) {
        _fitEntirePhoto.value = fit
        if (_collageSlots.value.any { it != null }) {
            generateCollage(context)
        }
    }

    fun generateCollage(context: Context) {
        val slots = _collageSlots.value
        if (slots.all { it == null }) {
            _collageState.value = CollageUiState.Error("Pilih atau ambil minimal 1 hingga 4 foto terlebih dahulu.")
            return
        }
        viewModelScope.launch {
            _collageState.value = CollageUiState.Generating
            try {
                val up = ReportFormatter.cleanSpeedNumber(_uploadMbpsInput.value)
                val down = ReportFormatter.cleanSpeedNumber(_downloadMbpsInput.value)
                val speedText = "Up ${up}Mbps / Down ${down}Mbps"
                val result = CollageGenerator.create2x2CollageUnder1Mb(
                    context = context,
                    slots = slots,
                    locationTitle = _locationInput.value,
                    dateLabel = _displayDateEnInput.value,
                    speedSummary = speedText,
                    includeHeaderWatermark = _includeCollageHeader.value,
                    fitEntirePhoto = _fitEntirePhoto.value
                )
                _collageState.value = CollageUiState.Ready(result)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate collage", e)
                _collageState.value = CollageUiState.Error(
                    e.localizedMessage ?: "Gagal membuat kolase foto."
                )
            }
        }
    }

    // --- Studio Foto AI ---
    fun setAiImageInputFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            val bmp = CollageGenerator.loadBitmapFromUri(context, uri, maxDimension = 1280)
            _aiImageInputBitmap.value = bmp
        }
    }

    fun clearAiImageInput() {
        _aiImageInputBitmap.value = null
    }

    fun generateOrEditImageWithAi(prompt: String, addTechnicalStamp: Boolean = true) {
        if (prompt.isBlank()) {
            _aiImageState.value = AiImageUiState.Error("Masukkan instruksi/prompt gambar terlebih dahulu.")
            return
        }
        val sourceBitmap = _aiImageInputBitmap.value
        val activeConfig = aiProviderConfig.value
        val up = ReportFormatter.cleanSpeedNumber(_uploadMbpsInput.value)
        val down = ReportFormatter.cleanSpeedNumber(_downloadMbpsInput.value)
        val speedSummary = "Up ${up}Mbps / Down ${down}Mbps"

        viewModelScope.launch {
            _aiImageState.value = AiImageUiState.Loading

            if (activeConfig.hasCustomConfig) {
                val enhancedBmp = CollageGenerator.enhanceDocumentationPhoto(
                    source = sourceBitmap,
                    locationTitle = _locationInput.value,
                    dateLabel = _displayDateEnInput.value,
                    speedSummary = speedSummary,
                    instructionText = prompt.trim(),
                    addTechnicalStamp = addTechnicalStamp
                )
                val inspectionNote = if (sourceBitmap != null) {
                    CustomAiClient.analyzeDocumentationPhoto(
                        config = activeConfig,
                        bitmap = sourceBitmap,
                        userPrompt = prompt.trim()
                    ).getOrElse { "" }
                } else {
                    "Kartu dokumentasi teknis berhasil dibuat sesuai lokasi & speedtest saat ini."
                }

                _aiImageState.value = AiImageUiState.Success(
                    bitmap = enhancedBmp,
                    aiInspectionNote = inspectionNote,
                    modeInfo = "Smart Studio Enhancer + ${if (activeConfig.providerType == "NINE_ROUTER") "9Router AI Vision" else "Gemini Vision"}"
                )
                return@launch
            }

            try {
                val outputBitmap = withTimeout(25_000L) {
                    val response = if (sourceBitmap != null) {
                        nanoBananaImageModel.generateContent(
                            content {
                                image(sourceBitmap)
                                text(prompt.trim())
                            }
                        )
                    } else {
                        nanoBananaImageModel.generateContent(prompt.trim())
                    }
                    response.candidates.firstOrNull()
                        ?.content?.parts?.filterIsInstance<ImagePart>()
                        ?.firstOrNull()?.image
                }

                if (outputBitmap != null) {
                    _aiImageState.value = AiImageUiState.Success(
                        bitmap = outputBitmap,
                        modeInfo = "Gemini Image AI"
                    )
                } else {
                    val fallbackBmp = CollageGenerator.enhanceDocumentationPhoto(
                        source = sourceBitmap,
                        locationTitle = _locationInput.value,
                        dateLabel = _displayDateEnInput.value,
                        speedSummary = speedSummary,
                        instructionText = prompt.trim(),
                        addTechnicalStamp = addTechnicalStamp
                    )
                    _aiImageState.value = AiImageUiState.Success(
                        bitmap = fallbackBmp,
                        modeInfo = "Smart Studio Enhancer (On-Device)"
                    )
                }
            } catch (e: CancellationException) {
                if (e !is TimeoutCancellationException) throw e
                val fallbackBmp = CollageGenerator.enhanceDocumentationPhoto(
                    source = sourceBitmap,
                    locationTitle = _locationInput.value,
                    dateLabel = _displayDateEnInput.value,
                    speedSummary = speedSummary,
                    instructionText = prompt.trim(),
                    addTechnicalStamp = addTechnicalStamp
                )
                _aiImageState.value = AiImageUiState.Success(
                    bitmap = fallbackBmp,
                    aiInspectionNote = "Foto telah ditingkatkan kecerahan/ketajamannya & diberi stempel lokasi secara otomatis di perangkat.",
                    modeInfo = "Smart Studio Enhancer (Mode Instan)"
                )
            } catch (e: Exception) {
                Log.w(TAG, "Cloud image model quota/auth limited, using Smart Studio Enhancer fallback", e)
                val fallbackBmp = CollageGenerator.enhanceDocumentationPhoto(
                    source = sourceBitmap,
                    locationTitle = _locationInput.value,
                    dateLabel = _displayDateEnInput.value,
                    speedSummary = speedSummary,
                    instructionText = prompt.trim(),
                    addTechnicalStamp = addTechnicalStamp
                )
                _aiImageState.value = AiImageUiState.Success(
                    bitmap = fallbackBmp,
                    aiInspectionNote = "Foto Anda telah diproses otomatis menggunakan Smart Studio Enhancer (Penerangan AP + Stempel Dokumentasi PM).",
                    modeInfo = "Smart Studio Enhancer"
                )
            }
        }
    }

    fun addGeneratedAiImageToCollage(context: Context, bitmap: Bitmap) {
        val slot = nextAvailableCameraSlot()
        setCollageSlotBitmap(context, slot, bitmap)
    }

    // --- Multi-Turn Chatbot ---
    fun setChatModel(option: ChatModelOption) {
        _selectedChatModel.value = option
    }

    fun sendChatMessage(userText: String) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty() || _isChatLoading.value) return

        val currentModelOption = _selectedChatModel.value
        val activeConfig = aiProviderConfig.value
        val userMsg = ChatMessageUi(
            id = System.currentTimeMillis(),
            isUser = true,
            text = trimmed
        )
        val priorHistory = _chatMessages.value
        _chatMessages.value = priorHistory + userMsg
        _isChatLoading.value = true
        _chatError.value = null

        viewModelScope.launch {
            val responseId = System.currentTimeMillis() + 1L

            if (activeConfig.hasCustomConfig) {
                val modelLabel = if (activeConfig.providerType == "NINE_ROUTER") {
                    "9Router • ${activeConfig.nineRouterModel.substringAfterLast('/')}"
                } else {
                    "Gemini • ${activeConfig.geminiDirectModel}"
                }
                _chatMessages.value = _chatMessages.value + ChatMessageUi(
                    id = responseId,
                    isUser = false,
                    text = "...",
                    modelUsed = modelLabel,
                    isStreaming = true
                )
                val historyPairs = priorHistory.map { it.isUser to it.text }
                val res = CustomAiClient.sendChatCompletion(
                    config = activeConfig,
                    systemInstruction = chatSystemPromptText,
                    history = historyPairs,
                    newMessage = trimmed
                )
                res.onSuccess { reply ->
                    _chatMessages.value = _chatMessages.value.map { item ->
                        if (item.id == responseId) {
                            item.copy(text = reply, isStreaming = false)
                        } else {
                            item
                        }
                    }
                }.onFailure { err ->
                    _chatMessages.value = _chatMessages.value.filterNot { it.id == responseId }
                    _chatError.value = err.localizedMessage ?: "Gagal menghubungi API 9Router / Gemini."
                }
                _isChatLoading.value = false
                return@launch
            }

            val accumulated = StringBuilder()
            try {
                val activeModel = when (currentModelOption) {
                    ChatModelOption.FLASH -> flashChatModel
                    ChatModelOption.ARGON -> argonChatModel
                    ChatModelOption.FLASH_LITE -> flashLiteChatModel
                }

                val sdkHistory = priorHistory.map { msg ->
                    content(role = if (msg.isUser) "user" else "model") {
                        text(msg.text)
                    }
                }
                val chat = activeModel.startChat(history = sdkHistory)

                _chatMessages.value = _chatMessages.value + ChatMessageUi(
                    id = responseId,
                    isUser = false,
                    text = "...",
                    modelUsed = currentModelOption.label,
                    isStreaming = true
                )

                withTimeout(25_000L) {
                    chat.sendMessageStream(trimmed).collect { chunk ->
                        val partText = chunk.text.orEmpty()
                        accumulated.append(partText)
                        val currentText = accumulated.toString().ifEmpty { "..." }
                        _chatMessages.value = _chatMessages.value.map { item ->
                            if (item.id == responseId) {
                                item.copy(text = currentText, isStreaming = true)
                            } else {
                                item
                            }
                        }
                    }
                }

                val finalReply = accumulated.toString().trim()
                if (finalReply.isNotEmpty()) {
                    _chatMessages.value = _chatMessages.value.map { item ->
                        if (item.id == responseId) {
                            item.copy(text = finalReply, isStreaming = false)
                        } else {
                            item
                        }
                    }
                } else {
                    _chatMessages.value = _chatMessages.value.filterNot { it.id == responseId }
                    _chatError.value = "Model mengembalikan pesan kosong."
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Chat timed out", e)
                _chatMessages.value = _chatMessages.value.filterNot { it.id == responseId }
                _chatError.value = "Koneksi Firebase AI timeout di HP fisik. Klik tombol 'Atur API' di kanan atas untuk memakai API 9Router Anda."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Chat failed", e)
                _chatMessages.value = _chatMessages.value.filterNot { it.id == responseId }
                _chatError.value = e.localizedMessage ?: "Gagal mengirim pesan."
            } finally {
                _isChatLoading.value = false
            }
        }
    }

    fun clearChatHistory() {
        _chatMessages.value = listOf(
            ChatMessageUi(
                isUser = false,
                text = "Riwayat chat telah dibersihkan. Silakan ajukan pertanyaan terkait PM WiFi, Speedtest, atau laporan harian Anda.",
                modelUsed = _selectedChatModel.value.label
            )
        )
        _chatError.value = null
    }

    companion object {
        fun provideFactory(repository: PmRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PmMainViewModel(repository)
            }
        }
    }
}
