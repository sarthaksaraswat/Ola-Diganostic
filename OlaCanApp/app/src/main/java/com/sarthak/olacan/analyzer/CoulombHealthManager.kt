package com.sarthak.olacan.analyzer

import android.content.Context
import android.content.SharedPreferences
import com.sarthak.olacan.model.CanFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Real-world state of battery health and dynamic estimated range computed using Coulomb counting
 * (integration of current and voltage over continuous riding distance).
 */
data class CoulombCalibrationState(
    val isCalibrated: Boolean = false,
    val estimatedRangeKm: Double = Double.NaN,
    val calibratedSohPercent: Double = Double.NaN,
    val calibratedCapacityKwh: Double = Double.NaN,
    val consumptionWhPerKm: Double = Double.NaN,
    val currentRideDistanceKm: Double = 0.0,
    val requiredDistanceKm: Double = 15.0,
    val calibrationProgressPercent: Double = 0.0,
    val netEnergyWh: Double = 0.0,
    val netDischargeAh: Double = 0.0,
    val deltaSocPercent: Double = 0.0,
    val statusLabel: String = "Drive 15+ km to calibrate"
)

/**
 * Coulomb Counting Health & Range Estimator.
 * Replaces static/inaccurate MoveOS CAN SOH with real measured capacity.
 * By default, stays blank until the vehicle has been driven for >= 15 km with real Coulomb integration.
 */
object CoulombHealthManager {

    private const val PREFS_NAME = "coulomb_health_prefs"
    private const val KEY_IS_CALIBRATED = "coulomb_is_calibrated"
    private const val KEY_CALIBRATED_SOH = "coulomb_calibrated_soh"
    private const val KEY_CALIBRATED_CAPACITY_WH = "coulomb_calibrated_capacity_wh"
    private const val KEY_CALIBRATED_WH_PER_KM = "coulomb_calibrated_wh_per_km"
    private const val KEY_CALIBRATED_DISTANCE = "coulomb_calibrated_distance"
    private const val KEY_CALIBRATION_TIMESTAMP = "coulomb_calibration_timestamp"

    private const val FACTORY_USABLE_WH = 3976.0 // 3.976 kWh (Ola S1 Pro 14S NMC nominal usable window)
    const val REQUIRED_DISTANCE_KM = 15.0
    private const val MIN_DELTA_SOC = 5.0 // Minimum 5.0% SOC discharge depth required to extrapolate

    private var prefs: SharedPreferences? = null

    private val _state = MutableStateFlow(CoulombCalibrationState())
    val state = _state.asStateFlow()

    // Real-time integration accumulators
    private var lastFrameTimeMs: Long = 0L
    private var lastVoltageV: Double = 51.8
    private var startOdoKm: Double = Double.NaN
    private var currentOdoKm: Double = Double.NaN
    private var integratedDistanceKm: Double = 0.0
    private var startSocVal: Double = Double.NaN
    private var currentSocVal: Double = Double.NaN
    private var currentSpeedKmh: Double = 0.0

    private var dischargeEnergyWh: Double = 0.0
    private var regenEnergyWh: Double = 0.0
    private var dischargeAh: Double = 0.0
    private var regenAh: Double = 0.0

    // Calibrated cached numbers
    private var savedIsCalibrated: Boolean = false
    private var savedSoh: Double = Double.NaN
    private var savedCapacityWh: Double = Double.NaN
    private var savedWhPerKm: Double = Double.NaN

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p
        loadFromPrefs()
    }

    fun loadFromPrefs() {
        val p = prefs ?: return
        savedIsCalibrated = p.getBoolean(KEY_IS_CALIBRATED, false)
        if (savedIsCalibrated) {
            val rawSoh = p.getFloat(KEY_CALIBRATED_SOH, -1f).toDouble()
            val soh = rawSoh.coerceIn(40.0, 100.0) // SOH can NEVER exceed 100.0%
            val rawCap = p.getFloat(KEY_CALIBRATED_CAPACITY_WH, -1f).toDouble()
            val cap = rawCap.coerceIn(1500.0, 4200.0)
            val whKm = p.getFloat(KEY_CALIBRATED_WH_PER_KM, -1f).toDouble()
            if (rawSoh in 40.0..115.0 && cap in 1500.0..4500.0 && whKm in 10.0..80.0) {
                savedSoh = soh
                savedCapacityWh = cap
                savedWhPerKm = whKm
                if (rawSoh > 100.0) {
                    // Automatically correct previously saved >100% values on device
                    p.edit().putFloat(KEY_CALIBRATED_SOH, soh.toFloat()).apply()
                }
            } else {
                savedIsCalibrated = false
                savedSoh = Double.NaN
                savedCapacityWh = Double.NaN
                savedWhPerKm = Double.NaN
            }
        }
        updateState()
    }

    fun saveCalibration(soh: Double, capacityWh: Double, whPerKm: Double, dist: Double) {
        val clampedSoh = soh.coerceIn(40.0, 100.0) // Strictly capped at 100.0% max
        val clampedCap = capacityWh.coerceIn(1500.0, 4200.0)
        val clampedWhKm = whPerKm.coerceIn(15.0, 75.0)
        prefs?.edit()?.apply {
            putBoolean(KEY_IS_CALIBRATED, true)
            putFloat(KEY_CALIBRATED_SOH, clampedSoh.toFloat())
            putFloat(KEY_CALIBRATED_CAPACITY_WH, clampedCap.toFloat())
            putFloat(KEY_CALIBRATED_WH_PER_KM, clampedWhKm.toFloat())
            putFloat(KEY_CALIBRATED_DISTANCE, dist.toFloat())
            putLong(KEY_CALIBRATION_TIMESTAMP, System.currentTimeMillis())
            apply()
        }
        savedIsCalibrated = true
        savedSoh = clampedSoh
        savedCapacityWh = clampedCap
        savedWhPerKm = clampedWhKm
        updateState()
    }

    fun resetCalibration() {
        prefs?.edit()?.clear()?.apply()
        savedIsCalibrated = false
        savedSoh = Double.NaN
        savedCapacityWh = Double.NaN
        savedWhPerKm = Double.NaN

        startOdoKm = Double.NaN
        currentOdoKm = Double.NaN
        integratedDistanceKm = 0.0
        startSocVal = Double.NaN
        dischargeEnergyWh = 0.0
        regenEnergyWh = 0.0
        dischargeAh = 0.0
        regenAh = 0.0
        lastFrameTimeMs = 0L

        updateState()
    }

    fun onCanFrameReceived(frame: CanFrame) {
        val d = frame.data
        val now = frame.timestampMs

        when (frame.id) {
            0x150 -> {
                // Bus voltage
                if (d.size >= 2) {
                    val rawV = (d[0].toInt() and 0xFF) or ((d[1].toInt() and 0xFF) shl 8)
                    val v = rawV * 0.001
                    if (v in 35.0..62.0) {
                        lastVoltageV = v
                    }
                }
            }
            0x120 -> {
                // SOC and Current
                if (d.size >= 4) {
                    val rawSoc = ((d[2].toInt() and 0xFF) or ((d[3].toInt() and 0xFF) shl 8)) * 0.01
                    if (rawSoc in 0.0..100.0) {
                        if (startSocVal.isNaN()) {
                            startSocVal = rawSoc
                        }
                        currentSocVal = rawSoc
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
            0x313 -> {
                // Speed
                if (d.size >= 2) {
                    val rawSpd = ((d[0].toInt() and 0xFF) or ((d[1].toInt() and 0xFF) shl 8)) * 0.1
                    if (rawSpd in 0.0..200.0) {
                        currentSpeedKmh = rawSpd
                    }
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
                        if (startOdoKm.isNaN()) {
                            startOdoKm = km
                        }
                        currentOdoKm = km
                        evaluateCalibration()
                    }
                }
            }
        }
    }

    private fun processCurrentSample(currentA: Double, nowMs: Long) {
        if (currentA !in -250.0..150.0) return
        if (lastFrameTimeMs > 0L && nowMs > lastFrameTimeMs) {
            val dtMs = nowMs - lastFrameTimeMs
            if (dtMs in 1..2000) {
                val dtHours = dtMs / 3600000.0
                val powerWatts = Math.abs(currentA * lastVoltageV)
                if (currentA < -0.5) {
                    // Motor discharge
                    dischargeEnergyWh += (powerWatts * dtHours)
                    dischargeAh += (Math.abs(currentA) * dtHours)
                } else if (currentA > 0.5 && currentSpeedKmh > 1.5) {
                    // Kinetic regen
                    regenEnergyWh += (powerWatts * dtHours)
                    regenAh += (currentA * dtHours)
                }
            }
        }
        lastFrameTimeMs = nowMs
    }

    fun addIntegratedDistance(incKm: Double, currentSpeed: Double, currentA: Double, voltageV: Double) {
        if (incKm > 0.0) {
            integratedDistanceKm += incKm
            currentSpeedKmh = currentSpeed
            if (lastVoltageV.isNaN() || lastVoltageV <= 0.0) {
                lastVoltageV = if (voltageV > 30.0) voltageV else 51.8
            }
            if (currentA < -0.5) {
                val dtHours = (incKm / Math.max(1.0, currentSpeed))
                val powerWatts = Math.abs(currentA * lastVoltageV)
                dischargeEnergyWh += (powerWatts * dtHours)
                dischargeAh += (Math.abs(currentA) * dtHours)
            } else if (currentA > 0.5) {
                val dtHours = (incKm / Math.max(1.0, currentSpeed))
                val powerWatts = Math.abs(currentA * lastVoltageV)
                regenEnergyWh += (powerWatts * dtHours)
                regenAh += (Math.abs(currentA) * dtHours)
            }
            evaluateCalibration()
        }
    }

    private fun getEffectiveDistance(): Double {
        val odoDelta = if (!startOdoKm.isNaN() && !currentOdoKm.isNaN() && currentOdoKm >= startOdoKm) {
            currentOdoKm - startOdoKm
        } else 0.0
        return if (odoDelta > 0.0) odoDelta else integratedDistanceKm
    }

    fun updateCalculations() {
        evaluateCalibration()
    }

    private fun evaluateCalibration() {
        val dist = getEffectiveDistance()

        val deltaSoc = if (!startSocVal.isNaN() && !currentSocVal.isNaN()) {
            Math.max(0.0, startSocVal - currentSocVal)
        } else 0.0

        val netWh = Math.max(0.0, dischargeEnergyWh - regenEnergyWh)

        // Threshold: Driven >= 15 km AND dropped >= 5% SOC with at least 150 Wh net discharge
        if (dist >= REQUIRED_DISTANCE_KM && deltaSoc >= MIN_DELTA_SOC && netWh >= 150.0) {
            val fullCapacityWh = (netWh / deltaSoc) * 100.0
            val measuredSoh = ((fullCapacityWh / FACTORY_USABLE_WH) * 100.0).coerceIn(40.0, 100.0) // Strictly capped at 100% max
            val whPerKm = (netWh / dist).coerceIn(15.0, 75.0)

            saveCalibration(measuredSoh, fullCapacityWh, whPerKm, dist)
        } else {
            updateState()
        }
    }

    /**
     * Called when a ride completes, recording stops, or vehicle disconnects.
     * Evaluates whether the session met calibration criteria or allows finalizing near 15km threshold.
     */
    fun finalizeCalibration() {
        val dist = getEffectiveDistance()

        val deltaSoc = if (!startSocVal.isNaN() && !currentSocVal.isNaN()) {
            Math.max(0.0, startSocVal - currentSocVal)
        } else 0.0

        val netWh = Math.max(0.0, dischargeEnergyWh - regenEnergyWh)

        if (dist >= (REQUIRED_DISTANCE_KM - 1.0) && deltaSoc >= MIN_DELTA_SOC && netWh >= 150.0) {
            val fullCapacityWh = (netWh / deltaSoc) * 100.0
            val measuredSoh = ((fullCapacityWh / FACTORY_USABLE_WH) * 100.0).coerceIn(40.0, 100.0)
            val whPerKm = (netWh / dist).coerceIn(15.0, 75.0)
            saveCalibration(measuredSoh, fullCapacityWh, whPerKm, dist)
        } else {
            updateState()
        }
    }

    private fun updateState() {
        val dist = getEffectiveDistance()

        val deltaSoc = if (!startSocVal.isNaN() && !currentSocVal.isNaN()) {
            Math.max(0.0, startSocVal - currentSocVal)
        } else 0.0

        val netWh = Math.max(0.0, dischargeEnergyWh - regenEnergyWh)
        val netAhVal = Math.max(0.0, dischargeAh - regenAh)

        val progressPct = ((dist / REQUIRED_DISTANCE_KM) * 100.0).coerceIn(0.0, 100.0)
        val effectiveSoc = if (!currentSocVal.isNaN()) currentSocVal else 50.0

        if (savedIsCalibrated) {
            // Calibrated via Coulomb counting over 15+ km!
            val totalRangeKm = savedCapacityWh / savedWhPerKm
            val estRange = (effectiveSoc / 100.0) * totalRangeKm

            _state.value = CoulombCalibrationState(
                isCalibrated = true,
                estimatedRangeKm = estRange,
                calibratedSohPercent = savedSoh,
                calibratedCapacityKwh = savedCapacityWh / 1000.0,
                consumptionWhPerKm = savedWhPerKm,
                currentRideDistanceKm = dist,
                requiredDistanceKm = REQUIRED_DISTANCE_KM,
                calibrationProgressPercent = 100.0,
                netEnergyWh = netWh,
                netDischargeAh = netAhVal,
                deltaSocPercent = deltaSoc,
                statusLabel = "Calibrated (${String.format(Locale.US, "%.1f", savedWhPerKm)} Wh/km)"
            )
        } else {
            // Default state: stays blank everywhere until >= 15 km driven!
            val status = if (dist > 0.1) {
                "Calibrating (${String.format(Locale.US, "%.1f", dist)} / 15.0 km)"
            } else {
                "Drive 15+ km to calibrate"
            }

            _state.value = CoulombCalibrationState(
                isCalibrated = false,
                estimatedRangeKm = Double.NaN,
                calibratedSohPercent = Double.NaN,
                calibratedCapacityKwh = Double.NaN,
                consumptionWhPerKm = Double.NaN,
                currentRideDistanceKm = dist,
                requiredDistanceKm = REQUIRED_DISTANCE_KM,
                calibrationProgressPercent = progressPct,
                netEnergyWh = netWh,
                netDischargeAh = netAhVal,
                deltaSocPercent = deltaSoc,
                statusLabel = status
            )
        }
    }
}
