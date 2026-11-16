package com.sarthak.olacan.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricScooter
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarthak.olacan.ui.components.ConnectionBanner
import com.sarthak.olacan.ui.components.MetricCard
import com.sarthak.olacan.ui.theme.AccentGreen
import com.sarthak.olacan.ui.theme.BgDark
import com.sarthak.olacan.ui.theme.BorderDark
import com.sarthak.olacan.ui.theme.DangerRed
import com.sarthak.olacan.ui.theme.PrimaryCyan
import com.sarthak.olacan.ui.theme.SurfaceDark
import com.sarthak.olacan.ui.theme.TextPrimary
import com.sarthak.olacan.ui.theme.TextSecondary
import com.sarthak.olacan.ui.theme.WarningAmber
import com.sarthak.olacan.viewmodel.TelemetryViewModel

@Composable
fun SafetyScreen(viewModel: TelemetryViewModel) {
    val stats by viewModel.streamStats.collectAsState()
    val vehicle by viewModel.vehicleStatus.collectAsState()
    val ecu by viewModel.ecuVersions.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Odometer & Regen
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val odoStr = if (vehicle.odometerKm.isNaN()) "---" else String.format(java.util.Locale.US, "%.1f", vehicle.odometerKm)
            MetricCard(
                title = "Total Distance",
                value = odoStr,
                unit = "km",
                subValue = "Lifetime Odometer",
                icon = Icons.Default.ElectricScooter,
                accentColor = PrimaryCyan,
                modifier = Modifier.weight(1f)
            )

            val regenStr = when (vehicle.regenLevel) {
                0 -> "OFF (0%)"
                1 -> "LOW (5%)"
                2 -> "MED (10%)"
                3 -> "HIGH (15%)"
                4 -> "MAX (20%)"
                else -> "OFF (0%)"
            }
            MetricCard(
                title = "Regen Dial",
                value = regenStr,
                subValue = "Recuperative Braking",
                icon = Icons.Default.Security,
                accentColor = if (vehicle.regenLevel > 0) AccentGreen else PrimaryCyan,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Safety State Flags Box (Live UI monitor - excluded from recording logs to save storage)
        Text(
            text = "SAFETY & BODY STATUS",
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceDark, RoundedCornerShape(12.dp))
                .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SafetyRow(
                    label = "Side Stand Sensor",
                    status = if (vehicle.sideStandDown) "DOWN (Locked)" else "UP (Riding/Awake)",
                    isOk = !vehicle.sideStandDown
                )
                SafetyRow(
                    label = "Hill Hold Assist",
                    status = if (vehicle.hillHoldActive) "ACTIVE" else "READY / INACTIVE",
                    isOk = true
                )
                SafetyRow(
                    label = "Fall / Crash Detection",
                    status = if (vehicle.fallDetected) "FALL ALERT TRIGGERED" else "NORMAL (Upright)",
                    isOk = !vehicle.fallDetected
                )
                SafetyRow(
                    label = "Tow / Anti-Theft Movement",
                    status = if (vehicle.towAlert) "TAMPER DETECTED" else "SECURE",
                    isOk = !vehicle.towAlert
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ECU Firmware & Versions Box
        Text(
            text = "INSTALLED ECU FIRMWARE VERSIONS",
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceDark, RoundedCornerShape(12.dp))
                .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ecu.modelName.isNotBlank()) {
                    EcuRow("Vehicle Model", ecu.modelName)
                }
                EcuRow("MoveOS Version", ecu.moveOsVersion.ifEmpty { "MoveOS 5.x (MoveOS Gen2)" })
                EcuRow("Firmware Compatibility", if (ecu.isMoveOsSupported) "MoveOS ≥ 4.0.5 (Fully Supported ✓)" else "Legacy MoveOS (< 4.0.5)")
                EcuRow("VCU Firmware", ecu.vcuFirmware.ifEmpty { "0.0.1.25.1.0 (Ola S1 Pro)" })
                EcuRow("BMS Firmware", ecu.bmsFirmware.ifEmpty { "0.0.1.25.0.0 (14S NMC)" })
                EcuRow("MCU Inverter", ecu.mcuFirmware.ifEmpty { "0.0.1.25.0.0" })
                EcuRow("Touch Head Unit (HU)", ecu.huFirmware.ifEmpty { "MoveOS Gen2 Navigation" })
                EcuRow("BLE Transceiver", ecu.bleVersion.ifEmpty { "Connected (Scooter BLE 5.0)" })
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SafetyRow(label: String, status: String, isOk: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Normal)
        Text(
            text = status,
            color = if (isOk) AccentGreen else DangerRed,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun EcuRow(ecuName: String, version: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = ecuName, color = TextSecondary, fontSize = 11.sp)
        Text(
            text = version,
            color = TextPrimary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
    }
}
