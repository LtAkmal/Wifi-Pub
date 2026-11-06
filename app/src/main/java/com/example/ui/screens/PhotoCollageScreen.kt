package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WifiTethering
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.SuccessGreen
import com.example.util.CollageGenerator
import com.example.viewmodel.CollageUiState
import com.example.viewmodel.PmMainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PhotoCollageScreen(
    viewModel: PmMainViewModel,
    onShowSnackbar: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val collageSlots by viewModel.collageSlots.collectAsStateWithLifecycle()
    val includeHeader by viewModel.includeCollageHeader.collectAsStateWithLifecycle()
    val fitEntirePhoto by viewModel.fitEntirePhoto.collectAsStateWithLifecycle()
    val collageState by viewModel.collageState.collectAsStateWithLifecycle()
    val location by viewModel.locationInput.collectAsStateWithLifecycle()
    val uploadMbps by viewModel.uploadMbpsInput.collectAsStateWithLifecycle()
    val downloadMbps by viewModel.downloadMbpsInput.collectAsStateWithLifecycle()
    val displayDateEn by viewModel.displayDateEnInput.collectAsStateWithLifecycle()

    var replaceSlotIndex by remember { mutableIntStateOf(-1) }
    var showBuiltInSpeedtest by remember { mutableStateOf(false) }
    var showBuiltInWifiAnalyzer by remember { mutableStateOf(false) }

    var pendingCameraSlot by remember { mutableIntStateOf(0) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            viewModel.onCameraPhotoCaptured(context, pendingCameraSlot, uri) { slot ->
                onShowSnackbar("Foto Kamera otomatis masuk ke FOTO ${slot + 1}!")
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
            }
        } else {
            onShowSnackbar("Izin kamera diperlukan untuk mengambil foto langsung.")
        }
    }

    fun launchCameraForSlot(slotIdx: Int) {
        pendingCameraSlot = slotIdx
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            val uri = CollageGenerator.createCameraCaptureUri(context, slotIdx)
            pendingCameraUri = uri
            if (uri != null) {
                takePictureLauncher.launch(uri)
            }
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val pickMultiplePhotosLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 4)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onCollagePhotosSelected(context, uris)
        }
    }

    val pickSingleSlotLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && replaceSlotIndex >= 0) {
            viewModel.replaceCollagePhotoAt(context, replaceSlotIndex, uri)
        }
    }

    val filledCount = collageSlots.count { it != null }
    var todayDdMmYy by remember {
        mutableStateOf(SimpleDateFormat("dd/MM/yy • HH:mm:ss", Locale.getDefault()).format(Date()))
    }
    LaunchedEffect(Unit) {
        while (true) {
            todayDdMmYy = SimpleDateFormat("dd/MM/yy • HH:mm:ss", Locale.getDefault()).format(Date())
            delay(1000L)
        }
    }

    // Automatically re-render the collage whenever location, speedtest, date, header toggle, or fit toggle changes!
    LaunchedEffect(location, uploadMbps, downloadMbps, displayDateEn, includeHeader, fitEntirePhoto, filledCount) {
        if (filledCount > 0) {
            viewModel.generateCollage(context, debounceMs = 150L)
        }
    }

    val slotMeta = listOf(
        Triple("FOTO 1 • KAMERA AP", "Ambil Kamera 1", Icons.Default.CameraAlt),
        Triple("FOTO 2 • KAMERA AREA", "Ambil Kamera 2", Icons.Default.CameraAlt),
        Triple("FOTO 3 • SPEEDTEST", "Mulai Speedtest", Icons.Default.Speed),
        Triple("FOTO 4 • WIFI ANALYZER", "WiFi Analyzer", Icons.Default.WifiTethering)
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("photo_collage_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Clean 2x2 Photo Grid Card (Foto 1-2 Camera, Foto 3 Speedtest, Foto 4 WiFi Analyzer)
        item {
            CleanSectionCard(
                stepNumber = "1",
                title = "Susun 4 Foto Dokumentasi ($filledCount/4)",
                subtitle = "Foto 1-2 Kamera, Foto 3 Speedtest, Foto 4 WiFi Analyzer (Utuh 100%)",
                trailingHeader = {
                    if (filledCount > 0) {
                        IconButton(
                            onClick = { viewModel.clearCollagePhotos() },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("clear_collage_photos_button")
                        ) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = "Reset Foto",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            ) {
                // Quick Action Bar: Kamera 1-2, Speedtest, WiFi Analyzer, or Gallery
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            val nextSlot = viewModel.nextAvailableCameraSlot()
                            launchCameraForSlot(nextSlot)
                        },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kamera (1-2)", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    OutlinedButton(
                        onClick = {
                            pickMultiplePhotosLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .testTag("pick_4_photos_button")
                    ) {
                        Icon(Icons.Default.Collections, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Dari Galeri", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                // Mode Tampilan Foto Selector (Default: Utuh / Tanpa Potong)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val modes = listOf(
                        true to "Utuh 100% (Tanpa Potong)",
                        false to "Penuh Kotak (Crop)"
                    )
                    modes.forEach { (isFitMode, label) ->
                        val selected = fitEntirePhoto == isFitMode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else Color.Transparent
                                )
                                .clickable { viewModel.setFitEntirePhoto(context, isFitMode) }
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isFitMode) {
                                    Icon(
                                        imageVector = Icons.Default.CropFree,
                                        contentDescription = null,
                                        tint = if (selected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
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

                // Clean 2x2 Grid showing all 4 dedicated slots
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (row in 0..1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            for (col in 0..1) {
                                val slotIdx = row * 2 + col
                                val bmp = collageSlots.getOrNull(slotIdx)
                                val (slotTitle, primaryActionText, primaryIcon) = slotMeta[slotIdx]

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(0.76f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF0B1120))
                                        .border(
                                            width = 1.dp,
                                            color = if (bmp != null) SuccessGreen.copy(alpha = 0.5f)
                                            else MaterialTheme.colorScheme.outline,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            when (slotIdx) {
                                                0, 1 -> launchCameraForSlot(slotIdx)
                                                2 -> showBuiltInSpeedtest = true
                                                3 -> showBuiltInWifiAnalyzer = true
                                            }
                                        }
                                        .testTag("collage_slot_$slotIdx"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = slotTitle,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(if (fitEntirePhoto) 4.dp else 0.dp),
                                            contentScale = if (fitEntirePhoto) ContentScale.Fit else ContentScale.Crop
                                        )
                                        // Top-Start Role Badge
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(6.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color.Black.copy(alpha = 0.75f))
                                                .padding(horizontal = 7.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = slotTitle,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 9.5.sp,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                        }
                                        // Bottom-Start Gallery Replace Pill
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(6.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color.Black.copy(alpha = 0.72f))
                                                .clickable {
                                                    replaceSlotIndex = slotIdx
                                                    pickSingleSlotLauncher.launch(
                                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                    )
                                                }
                                                .padding(horizontal = 7.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "Galeri",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 10.sp,
                                                color = Color(0xFF93C5FD),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        IconButton(
                                            onClick = { viewModel.removeCollagePhotoAt(context, slotIdx) },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(6.dp)
                                                .size(26.dp)
                                                .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Hapus",
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    } else {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                            modifier = Modifier.padding(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = primaryIcon,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = slotTitle,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = primaryActionText,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = Color.White,
                                                fontWeight = FontWeight.SemiBold,
                                                textAlign = TextAlign.Center,
                                                maxLines = 1
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFF1E293B),
                                                modifier = Modifier.clickable {
                                                    replaceSlotIndex = slotIdx
                                                    pickSingleSlotLauncher.launch(
                                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                    )
                                                }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        Icons.Default.AddPhotoAlternate,
                                                        contentDescription = null,
                                                        tint = Color(0xFF94A3B8),
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "Pilih Galeri",
                                                        fontSize = 10.sp,
                                                        color = Color(0xFFCBD5E1)
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

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                // Mandatory Bottom Watermark Badge Info
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Watermark Wajib Aktif di Bawah Gambar:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "• $todayDdMmYy   • App By Akmal Satrio N   • Visit https://akmal-satrio-n.work.gd",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Compact Header Banner Toggle
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
                            text = "Banner Lokasi & Speedtest di Atas Kolase",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Menampilkan nama lokasi & hasil speedtest di bagian atas",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Switch(
                        checked = includeHeader,
                        onCheckedChange = { viewModel.setIncludeCollageHeader(context, it) },
                        modifier = Modifier.testTag("toggle_collage_header_switch")
                    )
                }

                if (includeHeader) {
                    OutlinedTextField(
                        value = location,
                        onValueChange = { viewModel.updateLocation(it) },
                        label = { Text("Judul Lokasi pada Kolase") },
                        placeholder = { Text("Ketik nama lokasi...") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // 2. Collage Preview & <1MB Verification Card
        item {
            when (val state = collageState) {
                is CollageUiState.Generating -> {
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
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            Text(
                                text = "Menyusun kolase utuh + watermark & kompresi < 1 MB...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                is CollageUiState.Ready -> {
                    val res = state.result
                    val sizeKb = res.sizeBytes / 1024
                    CleanSectionCard(
                        stepNumber = "2",
                        title = "Hasil Kolase Siap Upload",
                        subtitle = "Seluruh 4 foto utuh + Watermark di bagian paling bawah (< 1 MB)",
                        trailingHeader = {
                            Surface(
                                color = SuccessGreen.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(50)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = SuccessGreen,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "$sizeKb KB",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = SuccessGreen,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.testTag("collage_size_badge")
                                    )
                                }
                            }
                        }
                    ) {
                        Image(
                            bitmap = res.bitmap.asImageBitmap(),
                            contentDescription = "Hasil Kolase 4 Foto Utuh",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(12.dp)
                                ),
                            contentScale = ContentScale.FillWidth
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        val savedUri = CollageGenerator.saveCollageToGallery(
                                            context = context,
                                            jpegBytes = res.jpegBytes,
                                            locationName = location
                                        )
                                        if (savedUri != null) {
                                            onShowSnackbar("Disimpan ke Galeri ($sizeKb KB)!")
                                        } else {
                                            onShowSnackbar("Gagal menyimpan ke Galeri.")
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("save_collage_gallery_button")
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Simpan Galeri", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }

                            OutlinedButton(
                                onClick = { shareCollageFile(context, res.file) },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("share_collage_file_button")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Bagikan File", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }

                is CollageUiState.Error -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                is CollageUiState.Idle -> {}
            }
        }
    }

    if (showBuiltInSpeedtest) {
        BuiltInSpeedtestDialog(
            viewModel = viewModel,
            onDismiss = { showBuiltInSpeedtest = false },
            onCompletedToast = { msg -> onShowSnackbar(msg) }
        )
    }

    if (showBuiltInWifiAnalyzer) {
        BuiltInWifiAnalyzerDialog(
            viewModel = viewModel,
            onDismiss = { showBuiltInWifiAnalyzer = false },
            onCapturedToast = { msg -> onShowSnackbar(msg) }
        )
    }
}

fun shareCollageFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Bagikan Foto Kolase (< 1 MB)"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
