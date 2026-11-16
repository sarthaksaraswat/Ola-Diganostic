package com.sarthak.olacan.ui.screens

import com.sarthak.olacan.ui.theme.AppFont
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarthak.olacan.network.DiscoveredScooter
import com.sarthak.olacan.network.NetworkScanner
import com.sarthak.olacan.ui.theme.M3Error
import com.sarthak.olacan.ui.theme.M3OnPrimaryContainer
import com.sarthak.olacan.ui.theme.M3OnSurface
import com.sarthak.olacan.ui.theme.M3OnSurfaceVariant
import com.sarthak.olacan.ui.theme.M3OnWarningContainer
import com.sarthak.olacan.ui.theme.M3Outline
import com.sarthak.olacan.ui.theme.M3OutlineVariant
import com.sarthak.olacan.ui.theme.M3Primary
import com.sarthak.olacan.ui.theme.M3PrimaryContainer
import com.sarthak.olacan.ui.theme.M3Success
import com.sarthak.olacan.ui.theme.M3SuccessContainer
import com.sarthak.olacan.ui.theme.M3Surface
import com.sarthak.olacan.ui.theme.M3SurfaceContainer
import com.sarthak.olacan.ui.theme.M3SurfaceContainerHigh
import com.sarthak.olacan.ui.theme.M3SurfaceContainerLow
import com.sarthak.olacan.ui.theme.M3Warning
import com.sarthak.olacan.ui.theme.M3WarningContainer
import com.sarthak.olacan.util.HapticHelper
import com.sarthak.olacan.viewmodel.TelemetryViewModel
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConnectScreen(
    viewModel: TelemetryViewModel,
    onConnectedNavigate: () -> Unit,
    onClose: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val view = LocalView.current
    val hostInput by viewModel.targetHostInput.collectAsState()
    val stats by viewModel.streamStats.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val discoveredList by viewModel.discoveredScooters.collectAsState()
    val hapticsOn by HapticHelper.hapticsEnabled.collectAsState()

    val autoReconnect by viewModel.autoReconnectEnabled.collectAsState()
    val bgTimeoutMins by viewModel.backgroundTimeoutMinutes.collectAsState()

    // Dynamic gateway & local IP detection without hardcoding
    val detectedGateway = remember(context) { NetworkScanner.getWifiGatewayIp(context) }
    val initialIp = remember(hostInput, detectedGateway) {
        if (hostInput.isNotBlank()) hostInput else if (!detectedGateway.isNullOrBlank() && detectedGateway != "0.0.0.0") detectedGateway else ""
    }

    // Mode: "auto" vs "manual"
    var connectionMode by remember { mutableStateOf("auto") }
    var selectedScooterIp by remember { mutableStateOf("") }
    var manualIpText by remember { mutableStateOf(initialIp) }
    var showAdvancedSettings by remember { mutableStateOf(false) }
    var renamingScooter by remember { mutableStateOf<DiscoveredScooter?>(null) }
    var renameInputText by remember { mutableStateOf("") }

    // PRESERVE SAVED VEHICLE FOR INSTANT RECONNECT WITHOUT RE-SCANNING
    val availableScooters: List<DiscoveredScooter> = if (discoveredList.isNotEmpty()) {
        discoveredList
    } else if (initialIp.isNotBlank()) {
        listOf(
            DiscoveredScooter(
                ip = initialIp,
                ipv4 = if (!initialIp.contains(":")) initialIp else "",
                ipv6 = if (initialIp.contains(":")) initialIp else "",
                displayName = "Saved Vehicle ($initialIp)",
                source = "Saved Endpoint"
            )
        )
    } else {
        emptyList()
    }

    // When discovered list updates, select first one automatically if nothing selected
    LaunchedEffect(availableScooters) {
        if (availableScooters.isNotEmpty()) {
            if (selectedScooterIp.isBlank() || availableScooters.none { it.ip == selectedScooterIp }) {
                selectedScooterIp = availableScooters.first().ip
            }
        }
    }

    // Navigate to Cockpit when a fresh connection is made or vehicle is switched
    var wasConnectedOnEntry by remember { mutableStateOf(stats.isConnected) }
    var lastKnownHost by remember { mutableStateOf(stats.connectedHost) }
    LaunchedEffect(stats.isConnected, stats.connectedHost) {
        val hostSwitched = stats.isConnected && stats.connectedHost.isNotBlank() && lastKnownHost.isNotBlank() && stats.connectedHost != lastKnownHost
        val freshConnect = !wasConnectedOnEntry && stats.isConnected
        if (freshConnect || hostSwitched) {
            onConnectedNavigate()
        }
        wasConnectedOnEntry = stats.isConnected
        lastKnownHost = stats.connectedHost
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(M3Surface)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // -------------------------------------------------------------
        // MINIMALIST TOP BAR
        // -------------------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
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
                        text = "Vehicle Diagnostics",
                        color = M3OnSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AppFont
                    )
                    Text(
                        text = if (stats.isConnected) "Connected to ${stats.connectedHost}" else "Wireless CAN Diagnostic Link",
                        color = if (stats.isConnected) M3Success else M3Outline,
                        fontSize = 11.sp,
                        fontFamily = AppFont
                    )
                }
            }

            // Back button if opened while connected or if onClose provided
            if (onClose != null || stats.isConnected) {
                Surface(
                    color = M3SurfaceContainerHigh,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.clickable {
                        HapticHelper.performClick(view)
                        if (onClose != null) onClose() else onConnectedNavigate()
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = M3Primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Back",
                            color = M3Primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = AppFont
                        )
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // CURRENTLY CONNECTED CARD (Shown when user wants to switch/disconnect)
        // -------------------------------------------------------------
        if (stats.isConnected) {
            Surface(
                color = M3SurfaceContainerHigh,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, M3Success.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(M3Success, CircleShape)
                        )
                        Column {
                            Text(
                                text = "Currently Connected",
                                color = M3Success,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                fontFamily = AppFont
                            )
                            Text(
                                text = stats.getEffectiveVehicleName(),
                                color = M3OnSurface,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = AppFont
                            )
                            Text(
                                text = "${stats.connectedHost} • ${stats.latencyMs} ms • ${String.format(Locale.US, "%.0f", stats.framesPerSecond)} FPS",
                                color = M3Outline,
                                fontSize = 10.5.sp,
                                fontFamily = AppFont
                            )
                        }
                    }

                    // Disconnect Button
                    OutlinedButton(
                        onClick = {
                            HapticHelper.performClick(view)
                            viewModel.disconnect()
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, M3Error.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = M3Error),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Disconnect", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // PRIMARY ACTION CARD: SCOOTER SELECTION & CONNECTION (TOP PRIORITY)
        // -------------------------------------------------------------
        Surface(
            color = M3SurfaceContainer,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Segmented Button: Auto-Detected vs Manual IP
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "CONNECT TO VEHICLE",
                        color = M3OnSurface,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        fontFamily = AppFont,
                        modifier = Modifier.weight(1f).padding(end = 6.dp),
                        maxLines = 1
                    )

                    // Segmented Button
                    Surface(
                        color = M3Surface,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // Auto-Detected Button
                            val isAuto = connectionMode == "auto"
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (isAuto) M3PrimaryContainer else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        HapticHelper.performClick(view)
                                        connectionMode = "auto"
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(if (availableScooters.isNotEmpty()) M3Success else M3Outline, CircleShape)
                                    )
                                    Text(
                                        text = "Auto (${availableScooters.size})",
                                        color = if (isAuto) M3OnPrimaryContainer else M3Outline,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = AppFont
                                    )
                                }
                            }

                            // Manual IP Button
                            val isManual = connectionMode == "manual"
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (isManual) M3PrimaryContainer else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        HapticHelper.performClick(view)
                                        connectionMode = "manual"
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Wifi,
                                        contentDescription = null,
                                        tint = if (isManual) M3OnPrimaryContainer else M3Outline,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "Manual IP",
                                        color = if (isManual) M3OnPrimaryContainer else M3Outline,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = AppFont
                                    )
                                }
                            }
                        }
                    }
                }

                // VIEW A: AUTO-DETECTED VEHICLES LIST
                if (connectionMode == "auto") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "SELECT YOUR SCOOTER:",
                                color = M3Outline,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp,
                                fontFamily = AppFont
                            )

                            // Rescan Subnet Button
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.clickable {
                                    HapticHelper.performClick(view)
                                    viewModel.startAutoScan(context)
                                }
                            ) {
                                if (isScanning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        color = M3Primary,
                                        strokeWidth = 1.5.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Rescan Subnet",
                                        tint = M3Primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                Text(
                                    text = if (isScanning) "Scanning..." else "Rescan Subnet",
                                    color = M3Primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = AppFont
                                )
                            }
                        }

                        // Case 1: Scooters Found via Real Auto-Scan
                        if (availableScooters.isNotEmpty()) {
                            availableScooters.forEach { scooter ->
                                val isSelected = (selectedScooterIp == scooter.ip)
                                val isCurrentVehicle = stats.isConnected && (stats.connectedHost == scooter.ip || stats.connectedHost.contains(scooter.ip))

                                Surface(
                                    color = if (isSelected) M3SurfaceContainerHigh else M3Surface,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) M3Primary else if (isCurrentVehicle) M3Success.copy(alpha = 0.5f) else M3OutlineVariant.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            HapticHelper.performClick(view)
                                            selectedScooterIp = scooter.ip
                                            manualIpText = scooter.ip
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            // Radio Selector
                                            Box(
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .background(
                                                        if (isSelected) M3PrimaryContainer else Color.Transparent,
                                                        CircleShape
                                                    )
                                                    .border(
                                                        width = 1.5.dp,
                                                        color = if (isSelected) M3Primary else if (isCurrentVehicle) M3Success else M3Outline,
                                                        shape = CircleShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isSelected) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .background(M3Primary, CircleShape)
                                                    )
                                                }
                                            }

                                            Column {
                                                val displayName = if (scooter.displayName.isNotBlank()) scooter.displayName
                                                                  else com.sarthak.olacan.util.VehicleNameManager.resolveVehicleName(context, scooter.ip, scooter.ipv4.ifBlank { null }, scooter.mac.ifBlank { null })
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = displayName,
                                                        color = M3OnSurface,
                                                        fontSize = 12.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = AppFont,
                                                        maxLines = 1,
                                                        modifier = Modifier.weight(1f, fill = false)
                                                    )
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = "Rename Vehicle",
                                                        tint = M3Outline,
                                                        modifier = Modifier
                                                            .size(13.dp)
                                                            .clickable {
                                                                renamingScooter = scooter
                                                                renameInputText = com.sarthak.olacan.util.VehicleNameManager.getCustomName(
                                                                    context,
                                                                    scooter.mac.ifBlank { scooter.ipv4.ifBlank { scooter.ip } }
                                                                ) ?: ""
                                                            }
                                                    )
                                                }
                                                val details = buildString {
                                                    if (scooter.ipv4.isNotBlank()) append(scooter.ipv4) else append(scooter.ip.substringBefore("%"))
                                                    if (scooter.mac.isNotBlank()) append(" • ${scooter.mac.uppercase()}")
                                                    if (scooter.latencyMs > 0) append(" • ${scooter.latencyMs} ms")
                                                }
                                                Text(
                                                    text = details,
                                                    color = M3Outline,
                                                    fontSize = 10.sp,
                                                    fontFamily = AppFont,
                                                    maxLines = 1
                                                )
                                            }
                                        }

                                        // Status Badge / Quick Switch Action Pill
                                        if (isCurrentVehicle) {
                                            Box(
                                                modifier = Modifier
                                                    .background(M3SuccessContainer, RoundedCornerShape(4.dp))
                                                    .border(1.dp, M3Success.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "CONNECTED",
                                                    color = M3Success,
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = AppFont,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        } else if (stats.isConnected) {
                                            Surface(
                                                color = M3PrimaryContainer,
                                                shape = RoundedCornerShape(6.dp),
                                                border = BorderStroke(1.dp, M3Primary.copy(alpha = 0.5f)),
                                                modifier = Modifier.clickable {
                                                    HapticHelper.performClick(view)
                                                    selectedScooterIp = scooter.ip
                                                    manualIpText = scooter.ip
                                                    viewModel.selectScooterToConnect(scooter)
                                                }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                                                        contentDescription = null,
                                                        tint = M3OnPrimaryContainer,
                                                        modifier = Modifier.size(11.dp)
                                                    )
                                                    Text(
                                                        text = "SWITCH",
                                                        color = M3OnPrimaryContainer,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = AppFont,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                }
                                            }
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .background(M3SuccessContainer, RoundedCornerShape(4.dp))
                                                    .border(1.dp, M3Success.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "PORT 50005 OPEN",
                                                    color = M3Success,
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = AppFont,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Case 2: No Scooters Discovered Yet (Real Zero-State)
                            Surface(
                                color = M3Surface,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (isScanning) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = M3Primary,
                                            strokeWidth = 2.5.dp
                                        )
                                        Text(
                                            text = "Scanning local subnet for vehicles...",
                                            color = M3OnSurface,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = AppFont
                                        )
                                        Text(
                                            text = "Pinging IPv6 link-local and testing diagnostic port 50005. Keep scooter screen awake.",
                                            color = M3Outline,
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp,
                                            fontFamily = AppFont
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = null,
                                            tint = M3Outline,
                                            modifier = Modifier.size(26.dp)
                                        )
                                        Text(
                                            text = "No Vehicles Detected on Subnet",
                                            color = M3OnSurface,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = AppFont
                                        )
                                        Text(
                                            text = "1. Connect scooter to phone's Wi-Fi hotspot.\n2. Keep scooter touchscreen awake.\n3. Tap 'Rescan Subnet' below, or switch to 'Manual IP' to connect directly.",
                                            color = M3Outline,
                                            fontSize = 11.sp,
                                            lineHeight = 16.sp,
                                            fontFamily = AppFont
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        OutlinedButton(
                                            onClick = {
                                                HapticHelper.performClick(view)
                                                viewModel.startAutoScan(context)
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, M3Primary.copy(alpha = 0.5f)),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = M3Primary)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Text("Rescan Subnet Now", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // VIEW B: MANUAL IP FALLBACK
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(M3Surface, RoundedCornerShape(12.dp))
                            .border(1.dp, M3OutlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Scooter IP Address",
                            color = M3OnSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = AppFont
                        )

                        OutlinedTextField(
                            value = manualIpText,
                            onValueChange = { manualIpText = it },
                            placeholder = { Text("e.g. 192.168.43.1 or 192.168.1.x", fontSize = 12.sp, color = M3Outline) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = M3SurfaceContainerHigh,
                                unfocusedContainerColor = M3SurfaceContainerHigh,
                                focusedBorderColor = M3Primary,
                                unfocusedBorderColor = M3OutlineVariant,
                                focusedTextColor = M3OnSurface,
                                unfocusedTextColor = M3OnSurface
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Text(
                            text = "Note: Scooter IP is assigned by your phone hotspot or Wi-Fi router. Enter the IP shown under Scooter Settings → Wi-Fi.",
                            color = M3Outline,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            fontFamily = AppFont
                        )
                    }
                }

                // Primary Launch Button
                val targetIpToConnect = if (connectionMode == "manual") {
                    manualIpText.trim()
                } else {
                    selectedScooterIp.ifBlank { availableScooters.firstOrNull()?.ip ?: "" }
                }

                val hasTarget = targetIpToConnect.isNotBlank()
                val selectedScooter = availableScooters.firstOrNull { it.ip == targetIpToConnect }

                val isDifferentVehicle = stats.isConnected && targetIpToConnect != stats.connectedHost && stats.connectedHost.isNotBlank()
                val buttonLabel = when {
                    connectionMode == "auto" && availableScooters.isEmpty() -> {
                        if (isScanning) "Scanning Subnet for Vehicles..." else "Scan for Vehicles"
                    }
                    connectionMode == "auto" -> {
                        val name = selectedScooter?.displayName ?: targetIpToConnect
                        if (isDifferentVehicle) "Switch to $name" else "Connect to $name"
                    }
                    else -> {
                        if (hasTarget) {
                            if (isDifferentVehicle) "Switch to $targetIpToConnect" else "Connect to $targetIpToConnect"
                        } else "Enter IP Address to Connect"
                    }
                }

                Button(
                    onClick = {
                        HapticHelper.performClick(view)
                        if (connectionMode == "auto" && availableScooters.isEmpty()) {
                            viewModel.startAutoScan(context)
                        } else if (hasTarget) {
                            if (selectedScooter != null) {
                                viewModel.selectScooterToConnect(selectedScooter)
                            } else {
                                viewModel.connect(targetIpToConnect)
                            }
                        }
                    },
                    enabled = (connectionMode == "auto" && availableScooters.isEmpty()) || hasTarget,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = M3PrimaryContainer,
                        contentColor = M3OnPrimaryContainer,
                        disabledContainerColor = M3SurfaceContainerHigh,
                        disabledContentColor = M3Outline
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (connectionMode == "auto" && availableScooters.isEmpty() && isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = M3OnPrimaryContainer,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (hasTarget) Icons.AutoMirrored.Filled.Launch else Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = buttonLabel,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = AppFont,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // Explore App (Simulation / Demo Mode) Button
                OutlinedButton(
                    onClick = {
                        HapticHelper.performClick(view)
                        viewModel.startDemoMode()
                        onConnectedNavigate()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, M3Primary.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = M3Primary
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = M3Primary
                        )
                        Text(
                            text = "Explore App (Simulation & Demo Mode)",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = AppFont
                        )
                    }
                }

                // Compact Connection Options Expandable
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAdvancedSettings = !showAdvancedSettings }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Connection Options & Background Persistence",
                        color = M3Outline,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = AppFont
                    )
                    Text(
                        text = if (showAdvancedSettings) "Hide" else "Configure",
                        color = M3Primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AppFont
                    )
                }

                if (showAdvancedSettings) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(M3Surface, RoundedCornerShape(10.dp))
                            .border(1.dp, M3OutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Auto Reconnect", color = M3OnSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text("Automatically reconnect when vehicle restarts", color = M3Outline, fontSize = 10.sp)
                            }
                            Switch(
                                checked = autoReconnect,
                                onCheckedChange = { viewModel.setAutoReconnect(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = M3OnPrimaryContainer,
                                    checkedTrackColor = M3PrimaryContainer
                                )
                            )
                        }

                        // Background Run Chips
                        Column {
                            Text("Background Duration", color = M3OnSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(5 to "5 Mins", 10 to "10 Mins", 15 to "15 Mins", 0 to "Continuous").forEach { (mins, label) ->
                                    val isSel = bgTimeoutMins == mins
                                    FilterChip(
                                        selected = isSel,
                                        onClick = { viewModel.setBackgroundTimeout(mins) },
                                        label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = M3PrimaryContainer,
                                            selectedLabelColor = M3OnPrimaryContainer,
                                            containerColor = M3SurfaceContainerHigh,
                                            labelColor = M3Outline
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // QUICK SETUP & TROUBLESHOOTING CARD (BELOW PRIMARY ACTION)
        // -------------------------------------------------------------
        Surface(
            color = M3SurfaceContainer,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = M3Primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "QUICK SETUP & TROUBLESHOOTING",
                        color = M3OnSurface,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        fontFamily = AppFont
                    )
                }

                Column(
                    modifier = Modifier.padding(start = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "1. Turn on your Android phone's Personal Hotspot (or ensure phone and scooter are connected to the same workshop Wi-Fi).",
                        color = M3Outline,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        fontFamily = AppFont
                    )
                    Text(
                        text = "2. On the scooter touchscreen, go to Settings → Wi-Fi and connect it to your phone's hotspot.",
                        color = M3Outline,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        fontFamily = AppFont
                    )
                    Text(
                        text = "3. Keep the scooter display awake. Tap Rescan or enter the scooter's IP in Manual IP if not auto-detected.",
                        color = M3Outline,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        fontFamily = AppFont
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // AUTHOR & CREATOR CARD (LOW-KEY, TECHNICAL M3 THEME)
        // -------------------------------------------------------------
        Surface(
            color = M3SurfaceContainer,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(M3SurfaceContainerHigh, CircleShape)
                                .border(1.dp, M3OutlineVariant.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "SS",
                                color = M3OnPrimaryContainer,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = AppFont
                            )
                        }

                        Column {
                            Text(
                                text = "APPLICATION ARCHITECT & ENGINEERING",
                                color = M3Outline,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp,
                                fontFamily = AppFont
                            )
                            Text(
                                text = "Sarthak Saraswat",
                                color = M3OnSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = AppFont
                            )
                        }
                    }
                }

                // Two Low-Key Technical Action Buttons that blend with dark M3 theme
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Buy Me a Coffee button
                    Surface(
                        color = M3SurfaceContainerHigh,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                HapticHelper.performClick(view)
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://buymeacoffee.com/sarthaksaraswat"))
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Coffee,
                                contentDescription = "Buy Me a Coffee",
                                tint = M3OnWarningContainer,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Buy Me a Coffee",
                                color = M3OnSurfaceVariant,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = AppFont
                            )
                        }
                    }

                    // LinkedIn button
                    Surface(
                        color = M3SurfaceContainerHigh,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                HapticHelper.performClick(view)
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.linkedin.com/in/sarthaksaraswat/"))
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "LinkedIn",
                                tint = M3Primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "LinkedIn",
                                color = M3OnSurfaceVariant,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = AppFont
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // -------------------------------------------------------------
    // VEHICLE NICKNAME / RENAME DIALOG
    // -------------------------------------------------------------
    val scooterToRename = renamingScooter
    if (scooterToRename != null) {
        val key = scooterToRename.mac.ifBlank { scooterToRename.ipv4.ifBlank { scooterToRename.ip } }
        AlertDialog(
            onDismissRequest = { renamingScooter = null },
            title = {
                Text("Rename Vehicle", color = M3OnSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Set a custom friendly nickname for this scooter (saved permanently on your phone):",
                        color = M3Outline,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = renameInputText,
                        onValueChange = { renameInputText = it },
                        singleLine = true,
                        placeholder = { Text("e.g. My Ola S1 Pro", color = M3Outline) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Hardware Target: ${scooterToRename.ip.substringBefore("%")} ${if (scooterToRename.mac.isNotBlank()) "• ${scooterToRename.mac}" else ""}",
                        color = M3Outline,
                        fontSize = 10.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        com.sarthak.olacan.util.VehicleNameManager.setCustomName(context, key, renameInputText)
                        if (scooterToRename.ip.isNotBlank()) {
                            com.sarthak.olacan.util.VehicleNameManager.setCustomName(context, scooterToRename.ip, renameInputText)
                        }
                        if (scooterToRename.ipv4.isNotBlank()) {
                            com.sarthak.olacan.util.VehicleNameManager.setCustomName(context, scooterToRename.ipv4, renameInputText)
                        }
                        renamingScooter = null
                        viewModel.startAutoScan(context)
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        renamingScooter = null
                    }
                ) {
                    Text("Cancel", color = M3Outline)
                }
            }
        )
    }
}
