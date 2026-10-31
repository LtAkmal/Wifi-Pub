package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppDatabase
import com.example.data.PmRepository
import com.example.ui.screens.AiStudioAndChatScreen
import com.example.ui.screens.DailyPmScreen
import com.example.ui.screens.PhotoCollageScreen
import com.example.ui.screens.TodayReportScreen
import com.example.ui.theme.MyApplicationTheme
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

@OptIn(ExperimentalMaterial3Api::class)
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
    val pmTodayList by viewModel.pmReportsForSelectedDate.collectAsStateWithLifecycle()
    val collageBitmaps by viewModel.collageBitmaps.collectAsStateWithLifecycle()

    // Handle shared images from Speedtest app or Gallery
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
                        snackbarHostState.showSnackbar("Foto Speedtest diterima! AI sedang mendeteksi angka...")
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
                        snackbarHostState.showSnackbar("${uris.take(4).size} foto diterima untuk Kolase < 1 MB!")
                    }
                }
            }
        }
        onConsumeIntent()
    }

    // BackHandler on secondary tabs to return to Home (Tab 0)
    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (selectedTab) {
                            0 -> "WiFi PM & Speedtest AI"
                            1 -> "Kolase 4 Foto (< 1 MB)"
                            2 -> "Today Report (${pmTodayList.size}/10)"
                            else -> "Asisten AI & Studio Gambar"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 0) Icons.Filled.Speed else Icons.Outlined.Speed,
                            contentDescription = "PM Speedtest"
                        )
                    },
                    label = { Text("PM Speedtest") },
                    modifier = Modifier.testTag("nav_tab_pm_speedtest")
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (collageBitmaps.isNotEmpty()) {
                                    Badge { Text("${collageBitmaps.size}/4") }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (selectedTab == 1) Icons.Filled.GridView else Icons.Outlined.GridView,
                                contentDescription = "Kolase 4 Foto"
                            )
                        }
                    },
                    label = { Text("Kolase <1MB") },
                    modifier = Modifier.testTag("nav_tab_collage")
                )

                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                        BadgedBox(
                            badge = {
                                Badge { Text("${pmTodayList.size}/10") }
                            }
                        ) {
                            Icon(
                                imageVector = if (selectedTab == 2) Icons.Filled.Description else Icons.Outlined.Description,
                                contentDescription = "Today Report"
                            )
                        }
                    },
                    label = { Text("Today Report") },
                    modifier = Modifier.testTag("nav_tab_today_report")
                )

                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 3) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome,
                            contentDescription = "Asisten AI"
                        )
                    },
                    label = { Text("Asisten AI") },
                    modifier = Modifier.testTag("nav_tab_ai_assistant")
                )
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
                    onNavigateToCollage = { selectedTab = 1 }
                )

                1 -> PhotoCollageScreen(
                    viewModel = viewModel,
                    onShowSnackbar = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                )

                2 -> TodayReportScreen(
                    viewModel = viewModel,
                    onShowSnackbar = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
                    onNavigateToAddPm = { selectedTab = 0 }
                )

                3 -> AiStudioAndChatScreen(
                    viewModel = viewModel,
                    onShowSnackbar = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
                    onNavigateToCollage = { selectedTab = 1 }
                )
            }
        }
    }
}
