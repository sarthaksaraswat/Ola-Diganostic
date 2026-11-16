package com.sarthak.olacan.analyzer

import android.content.Context
import android.net.Uri
import com.sarthak.olacan.model.AlertSeverity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.EOFException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.ZipException

/**
 * Detailed per-cell diagnostic metrics computed from CAN log playback.
 */
data class CellDiagnosticInfo(
    val channel: Int,
    val restingVoltageV: Double,
    val minVoltageV: Double,
    val maxSagMv: Double,
    val internalResistanceMOhm: Double,
    val condition: String, // "HEALTHY", "HIGH SAG", "CRITICAL DEGRADED"
    val excessSagPercent: Double
)

/**
 * Master Ride Diagnostic Analysis Report generated from an imported CAN log file.
 */
data class RideDiagnosticReport(
    val fileName: String,
    val fileSizeFormatted: String,
    val totalFrames: Long,
    val durationSeconds: Long,
    val startTimeFormatted: String,
    val endTimeFormatted: String,

    // Powertrain & Driving Metrics
    val startOdometerKm: Double,
    val endOdometerKm: Double,
    val distanceKm: Double,
    val maxSpeedKmh: Double,
    val avgMovingSpeedKmh: Double,
    val maxMotorRpm: Double,

    // Battery Pack Performance
    val startSoc: Double,
    val endSoc: Double,
    val minPackVoltageV: Double,
    val maxPackVoltageV: Double,
    val peakDischargeCurrentA: Double,
    val peakDischargePowerKw: Double,
    val peakDischargeHorsepower: Double,
    val peakRegenCurrentA: Double,
    val peakRegenPowerKw: Double,

    // Cell Balance & Degradation
    val restingCellDeltaMv: Double,
    val peakLoadCellDeltaMv: Double,
    val cells: List<CellDiagnosticInfo>,
    val worstCellChannel: Int,

    // MoveOS Interlocks & Derating
    val socClampOccurrences: Int,
    val socClampDurationSeconds: Double,
    val bmsCellDipFaultsCount: Int,
    val bmsLoadSagWarningsCount: Int,
    val bmsHighDischargeDerateCount: Int,
    val bmsRegenInhibitCount: Int,

    // Thermal Stress
    val startPackTempC: Double,
    val endPackTempC: Double,
    val maxPackTempC: Double,
    val maxPackThermalGradientC: Double,
    val hottestProbeIdx: Int,
    val coolestProbeIdx: Int,
    val maxMotorTempC: Double,
    val maxPduTempC: Double,
    val maxAfeTempC: Double,
    val maxMcuTempC: Double,

    // Critical Alerts Summary
    val loggedAlerts: List<String>,
    val isPartial: Boolean = false
) {
    val hasCriticalFault: Boolean
        get() = bmsCellDipFaultsCount > 0 || socClampOccurrences > 0 ||
            maxMotorTempC >= 115.0 || cells.any { it.condition == "CRITICAL DEGRADED" } ||
            loggedAlerts.any { it.startsWith("${AlertSeverity.CRITICAL}:") }

    val hasWarning: Boolean
        get() = bmsLoadSagWarningsCount > 0 || bmsHighDischargeDerateCount > 0 ||
            bmsRegenInhibitCount > 0 || maxMotorTempC >= 95.0 || maxPduTempC > 85.0 ||
            maxPackTempC > 50.0 || cells.any { it.condition == "HIGH SAG" } ||
            loggedAlerts.any { it.startsWith("${AlertSeverity.WARNING}:") }

    val hasIncompleteData: Boolean
        get() = isPartial || cells.size < 14 ||
            listOf(maxMotorTempC, maxMcuTempC, maxPackTempC, maxPduTempC, maxAfeTempC)
                .any { !it.isFinite() }

    val verdict: String
        get() = when {
            hasCriticalFault -> "CRITICAL FAULT INDICATORS RECORDED"
            hasWarning -> "DIAGNOSTIC WARNINGS RECORDED"
            hasIncompleteData -> "INCOMPLETE TELEMETRY"
            else -> "NO FAULT INDICATORS IN SAMPLED DATA"
        }
}

internal fun formatRideMetric(value: Double, decimals: Int, unit: String): String =
    if (value.isFinite()) String.format(Locale.US, "%.${decimals}f%s", value, unit) else "Unavailable"

object RideLogAnalyzer {

    private fun parseHexByte(hex: String, startIndex: Int): Int {
        if (startIndex + 2 > hex.length) return 0
        val c1 = Character.digit(hex[startIndex], 16)
        val c2 = Character.digit(hex[startIndex + 1], 16)
        return if (c1 >= 0 && c2 >= 0) (c1 shl 4) or c2 else 0
    }

    private fun parseHexLe16(hex: String, byteOffset: Int): Int {
        val i = byteOffset * 2
        val b0 = parseHexByte(hex, i)
        val b1 = parseHexByte(hex, i + 2)
        return b0 or (b1 shl 8)
    }

    private fun parseHexLe32(hex: String, byteOffset: Int): Long {
        val i = byteOffset * 2
        val b0 = parseHexByte(hex, i).toLong()
        val b1 = parseHexByte(hex, i + 2).toLong()
        val b2 = parseHexByte(hex, i + 4).toLong()
        val b3 = parseHexByte(hex, i + 6).toLong()
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    private fun decodeTemp(raw: Int): Double {
        if (raw <= 150 || raw >= 2500) return Double.NaN
        val temp = raw * 0.1 - 50.0
        return if (temp in -35.0..140.0) temp else Double.NaN
    }

    /**
     * Streams and parses an imported .csv or .csv.gz CAN log file, producing
     * a deep diagnostic report in seconds.
     */
    suspend fun analyze(
        context: Context,
        uri: Uri,
        onProgress: (percent: Int, framesProcessed: Long) -> Unit
    ): RideDiagnosticReport? = withContext(Dispatchers.Default) {
        val contentResolver = context.contentResolver
        val inputStream: InputStream = contentResolver.openInputStream(uri) ?: return@withContext null

        // Resolve file name & size
        var fileName = "Imported_CAN_Log.csv"
        var fileSize = 0L
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIdx >= 0) fileName = cursor.getString(nameIdx) ?: fileName
                    if (sizeIdx >= 0) fileSize = cursor.getLong(sizeIdx)
                }
            }
        } catch (_: Exception) {}

        val bufferedIn = inputStream.buffered(64 * 1024)
        bufferedIn.mark(2)
        val magic0 = bufferedIn.read()
        val magic1 = bufferedIn.read()
        bufferedIn.reset()
        val isGzip = (magic0 == 0x1F && magic1 == 0x8B) || fileName.endsWith(".gz", ignoreCase = true)

        val reader = try {
            val decoded = if (isGzip) GZIPInputStream(bufferedIn, 32 * 1024) else bufferedIn
            BufferedReader(InputStreamReader(decoded, StandardCharsets.UTF_8), 32 * 1024)
        } catch (e: Exception) {
            bufferedIn.close()
            throw e
        }

        // Analysis state accumulators
        var totalFrames = 0L
        var firstTimestampMs = -1L
        var lastTimestampMs = -1L

        var startOdo = Double.NaN
        var endOdo = Double.NaN
        var maxSpeed = 0.0
        var speedSum = 0.0
        var movingSamplesCount = 0L
        var maxRpm = 0.0

        var startSoc = Double.NaN
        var lastValidSoc = Double.NaN
        var minBusVoltage = Double.MAX_VALUE
        var maxBusVoltage = Double.NEGATIVE_INFINITY

        var peakDischargeCurrent = 0.0 // Negative on Ola (e.g. -207.56 A)
        var peakRegenCurrent = 0.0     // Positive on Ola (e.g. +73.81 A)

        // Cell voltages
        val currentCells = DoubleArray(14) { Double.NaN }
        val restCellVoltages = DoubleArray(14) { Double.NaN }
        val minCellVoltagesUnderLoad = DoubleArray(14) { Double.MAX_VALUE }
        var minRestCellDelta = Double.MAX_VALUE
        var maxLoadCellDelta = 0.0

        // MoveOS 1% Clamp Tracking
        var isCurrentlyInClamp = false
        var clampStartMs = 0L
        var totalClampDurationMs = 0L
        var clampOccurrences = 0

        // BMS Derate occurrences (0x122)
        var bmsCellDipFaultsCount = 0
        var bmsLoadSagWarningsCount = 0
        var bmsHighDischargeDerateCount = 0
        var bmsRegenInhibitCount = 0

        // Thermals
        var startPackTemp = Double.NaN
        var endPackTemp = Double.NaN
        var maxPackTemp = Double.NEGATIVE_INFINITY
        val packProbes = DoubleArray(6) { Double.NaN }
        var maxPackGradient = 0.0
        var hottestProbe = 0
        var coolestProbe = 0

        var maxMotorTemp = Double.NEGATIVE_INFINITY
        var maxPduTemp = Double.NEGATIVE_INFINITY
        var maxAfeTemp = Double.NEGATIVE_INFINITY
        var maxMcuTemp = Double.NEGATIVE_INFINITY

        val capturedAlerts = mutableListOf<String>()
        var lastPackCurrent = Double.NaN
        var lastCurrentTime: Long? = null
        var lastBusVoltage = Double.NaN
        var lastVoltageTime: Long? = null
        var peakDischargePowerKw = Double.NaN
        var peakRegenPowerKw = Double.NaN
        var isPartial = false
        val loadCurrentAtMinVoltage = DoubleArray(14) { Double.NaN }

        fun observePower() {
            val voltageTime = lastVoltageTime ?: return
            val currentTime = lastCurrentTime ?: return
            if (kotlin.math.abs(voltageTime - currentTime) > 1000L) return
            val power = lastBusVoltage * lastPackCurrent / 1000.0
            val discharge = maxOf(0.0, -power)
            val regen = maxOf(0.0, power)
            if (peakDischargePowerKw.isNaN() || discharge > peakDischargePowerKw) peakDischargePowerKw = discharge
            if (peakRegenPowerKw.isNaN() || regen > peakRegenPowerKw) peakRegenPowerKw = regen
        }

        try {
            reader.use { r ->
                while (true) {
                    val line = r.readLine() ?: break
                    val parts = line.split(',', limit = 4)
                    if (parts.size != 4) continue
                    val ts = parts[0].trim().toLongOrNull()?.takeIf { it >= 0 } ?: continue
                    val idStr = parts[1].trim()
                    if (idStr == "ALERT") {
                        capturedAlerts.add(parts[3].trim())
                        continue
                    }

                    val canId = (if (idStr.startsWith("0x", ignoreCase = true)) idStr.drop(2) else idStr)
                        .toIntOrNull(16)?.takeIf { it in 0..0x1FFFFFFF } ?: continue
                    val dlc = parts[2].trim().toIntOrNull()?.takeIf { it in 0..8 } ?: continue
                    val hexData = parts[3].trim()
                    if (hexData.length != dlc * 2 || hexData.any { Character.digit(it, 16) < 0 }) continue
                    val requiredBytes = when (canId) {
                        0x150 -> 2
                        0x160 -> 3
                        0x120, 0x1A7, 0x1A8, 0x1AA, 0x356 -> 4
                        0x122, 0x140 -> 6
                        0x1A4, 0x1A5, 0x1A6, 0x1A9, 0x313 -> 8
                        else -> 0
                    }
                    if (dlc < requiredBytes) continue

                    totalFrames++
                    if (firstTimestampMs < 0) firstTimestampMs = ts
                    lastTimestampMs = maxOf(lastTimestampMs, ts)
                    if (totalFrames % 25000L == 0L) {
                        val percent = if (fileSize > 0) {
                            ((totalFrames * 45L * 100L) / (fileSize * 6L)).coerceIn(1L, 95L).toInt()
                        } else 50
                        onProgress(percent, totalFrames)
                    }

                    when (canId) {
                        0x120 -> {
                            // SOC: le16(2) * 0.01
                            val rawSoc = parseHexLe16(hexData, 2) * 0.01
                            if (rawSoc in 0.0..100.0) {
                                if (startSoc.isNaN() && rawSoc > 2.0) startSoc = rawSoc
                                if (rawSoc > 2.0) lastValidSoc = rawSoc

                                // MoveOS 1% Clamp Interlock:
                                if (rawSoc in 0.9..1.1 && lastBusVoltage > 43.0 &&
                                    lastVoltageTime?.let { ts - it in 0L..1000L } == true) {
                                    if (!isCurrentlyInClamp) {
                                        isCurrentlyInClamp = true
                                        clampOccurrences++
                                        clampStartMs = ts
                                    }
                                } else if (rawSoc > 2.0 && isCurrentlyInClamp) {
                                    isCurrentlyInClamp = false
                                    if (clampStartMs > 0) {
                                        totalClampDurationMs += (ts - clampStartMs)
                                    }
                                }
                            }

                            // Current in 0x120 byte 0..1: le16(0) * 0.01 - 300.0
                            val cur = parseHexLe16(hexData, 0) * 0.01 - 300.0
                            if (cur in -250.0..150.0) {
                                lastPackCurrent = cur
                                lastCurrentTime = ts
                                observePower()
                                if (cur < peakDischargeCurrent) peakDischargeCurrent = cur
                                if (cur > peakRegenCurrent) peakRegenCurrent = cur
                            }
                        }

                        0x140 -> {
                            // Battery Current byte 4..5: le16(4) * 0.01 - 300.0
                            val cur = parseHexLe16(hexData, 4) * 0.01 - 300.0
                            if (cur in -250.0..150.0) {
                                lastPackCurrent = cur
                                lastCurrentTime = ts
                                observePower()
                                if (cur < peakDischargeCurrent) peakDischargeCurrent = cur
                                if (cur > peakRegenCurrent) peakRegenCurrent = cur
                            }
                        }

                        0x150 -> {
                            // Bus Voltage byte 0..1: le16(0) * 0.001
                            val v = parseHexLe16(hexData, 0) * 0.001
                            if (v in 35.0..60.0) {
                                lastBusVoltage = v
                                lastVoltageTime = ts
                                observePower()
                                if (v < minBusVoltage) minBusVoltage = v
                                if (v > maxBusVoltage) maxBusVoltage = v
                            }
                        }

                        0x122 -> {
                            // Overall Battery Faults
                            val b2 = parseHexByte(hexData, 4)
                            val b3 = parseHexByte(hexData, 6)
                            val b4 = parseHexByte(hexData, 8)
                            val b5 = parseHexByte(hexData, 10)

                            if ((b2 and 0x01) != 0) bmsCellDipFaultsCount++
                            if ((b3 and 0x01) != 0) bmsLoadSagWarningsCount++
                            if ((b4 and 0x01) != 0) bmsHighDischargeDerateCount++
                            if ((b5 and 0x01) != 0) bmsRegenInhibitCount++
                        }

                        0x160 -> {
                            val effT = decodeTemp(parseHexLe16(hexData, 1))
                            if (!effT.isNaN()) {
                                if (startPackTemp.isNaN()) startPackTemp = effT
                                endPackTemp = effT
                                if (effT > maxPackTemp) maxPackTemp = effT
                            }
                        }

                        0x1A4 -> {
                            for (i in 0 until 4) {
                                packProbes[i] = decodeTemp(parseHexLe16(hexData, i * 2))
                            }
                        }

                        0x1A7 -> {
                            packProbes[4] = decodeTemp(parseHexLe16(hexData, 0))
                            packProbes[5] = decodeTemp(parseHexLe16(hexData, 2))

                            val valid = packProbes.filter { !it.isNaN() }
                            if (valid.size >= 2) {
                                val minT = valid.minOrNull() ?: continue
                                val maxT = valid.maxOrNull() ?: continue
                                val grad = maxT - minT
                                if (grad > maxPackGradient) {
                                    maxPackGradient = grad
                                    hottestProbe = packProbes.indexOfFirst { Math.abs(it - maxT) < 0.001 } + 1
                                    coolestProbe = packProbes.indexOfFirst { Math.abs(it - minT) < 0.001 } + 1
                                }
                            }
                        }

                        0x1A8 -> {
                            val pduT = decodeTemp(parseHexLe16(hexData, 0))
                            val afeT = decodeTemp(parseHexLe16(hexData, 2))
                            if (!pduT.isNaN() && pduT > maxPduTemp) maxPduTemp = pduT
                            if (!afeT.isNaN() && afeT > maxAfeTemp) maxAfeTemp = afeT
                        }

                        0x1A5 -> {
                            for (i in 0 until 4) {
                                val v = parseHexLe16(hexData, i * 2) * 0.001
                                if (v in 1.5..4.5) currentCells[i] = v
                            }
                        }

                        0x1A6 -> {
                            for (i in 0 until 4) {
                                val v = parseHexLe16(hexData, i * 2) * 0.001
                                if (v in 1.5..4.5) currentCells[4 + i] = v
                            }
                        }

                        0x1A9 -> {
                            for (i in 0 until 4) {
                                val v = parseHexLe16(hexData, i * 2) * 0.001
                                if (v in 1.5..4.5) currentCells[8 + i] = v
                            }
                        }

                        0x1AA -> {
                            val v13 = parseHexLe16(hexData, 0) * 0.001
                            val v14 = parseHexLe16(hexData, 2) * 0.001
                            if (v13 in 1.5..4.5) currentCells[12] = v13
                            if (v14 in 1.5..4.5) currentCells[13] = v14

                            val validCells = currentCells.filter { !it.isNaN() && it > 1.5 }
                            if (validCells.size == 14) {
                                val cur = if (lastCurrentTime?.let { ts - it in 0L..1000L } == true) lastPackCurrent else Double.NaN
                                if (Math.abs(cur) < 5.0) {
                                    // Standstill resting balance
                                    for (i in 0 until 14) restCellVoltages[i] = currentCells[i]
                                    val maxV = validCells.maxOrNull() ?: 0.0
                                    val minV = validCells.minOrNull() ?: 0.0
                                    val restDelta = (maxV - minV) * 1000.0
                                    if (restDelta < minRestCellDelta) minRestCellDelta = restDelta
                                } else if (cur < -30.0) {
                                    // Motor draw discharge sag
                                    for (i in 0 until 14) {
                                        if (currentCells[i] < minCellVoltagesUnderLoad[i]) {
                                            minCellVoltagesUnderLoad[i] = currentCells[i]
                                            loadCurrentAtMinVoltage[i] = -cur
                                        }
                                    }
                                    val maxV = validCells.maxOrNull() ?: 0.0
                                    val minV = validCells.minOrNull() ?: 0.0
                                    val loadDelta = (maxV - minV) * 1000.0
                                    if (loadDelta > maxLoadCellDelta) maxLoadCellDelta = loadDelta
                                }
                            }
                        }

                        0x313 -> {
                            // Speed byte 0..1: scale 0.1
                            val spd = parseHexLe16(hexData, 0) * 0.1
                            if (spd in 0.0..180.0) {
                                if (spd > maxSpeed) maxSpeed = spd
                                if (spd > 3.0) {
                                    speedSum += spd
                                    movingSamplesCount++
                                }
                            }

                            val mcuT = parseHexLe16(hexData, 2) * 0.01
                            if (mcuT in 1.0..150.0 && mcuT > maxMcuTemp) maxMcuTemp = mcuT

                            val motorT = parseHexLe16(hexData, 4) * 0.01
                            if (motorT in 1.0..170.0 && motorT > maxMotorTemp) maxMotorTemp = motorT

                            val rpm = parseHexLe16(hexData, 6).toDouble()
                            if (rpm in 0.0..15000.0 && rpm > maxRpm) maxRpm = rpm
                        }

                        0x356 -> {
                            val odoM = parseHexLe32(hexData, 0)
                            if (odoM != 0xFFFFFFFFL && odoM > 0L) {
                                val odoKm = odoM / 1000.0
                                if (startOdo.isNaN()) startOdo = odoKm
                                endOdo = odoKm
                            }
                        }
                    }
                }
            }
        } catch (e: EOFException) {
            if (!isGzip) throw e
            isPartial = true
        } catch (e: ZipException) {
            if (!isGzip) throw e
            isPartial = true
        }

        // Close final clamp duration if file ended while in clamp
        if (isCurrentlyInClamp && clampStartMs > 0 && lastTimestampMs > clampStartMs) {
            totalClampDurationMs += (lastTimestampMs - clampStartMs)
        }

        onProgress(100, totalFrames)

        if (totalFrames == 0L) return@withContext null

        val durationSec = if (firstTimestampMs > 0 && lastTimestampMs >= firstTimestampMs) {
            (lastTimestampMs - firstTimestampMs) / 1000L
        } else 0L

        val distanceKm = if (!startOdo.isNaN() && !endOdo.isNaN() && endOdo >= startOdo) {
            endOdo - startOdo
        } else 0.0

        val avgSpeed = if (movingSamplesCount > 0) speedSum / movingSamplesCount else 0.0

        // Per-cell metrics calculation
        val cellDiagnostics = mutableListOf<CellDiagnosticInfo>()
        var worstCellIdx = -1
        var maxObservedSag = 0.0

        val nominalPackSag = 385.0 // Reference sag at 150 A.

        for (i in 0 until 14) {
            val restV = restCellVoltages[i]
            val minV = minCellVoltagesUnderLoad[i]
            val loadCurrent = loadCurrentAtMinVoltage[i]
            if (!restV.isFinite() || minV == Double.MAX_VALUE || !loadCurrent.isFinite()) continue

            val sagMv = maxOf(0.0, (restV - minV) * 1000.0)
            val riMOhm = sagMv / loadCurrent

            val condition = when {
                riMOhm > 3.8 || sagMv > 750.0 -> "CRITICAL DEGRADED"
                riMOhm > 2.8 || sagMv > 500.0 -> "HIGH SAG"
                else -> "HEALTHY"
            }

            val excessPercent = if (sagMv > nominalPackSag) ((sagMv - nominalPackSag) / nominalPackSag) * 100.0 else 0.0

            if (sagMv > maxObservedSag) {
                maxObservedSag = sagMv
                worstCellIdx = i
            }

            cellDiagnostics.add(
                CellDiagnosticInfo(
                    channel = i + 1,
                    restingVoltageV = restV,
                    minVoltageV = minV,
                    maxSagMv = sagMv,
                    internalResistanceMOhm = riMOhm,
                    condition = condition,
                    excessSagPercent = excessPercent
                )
            )
        }

        val sizeFormatted = when {
            fileSize >= 1024 * 1024 -> String.format(Locale.US, "%.2f MB", fileSize / (1024.0 * 1024.0))
            fileSize >= 1024 -> String.format(Locale.US, "%.1f KB", fileSize / 1024.0)
            else -> "$fileSize Bytes"
        }

        val sdf = java.text.SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.US)
        val startFormatted = if (firstTimestampMs > 0) sdf.format(java.util.Date(firstTimestampMs)) else "---"
        val endFormatted = if (lastTimestampMs > 0) sdf.format(java.util.Date(lastTimestampMs)) else "---"

        val trueRestDelta = if (minRestCellDelta == Double.MAX_VALUE) Double.NaN else minRestCellDelta
        val trueLoadDelta = if (loadCurrentAtMinVoltage.none { it.isFinite() }) Double.NaN else maxLoadCellDelta

        RideDiagnosticReport(
            fileName = fileName,
            fileSizeFormatted = sizeFormatted,
            totalFrames = totalFrames,
            durationSeconds = durationSec,
            startTimeFormatted = startFormatted,
            endTimeFormatted = endFormatted,
            startOdometerKm = if (startOdo.isNaN()) 0.0 else startOdo,
            endOdometerKm = if (endOdo.isNaN()) 0.0 else endOdo,
            distanceKm = distanceKm,
            maxSpeedKmh = maxSpeed,
            avgMovingSpeedKmh = avgSpeed,
            maxMotorRpm = maxRpm,
            startSoc = if (startSoc.isNaN()) 0.0 else startSoc,
            endSoc = if (lastValidSoc.isNaN()) 0.0 else lastValidSoc,
            minPackVoltageV = if (minBusVoltage == Double.MAX_VALUE) Double.NaN else minBusVoltage,
            maxPackVoltageV = if (maxBusVoltage.isFinite()) maxBusVoltage else Double.NaN,
            peakDischargeCurrentA = peakDischargeCurrent,
            peakDischargePowerKw = peakDischargePowerKw,
            peakDischargeHorsepower = peakDischargePowerKw * 1.34102,
            peakRegenCurrentA = peakRegenCurrent,
            peakRegenPowerKw = peakRegenPowerKw,
            restingCellDeltaMv = trueRestDelta,
            peakLoadCellDeltaMv = trueLoadDelta,
            cells = cellDiagnostics,
            worstCellChannel = worstCellIdx + 1,
            socClampOccurrences = clampOccurrences,
            socClampDurationSeconds = totalClampDurationMs / 1000.0,
            bmsCellDipFaultsCount = bmsCellDipFaultsCount,
            bmsLoadSagWarningsCount = bmsLoadSagWarningsCount,
            bmsHighDischargeDerateCount = bmsHighDischargeDerateCount,
            bmsRegenInhibitCount = bmsRegenInhibitCount,
            startPackTempC = if (startPackTemp.isNaN()) 0.0 else startPackTemp,
            endPackTempC = if (endPackTemp.isNaN()) 0.0 else endPackTemp,
            maxPackTempC = if (maxPackTemp.isFinite()) maxPackTemp else Double.NaN,
            maxPackThermalGradientC = maxPackGradient,
            hottestProbeIdx = if (hottestProbe > 0) hottestProbe else 1,
            coolestProbeIdx = if (coolestProbe > 0) coolestProbe else 1,
            maxMotorTempC = if (maxMotorTemp.isFinite()) maxMotorTemp else Double.NaN,
            maxPduTempC = if (maxPduTemp.isFinite()) maxPduTemp else Double.NaN,
            maxAfeTempC = if (maxAfeTemp.isFinite()) maxAfeTemp else Double.NaN,
            maxMcuTempC = if (maxMcuTemp.isFinite()) maxMcuTemp else Double.NaN,
            loggedAlerts = capturedAlerts.distinct(),
            isPartial = isPartial
        )
    }
}
