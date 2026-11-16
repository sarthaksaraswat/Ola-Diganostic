package com.sarthak.olacan.ui.screens

import com.sarthak.olacan.ui.theme.AppFont
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sarthak.olacan.ui.components.AppLogViewerDialog
import com.sarthak.olacan.util.AppLogManager
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarthak.olacan.model.CanFrame
import com.sarthak.olacan.ui.components.ConnectionBanner
import com.sarthak.olacan.ui.components.RideDiagnosticReportDialog
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
import com.sarthak.olacan.util.HapticHelper
import com.sarthak.olacan.util.PdfReportGenerator
import com.sarthak.olacan.viewmodel.TelemetryViewModel
import java.util.Locale

@Composable
fun RawCanScreen(
    viewModel: TelemetryViewModel,
    onNavigateConnect: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val view = LocalView.current

    val stats by viewModel.streamStats.collectAsState()
    val frames by viewModel.recentFrames.collectAsState()

    val isRecording by viewModel.isRecording.collectAsState()
    val recordingDuration by viewModel.recordingDurationSec.collectAsState()
    val recordedFrames by viewModel.recordedFramesCount.collectAsState()
    val recordedAlerts by viewModel.recordedAlertsCount.collectAsState()
    val recordedSizeFormatted by viewModel.recordedSizeFormatted.collectAsState()
    val lastSavedLog by viewModel.lastSavedLog.collectAsState()

    val currentRideReport by viewModel.currentRideReport.collectAsState()
    val isAnalyzingLog by viewModel.isAnalyzingLog.collectAsState()
    val logAnalysisProgress by viewModel.logAnalysisProgress.collectAsState()
    val logAnalysisFramesCount by viewModel.logAnalysisFramesCount.collectAsState()

    var isStreamPaused by remember { mutableStateOf(false) }
    var filterText by remember { mutableStateOf("") }
    var showRideReportDialog by remember { mutableStateOf(false) }
    var showAppLogDialog by remember { mutableStateOf(false) }
    var isSharingAppLogs by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val appLogs by AppLogManager.liveLogs.collectAsState()

    // Automatically open report dialog once log analysis is ready
    LaunchedEffect(currentRideReport) {
        if (currentRideReport != null) {
            showRideReportDialog = true
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.analyzeLogFile(context, uri)
        }
    }

    val activeReport = currentRideReport
    if (showRideReportDialog && activeReport != null) {
        RideDiagnosticReportDialog(
            report = activeReport,
            onDismiss = {
                showRideReportDialog = false
                viewModel.clearRideReport()
            }
        )
    }

    // Modal Progress Dialog with Live ProgressBar & Frames Counter (Restored from v1)
    if (isAnalyzingLog) {
        Dialog(
            onDismissRequest = { /* Modal while analyzing */ },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = M3SurfaceContainer,
                border = BorderStroke(1.dp, M3Primary.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = M3Primary,
                            strokeWidth = 2.5.dp
                        )
                        Column {
                            Text(
                                text = "Analyzing CAN Log",
                                color = M3OnSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Parsing MoveOS frames & telemetry...",
                                color = M3Outline,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Progress: $logAnalysisProgress%",
                                color = M3Primary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${String.format(Locale.US, "%,d", logAnalysisFramesCount)} frames",
                                color = M3Outline,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        LinearProgressIndicator(
                            progress = { (logAnalysisProgress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = M3Primary,
                            trackColor = M3SurfaceContainerHigh
                        )
                    }
                }
            }
        }
    }

    if (showAppLogDialog) {
        AppLogViewerDialog(onDismiss = { showAppLogDialog = false })
    }

    val filteredFrames = remember(frames, filterText) {
        if (filterText.isBlank()) frames else {
            val q = filterText.trim().lowercase(Locale.ROOT)
            frames.filter { frame ->
                frame.idHex.lowercase(Locale.ROOT).contains(q) ||
                        frame.id.toString().contains(q) ||
                        frame.dataHex.lowercase(Locale.ROOT).contains(q) ||
                        getCanMeaning(frame).title.lowercase(Locale.ROOT).contains(q) ||
                        getCanMeaning(frame).subsystem.lowercase(Locale.ROOT).contains(q) ||
                        getCanMeaning(frame).description.lowercase(Locale.ROOT).contains(q)
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(M3Surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // -------------------------------------------------------------
        // 1. STREAMING GZIP RECORDER (TOP PRIORITY)
        // -------------------------------------------------------------
        item {
            Surface(
                color = M3SurfaceContainer,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, if (isRecording) M3Error.copy(alpha = 0.5f) else M3OutlineVariant.copy(alpha = 0.4f)),
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
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(if (isRecording) M3Error else M3Outline, CircleShape)
                            )
                            Column {
                                Text(
                                    text = if (isRecording) "RECORDING CAN STREAM" else "CAN RECORDER",
                                    color = if (isRecording) M3Error else M3OnSurface,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.6.sp,
                                    fontFamily = AppFont,
                                    maxLines = 1
                                )
                                Text(
                                    text = if (isRecording) "Streaming to compressed gzip log" else "Gzip-compressed raw telemetry capture",
                                    color = M3Outline,
                                    fontSize = 9.5.sp,
                                    fontFamily = AppFont,
                                    maxLines = 1
                                )
                            }
                        }

                        // Toggle Record Button (Sleek and Compact)
                        Button(
                            onClick = {
                                HapticHelper.performClick(view)
                                viewModel.toggleRecording(context)
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRecording) M3ErrorContainer else M3PrimaryContainer,
                                contentColor = if (isRecording) M3OnErrorContainer else M3OnPrimaryContainer
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = if (isRecording) "Stop" else "Record",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = AppFont,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Recording Stats Grid: Elapsed, Messages, Size, Faults
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Elapsed
                        val mins = recordingDuration / 60
                        val secs = recordingDuration % 60
                        StatBoxItem("ELAPSED", String.format(Locale.US, "%02d:%02d", mins, secs), M3OnSurface, modifier = Modifier.weight(1f))
                        StatBoxItem("FRAMES", "$recordedFrames", M3Success, modifier = Modifier.weight(1f))
                        StatBoxItem("SIZE", recordedSizeFormatted, M3Primary, modifier = Modifier.weight(1f))
                        StatBoxItem("FAULTS", "$recordedAlerts", if (recordedAlerts > 0) M3Error else M3OnSurface, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // 2. SAVED LOCAL LOG ARCHIVE & OFFLINE ANALYZER (MOVED ABOVE CAN ID LIST)
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "RECORDED LOGS IN LOCAL STORAGE",
                            color = M3Outline,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            fontFamily = AppFont
                        )
                        Text(
                            text = "Downloads/CANLogs/",
                            color = M3Outline,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Open File & Analyze
                    Surface(
                        color = M3SurfaceContainerHigh,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                HapticHelper.performClick(view)
                                filePickerLauncher.launch(arrayOf("*/*"))
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = M3Primary, modifier = Modifier.size(18.dp))
                                Column {
                                    Text("Analyze Saved Ride Log (.csv / .gz)", color = M3OnSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text("Extract cell imbalance, motor efficiency & faults", color = M3Outline, fontSize = 10.sp)
                                }
                            }

                            if (isAnalyzingLog) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = M3Primary, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Assessment, contentDescription = null, tint = M3Primary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // Inline Log Analysis Progress Bar
                    AnimatedVisibility(visible = isAnalyzingLog) {
                        Surface(
                            color = M3SurfaceContainerHigh,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, M3Primary.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
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
                                    Text(
                                        text = "Analyzing Ride Log: $logAnalysisProgress%",
                                        color = M3Primary,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${String.format(Locale.US, "%,d", logAnalysisFramesCount)} frames",
                                        color = M3Outline,
                                        fontSize = 10.5.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { (logAnalysisProgress / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp),
                                    color = M3Primary,
                                    trackColor = M3SurfaceContainerLow
                                )
                            }
                        }
                    }

                    if (lastSavedLog != null) {
                        Surface(
                            color = M3SuccessContainer.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, M3Success.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val savedInfo = lastSavedLog
                            if (savedInfo != null) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = M3Success, modifier = Modifier.size(14.dp))
                                    Text(
                                        text = "Saved: ${savedInfo.fileName} (${savedInfo.fileSizeFormatted}, ${savedInfo.totalFrames} frames)",
                                        color = M3Success,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // 3. APP DIAGNOSTIC LOG ANALYTICS & LOGCAT EXPORT CARD
        // -------------------------------------------------------------
        item {
            Surface(
                color = M3SurfaceContainer,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                    .size(32.dp)
                                    .background(M3PrimaryContainer, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Terminal,
                                    contentDescription = null,
                                    tint = M3OnPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "APP LOG ANALYTICS & TRACE",
                                    color = M3OnSurface,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.6.sp,
                                    fontFamily = AppFont,
                                    maxLines = 1
                                )
                                Text(
                                    text = "Record & share app diagnostics, gRPC traces & logcat",
                                    color = M3Outline,
                                    fontSize = 10.sp,
                                    fontFamily = AppFont,
                                    maxLines = 1
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .background(M3SurfaceContainerHigh, RoundedCornerShape(4.dp))
                                .border(1.dp, M3OutlineVariant.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${appLogs.size} EVENTS",
                                color = M3Primary,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = AppFont,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    // Console preview box (latest 3 events)
                    Surface(
                        color = M3SurfaceContainerLow,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                HapticHelper.performClick(view)
                                showAppLogDialog = true
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            val previewLogs = appLogs.take(3)
                            if (previewLogs.isEmpty()) {
                                Text("No application logs recorded yet.", color = M3Outline, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            } else {
                                previewLogs.forEach { logEntry ->
                                    val logColor = when (logEntry.level) {
                                        "ERROR" -> M3Error
                                        "WARN" -> M3Warning
                                        "GRPC" -> M3Success
                                        else -> M3OnSurfaceVariant
                                    }
                                    Text(
                                        text = "[${logEntry.formattedTime}] [${logEntry.level}] ${logEntry.tag}: ${logEntry.message}",
                                        color = logColor,
                                        fontSize = 9.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    // Action buttons: Share Log Report & Open Live Viewer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                HapticHelper.performClick(view)
                                if (!isSharingAppLogs) {
                                    isSharingAppLogs = true
                                    scope.launch {
                                        try {
                                            AppLogManager.shareDiagnosticReport(context)
                                        } finally {
                                            isSharingAppLogs = false
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = M3PrimaryContainer,
                                contentColor = M3OnPrimaryContainer
                            )
                        ) {
                            if (isSharingAppLogs) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = M3Primary, strokeWidth = 2.dp)
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Text("Share Diagnostic Log", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                HapticHelper.performClick(view)
                                showAppLogDialog = true
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = M3Primary
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Assessment, contentDescription = null, modifier = Modifier.size(14.dp))
                                Text("View Live Console", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // 4. LIVE CAN INSPECTOR CONTROLS & BUFFER STREAM (NORMAL MEANING VIEW)
        // -------------------------------------------------------------
        item {
            Surface(
                color = M3SurfaceContainer,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = filterText,
                            onValueChange = { filterText = it },
                            placeholder = { Text("Filter meaning or ID (e.g. throttle, bms, 113, temp)...", fontSize = 11.sp, color = M3Outline) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = M3SurfaceContainerHigh,
                                unfocusedContainerColor = M3SurfaceContainerHigh,
                                focusedBorderColor = M3Primary,
                                unfocusedBorderColor = M3OutlineVariant,
                                focusedTextColor = M3OnSurface,
                                unfocusedTextColor = M3OnSurface
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )

                        // Freeze / Resume Stream button
                        Surface(
                            color = M3SurfaceContainerHigh,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.clickable {
                                HapticHelper.performClick(view)
                                isStreamPaused = !isStreamPaused
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = if (isStreamPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = null,
                                    tint = M3Primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (isStreamPaused) "Resume" else "Freeze",
                                    color = M3Primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = AppFont
                                )
                            }
                        }
                    }

                    // Buffer Table Header with Normal Meaning tag
                    Surface(
                        color = M3SurfaceContainerHigh,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "LIVE CAN BUS STREAM (${filteredFrames.size} FRAMES)",
                                color = M3Outline,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                fontFamily = AppFont
                            )
                            Text(
                                text = "NORMAL MEANING VIEW",
                                color = M3Primary,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                fontFamily = AppFont
                            )
                        }
                    }
                }
            }
        }

        // Live Frames list items (positional rendering, Normal Meaning View, 100% crash-proof)
        if (filteredFrames.isEmpty()) {
            item {
                Surface(
                    color = M3SurfaceContainer,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = null,
                            tint = M3Outline,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = if (filterText.isNotBlank()) "No frames match \"$filterText\"" else "Waiting for CAN frames...",
                            color = M3OnSurfaceVariant,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = AppFont,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = if (filterText.isNotBlank()) "Try clearing your search query or switching filter parameters" else "Connect to vehicle or wake scooter to stream real-time CAN bus telemetry",
                            color = M3Outline,
                            fontSize = 11.sp,
                            fontFamily = AppFont,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else if (!isStreamPaused) {
            items(filteredFrames.take(40)) { frame ->
                CanFrameRowItem(frame = frame)
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatBoxItem(
    title: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = M3SurfaceContainerHigh,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.25f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, color = M3Outline, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, fontFamily = AppFont)
            Text(value, color = valueColor, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, fontFamily = AppFont)
        }
    }
}

private data class CanMeaning(
    val title: String,
    val subsystem: String,
    val subsystemColor: Color,
    val description: String,
    val liveValue: String? = null
)

private fun u8(d: ByteArray, i: Int): Int =
    if (i < d.size) d[i].toInt() and 0xFF else 0

private fun le16(d: ByteArray, i: Int): Int =
    if (i + 1 < d.size) (d[i].toInt() and 0xFF) or ((d[i + 1].toInt() and 0xFF) shl 8) else 0

private fun le32(d: ByteArray, i: Int): Long =
    if (i + 3 < d.size) {
        ((d[i].toLong() and 0xFFL)) or
                ((d[i + 1].toLong() and 0xFFL) shl 8) or
                ((d[i + 2].toLong() and 0xFFL) shl 16) or
                ((d[i + 3].toLong() and 0xFFL) shl 24)
    } else 0L

private fun getCanMeaning(frame: CanFrame): CanMeaning {
    val d = frame.data
    return when (frame.id) {
        0x064 -> CanMeaning(
            title = "Node Presence & CAN Alive",
            subsystem = "CAN NETWORK",
            subsystemColor = M3Primary,
            description = "ECU alive status & network presence (BMS, MCU, ESCL)"
        )
        0x110 -> {
            val seq = u8(d, 0)
            val ascii = try {
                if (d.size > 1) String(d.sliceArray(1 until d.size), java.nio.charset.StandardCharsets.US_ASCII).filter { it.isLetterOrDigit() } else ""
            } catch (e: Exception) { "" }
            CanMeaning(
                title = "Battery Serial Part #$seq",
                subsystem = "BMS IDENT",
                subsystemColor = M3Success,
                description = "Battery hardware serial number chunk from BMS EEPROM",
                liveValue = if (ascii.isNotEmpty()) "Serial Seq $seq: $ascii" else null
            )
        }
        0x113 -> {
            val rawV = if (d.size >= 3) le16(d, 1) * 0.01 else Double.NaN
            val modeVal = if (d.size >= 6) (u8(d, 5) shr 1) and 0x0F else -1
            val modeStr = when (modeVal) {
                0 -> "Parked"
                2 -> "Normal"
                3 -> "Sport"
                4 -> "Custom"
                5, 10, 13 -> "Reverse"
                6 -> "Eco"
                7 -> "Hyper"
                else -> "Standby"
            }
            val throttlePct = if (!rawV.isNaN() && rawV > 0.95) (((rawV - 0.95) / (4.05 - 0.95)) * 100.0).coerceIn(0.0, 100.0) else 0.0
            CanMeaning(
                title = "Throttle & Drive State",
                subsystem = "POWERTRAIN",
                subsystemColor = M3Warning,
                description = "Throttle sensor voltage, drive mode & regen state",
                liveValue = "Mode: $modeStr | Throttle: ${String.format(Locale.US, "%.0f", throttlePct)}% (${String.format(Locale.US, "%.2f", rawV)}V)"
            )
        }
        0x120 -> {
            val rawSoc = if (d.size >= 4) le16(d, 2) * 0.01 else Double.NaN
            val current = if (d.size >= 2) le16(d, 0) * 0.01 - 300.0 else Double.NaN
            CanMeaning(
                title = "BMS State of Charge (SOC)",
                subsystem = "BATTERY SOC",
                subsystemColor = M3Success,
                description = "Battery pack true chemical SOC% & real-time current draw",
                liveValue = "SOC: ${String.format(Locale.US, "%.1f", rawSoc)}% | Current: ${String.format(Locale.US, "%.1f", current)}A"
            )
        }
        0x122 -> CanMeaning(
            title = "BMS Safety & Protection Flags",
            subsystem = "BMS ALERT",
            subsystemColor = M3Error,
            description = "Cell voltage sag derate, load imbalance, high discharge flags"
        )
        0x130 -> {
            val ccl = if (d.size >= 2) le16(d, 0) * 0.01 else 0.0
            val dcl = if (d.size >= 4) le16(d, 2) * 0.01 else 0.0
            CanMeaning(
                title = "BMS Current Limits (CCL / DCL)",
                subsystem = "BMS LIMITS",
                subsystemColor = M3Primary,
                description = "Maximum allowed charge current (CCL) and discharge current (DCL)",
                liveValue = "Charge Limit: ${ccl.toInt()}A | Discharge Limit: ${dcl.toInt()}A"
            )
        }
        0x140 -> {
            val current = if (d.size >= 6) le16(d, 4) * 0.01 - 300.0 else Double.NaN
            CanMeaning(
                title = "Battery Pack Current Draw",
                subsystem = "POWERTRAIN",
                subsystemColor = M3Warning,
                description = "Net current flowing out of pack (+) or regen charging into pack (-)",
                liveValue = "Net Current: ${String.format(Locale.US, "%.1f", current)} A"
            )
        }
        0x150 -> {
            val volts = if (d.size >= 2) le16(d, 0) * 0.001 else Double.NaN
            val soh = if (d.size >= 6) u8(d, 5) else 0
            CanMeaning(
                title = "Pack Bus Voltage & SOH",
                subsystem = "BATTERY",
                subsystemColor = M3Success,
                description = "Total battery terminal potential (V) and State of Health (%)",
                liveValue = "${String.format(Locale.US, "%.2f", volts)} V | Pack SOH: $soh%"
            )
        }
        0x15B -> {
            val wSpd = if (d.size >= 2) le16(d, 0) * 0.1 else 0.0
            val amb = if (d.size >= 4) le16(d, 2) * 0.1 else Double.NaN
            CanMeaning(
                title = "Wheel Speed & Ambient Temp",
                subsystem = "CHASSIS",
                subsystemColor = M3Primary,
                description = "Rear traction wheel speed sensor & outside ambient temperature",
                liveValue = "Wheel Speed: ${String.format(Locale.US, "%.1f", wSpd)} km/h | Amb: ${String.format(Locale.US, "%.1f", amb)}°C"
            )
        }
        0x160 -> {
            fun decT(raw: Int): Double = if (raw in 151..2499) raw * 0.1 - 50.0 else Double.NaN
            val eff = if (d.size >= 3) decT(le16(d, 1)) else Double.NaN
            val min = if (d.size >= 5) decT(le16(d, 3)) else Double.NaN
            val max = if (d.size >= 7) decT(le16(d, 5)) else Double.NaN
            CanMeaning(
                title = "Battery Thermistor Envelope",
                subsystem = "THERMAL",
                subsystemColor = M3Warning,
                description = "Core battery temperature matrix: effective, minimum & maximum",
                liveValue = "Effective: ${String.format(Locale.US, "%.1f", eff)}°C [Min: ${String.format(Locale.US, "%.1f", min)}°C, Max: ${String.format(Locale.US, "%.1f", max)}°C]"
            )
        }
        0x170 -> {
            val minMv = if (d.size >= 4) le16(d, 2) else 0
            val maxMv = if (d.size >= 6) le16(d, 4) else 0
            val delta = maxMv - minMv
            CanMeaning(
                title = "Cell Voltage Extremes (Min/Max)",
                subsystem = "BATTERY CELLS",
                subsystemColor = M3Success,
                description = "Lowest and highest single cell voltages across all 14 cells",
                liveValue = "Min: ${minMv}mV | Max: ${maxMv}mV (Imbalance: ${delta}mV)"
            )
        }
        0x1A4 -> CanMeaning(
            title = "Pack Thermistors 1 to 4",
            subsystem = "THERMAL",
            subsystemColor = M3Warning,
            description = "NTC thermal sensors embedded in battery module rows 1 to 4"
        )
        0x1A5 -> {
            val c1 = if (d.size >= 2) le16(d, 0) else 0
            val c2 = if (d.size >= 4) le16(d, 2) else 0
            val c3 = if (d.size >= 6) le16(d, 4) else 0
            val c4 = if (d.size >= 8) le16(d, 6) else 0
            CanMeaning(
                title = "Cell Voltages Brick 1 (Cells 1-4)",
                subsystem = "BATTERY CELLS",
                subsystemColor = M3Success,
                description = "Individual cell series voltages for cells 1, 2, 3 and 4",
                liveValue = "C1: ${c1}mV | C2: ${c2}mV | C3: ${c3}mV | C4: ${c4}mV"
            )
        }
        0x1A6 -> {
            val c5 = if (d.size >= 2) le16(d, 0) else 0
            val c6 = if (d.size >= 4) le16(d, 2) else 0
            val c7 = if (d.size >= 6) le16(d, 4) else 0
            val c8 = if (d.size >= 8) le16(d, 6) else 0
            CanMeaning(
                title = "Cell Voltages Brick 2 (Cells 5-8)",
                subsystem = "BATTERY CELLS",
                subsystemColor = M3Success,
                description = "Individual cell series voltages for cells 5, 6, 7 and 8",
                liveValue = "C5: ${c5}mV | C6: ${c6}mV | C7: ${c7}mV | C8: ${c8}mV"
            )
        }
        0x1A7 -> CanMeaning(
            title = "Pack Thermistors 5 to 6 & Gradient",
            subsystem = "THERMAL",
            subsystemColor = M3Warning,
            description = "Cell module temperature probes 5 & 6 and pack thermal gradient"
        )
        0x1A8 -> CanMeaning(
            title = "BMS Auxiliary Sensors (PDU / AFE)",
            subsystem = "THERMAL",
            subsystemColor = M3Warning,
            description = "Power Distribution Unit & Analog Front End chip temperatures"
        )
        0x1A9 -> {
            val c9 = if (d.size >= 2) le16(d, 0) else 0
            val c10 = if (d.size >= 4) le16(d, 2) else 0
            val c11 = if (d.size >= 6) le16(d, 4) else 0
            val c12 = if (d.size >= 8) le16(d, 6) else 0
            CanMeaning(
                title = "Cell Voltages Brick 3 (Cells 9-12)",
                subsystem = "BATTERY CELLS",
                subsystemColor = M3Success,
                description = "Individual cell series voltages for cells 9, 10, 11 and 12",
                liveValue = "C9: ${c9}mV | C10: ${c10}mV | C11: ${c11}mV | C12: ${c12}mV"
            )
        }
        0x1AA -> {
            val c13 = if (d.size >= 2) le16(d, 0) else 0
            val c14 = if (d.size >= 4) le16(d, 2) else 0
            CanMeaning(
                title = "Cell Voltages Brick 4 (Cells 13-14)",
                subsystem = "BATTERY CELLS",
                subsystemColor = M3Success,
                description = "Individual cell series voltages for top cells 13 and 14",
                liveValue = "C13: ${c13}mV | C14: ${c14}mV"
            )
        }
        0x210 -> CanMeaning(
            title = "Throttle Sensor Plausibility",
            subsystem = "MCU INPUTS",
            subsystemColor = M3Primary,
            description = "Twist grip dual potentiometer deviation & correlation status"
        )
        0x223 -> {
            val rawT = if (d.size >= 2) le16(d, 0) else 0
            val tNm = if (rawT >= 1000) (rawT - 1000) * 0.1 else 0.0
            val phaseA = if (d.size >= 7) u8(d, 6) else 0
            CanMeaning(
                title = "Motor Torque & Phase Current",
                subsystem = "POWERTRAIN",
                subsystemColor = M3Warning,
                description = "Shaft torque output (Nm) & motor AC stator phase current (A)",
                liveValue = "Torque: ${String.format(Locale.US, "%.1f", tNm)} Nm | Phase Current: ${phaseA} A"
            )
        }
        0x313 -> {
            val spd = if (d.size >= 2) le16(d, 0) * 0.1 else 0.0
            val mcuT = if (d.size >= 4) le16(d, 2) * 0.01 else 0.0
            val motT = if (d.size >= 6) le16(d, 4) * 0.01 else 0.0
            val rpm = if (d.size >= 8) le16(d, 6) else 0
            CanMeaning(
                title = "Motor & Inverter Core Telemetry",
                subsystem = "MOTOR & MCU",
                subsystemColor = M3Warning,
                description = "Speed (km/h), motor stator temp, MCU inverter temp & shaft RPM",
                liveValue = "${String.format(Locale.US, "%.0f", spd)} km/h | Mot: ${motT.toInt()}°C | Inv: ${mcuT.toInt()}°C | $rpm RPM"
            )
        }
        0x333 -> CanMeaning(
            title = "MCU Inverter Fault Register",
            subsystem = "MCU FAULTS",
            subsystemColor = M3Error,
            description = "Overcurrent, overvoltage, motor overtemp and derate flags"
        )
        0x352 -> {
            val dSoc = if (d.size >= 1) u8(d, 0) else 0
            CanMeaning(
                title = "MoveOS Cluster Display SOC",
                subsystem = "DASHBOARD",
                subsystemColor = M3Primary,
                description = "State of Charge percentage displayed on touchscreen dashboard",
                liveValue = "Cluster Display SOC: $dSoc%"
            )
        }
        0x353 -> CanMeaning(
            title = "Cluster Telltales & Safety State",
            subsystem = "DASHBOARD",
            subsystemColor = M3Primary,
            description = "Side stand sensor, master warning triangle, service spanner"
        )
        0x356 -> {
            val rawM = if (d.size >= 4) le32(d, 0) else 0L
            val odo = if (rawM in 0..1000000000L) rawM / 1000.0 else 0.0
            CanMeaning(
                title = "Vehicle Lifetime Odometer",
                subsystem = "DASHBOARD",
                subsystemColor = M3Primary,
                description = "Cumulative distance logged in non-volatile vehicle memory",
                liveValue = "Total Odometer: ${String.format(Locale.US, "%,.1f", odo)} km"
            )
        }
        0x64B -> {
            fun decT(raw: Int): Double = if (raw in 151..2499) raw * 0.1 - 50.0 else Double.NaN
            val vcuT = if (d.size >= 2) decT(le16(d, 0)) else Double.NaN
            val somT = if (d.size >= 4) decT(le16(d, 2)) else Double.NaN
            CanMeaning(
                title = "VCU Board & SoM Temperature",
                subsystem = "VCU SYSTEM",
                subsystemColor = M3Primary,
                description = "Main vehicle computer motherboard & Snapdragon SoM CPU temps",
                liveValue = "VCU: ${String.format(Locale.US, "%.1f", vcuT)}°C | SoM: ${String.format(Locale.US, "%.1f", somT)}°C"
            )
        }
        0x659 -> CanMeaning(
            title = "Battery Brick SOH Health",
            subsystem = "BATTERY HEALTH",
            subsystemColor = M3Success,
            description = "Individual battery brick state of health wear index percentages"
        )
        0x670 -> CanMeaning(
            title = "VCU Diagnostic Status & Limp",
            subsystem = "VCU DIAG",
            subsystemColor = M3Error,
            description = "Emergency drive stop command, limp-home mode, PDU status"
        )
        0x682 -> CanMeaning(
            title = "IMU 6-Axis Vehicle Dynamics",
            subsystem = "CHASSIS",
            subsystemColor = M3Primary,
            description = "Inertial measurement gyro rates, pitch, roll and lean angle"
        )
        0x683 -> CanMeaning(
            title = "Safety Fall & Tow Interlocks",
            subsystem = "SAFETY",
            subsystemColor = M3Error,
            description = "Fall/crash sensor trigger, unauthorized tow alert, hill hold"
        )
        0x685 -> CanMeaning(
            title = "MoveOS Drive State Machine",
            subsystem = "MOVEOS",
            subsystemColor = M3Primary,
            description = "Central vehicle state: Park, Ride, Reverse, Charging"
        )
        0x689 -> CanMeaning(
            title = "VCU Electronic Serial Number",
            subsystem = "VCU IDENT",
            subsystemColor = M3Primary,
            description = "Central Vehicle Control Unit hardware serial identification"
        )
        0x6B0 -> CanMeaning(
            title = "Critical Safety & Thermal Alarm",
            subsystem = "SAFETY",
            subsystemColor = M3Error,
            description = "Emergency thermal protection cutoff & tamper alert triggers"
        )
        0x6D8 -> {
            val cyc = if (d.size >= 4) le32(d, 0) else 0L
            CanMeaning(
                title = "Battery Hardware Cycles",
                subsystem = "BATTERY LIFE",
                subsystemColor = M3Success,
                description = "True physical battery pack cycles from BMS EEPROM & DC fast charges",
                liveValue = if (cyc in 1..10000) "Pack Cycles: $cyc" else null
            )
        }
        0x6DA -> {
            val ah = if (d.size >= 4) le32(d, 0) else 0L
            CanMeaning(
                title = "Lifetime Ampere-Hour Throughput",
                subsystem = "BATTERY LIFE",
                subsystemColor = M3Success,
                description = "Total cumulative battery charge throughput (Ah) processed",
                liveValue = if (ah > 0) "Throughput: ${String.format(Locale.US, "%,d", ah)} Ah" else null
            )
        }
        0x6E0 -> {
            val chassisC = if (d.size >= 4) le16(d, 2) else 0
            val acCount = if (d.size >= 6) le16(d, 4) else 0
            CanMeaning(
                title = "Vehicle Chassis Cycles & AC Charges",
                subsystem = "VEHICLE LIFE",
                subsystemColor = M3Primary,
                description = "Lifetime vehicle chassis cycles & AC home charging plug-in count",
                liveValue = "Chassis Cycles: $chassisC | AC Sessions: $acCount"
            )
        }
        0x6F4 -> {
            val eco = if (d.size >= 2) le16(d, 0) else 0
            val norm = if (d.size >= 4) le16(d, 2) else 0
            val sport = if (d.size >= 6) le16(d, 4) else 0
            val hyper = if (d.size >= 8) le16(d, 6) else 0
            CanMeaning(
                title = "MoveOS Range per Ride Mode",
                subsystem = "MOVEOS RANGE",
                subsystemColor = M3Primary,
                description = "Calculated remaining range estimates across all MoveOS ride modes",
                liveValue = "Eco: ${eco}km | Normal: ${norm}km | Sport: ${sport}km | Hyper: ${hyper}km"
            )
        }
        else -> {
            val sub = when (frame.id) {
                in 0x000..0x0FF -> "NETWORK" to M3Primary
                in 0x100..0x1FF -> "POWERTRAIN" to M3Warning
                in 0x200..0x2FF -> "TRACTION" to M3Success
                in 0x300..0x3FF -> "MCU / DASH" to M3Primary
                in 0x600..0x6FF -> "MOVEOS" to M3Primary
                else -> "CAN BUS" to M3Outline
            }
            CanMeaning(
                title = "CAN Message ${frame.idHex}",
                subsystem = sub.first,
                subsystemColor = sub.second,
                description = "Standard frame broadcast on Ola high-speed CAN network",
                liveValue = null
            )
        }
    }
}

@Composable
private fun CanFrameRowItem(frame: CanFrame) {
    val meaning = remember(frame.id, frame.data) { getCanMeaning(frame) }
    Surface(
        color = M3SurfaceContainerLow,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Row 1: Subsystem Badge + Title + CAN ID Tag
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
                    // Subsystem Pill
                    Box(
                        modifier = Modifier
                            .background(meaning.subsystemColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .border(0.8.dp, meaning.subsystemColor.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.5.dp)
                    ) {
                        Text(
                            text = meaning.subsystem,
                            color = meaning.subsystemColor,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = AppFont,
                            maxLines = 1
                        )
                    }

                    // Human Meaning Title
                    Text(
                        text = meaning.title,
                        color = M3OnSurface,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AppFont,
                        maxLines = 1
                    )
                }

                // Monospace ID Pill
                Surface(
                    color = M3SurfaceContainerHigh,
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(0.8.dp, M3OutlineVariant.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "${frame.idHex} (${frame.id})",
                        color = M3Primary,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }

            // Row 2: Live Decoded Interpretation (if available)
            if (meaning.liveValue != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(M3Success, CircleShape)
                    )
                    Text(
                        text = meaning.liveValue,
                        color = M3Success,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }
            }

            // Row 3: Descriptive explanation of this CAN ID
            Text(
                text = meaning.description,
                color = M3Outline,
                fontSize = 9.sp,
                fontFamily = AppFont,
                maxLines = 1
            )

            // Row 4: Raw Hex Payload strip
            Surface(
                color = M3SurfaceContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 2.5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "DLC ${frame.dlc}B",
                        color = M3Outline,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = frame.dataHex,
                        color = M3OnSurfaceVariant,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
