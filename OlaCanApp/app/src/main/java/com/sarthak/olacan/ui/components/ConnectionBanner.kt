package com.sarthak.olacan.ui.components

import com.sarthak.olacan.ui.theme.AppFont
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarthak.olacan.model.StreamStats
import com.sarthak.olacan.ui.theme.M3Error
import com.sarthak.olacan.ui.theme.M3OnSurface
import com.sarthak.olacan.ui.theme.M3Outline
import com.sarthak.olacan.ui.theme.M3OutlineVariant
import com.sarthak.olacan.ui.theme.M3Primary
import com.sarthak.olacan.ui.theme.M3Success
import com.sarthak.olacan.ui.theme.M3SurfaceContainer
import com.sarthak.olacan.ui.theme.M3Warning
import java.util.Locale

@Composable
fun ConnectionBanner(
    stats: StreamStats,
    onDisconnect: () -> Unit,
    onNavigateConnect: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        color = M3SurfaceContainer,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.4f)),
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (!stats.isConnected && onNavigateConnect != null) {
                    Modifier.clickable { onNavigateConnect() }
                } else Modifier
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (stats.isConnected) {
                            if (stats.isAwake) M3Success else M3Warning
                        } else M3Warning,
                        shape = CircleShape
                    )
            )
            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (stats.isConnected) stats.getEffectiveVehicleName() else "Vehicle Offline",
                        color = M3OnSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AppFont
                    )
                    if (stats.isConnected) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(
                                    (if (stats.isAwake) M3Success else M3Warning).copy(alpha = 0.15f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = if (stats.isAwake) "AWAKE" else "STANDBY",
                                color = if (stats.isAwake) M3Success else M3Warning,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = AppFont
                            )
                        }
                    }
                }

                Text(
                    text = if (stats.isConnected) {
                        "CAN Stream Active • Ping: ${stats.latencyMs}ms"
                    } else if (onNavigateConnect != null) {
                        "Tap to select scooter and connect"
                    } else {
                        stats.statusMessage
                    },
                    color = M3Outline,
                    fontSize = 11.sp,
                    fontFamily = AppFont
                )
            }

            if (stats.isConnected) {
                OutlinedButton(
                    onClick = onDisconnect,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, M3Error.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = M3Error),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(text = "Disconnect", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            } else if (onNavigateConnect != null) {
                OutlinedButton(
                    onClick = onNavigateConnect,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, M3Primary.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = M3Primary),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(text = "Connect", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
