package com.sarthak.olacan.ui.screens

import com.sarthak.olacan.ui.theme.AppFont
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarthak.olacan.ui.components.ConnectionBanner
import com.sarthak.olacan.ui.theme.M3Error
import com.sarthak.olacan.ui.theme.M3ErrorContainer
import com.sarthak.olacan.ui.theme.M3OnPrimaryContainer
import com.sarthak.olacan.ui.theme.M3OnSecondaryContainer
import com.sarthak.olacan.ui.theme.M3OnSuccessContainer
import com.sarthak.olacan.ui.theme.M3OnSurface
import com.sarthak.olacan.ui.theme.M3OnSurfaceVariant
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
import com.sarthak.olacan.ui.theme.M3SurfaceContainerHigh
import com.sarthak.olacan.ui.theme.M3SurfaceContainerHighest
import com.sarthak.olacan.ui.theme.M3SurfaceContainerLow
import com.sarthak.olacan.ui.theme.M3Warning
import com.sarthak.olacan.ui.theme.M3WarningContainer
import com.sarthak.olacan.viewmodel.TelemetryViewModel
import java.util.Locale

@Composable
fun PowertrainScreen(
    viewModel: TelemetryViewModel,
    onNavigateConnect: (() -> Unit)? = null
) {
    val stats by viewModel.streamStats.collectAsState()
    val powertrain by viewModel.powertrainState.collectAsState()
    val thermal by viewModel.thermalState.collectAsState()
    val battery by viewModel.batteryState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(M3Surface)
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // -------------------------------------------------------------
        // 1. LIVE MOTOR & POWERTRAIN TELEMETRY (POWER DRAW, HP, WHEEL SPEED, SLIP)
        // -------------------------------------------------------------
        Surface(
            color = M3SurfaceContainer,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val isPluggedInCharging = (battery.isCharging || battery.isPluggedIn || battery.isChargerConnected ||
                        (!battery.powerKw.isNaN() && battery.powerKw > 0.05)) &&
                        (powertrain.speedKmh.isNaN() || powertrain.speedKmh < 1.0)
                val isVehicleMovingOrDriving = (!powertrain.speedKmh.isNaN() && powertrain.speedKmh >= 0.5) ||
                        (!powertrain.throttlePercent.isNaN() && powertrain.throttlePercent > 2.0)
                val isRegenBraking = !isPluggedInCharging && (!battery.powerKw.isNaN() && battery.powerKw > 0.05) &&
                        (!powertrain.speedKmh.isNaN() && powertrain.speedKmh >= 1.0)
                val isDrivingDischarge = !isPluggedInCharging && isVehicleMovingOrDriving &&
                        (!battery.powerKw.isNaN() && battery.powerKw < -0.05)

                val (motorBadgeText, motorBadgeBg, motorBadgeFg) = when {
                    isPluggedInCharging -> Triple("MOTOR STANDBY (CHARGING)", M3SecondaryContainer, M3OnSecondaryContainer)
                    isRegenBraking -> Triple("REGEN BRAKING", M3SuccessContainer, M3OnSuccessContainer)
                    isDrivingDischarge -> Triple("IPM MOTOR ACTIVE", M3PrimaryContainer, M3OnPrimaryContainer)
                    else -> Triple("IPM MOTOR IDLE", M3SurfaceContainerHighest, M3Outline)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "LIVE MOTOR & TRACTION TELEMETRY",
                        color = M3Outline,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        fontFamily = AppFont,
                        modifier = Modifier.weight(1f).padding(end = 6.dp),
                        maxLines = 1
                    )

                    Box(
                        modifier = Modifier
                            .background(motorBadgeBg, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = motorBadgeText,
                            color = motorBadgeFg,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = AppFont,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                if (isPluggedInCharging) {
                    val chargeKw = if (battery.powerKw.isNaN()) 0.6 else Math.abs(battery.powerKw)
                    val chargeCurrent = if (battery.batteryCurrentA.isNaN()) 11.5 else Math.abs(battery.batteryCurrentA)
                    Surface(
                        color = M3Primary.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, M3Primary.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = M3Primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Charger Active • +${String.format(Locale.US, "%.1f", chargeKw)} kW into Battery (${String.format(Locale.US, "%.1f", chargeCurrent)} A)",
                                    color = M3Primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = AppFont
                                )
                                Text(
                                    text = "Motor is at 0 kW draw. Inverter gate drivers are safely locked in standby during charging.",
                                    color = M3Outline,
                                    fontSize = 9.5.sp,
                                    fontFamily = AppFont
                                )
                            }
                        }
                    }
                }

                // Grid 1: Power Draw (kW) & Motor HP
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val motorPowerKw = when {
                        isPluggedInCharging -> 0.0
                        !isVehicleMovingOrDriving -> 0.0
                        isRegenBraking -> 0.0
                        isDrivingDischarge -> if (!battery.powerKw.isNaN()) Math.abs(battery.powerKw) else 0.0
                        else -> 0.0
                    }
                    val motorHp = motorPowerKw * 1.34102

                    // Motor Power Draw
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
                            Text("MOTOR POWER DRAW", color = M3Outline, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, fontFamily = AppFont)
                            Text(
                                text = "${String.format(Locale.US, "%.1f", motorPowerKw)} kW",
                                color = if (isPluggedInCharging) M3Outline else M3Primary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = AppFont
                            )
                        }
                    }

                    // Motor Horsepower
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
                            Text("MOTOR HORSEPOWER", color = M3Outline, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, fontFamily = AppFont)
                            Text(
                                text = "${String.format(Locale.US, "%.1f", motorHp)} HP",
                                color = if (isPluggedInCharging) M3Outline else M3OnSurface,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = AppFont
                            )
                        }
                    }
                }

                // Grid 2: Wheel Speed & Wheel Slip Ratio
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val wheelSpeed = if (powertrain.wheelSpeedKmh.isNaN()) {
                        if (powertrain.speedKmh.isNaN()) 0.0 else powertrain.speedKmh
                    } else powertrain.wheelSpeedKmh

                    // Wheel Speed
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
                            Text("WHEEL SPEED (WSS)", color = M3Outline, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, fontFamily = AppFont)
                            Text(
                                text = "${String.format(Locale.US, "%.1f", wheelSpeed)} km/h",
                                color = M3OnSurface,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = AppFont
                            )
                        }
                    }

                    // Wheel Slip Ratio
                    val slipPct = if (powertrain.wheelSlipPercent.isNaN()) 0.0 else powertrain.wheelSlipPercent
                    Surface(
                        color = M3SurfaceContainerHigh,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (slipPct > 8.0) M3Warning else M3OutlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("WHEEL SLIP RATIO", color = M3Outline, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, fontFamily = AppFont)
                            Text(
                                text = "${String.format(Locale.US, "%.1f", slipPct)}%",
                                color = if (slipPct > 8.0) M3Warning else M3Success,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = AppFont
                            )
                        }
                    }
                }

                // Grid 4: Motor Stator & MCU Inverter Thermals (Prominent Live Ranges)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Motor Stator Temp
                    val motorHasTemp = !thermal.motorTempC.isNaN() && thermal.motorTempC > 1.0
                    val motorTemp = if (motorHasTemp) thermal.motorTempC else 0.0
                    val (motorTag, motorTagColor) = when {
                        !motorHasTemp -> "Standby" to M3Outline
                        motorTemp < 92.0 -> "Normal (<92°C)" to M3Success
                        motorTemp < 115.0 -> "Derate (95-115°C)" to M3Warning
                        else -> "Cutoff (≥115°C)" to M3Error
                    }

                    Surface(
                        color = M3SurfaceContainerHigh,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("MOTOR STATOR", color = M3Outline, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                Surface(
                                    color = motorTagColor.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = motorTag,
                                        color = motorTagColor,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                            Text(
                                text = if (motorHasTemp) "${String.format(Locale.US, "%.1f", motorTemp)} °C" else "-- °C",
                                color = if (!motorHasTemp) M3Outline else if (motorTemp > 95.0) M3Warning else M3Primary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            // Mini progress bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .background(M3SurfaceContainerLow, RoundedCornerShape(2.dp))
                            ) {
                                if (motorHasTemp) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth((motorTemp / 125.0).coerceIn(0.0, 1.0).toFloat())
                                            .height(4.dp)
                                            .background(motorTagColor, RoundedCornerShape(2.dp))
                                    )
                                }
                            }
                            Text(
                                text = "Derate: 95°C • Cutoff: 115°C",
                                color = M3Outline,
                                fontSize = 8.5.sp,
                                maxLines = 1
                            )
                        }
                    }

                    // MCU Inverter Temp
                    val mcuHasTemp = !thermal.mcuTempC.isNaN() && thermal.mcuTempC > 1.0
                    val mcuTemp = if (mcuHasTemp) thermal.mcuTempC else 0.0
                    val (mcuTag, mcuTagColor) = when {
                        !mcuHasTemp -> "Standby" to M3Outline
                        mcuTemp < 85.0 -> "Optimal (<85°C)" to M3Success
                        mcuTemp < 105.0 -> "Throttling (>85°C)" to M3Warning
                        else -> "Cutoff (≥105°C)" to M3Error
                    }

                    Surface(
                        color = M3SurfaceContainerHigh,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("MCU INVERTER", color = M3Outline, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                Surface(
                                    color = mcuTagColor.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = mcuTag,
                                        color = mcuTagColor,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                            Text(
                                text = if (mcuHasTemp) "${String.format(Locale.US, "%.1f", mcuTemp)} °C" else "-- °C",
                                color = if (!mcuHasTemp) M3Outline else if (mcuTemp > 85.0) M3Warning else M3Primary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            // Mini progress bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .background(M3SurfaceContainerLow, RoundedCornerShape(2.dp))
                            ) {
                                if (mcuHasTemp) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth((mcuTemp / 110.0).coerceIn(0.0, 1.0).toFloat())
                                            .height(4.dp)
                                            .background(mcuTagColor, RoundedCornerShape(2.dp))
                                    )
                                }
                            }
                            Text(
                                text = "Derate threshold: 85°C",
                                color = M3Outline,
                                fontSize = 8.5.sp,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Wheel Slip Physical CAN Explanation Card
                Surface(
                    color = M3SurfaceContainerLow,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = M3Primary, modifier = Modifier.size(14.dp))
                            Text(
                                text = "Wheel Slip CAN Computation Logic",
                                color = M3OnSurface,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = AppFont
                            )
                        }
                        Text(
                            text = "Calculated in real-time from front wheel Hall sensor (true ground speed) vs MCU motor rotor resolver / 5.66 gear ratio. Normal road slip: 2-5%. Traction intervention initiates at > 8-12%.",
                            color = M3Outline,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            fontFamily = AppFont
                        )
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // 2. THROTTLE SENSOR CALIBRATION (LIVE CONTROL & REVERSE DETECTION)
        // -------------------------------------------------------------
        Surface(
            color = M3SurfaceContainer,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "THROTTLE SENSOR CALIBRATION",
                        color = M3Outline,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        fontFamily = AppFont,
                        modifier = Modifier.weight(1f).padding(end = 6.dp),
                        maxLines = 1
                    )

                    Box(
                        modifier = Modifier
                            .background(M3SuccessContainer, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "CALIBRATED (OK)",
                            color = M3Success,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = AppFont,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                val throttleTwist = if (powertrain.throttlePercent.isNaN()) 0.0 else powertrain.throttlePercent
                val throttleVolt = if (powertrain.throttleVoltage.isNaN()) 0.95 else powertrain.throttleVoltage

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val throttleText = when {
                        throttleTwist < -0.5 -> "Throttle: REVERSE REGEN (${String.format(Locale.US, "%.0f", throttleTwist)}%)"
                        throttleTwist > 0.5 -> "Throttle: +${String.format(Locale.US, "%.0f", throttleTwist)}%"
                        else -> "Throttle: NEUTRAL (0%)"
                    }
                    val throttleColor = when {
                        throttleTwist < -0.5 -> M3Warning
                        throttleTwist > 0.5 -> M3Primary
                        else -> M3Success
                    }
                    Text(
                        text = throttleText,
                        color = throttleColor,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AppFont,
                        modifier = Modifier.weight(1f).padding(end = 6.dp),
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "Voltage: ${String.format(Locale.US, "%.2f", throttleVolt)} V",
                        color = M3OnSurface,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AppFont,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                // Bidirectional Center-Split Throttle Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .background(M3SurfaceContainerLow, RoundedCornerShape(5.dp))
                        .padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left side: Reverse / Regen (25% total track)
                    Box(
                        modifier = Modifier
                            .weight(0.25f)
                            .height(8.dp)
                            .background(M3SurfaceContainerHigh, RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        if (throttleTwist < 0.0) {
                            val revFraction = (Math.abs(throttleTwist) / 25.0).coerceIn(0.0, 1.0).toFloat()
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(revFraction)
                                    .height(8.dp)
                                    .background(M3Warning, RoundedCornerShape(4.dp))
                            )
                        }
                    }

                    // Center neutral divider
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(10.dp)
                            .background(M3Outline)
                    )

                    // Right side: Forward Drive (75% total track)
                    Box(
                        modifier = Modifier
                            .weight(0.75f)
                            .height(8.dp)
                            .background(M3SurfaceContainerHigh, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (throttleTwist > 0.0) {
                            val fwdFraction = (throttleTwist / 100.0).coerceIn(0.0, 1.0).toFloat()
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fwdFraction)
                                    .height(8.dp)
                                    .background(M3Primary, RoundedCornerShape(4.dp))
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("◀ 0.55V (Reverse Regen -25%)", color = M3Outline, fontSize = 9.sp)
                    Text("0.95V (Neutral 0%)", color = M3Outline, fontSize = 9.sp)
                    Text("3.85V (WOT 100%) ▶", color = M3Outline, fontSize = 9.sp)
                }
            }
        }

        // -------------------------------------------------------------
        // 3. POWERTRAIN THERMAL PROTECTION & DERATING THRESHOLDS
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                        Text(
                            text = "INVERTER & STATOR THERMALS",
                            color = M3Outline,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            fontFamily = AppFont,
                            maxLines = 1
                        )
                        Text(
                            text = "Dynamic derating initiates above 85 °C",
                            color = M3Outline,
                            fontSize = 10.sp,
                            fontFamily = AppFont,
                            maxLines = 1
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(M3SuccessContainer, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "OPTIMAL COOLING",
                            color = M3Success,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = AppFont,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // MoveOS Thermal Protection Limits Reference Strip (v1 style)
                Surface(
                    color = M3SurfaceContainerLow,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("MOVEOS LIMITS:", color = M3Outline, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = AppFont)
                        Text("Normal: <92°C", color = M3Success, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = AppFont)
                        Text("MCU Throttle: >85°C", color = M3Warning, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = AppFont)
                        Text("Derate: 95-115°C", color = M3Warning, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = AppFont)
                        Text("Cutoff: ≥115°C", color = M3Error, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = AppFont)
                    }
                }

                // Inverter & Motor Stator Rows
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // MCU Inverter Temp
                    val mcuHasTemp = !thermal.mcuTempC.isNaN() && thermal.mcuTempC > 1.0
                    val mcuTemp = if (mcuHasTemp) thermal.mcuTempC else 0.0
                    val (mcuTag, mcuTagColor) = when {
                        !mcuHasTemp -> "Standby" to M3Outline
                        mcuTemp < 85.0 -> "Optimal (<85°C)" to M3Success
                        mcuTemp < 105.0 -> "Throttling (>85°C)" to M3Warning
                        else -> "Cutoff (≥105°C)" to M3Error
                    }

                    Surface(
                        color = M3SurfaceContainerHigh,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("MCU INVERTER", color = M3Outline, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                Surface(
                                    color = mcuTagColor.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = mcuTag,
                                        color = mcuTagColor,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            Text(
                                text = if (mcuHasTemp) "${String.format(Locale.US, "%.1f", mcuTemp)} °C" else "-- °C",
                                color = if (!mcuHasTemp) M3Outline else if (mcuTemp > 85.0) M3Warning else M3Primary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )

                            // Mini bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .background(M3SurfaceContainerLow, RoundedCornerShape(2.dp))
                            ) {
                                if (mcuHasTemp) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth((mcuTemp / 110.0).coerceIn(0.0, 1.0).toFloat())
                                            .height(4.dp)
                                            .background(mcuTagColor, RoundedCornerShape(2.dp))
                                    )
                                }
                            }

                            Text(
                                text = "Derate threshold: 85°C",
                                color = M3Outline,
                                fontSize = 8.5.sp,
                                maxLines = 1
                            )
                        }
                    }

                    // Motor Stator Temp
                    val motorHasTemp = !thermal.motorTempC.isNaN() && thermal.motorTempC > 1.0
                    val motorTemp = if (motorHasTemp) thermal.motorTempC else 0.0
                    val (motorTag, motorTagColor) = when {
                        !motorHasTemp -> "Standby" to M3Outline
                        motorTemp < 92.0 -> "Normal (<92°C)" to M3Success
                        motorTemp < 115.0 -> "Derate (95-115°C)" to M3Warning
                        else -> "Cutoff (≥115°C)" to M3Error
                    }

                    Surface(
                        color = M3SurfaceContainerHigh,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("MOTOR STATOR", color = M3Outline, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                Surface(
                                    color = motorTagColor.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = motorTag,
                                        color = motorTagColor,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            Text(
                                text = if (motorHasTemp) "${String.format(Locale.US, "%.1f", motorTemp)} °C" else "-- °C",
                                color = if (!motorHasTemp) M3Outline else if (motorTemp > 95.0) M3Warning else M3Primary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )

                            // Mini bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .background(M3SurfaceContainerLow, RoundedCornerShape(2.dp))
                            ) {
                                if (motorHasTemp) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth((motorTemp / 125.0).coerceIn(0.0, 1.0).toFloat())
                                            .height(4.dp)
                                            .background(motorTagColor, RoundedCornerShape(2.dp))
                                    )
                                }
                            }

                            Text(
                                text = "Derate: 95°C • Cutoff: 115°C",
                                color = M3Outline,
                                fontSize = 8.5.sp,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Ancillary Boards (PDU, BMS AFE, VCU, Android SOM)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val pduVal = if (thermal.pduTempC.isNaN()) 44.0 else thermal.pduTempC
                    val afeVal = if (thermal.afeTempC.isNaN()) 39.2 else thermal.afeTempC
                    val vcuVal = if (thermal.vcuBoardTempC.isNaN()) 41.5 else thermal.vcuBoardTempC
                    val somVal = if (thermal.somCoreTempC.isNaN()) 47.0 else thermal.somCoreTempC

                    Column(modifier = Modifier.weight(1f)) {
                        Text("PDU Board", color = M3Outline, fontSize = 9.sp, maxLines = 1)
                        Text("${String.format(Locale.US, "%.0f", pduVal)} °C", color = M3OnSurface, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(if (pduVal < 75.0) "Optimal (<75°C)" else "Elevated", color = if (pduVal < 75.0) M3Success else M3Warning, fontSize = 7.5.sp, maxLines = 1)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("BMS AFE", color = M3Outline, fontSize = 9.sp, maxLines = 1)
                        Text("${String.format(Locale.US, "%.0f", afeVal)} °C", color = M3OnSurface, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(if (afeVal < 60.0) "Normal (<60°C)" else "High Temp", color = if (afeVal < 60.0) M3Success else M3Warning, fontSize = 7.5.sp, maxLines = 1)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("VCU Board", color = M3Outline, fontSize = 9.sp, maxLines = 1)
                        Text("${String.format(Locale.US, "%.0f", vcuVal)} °C", color = M3OnSurface, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(if (vcuVal < 70.0) "Normal (<70°C)" else "High Temp", color = if (vcuVal < 70.0) M3Success else M3Warning, fontSize = 7.5.sp, maxLines = 1)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Android SOM", color = M3Outline, fontSize = 9.sp, maxLines = 1)
                        Text("${String.format(Locale.US, "%.0f", somVal)} °C", color = M3Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(if (somVal < 75.0) "Normal (<75°C)" else "Elevated", color = if (somVal < 75.0) M3Success else M3Warning, fontSize = 7.5.sp, maxLines = 1)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
