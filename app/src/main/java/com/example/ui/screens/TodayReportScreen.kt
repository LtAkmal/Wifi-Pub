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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import com.example.data.CorrectiveMaintenanceEntity
import com.example.data.PmReportEntity
import com.example.data.TodayReportConfig
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SuccessGreen
import com.example.util.ReportFormatter
import com.example.viewmodel.PmMainViewModel

@Composable
fun TodayReportScreen(
    viewModel: PmMainViewModel,
    onShowSnackbar: (String) -> Unit,
    onNavigateToAddPm: () -> Unit,
    onOpenNasConfig: () -> Unit = {}
) {
    val context = LocalContext.current

    val selectedDateKey by viewModel.selectedDateKey.collectAsStateWithLifecycle()
    val pmList by viewModel.pmReportsForSelectedDate.collectAsStateWithLifecycle()
    val cmList by viewModel.cmReportsForSelectedDate.collectAsStateWithLifecycle()
    val reportConfig by viewModel.reportConfig.collectAsStateWithLifecycle()
    val nasConfig by viewModel.nasUploadConfig.collectAsStateWithLifecycle()

    var showAddCmDialog by remember { mutableStateOf(false) }
    var editingCmItem by remember { mutableStateOf<CorrectiveMaintenanceEntity?>(null) }
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

    val isPagiShift = reportConfig.shift.contains("Pagi", ignoreCase = true) ||
        reportConfig.shift.contains("SH1", ignoreCase = true)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("today_report_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Clean Shift Selector & Personnel Summary Card (1-Tap Shift Toggle: Pagi SH1 vs Malam SH2)
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Text(
                                text = hariTanggalId,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Aplusan: ${reportConfig.aplusanShiftLabel}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
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
                                    text = "Petugas",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    // 1-Tap Shift Toggle Pill (No Manual Typing!)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            true to "☀️ Shift Pagi (SH1)",
                            false to "🌙 Shift Malam (SH2)"
                        ).forEach { (pagiOption, label) ->
                            val selected = isPagiShift == pagiOption
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else Color.Transparent
                                    )
                                    .clickable {
                                        viewModel.selectShiftOption(pagiOption)
                                        onShowSnackbar(
                                            if (pagiOption) "Shift diubah ke Pagi (SH1)"
                                            else "Shift diubah ke Malam (SH2)"
                                        )
                                    }
                                    .padding(vertical = 8.dp, horizontal = 8.dp)
                                    .testTag(if (pagiOption) "shift_pagi_button" else "shift_malam_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Preventive Maintenance List Card
        item {
            CleanSectionCard(
                stepNumber = "1",
                title = "Preventive Maintenance (${pmList.size})",
                subtitle = "Daftar hasil speedtest yang masuk ke Today Report",
                trailingHeader = {
                    FilledTonalButton(
                        onClick = onNavigateToAddPm,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .testTag("add_more_pm_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Tambah",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            softWrap = false
                        )
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
                            Column(
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
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
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
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.deletePmReport(context, item.id) },
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

                                // NAS Upload Status Chip / Retry Action
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 36.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    NasUploadStatusChip(
                                        report = item,
                                        hasNasToken = nasConfig.token.isNotBlank(),
                                        onTriggerUpload = {
                                            if (nasConfig.token.isBlank()) {
                                                onOpenNasConfig()
                                            } else {
                                                viewModel.uploadPmReportToNas(
                                                    context = context,
                                                    reportId = item.id,
                                                    showFeedbackSnackbar = true
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Corrective Maintenance (CM) Card (Supports Adding & Editing Existing CM!)
        item {
            CleanSectionCard(
                stepNumber = "2",
                title = "Corrective Maintenance (${cmList.size})",
                subtitle = "Ketuk tombol Edit (pensil) untuk mengubah data CM tanpa buat ulang",
                trailingHeader = {
                    FilledTonalButton(
                        onClick = {
                            editingCmItem = null
                            showAddCmDialog = true
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .testTag("add_cm_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Tambah CM",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            softWrap = false
                        )
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
                                    .clickable {
                                        editingCmItem = cm
                                        showAddCmDialog = true
                                    }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = cm.activityTitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Lokasi: ${cm.location}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Kendala: ${cm.kendala} • Issue: ${cm.issue}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            editingCmItem = cm
                                            showAddCmDialog = true
                                        },
                                        modifier = Modifier
                                            .size(34.dp)
                                            .testTag("edit_cm_button_${cm.id}")
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Edit CM",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.deleteCorrectiveMaintenance(cm.id) },
                                        modifier = Modifier.size(34.dp)
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
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("copy_today_report_button")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Salin Rekap", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    OutlinedButton(
                        onClick = { shareTextToApps(context, compiledTodayReport) },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("share_today_report_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kirim WA", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    if (showAddCmDialog) {
        AddOrEditCorrectiveMaintenanceDialog(
            editingCm = editingCmItem,
            onDismiss = {
                showAddCmDialog = false
                editingCmItem = null
            },
            onConfirm = { title, loc, equip, kendala, issue, actions, status ->
                val targetEdit = editingCmItem
                if (targetEdit != null) {
                    viewModel.updateCorrectiveMaintenance(
                        existing = targetEdit,
                        activityTitle = title,
                        location = loc,
                        equipmentName = equip,
                        kendala = kendala,
                        issue = issue,
                        actionLines = actions,
                        statusLines = status
                    )
                    onShowSnackbar("Data Corrective Maintenance berhasil diperbarui!")
                } else {
                    viewModel.addCorrectiveMaintenance(
                        activityTitle = title,
                        location = loc,
                        equipmentName = equip,
                        kendala = kendala,
                        issue = issue,
                        actionLines = actions,
                        statusLines = status
                    )
                    onShowSnackbar("Corrective Maintenance ditambahkan!")
                }
                showAddCmDialog = false
                editingCmItem = null
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

/**
 * Dialog for Adding or Editing Corrective Maintenance.
 * CRITICAL UX IMPROVEMENT requested by user:
 * - When adding a new CM (`editingCm == null`), all fields start EMPTY (`""`) and display
 *   example text as `placeholder` (background hint text) so the user never has to manually delete pre-filled text!
 * - If the user leaves a field blank and taps Save, it gracefully falls back to the standard template value.
 * - When editing an existing CM (`editingCm != null`), fields are pre-populated with that CM's saved data so the user can edit directly.
 */
@Composable
private fun AddOrEditCorrectiveMaintenanceDialog(
    editingCm: CorrectiveMaintenanceEntity?,
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
    val defaultTitle = "Pembersihan & Pengujian Speed Access Point Wifi Publik"
    val defaultLocation = "Plaza Kedatangan Gate 1 Sebelah kanan, Terminal 3"
    val defaultEquipment = "Access Point Wifi Publik"
    val defaultKendala = "Temuan AP Tampak Tidak Bersih"
    val defaultIssue = "No Issue"
    val defaultActions = "-Team Melakukan Pembersihan\n-Team Melakukan Pengujian Speedtest"
    val defaultStatus = "- AP Sudah Bersih✅\n- AP Terpantau Normal ✅\n- Speedtest Terpantau Normal✅"

    var title by remember(editingCm) { mutableStateOf(editingCm?.activityTitle ?: "") }
    var location by remember(editingCm) { mutableStateOf(editingCm?.location ?: "") }
    var equipment by remember(editingCm) { mutableStateOf(editingCm?.equipmentName ?: "") }
    var kendala by remember(editingCm) { mutableStateOf(editingCm?.kendala ?: "") }
    var issue by remember(editingCm) { mutableStateOf(editingCm?.issue ?: "") }
    var actions by remember(editingCm) { mutableStateOf(editingCm?.actionLines ?: "") }
    var status by remember(editingCm) { mutableStateOf(editingCm?.statusLines ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (editingCm != null) "Edit Corrective Maintenance" else "Tambah Corrective Maintenance",
                style = MaterialTheme.typography.titleLarge
            )
        },
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
                    placeholder = {
                        Text(
                            text = defaultTitle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Lokasi") },
                    placeholder = {
                        Text(
                            text = defaultLocation,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = equipment,
                    onValueChange = { equipment = it },
                    label = { Text("Nama Peralatan") },
                    placeholder = {
                        Text(
                            text = defaultEquipment,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = kendala,
                    onValueChange = { kendala = it },
                    label = { Text("Kendala") },
                    placeholder = {
                        Text(
                            text = defaultKendala,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = issue,
                    onValueChange = { issue = it },
                    label = { Text("Issue") },
                    placeholder = {
                        Text(
                            text = defaultIssue,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = actions,
                    onValueChange = { actions = it },
                    label = { Text("Action (per baris)") },
                    placeholder = {
                        Text(
                            text = defaultActions,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    },
                    minLines = 2,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = status,
                    onValueChange = { status = it },
                    label = { Text("Status (per baris)") },
                    placeholder = {
                        Text(
                            text = defaultStatus,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    },
                    minLines = 3,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        title.trim().ifBlank { defaultTitle },
                        location.trim().ifBlank { defaultLocation },
                        equipment.trim().ifBlank { defaultEquipment },
                        kendala.trim().ifBlank { defaultKendala },
                        issue.trim().ifBlank { defaultIssue },
                        actions.trim().ifBlank { defaultActions },
                        status.trim().ifBlank { defaultStatus }
                    )
                }
            ) {
                Text(if (editingCm != null) "Simpan Perubahan" else "Simpan CM")
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

    val isPagi = shift.contains("Pagi", ignoreCase = true) || shift.contains("SH1", ignoreCase = true)

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
                Text(
                    text = "Pilih Shift (1-Tap):",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        true to "Pagi (SH1)",
                        false to "Malam (SH2)"
                    ).forEach { (pagiOption, label) ->
                        val selected = isPagi == pagiOption
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else Color.Transparent
                                )
                                .clickable {
                                    if (pagiOption) {
                                        shift = "Pagi(SH1)"
                                        aplusanShift = "Tim Shift Malam (SH2)"
                                    } else {
                                        shift = "Malam(SH2)"
                                        aplusanShift = "Tim Shift Pagi (SH1)"
                                    }
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

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

@Composable
fun NasUploadStatusChip(
    report: PmReportEntity,
    hasNasToken: Boolean,
    onTriggerUpload: () -> Unit
) {
    val warningOrange = Color(0xFFF59E0B)
    when (report.uploadStatus) {
        "SENT" -> {
            val serverId = report.uploadedReportId?.takeIf { it.isNotBlank() } ?: "NAS"
            Surface(
                shape = RoundedCornerShape(50),
                color = SuccessGreen.copy(alpha = 0.14f),
                modifier = Modifier
                    .border(1.dp, SuccessGreen.copy(alpha = 0.45f), RoundedCornerShape(50))
                    .testTag("nas_status_chip_sent_${report.id}")
            ) {
                Text(
                    text = "✓ NAS $serverId",
                    style = MaterialTheme.typography.labelSmall,
                    color = SuccessGreen,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    maxLines = 1
                )
            }
        }
        "UPLOADING" -> {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(50))
                    .testTag("nas_status_chip_uploading_${report.id}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(11.dp),
                        strokeWidth = 1.8.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Mengunggah…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
        "FAILED" -> {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = warningOrange.copy(alpha = 0.15f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, warningOrange.copy(alpha = 0.55f), RoundedCornerShape(50))
                        .clickable { onTriggerUpload() }
                        .testTag("nas_status_chip_failed_${report.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Upload ulang ke NAS",
                            tint = warningOrange,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Gagal, ketuk untuk upload ulang",
                            style = MaterialTheme.typography.labelSmall,
                            color = warningOrange,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
                if (!report.uploadError.isNullOrBlank()) {
                    Text(
                        text = report.uploadError,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        else -> {
            // "OFF" or "PENDING"
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(50))
                    .clickable { onTriggerUpload() }
                    .testTag("nas_status_chip_upload_${report.id}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = "Upload ke NAS",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (report.uploadStatus == "PENDING" && hasNasToken) {
                            "Antrean NAS (Ketuk Upload)"
                        } else {
                            "Upload ke NAS"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
