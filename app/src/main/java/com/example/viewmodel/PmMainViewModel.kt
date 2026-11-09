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
import com.example.data.NasUploadConfig
import com.example.data.PmReportEntity
import com.example.data.PmRepository
import com.example.data.TodayReportConfig
import com.example.BuildConfig
import com.example.util.AiProviderConfig
import com.example.util.CollageGenerator
import com.example.util.CollageResult
import com.example.util.CustomAiClient
import com.example.util.ExtractedCmFields
import com.example.util.GpsFix
import com.example.util.LiveSpeedtestResult
import com.example.util.LocationCandidateInput
import com.example.util.LocationMatch
import com.example.util.NasLocationEntry
import com.example.util.NasLocationMatcher
import com.example.util.NasUploadClient
import com.example.util.NasUploadResult
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

private const val TAG = "PmMainViewModel"

sealed interface SpeedtestScanState {
    data object Idle : SpeedtestScanState
    data object Scanning : SpeedtestScanState
    data class Success(val downloadMbps: String, val uploadMbps: String, val providerLabel: String = "AI") : SpeedtestScanState
    data class Error(val message: String, val suggestApiConfig: Boolean = false) : SpeedtestScanState
}

sealed interface CmScanState {
    data object Idle : CmScanState
    data object Scanning : CmScanState
    data class Success(val fields: ExtractedCmFields, val providerLabel: String = "AI") : CmScanState
    data class Error(val message: String, val suggestApiConfig: Boolean = false) : CmScanState
}

sealed interface LocationDetectUiState {
    data object Idle : LocationDetectUiState
    data object Detecting : LocationDetectUiState
    data class MatchedHigh(
        val location: String,
        val confidencePercent: Int,
        val refId: String,
        val reason: String,
        val refThumbFile: File
    ) : LocationDetectUiState
    data class SuggestedMedium(
        val location: String,
        val confidencePercent: Int,
        val refId: String,
        val reason: String,
        val refThumbFile: File
    ) : LocationDetectUiState
    data class Skipped(val message: String) : LocationDetectUiState
}

data class BuiltInSpeedtestUiState(
    val isRunning: Boolean = false,
    val phase: String = "IDLE", // "IDLE", "PING", "DOWNLOAD", "UPLOAD", "DONE"
    val currentGaugeMbps: Double = 0.0,
    val pingMs: Int = 3,
    val dlLoadedPingMs: Int = 362,
    val ulLoadedPingMs: Int = 327,
    val jitterMs: Int = 0,
    val downloadMbps: Double = 0.0,
    val uploadMbps: Double = 0.0,
    val progress: Float = 0f,
    val ispName: String = "Biznet",
    val deviceName: String = Build.MODEL ?: "Android Device",
    val serverSponsor: String = "PT.Jaringan Inte...",
    val serverCity: String = "Jakarta",
    val testId: String = "11995232333",
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

    // Tracks whether the current _locationInput was auto-filled by AI from Foto 1
    private val _locationAutoFilled = MutableStateFlow(false)
    val locationAutoFilled: StateFlow<Boolean> = _locationAutoFilled.asStateFlow()

    private val _locationDetectState = MutableStateFlow<LocationDetectUiState>(LocationDetectUiState.Idle)
    val locationDetectState: StateFlow<LocationDetectUiState> = _locationDetectState.asStateFlow()

    private val _currentPhoto1GpsFix = MutableStateFlow<GpsFix?>(null)
    val currentPhoto1GpsFix: StateFlow<GpsFix?> = _currentPhoto1GpsFix.asStateFlow()

    private var locationDetectJob: Job? = null

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

    private val _lastBuiltInPingMs = MutableStateFlow<Int?>(null)

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

    private val _slotOriginalUris = MutableStateFlow<List<Uri?>>(listOf(null, null, null, null))

    val collageBitmaps: StateFlow<List<Bitmap>> = _collageSlots
        .map { list -> list.filterNotNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _includeCollageHeader = MutableStateFlow(true)
    val includeCollageHeader: StateFlow<Boolean> = _includeCollageHeader.asStateFlow()

    private val _fitEntirePhoto = MutableStateFlow(true)
    val fitEntirePhoto: StateFlow<Boolean> = _fitEntirePhoto.asStateFlow()

    private val _collageState = MutableStateFlow<CollageUiState>(CollageUiState.Idle)
    val collageState: StateFlow<CollageUiState> = _collageState.asStateFlow()

    // Saved Locations (User-typed only), Report Config, AI Provider Config & NAS Upload Config
    val savedLocations: StateFlow<List<String>> = repository.savedLocationsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reportConfig: StateFlow<TodayReportConfig> = repository.reportConfigFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TodayReportConfig())

    val aiProviderConfig: StateFlow<AiProviderConfig> = repository.aiProviderConfigFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiProviderConfig())

    val nasUploadConfig: StateFlow<NasUploadConfig> = repository.nasUploadConfigFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NasUploadConfig())

    private val _isTestingNasConnection = MutableStateFlow(false)
    val isTestingNasConnection: StateFlow<Boolean> = _isTestingNasConnection.asStateFlow()

    private val _nasTestStatus = MutableStateFlow<String?>(null)
    val nasTestStatus: StateFlow<String?> = _nasTestStatus.asStateFlow()

    private val _nasTestSuccess = MutableStateFlow<Boolean?>(null)
    val nasTestSuccess: StateFlow<Boolean?> = _nasTestSuccess.asStateFlow()

    private val _nasSnackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val nasSnackbarEvents: SharedFlow<String> = _nasSnackbarEvents.asSharedFlow()

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
        "Anda adalah Asisten Teknis Senior ICT Operation & Preventive Maintenance (PM) WiFi Publik Bandara (CGK). " +
            "Bantu teknisi dalam menganalisis kualitas bandwidth/speedtest WiFi, menyusun laporan Corrective Maintenance (CM) dan Preventive Maintenance (PM), " +
            "serta memberikan solusi cepat kendala perangkat Access Point dengan bahasa Indonesia yang ringkas, jelas, dan profesional."

    // Corrective Maintenance AI Screenshot Scan State (Supports up to 10 screenshots/photos)
    private val _cmScanBitmaps = MutableStateFlow<List<Bitmap>>(emptyList())
    val cmScanBitmaps: StateFlow<List<Bitmap>> = _cmScanBitmaps.asStateFlow()

    private val _cmScanState = MutableStateFlow<CmScanState>(CmScanState.Idle)
    val cmScanState: StateFlow<CmScanState> = _cmScanState.asStateFlow()

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

    private val cmExtractorSchema = Schema.obj(
        properties = mapOf(
            "activityTitle" to Schema.string(
                description = "Judul kegiatan Corrective Maintenance tanpa emoji 🛠️ di awal (contoh: 'Pembersihan & Pengujian Speed Access Point Wifi Publik')."
            ),
            "location" to Schema.string(
                description = "Nama lokasi Corrective Maintenance yang tertera pada screenshot/foto (contoh: 'Plaza Kedatangan Gate 1 Sebelah kanan, Terminal 3')."
            ),
            "equipmentName" to Schema.string(
                description = "Nama peralatan (contoh: 'Access Point Wifi Publik')."
            ),
            "kendala" to Schema.string(
                description = "Kendala atau temuan yang tertera pada laporan CM (contoh: 'Temuan AP Tampak Tidak Bersih')."
            ),
            "issue" to Schema.string(
                description = "Issue teknis pada laporan CM (contoh: 'No Issue' atau detail issue)."
            ),
            "actionLines" to Schema.string(
                description = "Daftar tindakan/action per baris diawali '-' dipisahkan newline (contoh: '-Team Melakukan Pembersihan\\n-Team Melakukan Pengujian Speedtest')."
            ),
            "statusLines" to Schema.string(
                description = "Daftar status akhir per baris diawali '-' dipisahkan newline (contoh: '- AP Sudah Bersih✅\\n- AP Terpantau Normal ✅\\n- Speedtest Terpantau Normal✅')."
            )
        )
    )

    private val cmExtractorModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = cmExtractorSchema
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
        _locationAutoFilled.value = false
        if (_locationDetectState.value is LocationDetectUiState.MatchedHigh) {
            _locationDetectState.value = LocationDetectUiState.Idle
        }
    }

    fun applySuggestedLocation(context: Context, suggested: LocationDetectUiState.SuggestedMedium) {
        _locationInput.value = suggested.location
        _locationAutoFilled.value = false
        _locationDetectState.value = LocationDetectUiState.MatchedHigh(
            location = suggested.location,
            confidencePercent = suggested.confidencePercent,
            refId = suggested.refId,
            reason = suggested.reason,
            refThumbFile = suggested.refThumbFile
        )
        if (_collageSlots.value.any { it != null }) {
            generateCollage(context, debounceMs = 100L)
        }
    }

    fun cancelAutoDetectedLocation(context: Context) {
        locationDetectJob?.cancel()
        _locationInput.value = ""
        _locationAutoFilled.value = false
        _locationDetectState.value = LocationDetectUiState.Idle
        if (_collageSlots.value.any { it != null }) {
            generateCollage(context, debounceMs = 100L)
        }
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
            val (isp, device, server) = NetworkTelemetryEngine.detectIspDeviceAndServer(context)
            _builtInSpeedtestState.value = _builtInSpeedtestState.value.copy(
                ispName = isp,
                deviceName = device,
                serverSponsor = server.sponsor,
                serverCity = server.city
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

            val result = NetworkTelemetryEngine.runRealSpeedtest(context) { phase, currentMbps, ping, dlPing, ulPing, jitter, down, up, prog, srvSponsor, srvCity, tId ->
                _builtInSpeedtestState.value = _builtInSpeedtestState.value.copy(
                    isRunning = phase != "DONE",
                    phase = phase,
                    currentGaugeMbps = currentMbps,
                    pingMs = ping,
                    dlLoadedPingMs = dlPing,
                    ulLoadedPingMs = ulPing,
                    jitterMs = jitter,
                    downloadMbps = down,
                    uploadMbps = up,
                    progress = prog,
                    serverSponsor = srvSponsor,
                    serverCity = srvCity,
                    testId = tId
                )
            }

            // 1. Automatically populate Download & Upload Mbps in the PM Report form!
            val downStr = String.format(Locale.US, "%.1f", result.downloadMbps)
            val upStr = String.format(Locale.US, "%.1f", result.uploadMbps)
            _downloadMbpsInput.value = downStr
            _uploadMbpsInput.value = upStr
            _lastBuiltInPingMs.value = result.pingMs

            // 2. Automatically take screenshot of the Speedtest result and place it in Slot 3 (index 2 = FOTO 3)!
            val screenshotBmp = NetworkTelemetryEngine.renderSpeedtestScreenshotBitmap(result)
            _speedtestBitmap.value = screenshotBmp
            _scanState.value = SpeedtestScanState.Success(
                downloadMbps = downStr,
                uploadMbps = upStr,
                providerLabel = "Speedtest.net (ID ${result.testId})"
            )
            setCollageSlotBitmap(context, slotIndex = 2, bitmap = screenshotBmp, originalUri = null)

            _builtInSpeedtestState.value = _builtInSpeedtestState.value.copy(
                isRunning = false,
                phase = "DONE",
                downloadMbps = result.downloadMbps,
                uploadMbps = result.uploadMbps,
                pingMs = result.pingMs,
                dlLoadedPingMs = result.dlLoadedPingMs,
                ulLoadedPingMs = result.ulLoadedPingMs,
                jitterMs = result.jitterMs,
                ispName = result.ispName,
                deviceName = result.deviceName,
                serverSponsor = result.serverSponsor,
                serverCity = result.serverCity,
                testId = result.testId,
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
                captureWifiAnalyzerToSlot4(context, selectedAp = null, onCaptured = onCaptured)
            }
        }
    }

    fun captureWifiAnalyzerToSlot4(
        context: Context,
        selectedAp: WifiApItem? = null,
        onCaptured: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val currentBand = _selectedWifiBand.value
            val allAps = _wifiAccessPoints.value
            val filtered = allAps.filter { it.bandGhz == currentBand }.ifEmpty { allAps }
            val bmp = NetworkTelemetryEngine.renderWifiAnalyzerScreenshotBitmap(
                accessPoints = filtered,
                selectedBand = currentBand,
                selectedAp = selectedAp
            )
            // Automatically place in Slot 4 (index 3 = FOTO 4 • WIFI ANALYZER)
            setCollageSlotBitmap(context, slotIndex = 3, bitmap = bmp, originalUri = null)
            _wifiAnalyzerCapturedToSlot4.value = true
            onCaptured()
        }
    }

    fun renameWifiAccessPoint(
        context: Context,
        bssid: String,
        newSsid: String,
        onUpdated: (WifiApItem?) -> Unit = {}
    ) {
        val cleanSsid = newSsid.trim()
        if (cleanSsid.isBlank()) return
        val updatedList = _wifiAccessPoints.value.map { ap ->
            if (ap.bssid.equals(bssid, ignoreCase = true)) {
                ap.copy(ssid = cleanSsid, isHidden = false)
            } else {
                ap
            }
        }
        _wifiAccessPoints.value = updatedList
        val updatedAp = updatedList.firstOrNull { it.bssid.equals(bssid, ignoreCase = true) }
        captureWifiAnalyzerToSlot4(context, selectedAp = updatedAp)
        onUpdated(updatedAp)
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
                setCollageSlotBitmap(context, targetSlot, bmp, originalUri = uri)
                onLoaded(targetSlot)
            }
        }
    }

    fun setCollageSlotBitmap(
        context: Context,
        slotIndex: Int,
        bitmap: Bitmap?,
        originalUri: Uri? = null
    ) {
        val updated = _collageSlots.value.toMutableList()
        val updatedUris = _slotOriginalUris.value.toMutableList()
        if (slotIndex in 0..3) {
            updated[slotIndex] = bitmap
            updatedUris[slotIndex] = if (bitmap != null) originalUri else null
            _collageSlots.value = updated
            _slotOriginalUris.value = updatedUris
            if (slotIndex == 0) {
                if (bitmap != null) {
                    onPhoto1CapturedOrReplaced(context, bitmap)
                } else {
                    onPhoto1Removed()
                }
            }
            if (updated.any { it != null }) {
                generateCollage(context)
            } else {
                _collageState.value = CollageUiState.Idle
            }
        }
    }

    private fun onPhoto1Removed() {
        locationDetectJob?.cancel()
        _currentPhoto1GpsFix.value = null
        _locationDetectState.value = LocationDetectUiState.Idle
        if (_locationAutoFilled.value) {
            _locationInput.value = ""
            _locationAutoFilled.value = false
        }
    }

    fun refreshNasLocationRefsInBackground(context: Context, forceRefresh: Boolean = false) {
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            val nasConfig = repository.nasUploadConfigFlow.first()
            if (nasConfig.serverUrl.isBlank() || nasConfig.token.isBlank()) return@launch
            if (!NasUploadClient.isNetworkAvailable(appContext)) return@launch
            NasLocationMatcher.getOrFetchLocationRefs(
                context = appContext,
                serverUrl = nasConfig.serverUrl,
                token = nasConfig.token,
                forceRefresh = forceRefresh
            )
        }
    }

    private fun onPhoto1CapturedOrReplaced(context: Context, photo1Bitmap: Bitmap) {
        val appContext = context.applicationContext
        locationDetectJob?.cancel()

        locationDetectJob = viewModelScope.launch(Dispatchers.IO) {
            // Capture a single GPS fix for Foto 1 in parallel (4s timeout)
            val canOverwriteLocation = _locationInput.value.isBlank() || _locationAutoFilled.value
            if (!canOverwriteLocation) {
                // User already typed/selected a location manually — never overwrite it; just record GPS silently
                _currentPhoto1GpsFix.value = NasLocationMatcher.captureSingleGpsFix(appContext, timeoutMs = 4_000L)
                return@launch
            }

            val nasConfig = repository.nasUploadConfigFlow.first()
            if (!nasConfig.autoDetectLocationFromPhoto1) {
                _locationDetectState.value = LocationDetectUiState.Skipped("Deteksi lokasi nonaktif")
                _currentPhoto1GpsFix.value = NasLocationMatcher.captureSingleGpsFix(appContext, timeoutMs = 4_000L)
                return@launch
            }
            if (nasConfig.serverUrl.isBlank() || nasConfig.token.isBlank()) {
                _locationDetectState.value = LocationDetectUiState.Skipped("Token NAS belum diisi")
                _currentPhoto1GpsFix.value = NasLocationMatcher.captureSingleGpsFix(appContext, timeoutMs = 4_000L)
                return@launch
            }

            val aiConfig = repository.aiProviderConfigFlow.first()
            val isAiConfigured = aiConfig.hasCustomConfig || aiConfig.providerType == "FIREBASE"
            if (!isAiConfigured) {
                _locationDetectState.value = LocationDetectUiState.Skipped("AI belum diatur")
                _currentPhoto1GpsFix.value = NasLocationMatcher.captureSingleGpsFix(appContext, timeoutMs = 4_000L)
                return@launch
            }

            if (!NasUploadClient.isNetworkAvailable(appContext)) {
                _locationDetectState.value = LocationDetectUiState.Skipped("Offline, deteksi dilewati")
                _currentPhoto1GpsFix.value = NasLocationMatcher.captureSingleGpsFix(appContext, timeoutMs = 4_000L)
                return@launch
            }

            _locationDetectState.value = LocationDetectUiState.Detecting

            val (gpsFix, refsResult) = coroutineScope {
                val gpsDeferred = async {
                    NasLocationMatcher.captureSingleGpsFix(appContext, timeoutMs = 4_000L)
                }
                val refsDeferred = async {
                    NasLocationMatcher.getOrFetchLocationRefs(
                        context = appContext,
                        serverUrl = nasConfig.serverUrl,
                        token = nasConfig.token,
                        forceRefresh = false
                    )
                }
                gpsDeferred.await() to refsDeferred.await()
            }
            _currentPhoto1GpsFix.value = gpsFix

            val allEntries = refsResult.getOrElse { err ->
                _locationDetectState.value = LocationDetectUiState.Skipped(
                    err.message ?: "Gagal memuat lokasi referensi NAS"
                )
                return@launch
            }

            if (allEntries.isEmpty()) {
                _locationDetectState.value = LocationDetectUiState.Skipped("Belum ada lokasi referensi di NAS")
                return@launch
            }

            val (nearbyEntries, allByDateEntries) = NasLocationMatcher.partitionCandidatesByGpsAndDate(
                entries = allEntries,
                newGps = gpsFix
            )

            var totalBatchesRun = 0
            var bestOverallMatch: LocationMatch? = null
            var lastBatchError: String? = null

            suspend fun runBatchesOn(entriesToTest: List<NasLocationEntry>) {
                val chunks = entriesToTest.chunked(20)
                for (chunk in chunks) {
                    if (totalBatchesRun >= 5) break
                    // Build 1 candidate per location (refs[0], fallback refs[1])
                    val candidates = mutableListOf<LocationCandidateInput>()
                    for (entry in chunk) {
                        val ref0 = entry.refs.getOrNull(0)
                        val ref1 = entry.refs.getOrNull(1)
                        var chosenRef = ref0
                        var thumbFile = if (ref0 != null) {
                            NasLocationMatcher.ensureRefThumbnailFile(appContext, nasConfig.serverUrl, ref0)
                        } else null
                        if (thumbFile == null && ref1 != null) {
                            chosenRef = ref1
                            thumbFile = NasLocationMatcher.ensureRefThumbnailFile(appContext, nasConfig.serverUrl, ref1)
                        }
                        if (chosenRef != null && thumbFile != null) {
                            candidates.add(
                                LocationCandidateInput(
                                    candidateNumber = candidates.size + 1,
                                    location = entry.location,
                                    refId = chosenRef.id,
                                    refDate = chosenRef.date,
                                    refShift = chosenRef.shift,
                                    thumbFile = thumbFile
                                )
                            )
                        }
                    }
                    if (candidates.isEmpty()) continue

                    totalBatchesRun++
                    val batchRes = CustomAiClient.matchApLocation(
                        config = aiConfig,
                        newPhoto = photo1Bitmap,
                        candidates = candidates
                    )
                    batchRes.onSuccess { match ->
                        if (match.match > 0 && match.matchedCandidate != null) {
                            val currentBest = bestOverallMatch
                            if (currentBest == null || match.confidence > currentBest.confidence) {
                                bestOverallMatch = match
                            }
                        }
                    }.onFailure { err ->
                        lastBatchError = err.localizedMessage ?: "Gagal mencocokkan lokasi dengan AI"
                    }

                    // Early stop if confidence >= 0.85
                    val currentBest = bestOverallMatch
                    if (currentBest != null && currentBest.match > 0 && currentBest.confidence >= 0.85) {
                        break
                    }
                }
            }

            if (nearbyEntries.isNotEmpty()) {
                runBatchesOn(nearbyEntries)
                val nearbyBest = bestOverallMatch
                if ((nearbyBest == null || nearbyBest.confidence < 0.80) && totalBatchesRun < 5) {
                    val testedLocations = nearbyEntries.map { it.location }.toSet()
                    val remainingByDate = allByDateEntries.filter { it.location !in testedLocations }
                    if (remainingByDate.isNotEmpty()) {
                        runBatchesOn(remainingByDate)
                    }
                }
            } else {
                runBatchesOn(allByDateEntries)
            }

            // Re-verify that user did not type a manual location while AI was running
            if (_locationInput.value.isNotBlank() && !_locationAutoFilled.value) {
                _locationDetectState.value = LocationDetectUiState.Idle
                return@launch
            }

            val finalMatch = bestOverallMatch
            val matchedCand = finalMatch?.matchedCandidate
            if (finalMatch != null && finalMatch.match > 0 && matchedCand != null && finalMatch.confidence >= 0.80) {
                val pct = (finalMatch.confidence * 100.0).roundToInt().coerceIn(80, 100)
                _locationInput.value = matchedCand.location
                _locationAutoFilled.value = true
                _locationDetectState.value = LocationDetectUiState.MatchedHigh(
                    location = matchedCand.location,
                    confidencePercent = pct,
                    refId = matchedCand.refId,
                    reason = finalMatch.reason.ifBlank { "Perangkat AP & latar sekitar cocok dengan foto referensi." },
                    refThumbFile = matchedCand.thumbFile
                )
                if (_collageSlots.value.any { it != null }) {
                    generateCollage(appContext, debounceMs = 100L)
                }
            } else if (finalMatch != null && finalMatch.match > 0 && matchedCand != null && finalMatch.confidence >= 0.55) {
                val pct = (finalMatch.confidence * 100.0).roundToInt().coerceIn(55, 79)
                if (_locationAutoFilled.value) {
                    _locationInput.value = ""
                    _locationAutoFilled.value = false
                }
                _locationDetectState.value = LocationDetectUiState.SuggestedMedium(
                    location = matchedCand.location,
                    confidencePercent = pct,
                    refId = matchedCand.refId,
                    reason = finalMatch.reason.ifBlank { "Kemiripan sedang dengan foto referensi." },
                    refThumbFile = matchedCand.thumbFile
                )
            } else {
                if (_locationAutoFilled.value) {
                    _locationInput.value = ""
                    _locationAutoFilled.value = false
                }
                val msg = if (finalMatch == null && !lastBatchError.isNullOrBlank()) {
                    "Deteksi lokasi gagal: ${lastBatchError!!.take(60)}"
                } else {
                    "Lokasi baru, belum pernah difoto. Isi manual."
                }
                _locationDetectState.value = LocationDetectUiState.Skipped(msg)
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

    // --- NAS Upload Config & Connection Test ---
    fun saveNasUploadConfig(context: Context, config: NasUploadConfig) {
        viewModelScope.launch {
            repository.saveNasUploadConfig(config)
            _nasTestStatus.value = null
            _nasTestSuccess.value = null
            if (config.token.isNotBlank()) {
                refreshNasLocationRefsInBackground(context.applicationContext, forceRefresh = true)
            }
            if (config.token.isNotBlank() && config.autoUploadOnSave) {
                retryPendingOrFailedUploads(context.applicationContext)
            }
        }
    }

    fun testNasConnection(serverUrl: String, token: String) {
        viewModelScope.launch {
            _isTestingNasConnection.value = true
            _nasTestStatus.value = "Menghubungi server NAS..."
            _nasTestSuccess.value = null
            val res = NasUploadClient.testConnection(serverUrl, token)
            _isTestingNasConnection.value = false
            res.onSuccess { msg ->
                _nasTestStatus.value = msg
                _nasTestSuccess.value = true
            }.onFailure { err ->
                _nasTestStatus.value = err.message ?: "Gagal menghubungi server NAS"
                _nasTestSuccess.value = false
            }
        }
    }

    fun clearNasTestStatus() {
        _nasTestStatus.value = null
        _nasTestSuccess.value = null
    }

    // --- Manual Screenshot Speedtest Multimodal Extraction (also puts screenshot into Slot 3) ---
    fun onSpeedtestImageSelected(context: Context, uri: Uri, autoScan: Boolean = true) {
        viewModelScope.launch {
            val bitmap = CollageGenerator.loadBitmapFromUri(context, uri, maxDimension = 1280)
            if (bitmap != null) {
                _speedtestBitmap.value = bitmap
                _lastBuiltInPingMs.value = null
                setCollageSlotBitmap(context, slotIndex = 2, bitmap = bitmap, originalUri = uri)
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

    // --- Save Daily PM Entry to Today Report, Persist Individual NAS Slot Photos & Auto-Upload ---
    fun saveCurrentPmToTodayReport(context: Context, onSaved: (Int) -> Unit = {}) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            val currentCollage = (_collageState.value as? CollageUiState.Ready)?.result
            val cleanLoc = _locationInput.value.trim().ifBlank { "-" }
            val clientRef = UUID.randomUUID().toString()
            val slotsSnapshot = _collageSlots.value
            val urisSnapshot = _slotOriginalUris.value
            val cameraFilesSnapshot = (0..3).map { CollageGenerator.getLastCameraFileForSlot(it) }
            val nowMs = System.currentTimeMillis()

            val currentReportConfig = repository.reportConfigFlow.first()
            val shiftCode = if (
                currentReportConfig.shift.contains("Malam", ignoreCase = true) ||
                currentReportConfig.shift.contains("SH2", ignoreCase = true)
            ) {
                "SH2"
            } else {
                "SH1"
            }
            val pingMsValue = _lastBuiltInPingMs.value ?: if (_builtInSpeedtestState.value.phase == "DONE") {
                _builtInSpeedtestState.value.pingMs
            } else {
                null
            }

            // Save 4 separate slot photos + thumbnails in filesDir/pm_uploads/<clientRef>/
            val currentGps = _currentPhoto1GpsFix.value
            if (slotsSnapshot.any { it != null }) {
                NasUploadClient.saveSlotsForNasUpload(
                    context = appContext,
                    clientRef = clientRef,
                    slotBitmaps = slotsSnapshot,
                    slotOriginalUris = urisSnapshot,
                    slotOriginalFiles = cameraFilesSnapshot,
                    pingMs = pingMsValue,
                    shiftCode = shiftCode,
                    saveTimestampMs = nowMs,
                    gpsFix = currentGps
                )
            }

            val currentNasConfig = repository.nasUploadConfigFlow.first()
            val initialUploadStatus = if (currentNasConfig.autoUploadOnSave) "PENDING" else "OFF"

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
                collageSizeBytes = currentCollage?.sizeBytes ?: 0L,
                timestamp = nowMs,
                uploadClientRef = clientRef,
                uploadStatus = initialUploadStatus,
                uploadedReportId = null,
                uploadError = null,
                gpsLat = currentGps?.lat,
                gpsLon = currentGps?.lon,
                gpsAccuracyM = currentGps?.accuracyM
            )
            // Automatically remember this location so the user doesn't need to re-type next time!
            if (_locationInput.value.isNotBlank()) {
                repository.addCustomLocation(_locationInput.value.trim())
            }
            val insertedId = repository.insertPmReport(entity).toInt()
            val nextIndex = pmReportsForSelectedDate.value.size + 1
            onSaved(nextIndex)

            if (currentNasConfig.autoUploadOnSave && currentNasConfig.token.isNotBlank()) {
                uploadPmReportToNas(appContext, insertedId, showFeedbackSnackbar = true)
            }
        }
    }

    fun uploadPmReportToNas(
        context: Context,
        reportId: Int,
        showFeedbackSnackbar: Boolean = true
    ) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            val currentNasConfig = repository.nasUploadConfigFlow.first()
            if (currentNasConfig.token.isBlank()) {
                if (showFeedbackSnackbar) {
                    _nasSnackbarEvents.tryEmit("Atur Upload Token NAS terlebih dahulu lewat tombol NAS di atas.")
                }
                return@launch
            }

            val report = repository.getPmReportById(reportId) ?: return@launch
            if (report.uploadStatus == "UPLOADING") return@launch

            val clientRef = report.uploadClientRef?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
            repository.updatePmUploadStatus(
                id = report.id,
                status = "UPLOADING",
                uploadedId = report.uploadedReportId,
                error = null,
                clientRef = clientRef
            )

            val currentReportConfig = repository.reportConfigFlow.first()
            val shiftCode = if (
                currentReportConfig.shift.contains("Malam", ignoreCase = true) ||
                currentReportConfig.shift.contains("SH2", ignoreCase = true)
            ) {
                "SH2"
            } else {
                "SH1"
            }

            val pingMsValue = _lastBuiltInPingMs.value ?: if (_builtInSpeedtestState.value.phase == "DONE") {
                _builtInSpeedtestState.value.pingMs
            } else {
                null
            }

            // If upload directory does not have slot files yet (e.g. older report or manual trigger while slots are active), save current slots if matching
            val hasSavedPhotos = NasUploadClient.hasSavedPhotosForClientRef(appContext, clientRef)
            if (!hasSavedPhotos && _collageSlots.value.any { it != null }) {
                val cameraFilesSnapshot = (0..3).map { CollageGenerator.getLastCameraFileForSlot(it) }
                val reportGps = if (report.gpsLat != null && report.gpsLon != null) {
                    GpsFix(report.gpsLat, report.gpsLon, report.gpsAccuracyM)
                } else {
                    _currentPhoto1GpsFix.value
                }
                NasUploadClient.saveSlotsForNasUpload(
                    context = appContext,
                    clientRef = clientRef,
                    slotBitmaps = _collageSlots.value,
                    slotOriginalUris = _slotOriginalUris.value,
                    slotOriginalFiles = cameraFilesSnapshot,
                    pingMs = pingMsValue,
                    shiftCode = shiftCode,
                    saveTimestampMs = report.timestamp,
                    gpsFix = reportGps
                )
            }

            val cmList = repository.getCmListForDateSync(report.dateKey)
            val matchingCm = cmList.firstOrNull { cm ->
                cm.location.trim().equals(report.location.trim(), ignoreCase = true)
            }

            val updatedReport = report.copy(uploadClientRef = clientRef)
            when (
                val outcome = NasUploadClient.uploadPmReportToNas(
                    context = appContext,
                    serverUrl = currentNasConfig.serverUrl,
                    token = currentNasConfig.token,
                    report = updatedReport,
                    clientRef = clientRef,
                    fallbackShiftLabel = shiftCode,
                    matchingCm = matchingCm,
                    fallbackPingMs = pingMsValue
                )
            ) {
                is NasUploadResult.Success -> {
                    repository.updatePmUploadStatus(
                        id = report.id,
                        status = "SENT",
                        uploadedId = outcome.reportId,
                        error = null,
                        clientRef = clientRef
                    )
                    _nasSnackbarEvents.tryEmit("Terkirim ke NAS sebagai ${outcome.reportId}")
                    refreshNasLocationRefsInBackground(appContext, forceRefresh = true)
                }
                is NasUploadResult.Failure -> {
                    repository.updatePmUploadStatus(
                        id = report.id,
                        status = "FAILED",
                        uploadedId = report.uploadedReportId,
                        error = outcome.errorMessage,
                        clientRef = clientRef
                    )
                    if (showFeedbackSnackbar) {
                        _nasSnackbarEvents.tryEmit("Gagal upload NAS: ${outcome.errorMessage}")
                    }
                }
            }
        }
    }

    fun retryPendingOrFailedUploads(context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            val currentNasConfig = repository.nasUploadConfigFlow.first()
            if (currentNasConfig.token.isBlank() || !currentNasConfig.autoUploadOnSave) return@launch
            val pendingOrFailed = repository.getPendingOrFailedPmReports()
            if (pendingOrFailed.isEmpty()) return@launch
            for (report in pendingOrFailed) {
                uploadPmReportToNas(appContext, report.id, showFeedbackSnackbar = true)
            }
        }
    }

    fun deletePmReport(context: Context, id: Int) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            val existing = repository.getPmReportById(id)
            NasUploadClient.deleteUploadDirForClientRef(appContext, existing?.uploadClientRef)
            repository.deletePmReport(id)
        }
    }

    // --- Corrective Maintenance CRUD (Add, Edit & Delete) + AI Screenshot Detection (Up to 10 Photos) ---
    fun resetCmScanState() {
        _cmScanBitmaps.value = emptyList()
        _cmScanState.value = CmScanState.Idle
    }

    fun removeCmScanBitmapAt(index: Int) {
        val current = _cmScanBitmaps.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _cmScanBitmaps.value = current
            if (current.isEmpty()) {
                _cmScanState.value = CmScanState.Idle
            }
        }
    }

    fun onCmScreenshotsSelected(
        context: Context,
        uris: List<Uri>,
        append: Boolean = true,
        onExtracted: (ExtractedCmFields) -> Unit = {}
    ) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val existing = if (append) _cmScanBitmaps.value else emptyList()
            val remainingSlots = (10 - existing.size).coerceAtLeast(0)
            val targetUris = if (remainingSlots > 0) uris.take(remainingSlots) else uris.take(10)
            val newlyLoaded = targetUris.mapNotNull { uri ->
                CollageGenerator.loadBitmapFromUri(context, uri, maxDimension = 1280)
            }
            if (newlyLoaded.isNotEmpty()) {
                val combined = if (append && existing.size < 10) {
                    (existing + newlyLoaded).take(10)
                } else {
                    newlyLoaded.take(10)
                }
                _cmScanBitmaps.value = combined
                scanCmScreenshotBitmaps(combined, onExtracted = onExtracted)
            } else {
                _cmScanState.value = CmScanState.Error("Gagal membaca gambar dari perangkat.")
            }
        }
    }

    fun scanCmScreenshotBitmaps(
        bitmaps: List<Bitmap> = _cmScanBitmaps.value,
        onExtracted: (ExtractedCmFields) -> Unit = {}
    ) {
        val capped = bitmaps.take(10)
        if (capped.isEmpty()) {
            _cmScanState.value = CmScanState.Error("Pilih minimal 1 (maks 10) screenshot laporan CM terlebih dahulu.")
            return
        }
        val activeConfig = aiProviderConfig.value

        viewModelScope.launch {
            _cmScanState.value = CmScanState.Scanning

            if (activeConfig.hasCustomConfig) {
                val providerLabel = if (activeConfig.providerType == "NINE_ROUTER") {
                    "9Router (${activeConfig.nineRouterModel.substringAfterLast('/')})"
                } else {
                    "Gemini API"
                }
                val result = CustomAiClient.scanCmScreenshotImages(activeConfig, capped)
                result.onSuccess { fields ->
                    _cmScanState.value = CmScanState.Success(
                        fields = fields,
                        providerLabel = "$providerLabel • ${capped.size} Foto"
                    )
                    onExtracted(fields)
                }.onFailure { err ->
                    Log.e(TAG, "Custom AI CM screenshot scan failed", err)
                    _cmScanState.value = CmScanState.Error(
                        message = err.localizedMessage ?: "Gagal membaca screenshot CM lewat $providerLabel.",
                        suggestApiConfig = true
                    )
                }
                return@launch
            }

            try {
                val response = withTimeout(60_000L) {
                    cmExtractorModel.generateContent(
                        content {
                            capped.forEach { bmp ->
                                image(bmp)
                            }
                            text(
                                "Baca dan gabungkan informasi dari ${capped.size} screenshot laporan Corrective Maintenance (CM) grup WhatsApp atau foto temuan ini, lalu ekstrak ke-7 bagian berikut ke JSON: " +
                                    "activityTitle (judul kegiatan CM tanpa emoji 🛠️), location (lokasi), equipmentName (nama peralatan), " +
                                    "kendala (kendala/temuan), issue (issue teknis), actionLines (tindakan per baris diawali '-'), dan statusLines (status per baris diawali '-')."
                            )
                        }
                    )
                }
                val jsonText = response.text
                if (jsonText.isNullOrBlank()) {
                    _cmScanState.value = CmScanState.Error(
                        message = "AI mengembalikan hasil kosong.",
                        suggestApiConfig = true
                    )
                    return@launch
                }

                val extracted = CustomAiClient.parseCmFieldsFromJson(jsonText)
                _cmScanState.value = CmScanState.Success(
                    fields = extracted,
                    providerLabel = "Firebase AI • ${capped.size} Foto"
                )
                onExtracted(extracted)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "CM screenshot scan timed out", e)
                _cmScanState.value = CmScanState.Error(
                    message = "Waktu baca AI habis (60 detik). Anda juga dapat menghubungkan API 9Router lewat tombol 'Atur API'.",
                    suggestApiConfig = true
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "CM screenshot scan failed", e)
                _cmScanState.value = CmScanState.Error(
                    message = "Perlu API 9Router / Gemini untuk deteksi di HP fisik: ${e.localizedMessage ?: "Gagal"}",
                    suggestApiConfig = true
                )
            }
        }
    }

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
    private var collageJob: Job? = null

    fun onCollagePhotosSelected(context: Context, uris: List<Uri>) {
        viewModelScope.launch {
            val pickedUris = uris.take(4)
            val loadedPairs = pickedUris.mapNotNull { uri ->
                val bmp = CollageGenerator.loadBitmapFromUri(context, uri, maxDimension = 1280)
                if (bmp != null) uri to bmp else null
            }
            if (loadedPairs.isNotEmpty()) {
                val oldSlot0 = _collageSlots.value.getOrNull(0)
                val updated = _collageSlots.value.toMutableList()
                val updatedUris = _slotOriginalUris.value.toMutableList()
                var loadIdx = 0
                for (i in 0..3) {
                    if (updated[i] == null && loadIdx < loadedPairs.size) {
                        val (u, b) = loadedPairs[loadIdx++]
                        updated[i] = b
                        updatedUris[i] = u
                        if (i in 0..1) CollageGenerator.clearLastCameraFileForSlot(i)
                    }
                }
                var overwriteIdx = 0
                while (loadIdx < loadedPairs.size && overwriteIdx < 4) {
                    val (u, b) = loadedPairs[loadIdx++]
                    updated[overwriteIdx] = b
                    updatedUris[overwriteIdx] = u
                    if (overwriteIdx in 0..1) CollageGenerator.clearLastCameraFileForSlot(overwriteIdx)
                    overwriteIdx++
                }
                _collageSlots.value = updated
                _slotOriginalUris.value = updatedUris
                val newSlot0 = updated.getOrNull(0)
                if (newSlot0 != null && newSlot0 !== oldSlot0) {
                    onPhoto1CapturedOrReplaced(context, newSlot0)
                }
                generateCollage(context)
            }
        }
    }

    fun replaceCollagePhotoAt(context: Context, index: Int, uri: Uri) {
        viewModelScope.launch {
            val slotIdx = index.coerceIn(0, 3)
            val bmp = CollageGenerator.loadBitmapFromUri(context, uri, maxDimension = 1280) ?: return@launch
            if (slotIdx in 0..1) {
                CollageGenerator.clearLastCameraFileForSlot(slotIdx)
            }
            setCollageSlotBitmap(context, slotIdx, bmp, originalUri = uri)
        }
    }

    fun removeCollagePhotoAt(context: Context, index: Int) {
        val slotIdx = index.coerceIn(0, 3)
        if (slotIdx in 0..1) {
            CollageGenerator.clearLastCameraFileForSlot(slotIdx)
        }
        setCollageSlotBitmap(context, slotIdx, null, originalUri = null)
    }

    fun clearCollagePhotos() {
        collageJob?.cancel()
        onPhoto1Removed()
        CollageGenerator.clearLastCameraFileForSlot(0)
        CollageGenerator.clearLastCameraFileForSlot(1)
        _collageSlots.value = listOf(null, null, null, null)
        _slotOriginalUris.value = listOf(null, null, null, null)
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

    fun generateCollage(context: Context, debounceMs: Long = 0L) {
        val slots = _collageSlots.value
        if (slots.all { it == null }) {
            collageJob?.cancel()
            _collageState.value = CollageUiState.Error("Pilih atau ambil minimal 1 hingga 4 foto terlebih dahulu.")
            return
        }
        collageJob?.cancel()
        collageJob = viewModelScope.launch {
            if (debounceMs > 0L) {
                delay(debounceMs)
            }
            if (_collageState.value !is CollageUiState.Ready) {
                _collageState.value = CollageUiState.Generating
            }
            try {
                val up = ReportFormatter.cleanSpeedNumber(_uploadMbpsInput.value)
                val down = ReportFormatter.cleanSpeedNumber(_downloadMbpsInput.value)
                val speedText = "Up ${up}Mbps / Down ${down}Mbps"
                val latestLocation = _locationInput.value.trim()
                val latestDate = _displayDateEnInput.value
                val result = CollageGenerator.create2x2CollageUnder1Mb(
                    context = context,
                    slots = _collageSlots.value,
                    locationTitle = latestLocation,
                    dateLabel = latestDate,
                    speedSummary = speedText,
                    includeHeaderWatermark = _includeCollageHeader.value,
                    fitEntirePhoto = _fitEntirePhoto.value
                )
                _collageState.value = CollageUiState.Ready(result)
            } catch (e: CancellationException) {
                throw e
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
