package com.sarthak.olacan.model

/**
 * High-level decoded vehicle telemetry models.
 */

data class BatteryState(
    val socPercent: Double = Double.NaN,
    val displaySocPercent: Double = Double.NaN,
    val packSohPercent: Double = Double.NaN,
    val busVoltageV: Double = Double.NaN,
    val batteryCurrentA: Double = Double.NaN,
    val powerKw: Double = Double.NaN,
    val cellMinMv: Double = Double.NaN,
    val cellMaxMv: Double = Double.NaN,
    val cellDeltaMv: Double = Double.NaN,
    val cellVoltagesV: List<Double> = emptyList(), // 14 series cells
    val restingCellDeltaMv: Double = Double.NaN,
    val peakLoadCellDeltaMv: Double = Double.NaN,
    val cellInternalResistancesMOhms: List<Double> = emptyList(), // 14 cells dynamic Ri in mOhm
    val cellPeakSagMv: List<Double> = emptyList(), // 14 cells max dynamic sag under load (mV)
    val is1PercentSocClampActive: Boolean = false,
    val clampedTrueSocEstPercent: Double = Double.NaN,
    val criticalCellFloorBreached: Boolean = false,
    val criticalCellFloorNumber: Int = 0,
    val bmsMaxChargeCurrentA: Double = Double.NaN, // 0x130 CCL (Cuts to 0A when battery hot/full)
    val bmsMaxDischargeCurrentA: Double = Double.NaN, // 0x130 DCL
    val bmsNominalCapacityAh: Double = Double.NaN, // 0x130 (e.g. 31.0 Ah sub-pack)
    val packHardwareCycles: Long = 0L, // 0x6D8 True physical cycles stored in this pack's BMS EEPROM
    val vehicleChassisCycles: Int = 0, // 0x6E0 Cumulative vehicle chassis cycles across all packs
    val acChargeSessions: Int = 0, // 0x6E0 Home AC slow charging count
    val dcFastChargeSessions: Int = 0, // 0x6D8 Hypercharger fast charging count
    val lifetimeAhThroughput: Long = 0L, // 0x6DA Total lifetime Ah throughput
    val batterySerial: String = "", // 0x110 Physical battery barcode/serial
    val brickSohArray: List<Int> = emptyList(), // 0x659 Sub-module parallel brick SOH
    val isPluggedIn: Boolean = false, // 0x62D Charger cable connected/plugged in
    val isChargerConnected: Boolean = false, // 0x213 Charger active state
    val chargingSession: ChargingSessionTelemetry = ChargingSessionTelemetry()
) {
    // Verified on Ola CAN: Positive current is charging / regen into pack; negative current is motor draw
    val isCharging: Boolean
        get() = isPluggedIn || isChargerConnected || (!batteryCurrentA.isNaN() && batteryCurrentA > 0.5)

    val isDischarging: Boolean
        get() = !isCharging && !batteryCurrentA.isNaN() && batteryCurrentA < -0.5

    val dischargePowerKw: Double
        get() = if (!isCharging && !powerKw.isNaN() && powerKw < 0.0) Math.abs(powerKw) else 0.0

    val regenPowerKw: Double
        get() = if (!isCharging && !powerKw.isNaN() && powerKw > 0.0) powerKw else 0.0

    val motorHorsepower: Double
        get() = dischargePowerKw * 1.34102
}

data class ThermalState(
    val packEffectiveTempC: Double = Double.NaN,
    val packMinTempC: Double = Double.NaN,
    val packMaxTempC: Double = Double.NaN,
    val packProbesC: List<Double> = emptyList(), // 6 pack temperature sensors
    val mcuTempC: Double = Double.NaN,
    val motorTempC: Double = Double.NaN,
    val pduTempC: Double = Double.NaN,
    val afeTempC: Double = Double.NaN,
    val packThermalGradientC: Double = Double.NaN,
    val isHighThermalGradient: Boolean = false,
    val hottestSensorIdx: Int = 0, // 1-based index (e.g. 5 = Core)
    val coolestSensorIdx: Int = 0, // 1-based index (e.g. 3 = Shell)
    val pduThermalWarning: Boolean = false,
    val motorThermalWarning: Boolean = false,
    val afeThermalWarning: Boolean = false,
    val ambientTempC: Double = Double.NaN, // 0x15B External ambient temperature
    val vcuBoardTempC: Double = Double.NaN, // 0x64B VCU PCB temperature
    val somCoreTempC: Double = Double.NaN // 0x64B MoveOS Android SOM core temperature
)

data class PowertrainState(
    val motorRpm: Double = Double.NaN,
    val speedKmh: Double = Double.NaN,
    val throttlePercent: Double = Double.NaN,
    val throttleVoltage: Double = Double.NaN,
    val cruiseActive: Boolean = false,
    val motorTorqueNm: Double = Double.NaN, // 0x223 Mechanical shaft torque
    val motorPhaseCurrentA: Double = Double.NaN, // 0x223 Phase current magnitude
    val wheelSpeedKmh: Double = Double.NaN, // 0x15B Ground wheel sensor speed
    val wheelSlipPercent: Double = Double.NaN, // Wheel slip ratio vs motor speed
    val ecoRangeKm: Int = 0, // 0x6F4 Live range prediction
    val normalRangeKm: Int = 0,
    val sportRangeKm: Int = 0,
    val hyperRangeKm: Int = 0,
    val vcuHardwareSerial: String = "", // 0x689 Hardware identifier
    val operationalState: String = "", // 0x685 Driving state
    val driveMode: String = "" // 0x113 MCU Discharge Mode (Parked, Normal, Sport, Eco, Hyper, Reverse)
)

data class VehicleStatus(
    val odometerKm: Double = Double.NaN,
    val sideStandDown: Boolean = false,
    val regenLevel: Int = 0, // 0 = OFF (0%), 1 = LOW (5%), 2 = MED (10%), 3 = HIGH (15%), 4 = MAX (20%)
    val hillHoldActive: Boolean = false,
    val fallDetected: Boolean = false,
    val towAlert: Boolean = false,
    val isLocked: Boolean = false,
    val isBootOpen: Boolean = false
)

data class VehicleControlsState(
    val rightBrakePressed: Boolean = false, // Front hydraulic brake (0x15B bit 4, 0x320)
    val leftBrakePressed: Boolean = false,  // Rear brake lever (0x15B bit 5, 0x320)
    val hornPressed: Boolean = false,       // Horn button (0x6AC bit 4, 0x6BF bit 31, 0x6C5)
    val rightIndicatorActive: Boolean = false, // Right turn switch (0x6AC bit 3, 0x353 bit 40, 0x6BF bit 23)
    val leftIndicatorActive: Boolean = false,  // Left turn switch (0x6AC bit 2, 0x353 bit 41, 0x6BF bit 24)
    val highBeamActive: Boolean = false,    // High beam (0x6AC bit 0, 0x353 bit 42, 0x6BF bit 22)
    val lowBeamActive: Boolean = false,     // Low beam / DRL (0x353 bit 50, 0x6BF bit 21)
    val brakeLightActive: Boolean = false,  // Rear brake light (0x6BF bit 25, 0x6C3)
    val speakerActive: Boolean = false,     // MoveOS audio synthesizer (0x6BF bit 14)
    val mainContactorClosed: Boolean = false, // High-voltage pack contactor (0x11C bits 0..3, 0x6BF bits 29-30)
    val lastUpdatedTimestampMs: Long = 0L
)

data class CalibrationTelemetry(
    val throttleValue1V: Double = Double.NaN,
    val throttleValue2V: Double = Double.NaN,
    val throttleOffset: Int = 0,
    val throttleOffsetCount: Int = 0,
    val throttleDeviationFault: Boolean = false,
    val mcuCalibrationState: Int = 0, // 0=Idle, 1=In-Progress, 2=Complete, 3=Failed
    val mcuCalibrationResult: Int = 0,
    val offsetAngleMechanicalDegree: Double = Double.NaN,
    val successfulCalibrationCounter: Int = 0,
    val attemptedCalibrationCounter: Int = 0,
    val imuPitchDeg: Double = Double.NaN,
    val imuRollDeg: Double = Double.NaN,
    val imuYawDeg: Double = Double.NaN,
    val frontBrakeVoltageV: Double = Double.NaN,
    val rearBrakeVoltageV: Double = Double.NaN,
    val frontBrakePressed: Boolean = false,
    val rearBrakePressed: Boolean = false,
    val isDynoModeActive: Boolean = false,
    val isFloatKeepAwakeActive: Boolean = false,
    val cellBalancingActive: Boolean = false
)

data class ChargingSessionTelemetry(
    val isActive: Boolean = false,
    val isPluggedIn: Boolean = false,
    val chargerModeName: String = "Home AC Portable (750W)",
    val chargePowerKw: Double = Double.NaN,
    val chargeCurrentA: Double = Double.NaN,
    val chargeVoltageV: Double = Double.NaN,
    val sessionEnergyKwh: Double = 0.0,
    val sessionAh: Double = 0.0,
    val startSocPercent: Double = Double.NaN,
    val currentSocPercent: Double = Double.NaN,
    val gainedSocPercent: Double = 0.0,
    val durationSeconds: Long = 0L,
    val timeToFullMinutes: Int = 0,
    val timeTo80Minutes: Int = 0,
    val chargingRatePercentPerHour: Double = Double.NaN,
    val maxCellDeviationDuringChargeMv: Double = Double.NaN,
    val balancingActive: Boolean = false,
    val balancingCyclesCount: Int = 0,
    val lifetimeChargedKwh: Double = Double.NaN,
    val lifetimeAcSessions: Int = 0,
    val lifetimeDcSessions: Int = 0,
    val estimatedCostInr: Double = 0.0,
    val chargerAvailableVoltageV: Double = Double.NaN,
    val chargerAvailableCurrentA: Double = Double.NaN,
    val startTempC: Double = Double.NaN,
    val currentTempC: Double = Double.NaN,
    val thermalRiseC: Double = 0.0
)

enum class AlertSeverity {
    CRITICAL,
    WARNING,
    INFO
}

data class VehicleAlert(
    val id: String,
    val title: String,
    val description: String,
    val severity: AlertSeverity,
    val component: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val sourceCanIdHex: String = "",
    val isActive: Boolean = true
)

data class DtcFault(
    val id: String,
    val title: String,
    val description: String,
    val severity: AlertSeverity,
    val component: String,
    val fixable: Boolean = false,
    val postFixMessage: String? = null
)

data class EcuVersions(
    val vcuFirmware: String = "",
    val vcuBootloader: String = "",
    val bmsFirmware: String = "",
    val bmsBootloader: String = "",
    val mcuFirmware: String = "",
    val huFirmware: String = "",
    val huBootloader: String = "",
    val bleVersion: String = "",
    val hbcVersion: String = "",
    val moveOsVersion: String = "",
    val modelName: String = "",
    val isMoveOsSupported: Boolean = true
)

data class StreamStats(
    val isConnected: Boolean = false,
    val connectedHost: String = "",
    val vehicleName: String = "",
    val totalFrames: Long = 0L,
    val framesPerSecond: Double = 0.0,
    val distinctIdsCount: Int = 0,
    val latencyMs: Long = 0L,
    val isAwake: Boolean = false,
    val statusMessage: String = "Disconnected"
) {
    fun getEffectiveVehicleName(): String = if (vehicleName.isNotBlank()) vehicleName else if (connectedHost.isNotBlank()) connectedHost else "Ola S1"
}
