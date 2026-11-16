package com.sarthak.olacan.ui.components

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ElectricScooter
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sarthak.olacan.analyzer.BatteryHealthReport
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
fun BatteryHealthReportDialog(
    report: BatteryHealthReport,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val sharePdf: () -> Unit = {
        try {
            val pdfFile = com.sarthak.olacan.util.PdfReportGenerator.generateBatteryHealthPdf(context, report)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Ola S1 Battery Health Report - ${report.logFileName}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Battery Health Report (PDF)"))
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
                // Normal Share Icon on the left side
                IconButton(
                    onClick = { sharePdf() },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share Health Report (PDF)",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "BATTERY HEALTH (SOH) REPORT",
                        color = PrimaryCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${report.logFileName} (${String.format(Locale.US, "%,d", report.totalFrames)} frames | ${report.durationFormatted})",
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

            // Scrollable Diagnostic Body
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Executive Health Verdict Card
                val isGood = report.calculatedSohPercent >= 88.0
                val isFair = report.calculatedSohPercent in 80.0..87.99
                val verdictColor = when {
                    isGood -> AccentGreen
                    isFair -> WarningAmber
                    else -> DangerRed
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.5.dp, verdictColor.copy(alpha = 0.7f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = report.healthGrade,
                                    color = verdictColor,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Battery Pack Tested on ${report.testDateFormatted}",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", report.calculatedSohPercent)}%",
                                    color = verdictColor,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "STATE OF HEALTH",
                                    color = TextMuted,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = BorderDark,
                            thickness = 1.dp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("USABLE CAPACITY", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${String.format(Locale.US, "%.2f", report.estimatedFullCapacityKwh)} kWh",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "vs 3.70 kWh Usable (3.98 Gross)",
                                    color = TextSecondary,
                                    fontSize = 10.sp
                                )
                            }

                            Column {
                                Text("EFFICIENCY", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", report.batteryEfficiencyPercent)}%",
                                    color = AccentGreen,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "Internal heat loss ~${String.format(Locale.US, "%.0f", report.estimatedInternalLossWh)} Wh",
                                    color = TextSecondary,
                                    fontSize = 10.sp
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text("NET DISCHARGE", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", report.netDischargeAh)} Ah",
                                    color = PrimaryCyan,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", report.deltaSoc)}% SOC Span",
                                    color = TextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section 1: Capacity & Voltage Span
                Text(
                    text = "1. PACK CAPACITY & DISCHARGE SPAN",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DiagnosticMiniCard(
                        title = "SOC RANGE",
                        value = "${String.format(Locale.US, "%.0f", report.startSoc)}% → ${String.format(Locale.US, "%.0f", report.endSoc)}%",
                        sub = "Discharged: ${String.format(Locale.US, "%.1f", report.deltaSoc)}%",
                        icon = Icons.Default.BatteryChargingFull,
                        accentColor = PrimaryCyan,
                        modifier = Modifier.weight(1f)
                    )
                    DiagnosticMiniCard(
                        title = "VOLTAGE RANGE",
                        value = "${String.format(Locale.US, "%.1f", report.startBusVoltageV)}V → ${String.format(Locale.US, "%.1f", report.endBusVoltageV)}V",
                        sub = "Min: ${String.format(Locale.US, "%.1f", report.minBusVoltageV)}V | Max: ${String.format(Locale.US, "%.1f", report.maxBusVoltageV)}V",
                        icon = Icons.Default.Bolt,
                        accentColor = PrimaryCyan,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DiagnosticMiniCard(
                        title = "ESTIMATED TOTAL STORAGE",
                        value = "${String.format(Locale.US, "%.2f", report.estimatedFullCapacityKwh)} kWh",
                        sub = "${String.format(Locale.US, "%.0f", report.estimatedFullCapacityWh)} Wh @ 100% extrapolation",
                        icon = Icons.Default.Favorite,
                        accentColor = verdictColor,
                        modifier = Modifier.weight(1f)
                    )
                    DiagnosticMiniCard(
                        title = "NET AMP-HOURS",
                        value = "${String.format(Locale.US, "%.2f", report.netDischargeAh)} Ah",
                        sub = "Coulomb counting net draw",
                        icon = Icons.Default.Bolt,
                        accentColor = AccentGreen,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Section 2: Power Integration & Losses
                Text(
                    text = "2. POWER INTEGRATION & INTERNAL LOSSES",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DiagnosticMiniCard(
                        title = "GROSS DISCHARGE",
                        value = "${String.format(Locale.US, "%.2f", report.grossDischargeKwh)} kWh",
                        sub = "${String.format(Locale.US, "%.0f", report.grossDischargeWh)} Wh output into motor",
                        icon = Icons.Default.Bolt,
                        accentColor = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    DiagnosticMiniCard(
                        title = "REGEN RECAPTURED",
                        value = "${String.format(Locale.US, "%.2f", report.regenKwh)} kWh",
                        sub = "+${String.format(Locale.US, "%.1f", report.regenContributionPercent)}% energy recovery",
                        icon = Icons.Default.BatteryChargingFull,
                        accentColor = AccentGreen,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DiagnosticMiniCard(
                        title = "INTERNAL I²R HEAT LOSS",
                        value = "${String.format(Locale.US, "%.1f", report.estimatedInternalLossWh)} Wh",
                        sub = "Pack Ri ~35mΩ internal dissipation",
                        icon = Icons.Default.Warning,
                        accentColor = WarningAmber,
                        modifier = Modifier.weight(1f)
                    )
                    DiagnosticMiniCard(
                        title = "EFFICIENCY & CONSUMPTION",
                        value = "${String.format(Locale.US, "%.1f", report.avgConsumptionWhPerKm)} Wh/km",
                        sub = "Over ${String.format(Locale.US, "%.1f", report.totalDistanceKm)} km recorded distance",
                        icon = Icons.Default.ElectricScooter,
                        accentColor = PrimaryCyan,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Section 3: Cell Degradation & MoveOS Integrity
                Text(
                    text = "3. CELL DEGRADATION & MOVEOS INTEGRITY",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DiagnosticMiniCard(
                        title = "WEAKEST CELL CHANNEL",
                        value = "Cell #${report.worstCellChannel}",
                        sub = "Min: ${String.format(Locale.US, "%.3f", report.minCellVoltageRecordedV)}V under load",
                        icon = Icons.Default.Favorite,
                        accentColor = if (report.minCellVoltageRecordedV < 2.8) DangerRed else PrimaryCyan,
                        modifier = Modifier.weight(1f)
                    )
                    DiagnosticMiniCard(
                        title = "MOVEOS 1% CLAMPS",
                        value = "${report.socClampOccurrences}",
                        sub = if (report.socClampOccurrences == 0) "None (Clean run)" else "Sag protection triggered",
                        icon = Icons.Default.Warning,
                        accentColor = if (report.socClampOccurrences > 0) DangerRed else AccentGreen,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Section 4: Physical Pack Cycles vs Vehicle Chassis Lifetime
                Text(
                    text = "4. PACK HARDWARE VS CHASSIS LIFETIME CYCLES",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DiagnosticMiniCard(
                        title = "THIS PACK HARDWARE CYCLES",
                        value = if (report.packHardwareCycles > 0) "${report.packHardwareCycles} Cycles" else "---",
                        sub = "Stored in physical BMS EEPROM",
                        icon = Icons.Default.Favorite,
                        accentColor = PrimaryCyan,
                        modifier = Modifier.weight(1f)
                    )
                    DiagnosticMiniCard(
                        title = "CHASSIS CUMULATIVE CYCLES",
                        value = if (report.vehicleChassisCycles > 0) "${report.vehicleChassisCycles} Cycles" else "---",
                        sub = "VCU lifetime across all packs",
                        icon = Icons.Default.ElectricScooter,
                        accentColor = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DiagnosticMiniCard(
                        title = "AC HOME SESSIONS",
                        value = "${report.acChargeSessions}",
                        sub = "Standard 750W home slow plug-ins",
                        icon = Icons.Default.BatteryChargingFull,
                        accentColor = AccentGreen,
                        modifier = Modifier.weight(1f)
                    )
                    DiagnosticMiniCard(
                        title = "DC HYPERCHARGER SESSIONS",
                        value = "${report.dcFastChargeSessions}",
                        sub = "High C-rate fast charges",
                        icon = Icons.Default.Bolt,
                        accentColor = WarningAmber,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Section 5: Recommendations
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BorderDark)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Advisory",
                                tint = AccentGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "TECHNICAL ADVISORY",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        val advisory = when {
                            report.calculatedSohPercent >= 92.0 -> "Your battery pack shows excellent state of health with minimal degradation. Maintain standard 20%-80% charging habits for long-term cell longevity."
                            report.calculatedSohPercent >= 85.0 -> "Pack capacity is within normal operational aging bounds. Regularly balance cells by charging to 100% overnight once a week."
                            else -> "Significant capacity reduction detected. Consider requesting BMS balancing service at Ola Care if cell imbalance exceeds 80mV at resting voltage."
                        }
                        Text(
                            text = advisory,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Sticky Bottom Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark, RoundedCornerShape(10.dp))
                    .border(1.dp, BorderDark, RoundedCornerShape(10.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, BorderDark),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                ) {
                    Text("Close", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = { sharePdf() },
                    modifier = Modifier
                        .weight(1.5f)
                        .height(44.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share PDF",
                        tint = BgDark,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Share PDF Report",
                        color = BgDark,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
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
    icon: ImageVector,
    accentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, BorderDark)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = TextMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = accentColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = sub,
                color = TextSecondary,
                fontSize = 10.sp,
                lineHeight = 13.sp
            )
        }
    }
}
