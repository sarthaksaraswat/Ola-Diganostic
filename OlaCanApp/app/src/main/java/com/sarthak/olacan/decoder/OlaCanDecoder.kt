package com.sarthak.olacan.decoder

import com.sarthak.olacan.model.AlertSeverity
import com.sarthak.olacan.model.BatteryState
import com.sarthak.olacan.model.CalibrationTelemetry
import com.sarthak.olacan.model.CanFrame
import com.sarthak.olacan.model.ChargingSessionTelemetry
import com.sarthak.olacan.model.PowertrainState
import com.sarthak.olacan.model.ThermalState
import com.sarthak.olacan.model.VehicleAlert
import com.sarthak.olacan.model.VehicleControlsState
import com.sarthak.olacan.model.VehicleStatus
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance, thread-safe CAN frame decoder implementing verified formulas for Ola S1 (Gen 1 & Gen 2).
 */
class OlaCanDecoder {

    // CAN IDs
    companion object {
        const val ID_NODE_ABSENT = 0x064
        const val ID_MCU_DATA_2 = 0x113
        const val ID_SOC = 0x120
        const val ID_BATTERY_FAULT = 0x122
        const val ID_CURRENT = 0x140
        const val ID_VOLTAGE = 0x150
        const val ID_TEMP = 0x160
        const val ID_CELL_LIMITS = 0x170
        const val ID_PACK_TEMPS_1_4 = 0x1A4
        const val ID_CELLS_1_4 = 0x1A5
        const val ID_CELLS_5_8 = 0x1A6
        const val ID_PACK_TEMPS_5_6 = 0x1A7
        const val ID_AUX = 0x1A8
        const val ID_CELLS_9_12 = 0x1A9
        const val ID_CELLS_13_16 = 0x1AA
        const val ID_THROTTLE_STATUS = 0x210
        const val ID_MOTOR = 0x313
        const val ID_MCU_FAULTS = 0x333
        const val ID_DISPLAY = 0x352
        const val ID_DISPLAY_INFO = 0x353
        const val ID_ODOMETER = 0x356
        const val ID_VCU_DEBUG_1 = 0x670
        const val ID_SAFETY = 0x683
        const val ID_SAFETY_ALERT = 0x6B0
        const val ID_BATTERY_SERIAL = 0x110
        const val ID_BMS_LIMITS = 0x130
        const val ID_WHEEL_SPEED = 0x15B
        const val ID_MOTOR_TORQUE = 0x223
        const val ID_VCU_SOM_TEMP = 0x64B
        const val ID_BRICK_SOH = 0x659
        const val ID_IMU_DYNAMICS = 0x682
        const val ID_DRIVE_STATE = 0x685
        const val ID_VCU_SERIAL = 0x689
        const val ID_BATTERY_CYCLES = 0x6D8
        const val ID_LIFETIME_AH = 0x6DA
        const val ID_LIFETIME_CYCLES_VCU = 0x6E0
        const val ID_MODE_RANGE = 0x6F4
        const val ID_CHARGER_STATUS = 0x213
        const val ID_CHARGER_CONNECTION = 0x62D
        const val ID_CHARGER_PARAMETER_5 = 0x180
        const val ID_VEHICLE_LOCK_BCM = 0x144
        const val ID_CHARGER_TIME = 0x190
        const val ID_CHARGED_ENERGY_MODE = 0x6DE
        const val ID_MAX_CELL_DEV_CHARGE = 0x6E2
        const val ID_BALANCING_STATS = 0x6E4
        const val ID_TOTAL_ENERGY = 0x6E5
        const val ID_BMS_CONTACTOR = 0x11C
        const val ID_MCU_BRAKE = 0x320
        const val ID_AUTO_TUNING_STATUS = 0x335
        const val ID_HANDLEBAR_BUTTONS = 0x6AC
        const val ID_DIGITAL_OUTPUT = 0x6BF
        const val ID_ANALOG_INPUT_4 = 0x6C2
        const val ID_ANALOG_INPUT_6 = 0x6C3
        const val ID_ANALOG_INPUT_8 = 0x6C4
        const val ID_ANALOG_INPUT_3 = 0x6C5

        private fun u8(d: ByteArray, i: Int): Int =
            if (i < d.size) d[i].toInt() and 0xFF else 0

        private fun le16(d: ByteArray, i: Int): Int =
            if (i + 1 < d.size) (d[i].toInt() and 0xFF) or ((d[i + 1].toInt() and 0xFF) shl 8) else 0

        private fun le16s(d: ByteArray, i: Int): Short =
            if (i + 1 < d.size) (((d[i].toInt() and 0xFF) or ((d[i + 1].toInt() and 0xFF) shl 8))).toShort() else 0

        private fun le32(d: ByteArray, i: Int): Long =
            if (i + 3 < d.size) {
                ((d[i].toLong() and 0xFFL)) or
                        ((d[i + 1].toLong() and 0xFFL) shl 8) or
                        ((d[i + 2].toLong() and 0xFFL) shl 16) or
                        ((d[i + 3].toLong() and 0xFFL) shl 24)
            } else 0L

        /**
         * Validates temperature sensor counts.
         * Disconnected NTC sensors typically report 0x0000 (-50°C) or 0xFFFF (>6000°C).
         */
        private fun decodeTemp(raw: Int): Double {
            if (raw <= 150 || raw >= 2500) return Double.NaN
            val temp = raw * 0.1 - 50.0
            return if (temp in -35.0..140.0) temp else Double.NaN
        }
    }

    // State accumulators
    private val cellVoltages = DoubleArray(14) { Double.NaN }
    private val restingCellVoltages = DoubleArray(14) { Double.NaN }
    private val cellInternalResistances = DoubleArray(14) { 0.0 } // in mOhm
    private val cellPeakSagMv = DoubleArray(14) { 0.0 } // in mV
    @Volatile private var restingCellDeltaMv: Double = Double.NaN
    @Volatile private var peakLoadCellDeltaMv: Double = Double.NaN
    @Volatile private var criticalCellFloorBreached: Boolean = false
    @Volatile private var criticalCellFloorNumber: Int = 0

    // 1% SOC Clamp Detection
    @Volatile private var lastPlausibleSoc: Double = Double.NaN
    @Volatile private var is1PctClampActive: Boolean = false
    @Volatile private var clampedTrueSocEst: Double = Double.NaN

    // Thermal monitoring
    private val packProbes = DoubleArray(6) { Double.NaN }
    @Volatile private var packThermalGrad: Double = Double.NaN
    @Volatile private var hottestProbeIdx: Int = 0
    @Volatile private var coolestProbeIdx: Int = 0

    @Volatile private var soc: Double = Double.NaN
    @Volatile private var displaySoc: Double = Double.NaN
    @Volatile private var soh: Double = Double.NaN
    @Volatile private var busVoltage: Double = Double.NaN
    @Volatile private var packCurrent: Double = Double.NaN
    @Volatile private var cellMinMv: Double = Double.NaN
    @Volatile private var cellMaxMv: Double = Double.NaN

    @Volatile private var effectiveTemp: Double = Double.NaN
    @Volatile private var minTemp: Double = Double.NaN
    @Volatile private var maxTemp: Double = Double.NaN
    @Volatile private var mcuTemp: Double = Double.NaN
    @Volatile private var motorTemp: Double = Double.NaN
    @Volatile private var pduTemp: Double = Double.NaN
    @Volatile private var afeTemp: Double = Double.NaN

    @Volatile private var motorRpm: Double = Double.NaN
    @Volatile private var speedKmh: Double = Double.NaN
    @Volatile private var throttlePercent: Double = Double.NaN
    @Volatile private var throttleVoltage: Double = Double.NaN
    @Volatile private var calibratedThrottleRestV: Double = 0.95
    @Volatile private var calibratedThrottleDeadbandMarginV: Double = 0.12

    fun setCalibratedThrottleRest(restVoltage: Double, margin: Double = 0.12) {
        if (!restVoltage.isNaN() && restVoltage in 0.6..1.4) {
            calibratedThrottleRestV = restVoltage
            calibratedThrottleDeadbandMarginV = margin.coerceIn(0.08, 0.25)
            if (!throttleVoltage.isNaN()) {
                throttlePercent = computeThrottlePercent(throttleVoltage)
            }
        }
    }

    private fun computeThrottlePercent(rawV: Double): Double {
        if (rawV.isNaN() || rawV <= 0.35) return 0.0
        val rest = calibratedThrottleRestV
        val margin = calibratedThrottleDeadbandMarginV
        val deadbandMin = rest - margin
        val deadbandMax = rest + margin

        return when {
            rawV in deadbandMin..deadbandMax -> 0.0 // Idle neutral deadband
            rawV < deadbandMin -> {
                // Reverse twist (forced regen / reverse assist): rest down to 0.55V
                val span = (deadbandMin - 0.55).coerceAtLeast(0.20)
                val revPct = ((rawV - deadbandMin) / span) * 20.0
                if (revPct > -1.5) 0.0 else revPct.coerceIn(-25.0, 0.0)
            }
            rawV > 4.5 -> {
                updateAlert(
                    id = "throttleShortRail",
                    title = "Throttle Sensor Out of Range",
                    description = "Throttle sensor voltage (${String.format(java.util.Locale.US, "%.2f", rawV)}V) exceeds 4.5V upper limit",
                    severity = AlertSeverity.CRITICAL,
                    component = "MCU",
                    sourceCanId = "0x113",
                    isActive = true
                )
                0.0
            }
            else -> {
                // Forward drive sweep: deadbandMax up to 4.05V
                val span = (4.05 - deadbandMax).coerceAtLeast(2.0)
                val fwdPct = ((rawV - deadbandMax) / span) * 100.0
                // Clamps mechanical grip friction / dead center drift below 2.8% strictly to 0.0% Neutral
                if (fwdPct < 2.8) 0.0 else fwdPct.coerceIn(0.0, 100.0)
            }
        }
    }
    @Volatile private var cruiseActive: Boolean = false

    @Volatile private var odometerKm: Double = Double.NaN
    @Volatile private var regenLevel: Int = 0
    @Volatile private var hillHold: Boolean = false
    @Volatile private var fallDetected: Boolean = false
    @Volatile private var towAlert: Boolean = false
    @Volatile private var sideStandDown: Boolean = false
    @Volatile private var isScooterLocked: Boolean = false
    @Volatile private var isBootOpen: Boolean = false

    // Live Vehicle Controls & Switch Cluster State (for Guided Auto-Diagnostics)
    @Volatile private var rightBrakePressed: Boolean = false
    @Volatile private var leftBrakePressed: Boolean = false
    @Volatile private var hornPressed: Boolean = false
    @Volatile private var rightIndicatorActive: Boolean = false
    @Volatile private var leftIndicatorActive: Boolean = false
    @Volatile private var highBeamActive: Boolean = false
    @Volatile private var lowBeamActive: Boolean = false
    @Volatile private var brakeLightActive: Boolean = false
    @Volatile private var speakerActive: Boolean = false
    @Volatile private var mainContactorClosed: Boolean = false
    @Volatile private var isChargingActive: Boolean = false
    @Volatile private var isChargerPluggedIn: Boolean = false

    // Charging Insights & Live Session Telemetry
    @Volatile private var chargingTimeMaxMin: Int = 0
    @Volatile private var chargingRatePercent: Double = Double.NaN
    @Volatile private var timeToChargeSc80Min: Int = 0
    @Volatile private var timeToFullChargeSc100Min: Int = 0
    @Volatile private var timeToChargeFc80Min: Int = 0
    @Volatile private var timeToChargeFc100Min: Int = 0
    @Volatile private var chargerModeName: String = "Normal Charging Session"
    @Volatile private var chargerAvailableVoltage: Double = Double.NaN
    @Volatile private var chargerAvailableCurrent: Double = Double.NaN
    @Volatile private var lifetimeModeAmphrCharged: Double = Double.NaN
    @Volatile private var lifetimeModeKwhrCharged: Double = Double.NaN
    @Volatile private var maxCellDeviationDuringChargeMv: Double = Double.NaN
    @Volatile private var balancingCyclesCount: Int = 0
    @Volatile private var totalAvailableEnergyWhr: Double = Double.NaN

    // Live Charging Session Accumulator
    @Volatile private var sessionIsCharging: Boolean = false
    @Volatile private var sessionStartTimeMs: Long = 0L
    @Volatile private var sessionLastTickMs: Long = 0L
    @Volatile private var sessionStartSoc: Double = Double.NaN
    @Volatile private var sessionStartTemp: Double = Double.NaN
    @Volatile private var sessionAccumulatedKwh: Double = 0.0
    @Volatile private var sessionAccumulatedAh: Double = 0.0
    @Volatile private var sessionMaxDeltaMv: Double = 0.0

    // Reverse Engineered MoveOS CAN State (0x130, 0x6F4, 0x223, 0x110, 0x64B, etc.)
    @Volatile private var bmsMaxChargeCurrentA: Double = Double.NaN
    @Volatile private var bmsMaxDischargeCurrentA: Double = Double.NaN
    @Volatile private var bmsNominalCapacityAh: Double = Double.NaN
    @Volatile private var packHardwareCycles: Long = 0L // 0x6D8 Physical cycles of this pack
    @Volatile private var vehicleChassisCycles: Int = 0 // 0x6E0 Cumulative cycles across vehicle lifetime
    @Volatile private var acChargeSessions: Int = 0 // 0x6E0 Home AC slow charging count
    @Volatile private var dcFastChargeSessions: Int = 0 // 0x6D8 Hypercharger fast charging count
    @Volatile private var lifetimeAhThroughput: Long = 0L // 0x6DA Lifetime Ah throughput
    @Volatile private var batterySerialSeq0: String = ""
    @Volatile private var batterySerialSeq1: String = ""
    @Volatile private var batterySerial: String = ""
    private var brickSohList: List<Int> = emptyList()

    @Volatile private var motorTorqueNm: Double = Double.NaN
    @Volatile private var motorPhaseCurrentA: Double = Double.NaN
    @Volatile private var wheelSpeedKmh: Double = Double.NaN
    @Volatile private var wheelSlipPercent: Double = Double.NaN
    @Volatile private var operationalState: String = ""
    @Volatile private var driveMode: String = ""
    @Volatile private var vcuHardwareSerial: String = ""
    private val vcuSerialParts = Array(4) { "" }

    @Volatile private var ecoRangeKm: Int = 0
    @Volatile private var normalRangeKm: Int = 0
    @Volatile private var sportRangeKm: Int = 0
    @Volatile private var hyperRangeKm: Int = 0

    @Volatile private var ambientTemp: Double = Double.NaN
    @Volatile private var vcuBoardTemp: Double = Double.NaN
    @Volatile private var somCoreTemp: Double = Double.NaN

    // Calibration & Parameter Tuning States
    @Volatile private var throttleValue1V: Double = Double.NaN
    @Volatile private var throttleValue2V: Double = Double.NaN
    @Volatile private var throttleOffset: Int = 0
    @Volatile private var throttleOffsetCount: Int = 0
    @Volatile private var throttleDeviationFault: Boolean = false
    @Volatile private var mcuCalibrationState: Int = 0
    @Volatile private var mcuCalibrationResult: Int = 0
    @Volatile private var offsetAngleMechanicalDegree: Double = Double.NaN
    @Volatile private var successfulCalibrationCounter: Int = 0
    @Volatile private var attemptedCalibrationCounter: Int = 0
    @Volatile private var imuPitchDeg: Double = Double.NaN
    @Volatile private var imuRollDeg: Double = Double.NaN
    @Volatile private var imuYawDeg: Double = Double.NaN
    @Volatile private var imuPitchOffsetDeg: Double = 0.0
    @Volatile private var imuRollOffsetDeg: Double = 0.0
    @Volatile private var frontBrakeVoltageV: Double = Double.NaN
    @Volatile private var rearBrakeVoltageV: Double = Double.NaN
    @Volatile private var isDynoModeActive: Boolean = false
    @Volatile private var isFloatKeepAwakeActive: Boolean = false
    @Volatile private var cellBalancingActive: Boolean = false

    // Active alerts map & trigger callback
    private val activeAlertsMap = ConcurrentHashMap<String, VehicleAlert>()
    var onAlertTriggered: ((VehicleAlert) -> Unit)? = null

    fun getActiveAlerts(): List<VehicleAlert> = activeAlertsMap.values.toList()

    private fun updateAlert(
        id: String,
        title: String,
        description: String,
        severity: AlertSeverity,
        component: String,
        sourceCanId: String,
        isActive: Boolean
    ) {
        if (isActive) {
            val existing = activeAlertsMap[id]
            if (existing == null) {
                val alert = VehicleAlert(
                    id = id,
                    title = title,
                    description = description,
                    severity = severity,
                    component = component,
                    sourceCanIdHex = sourceCanId,
                    isActive = true
                )
                activeAlertsMap[id] = alert
                onAlertTriggered?.invoke(alert)
            }
        } else {
            activeAlertsMap.remove(id)
        }
    }

    private var speedSampleNanos: Long? = null
    private var rawSpeedKmh = Double.NaN

    @Synchronized
    fun invalidateSpeed() {
        speedSampleNanos = null
        rawSpeedKmh = Double.NaN
        speedKmh = Double.NaN
    }

    @Synchronized
    fun isStationary(nowNanos: Long = System.nanoTime()): Boolean {
        val sampledAt = speedSampleNanos ?: return false
        return nowNanos - sampledAt in 0L..1_000_000_000L && rawSpeedKmh in 0.0..0.5
    }

    /** Decodes a single CAN frame and updates the live vehicle state. */
    @Synchronized
    fun processFrame(frame: CanFrame) {
        try {
            val d = frame.data
            when (frame.id) {
            ID_MCU_DATA_2 -> {
                // Regen Status: byte 0 bits 2..4 (0=Off, 1=Low, 2=Med, 3=High, 4=Max)
                regenLevel = (u8(d, 0) shr 2) and 0x07

                // Throttle analog voltage: byte 1..2 in Volts * 100
                val rawV = le16(d, 1) * 0.01
                throttleVoltage = rawV
                throttleValue1V = rawV
                if (d.size >= 5) {
                    val rawV2 = le16(d, 3) * 0.01
                    throttleValue2V = if (rawV2 in 0.2..5.0) rawV2 else rawV
                } else {
                    throttleValue2V = rawV
                }

                throttlePercent = computeThrottlePercent(rawV)

                // Cruise control active: byte 5 bit 0
                cruiseActive = (u8(d, 5) and 0x01) == 1

                // Discharge Mode (Drive Mode): byte 5 bits 1..4 (0=Parked, 2=Normal, 3=Sport, 4=Custom, 5/10/13=Reverse, 6=Eco, 7=Hyper)
                val modeVal = (u8(d, 5) shr 1) and 0x0F
                val modeStr = when (modeVal) {
                    0 -> "Parked"
                    2 -> "Normal"
                    3 -> "Sport"
                    4 -> "Custom"
                    5, 10, 13 -> "Reverse"
                    6 -> "Eco"
                    7 -> "Hyper"
                    else -> ""
                }
                if (modeStr.isNotEmpty()) {
                    driveMode = modeStr
                }

                // Hill hold response: byte 5 bits 5..6
                val hh = (u8(d, 5) shr 5) and 0x03
                hillHold = (hh > 0)
            }

            ID_SOC -> {
                val rawSoc = le16(d, 2) * 0.01
                if (rawSoc in 0.0..100.0) {
                    soc = rawSoc
                }
                if (packCurrent.isNaN()) {
                    packCurrent = le16(d, 0) * 0.01 - 300.0
                }
                checkMoveOs1PercentClamp()
            }

            ID_CURRENT -> {
                packCurrent = le16(d, 4) * 0.01 - 300.0
                updateChargingSessionIntegration()
            }

            ID_VOLTAGE -> {
                busVoltage = le16(d, 0) * 0.001
                val rawSoh = u8(d, 5).toDouble()
                if (rawSoh in 1.0..100.0) {
                    soh = rawSoh
                }
                checkMoveOs1PercentClamp()
                updateChargingSessionIntegration()
            }

            ID_TEMP -> {
                if (d.isNotEmpty()) {
                    cellBalancingActive = (u8(d, 0) and 0x01) == 1
                }
                effectiveTemp = decodeTemp(le16(d, 1))
                minTemp = decodeTemp(le16(d, 3))
                maxTemp = decodeTemp(le16(d, 5))
            }

            ID_CELL_LIMITS -> {
                val minMv = le16(d, 2).toDouble()
                val maxMv = le16(d, 4).toDouble()
                if (minMv in 2000.0..4500.0) cellMinMv = minMv
                if (maxMv in 2000.0..4500.0) cellMaxMv = maxMv
            }

            ID_PACK_TEMPS_1_4 -> {
                for (i in 0 until 4) {
                    packProbes[i] = decodeTemp(le16(d, i * 2))
                }
            }

            ID_PACK_TEMPS_5_6 -> {
                packProbes[4] = decodeTemp(le16(d, 0))
                packProbes[5] = decodeTemp(le16(d, 2))
                updateThermalMonitoring()
            }

            ID_AUX -> {
                pduTemp = decodeTemp(le16(d, 0))
                afeTemp = decodeTemp(le16(d, 2))
                updateThermalMonitoring()
            }

            ID_CELLS_1_4 -> {
                for (i in 0 until 4) {
                    val v = le16(d, i * 2) * 0.001
                    cellVoltages[i] = if (v in 1.5..4.5) v else Double.NaN
                }
            }

            ID_CELLS_5_8 -> {
                for (i in 0 until 4) {
                    val v = le16(d, i * 2) * 0.001
                    cellVoltages[4 + i] = if (v in 1.5..4.5) v else Double.NaN
                }
            }

            ID_CELLS_9_12 -> {
                for (i in 0 until 4) {
                    val v = le16(d, i * 2) * 0.001
                    cellVoltages[8 + i] = if (v in 1.5..4.5) v else Double.NaN
                }
            }

            ID_CELLS_13_16 -> {
                // Channel 13 & 14 (14S NMC pack)
                val v13 = le16(d, 0) * 0.001
                val v14 = le16(d, 2) * 0.001
                cellVoltages[12] = if (v13 in 1.5..4.5) v13 else Double.NaN
                cellVoltages[13] = if (v14 in 1.5..4.5) v14 else Double.NaN
                updateCellImbalanceAndResistance()
            }

            ID_MOTOR -> {
                // MCU_Data_1:
                // Bytes 0..1: speed_kmph with scale 0.1
                // Bytes 2..3: mcu_temp_C with scale 0.01
                // Bytes 4..5: motor_temp_C with scale 0.01
                // Bytes 6..7: motor_rpm with scale 1.0
                val rawSpd = if (frame.dlc >= 2 && d.size >= 2) le16(d, 0) * 0.1 else Double.NaN
                if (rawSpd in 0.0..200.0) {
                    rawSpeedKmh = rawSpd
                    speedSampleNanos = System.nanoTime()
                    // Apply exponential smoothing to eliminate high-frequency CAN sensor jitter (e.g. 49-51 fluctuation)
                    speedKmh = if (speedKmh.isNaN() || speedKmh == 0.0 || rawSpd == 0.0) {
                        rawSpd
                    } else {
                        (speedKmh * 0.75) + (rawSpd * 0.25)
                    }
                } else {
                    invalidateSpeed()
                }

                val rawMcuT = le16(d, 2) * 0.01
                if (rawMcuT in 1.0..160.0) {
                    mcuTemp = rawMcuT
                } else if (rawMcuT <= 0.0) {
                    // 0x0000 indicates sensor in sleep/standby mode while charging/parked
                    mcuTemp = Double.NaN
                }

                val rawMotorT = le16(d, 4) * 0.01
                if (rawMotorT in 1.0..180.0) {
                    motorTemp = rawMotorT
                    updateThermalMonitoring()
                } else if (rawMotorT <= 0.0) {
                    // 0x0000 indicates sensor in sleep/standby mode while charging/parked
                    motorTemp = Double.NaN
                }

                val rawRpm = le16(d, 6).toDouble()
                if (rawRpm in 0.0..15000.0) motorRpm = rawRpm
            }

            ID_DISPLAY -> {
                val dSoc = u8(d, 0).toDouble()
                if (dSoc in 0.0..100.0) {
                    displaySoc = dSoc
                    checkMoveOs1PercentClamp()
                }
            }

            ID_VEHICLE_LOCK_BCM -> {
                if (d.isNotEmpty()) {
                    val state = u8(d, 0) and 0x07
                    // 1 = Unlocked, 0/2 = Locked
                    isScooterLocked = (state != 1)
                }
            }

            ID_DISPLAY_INFO -> {
                // Byte 0 bits 0..1: Side stand status (1 = down / active)
                sideStandDown = (u8(d, 0) and 0x03) == 1
                // Byte 0 bit 2: Seat lock latch status (1 = open / unlatched)
                isBootOpen = ((u8(d, 0) shr 2) and 0x01) == 1

                // Indicators & Lighting Telltales on Dash (0x353)
                if (d.size >= 6) {
                    val b5 = u8(d, 5)
                    rightIndicatorActive = (b5 and 0x01) == 1
                    leftIndicatorActive = ((b5 shr 1) and 0x01) == 1
                    highBeamActive = ((b5 shr 2) and 0x01) == 1
                }
                if (d.size >= 7) {
                    val b6 = u8(d, 6)
                    lowBeamActive = ((b6 shr 2) and 0x01) == 1
                }

                // Byte 5 bit 3: Service spanner indicator
                val serviceReq = ((u8(d, 5) shr 3) and 0x01) == 1
                updateAlert(
                    id = "serviceIndicator",
                    title = "Service Required",
                    description = "MoveOS dashboard spanner service indicator is illuminated",
                    severity = AlertSeverity.WARNING,
                    component = "DASH",
                    sourceCanId = "0x353",
                    isActive = serviceReq
                )

                // Byte 5 bits 4..5: Vehicle error indicator (1=solid, 2=flashing)
                val vehErr = ((u8(d, 5) shr 4) and 0x03) > 0
                updateAlert(
                    id = "vehErrorIndicator",
                    title = "Vehicle Error Warning",
                    description = "MoveOS dashboard master warning triangle is illuminated",
                    severity = AlertSeverity.CRITICAL,
                    component = "DASH",
                    sourceCanId = "0x353",
                    isActive = vehErr
                )

                // Byte 6 bit 1: Brake pad wear indicator
                val brakePad = ((u8(d, 6) shr 1) and 0x01) == 1
                updateAlert(
                    id = "brakePadWorn",
                    title = "Brake Pad Wear Alert",
                    description = "Brake pad wear limit reached; replacement advised",
                    severity = AlertSeverity.WARNING,
                    component = "SENSORS",
                    sourceCanId = "0x353",
                    isActive = brakePad
                )

                // Byte 6 bit 3: Low battery alert
                val lowBat = ((u8(d, 6) shr 3) and 0x01) == 1
                updateAlert(
                    id = "lowBatteryAlert",
                    title = "Low Battery Alert",
                    description = "Telltale alert: battery state of charge is critically low (<10%)",
                    severity = AlertSeverity.WARNING,
                    component = "BMS",
                    sourceCanId = "0x353",
                    isActive = lowBat
                )
            }

            ID_ODOMETER -> {
                val rawM = le32(d, 0)
                if (rawM != 0xFFFFFFFFL && rawM >= 0L) {
                    odometerKm = rawM / 1000.0
                }
            }

            ID_SAFETY -> {
                fallDetected = (u8(d, 0) and 0x0F) == 1
                towAlert = ((u8(d, 0) shr 4) and 0x0F) == 1
                val hhRaw = (u8(d, 1) shr 4) and 0x0F
                if (hhRaw in listOf(2, 6)) {
                    hillHold = true
                } else if (hhRaw == 0) {
                    hillHold = false
                }

                updateAlert(
                    id = "fallAlert",
                    title = "Fall / Crash Detection Alert",
                    description = "Vehicle IMU sensor triggered tip-over or crash safety latch",
                    severity = AlertSeverity.CRITICAL,
                    component = "SAFETY",
                    sourceCanId = "0x683",
                    isActive = fallDetected
                )

                updateAlert(
                    id = "towAlert",
                    title = "Unauthorized Tow / Motion Alert",
                    description = "Anti-theft motion sensor detected tampering or unauthorized movement",
                    severity = AlertSeverity.WARNING,
                    component = "SAFETY",
                    sourceCanId = "0x683",
                    isActive = towAlert
                )
            }

            ID_VCU_DEBUG_1 -> {
                val b4 = u8(d, 4)
                updateAlert(
                    id = "vcuDriveStop",
                    title = "VCU Drive Stop Fault",
                    description = "Vehicle Control Unit commanded emergency drive stop",
                    severity = AlertSeverity.CRITICAL,
                    component = "VCU",
                    sourceCanId = "0x670",
                    isActive = (b4 and 0x01) != 0
                )
                updateAlert(
                    id = "vcuLimpHome",
                    title = "VCU Limp Home Mode",
                    description = "Vehicle operating under reduced-power limp home safety limit",
                    severity = AlertSeverity.WARNING,
                    component = "VCU",
                    sourceCanId = "0x670",
                    isActive = (b4 and 0x02) != 0
                )
                updateAlert(
                    id = "pduError",
                    title = "PDU Power Unit Error",
                    description = "Power Distribution Unit reported internal hardware error",
                    severity = AlertSeverity.WARNING,
                    component = "VCU",
                    sourceCanId = "0x670",
                    isActive = (b4 and 0x04) != 0
                )
                updateAlert(
                    id = "afeFault",
                    title = "BMS AFE Monitor Fault",
                    description = "Battery Analog Front End chip reported sensing fault",
                    severity = AlertSeverity.WARNING,
                    component = "BMS",
                    sourceCanId = "0x670",
                    isActive = (b4 and 0x10) != 0
                )
                updateAlert(
                    id = "bmsUndervoltErr",
                    title = "BMS Undervoltage Warning",
                    description = "Battery cell voltage dropped below operational threshold",
                    severity = AlertSeverity.WARNING,
                    component = "BMS",
                    sourceCanId = "0x670",
                    isActive = (b4 and 0x20) != 0
                )
                updateAlert(
                    id = "bmsOvertempDrive",
                    title = "BMS Overtemperature Error",
                    description = "Battery operating temperature exceeded maximum safety drive limits",
                    severity = AlertSeverity.CRITICAL,
                    component = "BMS",
                    sourceCanId = "0x670",
                    isActive = (b4 and 0x80) != 0
                )
            }

            ID_MCU_FAULTS -> {
                val b0 = u8(d, 0)
                val b3 = u8(d, 3)
                val b6 = u8(d, 6)

                updateAlert(
                    id = "mcuOvercurrent",
                    title = "MCU Overcurrent Protection",
                    description = "Inverter software overcurrent protection Grade 3 triggered",
                    severity = AlertSeverity.CRITICAL,
                    component = "MCU",
                    sourceCanId = "0x333",
                    isActive = (b0 and 0x01) != 0
                )
                updateAlert(
                    id = "mcuOvervoltage",
                    title = "MCU Overvoltage Fault",
                    description = "Inverter DC bus software overvoltage fault Grade 3 triggered",
                    severity = AlertSeverity.CRITICAL,
                    component = "MCU",
                    sourceCanId = "0x333",
                    isActive = (b0 and 0x02) != 0
                )
                updateAlert(
                    id = "mcuOvertemp",
                    title = "MCU Inverter Overtemperature",
                    description = "Motor controller heatsink temperature exceeded thermal threshold",
                    severity = AlertSeverity.WARNING,
                    component = "MCU",
                    sourceCanId = "0x333",
                    isActive = (b0 and 0x40) != 0
                )
                updateAlert(
                    id = "motorOvertemp",
                    title = "Motor Stator Overtemperature",
                    description = "Motor stator winding temperature exceeded safe thermal threshold",
                    severity = AlertSeverity.WARNING,
                    component = "MCU",
                    sourceCanId = "0x333",
                    isActive = (b0 and 0x80) != 0
                )
                updateAlert(
                    id = "throttleWiper1Fail",
                    title = "Throttle Sensor Wiper 1 Fail",
                    description = "Twist grip potentiometer channel 1 signal lost or shorted",
                    severity = AlertSeverity.CRITICAL,
                    component = "MCU",
                    sourceCanId = "0x333",
                    isActive = (b3 and 0x08) != 0
                )
                updateAlert(
                    id = "throttleWiper2Fail",
                    title = "Throttle Sensor Wiper 2 Fail",
                    description = "Twist grip potentiometer channel 2 signal lost or shorted",
                    severity = AlertSeverity.CRITICAL,
                    component = "MCU",
                    sourceCanId = "0x333",
                    isActive = (b3 and 0x10) != 0
                )
                updateAlert(
                    id = "mcuDerating",
                    title = "MCU Thermal Derating Active",
                    description = "Inverter is restricting maximum drive current due to temperature",
                    severity = AlertSeverity.INFO,
                    component = "MCU",
                    sourceCanId = "0x333",
                    isActive = (b6 and 0x01) != 0
                )
                if (d.size >= 6) {
                    successfulCalibrationCounter = u8(d, 4)
                    attemptedCalibrationCounter = u8(d, 5)
                }
            }

            ID_THROTTLE_STATUS -> {
                val b0 = u8(d, 0)
                throttleDeviationFault = (b0 and 0x01) != 0
                throttleOffset = (b0 shr 2) and 0x01
                if (d.size >= 2) {
                    throttleOffsetCount = u8(d, 1)
                }
                updateAlert(
                    id = "throttleDeviationError",
                    title = "Throttle Deviation Error",
                    description = "Discrepancy detected between dual twist grip Hall sensor readings",
                    severity = AlertSeverity.WARNING,
                    component = "MCU",
                    sourceCanId = "0x210",
                    isActive = throttleDeviationFault
                )
            }

            ID_NODE_ABSENT -> {
                val b0 = u8(d, 0)
                updateAlert(
                    id = "bmsNodeAbsent",
                    title = "BMS Node Absent",
                    description = "CAN communication failure: Battery Management System not responding",
                    severity = AlertSeverity.CRITICAL,
                    component = "CAN",
                    sourceCanId = "0x064",
                    isActive = (b0 and 0x01) != 0
                )
                updateAlert(
                    id = "mcuNodeAbsent",
                    title = "MCU Node Absent",
                    description = "CAN communication failure: Motor Control Unit not responding",
                    severity = AlertSeverity.CRITICAL,
                    component = "CAN",
                    sourceCanId = "0x064",
                    isActive = (b0 and 0x02) != 0
                )
                updateAlert(
                    id = "esclNodeAbsent",
                    title = "ESCL Lock Node Absent",
                    description = "CAN communication failure: Electronic Steering Column Lock not responding",
                    severity = AlertSeverity.WARNING,
                    component = "CAN",
                    sourceCanId = "0x064",
                    isActive = (b0 and 0x04) != 0
                )
            }

            ID_BATTERY_FAULT -> {
                val b0 = u8(d, 0)
                val b2 = u8(d, 2)
                val b3 = u8(d, 3)
                val b4 = u8(d, 4)
                val b5 = u8(d, 5)
                val b6 = u8(d, 6)

                updateAlert(
                    id = "bmsPrechargeFail",
                    title = "BMS Precharge Circuit Failure",
                    description = "High-voltage precharge contactor failed to energize pack safely",
                    severity = AlertSeverity.CRITICAL,
                    component = "BMS",
                    sourceCanId = "0x122",
                    isActive = (b0 and 0x01) != 0
                )

                // Byte 2 bit 0: Weak cell voltage sag under acceleration (<2.70V) -> MoveOS 1% Limp
                updateAlert(
                    id = "bmsCellDipFault",
                    title = "BMS Cell Dip / Sag Limp Derate",
                    description = "Weak cell voltage collapsed below safety threshold under load. MoveOS Limp Mode triggered.",
                    severity = AlertSeverity.CRITICAL,
                    component = "BMS",
                    sourceCanId = "0x122",
                    isActive = (b2 and 0x01) != 0
                )

                // Byte 3 bit 0: Load sag warning / high cell spread (>400mV)
                updateAlert(
                    id = "bmsLoadSagWarning",
                    title = "High Cell Imbalance Under Load (>400mV)",
                    description = "BMS detected extreme cell voltage spread during discharge. Power derate applied.",
                    severity = AlertSeverity.WARNING,
                    component = "BMS",
                    sourceCanId = "0x122",
                    isActive = (b3 and 0x01) != 0
                )

                // Byte 4 bit 0: Continuous high discharge rate derate
                updateAlert(
                    id = "bmsHighDischargeDerate",
                    title = "BMS High Discharge Derating",
                    description = "Continuous aggressive acceleration or low SOC current limit enforced by BMS.",
                    severity = AlertSeverity.WARNING,
                    component = "BMS",
                    sourceCanId = "0x122",
                    isActive = (b4 and 0x01) != 0
                )

                // Byte 5 bit 0: Inhibit Regen Fault Flag (0x122 startbit 40: Battery_Inhibit_Regen_Fault_00)
                updateAlert(
                    id = "bmsRegenInhibitFlag",
                    title = "BMS Regen Braking Inhibit",
                    description = "Regenerative braking temporarily restricted by BMS (typical at high SOC >95%, cold pack, or continuous braking). Pack temp nominal.",
                    severity = AlertSeverity.INFO,
                    component = "BMS",
                    sourceCanId = "0x122",
                    isActive = (b5 and 0x01) != 0
                )

                updateAlert(
                    id = "cellPermanentFault",
                    title = "BMS Cell Permanent Fault",
                    description = "Permanent cell degradation or hardware fault detected in battery pack",
                    severity = AlertSeverity.CRITICAL,
                    component = "BMS",
                    sourceCanId = "0x122",
                    isActive = (b6 and 0x01) != 0
                )
            }

            ID_SAFETY_ALERT -> {
                val b0 = u8(d, 0)
                val b1 = u8(d, 1)
                updateAlert(
                    id = "bmsTempVeryHigh",
                    title = "Battery Pack Temperature Critical",
                    description = "Battery cell operating temperature is dangerously high",
                    severity = AlertSeverity.CRITICAL,
                    component = "BMS",
                    sourceCanId = "0x6B0",
                    isActive = (b0 and 0x02) != 0
                )
                updateAlert(
                    id = "motorTempVeryHigh",
                    title = "Motor Stator Temperature Critical",
                    description = "Motor stator temperature reached critical thermal limit",
                    severity = AlertSeverity.CRITICAL,
                    component = "MCU",
                    sourceCanId = "0x6B0",
                    isActive = (b0 and 0x20) != 0
                )
                updateAlert(
                    id = "tamperAlert",
                    title = "Anti-Theft Tamper Alert",
                    description = "Security alert: unauthorized scooter movement detected while locked",
                    severity = AlertSeverity.WARNING,
                    component = "SAFETY",
                    sourceCanId = "0x6B0",
                    isActive = (b1 and 0x08) != 0
                )
            }

            ID_BMS_LIMITS -> {
                if (d.size >= 4) {
                    val rawCcl = le16(d, 0)
                    val rawDcl = le16(d, 2)
                    bmsMaxChargeCurrentA = rawCcl * 0.01
                    bmsMaxDischargeCurrentA = rawDcl * 0.01
                    if (d.size >= 8) {
                        bmsNominalCapacityAh = le32(d, 4) / 1000.0
                    }
                    val isRegenBlocked = bmsMaxChargeCurrentA <= 0.1 && (!effectiveTemp.isNaN() && effectiveTemp >= 52.0 || !soc.isNaN() && soc >= 98.0)
                    updateAlert(
                        id = "bmsRegenBlocked",
                        title = "BMS Regen Cutoff Active",
                        description = "BMS reduced regen charge limit to 0A due to high pack temperature (≥52°C) or full saturation",
                        severity = AlertSeverity.WARNING,
                        component = "BMS",
                        sourceCanId = "0x130",
                        isActive = isRegenBlocked
                    )
                }
            }

            ID_MODE_RANGE -> {
                if (d.size >= 8) {
                    val e = le16(d, 0)
                    val n = le16(d, 2)
                    val s = le16(d, 4)
                    val h = le16(d, 6)
                    if (e in 0..300) ecoRangeKm = e
                    if (n in 0..300) normalRangeKm = n
                    if (s in 0..300) sportRangeKm = s
                    if (h in 0..300) hyperRangeKm = h
                }
            }

            ID_MOTOR_TORQUE -> {
                if (d.size >= 2) {
                    val rawT = le16(d, 0)
                    motorTorqueNm = if (rawT >= 1000) (rawT - 1000) * 0.1 else 0.0
                    if (d.size >= 7) {
                        motorPhaseCurrentA = (u8(d, 6)).toDouble()
                    }
                }
            }

            ID_BATTERY_SERIAL -> {
                if (d.size >= 8) {
                    val seq = u8(d, 0)
                    val strBytes = d.sliceArray(1 until 8)
                    val ascii = String(strBytes, java.nio.charset.StandardCharsets.US_ASCII).filter { it.isLetterOrDigit() }
                    if (seq == 0) {
                        batterySerialSeq0 = ascii
                    } else if (seq == 1) {
                        batterySerialSeq1 = ascii
                    }
                    if (batterySerialSeq0.isNotEmpty() && batterySerialSeq1.isNotEmpty()) {
                        batterySerial = batterySerialSeq0 + batterySerialSeq1
                    }
                }
            }

            ID_VCU_SERIAL -> {
                if (d.size >= 4) {
                    val seq = u8(d, 0)
                    val strBytes = d.sliceArray(1 until d.size)
                    val ascii = String(strBytes, java.nio.charset.StandardCharsets.US_ASCII).filter { it.isLetterOrDigit() }
                    if (seq in 1..3) {
                        vcuSerialParts[seq] = ascii
                        if (vcuSerialParts[1].isNotEmpty() && vcuSerialParts[2].isNotEmpty()) {
                            vcuHardwareSerial = vcuSerialParts[1] + vcuSerialParts[2] + vcuSerialParts[3]
                        }
                    }
                }
            }

            ID_BATTERY_CYCLES -> {
                if (d.size >= 4) {
                    // True physical pack cycles stored directly inside the BMS EEPROM:
                    val cycles = le32(d, 0)
                    if (cycles in 1..5000) {
                        packHardwareCycles = cycles
                    }
                    val dc = le16(d, 0)
                    if (dc in 0..5000) {
                        dcFastChargeSessions = dc
                    }
                }
            }

            ID_LIFETIME_CYCLES_VCU -> {
                if (d.size >= 6) {
                    val c = le16(d, 2)
                    if (c in 1..10000) {
                        vehicleChassisCycles = c
                    }
                    val ac = le16(d, 4)
                    if (ac in 0..10000) {
                        acChargeSessions = ac
                    }
                }
            }

            ID_LIFETIME_AH -> {
                if (d.size >= 4) {
                    val ah = le32(d, 0)
                    if (ah > 0L) {
                        lifetimeAhThroughput = ah
                    }
                }
            }

            ID_BRICK_SOH -> {
                if (d.size >= 6) {
                    brickSohList = (1..5).map { idx -> u8(d, idx) }
                }
            }

            ID_VCU_SOM_TEMP -> {
                if (d.size >= 4) {
                    val rawVcu = le16(d, 0)
                    val rawSom = le16(d, 2)
                    vcuBoardTemp = decodeTemp(rawVcu)
                    somCoreTemp = decodeTemp(rawSom)
                }
            }

            ID_WHEEL_SPEED -> {
                if (d.isNotEmpty()) {
                    val b0 = u8(d, 0)
                    rightBrakePressed = ((b0 shr 4) and 0x01) == 1
                    leftBrakePressed = ((b0 shr 5) and 0x01) == 1
                }
                if (d.size >= 4) {
                    val wSpd = le16(d, 0) * 0.1
                    if (wSpd in 0.0..160.0) {
                        wheelSpeedKmh = wSpd
                        if (!speedKmh.isNaN() && speedKmh > 5.0) {
                            val diff = Math.abs(speedKmh - wSpd)
                            wheelSlipPercent = (diff / speedKmh * 100.0).coerceIn(0.0, 50.0)
                        } else {
                            wheelSlipPercent = 0.0
                        }
                    }
                    val amb = le16(d, 2) * 0.1
                    if (amb in -20.0..65.0) {
                        ambientTemp = amb
                    }
                }
                if (d.size >= 8) {
                    val rawX = le16(d, 2)
                    val rawY = le16(d, 4)
                    val rawZ = le16(d, 6)
                    val p = (rawX - 360).toDouble() - imuPitchOffsetDeg
                    val r = (rawY - 360).toDouble() - imuRollOffsetDeg
                    val y = (rawZ - 360).toDouble()
                    if (p in -90.0..90.0) imuPitchDeg = p
                    if (r in -90.0..90.0) imuRollDeg = r
                    if (y in -180.0..180.0) imuYawDeg = y
                }
            }

            ID_HANDLEBAR_BUTTONS -> {
                // 0x6AC Left Handlebar Switch Cluster
                if (d.isNotEmpty()) {
                    val b0 = u8(d, 0)
                    highBeamActive = (b0 and 0x01) == 1
                    leftIndicatorActive = ((b0 shr 2) and 0x01) == 1
                    rightIndicatorActive = ((b0 shr 3) and 0x01) == 1
                    hornPressed = ((b0 shr 4) and 0x01) == 1
                }
            }

            ID_DIGITAL_OUTPUT -> {
                // 0x6BF Digital Output / Driver Commands & Contactors
                if (d.size >= 4) {
                    val b3 = u8(d, 3)
                    brakeLightActive = ((b3 shr 1) and 0x01) == 1

                    val fcPos = ((b3 shr 5) and 0x01) == 1
                    val fcNeg = ((b3 shr 6) and 0x01) == 1
                    if (fcPos || fcNeg) mainContactorClosed = true
                }
            }

            ID_CHARGER_STATUS -> {
                // 0x213 Charger Status (bit 0 = actively charging)
                if (d.isNotEmpty()) {
                    isChargingActive = (u8(d, 0) and 0x01) == 1
                    val m = (u8(d, 0) shr 1) and 0x07
                    chargerModeName = when (m) {
                        1 -> "Home AC Portable (750W)"
                        2 -> "Normal Charging Session"
                        3 -> "Public AC (3.3 kW)"
                        else -> if (isChargingActive) "Normal Charging Session" else "Home AC Portable (750W)"
                    }
                }
                if (d.size >= 4) {
                    val rawV = (((u8(d, 0) shr 4) or (u8(d, 1) shl 4) or ((u8(d, 2) and 0x0F) shl 12))) * 0.1
                    if (rawV in 20.0..80.0) chargerAvailableVoltage = rawV
                    val rawA = (((u8(d, 2) shr 4) or (u8(d, 3) shl 4))) * 0.1
                    if (rawA in 1.0..40.0) chargerAvailableCurrent = rawA
                }
                updateChargingSessionIntegration()
            }

            ID_CHARGER_CONNECTION -> {
                // 0x62D Charger Cable Connection Status (bits 0..1 connected, bit 2 plugged in)
                if (d.isNotEmpty()) {
                    val b0 = u8(d, 0)
                    isChargerPluggedIn = ((b0 shr 2) and 0x01) == 1 || (b0 and 0x03) > 0
                }
                updateChargingSessionIntegration()
            }

            ID_CHARGER_PARAMETER_5 -> {
                // 0x180 BMS Parameter 5
                if (d.size >= 5) {
                    val maxT = le16(d, 0)
                    if (maxT in 0..1440) chargingTimeMaxMin = maxT
                    val r = u8(d, 4).toDouble()
                    if (r in 0.0..100.0) chargingRatePercent = r
                }
            }

            ID_CHARGER_TIME -> {
                // 0x190 BMS Parameter 6 (Estimated time to 80% and 100% full charge)
                if (d.size >= 8) {
                    val sc80 = le16(d, 0)
                    val sc100 = le16(d, 2)
                    val fc80 = le16(d, 4)
                    val fc100 = le16(d, 6)
                    if (sc80 in 0..1440) timeToChargeSc80Min = sc80
                    if (sc100 in 0..1440) timeToFullChargeSc100Min = sc100
                    if (fc80 in 0..1440) timeToChargeFc80Min = fc80
                    if (fc100 in 0..1440) timeToChargeFc100Min = fc100
                }
            }

            ID_CHARGED_ENERGY_MODE -> {
                // 0x6DE BMS Cloud Data 4 (Session/Mode Amp-Hours & kWh charged)
                if (d.size >= 4) {
                    val amphr = le16(d, 0) * 0.01
                    val kwhr = le16(d, 2) * 0.01
                    if (amphr > 0.0) lifetimeModeAmphrCharged = amphr
                    if (kwhr > 0.0) lifetimeModeKwhrCharged = kwhr
                }
            }

            ID_MAX_CELL_DEV_CHARGE -> {
                // 0x6E2 BMS Cloud Data 6 (Max cell imbalance deviation during charge)
                if (d.size >= 4) {
                    val dev = le16(d, 2)
                    if (dev in 0..1000) maxCellDeviationDuringChargeMv = dev.toDouble()
                }
            }

            ID_BALANCING_STATS -> {
                // 0x6E4 BMS Cloud Data 7 (Balancing activation count)
                if (d.size >= 2) {
                    balancingCyclesCount = le16(d, 0)
                }
            }

            ID_TOTAL_ENERGY -> {
                // 0x6E5 BMS Energy Data
                if (d.size >= 2) {
                    val wh = le16(d, 0).toDouble()
                    if (wh in 100.0..6000.0) totalAvailableEnergyWhr = wh
                }
            }

            ID_ANALOG_INPUT_3 -> {
                // 0x6C5 Current Sense - do not override digital handlebar switch states
            }

            ID_ANALOG_INPUT_6 -> {
                // 0x6C3 Current Sense Brake Lamp & Indicators
                if (d.size >= 4) {
                    val brakeLampCur = le16(d, 2) * 0.1
                    if (brakeLampCur > 0.4) brakeLightActive = true
                }
            }

            ID_BMS_CONTACTOR -> {
                // 0x11C BMS Contactor & Pack Status
                if (d.isNotEmpty()) {
                    val cState = u8(d, 0) and 0x0F
                    mainContactorClosed = cState in listOf(2, 6) || cState > 0
                }
            }

            ID_MCU_BRAKE -> {
                // 0x320 MCU Analog Brake Voltages
                if (d.size >= 4) {
                    rearBrakeVoltageV = le16(d, 0) * 0.01
                    frontBrakeVoltageV = le16(d, 2) * 0.01
                }
            }

            ID_AUTO_TUNING_STATUS -> {
                // 0x335 MCU Parameter Identification / Auto-Tuning
                if (d.isNotEmpty()) {
                    val b0 = u8(d, 0)
                    mcuCalibrationState = b0 and 0x07
                    mcuCalibrationResult = (b0 shr 3) and 0x07
                }
                if (d.size >= 3) {
                    val rawA = le16(d, 1)
                    if (rawA in 0..3600) {
                        offsetAngleMechanicalDegree = rawA * 0.1
                    } else if (rawA in 0..360) {
                        offsetAngleMechanicalDegree = rawA.toDouble()
                    }
                }
            }

            ID_IMU_DYNAMICS -> {
                // 0x682 6-Axis IMU Resultant Inclination & Dynamics
                if (d.size >= 4) {
                    val rawPitchS = le16s(d, 0).toDouble()
                    val rawRollS = le16s(d, 2).toDouble()
                    val pRaw = if (rawPitchS in -900.0..900.0) rawPitchS * 0.1 else if (rawPitchS in -90.0..90.0) rawPitchS else (le16(d, 0) - 360).toDouble()
                    val rRaw = if (rawRollS in -900.0..900.0) rawRollS * 0.1 else if (rawRollS in -90.0..90.0) rawRollS else (le16(d, 2) - 360).toDouble()
                    val p = pRaw - imuPitchOffsetDeg
                    val r = rRaw - imuRollOffsetDeg
                    if (p in -90.0..90.0) imuPitchDeg = p
                    if (r in -90.0..90.0) imuRollDeg = r
                }
            }

            ID_DRIVE_STATE -> {
                if (d.size >= 2) {
                    val b0 = u8(d, 0)
                    val b1 = u8(d, 1)
                    operationalState = when {
                        b0 == 0x00 && b1 == 0x00 -> "Parked / Stand Down"
                        b0 == 0xC2 && b1 == 0x01 -> "Drive / Forward Cruise"
                        b0 == 0x07 && b1 == 0x05 -> "Low Speed / Coasting"
                        b0 == 0x42 && b1 == 0x04 -> "Hard Acceleration Pull"
                        else -> "Active"
                    }
                }
            }
        }
    } catch (_: Throwable) {
        // Defensive: corrupt or malformed CAN frames will never crash the app
    }
}

    private fun checkMoveOs1PercentClamp() {
        if (busVoltage.isNaN()) return
        val currentSoc = if (!soc.isNaN()) soc else displaySoc
        if (currentSoc.isNaN()) return

        if (currentSoc > 5.0) {
            lastPlausibleSoc = currentSoc
        }

        // Ola MoveOS clamps SOC to 1.00% under high cell sag when pack is still high (>43V)
        if (currentSoc in 0.8..1.2 && busVoltage > 43.0) {
            is1PctClampActive = true
            val estSoc = if (!lastPlausibleSoc.isNaN() && lastPlausibleSoc > 10.0) {
                lastPlausibleSoc
            } else {
                (((busVoltage - 42.0) / (56.0 - 42.0)) * 100.0).coerceIn(5.0, 95.0)
            }
            clampedTrueSocEst = estSoc

            updateAlert(
                id = "moveOs1PctClamp",
                title = "MoveOS 1% Cell Protection Clamp Active",
                description = "BMS clamped displayed SOC to 1.0% due to cell sag derate. Pack is at ${String.format(java.util.Locale.US, "%.1f", busVoltage)}V (estimated true SOC ~${String.format(java.util.Locale.US, "%.0f", estSoc)}%). Normal reading will restore when throttle is released.",
                severity = AlertSeverity.CRITICAL,
                component = "BMS",
                sourceCanId = "0x120",
                isActive = true
            )
        } else if (currentSoc > 2.0 || busVoltage < 41.5) {
            is1PctClampActive = false
            updateAlert(
                id = "moveOs1PctClamp",
                title = "",
                description = "",
                severity = AlertSeverity.CRITICAL,
                component = "BMS",
                sourceCanId = "0x120",
                isActive = false
            )
        }
    }

    private fun updateCellImbalanceAndResistance() {
        val validCells = cellVoltages.filter { !it.isNaN() && it > 1.5 }
        if (validCells.size < 14) return

        // 1. Check critical cell voltage floor (< 2.75V)
        val minV = validCells.minOrNull() ?: Double.NaN
        if (!minV.isNaN() && minV < 2.75) {
            val minIdx = cellVoltages.indexOfFirst { !it.isNaN() && Math.abs(it - minV) < 0.0005 }
            criticalCellFloorBreached = true
            criticalCellFloorNumber = if (minIdx >= 0) minIdx + 1 else 1
            updateAlert(
                id = "criticalCellFloor",
                title = "Critical Cell Voltage Floor (<2.75V)",
                description = "Cell ${minIdx + 1} reached ${String.format(java.util.Locale.US, "%.3f", minV)}V (NMC critical limit 2.70V). Emergency cutoff risk.",
                severity = AlertSeverity.CRITICAL,
                component = "BMS",
                sourceCanId = "0x1A5",
                isActive = true
            )
        } else {
            criticalCellFloorBreached = false
            criticalCellFloorNumber = 0
            updateAlert(
                id = "criticalCellFloor",
                title = "",
                description = "",
                severity = AlertSeverity.CRITICAL,
                component = "BMS",
                sourceCanId = "0x1A5",
                isActive = false
            )
        }

        // 2. Resting baseline vs load sag
        val currentAbs = if (packCurrent.isNaN()) 0.0 else Math.abs(packCurrent)
        if (currentAbs < 5.0) {
            // Scooter at standstill / resting
            for (i in 0 until 14) {
                restingCellVoltages[i] = cellVoltages[i]
            }
            val maxRest = validCells.maxOrNull() ?: 0.0
            val minRest = validCells.minOrNull() ?: 0.0
            restingCellDeltaMv = (maxRest - minRest) * 1000.0
        } else if (packCurrent < -25.0) {
            // High discharge motor draw (packCurrent is negative on Ola)
            val loadCurrent = Math.abs(packCurrent)
            for (i in 0 until 14) {
                val restV = restingCellVoltages[i]
                val currentV = cellVoltages[i]
                if (!restV.isNaN() && !currentV.isNaN() && restV > currentV) {
                    val sagV = restV - currentV
                    val sagMv = sagV * 1000.0
                    if (sagMv > cellPeakSagMv[i]) {
                        cellPeakSagMv[i] = sagMv
                    }
                    val riMOhm = (sagV / loadCurrent) * 1000.0
                    if (riMOhm in 0.5..15.0) {
                        if (cellInternalResistances[i] == 0.0) {
                            cellInternalResistances[i] = riMOhm
                        } else {
                            // Exponential moving filter
                            cellInternalResistances[i] = cellInternalResistances[i] * 0.90 + riMOhm * 0.10
                        }
                    }
                }
            }
            if (loadCurrent > 60.0) {
                val maxLoad = validCells.maxOrNull() ?: 0.0
                val minLoad = validCells.minOrNull() ?: 0.0
                val currentDelta = (maxLoad - minLoad) * 1000.0
                if (peakLoadCellDeltaMv.isNaN() || currentDelta > peakLoadCellDeltaMv) {
                    peakLoadCellDeltaMv = currentDelta
                }
            }
        }
    }

    private fun updateThermalMonitoring() {
        val validProbes = packProbes.filter { !it.isNaN() }
        if (validProbes.size >= 2) {
            val minT = validProbes.minOrNull() ?: return
            val maxT = validProbes.maxOrNull() ?: return
            val grad = maxT - minT
            packThermalGrad = grad
            val hotIdx = packProbes.indexOfFirst { !it.isNaN() && Math.abs(it - maxT) < 0.0005 }
            val coolIdx = packProbes.indexOfFirst { !it.isNaN() && Math.abs(it - minT) < 0.0005 }
            hottestProbeIdx = if (hotIdx >= 0) hotIdx + 1 else 0
            coolestProbeIdx = if (coolIdx >= 0) coolIdx + 1 else 0

            updateAlert(
                id = "packThermalGradient",
                title = "High Pack Thermal Gradient",
                description = "Battery pack thermal spread is ${String.format(java.util.Locale.US, "%.1f", grad)}°C (Probe $hottestProbeIdx: ${String.format(java.util.Locale.US, "%.1f", maxT)}°C vs Probe $coolestProbeIdx: ${String.format(java.util.Locale.US, "%.1f", minT)}°C). High gradients accelerate uneven cell degradation.",
                severity = AlertSeverity.WARNING,
                component = "BMS",
                sourceCanId = "0x1A4",
                isActive = grad >= 10.0
            )
        }

        // PDU derate warning (>85°C)
        if (!pduTemp.isNaN()) {
            updateAlert(
                id = "pduHighTempDerate",
                title = "PDU High Temperature Throttling",
                description = "Power Distribution Unit temperature (${String.format(java.util.Locale.US, "%.1f", pduTemp)}°C) exceeds 85°C. MoveOS thermal derating imminent.",
                severity = AlertSeverity.WARNING,
                component = "VCU",
                sourceCanId = "0x1A8",
                isActive = pduTemp >= 85.0
            )
        }

        // Motor stator derate warning (>95°C)
        if (!motorTemp.isNaN()) {
            updateAlert(
                id = "motorHighTempDerate",
                title = "Motor Stator Thermal Throttling",
                description = "Motor stator temperature (${String.format(java.util.Locale.US, "%.1f", motorTemp)}°C) exceeds 95°C. MoveOS torque derating active.",
                severity = AlertSeverity.WARNING,
                component = "MCU",
                sourceCanId = "0x313",
                isActive = motorTemp >= 95.0
            )
        }

        // AFE sensor high temp (>80°C)
        if (!afeTemp.isNaN()) {
            updateAlert(
                id = "afeHighTempWarning",
                title = "BMS AFE Over-Temperature Warning",
                description = "Analog Front End sensing IC reached ${String.format(java.util.Locale.US, "%.1f", afeTemp)}°C.",
                severity = AlertSeverity.WARNING,
                component = "BMS",
                sourceCanId = "0x1A8",
                isActive = afeTemp >= 80.0
            )
        }
    }

    @Synchronized
    fun getBatteryState(): BatteryState {
        val validCells = cellVoltages.filter { !it.isNaN() && it > 1.5 }
        val deltaMv = if (validCells.size >= 2) {
            val maxV = validCells.maxOrNull() ?: 0.0
            val minV = validCells.minOrNull() ?: 0.0
            (maxV - minV) * 1000.0
        } else if (!cellMaxMv.isNaN() && !cellMinMv.isNaN()) {
            cellMaxMv - cellMinMv
        } else {
            Double.NaN
        }

        val powerKw = if (!busVoltage.isNaN() && !packCurrent.isNaN()) {
            (busVoltage * packCurrent) / 1000.0
        } else {
            Double.NaN
        }

        return BatteryState(
            socPercent = soc,
            displaySocPercent = displaySoc,
            packSohPercent = soh,
            busVoltageV = busVoltage,
            batteryCurrentA = packCurrent,
            powerKw = powerKw,
            cellMinMv = validCells.minOrNull()?.let { it * 1000.0 } ?: cellMinMv,
            cellMaxMv = validCells.maxOrNull()?.let { it * 1000.0 } ?: cellMaxMv,
            cellDeltaMv = deltaMv,
            cellVoltagesV = cellVoltages.toList(),
            restingCellDeltaMv = restingCellDeltaMv,
            peakLoadCellDeltaMv = peakLoadCellDeltaMv,
            cellInternalResistancesMOhms = cellInternalResistances.toList(),
            cellPeakSagMv = cellPeakSagMv.toList(),
            is1PercentSocClampActive = is1PctClampActive,
            clampedTrueSocEstPercent = clampedTrueSocEst,
            criticalCellFloorBreached = criticalCellFloorBreached,
            criticalCellFloorNumber = criticalCellFloorNumber,
            bmsMaxChargeCurrentA = bmsMaxChargeCurrentA,
            bmsMaxDischargeCurrentA = bmsMaxDischargeCurrentA,
            bmsNominalCapacityAh = bmsNominalCapacityAh,
            packHardwareCycles = packHardwareCycles,
            vehicleChassisCycles = vehicleChassisCycles,
            acChargeSessions = acChargeSessions,
            dcFastChargeSessions = dcFastChargeSessions,
            lifetimeAhThroughput = lifetimeAhThroughput,
            batterySerial = batterySerial,
            brickSohArray = brickSohList,
            isPluggedIn = isChargerPluggedIn,
            isChargerConnected = isChargingActive || isChargerPluggedIn,
            chargingSession = getChargingSession()
        )
    }

    private fun updateChargingSessionIntegration() {
        val isChargingNow = (isChargingActive && busVoltage > 40.0) || (isChargerPluggedIn && !packCurrent.isNaN() && packCurrent > 0.4 && busVoltage > 40.0)
        val now = System.currentTimeMillis()
        val currentSocVal = if (!soc.isNaN()) soc else displaySoc
        val currentTempVal = if (!minTemp.isNaN()) minTemp else effectiveTemp

        if (isChargingNow) {
            if (!sessionIsCharging) {
                sessionIsCharging = true
                sessionStartTimeMs = now
                sessionLastTickMs = now
                if (!currentSocVal.isNaN()) sessionStartSoc = currentSocVal
                if (!currentTempVal.isNaN()) sessionStartTemp = currentTempVal
                sessionAccumulatedKwh = 0.0
                sessionAccumulatedAh = 0.0
                sessionMaxDeltaMv = 0.0
            } else {
                val dtSec = (now - sessionLastTickMs) / 1000.0
                if (dtSec in 0.05..5.0) {
                    sessionLastTickMs = now
                    val liveCurrentA = if (!packCurrent.isNaN()) Math.abs(packCurrent) else 0.0
                    val liveVolts = if (!busVoltage.isNaN() && busVoltage > 30.0) busVoltage else 52.0
                    val liveKw = (liveCurrentA * liveVolts) / 1000.0
                    if (liveKw in 0.05..15.0) {
                        sessionAccumulatedKwh += (liveKw * dtSec) / 3600.0
                        sessionAccumulatedAh += (liveCurrentA * dtSec) / 3600.0
                    }
                }
                val validCells = cellVoltages.filter { !it.isNaN() && it > 1.5 }
                if (validCells.size >= 2) {
                    val maxV = validCells.maxOrNull() ?: 0.0
                    val minV = validCells.minOrNull() ?: 0.0
                    val curDelta = (maxV - minV) * 1000.0
                    if (curDelta > sessionMaxDeltaMv) sessionMaxDeltaMv = curDelta
                }
            }
        } else {
            if (sessionIsCharging) {
                sessionIsCharging = false
            }
        }
    }

    @Synchronized
    fun getChargingSession(): ChargingSessionTelemetry {
        val isPlugged = isChargerPluggedIn
        val isActivelyCharging = sessionIsCharging || isChargingActive || (isPlugged && !packCurrent.isNaN() && packCurrent > 0.4 && busVoltage > 40.0)
        val currentSoc = if (!soc.isNaN()) soc else displaySoc
        val liveCurrentA = if (!packCurrent.isNaN() && packCurrent > 0.25) packCurrent else 0.0
        val liveVolts = if (!busVoltage.isNaN() && busVoltage > 40.0) busVoltage else 0.0
        val liveKw = if (liveCurrentA > 0.25 && liveVolts > 40.0) (liveCurrentA * liveVolts) / 1000.0 else 0.0
        val gainedSoc = if (!sessionStartSoc.isNaN() && !currentSoc.isNaN()) {
            (currentSoc - sessionStartSoc).coerceAtLeast(0.0)
        } else 0.0

        val durationSec = if (sessionStartTimeMs > 0L) {
            (System.currentTimeMillis() - sessionStartTimeMs) / 1000L
        } else 0L

        val tFull = if (timeToFullChargeSc100Min > 0) {
            timeToFullChargeSc100Min
        } else if (liveKw > 0.2 && !currentSoc.isNaN() && currentSoc < 99.0) {
            val remainKwh = ((100.0 - currentSoc) / 100.0) * 3.97
            ((remainKwh / liveKw) * 60.0).toInt().coerceIn(1, 480)
        } else 0

        val t80 = if (timeToChargeSc80Min > 0) {
            timeToChargeSc80Min
        } else if (liveKw > 0.2 && !currentSoc.isNaN() && currentSoc < 80.0) {
            val remainKwh = ((80.0 - currentSoc) / 100.0) * 3.97
            ((remainKwh / liveKw) * 60.0).toInt().coerceIn(1, 300)
        } else 0

        val liveRate = if (!chargingRatePercent.isNaN() && chargingRatePercent > 0.0) {
            chargingRatePercent
        } else if (liveKw > 0.1) {
            (liveKw / 3.97) * 100.0
        } else Double.NaN

        val maxDev = if (!maxCellDeviationDuringChargeMv.isNaN() && maxCellDeviationDuringChargeMv > 0.0) {
            maxCellDeviationDuringChargeMv
        } else sessionMaxDeltaMv

        return ChargingSessionTelemetry(
            isActive = isActivelyCharging,
            isPluggedIn = isPlugged,
            chargerModeName = chargerModeName,
            chargePowerKw = if (isActivelyCharging) liveKw else Double.NaN,
            chargeCurrentA = if (isActivelyCharging) liveCurrentA else Double.NaN,
            chargeVoltageV = liveVolts,
            sessionEnergyKwh = sessionAccumulatedKwh,
            sessionAh = sessionAccumulatedAh,
            startSocPercent = sessionStartSoc,
            currentSocPercent = currentSoc,
            gainedSocPercent = gainedSoc,
            durationSeconds = durationSec,
            timeToFullMinutes = tFull,
            timeTo80Minutes = t80,
            chargingRatePercentPerHour = liveRate,
            maxCellDeviationDuringChargeMv = maxDev,
            balancingActive = balancingCyclesCount > 0 || (maxDev in 1.0..25.0 && isActivelyCharging),
            balancingCyclesCount = balancingCyclesCount,
            lifetimeChargedKwh = lifetimeModeKwhrCharged,
            lifetimeAcSessions = acChargeSessions,
            lifetimeDcSessions = dcFastChargeSessions,
            estimatedCostInr = sessionAccumulatedKwh * 7.50,
            chargerAvailableVoltageV = chargerAvailableVoltage,
            chargerAvailableCurrentA = chargerAvailableCurrent,
            startTempC = sessionStartTemp,
            currentTempC = if (!minTemp.isNaN()) minTemp else effectiveTemp,
            thermalRiseC = if (!sessionStartTemp.isNaN() && !minTemp.isNaN()) (minTemp - sessionStartTemp).coerceAtLeast(0.0) else 0.0
        )
    }

    @Synchronized
    fun getThermalState(): ThermalState {
        return ThermalState(
            packEffectiveTempC = effectiveTemp,
            packMinTempC = minTemp,
            packMaxTempC = maxTemp,
            packProbesC = packProbes.toList(),
            mcuTempC = mcuTemp,
            motorTempC = motorTemp,
            pduTempC = pduTemp,
            afeTempC = afeTemp,
            packThermalGradientC = packThermalGrad,
            isHighThermalGradient = !packThermalGrad.isNaN() && packThermalGrad >= 10.0,
            hottestSensorIdx = hottestProbeIdx,
            coolestSensorIdx = coolestProbeIdx,
            pduThermalWarning = !pduTemp.isNaN() && pduTemp >= 85.0,
            motorThermalWarning = !motorTemp.isNaN() && motorTemp >= 90.0,
            afeThermalWarning = !afeTemp.isNaN() && afeTemp >= 80.0,
            ambientTempC = ambientTemp,
            vcuBoardTempC = vcuBoardTemp,
            somCoreTempC = somCoreTemp
        )
    }

    @Synchronized
    fun getPowertrainState(): PowertrainState {
        return PowertrainState(
            motorRpm = motorRpm,
            speedKmh = speedKmh,
            throttlePercent = throttlePercent,
            throttleVoltage = throttleVoltage,
            cruiseActive = cruiseActive,
            motorTorqueNm = motorTorqueNm,
            motorPhaseCurrentA = motorPhaseCurrentA,
            wheelSpeedKmh = wheelSpeedKmh,
            wheelSlipPercent = wheelSlipPercent,
            ecoRangeKm = ecoRangeKm,
            normalRangeKm = normalRangeKm,
            sportRangeKm = sportRangeKm,
            hyperRangeKm = hyperRangeKm,
            vcuHardwareSerial = vcuHardwareSerial,
            operationalState = operationalState,
            driveMode = driveMode
        )
    }

    @Synchronized
    fun getVehicleStatus(): VehicleStatus {
        return VehicleStatus(
            odometerKm = odometerKm,
            sideStandDown = sideStandDown,
            regenLevel = regenLevel,
            hillHoldActive = hillHold,
            fallDetected = fallDetected,
            towAlert = towAlert,
            isLocked = isScooterLocked,
            isBootOpen = isBootOpen
        )
    }

    fun setSimulatedLock(locked: Boolean) {
        isScooterLocked = locked
    }

    fun setSimulatedBoot(open: Boolean) {
        isBootOpen = open
    }

    @Synchronized
    fun getControlsState(): VehicleControlsState {
        return VehicleControlsState(
            rightBrakePressed = rightBrakePressed,
            leftBrakePressed = leftBrakePressed,
            hornPressed = hornPressed,
            rightIndicatorActive = rightIndicatorActive,
            leftIndicatorActive = leftIndicatorActive,
            highBeamActive = highBeamActive,
            lowBeamActive = lowBeamActive,
            brakeLightActive = brakeLightActive,
            speakerActive = speakerActive,
            mainContactorClosed = mainContactorClosed,
            lastUpdatedTimestampMs = System.currentTimeMillis()
        )
    }

    fun setDynoModeActive(active: Boolean) {
        isDynoModeActive = active
    }

    fun setFloatKeepAwakeActive(active: Boolean) {
        isFloatKeepAwakeActive = active
    }

    fun resetImuZeroOffset() {
        if (!imuPitchDeg.isNaN()) imuPitchOffsetDeg += imuPitchDeg
        if (!imuRollDeg.isNaN()) imuRollOffsetDeg += imuRollDeg
        imuPitchDeg = 0.0
        imuRollDeg = 0.0
        imuYawDeg = 0.0
    }

    fun setSimulatedMotorAutoTune(offsetDeg: Double) {
        offsetAngleMechanicalDegree = offsetDeg
        mcuCalibrationState = 2 // Complete
        successfulCalibrationCounter += 1
    }

    @Synchronized
    fun getCalibrationTelemetry(): CalibrationTelemetry {
        return CalibrationTelemetry(
            throttleValue1V = if (throttleValue1V.isNaN()) (if (!throttleVoltage.isNaN()) throttleVoltage else 0.96) else throttleValue1V,
            throttleValue2V = if (throttleValue2V.isNaN()) (if (!throttleVoltage.isNaN()) throttleVoltage + 0.01 else 0.97) else throttleValue2V,
            throttleOffset = throttleOffset,
            throttleOffsetCount = throttleOffsetCount,
            throttleDeviationFault = throttleDeviationFault,
            mcuCalibrationState = mcuCalibrationState,
            mcuCalibrationResult = mcuCalibrationResult,
            offsetAngleMechanicalDegree = if (offsetAngleMechanicalDegree.isNaN()) 34.2 else offsetAngleMechanicalDegree,
            successfulCalibrationCounter = successfulCalibrationCounter,
            attemptedCalibrationCounter = attemptedCalibrationCounter,
            imuPitchDeg = if (imuPitchDeg.isNaN()) 0.0 else imuPitchDeg,
            imuRollDeg = if (imuRollDeg.isNaN()) 0.0 else imuRollDeg,
            imuYawDeg = if (imuYawDeg.isNaN()) 0.0 else imuYawDeg,
            frontBrakeVoltageV = if (frontBrakeVoltageV.isNaN()) (if (rightBrakePressed) 3.12 else 0.82) else frontBrakeVoltageV,
            rearBrakeVoltageV = if (rearBrakeVoltageV.isNaN()) (if (leftBrakePressed) 3.15 else 0.81) else rearBrakeVoltageV,
            frontBrakePressed = rightBrakePressed,
            rearBrakePressed = leftBrakePressed,
            isDynoModeActive = isDynoModeActive,
            isFloatKeepAwakeActive = isFloatKeepAwakeActive,
            cellBalancingActive = cellBalancingActive
        )
    }
}
