package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.SuccessGreen
import com.example.util.WifiApItem
import com.example.viewmodel.PmMainViewModel
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * Full-screen Speedtest UI matching image_ab1a80a0.png.
 * Automatically inputs Download/Upload into PM form and captures screenshot into Foto 3 upon completion!
 */
@Composable
fun BuiltInSpeedtestDialog(
    viewModel: PmMainViewModel,
    onDismiss: () -> Unit,
    onCompletedToast: (String) -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.builtInSpeedtestState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.initNetworkInfo(context)
    }

    val animatedSpeed by animateFloatAsState(
        targetValue = state.currentGaugeMbps.toFloat(),
        animationSpec = tween(durationMillis = 160),
        label = "speed_gauge"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0E101A))
                .testTag("builtin_speedtest_dialog"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar: SPEEDTEST logo + Close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup",
                        tint = Color(0xFFA5ACC4)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = Color(0xFFA5ACC4),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SPEEDTEST",
                        color = Color(0xFFA5ACC4),
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        letterSpacing = 2.sp
                    )
                }

                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = null,
                    tint = Color(0xFFA5ACC4),
                    modifier = Modifier.size(24.dp)
                )
            }

            // Live Download & Upload Readout Bar (shown during/after test)
            if (state.phase != "IDLE") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.ArrowDownward,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "DOWNLOAD",
                                color = Color(0xFF8F96B2),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = if (state.downloadMbps > 0) String.format(Locale.US, "%.1f", state.downloadMbps) else "—",
                            color = Color.White,
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp
                        )
                        Text("Mbps", color = Color(0xFF6C7493), fontSize = 12.sp)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.ArrowUpward,
                                contentDescription = null,
                                tint = Color(0xFFA855F7),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "UPLOAD",
                                color = Color(0xFF8F96B2),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = if (state.uploadMbps > 0) String.format(Locale.US, "%.1f", state.uploadMbps) else "—",
                            color = Color.White,
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp
                        )
                        Text("Mbps", color = Color(0xFF6C7493), fontSize = 12.sp)
                    }
                }

                Text(
                    text = "Ping: ${state.pingMs} ms   •   Jitter: ${state.jitterMs} ms",
                    color = Color(0xFFFACC15),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Spacer(modifier = Modifier.height(60.dp))
            }

            Spacer(modifier = Modifier.weight(1f))

            // Central "GO" Circular Ring / Live Speedometer Gauge
            Box(
                modifier = Modifier
                    .size(250.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !state.isRunning) {
                        viewModel.runBuiltInSpeedtest(context) { res ->
                            onCompletedToast(
                                "Speedtest selesai (Down ${String.format(Locale.US, "%.1f", res.downloadMbps)} / Up ${String.format(Locale.US, "%.1f", res.uploadMbps)} Mbps) & otomatis masuk ke Foto 3!"
                            )
                        }
                    }
                    .testTag("speedtest_go_circle_button"),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val outerR = size.minDimension / 2f - 6.dp.toPx()
                    val innerR = size.minDimension / 2f - 28.dp.toPx()

                    // Subtle outer ring (#112E38)
                    drawCircle(
                        color = Color(0xFF132A38),
                        radius = outerR,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )

                    if (!state.isRunning) {
                        // Crisp Emerald Ring (#00E599) matching image_ab1a80a0.png
                        drawCircle(
                            color = Color(0xFF00E599),
                            radius = innerR,
                            center = center,
                            style = Stroke(width = 3.5.dp.toPx())
                        )
                    } else {
                        // Animated Speedtest Gauge Arc
                        val arcRect = Size(innerR * 2, innerR * 2)
                        val topLeft = Offset(center.x - innerR, center.y - innerR)
                        drawArc(
                            color = Color(0xFF1F2437),
                            startAngle = 135f,
                            sweepAngle = 270f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcRect,
                            style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                        )
                        val sweep = ((animatedSpeed / 120f).coerceIn(0.05f, 1f)) * 270f
                        val activeColor = if (state.phase == "UPLOAD") Color(0xFFA855F7) else Color(0xFF00E5FF)
                        drawArc(
                            color = activeColor,
                            startAngle = 135f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcRect,
                            style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                if (!state.isRunning) {
                    Text(
                        text = "GO",
                        color = Color.White,
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 52.sp
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (state.phase == "PING") "PING..."
                            else String.format(Locale.US, "%.1f", animatedSpeed),
                            color = Color.White,
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 42.sp
                        )
                        Text(
                            text = when (state.phase) {
                                "DOWNLOAD" -> "↓ DOWNLOAD Mbps"
                                "UPLOAD" -> "↑ UPLOAD Mbps"
                                else -> "MENGUJI..."
                            },
                            color = Color(0xFF00E599),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = if (state.autoCapturedToSlot3) {
                    "✅ Otomatis di-input & disimpan ke FOTO 3!"
                } else {
                    "You are connected to the internet"
                },
                color = Color(0xFF00BA7C),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )

            if (state.autoCapturedToSlot3) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BA7C)),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.heightIn(min = 44.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Selesai & Kembali ke Form PM", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Bottom Provider Card matching image_ab1a80a0.png
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF191C2B),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF3B4059))
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .border(2.dp, Color(0xFF565D7A), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = null,
                                tint = Color(0xFF7D85A6),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = state.ispName,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = state.deviceName,
                                color = Color(0xFF8F96B2),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            // Bottom Navigation Strip (Speed | Video | Downdetector | Map)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF10121D))
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val navItems = listOf(
                    Triple("Speed", Icons.Default.Speed, true),
                    Triple("Video", Icons.Default.PlayArrow, false),
                    Triple("Downdetector", Icons.Default.ErrorOutline, false),
                    Triple("Map", Icons.Default.Place, false)
                )
                navItems.forEach { (label, icon, active) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = if (active) Color.White else Color(0xFF666D8A),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = label,
                            color = if (active) Color.White else Color(0xFF666D8A),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Full-screen WiFi Analyzer UI matching image_4e87a276.png ("Access Points", 2.4|5|6 GHz toggle,
 * IN RANGE counter, by Strength, SSID/BSSID/Channel/Vendor details, and Circular Analog dBm Gauge).
 * Automatically captures & uploads screenshot to Foto 4 (Slot 4)!
 */
@Composable
fun BuiltInWifiAnalyzerDialog(
    viewModel: PmMainViewModel,
    onDismiss: () -> Unit,
    onCapturedToast: (String) -> Unit
) {
    val context = LocalContext.current
    val allAccessPoints by viewModel.wifiAccessPoints.collectAsStateWithLifecycle()
    val selectedBand by viewModel.selectedWifiBand.collectAsStateWithLifecycle()
    val isScanning by viewModel.isWifiScanning.collectAsStateWithLifecycle()
    val capturedToSlot4 by viewModel.wifiAnalyzerCapturedToSlot4.collectAsStateWithLifecycle()

    val filteredAps = allAccessPoints.filter { it.bandGhz == selectedBand }.ifEmpty { allAccessPoints }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.scanWifiAnalyzer(context, autoCaptureToSlot4 = true)
    }

    LaunchedEffect(Unit) {
        val hasFineLoc = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasFineLoc) {
            viewModel.scanWifiAnalyzer(context, autoCaptureToSlot4 = true)
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1B1B1B))
                .testTag("builtin_wifi_analyzer_dialog")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. Top Header Bar (#121212) - "Access Points" + 2.4 | 5 | 6 GHz pill + Pause + PRO + More
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF121212))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Tutup",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Access Points",
                                color = Color.White,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // 2.4 | 5 | 6 GHz Pill
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFF2C2C2C))
                                    .padding(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf("2.4", "5", "6").forEach { band ->
                                    val isSel = selectedBand == band
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(if (isSel) Color(0xFF3B82F6) else Color.Transparent)
                                            .clickable { viewModel.setSelectedWifiBand(band) }
                                            .padding(horizontal = 12.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = band,
                                            color = if (isSel) Color.White else Color(0xFF8E8E8E),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "GHz",
                                color = Color(0xFF777777),
                                fontSize = 10.sp
                            )
                        }

                        // Right Icons: Pause/Refresh, PRO badge, MoreVert
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    viewModel.scanWifiAnalyzer(context, autoCaptureToSlot4 = true) {
                                        onCapturedToast("WiFi Analyzer diperbarui & otomatis masuk ke Foto 4!")
                                    }
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = if (isScanning) Icons.Default.Refresh else Icons.Default.Pause,
                                    contentDescription = "Scan Ulang",
                                    tint = Color(0xFFD4D4D4)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFD4D4D4))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "PRO",
                                    color = Color.Black,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = null,
                                tint = Color(0xFFD4D4D4),
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .size(20.dp)
                            )
                        }
                    }

                    // 2. Icon Tabs Row (6 icons, 5th icon "Access Points" active with yellow bottom bar)
                    val tabIcons = listOf(
                        Icons.Default.BarChart,
                        Icons.Default.ShowChart,
                        Icons.Default.Star,
                        Icons.Default.Devices,
                        Icons.Default.WifiTethering,
                        Icons.Default.GridView
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        tabIcons.forEachIndexed { idx, icon ->
                            val active = idx == 4
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier
                                        .padding(vertical = 10.dp)
                                        .size(24.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .background(if (active) Color(0xFFE6B800) else Color.Transparent)
                                )
                            }
                        }
                    }
                }

                // 3. "IN RANGE X" & "by Strength" Filter Strip (#141414)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF141414))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "IN RANGE",
                            color = Color(0xFFD4D4D4),
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "${filteredAps.size}",
                            color = Color(0xFFD99B26),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = null,
                            tint = Color(0xFFD4D4D4),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "by Strength",
                            color = Color(0xFFD99B26),
                            fontSize = 14.sp
                        )
                    }
                }

                // 4. Access Points List with Circular Analog dBm Gauge
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    itemsIndexed(filteredAps) { index, ap ->
                        WifiAccessPointRow(ap = ap, isFirstConnected = index == 0 && ap.isConnected)
                        HorizontalDivider(color = Color(0xFF383838), thickness = 1.dp)
                    }
                }
            }

            // Bottom Floating Action Bar (Matches image_4e87a276.png "Ambil" button + WiFi badge)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xEE121212))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (capturedToSlot4) "✅ Tersimpan di FOTO 4 (Kolase)" else "Siap disimpan ke FOTO 4",
                        color = if (capturedToSlot4) SuccessGreen else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Otomatis masuk ke urutan ke-4 pada 4 Foto Dokumentasi",
                        color = Color(0xFF9E9E9E),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Button(
                    onClick = {
                        viewModel.captureWifiAnalyzerToSlot4(context) {
                            onCapturedToast("Screenshot WiFi Analyzer berhasil dimasukkan ke FOTO 4!")
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6D00)),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .testTag("capture_wifi_analyzer_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Ambil (Foto 4)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun WifiAccessPointRow(
    ap: WifiApItem,
    isFirstConnected: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1B1B1B))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Technical Details Column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Row 1: Connected WiFi Icon + SSID + [WiFi 5] badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (ap.isConnected) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = Color(0xFFE6A800),
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(20.dp)
                        )
                    }
                    Text(
                        text = ap.ssid,
                        color = if (ap.isHidden) Color(0xFFC94A29) else Color(0xFFDDA000),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF2A2A2A))
                            .border(1.dp, Color(0xFF7A6220), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = ap.wifiStandardLabel,
                            color = Color(0xFFB8B8B8),
                            fontSize = 11.sp
                        )
                    }
                }

                // Row 2: BSSID + Distance (0m, 21m, etc.)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = ap.bssid,
                        color = Color(0xFFBDBDBD),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "${ap.distanceMeters}m",
                        color = Color(0xFF00BCD4),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Row 3: 5745MHz (80MHz) + CH 149(155)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${ap.frequencyMhz}MHz (${ap.channelWidthMhz}MHz)",
                        color = Color(0xFFBDBDBD),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "CH ${ap.channelNumber}(${ap.centerChannelNumber})",
                        color = Color(0xFF00BCD4),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Row 4: Lock + WPA2 + 702Mbps + [Huawei]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color(0xFFFACC15),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = ap.securityLabel,
                        color = Color(0xFFBDBDBD),
                        fontSize = 14.sp
                    )
                    if (ap.linkSpeedMbps != null) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "${ap.linkSpeedMbps}Mbps",
                            color = Color(0xFF5C9CE6),
                            fontSize = 14.sp
                        )
                    }
                    if (ap.vendorName.isNotBlank()) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "[${ap.vendorName}]",
                            color = Color(0xFFB58918),
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Right Side: Circular Analog dBm Gauge matching image_4e87a276.png
            AnalogDbmGaugeComposable(
                rssiDbm = ap.rssiDbm,
                modifier = Modifier.size(92.dp)
            )
        }

        // Sub-action strip under first connected AP (matches image_4e87a276.png)
        if (isFirstConnected) {
            HorizontalDivider(color = Color(0xFF383838), thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Speed, contentDescription = null, tint = Color(0xFFD4D4D4), modifier = Modifier.size(18.dp))
                Box(modifier = Modifier.width(1.dp).height(16.dp).background(Color(0xFF424242)))
                Icon(Icons.Default.Devices, contentDescription = null, tint = Color(0xFFD4D4D4), modifier = Modifier.size(18.dp))
                Box(modifier = Modifier.width(1.dp).height(16.dp).background(Color(0xFF424242)))
                Icon(Icons.Default.Router, contentDescription = null, tint = Color(0xFFD4D4D4), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun AnalogDbmGaugeComposable(
    rssiDbm: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f - 2.dp.toPx()

            // Dark Navy Circle (#080C24)
            drawCircle(
                color = Color(0xFF080C24),
                radius = radius,
                center = center
            )
            drawCircle(
                color = Color(0xFF1D2342),
                radius = radius,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )

            // White Tick Marks around perimeter (135 deg to 405 deg)
            val totalTicks = 16
            for (i in 0..totalTicks) {
                val angleDeg = 135f + (i.toFloat() / totalTicks) * 270f
                val rad = Math.toRadians(angleDeg.toDouble())
                val innerR = if (i % 4 == 0) radius * 0.76f else radius * 0.84f
                val outerR = radius * 0.94f
                val start = Offset(
                    center.x + (innerR * cos(rad)).toFloat(),
                    center.y + (innerR * sin(rad)).toFloat()
                )
                val end = Offset(
                    center.x + (outerR * cos(rad)).toFloat(),
                    center.y + (outerR * sin(rad)).toFloat()
                )
                drawLine(
                    color = Color(0xFFD4D4D8),
                    start = start,
                    end = end,
                    strokeWidth = 1.2.dp.toPx()
                )
            }

            // Orange Needle pointing to signal dBm (-100 to -30 dBm)
            val norm = ((rssiDbm.coerceIn(-100, -30) + 100) / 70f).coerceIn(0f, 1f)
            val needleDeg = 135f + norm * 270f
            val needleRad = Math.toRadians(needleDeg.toDouble())
            val tip = Offset(
                center.x + (radius * 0.82f * cos(needleRad)).toFloat(),
                center.y + (radius * 0.82f * sin(needleRad)).toFloat()
            )
            drawLine(
                color = Color(0xFFF59E0B),
                start = center,
                end = tip,
                strokeWidth = 2.4.dp.toPx(),
                cap = StrokeCap.Round
            )

            // White Pivot Dot
            drawCircle(
                color = Color.White,
                radius = 4.5.dp.toPx(),
                center = center
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 34.dp)
        ) {
            Text(
                text = "$rssiDbm",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 16.sp
            )
            Text(
                text = "dBm",
                color = Color(0xFFD99B26),
                fontSize = 10.sp,
                lineHeight = 11.sp
            )
        }
    }
}
