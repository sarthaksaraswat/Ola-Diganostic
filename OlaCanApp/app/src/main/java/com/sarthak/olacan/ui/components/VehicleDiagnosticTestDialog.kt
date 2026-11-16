package com.sarthak.olacan.ui.components

import com.sarthak.olacan.ui.theme.AppFont
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sarthak.olacan.analyzer.VehicleSelfTestManager
import com.sarthak.olacan.model.DiagnosticTestStatus
import com.sarthak.olacan.model.SubsystemTestItem
import com.sarthak.olacan.model.SubsystemTestState
import com.sarthak.olacan.model.VehicleHealthReport
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VehicleDiagnosticTestDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current

    val status by VehicleSelfTestManager.testStatus.collectAsState()
    val stepIndex by VehicleSelfTestManager.currentStepIndex.collectAsState()
    val testItems by VehicleSelfTestManager.testItems.collectAsState()
    val lastReport by VehicleSelfTestManager.lastReport.collectAsState()

    val totalSteps = testItems.size.coerceAtLeast(1)
    val progress = ((stepIndex + 1).toFloat() / totalSteps.toFloat()).coerceIn(0f, 1f)

    Dialog(
        onDismissRequest = {
            if (status == DiagnosticTestStatus.RUNNING) {
                VehicleSelfTestManager.abortTest()
            }
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(20.dp),
            color = M3Surface,
            border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header Bar
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
                                .background(M3PrimaryContainer, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = M3OnPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Vehicle Diagnostic Self-Test",
                                color = M3OnSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = AppFont
                            )
                            val subText = when (status) {
                                DiagnosticTestStatus.RUNNING -> "Testing Step ${stepIndex + 1} of $totalSteps"
                                DiagnosticTestStatus.COMPLETED -> "Diagnostic Routine Complete"
                                DiagnosticTestStatus.ABORTED -> "Self-Test Cancelled"
                                DiagnosticTestStatus.IDLE -> "Complete Vehicle Health Suite"
                            }
                            Text(
                                text = subText,
                                color = M3Outline,
                                fontSize = 11.sp,
                                fontFamily = AppFont
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            HapticHelper.performClick(view)
                            if (status == DiagnosticTestStatus.RUNNING) {
                                VehicleSelfTestManager.abortTest()
                            }
                            onDismiss()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = M3Outline,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Progress Bar (when running or completed)
                if (status == DiagnosticTestStatus.RUNNING) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val activeItem = testItems.getOrNull(stepIndex)
                            Text(
                                text = "Testing: ${activeItem?.name ?: ""}",
                                color = M3Primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                color = M3Outline,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = M3Primary,
                            trackColor = M3SurfaceContainerHigh
                        )
                    }
                }

                // Active Interactive Card when a step needs user input
                val currentItem = testItems.getOrNull(stepIndex)
                if (status == DiagnosticTestStatus.RUNNING && currentItem != null && currentItem.isInteractive && currentItem.state == SubsystemTestState.RUNNING) {
                    InteractiveStepCard(
                        item = currentItem,
                        onPass = {
                            HapticHelper.performClick(view)
                            VehicleSelfTestManager.confirmInteractiveStep(currentItem.id, true)
                        },
                        onFail = {
                            HapticHelper.performClick(view)
                            VehicleSelfTestManager.confirmInteractiveStep(currentItem.id, false)
                        },
                        onSkip = {
                            HapticHelper.performClick(view)
                            VehicleSelfTestManager.skipCurrentStep()
                        },
                        onPlayChime = {
                            HapticHelper.performClick(view)
                            VehicleSelfTestManager.playAudioChime()
                        }
                    )
                }

                // Summary Certificate when Completed
                val rep = lastReport
                if (status == DiagnosticTestStatus.COMPLETED && rep != null) {
                    DiagnosticSummaryCertificateCard(
                        report = rep,
                        onShare = {
                            HapticHelper.performClick(view)
                            shareDiagnosticReport(context, rep)
                        }
                    )
                }

                // Subsystems Checklist
                Text(
                    text = "SUBSYSTEM HEALTH CHECKLIST:",
                    color = M3Outline,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    fontFamily = AppFont,
                    modifier = Modifier.padding(top = 2.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(testItems) { idx, item ->
                        SubsystemTestRow(
                            item = item,
                            isCurrent = (idx == stepIndex && status == DiagnosticTestStatus.RUNNING)
                        )
                    }
                }

                // Bottom Action Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (status == DiagnosticTestStatus.RUNNING) {
                        OutlinedButton(
                            onClick = {
                                HapticHelper.performClick(view)
                                VehicleSelfTestManager.abortTest()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.5f))
                        ) {
                            Text("Abort Self-Test", fontSize = 12.sp, color = M3Error)
                        }
                    } else {
                        Button(
                            onClick = {
                                HapticHelper.performClick(view)
                                onDismiss()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = M3PrimaryContainer,
                                contentColor = M3OnPrimaryContainer
                            )
                        ) {
                            Text("Done", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InteractiveStepCard(
    item: SubsystemTestItem,
    onPass: () -> Unit,
    onFail: () -> Unit,
    onSkip: () -> Unit,
    onPlayChime: () -> Unit
) {
    Surface(
        color = M3SurfaceContainerHigh,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, M3Primary),
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(M3PrimaryContainer, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = item.category,
                            color = M3OnPrimaryContainer,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = item.name,
                        color = M3OnSurface,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Live 30s Countdown Pill
                val remaining = item.timeoutSecondsRemaining
                val timerColor = if (remaining <= 5) M3Error else M3Warning
                Box(
                    modifier = Modifier
                        .background(timerColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .border(1.dp, timerColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "⏱️ ${remaining}s",
                        color = timerColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Timeout Progress Bar
            LinearProgressIndicator(
                progress = { (item.timeoutSecondsRemaining / 30f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = if (item.timeoutSecondsRemaining <= 5) M3Error else M3Warning,
                trackColor = M3SurfaceContainerLow
            )

            Text(
                text = item.interactivePrompt,
                color = M3OnSurface,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            // Live CAN Bus Listening or Manual Inspection Indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(M3SurfaceContainerLow, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                if (item.requiresManualConfirmation) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = M3Primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Manual Inspection: Inspect vehicle & tap '${item.interactiveActionLabel.ifBlank { "Confirm OK" }}' below (${item.timeoutSecondsRemaining}s).",
                        fontSize = 10.sp,
                        color = M3Primary,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = M3Primary
                    )
                    Text(
                        text = "Awaiting CAN bus signal... Trigger switch to pass automatically (${item.timeoutSecondsRemaining}s).",
                        fontSize = 10.sp,
                        color = M3Primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (item.id == "INDICATOR_VOICE" || item.id == "SPEAKER_AUDIO") {
                OutlinedButton(
                    onClick = onPlayChime,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, M3Primary.copy(alpha = 0.5f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = M3Primary, modifier = Modifier.size(16.dp))
                        Text("Replay Audio Test Chime", fontSize = 11.5.sp, color = M3Primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Confirm OK Button
                Button(
                    onClick = onPass,
                    modifier = Modifier
                        .weight(1.3f)
                        .height(38.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = M3SuccessContainer,
                        contentColor = M3Success
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text(item.interactiveActionLabel.ifBlank { "Confirm OK" }, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (item.requiresManualConfirmation) {
                    // Manual Issue / Defect Report Button
                    OutlinedButton(
                        onClick = onFail,
                        modifier = Modifier
                            .weight(0.9f)
                            .height(38.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, M3Error.copy(alpha = 0.6f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(13.dp), tint = M3Error)
                            Text("Issue ✗", fontSize = 11.sp, color = M3Error, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Explicit Skip Option with 30s reminder
                OutlinedButton(
                    onClick = onSkip,
                    modifier = Modifier
                        .weight(if (item.requiresManualConfirmation) 0.8f else 1f)
                        .height(38.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.6f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(13.dp), tint = M3Outline)
                        Text(if (item.requiresManualConfirmation) "Skip" else "Skip Step", fontSize = 11.sp, color = M3Outline)
                    }
                }
            }
        }
    }
}

@Composable
fun SubsystemTestRow(
    item: SubsystemTestItem,
    isCurrent: Boolean
) {
    val borderColor = when (item.state) {
        SubsystemTestState.PASSED -> M3Success.copy(alpha = 0.4f)
        SubsystemTestState.FAILED -> M3Error.copy(alpha = 0.5f)
        SubsystemTestState.RUNNING -> M3Primary
        SubsystemTestState.SKIPPED -> M3OutlineVariant.copy(alpha = 0.4f)
        SubsystemTestState.PENDING -> M3OutlineVariant.copy(alpha = 0.25f)
    }

    Surface(
        color = if (isCurrent) M3SurfaceContainerHigh else M3SurfaceContainer,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.name,
                        color = M3OnSurface,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = AppFont
                    )
                    Text(
                        text = "• ${item.category}",
                        color = M3Outline,
                        fontSize = 9.5.sp,
                        fontFamily = AppFont
                    )
                }

                if (item.measuredValue.isNotBlank()) {
                    Text(
                        text = item.measuredValue,
                        color = when (item.state) {
                            SubsystemTestState.PASSED -> M3Success
                            SubsystemTestState.FAILED -> M3Error
                            else -> M3Outline
                        },
                        fontSize = 10.5.sp,
                        fontFamily = AppFont,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                } else {
                    Text(
                        text = item.description,
                        color = M3Outline,
                        fontSize = 10.sp,
                        maxLines = 1,
                        fontFamily = AppFont
                    )
                }
            }

            // Status Badge
            when (item.state) {
                SubsystemTestState.PASSED -> {
                    Box(
                        modifier = Modifier
                            .background(M3SuccessContainer, RoundedCornerShape(4.dp))
                            .border(1.dp, M3Success.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("PASS ✓", color = M3Success, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                SubsystemTestState.FAILED -> {
                    Box(
                        modifier = Modifier
                            .background(M3ErrorContainer, RoundedCornerShape(4.dp))
                            .border(1.dp, M3Error.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("FAIL ✗", color = M3Error, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                SubsystemTestState.RUNNING -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (item.isInteractive) {
                            Box(
                                modifier = Modifier
                                    .background(M3Warning.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${item.timeoutSecondsRemaining}s",
                                    color = M3Warning,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = M3Primary
                        )
                    }
                }
                SubsystemTestState.SKIPPED -> {
                    Box(
                        modifier = Modifier
                            .background(M3SurfaceContainerHigh, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("SKIPPED", color = M3Outline, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
                SubsystemTestState.PENDING -> {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(M3OutlineVariant, CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
fun DiagnosticSummaryCertificateCard(
    report: VehicleHealthReport,
    onShare: () -> Unit
) {
    Surface(
        color = M3SurfaceContainerHigh,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, if (report.isPassed) M3Success else M3Warning),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "HEALTH SCORE",
                        color = M3Outline,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${report.overallHealthScore}",
                            color = if (report.overallHealthScore >= 80) M3Success else M3Warning,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = " / 100",
                            color = M3Outline,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .background(if (report.isPassed) M3SuccessContainer else M3WarningContainer, RoundedCornerShape(6.dp))
                        .border(1.dp, if (report.isPassed) M3Success.copy(alpha = 0.5f) else M3Warning.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = report.summaryVerdict,
                        color = if (report.isPassed) M3Success else M3Warning,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Stats grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatBadge("TESTS RUN", "${report.totalTests}", M3OnSurface, Modifier.weight(1f))
                StatBadge("PASSED", "${report.passedTests}", M3Success, Modifier.weight(1f))
                StatBadge("FAILED", "${report.failedTests}", if (report.failedTests > 0) M3Error else M3Outline, Modifier.weight(1f))
                StatBadge("ODOMETER", String.format(Locale.US, "%,.0f km", report.odometerKm), M3Primary, Modifier.weight(1.3f))
            }

            // Share Report Button
            Button(
                onClick = onShare,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = M3SurfaceContainerLow,
                    contentColor = M3Primary
                ),
                border = BorderStroke(1.dp, M3Primary.copy(alpha = 0.4f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                    Text("Share Diagnostic Certificate", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StatBadge(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier
) {
    Surface(
        color = M3SurfaceContainerLow,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, color = M3Outline, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
            Text(value, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun shareDiagnosticReport(context: Context, report: VehicleHealthReport) {
    val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(Date(report.timestampMs))
    val text = buildString {
        appendLine("═══════════════════════════════════════")
        appendLine("VEHICLE HEALTH & DIAGNOSTIC CERTIFICATE")
        appendLine("═══════════════════════════════════════")
        appendLine("Vehicle: ${report.vehicleModel}")
        appendLine("Odometer: ${String.format(Locale.US, "%,.1f km", report.odometerKm)}")
        appendLine("Date: $dateStr")
        appendLine("Overall Health Score: ${report.overallHealthScore}/100")
        appendLine("Verdict: ${report.summaryVerdict}")
        appendLine("Tests: ${report.passedTests} Passed / ${report.failedTests} Failed (${report.totalTests} Total)")
        appendLine("───────────────────────────────────────")
        appendLine("SUBSYSTEM TEST RESULTS:")
        report.testItems.forEach { item ->
            val icon = when (item.state) {
                SubsystemTestState.PASSED -> "[PASS ✓]"
                SubsystemTestState.FAILED -> "[FAIL ✗]"
                SubsystemTestState.SKIPPED -> "[SKIP -]"
                else -> "[PENDING]"
            }
            appendLine("$icon ${item.name} (${item.category}): ${item.measuredValue}")
            if (item.diagnosticDetail.isNotBlank()) {
                appendLine("   Detail: ${item.diagnosticDetail}")
            }
        }
        appendLine("═══════════════════════════════════════")
        appendLine("Generated by Ola EV CAN Diagnostics Link")
    }

    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Vehicle Diagnostic Health Certificate")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share Diagnostic Report"))
    } catch (_: Exception) {}
}
