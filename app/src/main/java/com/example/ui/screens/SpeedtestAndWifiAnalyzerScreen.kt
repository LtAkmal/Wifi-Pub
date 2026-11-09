package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material.icons.filled.Web
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.SuccessGreen
import com.example.util.NetworkTelemetryEngine
import com.example.util.WifiApItem
import com.example.viewmodel.PmMainViewModel
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Full-screen Speedtest UI matching:
 * - Start state: image_ab1a80a0.png (Green GO circle + ISP/Device card)
 * - Result state: image_3208efc3.png (100% original Speedtest result layout with Outlined Download/Upload cards,
 *   Ping/Loaded Latency/Jitter row, 4 Category Quality Icons with 5 dots, Cyan GO circle,
 *   ISP/Device + Phone/Server icons + Speedtest.net Server Sponsor/City + Test ID + Feedback card).
 * Automatically inputs Download/Upload into PM form and captures screenshot into Foto 3!
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
        animationSpec = tween(durationMillis = 150),
        label = "speed_gauge"
    )

    val triggerSpeedtest = {
        if (!state.isRunning) {
            viewModel.runBuiltInSpeedtest(context) { res ->
                onCompletedToast(
                    "Speedtest selesai (Down ${String.format(Locale.US, "%.1f", res.downloadMbps)} / Up ${String.format(Locale.US, "%.1f", res.uploadMbps)} Mbps • Test ID ${res.testId}) & otomatis masuk ke Foto 3!"
                )
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D0F1A))
                .testTag("builtin_speedtest_dialog"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Top App Bar matching image_3208efc3.png: [X]   (speed) SPEEDTEST   [Share] [Menu]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup",
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = Color(0xFF9DA4BE),
                        modifier = Modifier.size(21.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SPEEDTEST",
                        color = Color(0xFF9DA4BE),
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        letterSpacing = 2.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.IosShare,
                        contentDescription = "Share",
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier
                            .padding(end = 14.dp)
                            .size(21.dp)
                    )
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            if (state.phase == "IDLE") {
                // =========================================================================
                // INITIAL START SCREEN (100% matching image_ab1a80a0.png)
                // =========================================================================
                Spacer(modifier = Modifier.weight(1f))

                Box(
                    modifier = Modifier
                        .size(246.dp)
                        .clip(CircleShape)
                        .clickable { triggerSpeedtest() }
                        .testTag("speedtest_go_circle_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val outerR = size.minDimension / 2f - 6.dp.toPx()
                        val innerR = size.minDimension / 2f - 28.dp.toPx()

                        drawCircle(
                            color = Color(0xFF132A38),
                            radius = outerR,
                            center = center,
                            style = Stroke(width = 2.dp.toPx())
                        )
                        drawCircle(
                            color = Color(0xFF00E599),
                            radius = innerR,
                            center = center,
                            style = Stroke(width = 3.5.dp.toPx())
                        )
                    }
                    Text(
                        text = "GO",
                        color = Color.White,
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 52.sp
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                Text(
                    text = "You are connected to the internet",
                    color = Color(0xFF00BA7C),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )

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
            } else {
                // =========================================================================
                // TESTING / COMPLETED RESULT SCREEN (100% matching image_3208efc3.png)
                // =========================================================================
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Main Speedtest Card (#171929)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF171929),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // 1. Outlined Download & Upload Boxes
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OoklaSpeedMetricBox(
                                    title = "Download",
                                    speedMbps = state.downloadMbps,
                                    isUpload = false,
                                    modifier = Modifier.weight(1f)
                                )
                                OoklaSpeedMetricBox(
                                    title = "Upload",
                                    speedMbps = state.uploadMbps,
                                    isUpload = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // 2. Ping ms | Loaded Down | Loaded Up | Jitter ms Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Ping ms (Yellow)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Ping ms ",
                                        color = Color(0xFF7E85A3),
                                        fontSize = 12.5.sp
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(15.dp)
                                            .border(1.5.dp, Color(0xFFFACC15), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.SwapVert,
                                            contentDescription = null,
                                            tint = Color(0xFFFACC15),
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "${state.pingMs}",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }

                                // Loaded Download Ping (Cyan)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(15.dp)
                                            .border(1.5.dp, Color(0xFF2DD4BF), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.SwapVert,
                                            contentDescription = null,
                                            tint = Color(0xFF2DD4BF),
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = if (state.dlLoadedPingMs > 0) "${state.dlLoadedPingMs}" else "—",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }

                                // Loaded Upload Ping (Purple)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(15.dp)
                                            .border(1.5.dp, Color(0xFFA855F7), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.SwapVert,
                                            contentDescription = null,
                                            tint = Color(0xFFA855F7),
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = if (state.ulLoadedPingMs > 0) "${state.ulLoadedPingMs}" else "—",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }

                                // Jitter ms
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(15.dp)
                                            .border(1.5.dp, Color(0xFF7E85A3), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.ShowChart,
                                            contentDescription = null,
                                            tint = Color(0xFF7E85A3),
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Jitter ms ",
                                        color = Color(0xFF7E85A3),
                                        fontSize = 12.5.sp
                                    )
                                    Text(
                                        text = "${state.jitterMs}",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // 3. Four Category Quality Icons with 5 Dots (Web, Gaming, Video, Video Call)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val categories: List<Pair<ImageVector, Int>> = listOf(
                                    Icons.Default.Web to 5,
                                    Icons.Default.SportsEsports to 5,
                                    Icons.Default.SmartDisplay to if (state.downloadMbps >= 50.0) 5 else 4,
                                    Icons.Default.VideoCall to 5
                                )
                                categories.forEach { (icon, activeDots) ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(modifier = Modifier.height(5.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                            for (d in 1..5) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(5.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            if (d <= activeDots) Color.White
                                                            else Color(0xFF565C78)
                                                        )
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(22.dp))

                            // 4. Center Cyan "GO" Circle (or Live Speed Gauge while running)
                            Box(
                                modifier = Modifier
                                    .size(200.dp)
                                    .clip(CircleShape)
                                    .clickable(enabled = !state.isRunning) { triggerSpeedtest() }
                                    .testTag("speedtest_go_circle_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val center = Offset(size.width / 2f, size.height / 2f)
                                    val radius = size.minDimension / 2f - 6.dp.toPx()

                                    if (!state.isRunning) {
                                        // Bright Sky-Blue Circle (#00A8FF) matching image_3208efc3.png
                                        drawCircle(
                                            color = Color(0xFF00A8FF),
                                            radius = radius,
                                            center = center,
                                            style = Stroke(width = 2.5.dp.toPx())
                                        )
                                    } else {
                                        val arcRect = Size(radius * 2, radius * 2)
                                        val topLeft = Offset(center.x - radius, center.y - radius)
                                        drawArc(
                                            color = Color(0xFF232842),
                                            startAngle = 135f,
                                            sweepAngle = 270f,
                                            useCenter = false,
                                            topLeft = topLeft,
                                            size = arcRect,
                                            style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                                        )
                                        val sweep = ((animatedSpeed / 140f).coerceIn(0.05f, 1f)) * 270f
                                        val activeColor = if (state.phase == "UPLOAD") Color(0xFFA855F7) else Color(0xFF00E5D4)
                                        drawArc(
                                            color = activeColor,
                                            startAngle = 135f,
                                            sweepAngle = sweep,
                                            useCenter = false,
                                            topLeft = topLeft,
                                            size = arcRect,
                                            style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                                        )
                                    }
                                }

                                if (!state.isRunning) {
                                    Text(
                                        text = "GO",
                                        color = Color.White,
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 48.sp
                                    )
                                } else {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = if (state.phase == "PING") "PING..."
                                            else String.format(Locale.US, "%.1f", animatedSpeed),
                                            color = Color.White,
                                            fontFamily = SpaceGroteskFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 38.sp
                                        )
                                        Text(
                                            text = when (state.phase) {
                                                "DOWNLOAD" -> "↓ DOWNLOAD Mbps"
                                                "UPLOAD" -> "↑ UPLOAD Mbps"
                                                else -> "MENGHUBUNGKAN..."
                                            },
                                            color = Color(0xFF00A8FF),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(22.dp))

                            // 5. ISP/Device + Phone/Server Icons + Server Sponsor/City + Test ID
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        text = state.ispName,
                                        color = Color(0xFF8F96B2),
                                        fontSize = 13.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = state.deviceName,
                                        color = Color(0xFF68708E),
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Icon(
                                    imageVector = Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    tint = Color(0xFF68708E),
                                    modifier = Modifier.size(28.dp)
                                )

                                Spacer(modifier = Modifier.width(18.dp))

                                Icon(
                                    imageVector = Icons.Default.Dns,
                                    contentDescription = null,
                                    tint = Color(0xFF68708E),
                                    modifier = Modifier.size(28.dp)
                                )

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Text(
                                        text = state.serverSponsor,
                                        color = Color(0xFF8F96B2),
                                        fontSize = 13.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = state.serverCity,
                                        color = Color(0xFF68708E),
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            val openOfficialResult = {
                                try {
                                    val url = "https://www.speedtest.net/result/${state.testId}"
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                } catch (_: Exception) {
                                }
                            }

                            Text(
                                text = "Test ID ${state.testId}",
                                color = Color(0xFF68708E),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.clickable { openOfficialResult() }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Detailed result",
                                    color = Color(0xFF1DA1F2),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.clickable { openOfficialResult() }
                                )
                                Text(
                                    text = "Test Again",
                                    color = Color(0xFF1DA1F2),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.clickable { triggerSpeedtest() }
                                )
                            }
                        }
                    }

                    // Feedback Card (#171929) matching image_3208efc3.png
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF171929),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF68708E)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ChatBubbleOutline,
                                        contentDescription = null,
                                        tint = Color(0xFF171929),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Feedback",
                                    color = Color(0xFFB0B7D1),
                                    fontSize = 14.5.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                if (state.autoCapturedToSlot3) {
                                    Text(
                                        text = "✅ Masuk FOTO 3 • Selesai",
                                        color = SuccessGreen,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable { onDismiss() }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = Color(0xFF252940), thickness = 1.dp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "How does your download speed compare\nwith your expectations?",
                                color = Color(0xFFC5CBE3),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Bottom Navigation Strip (Speed | Video | Downdetector | Map)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D0F1A))
                    .padding(vertical = 10.dp),
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
                        Spacer(modifier = Modifier.height(3.dp))
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

@Composable
private fun OoklaSpeedMetricBox(
    title: String,
    speedMbps: Double,
    isUpload: Boolean,
    modifier: Modifier = Modifier
) {
    val accentColor = if (isUpload) Color(0xFFA855F7) else Color(0xFF2DD4BF)
    val formattedSpeed = if (speedMbps <= 0.0) {
        "—"
    } else if (speedMbps >= 100.0) {
        speedMbps.roundToInt().toString()
    } else {
        String.format(Locale.US, "%.1f", speedMbps)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFF636985), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .border(1.5.dp, accentColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isUpload) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(10.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formattedSpeed,
                color = Color.White,
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 36.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Mbps",
                color = Color(0xFF7E85A3),
                fontSize = 12.5.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
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

    var selectedApBssid by remember { mutableStateOf<String?>(null) }
    val selectedAp = remember(filteredAps, allAccessPoints, selectedApBssid) {
        if (selectedApBssid == null) null
        else allAccessPoints.firstOrNull { it.bssid.equals(selectedApBssid, ignoreCase = true) }
    }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInputText by remember { mutableStateOf("") }

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

                // 4. Access Points List with Circular Analog dBm Gauge (Click any AP to open Detail Sheet!)
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    itemsIndexed(filteredAps) { index, ap ->
                        WifiAccessPointRow(
                            ap = ap,
                            isFirstConnected = index == 0 && ap.isConnected,
                            onClick = {
                                selectedApBssid = ap.bssid
                                viewModel.captureWifiAnalyzerToSlot4(context, selectedAp = ap)
                            }
                        )
                        HorizontalDivider(color = Color(0xFF383838), thickness = 1.dp)
                    }
                }
            }

            // 5. Interactive Access Point Detail Overlay (100% matching Screenshot_20261106-093330_cceb785d.jpg)
            if (selectedAp != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable {
                            selectedApBssid = null
                            viewModel.captureWifiAnalyzerToSlot4(context, selectedAp = null)
                        }
                )

                WifiApDetailBottomSheet(
                    ap = selectedAp,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    onRenameClick = {
                        renameInputText = selectedAp.ssid
                        showRenameDialog = true
                    },
                    onCaptureAndClose = {
                        viewModel.captureWifiAnalyzerToSlot4(context, selectedAp = selectedAp) {
                            onCapturedToast("Detail ${selectedAp.ssid} berhasil dimasukkan ke FOTO 4!")
                            onDismiss()
                        }
                    }
                )
            } else {
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
                            text = if (capturedToSlot4) "✅ Tersimpan di FOTO 4 (Ketuk WiFi untuk Detail)" else "Ketuk WiFi untuk lihat Detail lengkap",
                            color = if (capturedToSlot4) SuccessGreen else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Klik salah satu SSID di atas untuk menampilkan detail sinyal, IP, & Vendor",
                            color = Color(0xFF9E9E9E),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Button(
                        onClick = {
                            viewModel.captureWifiAnalyzerToSlot4(context, selectedAp = null) {
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

            if (showRenameDialog && selectedAp != null) {
                AlertDialog(
                    onDismissRequest = { showRenameDialog = false },
                    title = { Text("Rename Access Point") },
                    text = {
                        OutlinedTextField(
                            value = renameInputText,
                            onValueChange = { renameInputText = it },
                            label = { Text("Nama SSID / Alias") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.renameWifiAccessPoint(
                                    context = context,
                                    bssid = selectedAp.bssid,
                                    newSsid = renameInputText
                                )
                                showRenameDialog = false
                            }
                        ) {
                            Text("Simpan")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRenameDialog = false }) {
                            Text("Batal")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun WifiApDetailBottomSheet(
    ap: WifiApItem,
    modifier: Modifier = Modifier,
    onRenameClick: () -> Unit,
    onCaptureAndClose: () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = false) {}
            .testTag("wifi_ap_detail_sheet"),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = Color(0xFF181818)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = ap.ssid,
                    color = Color(0xFFDDA000),
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF26200E))
                        .border(1.5.dp, Color(0xFF85681A), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = ap.wifiStandardLabel,
                        color = Color(0xFFDDA000),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (ap.isConnected) "• Connected" else "• In Range (${ap.securityLabel})",
                color = if (ap.isConnected) Color(0xFF22C55E) else Color(0xFF9E9E9E),
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(128.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val radius = size.minDimension / 2f - 12.dp.toPx()

                        drawCircle(
                            color = Color(0xFF00E5FF).copy(alpha = 0.16f),
                            radius = radius + 6.dp.toPx(),
                            center = center,
                            style = Stroke(width = 16.dp.toPx())
                        )
                        drawCircle(
                            color = Color(0xFF00E5FF).copy(alpha = 0.32f),
                            radius = radius + 2.dp.toPx(),
                            center = center,
                            style = Stroke(width = 10.dp.toPx())
                        )
                        drawCircle(
                            color = Color(0xFF23272A),
                            radius = radius - 4.dp.toPx(),
                            center = center
                        )
                        drawCircle(
                            color = Color(0xFF1D353C),
                            radius = radius,
                            center = center,
                            style = Stroke(width = 10.dp.toPx())
                        )

                        val sweep = (((ap.rssiDbm.coerceIn(-100, -30) + 100) / 70f) * 320f).coerceIn(60f, 340f)
                        val arcRect = Size(radius * 2, radius * 2)
                        drawArc(
                            color = Color(0xFF00E5FF),
                            startAngle = -90f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = arcRect,
                            style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${ap.rssiDbm}",
                            color = Color.White,
                            fontSize = 31.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 32.sp
                        )
                        Text(
                            text = "dBm",
                            color = Color(0xFFD4D4D8),
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(20.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ApDetailKeyValueRow(label = "Band", value = "${ap.bandGhz} GHz", valueColor = Color(0xFF60A5FA))
                    ApDetailKeyValueRow(label = "Channel", value = "${ap.channelNumber}", valueColor = Color(0xFF00E5FF))
                    ApDetailKeyValueRow(label = "Width", value = "${ap.channelWidthMhz} MHz", valueColor = Color(0xFF34D399))
                    ApDetailKeyValueRow(label = "Frequency", value = "${ap.frequencyMhz} MHz", valueColor = Color(0xFF60A5FA))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFF2E2E2E), thickness = 1.dp)
            Spacer(modifier = Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                ApDetailKeyValueRow(
                    label = "IP address",
                    value = if (ap.isConnected) ap.ipAddress else "—",
                    valueColor = Color(0xFF60A5FA)
                )
                ApDetailKeyValueRow(
                    label = "Security",
                    value = ap.rawSecurityCapabilities,
                    valueColor = Color(0xFFF87171)
                )
                ApDetailKeyValueRow(
                    label = "Vendor",
                    value = ap.fullVendorName,
                    valueColor = Color(0xFFDDA000)
                )
                ApDetailKeyValueRow(
                    label = "Distance",
                    value = "${ap.distanceMeters}m",
                    valueColor = Color(0xFF00E5FF)
                )
                ApDetailKeyValueRow(
                    label = "BSSID",
                    value = ap.bssid,
                    valueColor = Color(0xFFF1F5F9)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1F1D14))
                        .border(1.5.dp, Color(0xFF85681A), RoundedCornerShape(10.dp))
                        .clickable { onRenameClick() }
                        .padding(horizontal = 18.dp, vertical = 9.dp)
                        .testTag("wifi_ap_rename_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Rename",
                            tint = Color(0xFFFACC15),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Rename",
                            color = Color(0xFFDDA000),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFFFACC15),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = onCaptureAndClose,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6D00)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Ambil (Foto 4)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ApDetailKeyValueRow(
    label: String,
    value: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color(0xFF9E9E9E),
            fontSize = 15.sp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            color = valueColor,
            fontSize = 15.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

@Composable
private fun WifiAccessPointRow(
    ap: WifiApItem,
    isFirstConnected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1B1B1B))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${ap.frequencyMhz}MHz (${ap.channelWidthMhz}MHz)",
                        color = Color(0xFFBDBDBD),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = NetworkTelemetryEngine.formatChannelDisplay(ap),
                        color = Color(0xFF00BCD4),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

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

        // Bottom Text inside Gauge: "-58" and "dBm"
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp)
        ) {
            Text(
                text = "$rssiDbm",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 16.sp
            )
            Text(
                text = "dBm",
                color = Color(0xFFD99B26),
                fontSize = 10.sp,
                lineHeight = 10.sp
            )
        }
    }
}
