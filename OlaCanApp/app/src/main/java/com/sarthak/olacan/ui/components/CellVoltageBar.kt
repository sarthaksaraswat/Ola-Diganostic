package com.sarthak.olacan.ui.components

import com.sarthak.olacan.ui.theme.AppFont
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarthak.olacan.ui.theme.M3Error
import com.sarthak.olacan.ui.theme.M3OnSurface
import com.sarthak.olacan.ui.theme.M3OnSurfaceVariant
import com.sarthak.olacan.ui.theme.M3Outline
import com.sarthak.olacan.ui.theme.M3OutlineVariant
import com.sarthak.olacan.ui.theme.M3Primary
import com.sarthak.olacan.ui.theme.M3Success
import com.sarthak.olacan.ui.theme.M3SurfaceContainer
import com.sarthak.olacan.ui.theme.M3SurfaceContainerHigh
import com.sarthak.olacan.ui.theme.M3SurfaceContainerLow
import com.sarthak.olacan.ui.theme.M3Warning
import java.util.Locale

@Composable
fun CellVoltageBar(
    channelNumber: Int,
    voltageV: Double,
    isMin: Boolean = false,
    isMax: Boolean = false,
    avgVoltageV: Double = Double.NaN,
    internalResistanceMOhm: Double = Double.NaN,
    isCriticalFloor: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Standard NMC cell voltage range: 3.0V to 4.2V
    val progress = if (voltageV.isNaN()) 0f else {
        ((voltageV - 3.0) / (4.2 - 3.0)).coerceIn(0.0, 1.0).toFloat()
    }
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "cellProgress")

    val isDegraded = !internalResistanceMOhm.isNaN() && internalResistanceMOhm > 3.5
    val isElevatedSag = !internalResistanceMOhm.isNaN() && internalResistanceMOhm in 2.8..3.5
    val isDeviated = isMin || isMax || isDegraded || isElevatedSag

    // Color based on cell health
    val barColor = when {
        voltageV.isNaN() -> M3Outline
        isCriticalFloor || voltageV < 2.75 -> M3Error
        voltageV < 3.3 -> M3Error
        voltageV < 3.6 -> M3Warning
        isDegraded -> M3Error
        isElevatedSag -> M3Warning
        else -> M3Success
    }

    val borderColor = when {
        isCriticalFloor -> M3Error
        isDegraded -> M3Error.copy(alpha = 0.7f)
        isMin -> M3Warning.copy(alpha = 0.6f)
        isMax -> M3Primary.copy(alpha = 0.6f)
        isElevatedSag -> M3Warning.copy(alpha = 0.45f)
        else -> M3OutlineVariant.copy(alpha = 0.3f)
    }

    Surface(
        color = if (isDeviated) M3SurfaceContainerHigh else M3SurfaceContainer,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            width = if (isDeviated) 1.5.dp else 1.dp,
            color = borderColor
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Channel label - Subtle bold on deviated cells
                Text(
                    text = "CH ${String.format(Locale.US, "%02d", channelNumber)}",
                    color = M3OnSurface,
                    fontSize = 12.sp,
                    fontWeight = if (isDeviated) FontWeight.ExtraBold else FontWeight.Bold,
                    fontFamily = AppFont,
                    modifier = Modifier.width(48.dp)
                )

                // High / Low badge
                if (isMin) {
                    Box(
                        modifier = Modifier
                            .background(M3Warning.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text("LOW", color = M3Warning, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = AppFont)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                } else if (isMax) {
                    Box(
                        modifier = Modifier
                            .background(M3Primary.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text("HIGH", color = M3Primary, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = AppFont)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }

                Spacer(modifier = Modifier.weight(1f))

                // Internal Resistance
                val riValue = if (!internalResistanceMOhm.isNaN() && internalResistanceMOhm > 0.0) internalResistanceMOhm else 2.3
                val riColor = if (isDegraded) M3Error else if (isElevatedSag) M3Warning else M3Outline
                Text(
                    text = "${String.format(Locale.US, "%.1f", riValue)} mΩ",
                    color = riColor,
                    fontSize = 11.sp,
                    fontWeight = if (isDeviated) FontWeight.Bold else FontWeight.Medium,
                    fontFamily = AppFont,
                    modifier = Modifier.padding(end = 12.dp)
                )

                // Voltage reading (e.g. 3.942 V) - Subtle bold on deviated cells
                val voltText = if (voltageV.isNaN()) "--- V" else String.format(Locale.US, "%.3f V", voltageV)
                Text(
                    text = voltText,
                    color = barColor,
                    fontSize = if (isDeviated) 13.5.sp else 13.sp,
                    fontWeight = if (isDeviated) FontWeight.ExtraBold else FontWeight.Bold,
                    fontFamily = AppFont
                )
            }

            // Fill Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(M3SurfaceContainerLow, RoundedCornerShape(3.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                        .height(6.dp)
                        .background(barColor, RoundedCornerShape(3.dp))
                )
            }
        }
    }
}
