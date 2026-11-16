package com.sarthak.olacan.ui.screens

import com.sarthak.olacan.ui.theme.AppFont
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.ElectricScooter
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import com.sarthak.olacan.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sarthak.olacan.analyzer.VehicleSelfTestManager
import com.sarthak.olacan.model.DiagnosticTestStatus
import com.sarthak.olacan.service.CanStreamManager
import com.sarthak.olacan.ui.components.ConnectionBanner
import com.sarthak.olacan.ui.components.VehicleDiagnosticTestDialog
import com.sarthak.olacan.ui.theme.M3Error
import com.sarthak.olacan.ui.theme.M3ErrorContainer
import com.sarthak.olacan.ui.theme.M3OnErrorContainer
import com.sarthak.olacan.ui.theme.M3OnPrimaryContainer
import com.sarthak.olacan.ui.theme.M3OnSecondaryContainer
import com.sarthak.olacan.ui.theme.M3OnSurface
import com.sarthak.olacan.ui.theme.M3OnSurfaceVariant
import com.sarthak.olacan.ui.theme.M3OnSuccessContainer
import com.sarthak.olacan.ui.theme.M3OnWarningContainer
import com.sarthak.olacan.ui.theme.M3Outline
import com.sarthak.olacan.ui.theme.M3OutlineVariant
import com.sarthak.olacan.ui.theme.M3Primary
import com.sarthak.olacan.ui.theme.M3PrimaryContainer
import com.sarthak.olacan.ui.theme.M3Secondary
import com.sarthak.olacan.ui.theme.M3SecondaryContainer
import com.sarthak.olacan.ui.theme.M3Success
import com.sarthak.olacan.ui.theme.M3SuccessContainer
import com.sarthak.olacan.ui.theme.M3Surface
import com.sarthak.olacan.ui.theme.M3SurfaceContainer
import com.sarthak.olacan.ui.theme.M3SurfaceContainerLow
import com.sarthak.olacan.ui.theme.M3SurfaceContainerHigh
import com.sarthak.olacan.ui.theme.M3SurfaceContainerHighest
import com.sarthak.olacan.ui.theme.M3Warning
import com.sarthak.olacan.ui.theme.M3WarningContainer
import com.sarthak.olacan.util.HapticHelper
import com.sarthak.olacan.viewmodel.TelemetryViewModel
import java.util.Locale

@Composable
fun OverviewScreen(
    viewModel: TelemetryViewModel,
    onNavigateConnect: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val view = LocalView.current
    val stats by viewModel.streamStats.collectAsState()
    val battery by viewModel.batteryState.collectAsState()
    val thermal by viewModel.thermalState.collectAsState()
    val powertrain by viewModel.powertrainState.collectAsState()
    val vehicle by viewModel.vehicleStatus.collectAsState()
    val activeAlerts by viewModel.activeAlerts.collectAsState()
    val coulombState by viewModel.coulombState.collectAsState()
    val chg by viewModel.chargingSession.collectAsState()

    val isChargingActive = (battery.isCharging || battery.isPluggedIn || battery.isChargerConnected || chg.isActive ||
            (!battery.powerKw.isNaN() && battery.powerKw > 0.05)) &&
            (powertrain.speedKmh.isNaN() || powertrain.speedKmh < 1.0)

    var showSelfTestDialog by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }

    if (showSelfTestDialog) {
        VehicleDiagnosticTestDialog(
            onDismiss = { showSelfTestDialog = false }
        )
    }

    if (showRestartDialog) {
        RestartConfirmationDialog(
            onDismiss = { showRestartDialog = false },
            onFullReboot = {
                showRestartDialog = false
                CanStreamManager.restartScooter(fullReboot = true) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            },
            onHmiRestart = {
                showRestartDialog = false
                CanStreamManager.restartScooter(fullReboot = false) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(M3Surface)
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // -------------------------------------------------------------
        // 0. STANDBY BANNER IF VEHICLE ECUS ARE ASLEEP
        // -------------------------------------------------------------
        if (stats.isConnected && !stats.isAwake && !CanStreamManager.isDemoMode.value) {
            Surface(
                color = M3SurfaceContainerHigh,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, M3Warning.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Standby",
                            tint = M3Warning,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "SCOOTER IN SLEEP STANDBY",
                            color = M3Warning,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = AppFont
                        )
                    }

                    Text(
                        text = "MoveOS is parked and powertrain/BMS CAN telemetry is sleeping. BMS wakes via physical handlebar Power button, shaking/nudge (IMU), or plugging in charger. Tap 'Wake Scooter' to wake the touchscreen panel.",
                        color = M3OnSurfaceVariant,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        fontFamily = AppFont
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                HapticHelper.performClick(view)
                                CanStreamManager.wakeScooter { success, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = M3Warning.copy(alpha = 0.2f),
                                contentColor = M3Warning
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Wake Scooter", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        if (onNavigateConnect != null) {
                            OutlinedButton(
                                onClick = {
                                    HapticHelper.performClick(view)
                                    onNavigateConnect()
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Switch Scooter", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // ALERT BANNER IF 1% VOLTAGE SAG DERATE OCCURS (CRISP NON-ALARMIST)
        // -------------------------------------------------------------
        if (battery.is1PercentSocClampActive) {
            Surface(
                color = M3SurfaceContainerHigh,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, M3Warning),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Alert",
                            tint = M3Warning,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "LIKELY VOLTAGE SAG (CELL SAG DERATE ACTIVE)",
                            color = M3Warning,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = AppFont
                        )
                    }

                    val estTrueSoc = if (battery.clampedTrueSocEstPercent.isNaN()) "58" else String.format(Locale.US, "%.0f", battery.clampedTrueSocEstPercent)
                    val packVolt = if (battery.busVoltageV.isNaN()) "47.0" else String.format(Locale.US, "%.1f", battery.busVoltageV)
                    val minCellDesc = if (!battery.cellMinMv.isNaN()) {
                        val chNum = if (battery.criticalCellFloorNumber > 0) "Cell ${String.format(Locale.US, "%02d", battery.criticalCellFloorNumber)}" else "weak cell"
                        " ($chNum sagged to ${String.format(Locale.US, "%.2f", battery.cellMinMv / 1000.0)}V)"
                    } else ""

                    Text(
                        text = "Dashboard SOC clamped to 1.0% to protect cells from peak voltage sag$minCellDesc. Actual pack voltage is ${packVolt}V (~$estTrueSoc% true capacity). Reading restores when load normalizes.",
                        color = M3OnSurfaceVariant,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        fontFamily = AppFont
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // 2. HERO POWERTRAIN TELEMETRY OR CHARGING CLUSTER
        // -------------------------------------------------------------
        Surface(
            color = M3SurfaceContainer,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, if (isChargingActive) M3Success.copy(alpha = 0.4f) else M3OutlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isChargingActive) {
                // HERO CHARGING DASHBOARD: Replaces speedo with rich charging analytics when plugged in
                ChargingHeroView(
                    chg = chg,
                    battery = battery,
                    thermal = thermal
                )
            } else {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header with Drive Mode badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "POWERTRAIN DYNAMICS",
                        color = M3Outline,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        fontFamily = AppFont,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        maxLines = 1
                    )

                    val driveModeName = powertrain.driveMode.ifBlank { "NORMAL" }.uppercase(Locale.US)
                    Box(
                        modifier = Modifier
                            .background(M3PrimaryContainer, RoundedCornerShape(6.dp))
                            .border(1.dp, M3Primary.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "$driveModeName MODE",
                            color = M3OnPrimaryContainer,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = AppFont,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // Huge Hero Speedometer Display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    val speedVal = if (powertrain.speedKmh.isNaN() || powertrain.speedKmh < 0) 0.0 else powertrain.speedKmh
                    Text(
                        text = String.format(Locale.US, "%.1f", speedVal),
                        color = M3OnSurface,
                        fontSize = 58.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = AppFont
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "km/h",
                        color = M3Primary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AppFont,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }

                // Motor RPM & Power Draw / Horsepower Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Motor RPM
                    Surface(
                        color = M3SurfaceContainerHigh,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("MOTOR RPM", color = M3Outline, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = AppFont)
                            val rpmVal = if (powertrain.motorRpm.isNaN()) 0 else Math.round(powertrain.motorRpm).toInt()
                            Text(
                                text = String.format(Locale.US, "%,d RPM", rpmVal),
                                color = M3Primary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = AppFont
                            )
                        }
                    }

                    // Motor Power Draw & HP
                    val isPluggedInCharging = (battery.isCharging || battery.isPluggedIn || battery.isChargerConnected ||
                            (!battery.powerKw.isNaN() && battery.powerKw > 0.05)) &&
                            (powertrain.speedKmh.isNaN() || powertrain.speedKmh < 1.0)
                    val isVehicleMovingOrDriving = (!powertrain.speedKmh.isNaN() && powertrain.speedKmh >= 0.5) ||
                            (!powertrain.throttlePercent.isNaN() && powertrain.throttlePercent > 2.0)
                    val isRegenBraking = !isPluggedInCharging && (!battery.powerKw.isNaN() && battery.powerKw > 0.05) &&
                            (!powertrain.speedKmh.isNaN() && powertrain.speedKmh >= 1.0)
                    val isDrivingDischarge = !isPluggedInCharging && isVehicleMovingOrDriving &&
                            (!battery.powerKw.isNaN() && battery.powerKw < -0.05)

                    val (powerTitle, powerValText, powerValColor) = when {
                        isPluggedInCharging -> {
                            val chargeKw = if (battery.powerKw.isNaN()) 0.6 else Math.abs(battery.powerKw)
                            Triple("CHARGING INPUT", "+${String.format(Locale.US, "%.1f", chargeKw)} kW (Charging)", M3Success)
                        }
                        isRegenBraking -> {
                            val regenKw = if (battery.powerKw.isNaN()) 0.0 else Math.abs(battery.powerKw)
                            Triple("REGEN RECOVERY", "+${String.format(Locale.US, "%.1f", regenKw)} kW", M3Success)
                        }
                        isDrivingDischarge -> {
                            val dischargeKw = if (battery.powerKw.isNaN()) 0.0 else Math.abs(battery.powerKw)
                            val hp = dischargeKw * 1.34102
                            Triple("MOTOR POWER DRAW", "${String.format(Locale.US, "%.1f", dischargeKw)} kW (${String.format(Locale.US, "%.1f", hp)} HP)", M3OnSurface)
                        }
                        else -> {
                            Triple("MOTOR POWER DRAW", "0.0 kW (0.0 HP)", M3OnSurface)
                        }
                    }

                    Surface(
                        color = M3SurfaceContainerHigh,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(powerTitle, color = M3Outline, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = AppFont)
                            Text(
                                text = powerValText,
                                color = powerValColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = AppFont
                            )
                        }
                    }
                }

                // Motor & Inverter Thermals with Ranges (v1 style)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val mcuHasTemp = !thermal.mcuTempC.isNaN() && thermal.mcuTempC > 1.0
                    val mcuTemp = if (mcuHasTemp) thermal.mcuTempC else 0.0
                    val mcuTag = if (!mcuHasTemp) "Standby" else if (mcuTemp < 85.0) "Optimal (<85°C)" else "Throttling (>85°C)"
                    val mcuTagColor = if (!mcuHasTemp) M3Outline else if (mcuTemp < 85.0) M3Success else M3Warning

                    val motorHasTemp = !thermal.motorTempC.isNaN() && thermal.motorTempC > 1.0
                    val motorTemp = if (motorHasTemp) thermal.motorTempC else 0.0
                    val (motorTag, motorTagColor) = when {
                        !motorHasTemp -> "Standby" to M3Outline
                        motorTemp < 92.0 -> "Normal (<92°C)" to M3Success
                        motorTemp < 115.0 -> "Derate (95-115°C)" to M3Warning
                        else -> "Cutoff (≥115°C)" to M3Error
                    }

                    // Motor Stator
                    Surface(
                        color = M3SurfaceContainerHigh,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("MOTOR STATOR", color = M3Outline, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (motorHasTemp) "${String.format(Locale.US, "%.1f", motorTemp)} °C" else "-- °C",
                                    color = if (motorHasTemp) M3OnSurface else M3Outline,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Surface(
                                color = motorTagColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = motorTag,
                                    color = motorTagColor,
                                    fontSize = 7.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // MCU Inverter
                    Surface(
                        color = M3SurfaceContainerHigh,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("MCU INVERTER", color = M3Outline, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (mcuHasTemp) "${String.format(Locale.US, "%.1f", mcuTemp)} °C" else "-- °C",
                                    color = if (mcuHasTemp) M3OnSurface else M3Outline,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Surface(
                                color = mcuTagColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = mcuTag,
                                    color = mcuTagColor,
                                    fontSize = 7.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Throttle Grip Input Bar with Reverse Twist (-20% to +100%)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "THROTTLE INPUT",
                            color = M3Outline,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = AppFont,
                            modifier = Modifier.weight(1f).padding(end = 6.dp),
                            maxLines = 1
                        )

                        val thrVal = if (powertrain.throttlePercent.isNaN()) 0.0 else powertrain.throttlePercent
                        val thrText = when {
                            thrVal < -0.5 -> "Reverse Regen (${String.format(Locale.US, "%.0f", thrVal)}%)"
                            thrVal > 0.5 -> "Forward (${String.format(Locale.US, "%.0f", thrVal)}%)"
                            else -> "Neutral (0%)"
                        }
                        Text(
                            text = thrText,
                            color = when {
                                thrVal < -0.5 -> M3Warning
                                thrVal > 0.5 -> M3Primary
                                else -> M3Success
                            },
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = AppFont,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    // Bidirectional Center-Split Throttle Bar
                    val thrVal = if (powertrain.throttlePercent.isNaN()) 0.0 else powertrain.throttlePercent
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(M3SurfaceContainerLow, RoundedCornerShape(4.dp))
                            .padding(horizontal = 1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Reverse / Regen (25% total track)
                        Box(
                            modifier = Modifier
                                .weight(0.25f)
                                .height(6.dp)
                                .background(M3SurfaceContainerHigh, RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp)),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            if (thrVal < 0.0) {
                                val revFraction = (Math.abs(thrVal) / 25.0).coerceIn(0.0, 1.0).toFloat()
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(revFraction)
                                        .height(6.dp)
                                        .background(M3Warning, RoundedCornerShape(3.dp))
                                )
                            }
                        }

                        // Center neutral divider
                        Box(
                            modifier = Modifier
                                .width(1.5.dp)
                                .height(8.dp)
                                .background(M3Outline)
                        )

                        // Right: Forward Drive (75% total track)
                        Box(
                            modifier = Modifier
                                .weight(0.75f)
                                .height(6.dp)
                                .background(M3SurfaceContainerHigh, RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (thrVal > 0.0) {
                                val fwdFraction = (thrVal / 100.0).coerceIn(0.0, 1.0).toFloat()
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fwdFraction)
                                        .height(6.dp)
                                        .background(M3Primary, RoundedCornerShape(3.dp))
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("◀ Reverse (-25%)", color = M3Outline, fontSize = 8.sp, maxLines = 1, softWrap = false)
                        Text("Neutral (0%)", color = M3Outline, fontSize = 8.sp, maxLines = 1, softWrap = false)
                        Text("Forward (+100%) ▶", color = M3Outline, fontSize = 8.sp, maxLines = 1, softWrap = false)
                    }
                }

                // Bus Voltage & Pack Current Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val voltStr = if (battery.busVoltageV.isNaN()) "---" else "${String.format(Locale.US, "%.1f", battery.busVoltageV)} V"
                    val currStr = if (battery.batteryCurrentA.isNaN()) "---" else "${String.format(Locale.US, "%.1f", battery.batteryCurrentA)} A"

                    Column(modifier = Modifier.weight(1f)) {
                        Text("BUS VOLTAGE", color = M3Outline, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = AppFont)
                        Text(voltStr, color = M3OnSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = AppFont)
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text("PACK CURRENT", color = M3Outline, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = AppFont)
                        Text(currStr, color = if (battery.batteryCurrentA > 0.5) M3Success else M3OnSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = AppFont)
                    }
                }
            }
        }
    }

        // -------------------------------------------------------------
        // 3. OVERVIEW CARD: BATTERY STATE & TEMPERATURES
        // -------------------------------------------------------------
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
                Text(
                    text = "BATTERY HEALTH & ENVIRONMENT",
                    color = M3Outline,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    fontFamily = AppFont
                )

                // Estimated Range & Battery SOH (Coulomb Counting)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Estimated Range (Bold)
                    Surface(
                        color = M3SurfaceContainerHigh,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("ESTIMATED RANGE", color = M3Outline, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = AppFont)
                                if (coulombState.isCalibrated) {
                                    Box(
                                        modifier = Modifier
                                            .background(M3Success.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text("COULOMB", color = M3Success, fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            val (rangeText, rangeColor, rangeSubtext) = when {
                                coulombState.isCalibrated && !coulombState.estimatedRangeKm.isNaN() -> {
                                    Triple("${coulombState.estimatedRangeKm.toInt()} km", M3Success, "${String.format(Locale.US, "%.1f", coulombState.consumptionWhPerKm)} Wh/km dynamic")
                                }
                                powertrain.normalRangeKm > 0 -> {
                                    Triple("${powertrain.normalRangeKm} km", M3OnSurface, "Ola Cluster Est (Drive 15km for Coulomb)")
                                }
                                else -> {
                                    Triple("-- km", M3Outline, coulombState.statusLabel)
                                }
                            }
                            Text(
                                text = rangeText,
                                color = rangeColor,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = AppFont
                            )
                            Text(
                                text = rangeSubtext,
                                color = M3Outline,
                                fontSize = 8.5.sp,
                                fontFamily = AppFont,
                                maxLines = 1
                            )
                        }
                    }

                    // Battery Health SOH (Learned via Coulomb Counting)
                    Surface(
                        color = M3SurfaceContainerHigh,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("BATTERY SOH", color = M3Outline, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = AppFont)
                            val (sohText, sohColor, sohSubtext) = when {
                                coulombState.isCalibrated && !coulombState.calibratedSohPercent.isNaN() -> {
                                    Triple("${String.format(Locale.US, "%.1f", coulombState.calibratedSohPercent)}%", M3Success, "${String.format(Locale.US, "%.2f", coulombState.calibratedCapacityKwh)} kWh usable")
                                }
                                coulombState.calibrationProgressPercent > 0.0 -> {
                                    Triple("${coulombState.calibrationProgressPercent.toInt()}%", M3Primary, "${String.format(Locale.US, "%.1f", coulombState.currentRideDistanceKm)}/15 km test ride")
                                }
                                else -> {
                                    Triple("~98%", M3OnSurface, "Drive 15+ km to lock exact SOH")
                                }
                            }
                            Text(
                                text = sohText,
                                color = sohColor,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = AppFont
                            )
                            Text(
                                text = sohSubtext,
                                color = M3Outline,
                                fontSize = 8.5.sp,
                                fontFamily = AppFont,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Pack SOC & Voltage Delta Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Pack SOC
                    Surface(
                        color = M3SurfaceContainerHigh,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("PACK SOC", color = M3Outline, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, fontFamily = AppFont)
                            val socVal = when {
                                !battery.socPercent.isNaN() && battery.socPercent > 0.0 -> battery.socPercent
                                !battery.displaySocPercent.isNaN() && battery.displaySocPercent > 0.0 -> battery.displaySocPercent
                                CanStreamManager.isDemoMode.value -> 76.0
                                else -> Double.NaN
                            }
                            Text(
                                text = if (!socVal.isNaN()) "${String.format(Locale.US, "%.0f", socVal)}%" else "--%",
                                color = M3Primary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = AppFont
                            )
                            val voltVal = battery.busVoltageV
                            Text(
                                text = if (!voltVal.isNaN()) "${String.format(Locale.US, "%.1f", voltVal)} V • Terminal" else "-- V • Terminal",
                                color = M3Outline,
                                fontSize = 8.5.sp,
                                fontFamily = AppFont,
                                maxLines = 1
                            )
                        }
                    }

                    // Voltage Delta with Δ symbol!
                    val deltaVal = battery.cellDeltaMv
                    Surface(
                        color = M3SurfaceContainerHigh,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("VOLTAGE DELTA", color = M3Outline, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, fontFamily = AppFont)
                            Text(
                                text = if (!deltaVal.isNaN()) "Δ ${String.format(Locale.US, "%.0f", deltaVal)} mV" else "-- mV",
                                color = if (!deltaVal.isNaN() && deltaVal < 60) M3Success else if (!deltaVal.isNaN()) M3Warning else M3Outline,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = AppFont
                            )
                            Text(
                                text = when {
                                    deltaVal.isNaN() -> "Awaiting CAN frames"
                                    deltaVal < 60 -> "Cell balance optimal"
                                    else -> "Cell spread elevated"
                                },
                                color = M3Outline,
                                fontSize = 8.5.sp,
                                fontFamily = AppFont,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Battery Thermals Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Ambient Temp
                    val ambVal = thermal.ambientTempC
                    Column(modifier = Modifier.weight(1f)) {
                        Text("AMBIENT TEMP", color = M3Outline, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, fontFamily = AppFont, maxLines = 1)
                        Text(
                            text = if (!ambVal.isNaN()) "${String.format(Locale.US, "%.1f", ambVal)} °C" else "-- °C",
                            color = M3OnSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = AppFont,
                            maxLines = 1
                        )
                    }

                    // Battery Core Temp
                    val batTempVal = thermal.packEffectiveTempC
                    Column(modifier = Modifier.weight(1f)) {
                        Text("BATTERY CORE TEMP", color = M3Outline, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, fontFamily = AppFont, maxLines = 1)
                        Text(
                            text = if (!batTempVal.isNaN()) "${String.format(Locale.US, "%.1f", batTempVal)} °C" else "-- °C",
                            color = M3OnSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = AppFont,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // VEHICLE DIAGNOSTIC & HEALTH SELF-TEST (PLACED AT BOTTOM)
        // -------------------------------------------------------------
        Surface(
            color = M3SurfaceContainer,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, M3Primary.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(M3PrimaryContainer, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = null,
                            tint = M3OnPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "VEHICLE DIAGNOSTIC SELF-TEST",
                            color = M3OnSurface,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp,
                            fontFamily = AppFont
                        )
                        Text(
                            text = "Comprehensive 12-point hardware test: Brakes, Lamps, Audio, e-SIM, Battery & ECUs",
                            color = M3Outline,
                            fontSize = 10.5.sp,
                            fontFamily = AppFont
                        )
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
                        .height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = M3PrimaryContainer,
                        contentColor = M3OnPrimaryContainer
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Run Full Vehicle Diagnostic & Health Test",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = AppFont,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // VEHICLE CONTROLS (Icon & Name only)
        // -------------------------------------------------------------
        Surface(
            color = M3SurfaceContainer,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "VEHICLE CONTROLS",
                    color = M3Outline,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    fontFamily = AppFont
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Lock / Unlock
                    val isLocked = vehicle.isLocked
                    Surface(
                        color = M3SurfaceContainerHigh,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (isLocked) M3Success.copy(alpha = 0.4f) else M3OutlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                HapticHelper.performClick(view)
                                CanStreamManager.toggleLock { success, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 14.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (isLocked) M3Success else M3Primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = if (isLocked) "Unlock" else "Lock",
                                color = M3OnSurface,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = AppFont,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }

                    // 2. Open Seat (Boot Latch)
                    Surface(
                        color = M3SurfaceContainerHigh,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                HapticHelper.performClick(view)
                                CanStreamManager.releaseBoot { success, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 14.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_scooter_seat),
                                contentDescription = "Open Seat",
                                tint = M3Secondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Open Seat",
                                color = M3OnSurface,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = AppFont,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }

                    // 3. Restart Scooter
                    Surface(
                        color = M3SurfaceContainerHigh,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, M3Warning.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                HapticHelper.performClick(view)
                                showRestartDialog = true
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 14.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = M3Warning,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Restart",
                                color = M3OnSurface,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = AppFont,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // AUTHOR & CREATOR BIO CARD (MATCHING CONNECTION PAGE)
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
                                fontFamily = AppFont,
                                maxLines = 1,
                                softWrap = false
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
                                fontFamily = AppFont,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ModeRangeBox(
    mode: String,
    km: Int,
    color: Color,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (isActive) M3SurfaceContainerHigh else M3SurfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (isActive) 1.5.dp else 1.dp,
            color = if (isActive) color else M3OutlineVariant.copy(alpha = 0.3f)
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = mode,
                color = color,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = AppFont
            )
            val kmText = if (km > 0) "$km km" else when (mode) {
                "ECO" -> "142 km"
                "NORMAL" -> "104 km"
                "SPORT" -> "78 km"
                "HYPER" -> "56 km"
                else -> "---"
            }
            Text(
                text = kmText,
                color = M3OnSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = AppFont
            )
        }
    }
}

@Composable
fun RestartConfirmationDialog(
    onDismiss: () -> Unit,
    onFullReboot: () -> Unit,
    onHmiRestart: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = M3SurfaceContainerHigh,
            border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(M3WarningContainer, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = null,
                            tint = M3OnWarningContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "RESTART SCOOTER",
                            color = M3OnSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = AppFont
                        )
                        Text(
                            text = "Choose restart mode",
                            color = M3Outline,
                            fontSize = 11.sp,
                            fontFamily = AppFont
                        )
                    }
                }

                Surface(
                    color = M3WarningContainer.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, M3Warning.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Safety Notice: Ensure your scooter is safely parked on the main or side stand. Rebooting will power-cycle the MoveOS display.",
                        color = M3OnSurface,
                        fontSize = 10.5.sp,
                        fontFamily = AppFont,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Option 1: Full Scooter Reboot
                    Button(
                        onClick = onFullReboot,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = M3ErrorContainer,
                            contentColor = M3OnErrorContainer
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Full Scooter Reboot (Recommended)", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Option 2: Restart Dash UI Only
                    Button(
                        onClick = onHmiRestart,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = M3PrimaryContainer,
                            contentColor = M3OnPrimaryContainer
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Restart Dash UI Only (Fast HMI Reset)", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Cancel
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel", color = M3Outline, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/**
 * Hero Charging Analytics Dashboard: Replaces the 0.0 km/h speedometer with
 * rich real-time charging metrics whenever the scooter is plugged in and charging.
 */
@Composable
private fun ChargingHeroView(
    chg: com.sarthak.olacan.model.ChargingSessionTelemetry,
    battery: com.sarthak.olacan.model.BatteryState,
    thermal: com.sarthak.olacan.model.ThermalState
) {
    val liveSoc = if (!battery.socPercent.isNaN()) battery.socPercent else battery.displaySocPercent
    val startSoc = if (!chg.startSocPercent.isNaN() && chg.startSocPercent > 0.0) chg.startSocPercent else liveSoc
    val gainedSoc = if (!liveSoc.isNaN() && !startSoc.isNaN()) (liveSoc - startSoc).coerceAtLeast(0.0) else chg.gainedSocPercent

    val liveInflowKw = if (!chg.chargePowerKw.isNaN() && chg.chargePowerKw > 0.015) chg.chargePowerKw
                       else if (!battery.powerKw.isNaN() && battery.powerKw > 0.015) battery.powerKw else 0.0
    val liveInflowWatts = (liveInflowKw * 1000.0).toInt()
    val isActivelyDeliveringPower = liveInflowWatts >= 15

    val liveCurrentA = if (!chg.chargeCurrentA.isNaN() && chg.chargeCurrentA > 0.2) chg.chargeCurrentA
                       else if (!battery.batteryCurrentA.isNaN() && battery.batteryCurrentA > 0.2) battery.batteryCurrentA else 0.0
    val liveVoltageV = if (!chg.chargeVoltageV.isNaN() && chg.chargeVoltageV > 40.0) chg.chargeVoltageV
                       else if (!battery.busVoltageV.isNaN() && battery.busVoltageV > 40.0) battery.busVoltageV else 57.3

    val durationSec = chg.durationSeconds
    val hrs = durationSec / 3600
    val mins = (durationSec % 3600) / 60
    val secs = durationSec % 60
    val durationFormatted = when {
        hrs > 0 -> String.format(Locale.US, "%02dh %02dm", hrs, mins)
        mins > 0 -> String.format(Locale.US, "%02dm %02ds", mins, secs)
        durationSec > 0 -> "${secs}s"
        else -> "Active"
    }

    val timeToFullMins = chg.timeToFullMinutes
    val tFullHours = timeToFullMins / 60
    val tFullMinsRemainder = timeToFullMins % 60
    val timeToFullFormatted = when {
        !isActivelyDeliveringPower && liveSoc >= 99.0 -> "Full (100%)"
        !isActivelyDeliveringPower -> "Standby (0W)"
        timeToFullMins <= 0 && liveSoc >= 99.0 -> "Full (100%)"
        timeToFullMins > 0 -> if (tFullHours > 0) "${tFullHours}h ${tFullMinsRemainder}m" else "${tFullMinsRemainder}m"
        liveSoc >= 95.0 -> "~25m (Float)"
        liveSoc >= 80.0 -> "~45m"
        else -> "~1h 30m"
    }

    val maxDevMv = if (!chg.maxCellDeviationDuringChargeMv.isNaN() && chg.maxCellDeviationDuringChargeMv > 0.0) {
        chg.maxCellDeviationDuringChargeMv
    } else if (!battery.cellDeltaMv.isNaN()) battery.cellDeltaMv else 15.0

    val chargeRateText = if (!chg.chargingRatePercentPerHour.isNaN() && chg.chargingRatePercentPerHour > 0.0) {
        "+${String.format(Locale.US, "%.1f", chg.chargingRatePercentPerHour)}%/hr"
    } else if (liveInflowKw > 0.1) {
        "+${String.format(Locale.US, "%.1f", (liveInflowKw / 3.97) * 100.0)}%/hr"
    } else "+17.5%/hr"

    val coreT = if (!chg.currentTempC.isNaN()) chg.currentTempC else thermal.packEffectiveTempC
    val energyKwh = if (chg.sessionEnergyKwh > 0.01) chg.sessionEnergyKwh
                    else (liveInflowKw * (durationSec / 3600.0)).coerceAtLeast(0.0)
    val costInr = if (chg.estimatedCostInr > 0.1) chg.estimatedCostInr else (energyKwh * 7.5)

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Top Header: Title, Active Badge & Live Inflow Badge
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                        .size(36.dp)
                        .background(M3Success.copy(alpha = 0.18f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Charging",
                        tint = M3Success,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column {
                    Text(
                        text = "CHARGING SESSION",
                        color = M3Outline,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        fontFamily = AppFont,
                        maxLines = 1
                    )
                    val modeName = if (chg.chargerModeName.isNotBlank()) chg.chargerModeName else "Home AC Portable (750W)"
                    val statusText = when {
                        liveSoc >= 99.5 && !isActivelyDeliveringPower -> "$modeName • Charged 100% (Standby)"
                        !isActivelyDeliveringPower -> "$modeName • Plugged In (Smart Plug / AC Off)"
                        else -> "$modeName • Active"
                    }
                    Text(
                        text = statusText,
                        color = if (isActivelyDeliveringPower) M3Success else M3Outline,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AppFont,
                        maxLines = 1
                    )
                }
            }

            // Power Inflow Badge
            Surface(
                color = M3SurfaceContainerHigh,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, if (isActivelyDeliveringPower) M3Success.copy(alpha = 0.45f) else M3OutlineVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = if (isActivelyDeliveringPower) "+$liveInflowWatts W" else "0 W",
                        color = if (isActivelyDeliveringPower) M3Success else M3Outline,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AppFont,
                        maxLines = 1
                    )
                    Text(
                        text = if (isActivelyDeliveringPower) "Live Inflow" else if (liveSoc >= 99.5) "Full" else "Standby",
                        color = M3Outline,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AppFont,
                        maxLines = 1
                    )
                }
            }
        }

        // 2. HERO DISPLAY: Huge SOC % & Time to Full (Replacing the 0.0 km/h speedo)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center
        ) {
            val socVal = if (!liveSoc.isNaN()) liveSoc else 0.0
            Text(
                text = String.format(Locale.US, "%.0f", socVal),
                color = M3OnSurface,
                fontSize = 58.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = AppFont
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "%",
                color = M3Success,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = AppFont,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.padding(bottom = 8.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "TIME TO FULL",
                    color = M3Outline,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = AppFont
                )
                Text(
                    text = timeToFullFormatted,
                    color = M3Primary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = AppFont
                )
            }
        }

        // 3. Visual SOC Progression Bar
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Start: ${String.format(Locale.US, "%.0f", if (!startSoc.isNaN()) startSoc else liveSoc)}%",
                    color = M3Outline,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "+${String.format(Locale.US, "%.1f", gainedSoc)}% Gained ($durationFormatted)",
                    color = M3Success,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Target: 100%",
                    color = M3Outline,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            val progressFraction = if (!liveSoc.isNaN()) (liveSoc / 100.0).coerceIn(0.0, 1.0).toFloat() else 0.5f
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = M3Success,
                trackColor = M3SurfaceContainerLow
            )
        }

        // 4. 2x2 Metric Tiles Grid
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Row 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Tile 1: Energy Consumed
                Surface(
                    color = M3SurfaceContainerHigh,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "ENERGY TAKEN",
                            color = M3Outline,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${String.format(Locale.US, "%.2f", energyKwh)} kWh",
                            color = M3Success,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "≈ ₹${String.format(Locale.US, "%.1f", costInr)} electricity cost (@ ₹7.5/u)",
                            color = M3Outline,
                            fontSize = 8.sp
                        )
                    }
                }

                // Tile 2: Charging Rate & 80% Milestone
                Surface(
                    color = M3SurfaceContainerHigh,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "CHARGE RATE",
                            color = M3Outline,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = chargeRateText,
                            color = M3Primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = if (chg.timeTo80Minutes > 0) "${chg.timeTo80Minutes}m to 80% milestone" else "Pass 80% • CV float phase",
                            color = M3Outline,
                            fontSize = 8.sp
                        )
                    }
                }
            }

            // Row 2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Tile 3: Charger Inflow Current & Voltage
                Surface(
                    color = M3SurfaceContainerHigh,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "CHARGER INFLOW",
                            color = M3Outline,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "+${String.format(Locale.US, "%.1f", liveCurrentA)} A",
                            color = M3OnSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "@ ${String.format(Locale.US, "%.1f", liveVoltageV)} V (+${String.format(Locale.US, "%.1f", chg.sessionAh)} Ah)",
                            color = M3Outline,
                            fontSize = 8.sp
                        )
                    }
                }

                // Tile 4: Pack Balance & Thermals
                Surface(
                    color = M3SurfaceContainerHigh,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "PACK DELTA & TEMP",
                            color = M3Outline,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val deltaColor = if (maxDevMv <= 25.0) M3Success else if (maxDevMv <= 60.0) M3Primary else M3Warning
                        Text(
                            text = "${maxDevMv.toInt()} mV delta",
                            color = deltaColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Pack Temp: ${String.format(Locale.US, "%.1f", if (!coreT.isNaN()) coreT else 31.0)}°C • Balancing OK",
                            color = M3Outline,
                            fontSize = 8.sp
                        )
                    }
                }
            }
        }

        // 5. Bus Voltage & Pack Current Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val voltStr = if (battery.busVoltageV.isNaN()) "---" else "${String.format(Locale.US, "%.1f", battery.busVoltageV)} V"
            val currStr = if (!isActivelyDeliveringPower) "0.0 A" else if (battery.batteryCurrentA.isNaN()) "---" else "+${String.format(Locale.US, "%.1f", Math.abs(battery.batteryCurrentA))} A"

            Column(modifier = Modifier.weight(1f)) {
                Text("BUS VOLTAGE", color = M3Outline, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = AppFont)
                Text(voltStr, color = M3OnSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = AppFont)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text("CHARGING CURRENT", color = M3Outline, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = AppFont)
                Text(currStr, color = if (isActivelyDeliveringPower) M3Success else M3Outline, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = AppFont)
            }
        }
    }
}
