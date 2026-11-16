package com.sarthak.olacan.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.sarthak.olacan.service.CanStreamManager

class TelemetryViewModel(application: Application) : AndroidViewModel(application) {

    init {
        CanStreamManager.init(application)
    }

    // Direct StateFlow delegates from central CanStreamManager
    val streamStats = CanStreamManager.streamStats
    val batteryState = CanStreamManager.batteryState
    val thermalState = CanStreamManager.thermalState
    val powertrainState = CanStreamManager.powertrainState
    val vehicleStatus = CanStreamManager.vehicleStatus
    val controlsState = CanStreamManager.controlsState
    val chargingSession = CanStreamManager.chargingSession
    val ecuVersions = CanStreamManager.ecuVersions
    val calibrationTelemetry = CanStreamManager.calibrationTelemetry

    // Warnings and Errors States
    val activeAlerts = CanStreamManager.activeAlerts
    val recordedAlerts = CanStreamManager.recordedAlerts
    val dtcLibrary = CanStreamManager.dtcLibrary

    // Raw frames for Live Inspector
    val recentFrames = CanStreamManager.recentFrames

    // Host input & Scanning
    val targetHostInput = CanStreamManager.targetHostInput
    val isScanning = CanStreamManager.isScanning
    val discoveredScooters = CanStreamManager.discoveredScooters
    val showScooterSelectionDialog = CanStreamManager.showScooterSelectionDialog

    fun dismissScooterSelectionDialog() = CanStreamManager.dismissScooterSelectionDialog()
    fun selectScooterToConnect(scooter: com.sarthak.olacan.network.DiscoveredScooter) =
        CanStreamManager.selectScooterToConnect(scooter)

    // Log Recording States
    val isRecording = CanStreamManager.isRecording
    val recordingDurationSec = CanStreamManager.recordingDurationSec
    val recordedFramesCount = CanStreamManager.recordedFramesCount
    val recordedAlertsCount = CanStreamManager.recordedAlertsCount
    val recordedSizeFormatted = CanStreamManager.recordedSizeFormatted
    val lastSavedLog = CanStreamManager.lastSavedLog

    // Log Import & Offline Ride Diagnosis States
    val isAnalyzingLog = CanStreamManager.isAnalyzingLog
    val logAnalysisProgress = CanStreamManager.logAnalysisProgress
    val logAnalysisFramesCount = CanStreamManager.logAnalysisFramesCount
    val currentRideReport = CanStreamManager.currentRideReport

    // Battery Health Test States
    val batteryHealthStats = CanStreamManager.batteryHealthStats
    val batteryHealthReport = CanStreamManager.batteryHealthReport
    val batteryChargeCheck = CanStreamManager.batteryChargeCheck

    // Continuous Coulomb Counting Health & Range State
    val coulombState = CanStreamManager.coulombState
    fun resetCoulombCalibration() = CanStreamManager.resetCoulombCalibration()

    // Simulation / Demo Mode State
    val isDemoMode = CanStreamManager.isDemoMode
    fun startDemoMode() = CanStreamManager.startDemoMode()
    fun stopDemoMode() = CanStreamManager.stopDemoMode()

    // Dynamic Ride Battery Test States
    val rideDistLoggedKm = CanStreamManager.rideDistLoggedKm
    val ridePeakDischargeA = CanStreamManager.ridePeakDischargeA
    val rideSamplesCount = CanStreamManager.rideSamplesCount
    val rideSessionTag = CanStreamManager.rideSessionTag
    val isRideLoggingActive = CanStreamManager.isRideLoggingActive
    fun startRideBatteryTest() = CanStreamManager.startRideBatteryTest()
    fun stopRideBatteryTest() = CanStreamManager.stopRideBatteryTest()
    fun startNewRideCalibration() = CanStreamManager.startRideBatteryTest()
    fun storeRideCalibrationSession() = CanStreamManager.stopRideBatteryTest()

    // 14S Rest vs Load Imbalance & Thermal Probes
    val restCellVoltages = CanStreamManager.restCellVoltages
    val loadCellVoltages = CanStreamManager.loadCellVoltages
    val cellInternalResistances = CanStreamManager.cellInternalResistances
    val packThermalProbes = CanStreamManager.packThermalProbes

    // Auto-Connect & Background Run Settings
    val autoReconnectEnabled = CanStreamManager.autoReconnectEnabled
    val autoConnectOnLaunch = CanStreamManager.autoConnectOnLaunch
    val backgroundTimeoutMinutes = CanStreamManager.backgroundTimeoutMinutes
    val backgroundTimerRemainingSec = CanStreamManager.backgroundTimerRemainingSec

    fun clearRecordedAlerts() = CanStreamManager.clearRecordedAlerts()
    fun toggleRecording(context: Context) = CanStreamManager.toggleRecording(context)
    fun startRecording(context: Context) = CanStreamManager.startRecording(context)
    fun stopRecording(context: Context) = CanStreamManager.stopRecording(context)
    fun dismissSavedDialog() = CanStreamManager.dismissSavedDialog()
    fun analyzeLogFile(context: Context, uri: Uri) = CanStreamManager.analyzeLogFile(context, uri)
    fun clearRideReport() = CanStreamManager.clearRideReport()
    fun startAutoScan(context: Context) = CanStreamManager.startAutoScan(context)
    fun connect(rawInput: String = targetHostInput.value, scope: Int? = null) = CanStreamManager.connect(rawInput, scope)
    fun disconnect() = CanStreamManager.disconnect()

    fun setAutoReconnect(enabled: Boolean) = CanStreamManager.setAutoReconnect(enabled)
    fun setAutoConnectOnLaunch(enabled: Boolean) = CanStreamManager.setAutoConnectOnLaunch(enabled)
    fun setBackgroundTimeout(minutes: Int) = CanStreamManager.setBackgroundTimeout(minutes)

    // Battery Health Test Actions
    fun check100PercentCharge() = CanStreamManager.check100PercentCharge()
    fun dismissChargeCheck() = CanStreamManager.dismissChargeCheck()
    fun startBatteryTest(context: Context) = CanStreamManager.startBatteryTest(context)
    fun pauseBatteryTest() = CanStreamManager.pauseBatteryTest()
    fun resumeBatteryTest(context: Context) = CanStreamManager.resumeBatteryTest(context)
    fun resetBatteryTest(context: Context) = CanStreamManager.resetBatteryTest(context)
    suspend fun completeBatteryTest(context: Context) = CanStreamManager.completeBatteryTest(context)

    // Calibration Suite Actions with Progress
    fun triggerMotorAutoTune(onProgress: (step: String, progress: Float) -> Unit, onResult: (Boolean, String) -> Unit) =
        CanStreamManager.triggerMotorAutoTune(onProgress, onResult)
    fun setDynoMode(enabled: Boolean, onResult: (Boolean, String) -> Unit) =
        CanStreamManager.setDynoMode(enabled, onResult)
    fun setBmsFloatKeepAwake(enabled: Boolean, onResult: (Boolean, String) -> Unit) =
        CanStreamManager.setBmsFloatKeepAwake(enabled, onResult)
    fun resetBmsAfeFaults(onProgress: (step: String, progress: Float) -> Unit = { _, _ -> }, onResult: (Boolean, String) -> Unit) =
        CanStreamManager.resetBmsAfeFaults(onProgress, onResult)
    fun wakeupBatteryPack(onProgress: (step: String, progress: Float) -> Unit = { _, _ -> }, onResult: (Boolean, String) -> Unit) =
        CanStreamManager.wakeupBatteryPack(onProgress, onResult)
    fun syncOcvBaseline(onProgress: (step: String, progress: Float) -> Unit = { _, _ -> }, onResult: (Boolean, String) -> Unit) =
        CanStreamManager.syncOcvBaseline(onProgress, onResult)
    fun calibrateThrottleDeadband(onProgress: (step: String, progress: Float) -> Unit = { _, _ -> }, onResult: (Boolean, String) -> Unit) =
        CanStreamManager.calibrateThrottleDeadband(onProgress, onResult)
    fun calibrateImuZeroLevel(onProgress: (step: String, progress: Float) -> Unit = { _, _ -> }, onResult: (Boolean, String) -> Unit) =
        CanStreamManager.calibrateImuZeroLevel(onProgress, onResult)
    fun setRegenTorqueLevel(level: Int, onProgress: (step: String, progress: Float) -> Unit = { _, _ -> }, onResult: (Boolean, String) -> Unit) =
        CanStreamManager.setRegenTorqueLevel(level, onProgress, onResult)
}
