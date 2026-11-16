package com.sarthak.olacan.analyzer

enum class BatteryTestStatus {
    IDLE,
    CALIBRATING,
    ACTIVE,
    PAUSED,
    COMPLETED
}

data class ChargeCheckResult(
    val isFullyCharged: Boolean,
    val voltageV: Double,
    val socPercent: Double,
    val message: String
)

data class BatteryHealthLiveStats(
    val status: BatteryTestStatus = BatteryTestStatus.IDLE,
    val progressPercent: Double = 0.0, // 0 to 100%
    val startSoc: Double = Double.NaN,
    val currentSoc: Double = Double.NaN,
    val deltaSoc: Double = 0.0,
    val startVoltageV: Double = Double.NaN,
    val currentVoltageV: Double = Double.NaN,
    val dischargeWh: Double = 0.0,
    val regenWh: Double = 0.0,
    val netEnergyWh: Double = 0.0,
    val dischargeAh: Double = 0.0,
    val regenAh: Double = 0.0,
    val netAh: Double = 0.0,
    val distanceKm: Double = 0.0,
    val efficiencyWhPerKm: Double = 0.0,
    val estimatedSohPercent: Double = Double.NaN,
    val estimatedFullCapacityWh: Double = Double.NaN,
    val durationSeconds: Long = 0L,
    val activeRideSeconds: Long = 0L,
    val isAutoPausedParked: Boolean = false,
    val isChargerConnected: Boolean = false,
    val currentSpeedKmh: Double = 0.0,
    val activeLogFramesCount: Long = 0L
)

data class BatteryHealthReport(
    val testDateFormatted: String,
    val durationFormatted: String,
    val logFileName: String,
    val logFilePath: String,
    val totalFrames: Long,

    // SOC & Voltage Span
    val startSoc: Double,
    val endSoc: Double,
    val deltaSoc: Double,
    val startBusVoltageV: Double,
    val endBusVoltageV: Double,
    val minBusVoltageV: Double,
    val maxBusVoltageV: Double,

    // Energy & Ah Metrics
    val grossDischargeWh: Double,
    val grossDischargeKwh: Double,
    val regenWh: Double,
    val regenKwh: Double,
    val netEnergyOutputWh: Double,
    val netEnergyOutputKwh: Double,
    val netDischargeAh: Double,

    // SOH & Capacity Verdict
    val estimatedFullCapacityWh: Double,
    val estimatedFullCapacityKwh: Double,
    val factoryNominalWh: Double = 3980.0, // 3.98 kWh nominal gross Ola S1 Pro Gen 2
    val factoryUsableWh: Double = 3700.0, // 3.70 kWh factory usable window
    val calculatedSohPercent: Double,
    val healthGrade: String, // "GRADE A+ (EXCELLENT)", "GRADE A (GOOD)", "GRADE B (FAIR)", "GRADE C (DEGRADED)"
    val packHardwareCycles: Long = 0L, // 0x6D8 True physical cycles stored in this pack's BMS
    val vehicleChassisCycles: Int = 0, // 0x6E0 Chassis odometer cumulative cycles across all packs
    val acChargeSessions: Int = 0, // Home AC sessions
    val dcFastChargeSessions: Int = 0, // Hypercharger sessions

    // Losses & Efficiency
    val estimatedInternalLossWh: Double, // I^2 * R loss estimate
    val batteryEfficiencyPercent: Double,
    val totalDistanceKm: Double,
    val avgConsumptionWhPerKm: Double,
    val regenContributionPercent: Double,

    // Cell Degradation & MoveOS derates during test
    val worstCellChannel: Int,
    val minCellVoltageRecordedV: Double,
    val maxCellSagRecordedMv: Double,
    val socClampOccurrences: Int,
    val bmsCellSagFaultsCount: Int
)
