package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
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
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ICT OPERATION DAILY REPORT (CGK)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "$hariTanggalId • ${pmList.size}/10 PM • ${cmList.size} CM",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                        )
                    }
                    AssistChip(
                        onClick = { showConfigDialog = true },
                        label = { Text("Petugas & Shift") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("open_config_dialog_button")
                    )
                }
            }
        }

        // 2. Preventive Maintenance (PM Speedtest) List Card
        item {
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Wifi,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Daftar PM Speedtest (${pmList.size}/10)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        FilledTonalButton(
                            onClick = onNavigateToAddPm,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("add_more_pm_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tambah PM")
                        }
                    }

                    if (pmList.isEmpty()) {
                        Text(
                            text = "Belum ada data PM Speedtest yang disimpan hari ini. Tambahkan dari tab PM Speedtest.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        pmList.forEachIndexed { idx, item ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "#${idx + 1} • ${item.location}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Up ${item.uploadMbps}Mbps / Down ${item.downloadMbps}Mbps",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.deletePmReport(item.id) }
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Hapus PM",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Corrective Maintenance (CM) List Card
        item {
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Build,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Corrective Maintenance (${cmList.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        FilledTonalButton(
                            onClick = { showAddCmDialog = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("add_cm_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tambah CM")
                        }
                    }

                    if (cmList.isEmpty()) {
                        Text(
                            text = "Tidak ada Corrective Maintenance hari ini (opsional, tekan '+ Tambah CM' jika ada kegiatan pembersihan/perbaikan AP).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        cmList.forEach { cm ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = cm.activityTitle,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Lokasi: ${cm.location}",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "Kendala: ${cm.kendala}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.deleteCorrectiveMaintenance(cm.id) }
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Hapus CM",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Compiled Today Report Preview & 1-Tap Copy / WhatsApp Share
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
                                Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Preview Today Report",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(14.dp)
                    ) {
                        Text(
                            text = compiledTodayReport,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp,
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
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("copy_today_report_button")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Salin Today Report")
                        }

                        OutlinedButton(
                            onClick = {
                                shareTextToApps(context, compiledTodayReport)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("share_today_report_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Kirim WA")
                        }
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
                onShowSnackbar("Corrective Maintenance ditambahkan ke Today Report!")
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
        title = { Text("Tambah Corrective Maintenance") },
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
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Lokasi") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = equipment,
                    onValueChange = { equipment = it },
                    label = { Text("Nama Peralatan") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = kendala,
                    onValueChange = { kendala = it },
                    label = { Text("Kendala") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = issue,
                    onValueChange = { issue = it },
                    label = { Text("Issue") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = actions,
                    onValueChange = { actions = it },
                    label = { Text("Action (per baris)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = status,
                    onValueChange = { status = it },
                    label = { Text("Status (per baris)") },
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
        title = { Text("Atur Shift & Daftar Petugas") },
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
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = helpdesk,
                    onValueChange = { helpdesk = it },
                    label = { Text("Nomor Helpdesk") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                HorizontalDivider()
                OutlinedTextField(
                    value = onDutyDailyPm,
                    onValueChange = { onDutyDailyPm = it },
                    label = { Text("On Duty (Format Laporan PM Satuan)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = onDutyTodayCgk,
                    onValueChange = { onDutyTodayCgk = it },
                    label = { Text("On Duty CGK (Format Today Report)") },
                    modifier = Modifier.fillMaxWidth()
                )
                HorizontalDivider()
                OutlinedTextField(
                    value = aplusanShift,
                    onValueChange = { aplusanShift = it },
                    label = { Text("Label Tim Aplusan") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = petugasEos,
                    onValueChange = { petugasEos = it },
                    label = { Text("Petugas EOS") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = officers,
                    onValueChange = { officers = it },
                    label = { Text("OFFICER") },
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
