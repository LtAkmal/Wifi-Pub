package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.util.ReportFormatter
import com.example.viewmodel.PmMainViewModel
import com.example.viewmodel.SpeedtestScanState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DailyPmScreen(
    viewModel: PmMainViewModel,
    onShowSnackbar: (String) -> Unit,
    onNavigateToTodayReport: () -> Unit,
    onNavigateToCollage: () -> Unit
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

    var showLocationPresets by remember { mutableStateOf(false) }

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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("daily_pm_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Hero & Daily 10-PM Progress Banner
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            )
                        )
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Wifi,
                                    contentDescription = "WiFi PM Icon",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "PM WIFI PUBLIK CGK",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Target Harian: ${pmTodayList.size} / 10 Lokasi Selesai",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onNavigateToTodayReport() }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "${pmTodayList.size}/10 PM",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        LinearProgressIndicator(
                            progress = { (pmTodayList.size / 10f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f)
                        )
                    }
                }
            }
        }

        // 2. Step 1: Speedtest Screenshot AI Scanner Card
        item {
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "1. Auto-Deteksi Speedtest (AI)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Pilih screenshot atau Share langsung dari aplikasi Speedtest",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                pickSpeedtestLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pick_speedtest_image_button")
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pilih Foto Speedtest")
                        }

                        if (speedtestBitmap != null) {
                            FilledTonalButton(
                                onClick = { viewModel.scanSpeedtestBitmap() },
                                modifier = Modifier.testTag("rescan_speedtest_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Scan Ulang")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Scan AI")
                            }
                        }
                    }

                    // Thumbnail + AI Scan Status
                    if (speedtestBitmap != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Image(
                                bitmap = speedtestBitmap!!.asImageBitmap(),
                                contentDescription = "Screenshot Speedtest",
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                when (val st = scanState) {
                                    is SpeedtestScanState.Scanning -> {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "AI sedang membaca angka Download & Upload...",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                    is SpeedtestScanState.Success -> {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Terdeteksi Otomatis!",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Text(
                                            text = "Up ${st.uploadMbps}Mbps / Down ${st.downloadMbps}Mbps",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    is SpeedtestScanState.Error -> {
                                        Text(
                                            text = st.message,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    is SpeedtestScanState.Idle -> {
                                        Text(
                                            text = "Gambar siap. Tekan Scan AI untuk ekstrak otomatis.",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Manual / Auto-populated Speedtest Fields (Up first, then Down per user's format)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = uploadMbps,
                            onValueChange = { viewModel.updateUploadMbps(it) },
                            label = { Text("Upload (Mbps)") },
                            suffix = { Text("Mbps") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("upload_mbps_input")
                        )
                        OutlinedTextField(
                            value = downloadMbps,
                            onValueChange = { viewModel.updateDownloadMbps(it) },
                            label = { Text("Download (Mbps)") },
                            suffix = { Text("Mbps") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("download_mbps_input")
                        )
                    }
                }
            }
        }

        // 3. Step 2: Location Title & Automatic Date Card
        item {
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "2. Judul Lokasi & Tanggal Otomatis",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        AssistChip(
                            onClick = { showLocationPresets = !showLocationPresets },
                            label = { Text(if (showLocationPresets) "Tutup Daftar" else "Pilih Lokasi T3") },
                            trailingIcon = {
                                Icon(
                                    imageVector = if (showLocationPresets) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier.testTag("toggle_location_presets_button")
                        )
                    }

                    AnimatedVisibility(visible = showLocationPresets) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Klik salah satu lokasi rutin Terminal 3 CGK:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                savedLocations.forEach { preset ->
                                    val isUsedToday = pmTodayList.any {
                                        it.location.equals(preset, ignoreCase = true)
                                    }
                                    FilterChip(
                                        selected = location == preset,
                                        onClick = {
                                            viewModel.updateLocation(preset)
                                            showLocationPresets = false
                                        },
                                        label = {
                                            Text(
                                                text = if (isUsedToday) "✓ $preset" else preset,
                                                maxLines = 1
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = location,
                        onValueChange = { viewModel.updateLocation(it) },
                        label = { Text("Lokasi (Otomatis masuk ke Laporan & Kolase)") },
                        placeholder = { Text("Contoh: Area R. Tunggu samping mamma roti loby T3 Inter, Terminal 3") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("location_input_field")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = displayDateEn,
                            onValueChange = { viewModel.updateDisplayDateEn(it) },
                            label = { Text("Tanggal Otomatis") },
                            leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("date_input_field")
                        )
                        OutlinedTextField(
                            value = equipmentName,
                            onValueChange = { viewModel.updateEquipmentName(it) },
                            label = { Text("Nama Peralatan") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("equipment_input_field")
                        )
                    }

                    // Status Checkboxes
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = statusNetwork,
                                onCheckedChange = { viewModel.toggleStatusNetwork(it) }
                            )
                            Text("Jaringan Normal", style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = statusDevice,
                                onCheckedChange = { viewModel.toggleStatusDevice(it) }
                            )
                            Text("Perangkat Normal", style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = statusSpeed,
                                onCheckedChange = { viewModel.toggleStatusSpeed(it) }
                            )
                            Text("Speed Normal", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    // Quick shortcut to 4-Photo Collage (< 1 MB) for this location
                    OutlinedButton(
                        onClick = onNavigateToCollage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("shortcut_to_collage_button")
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Buat Kolase 4 Foto < 1 MB untuk Lokasi Ini")
                    }
                }
            }
        }

        // 4. Step 3: Live Formatted Daily Routine Report & Actions
        item {
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
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
                        Text(
                            text = "3. Hasil Format Laporan PM Harian",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        AssistChip(
                            onClick = {
                                copyToClipboard(context, "Daily PM Report", formattedDailyReport)
                                onShowSnackbar("Format laporan PM berhasil disalin!")
                            },
                            label = { Text("Salin Cepat") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(14.dp)
                    ) {
                        Text(
                            text = formattedDailyReport,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("daily_pm_preview_text")
                        )
                    }

                    // Primary Save to Today Report + Copy Button
                    Button(
                        onClick = {
                            copyToClipboard(context, "Daily PM Report", formattedDailyReport)
                            viewModel.saveCurrentPmToTodayReport { count ->
                                onShowSnackbar("Tersimpan ke Today Report (Lokasi ke-$count/10) & Teks Disalin!")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("save_and_copy_pm_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Simpan ke Today Report (${pmTodayList.size + 1}/10) & Salin",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                copyToClipboard(context, "Daily PM Report", formattedDailyReport)
                                onShowSnackbar("Teks laporan PM disalin ke Clipboard!")
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("copy_daily_pm_button")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Salin Teks Saja")
                        }

                        OutlinedButton(
                            onClick = {
                                shareTextToApps(context, formattedDailyReport)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("share_daily_pm_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Kirim WA")
                        }
                    }
                }
            }
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
