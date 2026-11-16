package com.sarthak.olacan.ui.components

import com.sarthak.olacan.ui.theme.AppFont
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sarthak.olacan.ui.theme.M3Error
import com.sarthak.olacan.ui.theme.M3ErrorContainer
import com.sarthak.olacan.ui.theme.M3OnPrimaryContainer
import com.sarthak.olacan.ui.theme.M3OnSurface
import com.sarthak.olacan.ui.theme.M3Outline
import com.sarthak.olacan.ui.theme.M3OutlineVariant
import com.sarthak.olacan.ui.theme.M3Primary
import com.sarthak.olacan.ui.theme.M3PrimaryContainer
import com.sarthak.olacan.ui.theme.M3Success
import com.sarthak.olacan.ui.theme.M3Surface
import com.sarthak.olacan.ui.theme.M3SurfaceContainer
import com.sarthak.olacan.ui.theme.M3SurfaceContainerHigh
import com.sarthak.olacan.ui.theme.M3SurfaceContainerLow
import com.sarthak.olacan.ui.theme.M3Warning
import com.sarthak.olacan.ui.theme.M3WarningContainer
import com.sarthak.olacan.util.AppLogEntry
import com.sarthak.olacan.util.AppLogManager
import kotlinx.coroutines.launch

@Composable
fun AppLogViewerDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val logs by AppLogManager.liveLogs.collectAsState()
    val lastCrash by AppLogManager.lastCrashReport.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedLevel by remember { mutableStateOf("ALL") }
    var isExporting by remember { mutableStateOf(false) }

    val filteredLogs = logs.filter { entry ->
        val matchesLevel = when (selectedLevel) {
            "ALL" -> true
            else -> entry.level.equals(selectedLevel, ignoreCase = true)
        }
        val matchesSearch = searchQuery.isBlank() ||
                entry.message.contains(searchQuery, ignoreCase = true) ||
                entry.tag.contains(searchQuery, ignoreCase = true)
        matchesLevel && matchesSearch
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f),
            shape = RoundedCornerShape(20.dp),
            color = M3Surface,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "APP DIAGNOSTIC LOGS & ANALYTICS",
                            color = M3OnSurface,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp,
                            fontFamily = AppFont
                        )
                        Text(
                            text = "${filteredLogs.size} logs • Real-time telemetry & daemon events",
                            color = M3Outline,
                            fontSize = 10.5.sp,
                            fontFamily = AppFont
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = M3Outline)
                    }
                }

                // Previous Crash Alert Banner
                val crashReport = lastCrash
                if (crashReport != null) {
                    Surface(
                        color = M3ErrorContainer.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, M3Error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = M3Error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Crash Captured from Previous Session",
                                        color = M3Error,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = AppFont
                                    )
                                }
                                Text(
                                    text = "Dismiss",
                                    color = M3Outline,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable {
                                        AppLogManager.clearCrashReport()
                                    }
                                )
                            }
                            Text(
                                text = crashReport.lineSequence().filter { it.startsWith("Exception") || it.startsWith("Crash Time") || it.contains("at com.sarthak") }.take(3).joinToString("\n"),
                                color = M3OnSurface,
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 4
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    onClick = {
                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("OlaCrashReport", crashReport)
                                        clipboard?.setPrimaryClip(clip)
                                        android.widget.Toast.makeText(context, "Full crash trace copied to clipboard!", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = M3Error,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier.height(26.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("Copy Full Crash Trace", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Filter Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("ALL", "ERROR", "WARN", "CAN", "GRPC", "INFO").forEach { level ->
                        val isSel = selectedLevel == level
                        val bg = if (isSel) M3PrimaryContainer else M3SurfaceContainerHigh
                        val textColor = if (isSel) M3OnPrimaryContainer else M3Outline
                        Surface(
                            color = bg,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (isSel) M3Primary else M3OutlineVariant.copy(alpha = 0.3f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(vertical = 5.dp)
                                    .clickable { selectedLevel = level },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = level,
                                    color = textColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = AppFont
                                )
                            }
                        }
                    }
                }

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filter logs by tag or keyword...", fontSize = 11.sp, color = M3Outline) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = M3Outline, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = M3SurfaceContainerLow,
                        unfocusedContainerColor = M3SurfaceContainerLow,
                        focusedBorderColor = M3Primary,
                        unfocusedBorderColor = M3OutlineVariant.copy(alpha = 0.5f),
                        focusedTextColor = M3OnSurface,
                        unfocusedTextColor = M3OnSurface
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                // Log Entries List
                Surface(
                    color = M3SurfaceContainerLow,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (filteredLogs.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No matching log entries found.", color = M3Outline, fontSize = 11.sp)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(filteredLogs) { log ->
                                LogItemRow(log)
                            }
                        }
                    }
                }

                // Action Buttons Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Share Diagnostic Log File Button
                    Button(
                        onClick = {
                            if (!isExporting) {
                                isExporting = true
                                scope.launch {
                                    try {
                                        AppLogManager.shareDiagnosticReport(context)
                                    } finally {
                                        isExporting = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = M3PrimaryContainer,
                            contentColor = M3OnPrimaryContainer
                        )
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = M3Primary, strokeWidth = 2.dp)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                Text("Share Diagnostic Log", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Copy to Clipboard Button
                    OutlinedButton(
                        onClick = { AppLogManager.copyLogsToClipboard(context) },
                        modifier = Modifier.height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(14.dp), tint = M3Primary)
                    }

                    // Clear Logs Button
                    OutlinedButton(
                        onClick = { AppLogManager.clearLogs() },
                        modifier = Modifier.height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear", modifier = Modifier.size(14.dp), tint = M3Error)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogItemRow(entry: AppLogEntry) {
    val (badgeBg, badgeColor) = when (entry.level) {
        "ERROR" -> Pair(M3ErrorContainer, M3Error)
        "WARN" -> Pair(M3WarningContainer, M3Warning)
        "CAN" -> Pair(M3PrimaryContainer, M3Primary)
        "GRPC" -> Pair(M3SurfaceContainerHigh, M3Success)
        else -> Pair(M3SurfaceContainer, M3Outline)
    }

    Surface(
        color = M3Surface,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(0.5.dp, M3OutlineVariant.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(badgeBg, RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = entry.level,
                        color = badgeColor,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AppFont
                    )
                }

                Text(
                    text = entry.formattedTime,
                    color = M3Outline,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )

                Text(
                    text = entry.tag,
                    color = M3Primary,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = AppFont
                )
            }

            Text(
                text = entry.message,
                color = M3OnSurface,
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 14.sp
            )
        }
    }
}
