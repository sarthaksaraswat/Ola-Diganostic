package com.sarthak.olacan.ui.screens

import com.sarthak.olacan.ui.theme.AppFont
import androidx.compose.ui.text.font.FontStyle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ElectricScooter
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarthak.olacan.model.CalibrationTelemetry
import com.sarthak.olacan.ui.components.CellVoltageBar
import com.sarthak.olacan.ui.theme.M3Error
import com.sarthak.olacan.ui.theme.M3OnPrimaryContainer
import com.sarthak.olacan.ui.theme.M3OnSurface
import com.sarthak.olacan.ui.theme.M3OnSurfaceVariant
import com.sarthak.olacan.ui.theme.M3Outline
import com.sarthak.olacan.ui.theme.M3OutlineVariant
import com.sarthak.olacan.ui.theme.M3Primary
import com.sarthak.olacan.ui.theme.M3PrimaryContainer
import com.sarthak.olacan.ui.theme.M3Success
import com.sarthak.olacan.ui.theme.M3Surface
import com.sarthak.olacan.ui.theme.M3SurfaceContainer
import com.sarthak.olacan.ui.theme.M3SurfaceContainerHigh
import com.sarthak.olacan.ui.theme.M3Warning
import com.sarthak.olacan.util.HapticHelper
import com.sarthak.olacan.viewmodel.TelemetryViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * List of available powertrain, sensor, and battery calibration routines.
 */
enum class CalibrationRoutine(
    val title: String,
    val subtitle: String,
    val category: String,
    val icon: ImageVector,
    val badgeLabel: String,
    val prerequisiteSummary: String
) {
    BMS_BALANCING(
        title = "BMS Cell Balancing & Float Guard",
        subtitle = "Cell delta reduction, CV float keep-awake & AFE reset",
        category = "Battery",
        icon = Icons.Default.BatteryChargingFull,
        badgeLabel = "BMS",
        prerequisiteSummary = "AC Plugged In • >95% SOC • Temp 15-42°C"
    ),
    MOTOR_AUTO_TUNE(
        title = "MCU Motor Auto-Tuning Wizard",
        subtitle = "Rotor mechanical offset angle identification & phase alignment",
        category = "Powertrain",
        icon = Icons.Default.Speed,
        badgeLabel = "MCU",
        prerequisiteSummary = "⚠️ Stand Required • Wheel Free • 0 km/h"
    ),
    THROTTLE_GRIP(
        title = "Dual-Hall Throttle Grip Calibration",
        subtitle = "Zero-point idle deadband, WOT sweep & snap-back test",
        category = "Controls",
        icon = Icons.Default.Tune,
        badgeLabel = "VCU",
        prerequisiteSummary = "Untouched Grip • 1-2mm Gap • Stationary"
    ),
    IMU_HILL_HOLD(
        title = "6-Axis IMU & Hill-Hold Incline Zeroing",
        subtitle = "Pitch/Roll level calibration & hill-hold engagement angle",
        category = "Dynamics",
        icon = Icons.Default.Explore,
        badgeLabel = "IMU",
        prerequisiteSummary = "Flat Level Ground • Center Stand • Dismount"
    ),
    DYNO_BENCH_MODE(
        title = "Dyno & Paddock Stand Bench Mode",
        subtitle = "Bypass traction control & stand cutoffs for free-wheel spin",
        category = "Bench",
        icon = Icons.Default.Engineering,
        badgeLabel = "BENCH",
        prerequisiteSummary = "⚠️ Paddock / Dyno Bed Only • 1m Clearance"
    ),
    BRAKE_REGEN(
        title = "Brake Lever Switch & Dynamic Regen",
        subtitle = "Front/Rear switch cutoffs & forced/coasting regen maps",
        category = "Braking",
        icon = Icons.Default.ElectricScooter,
        badgeLabel = "REGEN",
        prerequisiteSummary = "Levers Released • Firm Fluid • Stationary"
    ),
    COULOMB_SOH(
        title = "Coulomb Counting & SOH Calibration",
        subtitle = "Continuous Wh integration & 15 km baseline capacity learning",
        category = "Battery",
        icon = Icons.Default.Bolt,
        badgeLabel = "SOH",
        prerequisiteSummary = "100% Start SOC • 15+ km Continuous Ride"
    )
}

@Composable
fun CalibrationScreen(
    viewModel: TelemetryViewModel,
    onNavigateConnect: () -> Unit
) {
    var selectedRoutine by remember { mutableStateOf<CalibrationRoutine?>(null) }
    val view = LocalView.current

    // Hardware back press navigates back to calibration hub selection list
    BackHandler(enabled = selectedRoutine != null) {
        HapticHelper.performClick(view)
        selectedRoutine = null
    }

    if (selectedRoutine != null) {
        val routine = selectedRoutine!!
        CalibrationSubPageContainer(
            routine = routine,
            viewModel = viewModel,
            onBack = {
                HapticHelper.performClick(view)
                selectedRoutine = null
            }
        )
    } else {
        CalibrationHubView(
            viewModel = viewModel,
            onSelectRoutine = { routine ->
                HapticHelper.performClick(view)
                selectedRoutine = routine
            },
            onNavigateConnect = onNavigateConnect
        )
    }
}

/**
 * Main Calibration Hub: Selection cards for each routine with live preview metrics.
 */
@Composable
private fun CalibrationHubView(
    viewModel: TelemetryViewModel,
    onSelectRoutine: (CalibrationRoutine) -> Unit,
    onNavigateConnect: () -> Unit
) {
    val stats by viewModel.streamStats.collectAsState()
    val isDemo by viewModel.isDemoMode.collectAsState()
    val battery by viewModel.batteryState.collectAsState()
    val powertrain by viewModel.powertrainState.collectAsState()
    val calib by viewModel.calibrationTelemetry.collectAsState()
    val coulomb by viewModel.coulombState.collectAsState()

    var selectedCategory by remember { mutableStateOf("All") }
    val categories = listOf("All", "Battery", "Powertrain", "Controls", "Dynamics", "Bench", "Braking")

    val filteredRoutines = remember(selectedCategory) {
        if (selectedCategory == "All") CalibrationRoutine.values().toList()
        else CalibrationRoutine.values().filter { it.category == selectedCategory }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Header Banner
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Powertrain & Hardware Calibration",
                            color = M3OnSurface,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = AppFont
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Parameter auto-tuning, sensor zeroing & BMS balancing",
                            color = M3OnSurfaceVariant,
                            fontSize = 12.sp,
                            fontFamily = AppFont
                        )
                    }

                    // Connection Status Chip
                    Surface(
                        color = if (stats.isConnected || isDemo) M3PrimaryContainer else M3SurfaceContainerHigh,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(
                                        if (isDemo) M3Primary else if (stats.isConnected) M3Success else M3Error,
                                        CircleShape
                                    )
                            )
                            Text(
                                text = if (isDemo) "Demo" else if (stats.isConnected) "Online" else "Offline",
                                color = M3OnSurface,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // 2. Safety Interlock Notice Card
        item {
            val speed = powertrain.speedKmh
            val isStationary = speed in 0.0..0.5

            Surface(
                color = if (isStationary) M3SurfaceContainer else M3Error.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (isStationary) M3OutlineVariant.copy(alpha = 0.4f) else M3Error.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (isStationary) Icons.Default.Security else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isStationary) M3Success else M3Error,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when {
                                isStationary -> "Vehicle speed: stationary"
                                !speed.isFinite() -> "Vehicle speed unavailable"
                                else -> "Vehicle moving (${String.format(Locale.US, "%.1f", speed)} km/h)"
                            },
                            color = if (isStationary) M3OnSurface else M3Error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isStationary) "Scooter must remain safely parked on stand during active calibration." else "Keep the scooter parked and awake so live speed is available.",
                            color = M3OnSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // 3. Category Filter Chips
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories) { cat ->
                    val isSelected = selectedCategory == cat
                    Surface(
                        color = if (isSelected) M3PrimaryContainer else M3SurfaceContainer,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) M3Primary else M3OutlineVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.clickable { selectedCategory = cat }
                    ) {
                        Text(
                            text = cat,
                            color = if (isSelected) M3OnPrimaryContainer else M3OnSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // 4. Calibration Selection Cards
        items(filteredRoutines) { routine ->
            CalibrationSelectionCard(
                routine = routine,
                calib = calib,
                batteryDeltaMv = battery.cellDeltaMv,
                coulombProgress = coulomb.calibrationProgressPercent,
                onClick = { onSelectRoutine(routine) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Rich Card displaying routine summary and live preview values.
 */
@Composable
private fun CalibrationSelectionCard(
    routine: CalibrationRoutine,
    calib: CalibrationTelemetry,
    batteryDeltaMv: Double,
    coulombProgress: Double,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = M3SurfaceContainer),
        border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top Row: Category Tag + Icon + Title + Action Arrow
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Subsystem Icon Box
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(M3SurfaceContainerHigh, RoundedCornerShape(12.dp))
                        .border(1.dp, M3OutlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = routine.icon,
                        contentDescription = null,
                        tint = M3Primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = routine.title,
                        color = M3OnSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AppFont,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = routine.subtitle,
                        color = M3OnSurfaceVariant,
                        fontSize = 11.sp,
                        fontFamily = AppFont,
                        maxLines = 2
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Open Routine",
                    tint = M3Primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Prerequisite Requirements Summary Tag
            Surface(
                color = when (routine) {
                    CalibrationRoutine.MOTOR_AUTO_TUNE, CalibrationRoutine.DYNO_BENCH_MODE -> M3Error.copy(alpha = 0.12f)
                    CalibrationRoutine.BMS_BALANCING, CalibrationRoutine.THROTTLE_GRIP -> M3Warning.copy(alpha = 0.12f)
                    else -> M3SurfaceContainerHigh
                },
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(
                    1.dp,
                    when (routine) {
                        CalibrationRoutine.MOTOR_AUTO_TUNE, CalibrationRoutine.DYNO_BENCH_MODE -> M3Error.copy(alpha = 0.45f)
                        CalibrationRoutine.BMS_BALANCING, CalibrationRoutine.THROTTLE_GRIP -> M3Warning.copy(alpha = 0.45f)
                        else -> M3OutlineVariant.copy(alpha = 0.35f)
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = when (routine) {
                            CalibrationRoutine.MOTOR_AUTO_TUNE, CalibrationRoutine.DYNO_BENCH_MODE -> Icons.Default.Warning
                            CalibrationRoutine.BMS_BALANCING, CalibrationRoutine.THROTTLE_GRIP -> Icons.Default.Warning
                            else -> Icons.Default.Security
                        },
                        contentDescription = null,
                        tint = when (routine) {
                            CalibrationRoutine.MOTOR_AUTO_TUNE, CalibrationRoutine.DYNO_BENCH_MODE -> M3Error
                            CalibrationRoutine.BMS_BALANCING, CalibrationRoutine.THROTTLE_GRIP -> M3Warning
                            else -> M3Primary
                        },
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "Req: ${routine.prerequisiteSummary}",
                        color = when (routine) {
                            CalibrationRoutine.MOTOR_AUTO_TUNE, CalibrationRoutine.DYNO_BENCH_MODE -> M3Error
                            CalibrationRoutine.BMS_BALANCING, CalibrationRoutine.THROTTLE_GRIP -> M3Warning
                            else -> M3OnSurfaceVariant
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = M3OutlineVariant.copy(alpha = 0.25f), thickness = 0.8.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Metric Preview Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val (metricLabel, metricValue, isGood) = when (routine) {
                    CalibrationRoutine.BMS_BALANCING -> {
                        val delta = if (batteryDeltaMv.isNaN()) 16.0 else batteryDeltaMv
                        Triple("Pack Delta", "${delta.toInt()} mV", delta < 25.0)
                    }
                    CalibrationRoutine.MOTOR_AUTO_TUNE -> {
                        val deg = if (calib.offsetAngleMechanicalDegree.isNaN()) 34.2 else calib.offsetAngleMechanicalDegree
                        Triple("Rotor Offset", "${String.format(Locale.US, "%.1f", deg)}°", true)
                    }
                    CalibrationRoutine.THROTTLE_GRIP -> {
                        val v1 = if (calib.throttleValue1V.isNaN()) 0.96 else calib.throttleValue1V
                        Triple("Idle Sensor 1", "${String.format(Locale.US, "%.2f", v1)}V", v1 in 0.90..1.20)
                    }
                    CalibrationRoutine.IMU_HILL_HOLD -> {
                        val p = if (calib.imuPitchDeg.isNaN()) 0.0 else calib.imuPitchDeg
                        Triple("Pitch Angle", "${String.format(Locale.US, "%.1f", p)}°", Math.abs(p) < 2.0)
                    }
                    CalibrationRoutine.DYNO_BENCH_MODE -> {
                        Triple("Dyno Bench", if (calib.isDynoModeActive) "ACTIVE" else "DISABLED", calib.isDynoModeActive)
                    }
                    CalibrationRoutine.BRAKE_REGEN -> {
                        val rPressed = calib.rearBrakePressed
                        Triple("Rear Brake", if (rPressed) "ENGAGED" else "RELEASED", !rPressed)
                    }
                    CalibrationRoutine.COULOMB_SOH -> {
                        Triple("15 km Learned", "${coulombProgress.toInt()}%", coulombProgress >= 100.0)
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = metricLabel,
                        color = M3OnSurfaceVariant,
                        fontSize = 11.sp,
                        fontFamily = AppFont
                    )
                    Text(
                        text = metricValue,
                        color = if (isGood) M3Success else M3Warning,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Subsystem Badge Pill
                Surface(
                    color = M3SurfaceContainerHigh,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = routine.badgeLabel,
                        color = M3Primary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AppFont,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

/**
 * Dedicated Sub-Page Container with Back Navigation and Routine Views.
 */
@Composable
private fun CalibrationSubPageContainer(
    routine: CalibrationRoutine,
    viewModel: TelemetryViewModel,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(M3Surface)
    ) {
        Surface(
            color = M3SurfaceContainer,
            border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.4f)),
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButtonBack(onClick = onBack)
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    imageVector = routine.icon,
                    contentDescription = null,
                    tint = M3Primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = routine.title,
                        color = M3OnSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AppFont,
                        maxLines = 1
                    )
                    Text(
                        text = routine.subtitle,
                        color = M3OnSurfaceVariant,
                        fontSize = 10.sp,
                        fontFamily = AppFont,
                        maxLines = 1
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            when (routine) {
                CalibrationRoutine.BMS_BALANCING -> BmsBalancingSubPage(viewModel)
                CalibrationRoutine.MOTOR_AUTO_TUNE -> MotorAutoTuneSubPage(viewModel)
                CalibrationRoutine.THROTTLE_GRIP -> ThrottleGripSubPage(viewModel)
                CalibrationRoutine.IMU_HILL_HOLD -> ImuHillHoldSubPage(viewModel)
                CalibrationRoutine.DYNO_BENCH_MODE -> DynoBenchModeSubPage(viewModel)
                CalibrationRoutine.BRAKE_REGEN -> BrakeRegenSubPage(viewModel)
                CalibrationRoutine.COULOMB_SOH -> CoulombSohSubPage(viewModel)
            }
        }
    }
}

// =============================================================================
// 1. SMART BMS BALANCING & FLOAT GUARD SUB-PAGE
// =============================================================================

@Composable
private fun BmsBalancingSubPage(viewModel: TelemetryViewModel) {
    val context = LocalContext.current
    val view = LocalView.current
    val battery by viewModel.batteryState.collectAsState()
    val calib by viewModel.calibrationTelemetry.collectAsState()

    var floatKeepAwake by remember { mutableStateOf(calib.isFloatKeepAwakeActive) }
    var isExecutingAction by remember { mutableStateOf(false) }
    var activeActionTag by remember { mutableStateOf<String?>(null) }
    var actionProgress by remember { mutableFloatStateOf(0f) }
    var actionStepMessage by remember { mutableStateOf("") }

    LaunchedEffect(calib.isFloatKeepAwakeActive) {
        floatKeepAwake = calib.isFloatKeepAwakeActive
    }

    val deltaMv = if (battery.cellDeltaMv.isNaN()) 16.0 else battery.cellDeltaMv
    val isBalanced = deltaMv < 25.0

    // Compute cell min/max for 14S overview
    val cells = if (battery.cellVoltagesV.isNotEmpty()) battery.cellVoltagesV else listOf(
        3.998, 4.002, 3.996, 4.012, 3.999, 4.001, 3.997, 4.004, 3.995, 4.000, 4.003, 3.998, 4.002, 3.996
    )
    val maxV = cells.maxOrNull() ?: 4.012
    val minV = cells.minOrNull() ?: 3.995
    val avgV = if (cells.isNotEmpty()) cells.average() else 4.000

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Prerequisites & Setup Checklist Card
        item {
            PrerequisitesCard(
                severity = PrerequisiteSeverity.WARNING,
                title = "BMS BALANCING PRE-REQUISITES",
                items = listOf(
                    "Connect OEM/portable AC charger and confirm scooter is actively charging.",
                    "Battery SOC must be above 95% (>56.0V pack voltage) — passive bleed shunts only activate above 4.12V/cell.",
                    "Battery pack temperature must be between 15°C and 42°C (BMS disables balancing circuits outside this band).",
                    "Keep vehicle awake using 'Float Charge Guard' below to allow 2 to 4 hours of overnight float saturation."
                ),
                warningCallout = "Do not disconnect charger or let scooter go to sleep during active float bleeding."
            )
        }

        // Status & Overview Card
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = M3SurfaceContainer),
                border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "CELL DELTA SPREAD",
                                color = M3OnSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${deltaMv.toInt()} mV",
                                color = if (isBalanced) M3Success else M3Warning,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Surface(
                            color = if (isBalanced) M3Success.copy(alpha = 0.15f) else M3Warning.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (isBalanced) M3Success.copy(alpha = 0.4f) else M3Warning.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = if (isBalanced) "BALANCED (<25 mV)" else "IMBALANCE (Float Required)",
                                color = if (isBalanced) M3Success else M3Warning,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Ola passive balancing shunts engage only at top CV float saturation (>4.12V/cell). Keep vehicle awake during overnight charging to complete bleed cycles.",
                        color = M3OnSurfaceVariant,
                        fontSize = 11.sp
                    )

                    val balancingSaturationProgress = if (deltaMv <= 25.0) 1.0f else ((100.0 - deltaMv) / 75.0).coerceIn(0.05, 0.95).toFloat()
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Delta Target Progress",
                            color = M3OnSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (isBalanced) "100% (BALANCED <25 mV)" else "${(balancingSaturationProgress * 100).toInt()}% (${deltaMv.toInt()} mV)",
                            color = if (isBalanced) M3Success else M3Warning,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { balancingSaturationProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (isBalanced) M3Success else M3Warning,
                        trackColor = M3SurfaceContainerHigh
                    )
                }
            }
        }

        // Live 14-Series Cell Spread Grid
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = M3SurfaceContainer),
                border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SERIES CELL VOLTAGES & BLEED SHUNTS",
                            color = M3OnSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (calib.cellBalancingActive) {
                            Surface(
                                color = M3Success.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "BLEED ACTIVE",
                                    color = M3Success,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    cells.forEachIndexed { idx, v ->
                        val isHighest = Math.abs(v - maxV) < 0.001
                        val isLowest = Math.abs(v - minV) < 0.001
                        CellVoltageBar(
                            channelNumber = idx + 1,
                            voltageV = v,
                            isMin = isLowest,
                            isMax = isHighest,
                            avgVoltageV = avgV,
                            modifier = Modifier.padding(vertical = 3.dp)
                        )
                    }
                }
            }
        }

        // Float Charge Guard (Keep Awake)
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = M3SurfaceContainer),
                border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Float Charge Guard",
                                color = M3OnSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (floatKeepAwake) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(M3Success, CircleShape)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Prevents MoveOS from deep-suspending while plugged in. Holds BMS bleeder awake for full balancing.",
                            color = M3OnSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }

                    Switch(
                        checked = floatKeepAwake,
                        onCheckedChange = { checked ->
                            HapticHelper.performClick(view)
                            floatKeepAwake = checked
                            viewModel.setBmsFloatKeepAwake(checked) { success, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = M3Primary,
                            checkedTrackColor = M3PrimaryContainer
                        )
                    )
                }
            }
        }

        // Triggerable Service Actions
        item {
            Text(
                text = "BMS SERVICE TRIGGERS",
                color = M3OnSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            ActionCard(
                title = "Unlatch & Reset AFE (CAN 0x6DA)",
                description = "Clears transient Analog Front-End fault registers and unlocks Coulomb counter lockouts.",
                buttonText = if (isExecutingAction && activeActionTag == "AFE") "Resetting AFE..." else "Reset AFE Faults",
                icon = Icons.Default.RestartAlt,
                enabled = !isExecutingAction,
                progress = if (activeActionTag == "AFE") actionProgress else null,
                progressMessage = if (activeActionTag == "AFE") actionStepMessage else null,
                onClick = {
                    HapticHelper.performClick(view)
                    isExecutingAction = true
                    activeActionTag = "AFE"
                    actionProgress = 0.15f
                    actionStepMessage = "Preparing AFE unlatch command..."
                    viewModel.resetBmsAfeFaults(
                        onProgress = { step, progress ->
                            actionStepMessage = step
                            actionProgress = progress
                        },
                        onResult = { success, msg ->
                            isExecutingAction = false
                            activeActionTag = null
                            actionProgress = if (success) 1.0f else 0.0f
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            )
        }

        item {
            ActionCard(
                title = "Wake Battery Pack Contactors (CAN 0x00B)",
                description = "Broadcasts charger wakeup frames to command BMS contactors to close for charge flow.",
                buttonText = if (isExecutingAction && activeActionTag == "WAKE") "Waking Pack..." else "Send Contactor Wakeup",
                icon = Icons.Default.PlayArrow,
                enabled = !isExecutingAction,
                progress = if (activeActionTag == "WAKE") actionProgress else null,
                progressMessage = if (activeActionTag == "WAKE") actionStepMessage else null,
                onClick = {
                    HapticHelper.performClick(view)
                    isExecutingAction = true
                    activeActionTag = "WAKE"
                    actionProgress = 0.15f
                    actionStepMessage = "Broadcasting wakeup packets..."
                    viewModel.wakeupBatteryPack(
                        onProgress = { step, progress ->
                            actionStepMessage = step
                            actionProgress = progress
                        },
                        onResult = { success, msg ->
                            isExecutingAction = false
                            activeActionTag = null
                            actionProgress = if (success) 1.0f else 0.0f
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            )
        }

        item {
            ActionCard(
                title = "Sync SOC with Resting OCV Curve",
                description = "Forces BMS Coulomb counter to re-sync against the open-circuit voltage resting table.",
                buttonText = if (isExecutingAction && activeActionTag == "OCV") "Syncing OCV..." else "Re-sync OCV Baseline",
                icon = Icons.Default.Refresh,
                enabled = !isExecutingAction,
                progress = if (activeActionTag == "OCV") actionProgress else null,
                progressMessage = if (activeActionTag == "OCV") actionStepMessage else null,
                onClick = {
                    HapticHelper.performClick(view)
                    isExecutingAction = true
                    activeActionTag = "OCV"
                    actionProgress = 0.15f
                    actionStepMessage = "Reading resting cell voltage curve..."
                    viewModel.syncOcvBaseline(
                        onProgress = { step, progress ->
                            actionStepMessage = step
                            actionProgress = progress
                        },
                        onResult = { success, msg ->
                            isExecutingAction = false
                            activeActionTag = null
                            actionProgress = if (success) 1.0f else 0.0f
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// =============================================================================
// 2. MCU MOTOR PARAMETER AUTO-TUNING SUB-PAGE
// =============================================================================

@Composable
private fun MotorAutoTuneSubPage(viewModel: TelemetryViewModel) {
    val context = LocalContext.current
    val view = LocalView.current
    val powertrain by viewModel.powertrainState.collectAsState()
    val status by viewModel.vehicleStatus.collectAsState()
    val thermal by viewModel.thermalState.collectAsState()
    val calib by viewModel.calibrationTelemetry.collectAsState()

    var standConfirmed by remember { mutableStateOf(false) }
    var tuningStep by remember { mutableStateOf("") }
    var tuningProgress by remember { mutableFloatStateOf(0f) }
    var isTuning by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    val speed = powertrain.speedKmh
    val isStationary = speed in 0.0..0.5
    val throttleZero = powertrain.throttlePercent.isNaN() || powertrain.throttlePercent <= 1.0

    val measuredOffset = if (calib.offsetAngleMechanicalDegree.isNaN()) 34.2 else calib.offsetAngleMechanicalDegree

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = M3Primary)
                    Text("MCU Motor Offset Verification", color = M3OnSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "The app will query MCU non-volatile memory (NVM) tables to verify electrical-to-mechanical rotor resolver offset.",
                        color = M3OnSurface,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                    Text(
                        text = "ℹ️ MoveOS MCU firmware blocks automated motor spin over userland Wi-Fi without OEM Dealership Security Access (UDS 0x27).",
                        color = M3Warning,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "This routine reads and verifies the active factory angle calibration stored in the MCU inverter.",
                        color = M3OnSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        HapticHelper.performClick(view)
                        isTuning = true
                        tuningProgress = 0.05f
                        viewModel.triggerMotorAutoTune(
                            onProgress = { step, progress ->
                                tuningStep = step
                                tuningProgress = progress
                            },
                            onResult = { success, msg ->
                                isTuning = false
                                tuningStep = msg
                                tuningProgress = if (success) 1.0f else 0.0f
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                ) {
                    Text("Read MCU Offset", color = M3Primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Critical Prerequisites & Rotational Hazard Card
        item {
            PrerequisitesCard(
                severity = PrerequisiteSeverity.CRITICAL,
                title = "MOTOR CALIBRATION PRE-REQUISITES & HAZARD",
                items = listOf(
                    "Scooter MUST be securely elevated on a center stand or workshop paddock stand.",
                    "Rear drive wheel MUST have 100% free clearance off the floor with zero ground contact.",
                    "Maintain 360° clearance around tyre and carbon drive belt — keep hands, feet, loose tools, and wires away.",
                    "Vehicle speed must be strictly 0.0 km/h and throttle grip completely released at 0.0%.",
                    "Motor and MCU inverter temperature should be below 65°C to avoid thermal phase drift."
                ),
                warningCallout = "CRITICAL HAZARD: Motor pulses high DC currents causing sudden rotational kicks and rapid spin. NEVER run while tyre touches ground or while sitting on scooter!"
            )
        }

        // Safety Checklist Card
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = M3SurfaceContainer),
                border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "SAFETY INTERLOCK CHECKLIST",
                        color = M3OnSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    SafetyCheckItem("Scooter Speed: 0.0 km/h", isStationary)
                    SafetyCheckItem("Throttle Position: 0%", throttleZero)
                    SafetyCheckItem("Side Stand Switch: Stand UP", !status.sideStandDown)

                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        color = if (standConfirmed) M3Success.copy(alpha = 0.12f) else M3Error.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (standConfirmed) M3Success.copy(alpha = 0.4f) else M3Error.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { standConfirmed = !standConfirmed }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = standConfirmed,
                                onCheckedChange = { standConfirmed = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = M3Success,
                                    uncheckedColor = M3Error
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "I CONFIRM: Rear wheel is fully elevated off ground with 360° unobstructed clearance around tyre and belt drive.",
                                color = if (standConfirmed) M3Success else M3Error,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }

        // Current Rotor Offset & Live Motor Health
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = M3SurfaceContainer),
                border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ROTOR MECHANICAL OFFSET (CAN 0x335)",
                        color = M3OnSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${String.format(Locale.US, "%.1f", measuredOffset)}°",
                            color = M3Primary,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Calibrations Passed: ${calib.successfulCalibrationCounter}",
                                color = M3Success,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Total Attempts: ${calib.attemptedCalibrationCounter}",
                                color = M3OnSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = M3OutlineVariant.copy(alpha = 0.25f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val phaseA = if (powertrain.motorPhaseCurrentA.isNaN()) 0.0 else powertrain.motorPhaseCurrentA
                        val motTemp = if (thermal.motorTempC.isNaN()) 38.0 else thermal.motorTempC
                        Text("Phase Current: ${String.format(Locale.US, "%.1f", phaseA)} A", color = M3OnSurfaceVariant, fontSize = 11.sp)
                        Text("Motor Temp: ${motTemp.toInt()}°C", color = M3OnSurfaceVariant, fontSize = 11.sp)
                    }
                }
            }
        }

        // Active Tuning Step Banner with Progress Bar
        if (isTuning || tuningStep.isNotBlank()) {
            item {
                Surface(
                    color = M3PrimaryContainer.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, M3Primary.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isTuning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = M3Primary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = M3Success, modifier = Modifier.size(18.dp))
                                }
                                Text(
                                    text = tuningStep,
                                    color = M3OnSurface,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            if (isTuning || tuningProgress > 0f) {
                                Text(
                                    text = "${(tuningProgress * 100).toInt()}%",
                                    color = M3Primary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        if (isTuning || (tuningProgress in 0.01f..0.99f)) {
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { tuningProgress.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = M3Primary,
                                trackColor = M3SurfaceContainerHigh
                            )
                        }
                    }
                }
            }
        }

        // Start Auto-Tuning Trigger
        item {
            val canStart = isStationary && throttleZero && standConfirmed && !isTuning
            OutlinedButton(
                onClick = {
                    HapticHelper.performClick(view)
                    showConfirmDialog = true
                },
                enabled = canStart,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, if (canStart) M3Primary else M3Outline.copy(alpha = 0.4f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (canStart) M3PrimaryContainer else Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.Speed, contentDescription = null, tint = if (canStart) M3OnPrimaryContainer else M3Outline)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isTuning) "Querying MCU NVM..." else "Read MCU Motor Offset",
                    color = if (canStart) M3OnPrimaryContainer else M3Outline,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// =============================================================================
// 3. DUAL-HALL THROTTLE GRIP CALIBRATION SUB-PAGE
// =============================================================================

@Composable
private fun ThrottleGripSubPage(viewModel: TelemetryViewModel) {
    val context = LocalContext.current
    val view = LocalView.current
    val calib by viewModel.calibrationTelemetry.collectAsState()
    val powertrain by viewModel.powertrainState.collectAsState()
    val isDemo by viewModel.isDemoMode.collectAsState()

    var isCalibrating by remember { mutableStateOf(false) }
    var throttleProgress by remember { mutableFloatStateOf(0f) }
    var throttleStepMessage by remember { mutableStateOf("") }

    // Return spring & binding test stages:
    // 0 = Idle
    // 1 = Waiting for Twist (>= 2.60V)
    // 2 = Armed & Steady (User holds grip. Timer NOT running yet. Prompt: "Ready! Release grip abruptly.")
    // 3 = Snap transit in progress (User released grip; v1 dropped < 2.35V; timing transit down to <= 1.15V)
    // 4 = Complete (shows physical spring transit time or sticking detection)
    var snapTestStage by remember { mutableIntStateOf(0) }
    var snapReleaseStartTimeMs by remember { mutableLongStateOf(0L) }
    var snapBackResultMs by remember { mutableLongStateOf(-1L) }
    var snapStatusMessage by remember { mutableStateOf("Press 'Start Return Check' to evaluate handlebar spring snap-back.") }

    val scope = rememberCoroutineScope()

    val v1 = if (calib.throttleValue1V.isNaN()) 0.96 else calib.throttleValue1V
    val rawV2 = if (calib.throttleValue2V.isNaN()) 0.97 else calib.throttleValue2V
    // Check if Channel 2 uses automotive half-scale ratiometric safety track (V2 ~ V1 / 2)
    val isHalfScale = rawV2 > 0.20 && rawV2 < (v1 * 0.70)
    val v2Normalized = if (isHalfScale) rawV2 * 2.0 else rawV2
    val diffMv = (Math.abs(v1 - v2Normalized) * 1000).toInt()
    val isDeviationSafe = diffMv <= 150

    // Monitor live throttle voltages for the mechanical return spring test
    LaunchedEffect(v1, snapTestStage) {
        when (snapTestStage) {
            1 -> {
                // Waiting for user to twist throttle past 60%
                if (v1 >= 2.60) {
                    snapTestStage = 2
                    snapStatusMessage = "✓ Throttle held. Whenever ready, release the grip abruptly (let it snap shut)."
                    HapticHelper.performClick(view)
                }
            }
            2 -> {
                // User is holding grip open (>= 2.60V). Timer does NOT run while holding!
                // Release event is detected the moment voltage drops below 2.35V
                if (v1 < 2.35) {
                    snapTestStage = 3
                    snapReleaseStartTimeMs = System.currentTimeMillis()
                    snapStatusMessage = "Measuring physical spring transit to neutral deadband..."
                }
            }
            3 -> {
                // Spring is in transit. Wait until it reaches idle deadband (<= 1.15V)
                if (v1 <= 1.15) {
                    val elapsed = (System.currentTimeMillis() - snapReleaseStartTimeMs).coerceAtLeast(18L)
                    snapBackResultMs = elapsed
                    snapTestStage = 4
                    snapStatusMessage = if (elapsed <= 120) {
                        "EXCELLENT SNAP (~$elapsed ms) ✓ Instant spring snap-back to 0.0% Neutral (<3 CAN frames)"
                    } else if (elapsed <= 220) {
                        "NOMINAL RETURN (~$elapsed ms) ✓ Clean return to 0.0% Neutral deadband"
                    } else {
                        "SLOW RETURN (~$elapsed ms) ⚠️ Check bar-end gap or grip friction"
                    }
                    HapticHelper.performClick(view)
                }
            }
        }
    }

    // Safety timeout: If spring remains sticking above 1.15V for >2.5s during transit
    LaunchedEffect(snapTestStage) {
        if (snapTestStage == 3) {
            delay(2500)
            if (snapTestStage == 3) {
                snapTestStage = 4
                snapBackResultMs = -2L
                snapStatusMessage = "STICKING DETECTED ⚠️ Throttle did not return to 0.0% neutral deadband (rested at ${String.format(java.util.Locale.US, "%.2f", v1)}V)"
                HapticHelper.performClick(view)
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Prerequisites Card
        item {
            PrerequisitesCard(
                severity = PrerequisiteSeverity.WARNING,
                title = "THROTTLE CALIBRATION PRE-REQUISITES",
                items = listOf(
                    "DO NOT touch, twist, or exert force on the throttle grip during deadband zeroing.",
                    "Verify 1–2 mm mechanical air gap between rubber grip and handlebar switch housing / bar-end weight (no rubbing).",
                    "Ensure return spring snaps back smoothly and cleanly to 0.0% neutral without binding before calibrating deadband.",
                    "Vehicle must be stationary (0.0 km/h) in Park mode with side stand down."
                ),
                warningCallout = "Zeroing while resting hands on the grip will cause deadband offset errors and unexpected acceleration response."
            )
        }

        // Dual Channel Oscilloscope Gauges
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = M3SurfaceContainer),
                border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DUAL-HALL ADC SIGNALS (CAN 0x113)",
                            color = M3OnSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val pct = if (powertrain.throttlePercent.isNaN()) 0 else powertrain.throttlePercent.toInt()
                        Text(
                            text = "Grip Input: $pct%",
                            color = M3Primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // Channel 1
                    VoltageBarRow(
                        label = "Hall Channel 1 (Main Command)",
                        voltage = v1,
                        minV = 0.55,
                        maxV = 4.10,
                        accent = M3Primary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Channel 2
                    VoltageBarRow(
                        label = if (isHalfScale) "Hall Channel 2 (Half-Scale Safety x2: ${String.format(Locale.US, "%.2f", rawV2)}V)" else "Hall Channel 2 (Redundant Safety)",
                        voltage = if (isHalfScale) v2Normalized else rawV2,
                        minV = 0.55,
                        maxV = 4.10,
                        accent = M3Success
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = M3OutlineVariant.copy(alpha = 0.25f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Channel Discrepancy (ΔV): $diffMv mV",
                            color = if (isDeviationSafe) M3Success else M3Error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isDeviationSafe) "PASS (<150 mV)" else "DRIFT FAULT",
                            color = if (isDeviationSafe) M3Success else M3Error,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = M3OutlineVariant.copy(alpha = 0.25f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CAN Throttle Latency: ~20 ms (50 Hz)",
                            color = M3OnSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Neutral: 0.0% Calibrated",
                            color = M3Primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Throttle Operating Zones Legend
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = M3SurfaceContainer),
                border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "REFERENCE OPERATING BANDS",
                        color = M3OnSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ZoneLegendRow("Reverse Twist Zone", "0.55V - 0.85V", "Forced Regen / Reverse Assist")
                    ZoneLegendRow("Idle Deadband", "0.95V - 1.15V", "Neutral Standby (0% throttle)")
                    ZoneLegendRow("Forward Sweep", "1.15V - 3.95V", "Proportional Drive Acceleration")
                    ZoneLegendRow("Open / Short Faults", "<0.35V or >4.5V", "Rail short protection cutoff")
                }
            }
        }

        // Handlebar Return Spring & Binding Check
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = M3SurfaceContainer),
                border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "HANDLEBAR RETURN SPRING & BINDING CHECK",
                        color = M3OnSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tests mechanical return spring snap-back speed & zero-return when released. (Note: MoveOS CAN electronic throttle latency is fixed at ~20 ms).",
                        color = M3OnSurfaceVariant,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        color = when (snapTestStage) {
                            1 -> M3Warning.copy(alpha = 0.15f)
                            2 -> M3PrimaryContainer.copy(alpha = 0.35f)
                            3 -> M3PrimaryContainer.copy(alpha = 0.5f)
                            4 -> if (snapBackResultMs in 1..220) M3Success.copy(alpha = 0.15f) else M3Warning.copy(alpha = 0.15f)
                            else -> M3SurfaceContainerHigh
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = snapStatusMessage,
                                color = M3OnSurface,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (snapTestStage == 4 && snapBackResultMs > 0) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Note: Measures physical spring travel time (2.4V → 1.15V). Electronic CAN throttle latency is ~20 ms.",
                                    color = M3OnSurfaceVariant.copy(alpha = 0.8f),
                                    fontSize = 10.sp,
                                    fontStyle = FontStyle.Italic
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                HapticHelper.performClick(view)
                                if (snapTestStage in 1..3) {
                                    snapTestStage = 0
                                    snapBackResultMs = -1L
                                    snapStatusMessage = "Test cancelled. Press 'Start Return Check' to evaluate spring snap-back."
                                } else {
                                    snapTestStage = 1
                                    snapBackResultMs = -1L
                                    snapStatusMessage = "1. Twist throttle past 60% (>2.6V) and hold..."
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = when (snapTestStage) {
                                    1 -> "Twist & Hold (>60%)... (Cancel)"
                                    2 -> "Armed: Release Grip (Cancel)"
                                    3 -> "Measuring Transit..."
                                    4 -> "Retest Spring Snap"
                                    else -> "Start Return Check"
                                }
                            )
                        }

                        if (isDemo || snapTestStage in 1..3) {
                            OutlinedButton(
                                onClick = {
                                    HapticHelper.performClick(view)
                                    scope.launch {
                                        snapTestStage = 1
                                        snapStatusMessage = "Simulating throttle twist..."
                                        delay(350)
                                        snapTestStage = 2
                                        snapStatusMessage = "✓ Throttle held at 75%. Release grip abruptly."
                                        delay(450)
                                        snapTestStage = 3
                                        snapReleaseStartTimeMs = System.currentTimeMillis()
                                        snapStatusMessage = "Measuring spring transit to neutral deadband..."
                                        delay(58)
                                        snapBackResultMs = 58L
                                        snapTestStage = 4
                                        snapStatusMessage = "EXCELLENT SNAP (~58 ms) ✓ Instant spring snap-back to 0.0% Neutral (<3 CAN frames)"
                                    }
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Simulate")
                            }
                        }
                    }
                }
            }
        }

        // Re-learn Idle Deadband Button
        item {
            ActionCard(
                title = "Zero Resting Deadband & Re-calibrate Idle",
                description = "Samples current untouched throttle voltage, adapts the 0% neutral window, and zeros any resting 1% dead center drift.",
                buttonText = if (isCalibrating) "Zeroing Deadband..." else "Zero Idle Deadband (0%)",
                icon = Icons.Default.Tune,
                enabled = !isCalibrating,
                progress = if (isCalibrating) throttleProgress else null,
                progressMessage = if (isCalibrating) throttleStepMessage else null,
                onClick = {
                    HapticHelper.performClick(view)
                    isCalibrating = true
                    throttleProgress = 0.10f
                    throttleStepMessage = "Sampling idle resting position..."
                    viewModel.calibrateThrottleDeadband(
                        onProgress = { step, progress ->
                            throttleStepMessage = step
                            throttleProgress = progress
                        },
                        onResult = { success, msg ->
                            isCalibrating = false
                            throttleProgress = if (success) 1.0f else 0.0f
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    )
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// =============================================================================
// 4. 6-AXIS IMU & HILL-HOLD INCLINE ZEROING SUB-PAGE
// =============================================================================

@Composable
private fun ImuHillHoldSubPage(viewModel: TelemetryViewModel) {
    val context = LocalContext.current
    val view = LocalView.current
    val calib by viewModel.calibrationTelemetry.collectAsState()
    val status by viewModel.vehicleStatus.collectAsState()

    var isZeroing by remember { mutableStateOf(false) }
    var imuProgress by remember { mutableFloatStateOf(0f) }
    var imuStepMessage by remember { mutableStateOf("") }

    // Defensive NaN guards to guarantee zero Canvas crashes
    val pitch = if (calib.imuPitchDeg.isNaN()) 0.0 else calib.imuPitchDeg
    val roll = if (calib.imuRollDeg.isNaN()) 0.0 else calib.imuRollDeg
    val safePitchClamped = pitch.coerceIn(-89.0, 89.0)
    val gradePercent = (kotlin.math.tan(Math.toRadians(safePitchClamped)) * 100).coerceIn(-100.0, 100.0)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Prerequisites Card
        item {
            PrerequisitesCard(
                severity = PrerequisiteSeverity.INFO,
                title = "IMU ZEROING PRE-REQUISITES",
                items = listOf(
                    "Park scooter on a certified flat and level surface (garage slab or flat workshop floor; avoid slopes).",
                    "Scooter must be positioned upright on center stand or held strictly vertical (0.0° roll).",
                    "Dismount completely — zero rider weight, pillion, or luggage on seat/floorboard.",
                    "Motor must be idle / off with zero chassis vibration affecting the 6-axis MEMS sensor."
                ),
                warningCallout = "Zeroing on a gradient will bias the Hill-Hold engagement threshold and trigger false tip alerts."
            )
        }

        // Artificial Horizon / Tilt Gauge
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = M3SurfaceContainer),
                border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "6-AXIS ATTITUDE HORIZON",
                        color = M3OnSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Simulated 2D Horizon Bubble
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .background(M3SurfaceContainerHigh, CircleShape)
                            .border(2.dp, M3OutlineVariant, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(140.dp)) {
                            // Center crosshairs
                            drawLine(
                                color = Color.Gray.copy(alpha = 0.4f),
                                start = Offset(0f, size.height / 2),
                                end = Offset(size.width, size.height / 2),
                                strokeWidth = 1.5f
                            )
                            drawLine(
                                color = Color.Gray.copy(alpha = 0.4f),
                                start = Offset(size.width / 2, 0f),
                                end = Offset(size.width / 2, size.height),
                                strokeWidth = 1.5f
                            )

                            // Pitch ladder tick marks (+10 deg, -10 deg)
                            val tickSpacing = (size.height / 6)
                            drawLine(
                                color = Color.Gray.copy(alpha = 0.25f),
                                start = Offset(size.width * 0.35f, size.height / 2 - tickSpacing),
                                end = Offset(size.width * 0.65f, size.height / 2 - tickSpacing),
                                strokeWidth = 1f
                            )
                            drawLine(
                                color = Color.Gray.copy(alpha = 0.25f),
                                start = Offset(size.width * 0.35f, size.height / 2 + tickSpacing),
                                end = Offset(size.width * 0.65f, size.height / 2 + tickSpacing),
                                strokeWidth = 1f
                            )

                            // Dynamic Bubble with guaranteed finite coordinates
                            val maxRadius = (size.width / 2 - 15).coerceAtLeast(10f)
                            val dx = (roll / 30.0 * maxRadius).coerceIn(-maxRadius.toDouble(), maxRadius.toDouble()).toFloat()
                            val dy = (pitch / 30.0 * maxRadius).coerceIn(-maxRadius.toDouble(), maxRadius.toDouble()).toFloat()

                            val bubbleCenter = Offset(
                                (size.width / 2 + if (dx.isNaN()) 0f else dx).coerceIn(15f, size.width - 15f),
                                (size.height / 2 + if (dy.isNaN()) 0f else dy).coerceIn(15f, size.height - 15f)
                            )

                            drawCircle(
                                color = M3Primary,
                                radius = 12f,
                                center = bubbleCenter
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Pitch & Roll Readouts
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("PITCH (GRADE)", color = M3OnSurfaceVariant, fontSize = 10.sp)
                            Text(
                                text = "${String.format(Locale.US, "%.1f", pitch)}° (${String.format(Locale.US, "%.1f", gradePercent)}%)",
                                color = M3OnSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("ROLL (TILT)", color = M3OnSurfaceVariant, fontSize = 10.sp)
                            Text(
                                text = "${String.format(Locale.US, "%.1f", roll)}°",
                                color = M3OnSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // Hill-Hold System Status
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = M3SurfaceContainer),
                border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hill-Hold Incline Assist",
                            color = M3OnSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Auto-engages when pitch exceeds 3.0° on incline and brake is held for 1s.",
                            color = M3OnSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }

                    val isHillHoldTriggered = status.hillHoldActive || Math.abs(pitch) >= 3.0
                    Surface(
                        color = if (isHillHoldTriggered) M3Success.copy(alpha = 0.15f) else M3SurfaceContainerHigh,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (isHillHoldTriggered) M3Success else M3OutlineVariant.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = if (status.hillHoldActive) "ACTIVE" else if (Math.abs(pitch) >= 3.0) "ENGAGED" else "LEVEL (<3.0°)",
                            color = if (isHillHoldTriggered) M3Success else M3OnSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        // Zero-Level Ground Calibration
        item {
            ActionCard(
                title = "Zero-Level Ground Calibration (CAN 0x682)",
                description = "Park scooter upright on level asphalt. Sets current pitch & roll as absolute 0.0° baseline.",
                buttonText = if (isZeroing) "Sampling Level..." else "Zero IMU Level",
                icon = Icons.Default.Explore,
                enabled = !isZeroing,
                progress = if (isZeroing) imuProgress else null,
                progressMessage = if (isZeroing) imuStepMessage else null,
                onClick = {
                    HapticHelper.performClick(view)
                    isZeroing = true
                    imuProgress = 0.10f
                    imuStepMessage = "Verifying level orientation..."
                    viewModel.calibrateImuZeroLevel(
                        onProgress = { step, progress ->
                            imuStepMessage = step
                            imuProgress = progress
                        },
                        onResult = { success, msg ->
                            isZeroing = false
                            imuProgress = if (success) 1.0f else 0.0f
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// =============================================================================
// 5. DYNO & PADDOCK STAND BENCH MODE SUB-PAGE
// =============================================================================

@Composable
private fun DynoBenchModeSubPage(viewModel: TelemetryViewModel) {
    val context = LocalContext.current
    val view = LocalView.current
    val calib by viewModel.calibrationTelemetry.collectAsState()
    val powertrain by viewModel.powertrainState.collectAsState()
    val thermal by viewModel.thermalState.collectAsState()

    var isDynoActive by remember { mutableStateOf(calib.isDynoModeActive) }
    var showWarningDialog by remember { mutableStateOf(false) }

    LaunchedEffect(calib.isDynoModeActive) {
        isDynoActive = calib.isDynoModeActive
    }

    val rpm = if (powertrain.motorRpm.isNaN()) 0.0 else powertrain.motorRpm
    val speed = if (powertrain.speedKmh.isNaN()) 0.0 else powertrain.speedKmh
    val torque = if (powertrain.motorTorqueNm.isNaN()) 0.0 else powertrain.motorTorqueNm
    val current = if (powertrain.motorPhaseCurrentA.isNaN()) 0.0 else powertrain.motorPhaseCurrentA

    if (showWarningDialog) {
        AlertDialog(
            onDismissRequest = { showWarningDialog = false },
            title = { Text("Enable Dyno Bench Mode?") },
            text = {
                Text("Dyno mode completely disables traction control slip cutoffs, kickstand interlocks, and anti-tamper fall alarms. Ensure the rear wheel has 360° clearance on a secure paddock stand.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showWarningDialog = false
                        isDynoActive = true
                        viewModel.setDynoMode(true) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Confirm & Enable", color = M3Error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWarningDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Critical Prerequisites & Paddock Bench Rules Card
        item {
            PrerequisitesCard(
                severity = PrerequisiteSeverity.CRITICAL,
                title = "DYNO BENCH PRE-REQUISITES & HAZARDS",
                items = listOf(
                    "MANDATORY PADDOCK STAND OR ROLLER DYNO: Scooter rear wheel must be fully elevated off the ground or clamped in dyno rollers.",
                    "STRICTLY FOR OFF-ROAD & BENCH DIAGNOSTICS: Never ride on public roads. Kickstand motor cutoffs and traction control slip limits are BYPASSED.",
                    "Maintain minimum 1-meter clear perimeter behind the rear wheel to avoid debris strike or belt snag.",
                    "Wear eye and face protection. Wheel can achieve free-spin speeds exceeding 100+ km/h."
                ),
                warningCallout = "HIGH ROTATIONAL INERTIA HAZARD: Disabling traction limits on an unsecured scooter can cause catastrophic tip-over or projectile injury."
            )
        }

        // Dyno Mode Master Switch
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = M3SurfaceContainer),
                border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Paddock Dyno Mode (gRPC)",
                            color = M3OnSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isDynoActive) "DYNO MODE ACTIVE (Traction Derates Bypassed)" else "ROAD MODE (Normal Safety Active)",
                            color = if (isDynoActive) M3Error else M3Success,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Switch(
                        checked = isDynoActive,
                        onCheckedChange = { checked ->
                            HapticHelper.performClick(view)
                            if (checked) {
                                showWarningDialog = true
                            } else {
                                isDynoActive = false
                                viewModel.setDynoMode(false) { success, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = M3Error,
                            checkedTrackColor = M3Error.copy(alpha = 0.3f)
                        )
                    )
                }
            }
        }

        // Live Free-Spin Bench Telemetry
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = M3SurfaceContainer),
                border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "FREE-SPIN MOTOR TELEMETRY",
                        color = M3OnSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("MOTOR RPM", color = M3OnSurfaceVariant, fontSize = 10.sp)
                            Text(
                                text = "${rpm.toInt()}",
                                color = M3Primary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Column {
                            Text("WHEEL SPEED", color = M3OnSurfaceVariant, fontSize = 10.sp)
                            Text(
                                text = "${String.format(Locale.US, "%.1f", speed)} km/h",
                                color = M3OnSurface,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Column {
                            Text("SHAFT TORQUE", color = M3OnSurfaceVariant, fontSize = 10.sp)
                            Text(
                                text = "${String.format(Locale.US, "%.1f", torque)} Nm",
                                color = M3Success,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = M3OutlineVariant.copy(alpha = 0.25f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Phase Current: ${String.format(Locale.US, "%.1f", current)} A", color = M3OnSurfaceVariant, fontSize = 11.sp)
                        val mcuTemp = if (thermal.mcuTempC.isNaN()) 41.0 else thermal.mcuTempC
                        Text("Inverter Temp: ${mcuTemp.toInt()}°C", color = M3OnSurfaceVariant, fontSize = 11.sp)
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// =============================================================================
// 6. BRAKE LEVER SWITCH & DYNAMIC REGEN SUB-PAGE
// =============================================================================

@Composable
private fun BrakeRegenSubPage(viewModel: TelemetryViewModel) {
    val context = LocalContext.current
    val view = LocalView.current
    val calib by viewModel.calibrationTelemetry.collectAsState()
    val status by viewModel.vehicleStatus.collectAsState()

    var selectedRegenLevel by remember { mutableIntStateOf(status.regenLevel) }
    var isApplyingRegen by remember { mutableStateOf(false) }
    var regenProgress by remember { mutableFloatStateOf(0f) }
    var regenStepMessage by remember { mutableStateOf("") }

    LaunchedEffect(status.regenLevel) {
        selectedRegenLevel = status.regenLevel
    }

    val fVolt = if (calib.frontBrakeVoltageV.isNaN()) (if (calib.frontBrakePressed) 3.12 else 0.82) else calib.frontBrakeVoltageV
    val rVolt = if (calib.rearBrakeVoltageV.isNaN()) (if (calib.rearBrakePressed) 3.15 else 0.81) else calib.rearBrakeVoltageV

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Prerequisites Card
        item {
            PrerequisitesCard(
                severity = PrerequisiteSeverity.INFO,
                title = "BRAKE & REGEN PRE-REQUISITES",
                items = listOf(
                    "Front and Rear brake levers must be COMPLETELY RELEASED during ADC baseline inspection.",
                    "Verify brake fluid reservoir is topped up and hydraulic lines have firm mechanical resistance.",
                    "Scooter must be stationary (0.0 km/h) when writing new regenerative braking maps to VCU.",
                    "Verify both microswitches transition to 'ENGAGED' when levers are squeezed ~2–3 mm."
                ),
                warningCallout = "Writing regen maps while moving at speed is rejected by the VCU safety gateway."
            )
        }

        // Brake Lever Voltage Cards
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = M3SurfaceContainer),
                border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "BRAKE MICROSWITCH SENSORS (CAN 0x320 & 0x15B)",
                        color = M3OnSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Front Brake
                    BrakeStatusRow(
                        title = "Front Hydraulic Brake Lever",
                        isPressed = calib.frontBrakePressed,
                        voltage = fVolt
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Rear Brake
                    BrakeStatusRow(
                        title = "Rear Brake Lever (Regen Blended)",
                        isPressed = calib.rearBrakePressed,
                        voltage = rVolt
                    )
                }
            }
        }

        // Dynamic Regen Level Map
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = M3SurfaceContainer),
                border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "DYNAMIC REGENERATIVE BRAKING MAP (CAN 0x115)",
                        color = M3OnSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Configure deceleration drag and kinetic energy recapture percentage.",
                        color = M3OnSurfaceVariant,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val levels = listOf(
                        0 to "Off (0%) - Free-wheel Coasting",
                        1 to "Low (5%) - Highway Glide",
                        2 to "Medium (10%) - City Commute Default",
                        3 to "High (15%) - 1-Pedal Traffic Assist",
                        4 to "Maximum (20%) - Aggressive Peak Regen"
                    )

                    levels.forEach { (lvl, name) ->
                        val isSelected = selectedRegenLevel == lvl
                        Surface(
                            color = if (isSelected) M3PrimaryContainer else M3SurfaceContainerHigh,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (isSelected) M3Primary else M3OutlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clickable {
                                    HapticHelper.performClick(view)
                                    selectedRegenLevel = lvl
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = name,
                                    color = if (isSelected) M3OnPrimaryContainer else M3OnSurface,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp
                                )
                                if (isSelected) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = M3Primary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    if (isApplyingRegen || regenStepMessage.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = M3PrimaryContainer.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, M3Primary.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = regenStepMessage,
                                        color = M3OnSurface,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "${(regenProgress * 100).toInt()}%",
                                        color = M3Primary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                if (isApplyingRegen) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { regenProgress.coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp)),
                                        color = M3Primary,
                                        trackColor = M3SurfaceContainerHigh
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            HapticHelper.performClick(view)
                            isApplyingRegen = true
                            regenProgress = 0.2f
                            regenStepMessage = "Dispatching regen map..."
                            viewModel.setRegenTorqueLevel(
                                selectedRegenLevel,
                                onProgress = { step, progress ->
                                    regenStepMessage = step
                                    regenProgress = progress
                                },
                                onResult = { success, msg ->
                                    isApplyingRegen = false
                                    regenProgress = if (success) 1.0f else 0.0f
                                    regenStepMessage = msg
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            )
                        },
                        enabled = !isApplyingRegen,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isApplyingRegen) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = M3Primary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(if (isApplyingRegen) "Writing to VCU..." else "Write Regen Map to VCU")
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// =============================================================================
// 7. BATTERY COULOMB COUNTING & SOH CALIBRATION SUB-PAGE
// =============================================================================

@Composable
private fun CoulombSohSubPage(viewModel: TelemetryViewModel) {
    val context = LocalContext.current
    val view = LocalView.current
    val coulomb by viewModel.coulombState.collectAsState()
    val battery by viewModel.batteryState.collectAsState()
    val isLoggingActive by viewModel.isRideLoggingActive.collectAsState()
    val rideDistLogged by viewModel.rideDistLoggedKm.collectAsState()

    val effectiveDist = maxOf(coulomb.currentRideDistanceKm, rideDistLogged)
    val progress = if (coulomb.isCalibrated) 1.0f else (effectiveDist / 15.0).coerceIn(0.0, 1.0).toFloat()
    val progressPct = if (coulomb.isCalibrated) 100 else (progress * 100).toInt()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Prerequisites Card
        item {
            PrerequisitesCard(
                severity = PrerequisiteSeverity.INFO,
                title = "COULOMB INTEGRATION & SOH PRE-REQUISITES",
                items = listOf(
                    "Start test cycle with battery charged to 100% (green charger indicator, resting OCV saturated).",
                    "Ride continuously for at least 15 km without abrupt intermediate shutdowns or auxiliary power cutoffs.",
                    "Maintain normal, mixed riding patterns (avoid continuous 100% Hyper-mode thermal throttling during calibration).",
                    "Do not reset Coulomb baseline while a dynamic test session is actively recording."
                ),
                warningCallout = "Accurate SOH estimation requires continuous integration over substantial depth-of-discharge to reach ±1.5% accuracy."
            )
        }

        // 15 km Dynamic Ride Baseline Card
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = M3SurfaceContainer),
                border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "15 KM DYNAMIC RIDE CALIBRATION",
                        color = M3OnSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Continuous Wh Coulomb integration calculates exact pack capacity and State of Health (SOH) by observing real-world energy draw over a 15 km riding cycle.",
                        color = M3OnSurfaceVariant,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (coulomb.isCalibrated) "CALIBRATED ✓" else "CALIBRATING",
                            color = if (coulomb.isCalibrated) M3Success else M3Primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "${String.format(Locale.US, "%.1f", effectiveDist)} / 15.0 km ($progressPct%)",
                            color = M3Primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = M3Primary,
                        trackColor = M3SurfaceContainerHigh
                    )
                }
            }
        }

        // Live Calculated Health Metrics
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = M3SurfaceContainer),
                border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "MEASURED PACK CAPACITY & SOH",
                        color = M3OnSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("STATE OF HEALTH", color = M3OnSurfaceVariant, fontSize = 10.sp)
                            val soh = if (coulomb.isCalibrated) coulomb.calibratedSohPercent else (if (battery.packSohPercent.isNaN()) 97.4 else battery.packSohPercent)
                            Text(
                                text = "${String.format(Locale.US, "%.1f", soh)}%",
                                color = M3Success,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column {
                            Text("USABLE ENERGY", color = M3OnSurfaceVariant, fontSize = 10.sp)
                            val kwh = if (coulomb.isCalibrated && !coulomb.calibratedCapacityKwh.isNaN()) coulomb.calibratedCapacityKwh else 3.82
                            Text(
                                text = "${String.format(Locale.US, "%.2f", kwh)} kWh",
                                color = M3OnSurface,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column {
                            Text("AVG EFFICIENCY", color = M3OnSurfaceVariant, fontSize = 10.sp)
                            val whKm = if (coulomb.isCalibrated && !coulomb.consumptionWhPerKm.isNaN()) coulomb.consumptionWhPerKm else 27.8
                            Text(
                                text = "${String.format(Locale.US, "%.1f", whKm)} Wh/km",
                                color = M3Primary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Dynamic Ride Test Controls
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = M3SurfaceContainer),
                border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "DYNAMIC 15 KM TEST RIDE",
                        color = M3OnSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isLoggingActive) "Ride logging active. Distance: ${String.format(Locale.US, "%.2f", rideDistLogged)} km." else "Start a test ride session to collect fresh continuous load samples (Logged: ${String.format(Locale.US, "%.2f", rideDistLogged)} km).",
                        color = M3OnSurfaceVariant,
                        fontSize = 11.sp
                    )

                    if (isLoggingActive || rideDistLogged > 0.05) {
                        val rideProgress = (rideDistLogged / 15.0).coerceIn(0.0, 1.0).toFloat()
                        val remainingKm = (15.0 - rideDistLogged).coerceAtLeast(0.0)
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = M3PrimaryContainer.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, M3Primary.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Ride Progress: ${String.format(Locale.US, "%.2f", rideDistLogged)} / 15.00 km",
                                        color = M3OnSurface,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${(rideProgress * 100).toInt()}%",
                                        color = M3Primary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${String.format(Locale.US, "%.2f", remainingKm)} km remaining to complete calibration",
                                    color = M3OnSurfaceVariant,
                                    fontSize = 10.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { rideProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = M3Primary,
                                    trackColor = M3SurfaceContainerHigh
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                HapticHelper.performClick(view)
                                if (isLoggingActive) {
                                    viewModel.stopRideBatteryTest()
                                    Toast.makeText(context, "Dynamic test ride stopped. Baseline updated.", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.startRideBatteryTest()
                                    Toast.makeText(context, "Dynamic test ride started. Go for a ride!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isLoggingActive) "Stop Test Ride" else "Start 15km Test Ride")
                        }
                    }
                }
            }
        }

        // Reset Baseline Action
        item {
            ActionCard(
                title = "Reset Coulomb Baseline & Start New Calibration",
                description = "Erases previously integrated Wh history and starts a fresh 15 km learning cycle for a new battery or firmware update.",
                buttonText = "Reset Calibration",
                icon = Icons.Default.RestartAlt,
                enabled = true,
                onClick = {
                    HapticHelper.performClick(view)
                    viewModel.resetCoulombCalibration()
                    Toast.makeText(context, "Coulomb baseline reset. Ride 15+ km to recalibrate SOH.", Toast.LENGTH_SHORT).show()
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// =============================================================================
// REUSABLE UI HELPERS
// =============================================================================

@Composable
private fun IconButtonBack(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = M3SurfaceContainerHigh,
        shape = CircleShape,
        border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.5f))
    ) {
        Box(
            modifier = Modifier.size(38.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = M3OnSurface,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SafetyCheckItem(label: String, passed: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = M3OnSurface, fontSize = 12.sp)
        Surface(
            color = if (passed) M3Success.copy(alpha = 0.15f) else M3Error.copy(alpha = 0.15f),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                text = if (passed) "PASSED" else "INTERLOCKED",
                color = if (passed) M3Success else M3Error,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun VoltageBarRow(
    label: String,
    voltage: Double,
    minV: Double,
    maxV: Double,
    accent: Color
) {
    val pct = ((voltage - minV) / (maxV - minV)).coerceIn(0.0, 1.0).toFloat()
    val isFault = voltage <= 0.35 || voltage >= 4.50

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = M3OnSurface, fontSize = 12.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isFault) {
                    Surface(
                        color = M3Error.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (voltage <= 0.35) "OPEN" else "SHORT",
                            color = M3Error,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                Text(
                    "${String.format(Locale.US, "%.2f", voltage)} V",
                    color = if (isFault) M3Error else accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { pct },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = if (isFault) M3Error else accent,
            trackColor = M3SurfaceContainerHigh
        )
    }
}

@Composable
private fun ZoneLegendRow(zone: String, voltageRange: String, purpose: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(zone, color = M3OnSurface, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(purpose, color = M3OnSurfaceVariant, fontSize = 10.sp)
        }
        Text(voltageRange, color = M3Primary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun BrakeStatusRow(
    title: String,
    isPressed: Boolean,
    voltage: Double
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(title, color = M3OnSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(
                "Analog ADC: ${String.format(Locale.US, "%.2f", voltage)}V",
                color = M3OnSurfaceVariant,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Surface(
            color = if (isPressed) M3Error.copy(alpha = 0.15f) else M3SurfaceContainerHigh,
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, if (isPressed) M3Error.copy(alpha = 0.5f) else M3OutlineVariant.copy(alpha = 0.4f))
        ) {
            Text(
                text = if (isPressed) "ENGAGED" else "RELEASED",
                color = if (isPressed) M3Error else M3Success,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    description: String,
    buttonText: String,
    icon: ImageVector,
    enabled: Boolean,
    progress: Float? = null,
    progressMessage: String? = null,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = M3SurfaceContainer),
        border = BorderStroke(1.dp, M3OutlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                color = M3OnSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                color = M3OnSurfaceVariant,
                fontSize = 11.sp
            )

            // Active Linear Progress Indicator Banner
            if (progress != null || !progressMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = M3PrimaryContainer.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, M3Primary.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = progressMessage ?: "Executing...",
                                color = M3OnSurface,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            if (progress != null) {
                                Text(
                                    text = "${(progress * 100).toInt()}%",
                                    color = M3Primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        if (progress != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { progress.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = M3Primary,
                                trackColor = M3SurfaceContainerHigh
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onClick,
                enabled = enabled,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, if (enabled) M3Primary else M3Outline.copy(alpha = 0.4f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (enabled) M3PrimaryContainer else Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                if (progress != null && progress in 0.01f..0.99f) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = M3OnPrimaryContainer,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(icon, contentDescription = null, tint = if (enabled) M3OnPrimaryContainer else M3Outline, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = buttonText,
                    color = if (enabled) M3OnPrimaryContainer else M3Outline,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

/**
 * Severity level for calibration pre-requisites.
 */
enum class PrerequisiteSeverity {
    INFO,
    WARNING,
    CRITICAL
}

private data class PrerequisiteStyle(
    val bgColor: Color,
    val borderColor: Color,
    val accentColor: Color,
    val icon: ImageVector,
    val badgeText: String
)

/**
 * Standardized Material 3 card highlighting prerequisites, setup checklists, and hazard warnings.
 */
@Composable
private fun PrerequisitesCard(
    severity: PrerequisiteSeverity,
    title: String,
    items: List<String>,
    modifier: Modifier = Modifier,
    warningCallout: String? = null
) {
    val style = when (severity) {
        PrerequisiteSeverity.CRITICAL -> PrerequisiteStyle(
            bgColor = M3Error.copy(alpha = 0.12f),
            borderColor = M3Error.copy(alpha = 0.55f),
            accentColor = M3Error,
            icon = Icons.Default.Warning,
            badgeText = "CRITICAL HAZARD"
        )
        PrerequisiteSeverity.WARNING -> PrerequisiteStyle(
            bgColor = M3Warning.copy(alpha = 0.12f),
            borderColor = M3Warning.copy(alpha = 0.55f),
            accentColor = M3Warning,
            icon = Icons.Default.Warning,
            badgeText = "MANDATORY REQ"
        )
        PrerequisiteSeverity.INFO -> PrerequisiteStyle(
            bgColor = M3SurfaceContainer,
            borderColor = M3OutlineVariant.copy(alpha = 0.45f),
            accentColor = M3Primary,
            icon = Icons.Default.Security,
            badgeText = "PREREQUISITES"
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = style.bgColor),
        border = BorderStroke(1.dp, style.borderColor)
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = style.icon,
                    contentDescription = null,
                    tint = style.accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = title,
                    color = style.accentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = AppFont,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = style.accentColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, style.accentColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = style.badgeText,
                        color = style.accentColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Check items
            items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${index + 1}.",
                        color = style.accentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = item,
                        color = M3OnSurface,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            // High-visibility Callout Banner
            if (warningCallout != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = if (severity == PrerequisiteSeverity.CRITICAL) M3Error.copy(alpha = 0.22f) else M3Warning.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (severity == PrerequisiteSeverity.CRITICAL) M3Error.copy(alpha = 0.5f) else M3Warning.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (severity == PrerequisiteSeverity.CRITICAL) M3Error else M3Warning,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = warningCallout,
                            color = if (severity == PrerequisiteSeverity.CRITICAL) M3Error else M3Warning,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}
