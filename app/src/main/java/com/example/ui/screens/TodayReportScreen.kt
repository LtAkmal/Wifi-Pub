package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.TodayReportConfig
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.util.ReportFormatter
import com.example.viewmodel.PmMainViewModel

@Composable
fun TodayReportScreen(
    viewModel: PmMainViewModel,
    onShowSnackbar: (String) -> Unit,
    onNavigateToAddPm: () -> Unit
) {
    val context = LocalContext.current

    val selectedDateKey by viewModel.selectedDateKey.collectAsStateWithLifecycle()
    val pmList by viewModel.pmReportsForSelectedDate.collectAsStateWithLifecycle()
    val cmList by viewModel.cmReportsForSelectedDate.collectAsStateWithLifecycle()
    val reportConfig by viewModel.reportConfig.collectAsStateWithLifecycle()

    var showAddCmDialog by remember { mutableStateOf(false) }
    var showConfigDialog by remember { mutableStateOf(false) }

    val hariTanggalId = remember(selectedDateKey) {
        ReportFormatter.formatHariTanggalId(selectedDateKey)
    }

    val compiledTodayReport = remember(hariTanggalId, reportConfig, pmList, cmList) {
        ReportFormatter.formatTodayReport(
            hariTanggalId = hariTanggalId,
            config = reportConfig,
            pmList = pmList,
            cmList = cmList
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("today_report_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Clean Shift & Personnel Summary Strip
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = hariTanggalId,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Shift: ${reportConfig.shift} • Aplusan: ${reportConfig.aplusanShiftLabel}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { showConfigDialog = true }
                            .testTag("open_config_dialog_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Atur Petugas",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 2. Preventive Maintenance (10 Locations) List Card
        item {
            CleanSectionCard(
                stepNumber = "1",
                title = "Preventive Maintenance (${pmList.size}/10)",
                subtitle = "Daftar hasil speedtest yang masuk ke Today Report",
                trailingHeader = {
                    FilledTonalButton(
                        onClick = onNavigateToAddPm,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("add_more_pm_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Tambah", style = MaterialTheme.typography.labelMedium)
                    }
                }
            ) {
                if (pmList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "Belum ada data PM disimpan hari ini. Tambahkan lokasi dari tab Input PM.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        pmList.forEachIndexed { idx, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${idx + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.location,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Up ${item.uploadMbps}Mbps / Down ${item.downloadMbps}Mbps",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.deletePmReport(item.id) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "Hapus PM",
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

        // 3. Corrective Maintenance (CM) Card
        item {
            CleanSectionCard(
                stepNumber = "2",
                title = "Corrective Maintenance (${cmList.size})",
                subtitle = "Opsional — tambahkan jika ada pembersihan / perbaikan AP",
                trailingHeader = {
                    FilledTonalButton(
                        onClick = { showAddCmDialog = true },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("add_cm_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Tambah CM", style = MaterialTheme.typography.labelMedium)
                    }
                }
            ) {
                if (cmList.isEmpty()) {
                    Text(
                        text = "Kosong (otomatis tertulis *Corrective Maintenance: - pada laporan).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        cmList.forEach { cm ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = cm.activityTitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Lokasi: ${cm.location}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.deleteCorrectiveMaintenance(cm.id) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "Hapus CM",
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

        // 4. Compiled ICT Operation Daily Report Preview & Actions
        item {
            CleanSectionCard(
                stepNumber = "3",
                title = "Hasil Teks Today Report",
                subtitle = "Format *ICT OPERATION DAILY REPORT (CGK)."
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(14.dp)
                ) {
                    Text(
                        text = compiledTodayReport,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 12.sp,
                        lineHeight = 17.5.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("today_report_preview_text")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            copyToClipboard(context, "ICT Operation Daily Report", compiledTodayReport)
                            onShowSnackbar("Today Report berhasil disalin ke Clipboard!")
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("copy_today_report_button")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Salin Today Report")
                    }

                    OutlinedButton(
                        onClick = { shareTextToApps(context, compiledTodayReport) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("share_today_report_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kirim WA")
                    }
                }
            }
        }
    }

    if (showAddCmDialog) {
        AddCorrectiveMaintenanceDialog(
            onDismiss = { showAddCmDialog = false },
            onConfirm = { title, loc, equip, kendala, issue, actions, status ->
                viewModel.addCorrectiveMaintenance(
                    activityTitle = title,
                    location = loc,
                    equipmentName = equip,
                    kendala = kendala,
                    issue = issue,
                    actionLines = actions,
                    statusLines = status
                )
                showAddCmDialog = false
                onShowSnackbar("Corrective Maintenance ditambahkan!")
            }
        )
    }

    if (showConfigDialog) {
        EditReportConfigDialog(
            initialConfig = reportConfig,
            onDismiss = { showConfigDialog = false },
            onSave = { updated ->
                viewModel.updateReportConfig(updated)
                showConfigDialog = false
                onShowSnackbar("Pengaturan Petugas & Shift disimpan!")
            }
        )
    }
}

@Composable
private fun AddCorrectiveMaintenanceDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        location: String,
        equipment: String,
        kendala: String,
        issue: String,
        actions: String,
        status: String
    ) -> Unit
) {
    var title by remember { mutableStateOf("Pembersihan & Pengujian Speed Access Point Wifi Publik") }
    var location by remember { mutableStateOf("Plaza Kedatangan Gate 1 Sebelah kanan, Terminal 3") }
    var equipment by remember { mutableStateOf("Access Point Wifi Publik") }
    var kendala by remember { mutableStateOf("Temuan AP Tampak Tidak Bersih") }
    var issue by remember { mutableStateOf("No Issue") }
    var actions by remember {
        mutableStateOf("-Team Melakukan Pembersihan\n-Team Melakukan Pengujian Speedtest")
    }
    var status by remember {
        mutableStateOf("- AP Sudah Bersih✅\n- AP Terpantau Normal ✅\n- Speedtest Terpantau Normal✅")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah Corrective Maintenance", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Judul Kegiatan (🛠️)") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Lokasi") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = equipment,
                    onValueChange = { equipment = it },
                    label = { Text("Nama Peralatan") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = kendala,
                    onValueChange = { kendala = it },
                    label = { Text("Kendala") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = issue,
                    onValueChange = { issue = it },
                    label = { Text("Issue") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = actions,
                    onValueChange = { actions = it },
                    label = { Text("Action (per baris)") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = status,
                    onValueChange = { status = it },
                    label = { Text("Status (per baris)") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(title, location, equipment, kendala, issue, actions, status)
                }
            ) {
                Text("Simpan CM")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

@Composable
private fun EditReportConfigDialog(
    initialConfig: TodayReportConfig,
    onDismiss: () -> Unit,
    onSave: (TodayReportConfig) -> Unit
) {
    var shift by remember { mutableStateOf(initialConfig.shift) }
    var helpdesk by remember { mutableStateOf(initialConfig.helpdesk) }
    var onDutyDailyPm by remember { mutableStateOf(initialConfig.onDutyDailyPm) }
    var onDutyTodayCgk by remember { mutableStateOf(initialConfig.onDutyTodayCgk) }
    var aplusanShift by remember { mutableStateOf(initialConfig.aplusanShiftLabel) }
    var petugasEos by remember { mutableStateOf(initialConfig.petugasEos) }
    var officers by remember { mutableStateOf(initialConfig.officers) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Atur Shift & Daftar Petugas", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = shift,
                    onValueChange = { shift = it },
                    label = { Text("Shift (Contoh: Pagi(SH1))") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = helpdesk,
                    onValueChange = { helpdesk = it },
                    label = { Text("Nomor Helpdesk") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                HorizontalDivider()
                OutlinedTextField(
                    value = onDutyDailyPm,
                    onValueChange = { onDutyDailyPm = it },
                    label = { Text("On Duty (Laporan PM Satuan)") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = onDutyTodayCgk,
                    onValueChange = { onDutyTodayCgk = it },
                    label = { Text("On Duty CGK (Today Report)") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                HorizontalDivider()
                OutlinedTextField(
                    value = aplusanShift,
                    onValueChange = { aplusanShift = it },
                    label = { Text("Label Tim Aplusan") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = petugasEos,
                    onValueChange = { petugasEos = it },
                    label = { Text("Petugas EOS") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = officers,
                    onValueChange = { officers = it },
                    label = { Text("OFFICER") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        TodayReportConfig(
                            shift = shift,
                            helpdesk = helpdesk,
                            onDutyDailyPm = onDutyDailyPm,
                            onDutyTodayCgk = onDutyTodayCgk,
                            aplusanShiftLabel = aplusanShift,
                            petugasEos = petugasEos,
                            officers = officers
                        )
                    )
                }
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
