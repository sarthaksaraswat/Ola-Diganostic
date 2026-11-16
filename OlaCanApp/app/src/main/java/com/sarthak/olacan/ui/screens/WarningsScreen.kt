package com.sarthak.olacan.ui.screens

import com.sarthak.olacan.ui.theme.AppFont
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarthak.olacan.analyzer.VehicleSelfTestManager
import com.sarthak.olacan.model.AlertSeverity
import com.sarthak.olacan.model.DtcFault
import com.sarthak.olacan.ui.components.ConnectionBanner
import com.sarthak.olacan.ui.components.VehicleDiagnosticTestDialog
import com.sarthak.olacan.ui.theme.M3Error
import com.sarthak.olacan.ui.theme.M3ErrorContainer
import com.sarthak.olacan.ui.theme.M3OnErrorContainer
import com.sarthak.olacan.ui.theme.M3OnPrimaryContainer
import com.sarthak.olacan.ui.theme.M3OnSurface
import com.sarthak.olacan.ui.theme.M3OnSurfaceVariant
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
fun WarningsScreen(
    viewModel: TelemetryViewModel,
    onNavigateConnect: (() -> Unit)? = null
) {
    val view = LocalView.current
    val stats by viewModel.streamStats.collectAsState()
    val activeAlerts by viewModel.activeAlerts.collectAsState()
    val recordedAlerts by viewModel.recordedAlerts.collectAsState()
    val dtcLibrary by viewModel.dtcLibrary.collectAsState()
    val battery by viewModel.batteryState.collectAsState()
    val powertrain by viewModel.powertrainState.collectAsState()
    val thermal by viewModel.thermalState.collectAsState()
    val vehicle by viewModel.vehicleStatus.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf("ALL") }
    var showSelfTestDialog by remember { mutableStateOf(false) }

    if (showSelfTestDialog) {
        VehicleDiagnosticTestDialog(
            onDismiss = { showSelfTestDialog = false }
        )
    }

    val tags = listOf(
        "ALL" to "ALL (194)",
        "BMS" to "BMS / BATTERY",
        "MCU" to "MCU / MOTOR",
        "VCU" to "VCU / CONTROL",
        "THROTTLE" to "THROTTLE",
        "CHARGER" to "CHARGER",
        "CRITICAL" to "CRITICAL"
    )

    val filteredDtcs = remember(dtcLibrary, searchQuery, selectedTag) {
        dtcLibrary.filter { dtc ->
            val matchesTag = when (selectedTag) {
                "ALL" -> true
                "BMS" -> dtc.component.contains("BMS", ignoreCase = true) || dtc.id.contains("bms", ignoreCase = true)
                "MCU" -> dtc.component.contains("MCU", ignoreCase = true) || dtc.id.contains("mcu", ignoreCase = true) || dtc.component.contains("MOTOR", ignoreCase = true)
                "VCU" -> dtc.component.contains("VCU", ignoreCase = true) || dtc.id.contains("vcu", ignoreCase = true)
                "THROTTLE" -> dtc.id.contains("throttle", ignoreCase = true) || dtc.title.contains("throttle", ignoreCase = true)
                "CHARGER" -> dtc.id.contains("charge", ignoreCase = true) || dtc.component.contains("CHARGE", ignoreCase = true)
                "CRITICAL" -> dtc.severity == AlertSeverity.CRITICAL
                else -> true
            }

            val matchesSearch = if (searchQuery.isBlank()) true else {
                dtc.id.contains(searchQuery, ignoreCase = true) ||
                        dtc.title.contains(searchQuery, ignoreCase = true) ||
                        dtc.description.contains(searchQuery, ignoreCase = true) ||
                        dtc.component.contains(searchQuery, ignoreCase = true)
            }

            matchesTag && matchesSearch
        }
    }

    val criticalFaults = remember(activeAlerts) {
        activeAlerts.filter { it.severity == AlertSeverity.CRITICAL }
    }
    val operationalNotices = remember(activeAlerts) {
        activeAlerts.filter { it.severity != AlertSeverity.CRITICAL }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(M3Surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // -------------------------------------------------------------
        // 1. ACTIVE CAN FAULTS SECTION (CRITICAL HARDWARE ERRORS)
        // -------------------------------------------------------------
        item {
            Surface(
                color = M3SurfaceContainer,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, if (criticalFaults.isNotEmpty()) M3Error else M3Success.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f).padding(end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (criticalFaults.isNotEmpty()) Icons.Default.Warning else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (criticalFaults.isNotEmpty()) M3Error else M3Success,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (criticalFaults.isNotEmpty()) "ACTIVE FAULT CODES FOUND (${criticalFaults.size})" else "ACTIVE FAULT CODES (0)",
                                color = if (criticalFaults.isNotEmpty()) M3Error else M3Success,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = AppFont,
                                maxLines = 1
                            )
                        }

                        if (criticalFaults.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .background(M3ErrorContainer, RoundedCornerShape(4.dp))
                                    .border(1.dp, M3Error.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "ACTION REQUIRED",
                                    color = M3Error,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = AppFont,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .background(M3SuccessContainer, RoundedCornerShape(4.dp))
                                    .border(1.dp, M3Success.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "ALL SYSTEMS NOMINAL",
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

                    if (criticalFaults.isNotEmpty()) {
                        criticalFaults.forEach { alert ->
                            Surface(
                                color = M3SurfaceContainerHigh,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, M3Error.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(alert.title, color = M3OnSurface, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                        Text(alert.component, color = M3Error, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text(alert.description, color = M3OnSurfaceVariant, fontSize = 10.5.sp, lineHeight = 14.sp)
                                    Text("CAN ID: ${alert.sourceCanIdHex}", color = M3Outline, fontSize = 9.sp)
                                }
                            }
                        }
                    } else {
                        Surface(
                            color = M3Success.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, M3Success.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(M3Success.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = M3Success,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "All Systems Operating Nominally",
                                        color = M3Success,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = AppFont
                                    )
                                    Text(
                                        text = "Zero active critical fault codes broadcast over CAN bus. BMS, MCU, VCU, and ABS controllers reporting healthy operation.",
                                        color = M3OnSurfaceVariant,
                                        fontSize = 10.5.sp,
                                        lineHeight = 14.sp,
                                        fontFamily = AppFont
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // 1B. OPERATIONAL WARNINGS & ADVISORIES (NORMAL VEHICLE BEHAVIORS)
        // -------------------------------------------------------------
        if (operationalNotices.isNotEmpty()) {
            item {
                Surface(
                    color = M3SurfaceContainer,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, M3Warning.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f).padding(end = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = M3Warning,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "OPERATIONAL WARNINGS & NOTICES (${operationalNotices.size})",
                                    color = M3Warning,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = AppFont,
                                    maxLines = 1
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .background(M3WarningContainer, RoundedCornerShape(4.dp))
                                    .border(1.dp, M3Warning.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "NORMAL ADVISORY",
                                    color = M3Warning,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = AppFont,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        Text(
                            text = "Standard MoveOS operating limits & advisories (e.g., regen pause near 100% SOC, temporary thermal derates). These are expected vehicle behaviors, not hardware faults.",
                            color = M3OnSurfaceVariant,
                            fontSize = 10.5.sp,
                            lineHeight = 14.sp,
                            fontFamily = AppFont
                        )

                        operationalNotices.forEach { alert ->
                            val alertColor = if (alert.severity == AlertSeverity.WARNING) M3Warning else M3Primary
                            Surface(
                                color = M3SurfaceContainerHigh,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, alertColor.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(alert.title, color = M3OnSurface, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = if (alert.severity == AlertSeverity.WARNING) "WARNING" else "ADVISORY",
                                            color = alertColor,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(alert.description, color = M3OnSurfaceVariant, fontSize = 10.5.sp, lineHeight = 14.sp)
                                    Text("CAN Source: ${alert.sourceCanIdHex} • Component: ${alert.component}", color = M3Outline, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // 2. STORED FAULT CODES (CAN HISTORY & VEHICLE EEPROM)
        // -------------------------------------------------------------
        item {
            Surface(
                color = M3SurfaceContainer,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f).padding(end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.History, contentDescription = null, tint = M3Primary, modifier = Modifier.size(15.dp))
                            Text(
                                text = "STORED FAULTS (CAN HISTORY)",
                                color = M3OnSurface,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp,
                                fontFamily = AppFont,
                                maxLines = 1
                            )
                        }

                        if (recordedAlerts.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .background(M3WarningContainer, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${recordedAlerts.size} STORED",
                                    color = M3Warning,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    if (recordedAlerts.isNotEmpty()) {
                        recordedAlerts.take(5).forEach { alert ->
                            Surface(
                                color = M3SurfaceContainerHigh,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                                        Text(alert.title, color = M3OnSurface, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text(alert.description, color = M3Outline, fontSize = 10.sp, maxLines = 1)
                                        Text("Stored from CAN ID: ${alert.sourceCanIdHex}", color = M3Outline, fontSize = 9.sp)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .background(M3SurfaceContainerLow, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text("HISTORIC", color = M3Outline, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Surface(
                            color = M3SurfaceContainerLow,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = M3Success, modifier = Modifier.size(13.dp))
                                Text(
                                    text = "Vehicle fault records are preserved for diagnostic history and cannot be cleared mistakenly.",
                                    color = M3Outline,
                                    fontSize = 10.5.sp,
                                    fontFamily = AppFont
                                )
                            }
                        }
                    } else {
                        Surface(
                            color = M3SurfaceContainerHigh.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "No historical fault codes stored in vehicle CAN buffer or controller memory.",
                                color = M3Outline,
                                fontSize = 11.sp,
                                fontFamily = AppFont,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // 3. VEHICLE DIAGNOSTIC SELF-TEST SUITE ACTION CARD
        // -------------------------------------------------------------
        item {
            Surface(
                color = M3SurfaceContainer,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, M3Primary.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(M3PrimaryContainer, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Build, contentDescription = null, tint = M3OnPrimaryContainer, modifier = Modifier.size(15.dp))
                            }
                            Column {
                                Text(
                                    text = "VEHICLE DIAGNOSTIC HEALTH TEST",
                                    color = M3OnSurface,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.6.sp,
                                    fontFamily = AppFont
                                )
                                Text(
                                    text = "Tests Brakes, Lamps, Audio, e-SIM, Battery, VCU, MCU & DTCs",
                                    color = M3Outline,
                                    fontSize = 10.sp,
                                    fontFamily = AppFont
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            HapticHelper.performClick(view)
                            VehicleSelfTestManager.startSelfTest(
                                battery = battery,
                                powertrain = powertrain,
                                thermal = thermal,
                                vehicle = vehicle,
                                activeAlerts = activeAlerts,
                                isConnected = stats.isConnected
                            )
                            showSelfTestDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = M3PrimaryContainer,
                            contentColor = M3OnPrimaryContainer
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(15.dp))
                            Text("Run Full Vehicle Diagnostic Self-Test", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // DTC LIBRARY HEADER & CONTROLS
        // -------------------------------------------------------------
        item {
            Surface(
                color = M3SurfaceContainer,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Diagnostic Trouble Code Library",
                                    color = M3OnSurface,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = AppFont
                                )
                                Box(
                                    modifier = Modifier
                                        .background(M3SurfaceContainerHigh, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${dtcLibrary.size} CODES",
                                        color = M3Primary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = AppFont
                                    )
                                }
                            }
                            Text(
                                text = "Complete OEM diagnostic trouble code definitions & repair actions",
                                color = M3Outline,
                                fontSize = 11.sp,
                                fontFamily = AppFont
                            )
                        }
                    }

                    // Search input
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search code or description...", fontSize = 11.5.sp, color = M3Outline) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = M3Outline, modifier = Modifier.size(16.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = M3Outline, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
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

                    // Tag-Based Filter Chips
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        tags.forEach { (tagKey, label) ->
                            val isSel = selectedTag == tagKey
                            FilterChip(
                                selected = isSel,
                                onClick = {
                                    HapticHelper.performClick(view)
                                    selectedTag = tagKey
                                },
                                label = { Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = AppFont) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = M3PrimaryContainer,
                                    selectedLabelColor = M3OnPrimaryContainer,
                                    containerColor = M3SurfaceContainerHigh,
                                    labelColor = M3Outline
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSel,
                                    selectedBorderColor = M3Primary,
                                    borderColor = M3OutlineVariant.copy(alpha = 0.4f),
                                    borderWidth = 1.dp
                                )
                            )
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // DTC CODE LIST ITEMS
        // -------------------------------------------------------------
        items(filteredDtcs, key = { it.id }) { dtc ->
            DtcCardItem(dtc = dtc)
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DtcCardItem(dtc: DtcFault) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        color = M3SurfaceContainer,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(M3SurfaceContainerHigh, RoundedCornerShape(6.dp))
                            .border(1.dp, M3OutlineVariant.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = dtc.id,
                            color = M3Primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = AppFont,
                            maxLines = 1
                        )
                    }

                    Text(
                        text = dtc.component,
                        color = M3Outline,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AppFont,
                        maxLines = 1
                    )
                }

                // Severity Badge
                val sevColor = when (dtc.severity) {
                    AlertSeverity.CRITICAL -> M3Error
                    AlertSeverity.WARNING -> M3Warning
                    AlertSeverity.INFO -> M3Success
                }
                Box(
                    modifier = Modifier
                        .background(sevColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = dtc.severity.name,
                        color = sevColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AppFont,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Text(
                text = dtc.title,
                color = M3OnSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = AppFont
            )

            Text(
                text = dtc.description,
                color = M3OnSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                fontFamily = AppFont
            )

            if (expanded && dtc.postFixMessage != null) {
                Surface(
                    color = M3SurfaceContainerHigh,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Text(
                        text = "Repair Remedy: ${dtc.postFixMessage}",
                        color = M3Success,
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp,
                        fontFamily = AppFont,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}
