package com.sarthak.olacan.analyzer

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.sarthak.olacan.model.CanFrame
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPOutputStream

object BatteryHealthManager {

    private const val PREFS_NAME = "ola_battery_health_prefs"
    private const val FACTORY_USABLE_WH = 3700.0 // 3.70 kWh factory usable window
    private const val FACTORY_GROSS_WH = 3980.0 // 3.98 kWh nominal gross Ola S1 Pro Gen 2
    private const val PACK_INTERNAL_RESISTANCE_OHM = 0.035 // ~35 mOhm pack total Ri

    private val batteryExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        com.sarthak.olacan.util.AppLogManager.error("BatteryHealthManager", "Battery test error: ${throwable.message}", throwable)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + batteryExceptionHandler)

    private var appContext: Context? = null
    private var prefs: SharedPreferences? = null

    // Real Hardware vs Chassis Cycles
    private var packHardwareCycles: Long = 0L // Stored in BMS EEPROM (this pack)
    private var vehicleChassisCycles: Int = 0 // Stored in VCU (cumulative across all packs)
    private var acSessions: Int = 0
    private var dcFastSessions: Int = 0

    // Live Test StateFlows
    private val _liveStats = MutableStateFlow(BatteryHealthLiveStats())
    val liveStats = _liveStats.asStateFlow()

    private val _lastReport = MutableStateFlow<BatteryHealthReport?>(null)
    val lastReport = _lastReport.asStateFlow()

    private val _chargeCheck = MutableStateFlow<ChargeCheckResult?>(null)
    val chargeCheck = _chargeCheck.asStateFlow()

    // Logging components for unified log file
    private var testLogFile: File? = null
    private var gzipStream: GZIPOutputStream? = null
    private var gzipWriter: BufferedWriter? = null
    private var totalFramesWritten: Long = 0L
    private var syncFrameCounter: Long = 0L

    // Integration accumulators
    private var lastFrameTimeMs: Long = 0L
    private var lastVoltageV: Double = 51.8
    private var lastCurrentA: Double = 0.0

    private var startOdoKm: Double = Double.NaN
    private var currentOdoKm: Double = Double.NaN
    private var startVoltageV: Double = Double.NaN
    private var startSocVal: Double = Double.NaN
    private var currentSocVal: Double = Double.NaN

    private var minVoltageSeenV: Double = Double.MAX_VALUE
    private var maxVoltageSeenV: Double = Double.MIN_VALUE

    private var dischargeEnergyWh: Double = 0.0
    private var regenEnergyWh: Double = 0.0
    private var dischargeAh: Double = 0.0
    private var regenAh: Double = 0.0
    private var internalLossWh: Double = 0.0

    private var testStartTimeMs: Long = 0L
    private var totalActiveDurationMs: Long = 0L
    private var lastActiveResumeTimeMs: Long = 0L

    // Cell degradation metrics during test
    private val minCellVoltages = DoubleArray(14) { Double.MAX_VALUE }
    private val maxCellSagMv = DoubleArray(14) { 0.0 }
    private var worstCellIdx: Int = 0
    private var socClampCount: Int = 0
    private var cellSagFaultCount: Int = 0

    // Vehicle dynamics and charger detection
    private var currentSpeedKmh: Double = 0.0
    private var consecutiveStandDownFrames: Int = 0
    private var stationaryChargingCount: Int = 0
    private var isChargerConnected: Boolean = false

    fun init(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        restoreStateFromPrefs()
    }

    /**
     * Checks if the scooter is genuinely 100% saturated.
     * On Ola 14S NMC, full saturation occurs at >= 57.2V (4.09V+ per cell) and 100% SOC.
     */
    fun check100PercentCharge(currentVoltage: Double, currentSoc: Double): ChargeCheckResult {
        val result = if (currentSoc.isNaN() || currentVoltage.isNaN()) {
            ChargeCheckResult(
                isFullyCharged = false,
                voltageV = currentVoltage,
                socPercent = currentSoc,
                message = "Waiting for CAN bus telemetry... Please connect to scooter Wi-Fi to verify charge."
            )
        } else if (currentSoc < 99.0) {
            ChargeCheckResult(
                isFullyCharged = false,
                voltageV = currentVoltage,
                socPercent = currentSoc,
                message = "Scooter is at ${String.format(Locale.US, "%.1f", currentSoc)}% SOC. For an accurate State-of-Health test, please charge until 100%."
            )
        } else if (currentVoltage < 56.5) {
            // MoveOS SOC drift: reports 100% but actual pack voltage has not reached full saturation (~57.5V)
            ChargeCheckResult(
                isFullyCharged = false,
                voltageV = currentVoltage,
                socPercent = currentSoc,
                message = "MoveOS reports 100% SOC, but pack voltage is only ${String.format(Locale.US, "%.1f", currentVoltage)}V (14S NMC full saturation is ~57.8V). BMS calibration is drifted. Please reboot your scooter (hold Power + Reverse for 10s) and charge until charger cuts off."
            )
        } else {
            ChargeCheckResult(
                isFullyCharged = true,
                voltageV = currentVoltage,
                socPercent = currentSoc,
                message = "Pack verified 100% saturated (${String.format(Locale.US, "%.2f", currentVoltage)}V, ${String.format(Locale.US, "%.1f", currentSoc)}% SOC). Ready to start Battery Health Test!"
            )
        }
        _chargeCheck.value = result
        return result
    }

    fun dismissChargeCheck() {
        _chargeCheck.value = null
    }

    /**
     * Starts a fresh Battery Health Test session.
     */
    fun startTest(context: Context, initialVoltage: Double, initialSoc: Double, initialOdo: Double) {
        val ctx = appContext ?: context.applicationContext
        appContext = ctx

        // Reset accumulators
        testStartTimeMs = System.currentTimeMillis()
        lastActiveResumeTimeMs = testStartTimeMs
        totalActiveDurationMs = 0L

        startSocVal = if (!initialSoc.isNaN()) initialSoc else 100.0
        currentSocVal = startSocVal
        startVoltageV = if (!initialVoltage.isNaN()) initialVoltage else 57.6
        lastVoltageV = startVoltageV
        startOdoKm = if (!initialOdo.isNaN()) initialOdo else 0.0
        currentOdoKm = startOdoKm

        minVoltageSeenV = startVoltageV
        maxVoltageSeenV = startVoltageV

        dischargeEnergyWh = 0.0
        regenEnergyWh = 0.0
        dischargeAh = 0.0
        regenAh = 0.0
        internalLossWh = 0.0
        totalFramesWritten = 0L
        worstCellIdx = 0
        socClampCount = 0
        cellSagFaultCount = 0
        currentSpeedKmh = 0.0
        consecutiveStandDownFrames = 0
        stationaryChargingCount = 0
        isChargerConnected = false

        for (i in 0 until 14) {
            minCellVoltages[i] = Double.MAX_VALUE
            maxCellSagMv[i] = 0.0
        }

        // Initialize single unified log file in cache
        initUnifiedLogFile(ctx)

        _liveStats.value = BatteryHealthLiveStats(
            status = BatteryTestStatus.ACTIVE,
            progressPercent = 0.0,
            startSoc = startSocVal,
            currentSoc = currentSocVal,
            deltaSoc = 0.0,
            startVoltageV = startVoltageV,
            currentVoltageV = startVoltageV,
            distanceKm = 0.0,
            durationSeconds = 0L,
            activeRideSeconds = 0L,
            isAutoPausedParked = false
        )

        saveStateToPrefs()
    }

    private fun initUnifiedLogFile(context: Context) {
        closeLogWriters()
        val file = File(context.cacheDir, "active_battery_health_test.csv.gz")
        testLogFile = file

        val fos = FileOutputStream(file)
        val bos = BufferedOutputStream(fos, 64 * 1024)
        val gzos = GZIPOutputStream(bos)
        val writer = BufferedWriter(OutputStreamWriter(gzos, StandardCharsets.UTF_8), 16 * 1024)

        val header = "timestamp_ms,id_hex,dlc,data_hex\n"
        writer.write(header)
        writer.flush()

        gzipStream = gzos
        gzipWriter = writer
    }

    /**
     * Pauses test (either manually or auto-pause when parked/stand-down).
     */
    fun pauseTest(isAutoPaused: Boolean = false) {
        if (_liveStats.value.status != BatteryTestStatus.ACTIVE) return
        val now = System.currentTimeMillis()
        if (lastActiveResumeTimeMs > 0) {
            totalActiveDurationMs += (now - lastActiveResumeTimeMs)
            lastActiveResumeTimeMs = 0L
        }

        try {
            gzipWriter?.flush()
            gzipStream?.flush()
        } catch (_: Exception) {}

        _liveStats.value = _liveStats.value.copy(
            status = BatteryTestStatus.PAUSED,
            isAutoPausedParked = isAutoPaused
        )
        saveStateToPrefs()
    }

    /**
     * Resumes the ongoing Battery Health Test across the exact same unified log.
     */
    fun resumeTest(context: Context) {
        val currentStatus = _liveStats.value.status
        if (currentStatus != BatteryTestStatus.PAUSED) return

        lastActiveResumeTimeMs = System.currentTimeMillis()
        lastFrameTimeMs = lastActiveResumeTimeMs

        // Reconnect writers if stream was closed
        val file = testLogFile
        if (gzipWriter == null && file != null && file.exists()) {
            try {
                // If writers were cleared, append or reopen
                val fos = FileOutputStream(file, true)
                val bos = BufferedOutputStream(fos, 64 * 1024)
                val gzos = GZIPOutputStream(bos)
                gzipStream = gzos
                gzipWriter = BufferedWriter(OutputStreamWriter(gzos, StandardCharsets.UTF_8), 16 * 1024)
            } catch (_: Exception) {}
        }

        _liveStats.value = _liveStats.value.copy(
            status = BatteryTestStatus.ACTIVE,
            isAutoPausedParked = false
        )
        saveStateToPrefs()
    }

    /**
     * Resets / Cancels the test to start fresh.
     */
    fun resetTest(context: Context) {
        closeLogWriters()
        testLogFile?.delete()
        testLogFile = null
        clearPrefs()

        currentSpeedKmh = 0.0
        consecutiveStandDownFrames = 0
        stationaryChargingCount = 0
        isChargerConnected = false

        _liveStats.value = BatteryHealthLiveStats(status = BatteryTestStatus.IDLE)
        _lastReport.value = null
        _chargeCheck.value = null
    }

    /**
     * Feed CAN frame into the unified battery health test tracker.
     */
    fun onCanFrameReceived(frame: CanFrame) {
        try {
            val status = _liveStats.value.status
            if (status != BatteryTestStatus.ACTIVE) return

            // Downsample 100 Hz MCU/VCU heartbeat sync frames (0x11C, 0x640) to save ~20% log size
            if (frame.id == 0x11C || frame.id == 0x640) {
                if (++syncFrameCounter % 10L != 0L) {
                    return
                }
            }

            // Write frame to the single unified log file
            val writer = gzipWriter
            if (writer != null) {
                try {
                    writer.write("${frame.timestampMs},${frame.idHex},${frame.dlc},${frame.compactDataHex}\n")
                    totalFramesWritten++
                    if (totalFramesWritten % 1500L == 0L) {
                        writer.flush()
                        gzipStream?.flush()
                    }
                } catch (_: Exception) {}
            }

            val now = frame.timestampMs
            val d = frame.data

            when (frame.id) {
            0x150 -> {
                // Bus voltage
                if (d.size >= 2) {
                    val rawV = (d[0].toInt() and 0xFF) or ((d[1].toInt() and 0xFF) shl 8)
                    val v = rawV * 0.001
                    if (v in 35.0..62.0) {
                        lastVoltageV = v
                        if (v < minVoltageSeenV) minVoltageSeenV = v
                        if (v > maxVoltageSeenV) maxVoltageSeenV = v
                    }
                }
            }

            0x120 -> {
                // SOC and Current
                if (d.size >= 4) {
                    val rawSoc = ((d[2].toInt() and 0xFF) or ((d[3].toInt() and 0xFF) shl 8)) * 0.01
                    if (rawSoc in 0.0..100.0) {
                        if (rawSoc in 0.8..1.2 && lastVoltageV > 43.0) {
                            socClampCount++
                            // MoveOS 1% cell sag derate: do not overwrite currentSocVal during temporary clamp
                        } else {
                            currentSocVal = rawSoc
                        }
                    }
                    val rawCur = ((d[0].toInt() and 0xFF) or ((d[1].toInt() and 0xFF) shl 8)) * 0.01 - 300.0
                    processCurrentSample(rawCur, now)
                }
            }

            0x140 -> {
                // High precision current
                if (d.size >= 6) {
                    val rawCur = ((d[4].toInt() and 0xFF) or ((d[5].toInt() and 0xFF) shl 8)) * 0.01 - 300.0
                    processCurrentSample(rawCur, now)
                }
            }

            0x122 -> {
                // BMS cell sag derate
                if (d.size >= 3) {
                    val b2 = d[2].toInt() and 0xFF
                    if ((b2 and 0x01) != 0) cellSagFaultCount++
                }
            }

            0x313 -> {
                // Vehicle Speed (scale 0.1)
                if (d.size >= 2) {
                    val rawSpd = ((d[0].toInt() and 0xFF) or ((d[1].toInt() and 0xFF) shl 8)) * 0.1
                    if (rawSpd in 0.0..200.0) {
                        currentSpeedKmh = rawSpd
                    }
                }
            }

            0x1A5 -> {
                // Cells 1..4 (14S NMC pack)
                if (d.size >= 8) {
                    recordCellVoltage(0, ((d[0].toInt() and 0xFF) or ((d[1].toInt() and 0xFF) shl 8)) * 0.001)
                    recordCellVoltage(1, ((d[2].toInt() and 0xFF) or ((d[3].toInt() and 0xFF) shl 8)) * 0.001)
                    recordCellVoltage(2, ((d[4].toInt() and 0xFF) or ((d[5].toInt() and 0xFF) shl 8)) * 0.001)
                    recordCellVoltage(3, ((d[6].toInt() and 0xFF) or ((d[7].toInt() and 0xFF) shl 8)) * 0.001)
                }
            }

            0x1A6 -> {
                // Cells 5..8
                if (d.size >= 8) {
                    recordCellVoltage(4, ((d[0].toInt() and 0xFF) or ((d[1].toInt() and 0xFF) shl 8)) * 0.001)
                    recordCellVoltage(5, ((d[2].toInt() and 0xFF) or ((d[3].toInt() and 0xFF) shl 8)) * 0.001)
                    recordCellVoltage(6, ((d[4].toInt() and 0xFF) or ((d[5].toInt() and 0xFF) shl 8)) * 0.001)
                    recordCellVoltage(7, ((d[6].toInt() and 0xFF) or ((d[7].toInt() and 0xFF) shl 8)) * 0.001)
                }
            }

            0x1A9 -> {
                // Cells 9..12
                if (d.size >= 8) {
                    recordCellVoltage(8, ((d[0].toInt() and 0xFF) or ((d[1].toInt() and 0xFF) shl 8)) * 0.001)
                    recordCellVoltage(9, ((d[2].toInt() and 0xFF) or ((d[3].toInt() and 0xFF) shl 8)) * 0.001)
                    recordCellVoltage(10, ((d[4].toInt() and 0xFF) or ((d[5].toInt() and 0xFF) shl 8)) * 0.001)
                    recordCellVoltage(11, ((d[6].toInt() and 0xFF) or ((d[7].toInt() and 0xFF) shl 8)) * 0.001)
                }
            }

            0x1AA -> {
                // Cells 13..14
                if (d.size >= 4) {
                    recordCellVoltage(12, ((d[0].toInt() and 0xFF) or ((d[1].toInt() and 0xFF) shl 8)) * 0.001)
                    recordCellVoltage(13, ((d[2].toInt() and 0xFF) or ((d[3].toInt() and 0xFF) shl 8)) * 0.001)
                }
            }

            0x356 -> {
                // Odometer
                if (d.size >= 4) {
                    val odoM = (d[0].toLong() and 0xFFL) or
                            ((d[1].toLong() and 0xFFL) shl 8) or
                            ((d[2].toLong() and 0xFFL) shl 16) or
                            ((d[3].toLong() and 0xFFL) shl 24)
                    if (odoM != 0xFFFFFFFFL && odoM > 0L) {
                        val km = odoM / 1000.0
                        if (startOdoKm.isNaN()) startOdoKm = km
                        currentOdoKm = km
                    }
                }
            }

            0x683 -> {
                // Side stand detection: debounced auto-pause test when side-stand is sustained down
                // Debounced across 5 consecutive frames (~500ms) to reject pothole switch vibrations
                if (d.isNotEmpty()) {
                    val stand = (d[0].toInt() and 0x03) == 1
                    if (stand) {
                        consecutiveStandDownFrames++
                        if (consecutiveStandDownFrames >= 5 && _liveStats.value.status == BatteryTestStatus.ACTIVE) {
                            pauseTest(isAutoPaused = true)
                        }
                    } else {
                        consecutiveStandDownFrames = 0
                    }
                }
            }

            0x6D8 -> {
                // True physical hardware cycles inside the BMS EEPROM
                if (d.size >= 4) {
                    val cycles = (d[0].toLong() and 0xFFL) or
                            ((d[1].toLong() and 0xFFL) shl 8) or
                            ((d[2].toLong() and 0xFFL) shl 16) or
                            ((d[3].toLong() and 0xFFL) shl 24)
                    if (cycles in 1..5000) packHardwareCycles = cycles
                    val dc = (d[0].toInt() and 0xFF) or ((d[1].toInt() and 0xFF) shl 8)
                    if (dc in 0..5000) dcFastSessions = dc
                }
            }

            0x6E0 -> {
                // Cumulative chassis cycles tracked by VCU across all packs
                if (d.size >= 6) {
                    val c = (d[2].toInt() and 0xFF) or ((d[3].toInt() and 0xFF) shl 8)
                    if (c in 1..10000) vehicleChassisCycles = c
                    val ac = (d[4].toInt() and 0xFF) or ((d[5].toInt() and 0xFF) shl 8)
                    if (ac in 0..10000) acSessions = ac
                }
            }
        }
    } catch (_: Throwable) {}
}

    private fun recordCellVoltage(channelIdx: Int, v: Double) {
        if (channelIdx !in 0..13 || v !in 1.5..4.5) return
        if (v < minCellVoltages[channelIdx]) {
            minCellVoltages[channelIdx] = v
        }
        val sagMv = Math.max(0.0, (4.15 - v) * 1000.0)
        if (sagMv > maxCellSagMv[channelIdx]) {
            maxCellSagMv[channelIdx] = sagMv
        }
        var minIdx = 0
        var lowestV = Double.MAX_VALUE
        for (i in 0 until 14) {
            if (minCellVoltages[i] < lowestV) {
                lowestV = minCellVoltages[i]
                minIdx = i
            }
        }
        worstCellIdx = minIdx
    }

    private fun processCurrentSample(currentA: Double, nowMs: Long) {
        if (currentA !in -250.0..150.0) return
        lastCurrentA = currentA

        if (lastFrameTimeMs > 0 && nowMs > lastFrameTimeMs) {
            val deltaMs = nowMs - lastFrameTimeMs
            // Guard against large time jumps
            if (deltaMs in 1..2000) {
                val deltaHours = deltaMs / 3600000.0
                val voltage = lastVoltageV
                val powerWatts = Math.abs(currentA * voltage)

                if (currentA < -0.5) {
                    // Motor Draw (Discharge)
                    val wh = powerWatts * deltaHours
                    val ah = Math.abs(currentA) * deltaHours
                    dischargeEnergyWh += wh
                    dischargeAh += ah

                    // Internal I^2 * R loss estimate
                    val lossWatts = (currentA * currentA) * PACK_INTERNAL_RESISTANCE_OHM
                    internalLossWh += (lossWatts * deltaHours)

                    // Active motor draw confirms scooter is running under its own power
                    stationaryChargingCount = 0
                    isChargerConnected = false
                } else if (currentA > 0.5) {
                    if (currentSpeedKmh > 1.5) {
                        // True Kinetic Regenerative Braking (scooter is moving downhill or decelerating)
                        val wh = powerWatts * deltaHours
                        val ah = currentA * deltaHours
                        regenEnergyWh += wh
                        regenAh += ah
                        stationaryChargingCount = 0
                        isChargerConnected = false
                    } else {
                        // Stationary Charging (Grid Wall AC or DC Hypercharging)
                        // Do NOT accumulate into kinetic regen energy/Ah, as that would deduct from net Wh and corrupt SOH!
                        stationaryChargingCount++
                        if (stationaryChargingCount >= 5) {
                            isChargerConnected = true
                            if (_liveStats.value.status == BatteryTestStatus.ACTIVE) {
                                pauseTest(isAutoPaused = true)
                            }
                        }
                    }
                }
            }
        }
        lastFrameTimeMs = nowMs
    }

    /**
     * Periodic ticker (every 500ms) to compute live stats & progress.
     */
    fun updateLiveCalculations() {
        if (_liveStats.value.status == BatteryTestStatus.IDLE || _liveStats.value.status == BatteryTestStatus.COMPLETED) return

        val now = System.currentTimeMillis()
        val activeMs = totalActiveDurationMs + if (_liveStats.value.status == BatteryTestStatus.ACTIVE && lastActiveResumeTimeMs > 0) {
            now - lastActiveResumeTimeMs
        } else 0L

        val totalDurationMs = if (testStartTimeMs > 0) now - testStartTimeMs else 0L

        val netWh = Math.max(0.0, dischargeEnergyWh - regenEnergyWh)
        val netAhVal = Math.max(0.0, dischargeAh - regenAh)

        val deltaSoc = if (!startSocVal.isNaN() && !currentSocVal.isNaN()) {
            Math.max(0.0, startSocVal - currentSocVal)
        } else 0.0

        // Progress: Depth of discharge from 100% (e.g. 100% -> 75% = 25% progress)
        val progress = deltaSoc.coerceIn(0.0, 100.0)

        // Estimated full pack capacity & SOH % (extrapolated once at least 5% SOC is discharged)
        val estCapacityWh = if (deltaSoc >= 5.0) {
            (netWh / deltaSoc) * 100.0
        } else {
            Double.NaN
        }

        val estSoh = if (!estCapacityWh.isNaN()) {
            ((estCapacityWh / FACTORY_USABLE_WH) * 100.0).coerceIn(40.0, 105.0)
        } else {
            Double.NaN
        }

        val dist = if (!startOdoKm.isNaN() && !currentOdoKm.isNaN() && currentOdoKm >= startOdoKm) {
            currentOdoKm - startOdoKm
        } else 0.0

        val efficiency = if (dist > 0.5) netWh / dist else 0.0

        _liveStats.value = _liveStats.value.copy(
            progressPercent = progress,
            currentSoc = currentSocVal,
            deltaSoc = deltaSoc,
            currentVoltageV = lastVoltageV,
            dischargeWh = dischargeEnergyWh,
            regenWh = regenEnergyWh,
            netEnergyWh = netWh,
            dischargeAh = dischargeAh,
            regenAh = regenAh,
            netAh = netAhVal,
            distanceKm = dist,
            efficiencyWhPerKm = efficiency,
            estimatedSohPercent = estSoh,
            estimatedFullCapacityWh = estCapacityWh,
            durationSeconds = totalDurationMs / 1000L,
            activeRideSeconds = activeMs / 1000L,
            isAutoPausedParked = _liveStats.value.isAutoPausedParked,
            isChargerConnected = isChargerConnected,
            currentSpeedKmh = currentSpeedKmh,
            activeLogFramesCount = totalFramesWritten
        )
    }

    /**
     * Completes the Battery Health Test, finalizes the single unified log file,
     * and produces the definitive SOH Diagnostic Report.
     */
    suspend fun completeTest(context: Context): BatteryHealthReport? = withContext(Dispatchers.IO) {
        val currentStatus = _liveStats.value.status
        if (currentStatus == BatteryTestStatus.IDLE || currentStatus == BatteryTestStatus.COMPLETED) return@withContext null

        val deltaSoc = if (!startSocVal.isNaN() && !currentSocVal.isNaN()) Math.max(0.0, startSocVal - currentSocVal) else 0.0

        // Strict Guard: At least 3.0% discharge depth is mathematically required to extrapolate SOH
        // Do NOT close log writers or finalize session if user tapped complete too early!
        if (deltaSoc < 3.0) {
            return@withContext null
        }

        pauseTest(isAutoPaused = false)
        closeLogWriters()

        val tempFile = testLogFile ?: return@withContext null

        val netWh = Math.max(0.0, dischargeEnergyWh - regenEnergyWh)
        val netAhVal = Math.max(0.0, dischargeAh - regenAh)

        val timeTag = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(testStartTimeMs))
        val targetFileName = "OlaCAN_BatteryHealth_$timeTag.csv.gz"

        // Export unified log to Downloads/OlaCAN with fallback to internal storage to guarantee zero data loss
        val uri = saveFileToDownloads(context, tempFile, targetFileName, "application/gzip")
        val finalLogPath = if (uri != null) {
            tempFile.delete()
            testLogFile = null
            "Downloads/OlaCAN/$targetFileName"
        } else {
            val fallbackDir = File(context.filesDir, "OlaCAN_BatteryHealth").apply { mkdirs() }
            val fallbackFile = File(fallbackDir, targetFileName)
            tempFile.copyTo(fallbackFile, overwrite = true)
            tempFile.delete()
            testLogFile = null
            fallbackFile.absolutePath
        }

        val fullCapacityWh = (netWh / deltaSoc) * 100.0
        val measuredSoh = ((fullCapacityWh / FACTORY_USABLE_WH) * 100.0).coerceIn(40.0, 105.0)

        val healthGrade = when {
            measuredSoh >= 94.0 -> "GRADE A+ (EXCELLENT HEALTH)"
            measuredSoh >= 88.0 -> "GRADE A (GOOD / MINIMAL AGING)"
            measuredSoh >= 80.0 -> "GRADE B (FAIR / EXPECTED DEGRADATION)"
            else -> "GRADE C (SIGNIFICANT DEGRADATION - SERVICE ADVISORY)"
        }

        val totalDist = if (!startOdoKm.isNaN() && !currentOdoKm.isNaN() && currentOdoKm >= startOdoKm) currentOdoKm - startOdoKm else 0.0
        val avgWhPerKm = if (totalDist > 0.5) netWh / totalDist else 0.0
        val regenContrib = if (dischargeEnergyWh > 0.0) (regenEnergyWh / dischargeEnergyWh) * 100.0 else 0.0

        val totalEnergyInput = netWh + internalLossWh
        val efficiency = if (totalEnergyInput > 0.0) (netWh / totalEnergyInput) * 100.0 else 96.5

        val totalDuration = (System.currentTimeMillis() - testStartTimeMs) / 1000L
        val durMins = totalDuration / 60
        val durSecs = totalDuration % 60
        val durationFormatted = "${durMins}m ${durSecs}s"

        val report = BatteryHealthReport(
            testDateFormatted = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.US).format(Date(testStartTimeMs)),
            durationFormatted = durationFormatted,
            logFileName = targetFileName,
            logFilePath = finalLogPath,
            totalFrames = totalFramesWritten,
            startSoc = startSocVal,
            endSoc = currentSocVal,
            deltaSoc = deltaSoc,
            startBusVoltageV = startVoltageV,
            endBusVoltageV = lastVoltageV,
            minBusVoltageV = if (minVoltageSeenV < 100.0) minVoltageSeenV else 48.0,
            maxBusVoltageV = if (maxVoltageSeenV > 0.0) maxVoltageSeenV else 57.6,
            grossDischargeWh = dischargeEnergyWh,
            grossDischargeKwh = dischargeEnergyWh / 1000.0,
            regenWh = regenEnergyWh,
            regenKwh = regenEnergyWh / 1000.0,
            netEnergyOutputWh = netWh,
            netEnergyOutputKwh = netWh / 1000.0,
            netDischargeAh = netAhVal,
            estimatedFullCapacityWh = fullCapacityWh,
            estimatedFullCapacityKwh = fullCapacityWh / 1000.0,
            factoryNominalWh = FACTORY_GROSS_WH,
            factoryUsableWh = FACTORY_USABLE_WH,
            calculatedSohPercent = measuredSoh,
            healthGrade = healthGrade,
            packHardwareCycles = packHardwareCycles,
            vehicleChassisCycles = vehicleChassisCycles,
            acChargeSessions = acSessions,
            dcFastChargeSessions = dcFastSessions,
            estimatedInternalLossWh = internalLossWh,
            batteryEfficiencyPercent = efficiency,
            totalDistanceKm = totalDist,
            avgConsumptionWhPerKm = avgWhPerKm,
            regenContributionPercent = regenContrib,
            worstCellChannel = worstCellIdx + 1,
            minCellVoltageRecordedV = minCellVoltages.minOrNull()?.takeIf { it < 5.0 } ?: 3.12,
            maxCellSagRecordedMv = maxCellSagMv.maxOrNull() ?: 0.0,
            socClampOccurrences = socClampCount,
            bmsCellSagFaultsCount = cellSagFaultCount
        )

        withContext(Dispatchers.Main) {
            _lastReport.value = report
            _liveStats.value = _liveStats.value.copy(
                status = BatteryTestStatus.COMPLETED,
                estimatedSohPercent = measuredSoh,
                estimatedFullCapacityWh = fullCapacityWh
            )
        }

        clearPrefs()
        report
    }

    private fun closeLogWriters() {
        try {
            gzipWriter?.flush()
            gzipWriter?.close()
            gzipStream?.finish()
            gzipStream?.close()
        } catch (_: Exception) {}
        gzipWriter = null
        gzipStream = null
    }

    private fun saveFileToDownloads(context: Context, sourceFile: File, targetName: String, mimeType: String): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, targetName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/OlaCAN")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { outStream ->
                        sourceFile.inputStream().use { inStream ->
                            inStream.copyTo(outStream)
                        }
                    }
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                }
                uri
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val targetDir = File(downloadsDir, "OlaCAN").apply { mkdirs() }
                val targetFile = File(targetDir, targetName)
                sourceFile.copyTo(targetFile, overwrite = true)
                Uri.fromFile(targetFile)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun saveStateToPrefs() {
        prefs?.edit()?.apply {
            putString("status", _liveStats.value.status.name)
            putLong("testStartTimeMs", testStartTimeMs)
            putLong("totalActiveDurationMs", totalActiveDurationMs)
            putFloat("startSocVal", startSocVal.toFloat())
            putFloat("currentSocVal", currentSocVal.toFloat())
            putFloat("startVoltageV", startVoltageV.toFloat())
            putFloat("lastVoltageV", lastVoltageV.toFloat())
            putFloat("dischargeEnergyWh", dischargeEnergyWh.toFloat())
            putFloat("regenEnergyWh", regenEnergyWh.toFloat())
            putFloat("dischargeAh", dischargeAh.toFloat())
            putFloat("regenAh", regenAh.toFloat())
            putFloat("startOdoKm", startOdoKm.toFloat())
            putFloat("currentOdoKm", currentOdoKm.toFloat())
            putLong("totalFramesWritten", totalFramesWritten)
            apply()
        }
    }

    private fun restoreStateFromPrefs() {
        val p = prefs ?: return
        val statusName = p.getString("status", BatteryTestStatus.IDLE.name) ?: BatteryTestStatus.IDLE.name
        val status = try { BatteryTestStatus.valueOf(statusName) } catch (_: Exception) { BatteryTestStatus.IDLE }

        if (status == BatteryTestStatus.ACTIVE || status == BatteryTestStatus.PAUSED) {
            testStartTimeMs = p.getLong("testStartTimeMs", 0L)
            totalActiveDurationMs = p.getLong("totalActiveDurationMs", 0L)
            startSocVal = p.getFloat("startSocVal", 100f).toDouble()
            currentSocVal = p.getFloat("currentSocVal", 100f).toDouble()
            startVoltageV = p.getFloat("startVoltageV", 57.6f).toDouble()
            lastVoltageV = p.getFloat("lastVoltageV", 57.6f).toDouble()
            dischargeEnergyWh = p.getFloat("dischargeEnergyWh", 0f).toDouble()
            regenEnergyWh = p.getFloat("regenEnergyWh", 0f).toDouble()
            dischargeAh = p.getFloat("dischargeAh", 0f).toDouble()
            regenAh = p.getFloat("regenAh", 0f).toDouble()
            startOdoKm = p.getFloat("startOdoKm", 0f).toDouble()
            currentOdoKm = p.getFloat("currentOdoKm", 0f).toDouble()
            totalFramesWritten = p.getLong("totalFramesWritten", 0L)

            // When restored from kill, put in PAUSED so user can explicitly resume
            _liveStats.value = BatteryHealthLiveStats(
                status = BatteryTestStatus.PAUSED,
                startSoc = startSocVal,
                currentSoc = currentSocVal,
                startVoltageV = startVoltageV,
                currentVoltageV = lastVoltageV,
                dischargeWh = dischargeEnergyWh,
                regenWh = regenEnergyWh,
                netEnergyWh = Math.max(0.0, dischargeEnergyWh - regenEnergyWh),
                dischargeAh = dischargeAh,
                regenAh = regenAh,
                netAh = Math.max(0.0, dischargeAh - regenAh),
                distanceKm = Math.max(0.0, currentOdoKm - startOdoKm),
                durationSeconds = (System.currentTimeMillis() - testStartTimeMs) / 1000L,
                activeRideSeconds = totalActiveDurationMs / 1000L,
                activeLogFramesCount = totalFramesWritten
            )
        }
    }

    private fun clearPrefs() {
        prefs?.edit()?.clear()?.apply()
    }
}
