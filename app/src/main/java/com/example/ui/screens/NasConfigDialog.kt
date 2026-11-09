package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.NasUploadConfig
import com.example.ui.theme.SuccessGreen
import com.example.viewmodel.PmMainViewModel

@Composable
fun NasConfigDialog(
    viewModel: PmMainViewModel,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit
) {
    val context = LocalContext.current
    val currentConfig by viewModel.nasUploadConfig.collectAsStateWithLifecycle()
    val isTesting by viewModel.isTestingNasConnection.collectAsStateWithLifecycle()
    val testStatus by viewModel.nasTestStatus.collectAsStateWithLifecycle()
    val testSuccess by viewModel.nasTestSuccess.collectAsStateWithLifecycle()

    var serverUrl by remember(currentConfig.serverUrl) {
        mutableStateOf(currentConfig.serverUrl.ifBlank { "https://akmal-satrio-n.work.gd/pm" })
    }
    var token by remember(currentConfig.token) {
        mutableStateOf(currentConfig.token)
    }
    var autoUploadOnSave by remember(currentConfig.autoUploadOnSave) {
        mutableStateOf(currentConfig.autoUploadOnSave)
    }
    var autoDetectLocationFromPhoto1 by remember(currentConfig.autoDetectLocationFromPhoto1) {
        mutableStateOf(currentConfig.autoDetectLocationFromPhoto1)
    }
    var showTokenPassword by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = "Pengaturan Upload NAS",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Kirim 4 foto PM terpisah ke server riwayat publik",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Server URL Field
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = {
                        serverUrl = it
                        viewModel.clearNasTestStatus()
                    },
                    label = { Text("URL Server NAS") },
                    placeholder = { Text("https://akmal-satrio-n.work.gd/pm") },
                    leadingIcon = {
                        Icon(Icons.Default.Link, contentDescription = null)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("nas_server_url_input")
                )

                // 2. Token Upload Field (Password + Paste Button)
                OutlinedTextField(
                    value = token,
                    onValueChange = {
                        token = it
                        viewModel.clearNasTestStatus()
                    },
                    label = { Text("Token Upload (Bearer)") },
                    placeholder = { Text("Tempel token autentikasi server...") },
                    singleLine = true,
                    visualTransformation = if (showTokenPassword) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val pasted = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
                                    if (!pasted.isNullOrBlank()) {
                                        token = pasted
                                        viewModel.clearNasTestStatus()
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Paste Token",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { showTokenPassword = !showTokenPassword },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (showTokenPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showTokenPassword) "Sembunyikan Token" else "Tampilkan Token",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("nas_upload_token_input")
                )

                // 3. Test Connection Button & Status Banner
                OutlinedButton(
                    onClick = {
                        viewModel.testNasConnection(serverUrl = serverUrl, token = token)
                    },
                    enabled = !isTesting,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .testTag("nas_test_connection_button")
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Mengetes koneksi ke server...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tes Koneksi (/api/auth/check)",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (!testStatus.isNullOrBlank()) {
                    val isOk = testSuccess == true
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isOk) SuccessGreen.copy(alpha = 0.14f)
                        else MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = if (isOk) SuccessGreen.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .testTag("nas_test_status_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isOk) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = if (isOk) SuccessGreen else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = testStatus.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isOk) SuccessGreen else MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                // 4. Auto-Upload Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 10.dp)
                    ) {
                        Text(
                            text = "Upload otomatis saat Simpan PM",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Kirim 4 foto slot terpisah ke NAS di background setiap kali klik Simpan PM",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = autoUploadOnSave,
                        onCheckedChange = { autoUploadOnSave = it },
                        modifier = Modifier.testTag("nas_auto_upload_switch")
                    )
                }

                // 5. Auto-Detect Location from Photo 1 Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 10.dp)
                    ) {
                        Text(
                            text = "Deteksi lokasi otomatis dari Foto 1",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Bandingkan Foto 1 (AP) dengan referensi lokasi di NAS menggunakan AI & GPS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = autoDetectLocationFromPhoto1,
                        onCheckedChange = { autoDetectLocationFromPhoto1 = it },
                        modifier = Modifier.testTag("nas_auto_detect_location_switch")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = NasUploadConfig(
                        serverUrl = serverUrl.trim().ifBlank { "https://akmal-satrio-n.work.gd/pm" },
                        token = token.trim(),
                        autoUploadOnSave = autoUploadOnSave,
                        autoDetectLocationFromPhoto1 = autoDetectLocationFromPhoto1
                    )
                    viewModel.saveNasUploadConfig(context, updated)
                    onSaved("Pengaturan Upload NAS berhasil disimpan!")
                },
                modifier = Modifier.testTag("save_nas_config_button")
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
