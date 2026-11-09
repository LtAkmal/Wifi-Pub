package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppDatabase
import com.example.data.PmRepository
import com.example.ui.screens.AiStudioAndChatScreen
import com.example.ui.screens.DailyPmScreen
import com.example.ui.screens.PhotoCollageScreen
import com.example.ui.screens.TodayReportScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SuccessGreen
import com.example.viewmodel.PmMainViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var pendingIncomingIntent by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        if (intent?.action == Intent.ACTION_SEND || intent?.action == Intent.ACTION_SEND_MULTIPLE) {
            pendingIncomingIntent = intent
        }

        val database = AppDatabase.getDatabase(applicationContext)
        val repository = PmRepository(applicationContext, database.pmDao())

        setContent {
            MyApplicationTheme {
                val pmViewModel: PmMainViewModel = viewModel(
                    factory = PmMainViewModel.provideFactory(repository)
                )
                WifiPmHelperApp(
                    viewModel = pmViewModel,
                    incomingIntent = pendingIncomingIntent,
                    onConsumeIntent = { pendingIncomingIntent = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE) {
            pendingIncomingIntent = intent
        }
    }
}

@Composable
fun WifiPmHelperApp(
    viewModel: PmMainViewModel,
    incomingIntent: Intent?,
    onConsumeIntent: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showAiConfigDialog by rememberSaveable { mutableStateOf(false) }
    var showNasConfigDialog by rememberSaveable { mutableStateOf(false) }
    val pmTodayList by viewModel.pmReportsForSelectedDate.collectAsStateWithLifecycle()
    val collageBitmaps by viewModel.collageBitmaps.collectAsStateWithLifecycle()
    val displayDateEn by viewModel.displayDateEnInput.collectAsStateWithLifecycle()
    val aiConfig by viewModel.aiProviderConfig.collectAsStateWithLifecycle()
    val nasConfig by viewModel.nasUploadConfig.collectAsStateWithLifecycle()

    // Auto-retry any PENDING / FAILED NAS uploads when app opens & listen for NAS upload snackbars
    LaunchedEffect(Unit) {
        viewModel.retryPendingOrFailedUploads(context)
        viewModel.nasSnackbarEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(incomingIntent) {
        val currentIntent = incomingIntent ?: return@LaunchedEffect
        val mime = currentIntent.type.orEmpty()
        if (mime.startsWith("image/")) {
            when (currentIntent.action) {
                Intent.ACTION_SEND -> {
                    val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        currentIntent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        currentIntent.getParcelableExtra(Intent.EXTRA_STREAM)
                    }
                    if (uri != null) {
                        selectedTab = 0
                        viewModel.onSpeedtestImageSelected(context, uri, autoScan = true)
                        snackbarHostState.showSnackbar("Screenshot Speedtest diterima! AI sedang membaca angka...")
                    }
                }
                Intent.ACTION_SEND_MULTIPLE -> {
                    val uris: List<Uri>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        currentIntent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        currentIntent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                    }
                    if (!uris.isNullOrEmpty()) {
                        selectedTab = 1
                        viewModel.onCollagePhotosSelected(context, uris)
                        snackbarHostState.showSnackbar("${uris.take(4).size} foto dimuat ke Kolase < 1 MB!")
                    }
                }
            }
        }
        onConsumeIntent()
    }

    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CleanEnterpriseTopBar(
                displayDate = displayDateEn,
                pmCount = pmTodayList.size,
                hasCustomApi = aiConfig.hasCustomConfig,
                hasNasToken = nasConfig.token.isNotBlank(),
                onClickNasConfig = { showNasConfigDialog = true },
                onClickApiConfig = { showAiConfigDialog = true },
                onClickProgress = { selectedTab = 2 }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    val navColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        colors = navColors,
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == 0) Icons.Filled.Speed else Icons.Outlined.Speed,
                                contentDescription = "Input PM"
                            )
                        },
                        label = { Text("Input PM", fontWeight = FontWeight.Medium, maxLines = 1) },
                        modifier = Modifier.testTag("nav_tab_pm_speedtest")
                    )

                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        colors = navColors,
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (collageBitmaps.isNotEmpty()) {
                                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                            Text("${collageBitmaps.size}/4")
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (selectedTab == 1) Icons.Filled.GridView else Icons.Outlined.GridView,
                                    contentDescription = "4 Foto <1MB"
                                )
                            }
                        },
                        label = { Text("4 Foto", fontWeight = FontWeight.Medium, maxLines = 1) },
                        modifier = Modifier.testTag("nav_tab_collage")
                    )

                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        colors = navColors,
                        icon = {
                            BadgedBox(
                                badge = {
                                    Badge(
                                        containerColor = if (pmTodayList.size >= 10) SuccessGreen
                                        else MaterialTheme.colorScheme.secondary
                                    ) {
                                        Text("${pmTodayList.size}")
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (selectedTab == 2) Icons.Filled.Description else Icons.Outlined.Description,
                                    contentDescription = "Rekap Harian"
                                )
                            }
                        },
                        label = { Text("Rekap", fontWeight = FontWeight.Medium, maxLines = 1) },
                        modifier = Modifier.testTag("nav_tab_today_report")
                    )

                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        colors = navColors,
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == 3) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome,
                                contentDescription = "AI Tools"
                            )
                        },
                        label = { Text("Asisten AI", fontWeight = FontWeight.Medium, maxLines = 1) },
                        modifier = Modifier.testTag("nav_tab_ai_assistant")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> DailyPmScreen(
                    viewModel = viewModel,
                    onShowSnackbar = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
                    onNavigateToTodayReport = { selectedTab = 2 },
                    onNavigateToCollage = { selectedTab = 1 },
                    onOpenApiConfig = { showAiConfigDialog = true },
                    onOpenNasConfig = { showNasConfigDialog = true }
                )

                1 -> PhotoCollageScreen(
                    viewModel = viewModel,
                    onShowSnackbar = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                )

                2 -> TodayReportScreen(
                    viewModel = viewModel,
                    onShowSnackbar = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
                    onNavigateToAddPm = { selectedTab = 0 },
                    onOpenNasConfig = { showNasConfigDialog = true },
                    onOpenApiConfig = { showAiConfigDialog = true }
                )

                3 -> AiStudioAndChatScreen(
                    viewModel = viewModel,
                    onShowSnackbar = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
                    onNavigateToCollage = { selectedTab = 1 },
                    onOpenApiConfig = { showAiConfigDialog = true }
                )
            }
        }
    }

    if (showAiConfigDialog) {
        com.example.ui.screens.AiConfigDialog(
            viewModel = viewModel,
            onDismiss = { showAiConfigDialog = false },
            onSaved = { msg ->
                showAiConfigDialog = false
                scope.launch { snackbarHostState.showSnackbar(msg) }
            }
        )
    }

    if (showNasConfigDialog) {
        com.example.ui.screens.NasConfigDialog(
            viewModel = viewModel,
            onDismiss = { showNasConfigDialog = false },
            onSaved = { msg ->
                showNasConfigDialog = false
                scope.launch { snackbarHostState.showSnackbar(msg) }
            }
        )
    }
}

@Composable
private fun CleanEnterpriseTopBar(
    displayDate: String,
    pmCount: Int,
    hasCustomApi: Boolean,
    hasNasToken: Boolean,
    onClickNasConfig: () -> Unit,
    onClickApiConfig: () -> Unit,
    onClickProgress: () -> Unit
) {
    val compactDate = remember(displayDate) {
        displayDate
            .replace("January", "Jan")
            .replace("February", "Feb")
            .replace("March", "Mar")
            .replace("April", "Apr")
            .replace("August", "Aug")
            .replace("September", "Sep")
            .replace("October", "Oct")
            .replace("November", "Nov")
            .replace("December", "Dec")
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "WiFi PM Helper v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.titleMedium,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "CGK • $compactDate",
                            style = MaterialTheme.typography.labelMedium,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    // NAS Upload Config Pill Button
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (hasNasToken) SuccessGreen.copy(alpha = 0.14f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .border(
                                width = 1.dp,
                                color = if (hasNasToken) SuccessGreen.copy(alpha = 0.45f)
                                else MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(50)
                            )
                            .clickable { onClickNasConfig() }
                            .testTag("top_bar_nas_config_button")
                    ) {
                        Text(
                            text = if (hasNasToken) "NAS ✓" else "NAS",
                            style = MaterialTheme.typography.labelMedium,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (hasNasToken) SuccessGreen else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    // 9Router / API Config Pill Button
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (hasCustomApi) SuccessGreen.copy(alpha = 0.14f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .border(
                                width = 1.dp,
                                color = if (hasCustomApi) SuccessGreen.copy(alpha = 0.45f)
                                else MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(50)
                            )
                            .clickable { onClickApiConfig() }
                            .testTag("top_bar_api_config_button")
                    ) {
                        Text(
                            text = if (hasCustomApi) "9Router ✓" else "Atur API",
                            style = MaterialTheme.typography.labelMedium,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (hasCustomApi) SuccessGreen else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    // Target Progress Pill
                    val isComplete = pmCount >= 10
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (isComplete) SuccessGreen.copy(alpha = 0.14f)
                                else MaterialTheme.colorScheme.primaryContainer
                            )
                            .border(
                                width = 1.dp,
                                color = if (isComplete) SuccessGreen.copy(alpha = 0.45f)
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(50)
                            )
                            .clickable { onClickProgress() }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isComplete) SuccessGreen else MaterialTheme.colorScheme.primary
                                )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$pmCount/10",
                            style = MaterialTheme.typography.labelLarge,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            color = if (isComplete) SuccessGreen else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
        }
    }
}
