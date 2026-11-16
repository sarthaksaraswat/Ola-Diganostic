package com.sarthak.olacan

import com.sarthak.olacan.ui.theme.AppFont
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.sarthak.olacan.service.CanStreamManager
import com.sarthak.olacan.ui.components.SavedLogDialog
import com.sarthak.olacan.ui.screens.BatteryScreen
import com.sarthak.olacan.ui.screens.CalibrationScreen
import com.sarthak.olacan.ui.screens.ConnectScreen
import com.sarthak.olacan.ui.screens.OverviewScreen
import com.sarthak.olacan.ui.screens.PowertrainScreen
import com.sarthak.olacan.ui.screens.RawCanScreen
import com.sarthak.olacan.ui.screens.WarningsScreen
import com.sarthak.olacan.ui.theme.M3Error
import com.sarthak.olacan.ui.theme.M3OnPrimaryContainer
import com.sarthak.olacan.ui.theme.M3OnSurface
import com.sarthak.olacan.ui.theme.M3OnSurfaceVariant
import com.sarthak.olacan.ui.theme.M3Outline
import com.sarthak.olacan.ui.theme.M3OutlineVariant
import com.sarthak.olacan.ui.theme.M3Primary
import com.sarthak.olacan.ui.theme.M3PrimaryContainer
import com.sarthak.olacan.ui.theme.M3Success
import com.sarthak.olacan.ui.theme.M3Surface
import com.sarthak.olacan.ui.theme.M3SurfaceContainer
import com.sarthak.olacan.ui.theme.M3SurfaceContainerHigh
import com.sarthak.olacan.ui.theme.M3Warning
import com.sarthak.olacan.analyzer.VehicleSelfTestManager
import com.sarthak.olacan.ui.theme.OlaCanViewerTheme
import com.sarthak.olacan.util.FaultCatalog
import com.sarthak.olacan.util.HapticHelper
import com.sarthak.olacan.viewmodel.TelemetryViewModel
import java.util.Locale

enum class MainAppTab(val title: String, val icon: ImageVector) {
    COCKPIT("Cockpit", Icons.Default.Dashboard),
    BATTERY("Battery", Icons.Default.BatteryChargingFull),
    MOTOR("Motor", Icons.Default.Speed),
    FAULTS("Faults", Icons.Default.Warning),
    CALIBRATE("Calibrate", Icons.Default.Tune),
    CAN_LOGS("CAN Logs", Icons.AutoMirrored.Filled.List)
}

class MainActivity : ComponentActivity() {

    private val viewModel: TelemetryViewModel by viewModels()

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* Granted */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            // Initialize helpers & analytics persistence
            com.sarthak.olacan.util.AppLogManager.init(this)
            com.sarthak.olacan.util.LogRecorder.recoverOrphanedLogs(this)
            HapticHelper.init(this)
            FaultCatalog.init(this)
            VehicleSelfTestManager.init(this)
        } catch (e: Throwable) {
            com.sarthak.olacan.util.AppLogManager.error("MainActivity", "Error during onCreate init: ${e.message}", e)
        }

        // Request notification permission on Android 13+ for background foreground service
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } catch (_: Throwable) {}

        setContent {
            OlaCanViewerTheme {
                MainContent(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainContent(viewModel: TelemetryViewModel) {
    val context = LocalContext.current
    val view = LocalView.current
    val stats by viewModel.streamStats.collectAsState()
    val activeAlerts by viewModel.activeAlerts.collectAsState()
    val lastSavedLog by viewModel.lastSavedLog.collectAsState()
    val isDemo by viewModel.isDemoMode.collectAsState()
    val hapticsOn by HapticHelper.hapticsEnabled.collectAsState()

    var selectedTab by remember { mutableStateOf(MainAppTab.COCKPIT) }
    var showConnectScreen by remember { mutableStateOf(!stats.isConnected) }
    var wasConnected by remember { mutableStateOf(stats.isConnected) }
    var lastConnectedHost by remember { mutableStateOf(stats.connectedHost) }

    // When a fresh connection is established (disconnected -> connected) or vehicle is switched, auto-close connect screen
    LaunchedEffect(stats.isConnected, stats.connectedHost) {
        val hostSwitched = stats.isConnected && stats.connectedHost.isNotBlank() && lastConnectedHost.isNotBlank() && stats.connectedHost != lastConnectedHost
        val freshConnect = !wasConnected && stats.isConnected
        if (freshConnect || hostSwitched) {
            showConnectScreen = false
        }
        wasConnected = stats.isConnected
        lastConnectedHost = stats.connectedHost
    }

    if (showConnectScreen) {
        ConnectScreen(
            viewModel = viewModel,
            onConnectedNavigate = {
                HapticHelper.performClick(view)
                showConnectScreen = false
                selectedTab = MainAppTab.COCKPIT
            },
            onClose = {
                HapticHelper.performClick(view)
                showConnectScreen = false
            }
        )
    } else {
        Scaffold(
            topBar = {
                Surface(
                    color = M3SurfaceContainer,
                    border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.4f)),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .statusBarsPadding()
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Left: Brand & Diagnostics Title
                            Row(
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(M3PrimaryContainer, RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = null,
                                        tint = M3OnPrimaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = "Ola Diagnostic",
                                        color = M3OnSurface,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = AppFont,
                                        maxLines = 1
                                    )
                                    val statusLabel = when {
                                        isDemo -> "Demo Mode"
                                        !stats.isConnected -> "Vehicle Offline"
                                        !stats.isAwake -> "Standby • ${stats.getEffectiveVehicleName()}"
                                        else -> "Connected • ${stats.getEffectiveVehicleName()}"
                                    }
                                    val statusColor = when {
                                        isDemo -> M3Primary
                                        !stats.isConnected -> M3Outline
                                        !stats.isAwake -> M3Warning
                                        else -> M3Success
                                    }
                                    Text(
                                        text = statusLabel,
                                        color = statusColor,
                                        fontSize = 11.sp,
                                        fontFamily = AppFont,
                                        maxLines = 1
                                    )
                                }
                            }

                            // Right: Green / Amber / Red dot + Switch / Connect button
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Status Dot: Green when active, Amber when in sleep standby, Red when disconnected
                                val dotColor = when {
                                    !stats.isConnected -> M3Error
                                    !stats.isAwake && !isDemo -> M3Warning
                                    else -> M3Success
                                }
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(dotColor, CircleShape)
                                )

                                // Switch / Connect Button
                                Surface(
                                    color = M3SurfaceContainerHigh,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier.clickable {
                                        HapticHelper.performClick(view)
                                        showConnectScreen = true
                                    }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                                            contentDescription = if (stats.isConnected) "Switch Vehicle" else "Connect Vehicle",
                                            tint = M3Primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = if (stats.isConnected) "Switch" else "Connect",
                                            color = M3Primary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            fontFamily = AppFont
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            bottomBar = {
                NavigationBar(
                    containerColor = M3SurfaceContainer,
                    tonalElevation = 4.dp
                ) {
                    MainAppTab.values().forEach { tab ->
                        val isSelected = selectedTab == tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                HapticHelper.performClick(view)
                                selectedTab = tab
                            },
                            icon = {
                                if (tab == MainAppTab.FAULTS && activeAlerts.isNotEmpty()) {
                                    BadgedBox(
                                        badge = {
                                            Badge(
                                                containerColor = M3Error,
                                                contentColor = M3Surface
                                            ) {
                                                Text(
                                                    text = "${activeAlerts.size}",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = tab.title,
                                            tint = if (isSelected) M3Primary else M3Error
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.title,
                                        tint = if (isSelected) M3Primary else M3Outline
                                    )
                                }
                            },
                            label = {
                                Text(
                                    text = tab.title,
                                    fontSize = 9.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontFamily = AppFont,
                                    color = if (isSelected) M3Primary else M3OnSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = M3PrimaryContainer,
                                selectedIconColor = M3OnPrimaryContainer,
                                unselectedIconColor = M3Outline,
                                selectedTextColor = M3Primary,
                                unselectedTextColor = M3OnSurfaceVariant
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(M3Surface)
                    .padding(innerPadding)
            ) {
                when (selectedTab) {
                    MainAppTab.COCKPIT -> OverviewScreen(
                        viewModel = viewModel,
                        onNavigateConnect = {
                            HapticHelper.performClick(view)
                            showConnectScreen = true
                        }
                    )
                    MainAppTab.BATTERY -> BatteryScreen(
                        viewModel = viewModel,
                        onNavigateConnect = {
                            HapticHelper.performClick(view)
                            showConnectScreen = true
                        }
                    )
                    MainAppTab.MOTOR -> PowertrainScreen(
                        viewModel = viewModel,
                        onNavigateConnect = {
                            HapticHelper.performClick(view)
                            showConnectScreen = true
                        }
                    )
                    MainAppTab.FAULTS -> WarningsScreen(
                        viewModel = viewModel,
                        onNavigateConnect = {
                            HapticHelper.performClick(view)
                            showConnectScreen = true
                        }
                    )
                    MainAppTab.CALIBRATE -> CalibrationScreen(
                        viewModel = viewModel,
                        onNavigateConnect = {
                            HapticHelper.performClick(view)
                            showConnectScreen = true
                        }
                    )
                    MainAppTab.CAN_LOGS -> RawCanScreen(
                        viewModel = viewModel,
                        onNavigateConnect = {
                            HapticHelper.performClick(view)
                            showConnectScreen = true
                        }
                    )
                }
            }
        }
    }

    // Pop-up dialog to share and analyse CAN logs upon recording completion (just like v1)
    val savedLogItem = lastSavedLog
    if (savedLogItem != null) {
        SavedLogDialog(
            savedLog = savedLogItem,
            onDismiss = {
                HapticHelper.performClick(view)
                viewModel.dismissSavedDialog()
            },
            onAnalyse = {
                HapticHelper.performClick(view)
                viewModel.dismissSavedDialog()
                selectedTab = MainAppTab.CAN_LOGS
                showConnectScreen = false
                if (savedLogItem.uri != null) {
                    viewModel.analyzeLogFile(context, savedLogItem.uri)
                }
            },
            onShare = {
                HapticHelper.performClick(view)
                if (savedLogItem.uri != null) {
                    try {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/gzip"
                            putExtra(Intent.EXTRA_STREAM, savedLogItem.uri)
                            putExtra(Intent.EXTRA_SUBJECT, "Ola S1 CAN Log - ${savedLogItem.fileName}")
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Ola S1 CAN Diagnostic Log: ${savedLogItem.fileName} (${savedLogItem.fileSizeFormatted}, ${savedLogItem.totalFrames} frames)"
                            )
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share CAN Ride Log"))
                    } catch (e: Exception) {
                        Toast.makeText(context, "Share error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }
}
