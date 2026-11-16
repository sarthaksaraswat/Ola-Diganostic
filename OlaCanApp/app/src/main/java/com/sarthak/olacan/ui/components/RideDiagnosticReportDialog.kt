package com.sarthak.olacan.ui.components

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ElectricScooter
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sarthak.olacan.analyzer.RideDiagnosticReport
import com.sarthak.olacan.analyzer.formatRideMetric
import com.sarthak.olacan.ui.theme.AccentGreen
import com.sarthak.olacan.ui.theme.BgDark
import com.sarthak.olacan.ui.theme.BorderDark
import com.sarthak.olacan.ui.theme.DangerRed
import com.sarthak.olacan.ui.theme.PrimaryCyan
import com.sarthak.olacan.ui.theme.SurfaceDark
import com.sarthak.olacan.ui.theme.SurfaceElevated
import com.sarthak.olacan.ui.theme.TextMuted
import com.sarthak.olacan.ui.theme.TextPrimary
import com.sarthak.olacan.ui.theme.TextSecondary
import com.sarthak.olacan.ui.theme.WarningAmber
import java.util.Locale

@Composable
fun RideDiagnosticReportDialog(
    report: RideDiagnosticReport,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val sharePdf: () -> Unit = {
        try {
            val pdfFile = com.sarthak.olacan.util.PdfReportGenerator.generatePdfReport(context, report)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Ola S1 Ride Diagnostic Report - ${report.fileName}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Diagnostic PDF"))
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open share: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BgDark)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(top = 10.dp, start = 14.dp, end = 14.dp, bottom = 10.dp)
        ) {
            // Sticky Top Header Bar (Always visible)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark, RoundedCornerShape(10.dp))
                    .border(1.dp, BorderDark, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Normal Share Icon on the left side (same style & color as the cross)
                IconButton(
                    onClick = { sharePdf() },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share Report (PDF)",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "OLA S1 RIDE DIAGNOSIS",
                        color = PrimaryCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${report.fileName} (${report.fileSizeFormatted} | ${String.format(Locale.US, "%,d", report.totalFrames)} frames)",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Cross (Close) Icon on the right
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Scrollable Diagnostic Body (Fills all available middle space)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                // Executive Verdict Card
                val hasDegradedCell = report.cells.any { it.condition == "CRITICAL DEGRADED" }
                val hasMotorCutoff = report.maxMotorTempC >= 115.0
                val hasHighTemp = report.maxPduTempC > 85.0 || report.maxMotorTempC >= 95.0 || report.maxPackTempC > 50.0
                val hasClamp = report.socClampOccurrences > 0
                val isCritical = report.hasCriticalFault
                val needsAttention = report.hasWarning || report.hasIncompleteData

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isCritical) DangerRed.copy(alpha = 0.15f) else if (needsAttention) WarningAmber.copy(alpha = 0.15f) else AccentGreen.copy(alpha = 0.15f),
                            RoundedCornerShape(12.dp)
                        )
                        .border(
                            1.5.dp,
                            if (isCritical) DangerRed else if (needsAttention) WarningAmber else AccentGreen,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(14.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isCritical || needsAttention) Icons.Default.Warning else Icons.Default.Favorite,
                                contentDescription = null,
                                tint = if (isCritical) DangerRed else if (needsAttention) WarningAmber else AccentGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = report.verdict,
                                color = if (isCritical) DangerRed else if (needsAttention) WarningAmber else AccentGreen,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        val verdictText = buildString {
                            if (hasDegradedCell) {
                                val degraded = report.cells.filter { it.condition == "CRITICAL DEGRADED" }
                                val cellList = degraded.joinToString(", ") { "Cell ${String.format(Locale.US, "%02d", it.channel)}" }
                                val worst = degraded.maxByOrNull { it.internalResistanceMOhm } ?: report.cells.first()
                                append("• Severe cell degradation on $cellList (internal resistance up to ${String.format(Locale.US, "%.2f", worst.internalResistanceMOhm)} mΩ; sagged to ${String.format(Locale.US, "%.2f", worst.minVoltageV)}V under acceleration).\n")
                            }
                            if (hasClamp) {
                                append("• MoveOS 1% Limp Clamp triggered ${report.socClampOccurrences} time(s) (${String.format(Locale.US, "%.1f", report.socClampDurationSeconds)}s total) to protect weak cells.\n")
                            }
                            if (hasMotorCutoff) {
                                append("• Critical motor stator overtemperature cutoff: stator hit ${formatRideMetric(report.maxMotorTempC, 1, "°C")} (≥115°C safety limit enforced).\n")
                            } else if (hasHighTemp) {
                                val tempDetails = mutableListOf<String>()
                                if (report.maxMotorTempC >= 95.0) tempDetails.add("Motor stator ${formatRideMetric(report.maxMotorTempC, 1, "°C")}")
                                if (report.maxPduTempC > 85.0) tempDetails.add("PDU ${formatRideMetric(report.maxPduTempC, 1, "°C")}")
                                if (report.maxPackTempC > 50.0) tempDetails.add("Pack core ${formatRideMetric(report.maxPackTempC, 1, "°C")}")
                                append("• Elevated component temperatures: ${tempDetails.joinToString(", ")}.\n")
                            }
                            if (report.bmsCellDipFaultsCount > 0) {
                                appendLine("• The log contains BMS cell-undervoltage fault frames.")
                            }
                            if (report.hasIncompleteData) {
                                appendLine("• Some measurements are missing or the log ended early.")
                            }
                            if (isEmpty()) {
                                append("The verdict covers the telemetry captured in this log.")
                            }
                        }.trimEnd()
                        Text(
                            text = verdictText,
                            color = TextPrimary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Ride & Powertrain Performance
                Text(
                    text = "POWERTRAIN & SESSION METRICS",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DiagnosticMiniCard(
                        title = "Top Speed",
                        value = "${String.format(Locale.US, "%.1f", report.maxSpeedKmh)} km/h",
                        sub = "Avg: ${String.format(Locale.US, "%.1f", report.avgMovingSpeedKmh)} km/h",
                        color = PrimaryCyan,
                        modifier = Modifier.weight(1f)
                    )
                    DiagnosticMiniCard(
                        title = "Distance",
                        value = "${String.format(Locale.US, "%.2f", report.distanceKm)} km",
                        sub = "Time: ${report.durationSeconds / 60}m ${report.durationSeconds % 60}s",
                        color = PrimaryCyan,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DiagnosticMiniCard(
                        title = "Peak Power",
                        value = formatRideMetric(report.peakDischargePowerKw, 2, " kW"),
                        sub = "${formatRideMetric(report.peakDischargeHorsepower, 1, " HP")} (${String.format(Locale.US, "%.1f", report.peakDischargeCurrentA)}A)",
                        color = PrimaryCyan,
                        modifier = Modifier.weight(1f)
                    )
                    DiagnosticMiniCard(
                        title = "Peak Regen",
                        value = formatRideMetric(report.peakRegenPowerKw, 2, " kW"),
                        sub = "+${String.format(Locale.US, "%.1f", report.peakRegenCurrentA)}A Charge",
                        color = AccentGreen,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Battery Pack & 14S Cell Matrix
                Text(
                    text = "14S BATTERY HEALTH & DYNAMIC IMPEDANCE",
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
                        .padding(12.dp)
                ) {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("REST IMBALANCE", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Text("${formatRideMetric(report.restingCellDeltaMv, 0, " mV")}", color = AccentGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                val loadDeltaColor = when {
                                    report.peakLoadCellDeltaMv >= 400.0 -> DangerRed
                                    report.peakLoadCellDeltaMv >= 250.0 -> WarningAmber
                                    else -> AccentGreen
                                }
                                Text("PEAK LOAD DELTA", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Text("${formatRideMetric(report.peakLoadCellDeltaMv, 0, " mV")}", color = loadDeltaColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("VOLTAGE SPAN", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Text("${formatRideMetric(report.minPackVoltageV, 1, " V")} - ${formatRideMetric(report.maxPackVoltageV, 1, " V")}", color = PrimaryCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = BorderDark)
                        Spacer(modifier = Modifier.height(8.dp))

                        // Per-cell breakdown table
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("CELL", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(36.dp))
                            Text("REST", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("MIN (LOAD)", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("SAG", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("IR (Ri)", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("STATUS", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(6.dp))

                        if (report.cells.isEmpty()) {
                            Text("No paired resting and loaded cell measurements.", color = TextSecondary, fontSize = 12.sp)
                        }
                        report.cells.forEach { cell ->
                            val isWorst = cell.condition == "CRITICAL DEGRADED"
                            val isHighSag = cell.condition == "HIGH SAG"
                            val rowColor = when {
                                isWorst -> DangerRed
                                isHighSag -> WarningAmber
                                else -> TextPrimary
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "C${String.format("%02d", cell.channel)}",
                                    color = rowColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.width(36.dp)
                                )
                                Text(
                                    text = String.format(Locale.US, "%.3fV", cell.restingVoltageV),
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = String.format(Locale.US, "%.3fV", cell.minVoltageV),
                                    color = rowColor,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "${cell.maxSagMv.toInt()}mV",
                                    color = rowColor,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = String.format(Locale.US, "%.2fmΩ", cell.internalResistanceMOhm),
                                    color = rowColor,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (isWorst || isHighSag) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    text = if (isWorst) "DEGRADED" else if (isHighSag) "HIGH SAG" else "NOMINAL",
                                    color = if (isWorst) DangerRed else if (isHighSag) WarningAmber else AccentGreen,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // MoveOS Safety Interlocks & Derates
                Text(
                    text = "MOVEOS FAULT & DERATING ANALYSIS",
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
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DerateRow(
                            label = "1% False-Empty Clamp",
                            count = "${report.socClampOccurrences} time(s)",
                            detail = "Total ${String.format(Locale.US, "%.1f", report.socClampDurationSeconds)}s in cell-protection limp",
                            isActive = report.socClampOccurrences > 0,
                            activeColor = DangerRed
                        )
                        DerateRow(
                            label = "Cell Undervoltage Sag Derate (0x122 b2)",
                            count = "${report.bmsCellDipFaultsCount} frames",
                            detail = "Counts frames carrying the BMS cell-undervoltage flag",
                            isActive = report.bmsCellDipFaultsCount > 0,
                            activeColor = DangerRed
                        )
                        DerateRow(
                            label = "High Spread Load Sag Warning (0x122 b3)",
                            count = "${report.bmsLoadSagWarningsCount} frames",
                            detail = "Active during high load imbalance > 400mV",
                            isActive = report.bmsLoadSagWarningsCount > 0,
                            activeColor = WarningAmber
                        )
                        DerateRow(
                            label = "High Discharge Current Derate (0x122 b4)",
                            count = "${report.bmsHighDischargeDerateCount} frames",
                            detail = "Prolonged hard acceleration current clamp",
                            isActive = report.bmsHighDischargeDerateCount > 0,
                            activeColor = WarningAmber
                        )
                        DerateRow(
                            label = "Regen Inhibit Flag (0x122 b5)",
                            count = "${report.bmsRegenInhibitCount} frames",
                            detail = "Normal regen braking restricted at high SOC or cold pack",
                            isActive = report.bmsRegenInhibitCount > 0,
                            activeColor = WarningAmber
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Thermal Stress & Hotspots
                Text(
                    text = "THERMAL STRESS & COMPONENT PEAKS",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val packHotspotColor = when {
                        report.maxPackTempC >= 55.0 || report.maxPackThermalGradientC >= 10.0 -> DangerRed
                        report.maxPackTempC >= 48.0 -> WarningAmber
                        else -> AccentGreen
                    }
                    DiagnosticMiniCard(
                        title = "Pack Hotspot (Probe ${report.hottestProbeIdx})",
                        value = "${formatRideMetric(report.maxPackTempC, 1, "°C")}",
                        sub = "ΔT Spread: ${String.format(Locale.US, "%.1f", report.maxPackThermalGradientC)}°C",
                        color = packHotspotColor,
                        modifier = Modifier.weight(1f)
                    )
                    DiagnosticMiniCard(
                        title = "PDU Distribution",
                        value = "${formatRideMetric(report.maxPduTempC, 1, "°C")}",
                        sub = if (report.maxPduTempC > 85.0) "Throttling (>85°C)" else "Normal",
                        color = if (report.maxPduTempC > 85.0) DangerRed else PrimaryCyan,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val motorColor = when {
                        report.maxMotorTempC >= 115.0 -> DangerRed
                        report.maxMotorTempC >= 95.0 -> WarningAmber
                        else -> AccentGreen
                    }
                    val motorSub = when {
                        report.maxMotorTempC >= 115.0 -> "Critical Cutoff (≥115°C)"
                        report.maxMotorTempC >= 95.0 -> "Thermal Derate (≥95°C)"
                        else -> "Normal (<92°C)"
                    }
                    DiagnosticMiniCard(
                        title = "Motor Stator",
                        value = "${formatRideMetric(report.maxMotorTempC, 1, "°C")}",
                        sub = motorSub,
                        color = motorColor,
                        modifier = Modifier.weight(1f)
                    )
                    DiagnosticMiniCard(
                        title = "BMS AFE IC",
                        value = "${formatRideMetric(report.maxAfeTempC, 1, "°C")}",
                        sub = if (report.maxAfeTempC > 80.0) "High Temp" else "Normal",
                        color = if (report.maxAfeTempC > 80.0) WarningAmber else PrimaryCyan,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sticky Bottom Action Bar (ALWAYS VISIBLE, never hidden!)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark, RoundedCornerShape(12.dp))
                    .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. Share as PDF Report Button
                    Button(
                        onClick = { sharePdf() },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan, contentColor = BgDark),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(44.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // 2. Share Text Summary Button
                    OutlinedButton(
                        onClick = {
                            try {
                                val summary = buildString {
                                    appendLine("OLA S1 CAN RIDE DIAGNOSTIC REPORT")
                                    appendLine("File: ${report.fileName}")
                                    appendLine("Distance: ${String.format(Locale.US, "%.2f", report.distanceKm)} km | Max Speed: ${String.format(Locale.US, "%.1f", report.maxSpeedKmh)} km/h")
                                    appendLine("Peak Power: ${formatRideMetric(report.peakDischargePowerKw, 2, " kW")} (${formatRideMetric(report.peakDischargeHorsepower, 1, " HP")})")
                                    appendLine("Rest Imbalance: ${formatRideMetric(report.restingCellDeltaMv, 0, " mV")} | Peak Load Imbalance: ${formatRideMetric(report.peakLoadCellDeltaMv, 0, " mV")}")
                                    val degCell = report.cells.firstOrNull { it.condition == "CRITICAL DEGRADED" }
                                    val degStr = if (degCell != null) "Cell ${String.format(Locale.US, "%02d", degCell.channel)} (Ri = ${String.format(Locale.US, "%.2f", degCell.internalResistanceMOhm)} mΩ)" else "Nominal"
                                    appendLine("Degraded Cell: $degStr")
                                    appendLine("MoveOS 1% Clamp: ${report.socClampOccurrences} time(s) (${String.format(Locale.US, "%.1f", report.socClampDurationSeconds)}s)")
                                    appendLine("Peak Temps: Pack ${formatRideMetric(report.maxPackTempC, 1, "°C")} (ΔT ${String.format(Locale.US, "%.1f", report.maxPackThermalGradientC)}°C) | PDU ${formatRideMetric(report.maxPduTempC, 1, "°C")} | Motor ${formatRideMetric(report.maxMotorTempC, 1, "°C")}")
                                }
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Ola S1 Ride Diagnostic Analysis")
                                    putExtra(Intent.EXTRA_TEXT, summary)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share Diagnostic Summary"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not open share: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryCyan),
                        border = BorderStroke(1.dp, PrimaryCyan.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Text("Text", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // 3. Close Button
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        border = BorderStroke(1.dp, BorderDark),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(0.8f)
                            .height(44.dp)
                    ) {
                        Text("Close", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticMiniCard(
    title: String,
    value: String,
    sub: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(SurfaceDark, RoundedCornerShape(10.dp))
            .border(1.dp, BorderDark, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Column {
            Text(title, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.height(2.dp))
            Text(sub, color = TextSecondary, fontSize = 10.sp)
        }
    }
}

@Composable
private fun DerateRow(
    label: String,
    count: String,
    detail: String,
    isActive: Boolean,
    activeColor: androidx.compose.ui.graphics.Color = DangerRed
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = TextMuted, fontSize = 9.sp)
        }
        Text(
            text = count,
            color = if (isActive) activeColor else AccentGreen,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
