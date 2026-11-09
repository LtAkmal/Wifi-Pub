package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.SuccessGreen
import com.example.viewmodel.AiImageUiState
import com.example.viewmodel.ChatModelOption
import com.example.viewmodel.PmMainViewModel

@Composable
fun AiStudioAndChatScreen(
    viewModel: PmMainViewModel,
    onShowSnackbar: (String) -> Unit,
    onNavigateToCollage: () -> Unit,
    onOpenApiConfig: () -> Unit = {}
) {
    var selectedSubTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("ai_studio_and_chat_screen")
    ) {
        // Sleek Pill Switcher
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val tabs = listOf(
                    Triple(0, "Chat Teknisi AI", Icons.Default.Chat),
                    Triple(1, "Studio Foto AI", Icons.Default.Image)
                )
                tabs.forEach { (idx, label, icon) ->
                    val isSelected = selectedSubTab == idx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surface
                            )
                            .clickable { selectedSubTab = idx }
                            .padding(vertical = 10.dp, horizontal = 6.dp)
                            .testTag(if (idx == 0) "subtab_chatbot_button" else "subtab_image_ai_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        if (selectedSubTab == 0) {
            GeminiTechChatbotSection(
                viewModel = viewModel,
                onShowSnackbar = onShowSnackbar,
                onOpenApiConfig = onOpenApiConfig
            )
        } else {
            SmartPhotoStudioSection(
                viewModel = viewModel,
                onShowSnackbar = onShowSnackbar,
                onNavigateToCollage = onNavigateToCollage
            )
        }
    }
}

@Composable
private fun GeminiTechChatbotSection(
    viewModel: PmMainViewModel,
    onShowSnackbar: (String) -> Unit,
    onOpenApiConfig: () -> Unit
) {
    val context = LocalContext.current
    val selectedModel by viewModel.selectedChatModel.collectAsStateWithLifecycle()
    val messages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isLoading by viewModel.isChatLoading.collectAsStateWithLifecycle()
    val chatError by viewModel.chatError.collectAsStateWithLifecycle()
    val aiConfig by viewModel.aiProviderConfig.collectAsStateWithLifecycle()

    var inputPrompt by remember { mutableStateOf("") }

    val quickPrompts = listOf(
        "Buatkan kalimat CM untuk penggantian kabel LAN AP",
        "Apakah speed Up 44 Mbps / Down 66 Mbps optimal?",
        "Solusi jika Access Point tidak memancarkan SSID"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Model / Provider Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (aiConfig.hasCustomConfig) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SuccessGreen.copy(alpha = 0.14f),
                    modifier = Modifier
                        .border(1.dp, SuccessGreen.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .clickable { onOpenApiConfig() }
                ) {
                    Text(
                        text = if (aiConfig.providerType == "NINE_ROUTER") {
                            "Aktif: 9Router (${aiConfig.nineRouterModel.substringAfterLast('/')})"
                        } else {
                            "Aktif: Gemini (${aiConfig.geminiDirectModel})"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = SuccessGreen,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            } else {
                ChatModelOption.entries.forEach { option ->
                    FilterChip(
                        selected = selectedModel == option,
                        onClick = { viewModel.setChatModel(option) },
                        label = { Text(option.label, style = MaterialTheme.typography.labelMedium) },
                        modifier = Modifier.testTag("chat_model_chip_${option.name.lowercase()}")
                    )
                }
            }
            IconButton(
                onClick = { viewModel.clearChatHistory() },
                modifier = Modifier
                    .size(32.dp)
                    .testTag("clear_chat_history_button")
            ) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Bersihkan Chat",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Scrollable Message Thread
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("chat_messages_list"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val alignment = if (msg.isUser) Alignment.CenterEnd else Alignment.CenterStart
                val containerColor = if (msg.isUser) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface
                }
                val contentColor = if (msg.isUser) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = alignment
                ) {
                    Surface(
                        shape = RoundedCornerShape(
                            topStart = 14.dp,
                            topEnd = 14.dp,
                            bottomStart = if (msg.isUser) 14.dp else 4.dp,
                            bottomEnd = if (msg.isUser) 4.dp else 14.dp
                        ),
                        color = containerColor,
                        modifier = Modifier
                            .widthIn(max = 310.dp)
                            .then(
                                if (!msg.isUser) {
                                    Modifier.border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(
                                            topStart = 14.dp,
                                            topEnd = 14.dp,
                                            bottomStart = 4.dp,
                                            bottomEnd = 14.dp
                                        )
                                    )
                                } else Modifier
                            )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (!msg.isUser && msg.modelUsed.isNotBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = msg.modelUsed,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            copyToClipboard(context, "AI Response", msg.text)
                                            onShowSnackbar("Pesan disalin!")
                                        },
                                        modifier = Modifier.size(22.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = "Salin",
                                            modifier = Modifier.size(13.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            Text(
                                text = msg.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = contentColor
                            )
                        }
                    }
                }
            }
        }

        if (chatError != null) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = chatError!!,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        // Quick Prompt Pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickPrompts.forEach { suggestion ->
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
                        .clickable { viewModel.sendChatMessage(suggestion) }
                ) {
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        maxLines = 1
                    )
                }
            }
        }

        // Clean Input Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = inputPrompt,
                onValueChange = { inputPrompt = it },
                placeholder = { Text("Tanya asisten teknisi...") },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_field"),
                maxLines = 3
            )
            IconButton(
                onClick = {
                    val toSend = inputPrompt
                    inputPrompt = ""
                    viewModel.sendChatMessage(toSend)
                },
                enabled = inputPrompt.isNotBlank() && !isLoading,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (inputPrompt.isNotBlank() && !isLoading) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .testTag("send_chat_message_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Kirim",
                        tint = if (inputPrompt.isNotBlank()) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SmartPhotoStudioSection(
    viewModel: PmMainViewModel,
    onShowSnackbar: (String) -> Unit,
    onNavigateToCollage: () -> Unit
) {
    val context = LocalContext.current
    val inputBitmap by viewModel.aiImageInputBitmap.collectAsStateWithLifecycle()
    val aiImageState by viewModel.aiImageState.collectAsStateWithLifecycle()

    var imagePrompt by remember {
        mutableStateOf("Tingkatkan pencahayaan & kontras foto Access Point ini serta periksa kebersihan fisiknya untuk dokumentasi PM")
    }
    var addTechnicalStamp by remember { mutableStateOf(true) }

    val studioPresets = listOf(
        "Perjelas & Terangkan Foto AP",
        "Cek Kebersihan Fisik AP (CM)",
        "Kartu Ringkasan Speedtest T3"
    )

    val pickSourceImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.setAiImageInputFromUri(context, uri)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            CleanSectionCard(
                stepNumber = "AI",
                title = "Studio Foto Dokumentasi PM",
                subtitle = "Optimasi kecerahan foto AP, Stempel Lokasi T3 & Analisis Vision 9Router"
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            pickSourceImageLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("pick_ai_edit_source_button")
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (inputBitmap == null) "Pilih Foto Perangkat / AP" else "Ganti Foto Sumber",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (inputBitmap != null) {
                        IconButton(onClick = { viewModel.clearAiImageInput() }) {
                            Icon(Icons.Default.Close, contentDescription = "Hapus Foto")
                        }
                    }
                }

                if (inputBitmap != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Image(
                            bitmap = inputBitmap!!.asImageBitmap(),
                            contentDescription = "Foto Sumber",
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0B1120)),
                            contentScale = ContentScale.Fit
                        )
                        Text(
                            text = "Foto siap diperjelas, diberi stempel PM CGK, dan dianalisis dengan AI.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Preset Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    studioPresets.forEach { preset ->
                        FilterChip(
                            selected = imagePrompt == preset,
                            onClick = { imagePrompt = preset },
                            label = { Text(preset, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }

                OutlinedTextField(
                    value = imagePrompt,
                    onValueChange = { imagePrompt = it },
                    label = { Text("Instruksi Optimasi / Pemeriksaan Foto") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_image_prompt_input"),
                    minLines = 2
                )

                // Toggle Technical Watermark Stamp
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
                            text = "Stempel Dokumentasi PM di Bawah Foto",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Mencantumkan Lokasi, Tanggal & Speedtest pada foto",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Switch(
                        checked = addTechnicalStamp,
                        onCheckedChange = { addTechnicalStamp = it }
                    )
                }

                Button(
                    onClick = { viewModel.generateOrEditImageWithAi(imagePrompt, addTechnicalStamp) },
                    enabled = aiImageState !is AiImageUiState.Loading && imagePrompt.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("generate_ai_image_button")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (inputBitmap != null) "Proses & Perjelas Foto Dokumentasi"
                        else "Buat Kartu Dokumentasi Teknis",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        item {
            when (val st = aiImageState) {
                is AiImageUiState.Loading -> {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            Text(
                                text = "Memproses foto & analisis dokumentasi...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                is AiImageUiState.Success -> {
                    CleanSectionCard(
                        stepNumber = "✓",
                        title = "Hasil Studio Dokumentasi",
                        subtitle = st.modeInfo.ifBlank { "Siap dimasukkan ke Kolase 4 Foto (< 1 MB)" }
                    ) {
                        Image(
                            bitmap = st.bitmap.asImageBitmap(),
                            contentDescription = "Hasil Studio Foto",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0B1120))
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.FillWidth
                        )

                        if (st.aiInspectionNote.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = SuccessGreen,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Hasil Pemeriksaan / Catatan Studio",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = st.aiInspectionNote,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                        Button(
                            onClick = {
                                viewModel.addGeneratedAiImageToCollage(context, st.bitmap)
                                onShowSnackbar("Ditambahkan ke Kolase 4 Foto (< 1 MB)!")
                                onNavigateToCollage()
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("send_ai_image_to_collage_button")
                        ) {
                            Icon(Icons.Default.GridView, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Masukkan ke Kolase 4 Foto (< 1 MB)",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                is AiImageUiState.Error -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = st.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                is AiImageUiState.Idle -> {}
            }
        }
    }
}
