package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.util.AiProviderConfig
import com.example.viewmodel.PmMainViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiConfigDialog(
    viewModel: PmMainViewModel,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit
) {
    val currentConfig by viewModel.aiProviderConfig.collectAsStateWithLifecycle()
    val fetchedModels by viewModel.fetchedNineRouterModels.collectAsStateWithLifecycle()
    val testStatus by viewModel.nineRouterTestStatus.collectAsStateWithLifecycle()
    val isTesting by viewModel.isTestingNineRouter.collectAsStateWithLifecycle()

    var providerType by remember(currentConfig) { mutableStateOf(currentConfig.providerType) }
    var nineRouterUrl by remember(currentConfig) { mutableStateOf(currentConfig.nineRouterBaseUrl) }
    var nineRouterKey by remember(currentConfig) { mutableStateOf(currentConfig.nineRouterApiKey) }
    var nineRouterModel by remember(currentConfig) { mutableStateOf(currentConfig.nineRouterModel) }
    var geminiKey by remember(currentConfig) { mutableStateOf(currentConfig.geminiDirectApiKey) }
    var geminiModel by remember(currentConfig) { mutableStateOf(currentConfig.geminiDirectModel) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Router,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Pengaturan API AI (9Router / Gemini)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Gunakan API 9Router milik Anda atau Gemini API Key agar Scan Speedtest & Chat AI berjalan cepat di HP fisik:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Provider Selector Pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val options = listOf(
                        "NINE_ROUTER" to "9Router API",
                        "GEMINI_DIRECT" to "Gemini Key",
                        "FIREBASE" to "Firebase"
                    )
                    options.forEach { (typeId, label) ->
                        val selected = providerType == typeId
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { providerType = typeId }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                HorizontalDivider()

                when (providerType) {
                    "NINE_ROUTER" -> {
                        OutlinedTextField(
                            value = nineRouterUrl,
                            onValueChange = { nineRouterUrl = it },
                            label = { Text("Base URL 9Router") },
                            placeholder = { Text("https://domain-anda.com/v1 atau http://IP:20128/v1") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ninerouter_url_input")
                        )

                        OutlinedTextField(
                            value = nineRouterKey,
                            onValueChange = { nineRouterKey = it },
                            label = { Text("API Key 9Router") },
                            placeholder = { Text("Tempel API Key 9Router Anda...") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ninerouter_key_input")
                        )

                        OutlinedButton(
                            onClick = {
                                viewModel.testAndFetchNineRouterModels(nineRouterUrl, nineRouterKey)
                            },
                            enabled = !isTesting && nineRouterUrl.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cek Koneksi & Ambil Daftar Model")
                        }

                        if (!testStatus.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = testStatus!!,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        if (fetchedModels.isNotEmpty()) {
                            Text(
                                text = "Pilih model dari server 9Router Anda:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                fetchedModels.take(15).forEach { modelId ->
                                    FilterChip(
                                        selected = nineRouterModel == modelId,
                                        onClick = { nineRouterModel = modelId },
                                        label = { Text(modelId, style = MaterialTheme.typography.labelMedium) }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = nineRouterModel,
                            onValueChange = { nineRouterModel = it },
                            label = { Text("Model ID (Harus mendukung Vision/Gambar)") },
                            placeholder = { Text("Contoh: gemini/gemini-2.5-flash atau openai/gpt-4o") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ninerouter_model_input")
                        )
                    }

                    "GEMINI_DIRECT" -> {
                        OutlinedTextField(
                            value = geminiKey,
                            onValueChange = { geminiKey = it },
                            label = { Text("Google AI Studio Gemini API Key") },
                            placeholder = { Text("AIzaSy...") },
                            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = geminiModel,
                            onValueChange = { geminiModel = it },
                            label = { Text("Nama Model Gemini") },
                            placeholder = { Text("gemini-2.5-flash") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    else -> {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Mode Firebase AI Logic ditujukan untuk emulator internal AI Studio. Untuk pemakaian langsung di HP fisik, sangat disarankan memilih tab '9Router API' atau 'Gemini Key'.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = AiProviderConfig(
                        providerType = providerType,
                        nineRouterBaseUrl = nineRouterUrl.trim(),
                        nineRouterApiKey = nineRouterKey.trim(),
                        nineRouterModel = nineRouterModel.trim().ifEmpty { "gemini/gemini-2.5-flash" },
                        geminiDirectApiKey = geminiKey.trim(),
                        geminiDirectModel = geminiModel.trim().ifEmpty { "gemini-2.5-flash" }
                    )
                    viewModel.saveAiProviderConfig(updated)
                    onSaved(
                        when (providerType) {
                            "NINE_ROUTER" -> "API 9Router disimpan & diaktifkan!"
                            "GEMINI_DIRECT" -> "Gemini API Key disimpan & diaktifkan!"
                            else -> "Mode Firebase AI disimpan."
                        }
                    )
                },
                modifier = Modifier.testTag("save_ai_config_button")
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Simpan & Aktifkan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}
