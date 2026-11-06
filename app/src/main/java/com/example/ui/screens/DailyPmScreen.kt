package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.SuccessGreen
import com.example.util.CollageGenerator
import com.example.util.ReportFormatter
import com.example.viewmodel.PmMainViewModel
import com.example.viewmodel.SpeedtestScanState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyPmScreen(
    viewModel: PmMainViewModel,
    onShowSnackbar: (String) -> Unit,
    onNavigateToTodayReport: () -> Unit,
    onNavigateToCollage: () -> Unit,
    onOpenApiConfig: () -> Unit = {}
) {
    val context = LocalContext.current

    val location by viewModel.locationInput.collectAsStateWithLifecycle()
    val equipmentName by viewModel.equipmentNameInput.collectAsStateWithLifecycle()
    val uploadMbps by viewModel.uploadMbpsInput.collectAsStateWithLifecycle()
    val downloadMbps by viewModel.downloadMbpsInput.collectAsStateWithLifecycle()
    val displayDateEn by viewModel.displayDateEnInput.collectAsStateWithLifecycle()
    val statusNetwork by viewModel.statusNetwork.collectAsStateWithLifecycle()
    val statusDevice by viewModel.statusDevice.collectAsStateWithLifecycle()
    val statusSpeed by viewModel.statusSpeed.collectAsStateWithLifecycle()

    val speedtestBitmap by viewModel.speedtestBitmap.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val savedLocations by viewModel.savedLocations.collectAsStateWithLifecycle()
    val pmTodayList by viewModel.pmReportsForSelectedDate.collectAsStateWithLifecycle()
    val reportConfig by viewModel.reportConfig.collectAsStateWithLifecycle()
    val collageSlots by viewModel.collageSlots.collectAsStateWithLifecycle()

    var showLocationSheet by remember { mutableStateOf(false) }
    var showPreviewText by remember { mutableStateOf(true) }
    var showAdvancedFields by remember { mutableStateOf(false) }
    var showBuiltInSpeedtest by remember { mutableStateOf(false) }
    var showBuiltInWifiAnalyzer by remember { mutableStateOf(false) }

    // Direct In-App Camera Capture state for Foto 1 (slot 0) and Foto 2 (slot 1)
    var pendingCameraSlot by remember { mutableIntStateOf(0) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            viewModel.onCameraPhotoCaptured(context, pendingCameraSlot, uri) { slot ->
                onShowSnackbar("Foto Kamera berhasil masuk otomatis ke FOTO ${slot + 1} (Kolase 4 Foto)!")
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = CollageGenerator.createCameraCaptureUri(context, pendingCameraSlot)
            pendingCameraUri = uri
            if (uri != null) {
                takePictureLauncher.launch(uri)
            } else {
                onShowSnackbar("Gagal menyiapkan file kamera.")
            }
        } else {
            onShowSnackbar("Izin kamera diperlukan untuk mengambil foto dokumentasi langsung.")
        }
    }

    fun launchCameraForSlot(slotIndex: Int) {
        pendingCameraSlot = slotIndex
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            val uri = CollageGenerator.createCameraCaptureUri(context, slotIndex)
            pendingCameraUri = uri
            if (uri != null) {
                takePictureLauncher.launch(uri)
            } else {
                onShowSnackbar("Gagal menyiapkan kamera.")
            }
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val pickSpeedtestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.onSpeedtestImageSelected(context, uri, autoScan = true)
        }
    }

    val formattedDailyReport = remember(
        location,
        equipmentName,
        uploadMbps,
        downloadMbps,
        displayDateEn,
        statusNetwork,
        statusDevice,
        statusSpeed,
        reportConfig.onDutyDailyPm
    ) {
        ReportFormatter.formatDailyPmReport(
            location = location,
            equipmentName = equipmentName,
            uploadMbps = uploadMbps,
            downloadMbps = downloadMbps,
            displayDateEn = displayDateEn,
            statusNetwork = statusNetwork,
            statusDevice = statusDevice,
            statusSpeed = statusSpeed,
            onDutyText = reportConfig.onDutyDailyPm
        )
    }

    val filledSlotsCount = collageSlots.count { it != null }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("daily_pm_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Compact Progress & Quick Shift Selector Card
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(14.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToTodayReport() },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Progres PM Harian",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${pmTodayList.size} Lokasi Selesai",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (pmTodayList.size / 10f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (pmTodayList.size >= 10) SuccessGreen else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    // 1-Tap Shift Toggle (Pagi SH1 vs Malam SH2)
                    val isPagi = reportConfig.shift.contains("Pagi", ignoreCase = true) ||
                        reportConfig.shift.contains("SH1", ignoreCase = true)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Shift Aktif:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                true to "Pagi (SH1)",
                                false to "Malam (SH2)"
                            ).forEach { (pagiOption, label) ->
                                val selected = isPagi == pagiOption
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary
                                            else Color.Transparent
                                        )
                                        .clickable { viewModel.selectShiftOption(pagiOption) }
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // SECTION 1: All-In-One Telemetry & Auto 4-Foto Capture Hub
        item {
            CleanSectionCard(
                stepNumber = "1",
                title = "Kamera, Speedtest & WiFi Analyzer",
                subtitle = "Otomatis masuk ke sesi 4 Foto (Foto 1-2 Kamera, 3 Speedtest, 4 WiFi Analyzer)",
                trailingHeader = {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (filledSlotsCount == 4) SuccessGreen.copy(alpha = 0.16f)
                        else MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.clickable { onNavigateToCollage() }
                    ) {
                        Text(
                            text = "$filledSlotsCount/4 Foto",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (filledSlotsCount == 4) SuccessGreen else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            ) {
                // 2x2 Action Grid for Foto 1 (Kamera 1), Foto 2 (Kamera 2), Foto 3 (Speedtest), Foto 4 (WiFi Analyzer)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AutoSlotTriggerButton(
                            slotLabel = "FOTO 1 • KAMERA",
                            actionTitle = if (collageSlots[0] != null) "Foto 1 Siap (Ulang)" else "Ambil Foto 1",
                            icon = Icons.Default.CameraAlt,
                            isCaptured = collageSlots[0] != null,
                            accentColor = Color(0xFF38BDF8),
                            onClick = { launchCameraForSlot(0) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("capture_camera_slot_1_button")
                        )
                        AutoSlotTriggerButton(
                            slotLabel = "FOTO 2 • KAMERA",
                            actionTitle = if (collageSlots[1] != null) "Foto 2 Siap (Ulang)" else "Ambil Foto 2",
                            icon = Icons.Default.CameraAlt,
                            isCaptured = collageSlots[1] != null,
                            accentColor = Color(0xFF38BDF8),
                            onClick = { launchCameraForSlot(1) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("capture_camera_slot_2_button")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AutoSlotTriggerButton(
                            slotLabel = "FOTO 3 • SPEEDTEST",
                            actionTitle = if (collageSlots[2] != null) "Speedtest Siap" else "Mulai Speedtest",
                            icon = Icons.Default.Speed,
                            isCaptured = collageSlots[2] != null,
                            accentColor = Color(0xFF00E599),
                            onClick = { showBuiltInSpeedtest = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_builtin_speedtest_button")
                        )
                        AutoSlotTriggerButton(
                            slotLabel = "FOTO 4 • ANALYZER",
                            actionTitle = if (collageSlots[3] != null) "Analyzer Siap" else "WiFi Analyzer",
                            icon = Icons.Default.WifiTethering,
                            isCaptured = collageSlots[3] != null,
                            accentColor = Color(0xFFF59E0B),
                            onClick = { showBuiltInWifiAnalyzer = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_builtin_wifi_analyzer_button")
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                // Clean Dual Metric Gauges (Upload & Download - Auto-populated by Speedtest!)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SpeedMetricInputCard(
                        label = "UPLOAD",
                        value = uploadMbps,
                        onValueChange = { viewModel.updateUploadMbps(it) },
                        isUpload = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("upload_mbps_input")
                    )

                    SpeedMetricInputCard(
                        label = "DOWNLOAD",
                        value = downloadMbps,
                        onValueChange = { viewModel.updateDownloadMbps(it) },
                        isUpload = false,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("download_mbps_input")
                    )
                }

                // Optional Manual Screenshot Picker if user still wants to scan an existing image
                if (speedtestBitmap == null) {
                    OutlinedButton(
                        onClick = {
                            pickSpeedtestLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 40.dp)
                            .testTag("pick_speedtest_image_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Atau Upload Screenshot Speedtest dari Galeri",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(12.dp)
                            )
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            bitmap = speedtestBitmap!!.asImageBitmap(),
                            contentDescription = "Screenshot Speedtest",
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { showBuiltInSpeedtest = true },
                            contentScale = ContentScale.Fit
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            when (val st = scanState) {
                                is SpeedtestScanState.Scanning -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Membaca angka speedtest...",
                                            style = MaterialTheme.typography.labelLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                is SpeedtestScanState.Success -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = SuccessGreen,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = "Otomatis Terisi (${st.providerLabel})",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = SuccessGreen,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = "Screenshot sudah masuk otomatis ke FOTO 3",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                is SpeedtestScanState.Error -> {
                                    Text(
                                        text = st.message,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.error,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (st.suggestApiConfig) {
                                        Text(
                                            text = "👉 Atur API 9Router",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .padding(top = 4.dp)
                                                .clickable { onOpenApiConfig() }
                                        )
                                    }
                                }
                                is SpeedtestScanState.Idle -> {
                                    Text(
                                        text = "Screenshot tersimpan di FOTO 3",
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        FilledTonalButton(
                            onClick = { showBuiltInSpeedtest = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .heightIn(min = 36.dp)
                                .testTag("rescan_speedtest_button")
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Ulang Speedtest",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Uji Lagi",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        // SECTION 2: Location & Automatic Date (No hardcoded existing locations; auto-saves user-typed locations!)
        item {
            CleanSectionCard(
                stepNumber = "2",
                title = "Lokasi & Detail PM",
                subtitle = "Lokasi yang Anda ketik otomatis tersimpan agar tidak perlu ketik ulang"
            ) {
                // Saved User Locations Selector Button (only shows locations saved by the user)
                if (savedLocations.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { showLocationSheet = true }
                            .testTag("toggle_location_presets_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Pilih Lokasi Tersimpan (${savedLocations.size} Lokasi)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = location.ifBlank { "Ketuk untuk memilih riwayat lokasi Anda..." },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Pilih Lokasi",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Editable Location Field with Save-Location quick action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = location,
                        onValueChange = { viewModel.updateLocation(it) },
                        label = { Text("Nama / Detail Lokasi") },
                        placeholder = { Text("Ketik nama lokasi PM (otomatis disimpan)...") },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("location_input_field")
                    )

                    if (location.isNotBlank() && savedLocations.none { it.equals(location.trim(), ignoreCase = true) }) {
                        FilledTonalButton(
                            onClick = {
                                viewModel.saveLocationIfNotBlank(location)
                                onShowSnackbar("Lokasi '${location.trim()}' disimpan ke daftar lokasi Anda!")
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                            modifier = Modifier.heightIn(min = 52.dp)
                        ) {
                            Icon(
                                Icons.Default.BookmarkAdd,
                                contentDescription = "Simpan Lokasi",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Clean Toggle for Date, Equipment & Status
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showAdvancedFields = !showAdvancedFields }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.DateRange,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Tanggal: $displayDateEn • Normal ✅",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (showAdvancedFields) "Tutup" else "Ubah",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = if (showAdvancedFields) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = showAdvancedFields) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = displayDateEn,
                            onValueChange = { viewModel.updateDisplayDateEn(it) },
                            label = { Text("Tanggal Laporan") },
                            leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("date_input_field")
                        )

                        OutlinedTextField(
                            value = equipmentName,
                            onValueChange = { viewModel.updateEquipmentName(it) },
                            label = { Text("Nama Peralatan") },
                            leadingIcon = { Icon(Icons.Default.Router, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("equipment_input_field")
                        )

                        // Clean Status Toggle Pills
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatusTogglePill(
                                label = "Jaringan",
                                checked = statusNetwork,
                                onToggle = { viewModel.toggleStatusNetwork(!statusNetwork) },
                                modifier = Modifier.weight(1f)
                            )
                            StatusTogglePill(
                                label = "Perangkat",
                                checked = statusDevice,
                                onToggle = { viewModel.toggleStatusDevice(!statusDevice) },
                                modifier = Modifier.weight(1f)
                            )
                            StatusTogglePill(
                                label = "Speed",
                                checked = statusSpeed,
                                onToggle = { viewModel.toggleStatusSpeed(!statusSpeed) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Shortcut to 4-Photo Collage (< 1 MB)
                OutlinedButton(
                    onClick = onNavigateToCollage,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("shortcut_to_collage_button")
                ) {
                    Icon(
                        Icons.Default.GridView,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Lihat Hasil 4 Foto Dokumentasi ($filledSlotsCount/4 Terisi)",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // SECTION 3: Formatted Report Preview & Primary Actions
        item {
            CleanSectionCard(
                stepNumber = "3",
                title = "Format Laporan PM",
                subtitle = "Siap tempel ke WhatsApp & masuk ke Rekap",
                trailingHeader = {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showPreviewText = !showPreviewText }
                    ) {
                        Text(
                            text = if (showPreviewText) "Tutup" else "Lihat",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            ) {
                AnimatedVisibility(visible = showPreviewText) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(14.dp)
                    ) {
                        Text(
                            text = formattedDailyReport,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("daily_pm_preview_text")
                        )
                    }
                }

                Button(
                    onClick = {
                        copyToClipboard(context, "Daily PM Report", formattedDailyReport)
                        viewModel.saveCurrentPmToTodayReport { count ->
                            onShowSnackbar("Lokasi ke-$count disimpan (lokasi otomatis diingat) & teks laporan disalin!")
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .testTag("save_and_copy_pm_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Simpan ke Rekap (${pmTodayList.size + 1}) & Salin",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            viewModel.saveLocationIfNotBlank(location)
                            copyToClipboard(context, "Daily PM Report", formattedDailyReport)
                            onShowSnackbar("Teks laporan disalin ke Clipboard!")
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 11.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 46.dp)
                            .testTag("copy_daily_pm_button")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Salin Teks", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.saveLocationIfNotBlank(location)
                            shareTextToApps(context, formattedDailyReport)
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 11.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 46.dp)
                            .testTag("share_daily_pm_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kirim WA", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    // Built-in Fullscreen Speedtest Dialog
    if (showBuiltInSpeedtest) {
        BuiltInSpeedtestDialog(
            viewModel = viewModel,
            onDismiss = { showBuiltInSpeedtest = false },
            onCompletedToast = { msg -> onShowSnackbar(msg) }
        )
    }

    // Built-in Fullscreen WiFi Analyzer Dialog
    if (showBuiltInWifiAnalyzer) {
        BuiltInWifiAnalyzerDialog(
            viewModel = viewModel,
            onDismiss = { showBuiltInWifiAnalyzer = false },
            onCapturedToast = { msg -> onShowSnackbar(msg) }
        )
    }

    // Clean BottomSheet for Selecting User-Saved Locations
    if (showLocationSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showLocationSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    text = "Daftar Lokasi PM Tersimpan",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Lokasi yang pernah Anda ketik otomatis tersimpan di sini",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 420.dp)
                ) {
                    itemsIndexed(savedLocations) { idx, preset ->
                        val isUsedToday = pmTodayList.any {
                            it.location.equals(preset, ignoreCase = true)
                        }
                        val isSelected = location.equals(preset, ignoreCase = true)

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = when {
                                isSelected -> MaterialTheme.colorScheme.primaryContainer
                                isUsedToday -> SuccessGreen.copy(alpha = 0.08f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 1.dp,
                                    color = when {
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        isUsedToday -> SuccessGreen.copy(alpha = 0.4f)
                                        else -> MaterialTheme.colorScheme.outline
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    viewModel.updateLocation(preset)
                                    showLocationSheet = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isUsedToday) SuccessGreen
                                            else MaterialTheme.colorScheme.surface
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isUsedToday) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    } else {
                                        Text(
                                            text = "${idx + 1}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Text(
                                    text = preset,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )

                                IconButton(
                                    onClick = { viewModel.removeSavedLocation(preset) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "Hapus Lokasi",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoSlotTriggerButton(
    slotLabel: String,
    actionTitle: String,
    icon: ImageVector,
    isCaptured: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isCaptured) SuccessGreen.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .heightIn(min = 66.dp)
            .border(
                width = 1.dp,
                color = if (isCaptured) SuccessGreen.copy(alpha = 0.55f)
                else accentColor.copy(alpha = 0.45f),
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (isCaptured) SuccessGreen else accentColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isCaptured) Icons.Default.Check else icon,
                    contentDescription = null,
                    tint = if (isCaptured) Color.Black else accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = slotLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = if (isCaptured) SuccessGreen else accentColor,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = actionTitle,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun CleanSectionCard(
    stepNumber: String,
    title: String,
    subtitle: String,
    trailingHeader: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = if (trailingHeader != null) 8.dp else 0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stepNumber,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (trailingHeader != null) {
                    trailingHeader()
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

            content()
        }
    }
}

@Composable
private fun SpeedMetricInputCard(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isUpload: Boolean,
    modifier: Modifier = Modifier
) {
    val accentColor = if (isUpload) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline,
            shape = RoundedCornerShape(12.dp)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isUpload) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    cursorBrush = SolidColor(accentColor),
                    textStyle = TextStyle(
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Mbps",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun StatusTogglePill(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (checked) SuccessGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .heightIn(min = 40.dp)
            .border(
                width = 1.dp,
                color = if (checked) SuccessGreen.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onToggle() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (checked) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (checked) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
}

fun shareTextToApps(context: Context, text: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(sendIntent, "Kirim Laporan ke..."))
}
