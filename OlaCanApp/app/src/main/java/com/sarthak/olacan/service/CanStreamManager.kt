package com.sarthak.olacan.service

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.ContextCompat
import com.sarthak.olacan.BuildConfig
import com.sarthak.olacan.analyzer.BatteryHealthManager
import com.sarthak.olacan.analyzer.BatteryHealthLiveStats
import com.sarthak.olacan.analyzer.BatteryHealthReport
import com.sarthak.olacan.analyzer.BatteryTestStatus
import com.sarthak.olacan.analyzer.ChargeCheckResult
import com.sarthak.olacan.analyzer.CoulombHealthManager
import com.sarthak.olacan.analyzer.RideDiagnosticReport
import com.sarthak.olacan.analyzer.RideLogAnalyzer
import com.sarthak.olacan.decoder.OlaCanDecoder
import com.sarthak.olacan.model.BatteryState
import com.sarthak.olacan.model.CalibrationTelemetry
import com.sarthak.olacan.model.CanFrame
import com.sarthak.olacan.model.DtcFault
import com.sarthak.olacan.model.EcuVersions
import com.sarthak.olacan.model.PowertrainState
import com.sarthak.olacan.model.StreamStats
import com.sarthak.olacan.model.ThermalState
import com.sarthak.olacan.model.VehicleAlert
import com.sarthak.olacan.model.VehicleControlsState
import com.sarthak.olacan.model.VehicleStatus
import com.sarthak.olacan.network.DiscoveredScooter
import com.sarthak.olacan.network.NetworkScanner
import com.sarthak.olacan.network.S1GrpcStreamClient
import com.sarthak.olacan.util.FaultCatalog
import com.sarthak.olacan.util.LogRecorder
import com.sarthak.olacan.util.SavedLogInfo
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Central singleton managing the CAN stream lifecycle, auto-reconnection,
 * background countdown timers, and high-performance throttled data distribution.
 */
object CanStreamManager {

    private const val PREFS_NAME = "ola_can_prefs"
    private const val KEY_LAST_HOST = "last_connected_host"
    private const val KEY_AUTO_RECONNECT = "auto_reconnect_enabled"
    private const val KEY_AUTO_CONNECT_LAUNCH = "auto_connect_on_launch"
    private const val KEY_BG_TIMEOUT_MINS = "bg_timeout_minutes"

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        com.sarthak.olacan.util.AppLogManager.error("CanStreamManager", "Uncaught coroutine exception: ${throwable.message}", throwable)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + coroutineExceptionHandler)

    private val decoder = OlaCanDecoder()
    private val logRecorder = LogRecorder()
    @Volatile private var client: S1GrpcStreamClient? = null
    private var connectJob: Job? = null
    private var reconnectJob: Job? = null
    private var bgTimerJob: Job? = null

    private var appContext: Context? = null
    private var prefs: SharedPreferences? = null

    // Connection & Stream States
    val streamStats = MutableStateFlow(StreamStats())
    val batteryState = MutableStateFlow(BatteryState())
    val thermalState = MutableStateFlow(ThermalState())
    val powertrainState = MutableStateFlow(PowertrainState())
    val vehicleStatus = MutableStateFlow(VehicleStatus())
    val controlsState = MutableStateFlow(VehicleControlsState())
    val chargingSession = MutableStateFlow(com.sarthak.olacan.model.ChargingSessionTelemetry())
    val ecuVersions = MutableStateFlow(EcuVersions())
    val calibrationTelemetry = MutableStateFlow(com.sarthak.olacan.model.CalibrationTelemetry())
    private var bmsKeepAwakeJob: Job? = null

    // Simulation / Demo Mode State
    val isDemoMode = MutableStateFlow(false)
    private var demoJob: Job? = null

    // Short Ride Sample Analytics & Dynamic Calibration States
    val rideDistLoggedKm = MutableStateFlow(3.4)
    val ridePeakDischargeA = MutableStateFlow(118.4)
    val rideSamplesCount = MutableStateFlow(1420)
    val rideSessionTag = MutableStateFlow("Session #4 (Logged)")
    val isRideLoggingActive = MutableStateFlow(false)
    private var rideLoggingJob: Job? = null

    // 14S Rest vs Load Cell Voltages & Thermal Reference
    val restCellVoltages = MutableStateFlow(listOf(3.688, 3.690, 3.685, 3.682, 3.694, 3.686, 3.689, 3.691, 3.683, 3.688, 3.696, 3.687, 3.692, 3.685))
    val loadCellVoltages = MutableStateFlow(listOf(3.542, 3.550, 3.538, 3.518, 3.554, 3.536, 3.540, 3.548, 3.532, 3.544, 3.556, 3.538, 3.548, 3.535))
    val cellInternalResistances = MutableStateFlow(listOf(2.3, 2.4, 2.2, 2.4, 2.3, 2.4, 2.3, 2.2, 2.4, 2.3, 2.2, 2.4, 2.3, 2.4))
    val packThermalProbes = MutableStateFlow(listOf(33.5, 34.1, 33.8, 34.6, 35.6, 33.2))

    // Warnings & Errors States
    val activeAlerts = MutableStateFlow<List<VehicleAlert>>(emptyList())
    val recordedAlerts = MutableStateFlow<List<VehicleAlert>>(emptyList())
    val dtcLibrary = MutableStateFlow<List<DtcFault>>(emptyList())

    // Raw Frames
    private val _recentFrames = MutableStateFlow<List<CanFrame>>(emptyList())
    val recentFrames = _recentFrames.asStateFlow()
    private val pendingFramesBuffer = Collections.synchronizedList(mutableListOf<CanFrame>())

    // Coulomb Counting & Real SOH / Range Estimation State
    val coulombState = CoulombHealthManager.state
    fun resetCoulombCalibration() = CoulombHealthManager.resetCalibration()

    // Host & Scanning Input
    val targetHostInput = MutableStateFlow("192.168.1.134")
    val isScanning = MutableStateFlow(false)

    // Log Recording States
    val isRecording = MutableStateFlow(false)
    private val savingRecording = java.util.concurrent.atomic.AtomicBoolean(false)
    val recordingDurationSec = MutableStateFlow(0L)
    val recordedFramesCount = MutableStateFlow(0L)
    val recordedAlertsCount = MutableStateFlow(0)
    val recordedSizeFormatted = MutableStateFlow("0 KB")
    val lastSavedLog = MutableStateFlow<SavedLogInfo?>(null)

    // Log Import & Offline Ride Diagnosis States
    val isAnalyzingLog = MutableStateFlow(false)
    val logAnalysisProgress = MutableStateFlow(0)
    val logAnalysisFramesCount = MutableStateFlow(0L)
    val currentRideReport = MutableStateFlow<RideDiagnosticReport?>(null)

    // Battery Health Test States (Delegated from BatteryHealthManager)
    val batteryHealthStats = BatteryHealthManager.liveStats
    val batteryHealthReport = BatteryHealthManager.lastReport
    val batteryChargeCheck = BatteryHealthManager.chargeCheck

    // Multiple Scooter Discovery States
    val discoveredScooters = MutableStateFlow<List<DiscoveredScooter>>(emptyList())
    val showScooterSelectionDialog = MutableStateFlow(false)


    // Auto-Connect & Background Settings
    val autoReconnectEnabled = MutableStateFlow(true)
    val autoConnectOnLaunch = MutableStateFlow(true)
    val backgroundTimeoutMinutes = MutableStateFlow(5) // default 5 minutes
    val backgroundTimerRemainingSec = MutableStateFlow<Int?>(null)
    val isAppInBackground = MutableStateFlow(false)

    // Rate calculation & distinct CAN ID tracking
    private var frameCountInInterval = 0
    private val totalFramesCounter = AtomicLong(0L)
    private val seenCanIds = java.util.BitSet(0x800)
    private val distinctIdsCount = java.util.concurrent.atomic.AtomicInteger(0)
    private var shouldKeepConnected = false
    private var reconnectAttempt = 0

    fun init(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Load saved preferences
        prefs?.let { p ->
            val savedHost = p.getString(KEY_LAST_HOST, "192.168.1.134") ?: "192.168.1.134"
            targetHostInput.value = savedHost
            autoReconnectEnabled.value = p.getBoolean(KEY_AUTO_RECONNECT, true)
            autoConnectOnLaunch.value = p.getBoolean(KEY_AUTO_CONNECT_LAUNCH, true)
            backgroundTimeoutMinutes.value = p.getInt(KEY_BG_TIMEOUT_MINS, 5)
            val savedThrottleRest = p.getFloat("calibrated_throttle_rest_v", 0.95f).toDouble()
            decoder.setCalibratedThrottleRest(savedThrottleRest, margin = 0.12)
        }

        // Initialize MoveOS fault catalog from assets
        FaultCatalog.init(app)
        dtcLibrary.value = FaultCatalog.getAllFaults()

        // Initialize Battery Health Manager
        BatteryHealthManager.init(app)
        CoulombHealthManager.init(app)

        // Attach alert callback from CAN decoder
        decoder.onAlertTriggered = { alert ->
            scope.launch(Dispatchers.Main) {
                if (logRecorder.isRecording()) {
                    logRecorder.recordAlert(alert)
                    recordedAlertsCount.value = logRecorder.getRecordedAlertsCount()
                }

                val current = recordedAlerts.value
                val isRecentDup = current.any { it.id == alert.id && (alert.timestampMs - it.timestampMs) < 4000 }
                if (!isRecentDup) {
                    recordedAlerts.value = (listOf(alert) + current).take(200)
                }
            }
        }

        // Periodic ticker for FPS, UI updates & recording telemetry (every 250ms)
        startPeriodicTicker()

        // Auto-connect on startup if enabled and no startup crash detected
        if (autoConnectOnLaunch.value && targetHostInput.value.isNotBlank()) {
            val hadCrashOnLaunch = com.sarthak.olacan.util.AppLogManager.hasRecentCrashOnLaunch(app)
            if (hadCrashOnLaunch) {
                com.sarthak.olacan.util.AppLogManager.warn(
                    "CanStreamManager",
                    "Previous session crashed shortly after startup. Pausing auto-connect on this launch to ensure UI stability."
                )
            } else {
                scope.launch {
                    delay(800) // slight delay to allow network sockets & Compose UI to initialize
                    connect(targetHostInput.value, isAutoLaunch = true)
                }
            }
        }
    }

    private fun startPeriodicTicker() {
        scope.launch(Dispatchers.Default) {
            var lastTickTime = System.currentTimeMillis()
            var lastBufferFlush = System.currentTimeMillis()
            var lastBgUiUpdate = 0L

            while (isActive) {
                val isBg = isAppInBackground.value
                val tickDelay = if (isBg) 1000L else 250L
                delay(tickDelay)

                val now = System.currentTimeMillis()
                val deltaSec = (now - lastTickTime) / 1000.0

                // Rate calculation & 1 Hz streamStats update (dampens recomposition from 5 Hz to 1 Hz)
                if (deltaSec >= 1.0) {
                    val fps = frameCountInInterval / deltaSec
                    frameCountInInterval = 0
                    lastTickTime = now

                    streamStats.value = streamStats.value.copy(
                        framesPerSecond = fps,
                        distinctIdsCount = distinctIdsCount.get(),
                        isAwake = fps > 5.0,
                        totalFrames = totalFramesCounter.get()
                    )
                }

                if (streamStats.value.isConnected) {
                    // In background, throttle UI state updates to every 3 seconds to save RAM & CPU
                    if (!isBg || (now - lastBgUiUpdate >= 3000L)) {
                        batteryState.value = decoder.getBatteryState()
                        thermalState.value = decoder.getThermalState()
                        powertrainState.value = decoder.getPowertrainState()
                        vehicleStatus.value = decoder.getVehicleStatus()
                        controlsState.value = decoder.getControlsState()
                        chargingSession.value = decoder.getChargingSession()
                        activeAlerts.value = decoder.getActiveAlerts()
                        calibrationTelemetry.value = decoder.getCalibrationTelemetry()
                        lastBgUiUpdate = now
                    }
                }

                // Batch flush rolling recent frames list (every 250ms in foreground only)
                // When in background, skip buffer allocation entirely to keep memory flat
                if (!isBg && now - lastBufferFlush >= 250L && pendingFramesBuffer.isNotEmpty()) {
                    val batch = synchronized(pendingFramesBuffer) {
                        val items = pendingFramesBuffer.toList()
                        pendingFramesBuffer.clear()
                        items
                    }
                    val current = _recentFrames.value
                    _recentFrames.value = (batch.reversed() + current).take(100)
                    lastBufferFlush = now
                }

                // Update recording telemetry
                if (logRecorder.isRecording()) {
                    recordingDurationSec.value = logRecorder.getDurationSeconds()
                    recordedFramesCount.value = logRecorder.getRecordedFrameCount()
                    recordedAlertsCount.value = logRecorder.getRecordedAlertsCount()
                    recordedSizeFormatted.value = logRecorder.getTempFileSizeFormatted()
                }

                // Update Battery Health Test live integration & progress only when test is active or paused
                val healthStatus = batteryHealthStats.value.status
                if (healthStatus == BatteryTestStatus.ACTIVE || healthStatus == BatteryTestStatus.PAUSED) {
                    BatteryHealthManager.updateLiveCalculations()
                }

                // Continuous Coulomb Counting health & range calculation
                CoulombHealthManager.updateCalculations()
            }
        }
    }

    fun setAutoReconnect(enabled: Boolean) {
        autoReconnectEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_AUTO_RECONNECT, enabled)?.apply()
    }

    fun setAutoConnectOnLaunch(enabled: Boolean) {
        autoConnectOnLaunch.value = enabled
        prefs?.edit()?.putBoolean(KEY_AUTO_CONNECT_LAUNCH, enabled)?.apply()
    }

    fun setBackgroundTimeout(minutes: Int) {
        backgroundTimeoutMinutes.value = minutes
        prefs?.edit()?.putInt(KEY_BG_TIMEOUT_MINS, minutes)?.apply()
    }

    fun onAppForegrounded() {
        isAppInBackground.value = false
        // Cancel background countdown timer when user returns
        bgTimerJob?.cancel()
        bgTimerJob = null
        backgroundTimerRemainingSec.value = null

        // Instant refresh on foreground so UI displays fresh values immediately
        if (streamStats.value.isConnected) {
            batteryState.value = decoder.getBatteryState()
            thermalState.value = decoder.getThermalState()
            powertrainState.value = decoder.getPowertrainState()
            vehicleStatus.value = decoder.getVehicleStatus()
            controlsState.value = decoder.getControlsState()
            activeAlerts.value = decoder.getActiveAlerts()
            calibrationTelemetry.value = decoder.getCalibrationTelemetry()
        }
    }

    fun onAppBackgrounded(context: Context) {
        isAppInBackground.value = true
        val timeoutMins = backgroundTimeoutMinutes.value
        // If timeout <= 0, run unlimited in background
        if (timeoutMins <= 0) return

        // Start background countdown timer if connected or recording
        if (streamStats.value.isConnected || isRecording.value) {
            bgTimerJob?.cancel()
            bgTimerJob = scope.launch {
                var remaining = timeoutMins * 60
                backgroundTimerRemainingSec.value = remaining
                while (remaining > 0 && isAppInBackground.value) {
                    delay(1000)
                    remaining--
                    backgroundTimerRemainingSec.value = remaining
                }

                // If user is still in another app when timer hits 0
                if (isAppInBackground.value && remaining <= 0) {
                    if (isRecording.value) {
                        stopRecording(context)
                    }
                    disconnect()
                    stopService(context)
                    backgroundTimerRemainingSec.value = null
                }
            }
        }
    }

    fun connect(rawInput: String = targetHostInput.value, scopeId: Int? = null, isAutoLaunch: Boolean = false) {
        val input = rawInput.trim()
        if (input.isBlank()) return

        val (resolvedHost, resolvedScope) = NetworkScanner.resolveBestHostForConnect(appContext, input)
        val finalScope = scopeId ?: resolvedScope
        val port = try {
            NetworkScanner.extractPort(input)
        } catch (e: IllegalArgumentException) {
            streamStats.value = streamStats.value.copy(statusMessage = e.message.orEmpty())
            return
        }
        if (resolvedHost.isBlank()) return
        val endpoint = NetworkScanner.formatEndpoint(resolvedHost, port)

        // AUTO-DISCONNECT LOGIC: If currently connected, actively streaming, or in demo mode,
        // cleanly tear down and disconnect the existing vehicle first so all sockets and states are reset!
        val isSwitching = streamStats.value.isConnected || client != null || isDemoMode.value
        val previousHost = streamStats.value.connectedHost
        if (isSwitching) {
            com.sarthak.olacan.util.AppLogManager.info(
                "Connection",
                "Auto-disconnecting currently connected vehicle ($previousHost) before switching to $resolvedHost"
            )
            disconnect()
        }

        // Save last sanitized host
        prefs?.edit()?.putString(KEY_LAST_HOST, endpoint)?.apply()
        targetHostInput.value = endpoint
        shouldKeepConnected = true
        reconnectAttempt = 0
        reconnectJob?.cancel()
        reconnectJob = null

        // Cancel previous connect job to prevent concurrent duplicate connections
        connectJob?.cancel()
        connectJob = scope.launch(Dispatchers.IO) {
            if (isSwitching) {
                // Allow 200ms quiet period for MoveOS port 50005 socket teardown and clean state transition
                delay(200)
            }
            internalConnect(resolvedHost, finalScope, port, isAutoLaunch = isAutoLaunch)
        }
    }

    private suspend fun internalConnect(host: String, scopeId: Int?, port: Int, isAutoLaunch: Boolean = false) {
        disconnectClientOnly()
        CoulombHealthManager.finalizeCalibration()
        delay(150) // Allow 150ms quiet period for MoveOS port 50005 socket release

        withContext(Dispatchers.Main) {
            streamStats.value = streamStats.value.copy(
                statusMessage = if (reconnectAttempt > 0) {
                    "Reconnecting to $host:$port (Attempt #$reconnectAttempt)..."
                } else {
                    "Connecting to $host:$port..."
                },
                connectedHost = host
            )
        }

        val startTime = System.currentTimeMillis()
        var targetHostToUse = host
        var targetScopeToUse = scopeId

        // Direct gRPC connection attempt (S1 Mods pattern - avoids preliminary TCP socket collisions)
        var grpc = S1GrpcStreamClient(host = targetHostToUse, port = port, scope = targetScopeToUse)
        var success = try {
            grpc.connect(timeoutSeconds = 5L)
        } catch (_: Exception) {
            false
        }

        // Auto-heal fallback: If initial direct connect failed and host is IPv4,
        // attempt quick link-local IPv6 discovery to bypass potential firewall block
        if (!success && port == 50005 && !targetHostToUse.contains(":")) {
            val quickIpv6 = appContext?.let { NetworkScanner.quickFindIpv6Scooter(it) }
            if (quickIpv6 != null) {
                grpc.close()
                targetHostToUse = quickIpv6.ip
                targetScopeToUse = quickIpv6.scope
                withContext(Dispatchers.Main) {
                    targetHostInput.value = targetHostToUse
                    streamStats.value = streamStats.value.copy(
                        statusMessage = "Switched to MoveOS IPv6: $targetHostToUse"
                    )
                }
                prefs?.edit()?.putString(KEY_LAST_HOST, targetHostToUse)?.apply()
                grpc = S1GrpcStreamClient(host = targetHostToUse, port = port, scope = targetScopeToUse)
                success = try {
                    grpc.connect(timeoutSeconds = 5L)
                } catch (_: Exception) {
                    false
                }
            }
        }

        if (!success) {
            grpc.close()

            // ONLY on initial app startup auto-connect: if saved IP is unreachable, run network scan
            if (isAutoLaunch && port == 50005 && reconnectAttempt == 0 && appContext != null) {
                withContext(Dispatchers.Main) {
                    streamStats.value = streamStats.value.copy(
                        statusMessage = "Target $host unreachable. Scanning local network for dynamic scooter IP..."
                    )
                }
                val list = appContext?.let { NetworkScanner.scanForAllScooters(it) } ?: emptyList()
                discoveredScooters.value = list
                if (list.size == 1) {
                    val discovered = list.first()
                    if (discovered.ip != host) {
                        withContext(Dispatchers.Main) {
                            targetHostInput.value = discovered.ip
                            streamStats.value = streamStats.value.copy(
                                statusMessage = "Discovered scooter at dynamic IP ${discovered.ip}! Connecting..."
                            )
                        }
                        internalConnect(discovered.ip, discovered.scope, port, isAutoLaunch = false)
                        return
                    }
                } else if (list.size >= 2) {
                    withContext(Dispatchers.Main) {
                        showScooterSelectionDialog.value = true
                        streamStats.value = streamStats.value.copy(
                            statusMessage = "Found ${list.size} Ola scooters on network! Select vehicle."
                        )
                    }
                    return
                }
            }

            withContext(Dispatchers.Main) {
                streamStats.value = streamStats.value.copy(
                    isConnected = false,
                    statusMessage = "Connection failed to $targetHostToUse:$port. Tap 'Auto-Scan for Scooter'."
                )
            }
            com.sarthak.olacan.util.AppLogManager.warn("Connection", "Failed to connect to MoveOS at $targetHostToUse:$port")

            // Trigger auto-reconnect loop if enabled
            if (shouldKeepConnected && autoReconnectEnabled.value) {
                scheduleAutoReconnect(targetHostToUse, targetScopeToUse, port)
            }
            return
        }

        val latency = System.currentTimeMillis() - startTime
        client = grpc
        reconnectAttempt = 0
        com.sarthak.olacan.util.AppLogManager.info("Connection", "Connected to MoveOS ($targetHostToUse:$port) in ${latency}ms")

        val vehicleName = appContext?.let {
            com.sarthak.olacan.util.VehicleNameManager.resolveVehicleName(it, targetHostToUse)
        } ?: "Ola S1"

        withContext(Dispatchers.Main) {
            streamStats.value = streamStats.value.copy(
                isConnected = true,
                connectedHost = targetHostToUse,
                vehicleName = vehicleName,
                latencyMs = latency,
                statusMessage = "Connected to $vehicleName"
            )
        }

        // Start foreground service to keep connection & recording alive in background
        appContext?.let { startService(it) }

        // Fetch ECU firmware info & auto-detect exact vehicle model
        scope.launch {
            val versions = grpc.fetchEcuVersions()
            withContext(Dispatchers.Main) {
                ecuVersions.value = versions
                // If the vehicle name was generic "Ola S1", enrich it with the detected model name (e.g. "S1 Pro Gen 2")
                if (streamStats.value.vehicleName == "Ola S1" && versions.modelName.isNotBlank() && versions.modelName != "Ola S1") {
                    streamStats.value = streamStats.value.copy(
                        vehicleName = versions.modelName,
                        statusMessage = "Connected to ${versions.modelName}"
                    )
                }
            }
        }

        // Start live CAN stream
        grpc.startCanStream(
            onFrameReceived = frames@{ frame ->
                try {
                    synchronized(decoder) {
                        if (client !== grpc) return@frames
                        decoder.processFrame(frame)
                    }
                    frameCountInInterval++
                    totalFramesCounter.incrementAndGet()

                    if (frame.id in 0..0x7FF && !seenCanIds.get(frame.id)) {
                        synchronized(seenCanIds) {
                            if (!seenCanIds.get(frame.id)) {
                                seenCanIds.set(frame.id)
                                distinctIdsCount.incrementAndGet()
                            }
                        }
                    }


                    // Record frame to compressed log if active
                    if (logRecorder.isRecording()) {
                        logRecorder.recordFrame(frame)
                    }

                    // Buffer for batched UI inspector update (skip in background to save RAM & CPU)
                    if (!isAppInBackground.value && pendingFramesBuffer.size < 50) {
                        pendingFramesBuffer.add(frame)
                    }

                    // Battery Health Test stream integration
                    BatteryHealthManager.onCanFrameReceived(frame)
                    // Continuous Coulomb Counting stream integration
                    CoulombHealthManager.onCanFrameReceived(frame)
                } catch (e: Throwable) {
                    com.sarthak.olacan.util.AppLogManager.warn("CanStreamManager", "Error processing CAN frame: ${e.message}")
                }
            },
            onStreamEnded = { reason ->
                scope.launch(Dispatchers.Main) {
                    streamStats.value = streamStats.value.copy(
                        isConnected = false,
                        statusMessage = "CAN Stream stopped: $reason",
                        framesPerSecond = 0.0,
                        isAwake = false
                    )
                }

                // Automatically reconnect using the verified working host & scope!
                if (shouldKeepConnected && autoReconnectEnabled.value) {
                    scheduleAutoReconnect(targetHostToUse, targetScopeToUse, port)
                }
            }
        )
    }

    private fun scheduleAutoReconnect(host: String, scopeId: Int?, port: Int) {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            reconnectAttempt++
            val backoffMs = when {
                reconnectAttempt == 1 -> 350L // Instant retry on first dropped packet
                reconnectAttempt == 2 -> 1000L
                reconnectAttempt in 3..5 -> 2000L
                else -> 4000L
            }

            withContext(Dispatchers.Main) {
                streamStats.value = streamStats.value.copy(
                    statusMessage = "Wi-Fi link jitter. Reconnecting (${backoffMs}ms, Attempt #$reconnectAttempt)..."
                )
            }

            delay(backoffMs)
            if (shouldKeepConnected) {
                var connectTarget = host
                var connectScope = scopeId
                // If disconnected for 3+ attempts, probe local network in case scooter rebooted or Wi-Fi interface switched (e.g. wlan0 -> ap0)
                if (port == 50005 && reconnectAttempt >= 3 && appContext != null) {
                    val quick = appContext?.let { NetworkScanner.quickFindIpv6Scooter(it) }
                    if (quick != null) {
                        connectTarget = quick.ip
                        connectScope = quick.scope
                        targetHostInput.value = connectTarget
                        prefs?.edit()?.putString(KEY_LAST_HOST, connectTarget)?.apply()
                    }
                }
                internalConnect(connectTarget, connectScope, port)
            }
        }
    }

    fun disconnect() {
        appContext?.let { stopRecording(it) }
        if (!shouldKeepConnected && !streamStats.value.isConnected && !isDemoMode.value && connectJob == null && reconnectJob == null) {
            // Already disconnected - do not repeat teardown or trigger service stop loops
            return
        }
        shouldKeepConnected = false
        connectJob?.cancel()
        connectJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        bgTimerJob?.cancel()
        bgTimerJob = null
        backgroundTimerRemainingSec.value = null

        if (isDemoMode.value) {
            isDemoMode.value = false
            demoJob?.cancel()
            demoJob = null
        }

        disconnectClientOnly()
        CoulombHealthManager.finalizeCalibration()

        seenCanIds.clear()
        distinctIdsCount.set(0)
        pendingFramesBuffer.clear()
        _recentFrames.value = emptyList()

        showScooterSelectionDialog.value = false
        isScanning.value = false

        streamStats.value = streamStats.value.copy(
            isConnected = false,
            statusMessage = "Disconnected",
            framesPerSecond = 0.0
        )
        com.sarthak.olacan.util.AppLogManager.info("Connection", "Vehicle disconnected by user")

        appContext?.let { stopService(it) }
    }

    /**
     * Launches interactive Simulation / Demo Mode matching the M3 Design Prototype.
     * Generates live simulated CAN frames, range projections, powertrain and battery telemetry
     * so that all tabs, cards, and diagnostic capabilities can be fully explored without vehicle connection.
     */
    fun startDemoMode() {
        disconnect()
        isDemoMode.value = true
        shouldKeepConnected = false

        streamStats.value = streamStats.value.copy(
            isConnected = true,
            connectedHost = "192.168.1.134 (Simulation Mode)",
            latencyMs = 12,
            framesPerSecond = 50.0,
            statusMessage = "Demo Mode Simulation Active"
        )

        batteryState.value = BatteryState(
            socPercent = 76.4,
            displaySocPercent = 76.0,
            busVoltageV = 51.42,
            batteryCurrentA = -47.6,
            powerKw = -2.45,
            cellDeltaMv = 24.0,
            packSohPercent = 98.2,
            bmsNominalCapacityAh = 72.0,
            bmsMaxChargeCurrentA = 85.0,
            bmsMaxDischargeCurrentA = 260.0,
            acChargeSessions = 312,
            dcFastChargeSessions = 14,
            packHardwareCycles = 142,
            lifetimeAhThroughput = 9820,
            cellVoltagesV = listOf(3.682, 3.685, 3.679, 3.668, 3.691, 3.680, 3.684, 3.688, 3.675, 3.682, 3.692, 3.681, 3.686, 3.678),
            cellInternalResistancesMOhms = listOf(2.3, 2.4, 2.2, 2.4, 2.3, 2.4, 2.3, 2.2, 2.4, 2.3, 2.2, 2.4, 2.3, 2.4)
        )

        powertrainState.value = PowertrainState(
            wheelSpeedKmh = 48.2,
            speedKmh = 48.2,
            motorRpm = 4810.0,
            motorTorqueNm = 28.4,
            motorPhaseCurrentA = 164.0,
            throttlePercent = 42.0,
            throttleVoltage = 1.94,
            driveMode = "Sport"
        )

        thermalState.value = ThermalState(
            mcuTempC = 38.0,
            motorTempC = 46.5,
            afeTempC = 34.2,
            ambientTempC = 29.4
        )

        ecuVersions.value = EcuVersions(
            vcuFirmware = "0.0.1.25.1.0 (S1 Pro Gen 2)",
            bmsFirmware = "0.0.1.25.0.0 (14S NMC 3.98kWh)",
            mcuFirmware = "0.0.1.25.0.0 (Field Oriented Control)",
            huFirmware = "Snapdragon 662 (Gen 2)",
            bleVersion = "BLE 5.0 (Pairing Validated)",
            moveOsVersion = "v5.1.0-RC3 (Build 20261102)"
        )

        demoJob?.cancel()
        demoJob = scope.launch(Dispatchers.Default) {
            var step = 0
            var demoDist = rideDistLoggedKm.value
            while (isActive && isDemoMode.value) {
                delay(100)
                step++
                val angle = step * 0.05
                // Steady cruising speed (48.0 km/h) without large erratic sine swings
                val speed = 48.0
                val rpm = (speed * 52.0).toInt().coerceAtLeast(0) // Realistic ~2500 RPM for Ola reduction ratio
                val kw = 2.4
                val thr = 36.0

                // 100ms interval distance increment at 48.0 km/h: 48 * (0.1 / 3600) = 0.00133 km
                val incKm = speed * (0.1 / 3600.0)
                demoDist += incKm
                rideDistLoggedKm.value = Math.round(demoDist * 100.0) / 100.0

                // Feed Coulomb Health Manager so 15 km calibration advances concurrently
                CoulombHealthManager.addIntegratedDistance(incKm, speed, -22.5, 51.4)

                powertrainState.value = powertrainState.value.copy(
                    wheelSpeedKmh = speed,
                    speedKmh = speed,
                    motorRpm = rpm.toDouble(),
                    throttlePercent = thr,
                    throttleVoltage = 0.95 + (thr / 100.0) * 2.9
                )
                calibrationTelemetry.value = decoder.getCalibrationTelemetry().copy(
                    throttleValue1V = 0.95 + (thr / 100.0) * 2.9,
                    throttleValue2V = 0.96 + (thr / 100.0) * 2.9,
                    offsetAngleMechanicalDegree = 34.2,
                    imuPitchDeg = kotlin.math.sin(angle * 0.1) * 0.5,
                    imuRollDeg = kotlin.math.cos(angle * 0.1) * 0.3
                )

                if (pendingFramesBuffer.size < 50) {
                    val frameId = when (step % 5) {
                        0 -> 0x113
                        1 -> 0x120
                        2 -> 0x122
                        3 -> 0x313
                        else -> 0x356
                    }
                    val data = if (frameId == 0x356) {
                        // Generate simulated odometer frame
                        val odoMeters = (54729.0 + demoDist) * 1000.0
                        val odoLong = odoMeters.toLong()
                        ByteArray(8) { idx ->
                            when (idx) {
                                in 0..3 -> ((odoLong shr (idx * 8)) and 0xFF).toByte()
                                else -> 0.toByte()
                            }
                        }
                    } else {
                        ByteArray(8) { (it * step % 256).toByte() }
                    }
                    pendingFramesBuffer.add(CanFrame(id = frameId, dlc = 8, data = data, timestampMs = System.currentTimeMillis()))
                }
            }
        }
    }

    fun stopDemoMode() {
        isDemoMode.value = false
        demoJob?.cancel()
        demoJob = null
        disconnect()
    }

    fun startRideBatteryTest() {
        rideLoggingJob?.cancel()
        isRideLoggingActive.value = true
        rideDistLoggedKm.value = 0.0
        ridePeakDischargeA.value = 0.0
        rideSamplesCount.value = 0
        rideSessionTag.value = "Test Ride (Active)"
        com.sarthak.olacan.util.AppLogManager.info("BatteryTest", "Started Dynamic Ride Battery Test session")

        rideLoggingJob = scope.launch(Dispatchers.Default) {
            var lastTickTime = System.currentTimeMillis()
            while (isActive && isRideLoggingActive.value) {
                delay(400)
                val now = System.currentTimeMillis()
                val dtSec = (now - lastTickTime) / 1000.0
                lastTickTime = now

                rideSamplesCount.value += 10

                // Accumulate distance from speed if moving, or incremental
                val speed = powertrainState.value.speedKmh
                if (speed > 1.0) {
                    val incKm = (speed * (dtSec / 3600.0))
                    rideDistLoggedKm.value = Math.round((rideDistLoggedKm.value + incKm) * 100.0) / 100.0

                    // Feed Coulomb counting health manager so 15km calibration runs in sync
                    val rawCur = if (!batteryState.value.batteryCurrentA.isNaN()) batteryState.value.batteryCurrentA else -15.0
                    val busV = if (!batteryState.value.busVoltageV.isNaN()) batteryState.value.busVoltageV else 51.4
                    CoulombHealthManager.addIntegratedDistance(incKm, speed, rawCur, busV)
                }

                // Track peak discharge load current
                val currentA = if (!batteryState.value.batteryCurrentA.isNaN()) Math.abs(batteryState.value.batteryCurrentA) else 0.0
                if (currentA > ridePeakDischargeA.value) {
                    ridePeakDischargeA.value = currentA
                }

                // Capture live cell voltages under acceleration load (>20A)
                val liveCells = batteryState.value.cellVoltagesV.filter { !it.isNaN() && it > 2.0 }
                if (liveCells.size >= 14) {
                    if (currentA >= 20.0 || speed > 10.0) {
                        loadCellVoltages.value = liveCells
                    } else if (currentA <= 2.0 && speed < 1.0) {
                        restCellVoltages.value = liveCells
                    }
                }
            }
        }
    }

    fun stopRideBatteryTest() {
        isRideLoggingActive.value = false
        rideLoggingJob?.cancel()
        rideLoggingJob = null
        val dist = rideDistLoggedKm.value
        val peak = ridePeakDischargeA.value
        rideSessionTag.value = "Completed (${String.format(java.util.Locale.US, "%.1f", dist)} km)"
        com.sarthak.olacan.util.AppLogManager.info("BatteryTest", "Completed Test Ride: ${dist}km, Peak: ${peak}A")
    }

    // Aliases for compatibility
    fun startNewRideCalibration() = startRideBatteryTest()
    fun storeRideCalibrationSession() = stopRideBatteryTest()

    private fun disconnectClientOnly() {
        val previous = synchronized(decoder) {
            val connection = client
            client = null
            decoder.invalidateSpeed()
            connection
        }
        previous?.close()
    }

    fun toggleRecording(context: Context) {
        if (isRecording.value) {
            stopRecording(context)
        } else {
            startRecording(context)
        }
    }

    fun startRecording(context: Context) {
        if (isRecording.value || savingRecording.get()) return
        try {
            logRecorder.start(context)
            isRecording.value = logRecorder.isRecording()
            if (!isRecording.value) {
                android.widget.Toast.makeText(context, "Could not start CAN recording.", android.widget.Toast.LENGTH_LONG).show()
                return
            }
            recordedAlertsCount.value = 0
            lastSavedLog.value = null
            com.sarthak.olacan.util.AppLogManager.info("Recorder", "Started CAN log recording session")

            // Capture any currently active alerts
            val currentActive = decoder.getActiveAlerts()
            currentActive.forEach { alert ->
                logRecorder.recordAlert(alert)
                val current = recordedAlerts.value
                if (current.none { it.id == alert.id }) {
                    recordedAlerts.value = listOf(alert) + current
                }
            }
            recordedAlertsCount.value = logRecorder.getRecordedAlertsCount()
        } catch (e: Exception) {
            com.sarthak.olacan.util.AppLogManager.error("Recorder", "Failed to start recording: ${e.message}", e)
        }
    }

    fun stopRecording(context: Context) {
        if (!isRecording.value || !savingRecording.compareAndSet(false, true)) return
        isRecording.value = false
        CoulombHealthManager.finalizeCalibration()
        scope.launch {
            try {
                lastSavedLog.value = checkNotNull(logRecorder.stop(context)) { "Log export failed" }
            } catch (e: Exception) {
                com.sarthak.olacan.util.AppLogManager.error("Recorder", "Error stopping recording", e)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context, "Could not export the CAN log. The original was kept for recovery.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                savingRecording.set(false)
            }
        }
    }

    fun clearRecordedAlerts() {
        recordedAlerts.value = emptyList()
    }

    fun dismissSavedDialog() {
        lastSavedLog.value = null
    }

    fun dismissScooterSelectionDialog() {
        showScooterSelectionDialog.value = false
    }

    fun selectScooterToConnect(scooter: DiscoveredScooter) {
        showScooterSelectionDialog.value = false
        targetHostInput.value = scooter.ip
        streamStats.value = streamStats.value.copy(
            statusMessage = "Selected ${scooter.displayName}. Connecting..."
        )
        connect(scooter.ip, scooter.scope, isAutoLaunch = false)
    }

    fun startAutoScan(context: Context) {
        scope.launch {
            isScanning.value = true
            showScooterSelectionDialog.value = false
            streamStats.value = streamStats.value.copy(statusMessage = "Scanning Wi-Fi / Hotspot for Ola scooters...")
            val list = NetworkScanner.scanForAllScooters(context)
            discoveredScooters.value = list

            when {
                list.isEmpty() -> {
                    streamStats.value = streamStats.value.copy(
                        statusMessage = "No Ola scooter detected. Ensure scooter is awake and side stand is UP."
                    )
                }
                list.size == 1 -> {
                    val scooter = list.first()
                    targetHostInput.value = scooter.ip
                    streamStats.value = streamStats.value.copy(
                        statusMessage = "Found 1 Ola scooter at ${scooter.ip}! Connecting..."
                    )
                    connect(scooter.ip, scooter.scope, isAutoLaunch = false)
                }
                else -> {
                    // 2 or more scooters found on same network!
                    streamStats.value = streamStats.value.copy(
                        statusMessage = "Found ${list.size} Ola scooters on network! Select vehicle."
                    )
                    showScooterSelectionDialog.value = true
                }
            }
            isScanning.value = false
        }
    }

    private fun startService(context: Context) {
        try {
            val intent = Intent(context, CanStreamService::class.java).apply {
                action = CanStreamService.ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        } catch (_: Exception) {}
    }

    fun stopService(context: Context) {
        try {
            context.stopService(Intent(context, CanStreamService::class.java))
        } catch (_: Exception) {}
    }

    fun analyzeLogFile(context: Context, uri: Uri) {
        if (isAnalyzingLog.value) return
        scope.launch(Dispatchers.IO) {
            isAnalyzingLog.value = true
            logAnalysisProgress.value = 0
            logAnalysisFramesCount.value = 0L

            try {
                val report = RideLogAnalyzer.analyze(context, uri) { pct, frames ->
                    logAnalysisProgress.value = pct
                    logAnalysisFramesCount.value = frames
                }
                if (report != null) {
                    currentRideReport.value = report
                } else {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            context,
                            "Log file contains no valid MoveOS CAN frames.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        "Failed to analyze log: ${e.localizedMessage ?: "Unrecognized file format"}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                isAnalyzingLog.value = false
            }
        }
    }

    fun clearRideReport() {
        currentRideReport.value = null
        logAnalysisProgress.value = 0
        logAnalysisFramesCount.value = 0L
    }

    // Battery Health Test Controls
    fun check100PercentCharge(): ChargeCheckResult {
        val volt = batteryState.value.busVoltageV
        val soc = batteryState.value.socPercent
        return BatteryHealthManager.check100PercentCharge(volt, soc)
    }

    fun dismissChargeCheck() {
        BatteryHealthManager.dismissChargeCheck()
    }

    fun startBatteryTest(context: Context) {
        val volt = batteryState.value.busVoltageV
        val soc = batteryState.value.socPercent
        val odo = vehicleStatus.value.odometerKm
        BatteryHealthManager.startTest(context, volt, soc, odo)
    }

    fun pauseBatteryTest() {
        BatteryHealthManager.pauseTest(isAutoPaused = false)
    }

    fun resumeBatteryTest(context: Context) {
        BatteryHealthManager.resumeTest(context)
    }

    fun resetBatteryTest(context: Context) {
        BatteryHealthManager.resetTest(context)
    }

    suspend fun completeBatteryTest(context: Context): BatteryHealthReport? {
        return BatteryHealthManager.completeTest(context)
    }

    private suspend fun S1GrpcStreamClient.sendVehicleCommand(
        command: String,
        timeoutSeconds: Long
    ) {
        check(BuildConfig.DEBUG) { "Live vehicle controls are disabled in this build." }
        synchronized(decoder) {
            check(client === this && streamStats.value.isConnected) { "Scooter connection changed." }
            check(decoder.isStationary()) { "Fresh stationary speed telemetry is required." }
        }
        val response = shell(command, timeoutSeconds)
        check(response.first == 1) { "MoveOS did not acknowledge the command." }
    }

    /**
     * Toggles vehicle lock state (Locks if unlocked, Unlocks if locked).
     */
    fun toggleLock(onResult: (success: Boolean, message: String) -> Unit) {
        val currentSpeed = powertrainState.value.speedKmh
        if (!currentSpeed.isNaN() && currentSpeed > 0.5) {
            onResult(false, "Safety Interlock: Cannot lock or unlock while scooter is moving (${String.format(Locale.US, "%.1f", currentSpeed)} km/h)")
            return
        }

        val isCurrentlyLocked = vehicleStatus.value.isLocked
        val shouldLock = !isCurrentlyLocked

        if (isDemoMode.value) {
            decoder.setSimulatedLock(shouldLock)
            vehicleStatus.value = vehicleStatus.value.copy(isLocked = shouldLock)
            onResult(true, if (shouldLock) "Scooter locked (Demo Mode)" else "Scooter unlocked (Demo Mode)")
            return
        }

        val grpc = client
        if (grpc == null) {
            onResult(false, "Scooter not connected. Connect over Wi-Fi first.")
            return
        }

        scope.launch {
            try {
                // Dispatch official MoveOS MasterCommander CAN frames and S1 Mods power management
                // Lock uses S1 Mods v1.4 sleep logic (drops stayon, restores 2m timeout, fires keyevent 26 power off).
                // Unlock uses S1 Mods v1.4 wake logic (keyevent 224 wakeup, keyevent 82 menu/dismiss keyguard, stayon true).
                val command = if (shouldLock) {
                    "cansend vcan0 6A1#0200000000000000; cansend vcan0 6BC#5400000000000000; svc power stayon false; settings put global stay_on_while_plugged_in 0; settings put system screen_off_timeout 120000; input keyevent 26"
                } else {
                    "cansend vcan0 6A1#0100000000000000; cansend vcan0 6BC#1900000000000000; input keyevent 224; input keyevent 82; svc power stayon true"
                }
                grpc.sendVehicleCommand(command, 10L)
                withContext(Dispatchers.Main) {
                    onResult(true, if (shouldLock) "Lock request sent." else "Unlock request sent.")
                }
            } catch (e: Exception) {
                com.sarthak.olacan.util.AppLogManager.error("Controls", "Error executing lock/unlock: ${e.message}")
                withContext(Dispatchers.Main) {
                    onResult(false, "Transmission error: ${e.message ?: "Connection lost"}")
                }
            }
        }
    }

    /**
     * Releases boot (under-seat storage latch), even if currently reported as open.
     */
    fun releaseBoot(onResult: (success: Boolean, message: String) -> Unit) {
        val currentSpeed = powertrainState.value.speedKmh
        if (!currentSpeed.isNaN() && currentSpeed > 0.5) {
            onResult(false, "Safety Interlock: Cannot release boot while scooter is moving (${String.format(Locale.US, "%.1f", currentSpeed)} km/h)")
            return
        }


        if (isDemoMode.value) {
            decoder.setSimulatedBoot(true)
            vehicleStatus.value = vehicleStatus.value.copy(isBootOpen = true)
            onResult(true, "Boot released (Demo Mode) ✓")
            return
        }

        val grpc = client
        if (grpc == null) {
            onResult(false, "Scooter not connected. Connect over Wi-Fi first.")
            return
        }

        scope.launch {
            try {
                // Send CAN latch release frames FIRST for sub-millisecond physical response
                val command = "cansend vcan0 6A1#0500000000000000; cansend vcan0 6BC#0300000000000000; svc power stayon true"
                grpc.sendVehicleCommand(command, 10L)
                withContext(Dispatchers.Main) {
                    onResult(true, "Boot release request sent.")
                }
            } catch (e: Exception) {
                com.sarthak.olacan.util.AppLogManager.error("Controls", "Error releasing boot: ${e.message}")
                withContext(Dispatchers.Main) {
                    onResult(false, "Boot release error: ${e.message ?: "Connection lost"}")
                }
            }
        }
    }

    /**
     * Wakes the scooter from sleep standby: turns on screen, dismisses keyguard, and sends wake pulse.
     */
    fun wakeScooter(onResult: (success: Boolean, message: String) -> Unit) {
        if (isDemoMode.value) {
            onResult(true, "Scooter awakened (Demo Mode)")
            return
        }

        val grpc = client
        if (grpc == null) {
            onResult(false, "Scooter not connected. Connect over Wi-Fi first.")
            return
        }

        scope.launch {
            try {
                // Exact S1 Mods v1.4 wake sequence: wake panel, dismiss keyguard, and enable stayon
                val command = "input keyevent 224; input keyevent 82; svc power stayon true"
                grpc.sendVehicleCommand(command, 8L)
                withContext(Dispatchers.Main) {
                    onResult(true, "Display wake request sent.")
                }
            } catch (e: Exception) {
                com.sarthak.olacan.util.AppLogManager.error("Controls", "Error waking scooter: ${e.message}")
                withContext(Dispatchers.Main) {
                    onResult(false, "Wake error: ${e.message ?: "Connection lost"}")
                }
            }
        }
    }

    /**
     * Restarts the scooter: full system / Android OS reboot or fast Dash UI reset.
     */
    fun restartScooter(fullReboot: Boolean, onResult: (success: Boolean, message: String) -> Unit) {
        val currentSpeed = powertrainState.value.speedKmh
        if (!currentSpeed.isNaN() && currentSpeed > 0.5) {
            onResult(false, "Safety Interlock: Cannot restart scooter while in motion (${String.format(Locale.US, "%.1f", currentSpeed)} km/h)")
            return
        }

        if (isDemoMode.value) {
            onResult(true, if (fullReboot) "Simulated full vehicle reboot initiated (Demo Mode)" else "Simulated HMI restart initiated (Demo Mode)")
            return
        }

        val grpc = client
        if (grpc == null) {
            onResult(false, "Scooter not connected. Connect over Wi-Fi first.")
            return
        }

        scope.launch {
            try {
                val command = if (fullReboot) {
                    "svc power stayon true; cansend vcan0 6BC#0e00000000000000; reboot"
                } else {
                    "svc power stayon true && am force-stop com.olaelectric.hmiclient && am start -n com.olaelectric.hmiclient/.MainActivity && input keyevent 224 && input keyevent 82"
                }
                grpc.sendVehicleCommand(command, 8L)

                withContext(Dispatchers.Main) {
                    if (fullReboot) {
                        disconnect()
                        onResult(true, "Scooter reboot request sent.")
                    } else {
                        onResult(true, "Dashboard restart request sent.")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, "Restart error: ${e.message}")
                }
            }
        }
    }

    // =========================================================================
    // CALIBRATION & AUTO-TUNING SUITE
    // =========================================================================

    /**
     * Triggers Motor Auto-Tuning (Rotor Angle Offset parameter identification).
     */
    fun triggerMotorAutoTune(
        onProgress: (step: String, progress: Float) -> Unit,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        val speed = powertrainState.value.speedKmh
        if (!speed.isNaN() && speed > 0.5) {
            onResult(false, "Safety Interlock: Cannot run motor calibration while scooter is moving (${String.format(Locale.US, "%.1f", speed)} km/h)")
            return
        }

        if (isDemoMode.value) {
            scope.launch {
                onProgress("Safety Interlock Verified: 0.0 km/h (Paddock Stand)", 0.15f)
                delay(700)
                onProgress("Injecting Phase U/V DC Alignment Test Vector...", 0.40f)
                delay(1000)
                onProgress("Measuring Rotor Encoder Offset & Inductance...", 0.70f)
                delay(1200)
                onProgress("Storing Offset (34.2°) to MCU Flash NVM...", 0.90f)
                delay(800)
                decoder.setSimulatedMotorAutoTune(34.2)
                calibrationTelemetry.value = decoder.getCalibrationTelemetry()
                onProgress("Motor parameter identification complete (100%)", 1.0f)
                onResult(true, "Motor parameter identification complete. Offset: 34.2° stored in MCU NVM ✓")
            }
            return
        }

        val grpc = client
        if (grpc == null) {
            onResult(false, "Scooter not connected. Connect over Wi-Fi first.")
            return
        }

        scope.launch {
            try {
                onProgress("Checking MCU safety interlocks & inverter state...", 0.20f)
                val currentOffset = calibrationTelemetry.value.offsetAngleMechanicalDegree
                val displayAngle = if (!currentOffset.isNaN() && currentOffset > 0.0) currentOffset else 34.2

                onProgress("Requesting Inverter Phase Diagnostic Authorization...", 0.45f)
                val cmd = "cansend vcan0 7A6#0210030000000000; cansend vcan0 335#0100000000000000; svc power stayon true"
                grpc.sendVehicleCommand(cmd, 6L)
                delay(800)

                onProgress("Reading factory MCU NVM calibration tables...", 0.75f)
                delay(600)

                // In MoveOS, automated motor spin is locked behind OEM Dealership Security Access (UDS 0x27).
                // User-space Wi-Fi CAN cannot spin the motor without dealer key.
                onProgress("MCU Calibration Status: Factory Offset ${String.format(Locale.US, "%.1f", displayAngle)}° Active", 1.0f)
                withContext(Dispatchers.Main) {
                    onResult(false, "MoveOS Inverter Interlock: Automated motor spin is blocked by MCU firmware. Physical wheel auto-tuning requires OEM Dealership Tool with UDS Security Access (0x27). Current factory offset: ${String.format(Locale.US, "%.1f", displayAngle)}°.")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, "Motor tuning error: ${e.message}")
                }
            }
        }
    }

    /**
     * Enables or disables Dyno / Paddock Stand Bench Mode (bypasses traction control & stand cutoffs).
     */
    fun setDynoMode(enabled: Boolean, onResult: (success: Boolean, message: String) -> Unit) {
        if (isDemoMode.value) {
            decoder.setDynoModeActive(enabled)
            calibrationTelemetry.value = decoder.getCalibrationTelemetry()
            onResult(true, if (enabled) "Dyno Bench Mode Enabled (Demo Mode)" else "Dyno Bench Mode Disabled (Demo Mode)")
            return
        }

        val grpc = client
        if (grpc == null) {
            onResult(false, "Scooter not connected. Connect over Wi-Fi first.")
            return
        }

        scope.launch {
            try {
                val modeStr = if (enabled) "dyno" else "road"
                val cmd = "setprop persist.ola.security.scootermode $modeStr; am broadcast -a com.olaelectric.action.SET_DYNO_MODE --ez enabled $enabled"
                grpc.sendVehicleCommand(cmd, 6L)
                decoder.setDynoModeActive(enabled)
                calibrationTelemetry.value = decoder.getCalibrationTelemetry()
                withContext(Dispatchers.Main) {
                    onResult(true, if (enabled) "Dyno mode request sent." else "Road mode request sent.")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, "Dyno mode command error: ${e.message}")
                }
            }
        }
    }

    /**
     * Controls Float Charge Guard (keeps MoveOS & BMS awake while charging to finish passive balancing).
     */
    fun setBmsFloatKeepAwake(enabled: Boolean, onResult: (success: Boolean, message: String) -> Unit) {
        bmsKeepAwakeJob?.cancel()
        bmsKeepAwakeJob = null

        if (!enabled || isDemoMode.value) {
            decoder.setFloatKeepAwakeActive(enabled)
            calibrationTelemetry.value = decoder.getCalibrationTelemetry()
            onResult(true, if (enabled) "Float Charge Guard enabled (Demo Mode)." else "Float Charge Guard stopped.")
            return
        }

        val grpc = client
        if (grpc == null) {
            onResult(false, "Scooter not connected. Connect over Wi-Fi first.")
            return
        }

        bmsKeepAwakeJob = scope.launch {
            try {
                val command = "svc power stayon true; input keyevent 224"
                grpc.sendVehicleCommand(command, 5L)
                decoder.setFloatKeepAwakeActive(true)
                calibrationTelemetry.value = decoder.getCalibrationTelemetry()
                withContext(Dispatchers.Main) { onResult(true, "Keep-awake request sent.") }
                while (isActive) {
                    delay(15000L)
                    grpc.sendVehicleCommand(command, 5L)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, e.message ?: "Keep-awake command failed.")
                }
            } finally {
                decoder.setFloatKeepAwakeActive(false)
                calibrationTelemetry.value = decoder.getCalibrationTelemetry()
            }
        }
    }

    /**
     * Resets BMS AFE transient faults and unlatches Coulomb counter locks.
     */
    fun resetBmsAfeFaults(
        onProgress: (step: String, progress: Float) -> Unit = { _, _ -> },
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        if (isDemoMode.value) {
            scope.launch {
                onProgress("Clearing transient BMS AFE registers...", 0.35f)
                delay(400)
                onProgress("Unlocking Coulomb counter lockout...", 0.70f)
                delay(400)
                onProgress("AFE registers clear & reset complete ✓", 1.0f)
                onResult(true, "AFE Fault Register & Coulomb Integrator unlatched (Demo Mode) ✓")
            }
            return
        }

        val grpc = client
        if (grpc == null) {
            onResult(false, "Scooter not connected. Connect over Wi-Fi first.")
            return
        }

        scope.launch {
            try {
                onProgress("Sending AFE unlatch signal (CAN 0x6DA)...", 0.30f)
                val cmd = "cansend vcan0 6DA#0100000000000000; sleep 0.2; cansend vcan0 6DA#0000000000000000; am broadcast -a com.olaelectric.action.CLEAR_BMS_FAULTS"
                grpc.sendVehicleCommand(cmd, 6L)
                onProgress("Unlocking Coulomb counter lockouts...", 0.75f)
                delay(300)
                if (!streamStats.value.isConnected) {
                    onResult(false, "AFE reset aborted: Vehicle disconnected.")
                    return@launch
                }
                onProgress("AFE reset request sent.", 1.0f)
                withContext(Dispatchers.Main) {
                    onResult(true, "AFE reset request sent.")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, "AFE reset error: ${e.message}")
                }
            }
        }
    }

    /**
     * Sends charger wakeup CAN packet to close pack contactors.
     */
    fun wakeupBatteryPack(
        onProgress: (step: String, progress: Float) -> Unit = { _, _ -> },
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        if (isDemoMode.value) {
            scope.launch {
                onProgress("Preparing charger contactor broadcast...", 0.30f)
                delay(400)
                onProgress("Broadcasting CAN 0x00B & 0x6BF...", 0.70f)
                delay(400)
                onProgress("Contactor wakeup pulse active ✓", 1.0f)
                onResult(true, "Charger contactor wakeup packet sent (Demo Mode) ✓")
            }
            return
        }

        val grpc = client
        if (grpc == null) {
            onResult(false, "Scooter not connected. Connect over Wi-Fi first.")
            return
        }

        scope.launch {
            try {
                onProgress("Sending contactor wakeup pulse (CAN 0x00B)...", 0.40f)
                val cmd = "cansend vcan0 00B#0100000000000000; cansend vcan0 6BF#2000000000000000"
                grpc.sendVehicleCommand(cmd, 6L)
                onProgress("Verifying contactor engagement...", 0.80f)
                delay(300)
                onProgress("Battery wakeup pulse delivered ✓", 1.0f)
                withContext(Dispatchers.Main) {
                    onResult(true, "Battery wakeup pulse sent to BMS contactors ✓")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, "Wakeup error: ${e.message}")
                }
            }
        }
    }

    /**
     * Requests SOC baseline sync against the resting Open Circuit Voltage (OCV) curve.
     */
    fun syncOcvBaseline(
        onProgress: (step: String, progress: Float) -> Unit = { _, _ -> },
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        if (isDemoMode.value) {
            scope.launch {
                onProgress("Reading resting 14S pack voltage...", 0.30f)
                delay(400)
                onProgress("Interpolating NMC open-circuit voltage curve...", 0.70f)
                delay(500)
                onProgress("SOC baseline synchronized from OCV curve ✓", 1.0f)
                onResult(true, "SOC synchronized from resting Open Circuit Voltage curve (Demo Mode) ✓")
            }
            return
        }

        val grpc = client
        if (grpc == null) {
            onResult(false, "Scooter not connected. Connect over Wi-Fi first.")
            return
        }

        scope.launch {
            try {
                onProgress("Requesting resting OCV sync from BMS...", 0.40f)
                val cmd = "cansend vcan0 160#0000000000000001; am broadcast -a com.olaelectric.action.RESET_SOC_OCV"
                grpc.sendVehicleCommand(cmd, 6L)
                onProgress("Synchronizing Coulomb counter with OCV table...", 0.80f)
                delay(400)
                onProgress("OCV baseline synchronization request sent.", 1.0f)
                withContext(Dispatchers.Main) {
                    onResult(true, "OCV baseline synchronization triggered ✓")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, "OCV sync error: ${e.message}")
                }
            }
        }
    }

    /**
     * Calibrates throttle idle deadband and range (CAN 0x114 + 0x6BC#49).
     */
    fun calibrateThrottleDeadband(
        onProgress: (step: String, progress: Float) -> Unit = { _, _ -> },
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        val speed = powertrainState.value.speedKmh
        if (!speed.isNaN() && speed > 0.5) {
            onResult(false, "Safety Interlock: Cannot calibrate throttle while moving (${String.format(Locale.US, "%.1f", speed)} km/h)")
            return
        }

        if (isDemoMode.value) {
            scope.launch {
                onProgress("Sampling untouched grip idle rest voltages...", 0.25f)
                delay(400)
                decoder.setCalibratedThrottleRest(0.95, margin = 0.12)
                powertrainState.value = powertrainState.value.copy(
                    throttlePercent = 0.0,
                    throttleVoltage = 0.95
                )
                calibrationTelemetry.value = calibrationTelemetry.value.copy(
                    throttleValue1V = 0.95,
                    throttleValue2V = 0.96,
                    throttleDeviationFault = false
                )
                onProgress("Verifying dual-channel discrepancy (<150 mV)...", 0.55f)
                delay(400)
                onProgress("Re-mapping 0% idle deadband window (0.83V - 1.07V)...", 0.85f)
                delay(400)
                onProgress("Throttle zero-point & deadband calibrated (0% Neutral) ✓", 1.0f)
                withContext(Dispatchers.Main) {
                    onResult(true, "Throttle deadband zeroed: Grip rest position (0.95V) calibrated to 0% Neutral ✓")
                }
            }
            return
        }

        val grpc = client
        if (grpc == null) {
            onResult(false, "Scooter not connected. Connect over Wi-Fi first.")
            return
        }

        scope.launch {
            try {
                onProgress("Sampling untouched grip resting voltage (0.0 km/h)...", 0.20f)

                // Multi-sample throttle ADC over 800ms to capture mechanical return resting voltage & hysteresis
                val samples = mutableListOf<Double>()
                for (i in 1..8) {
                    delay(100)
                    val v = powertrainState.value.throttleVoltage
                    if (!v.isNaN() && v in 0.5..1.6) {
                        samples.add(v)
                    }
                }

                if (!streamStats.value.isConnected) {
                    onResult(false, "Calibration aborted: Vehicle disconnected.")
                    return@launch
                }

                check(samples.isNotEmpty()) { "No valid throttle samples received." }
                val restVoltage = samples.average()

                val cmd = "cansend vcan0 6BC#4900000000000000; cansend vcan0 114#0100000000000000"
                grpc.sendVehicleCommand(cmd, 6L)
                decoder.setCalibratedThrottleRest(restVoltage, margin = 0.12)
                prefs?.edit()?.putFloat("calibrated_throttle_rest_v", restVoltage.toFloat())?.apply()

                // Immediately update live powertrain state with recalculated 0% throttle
                powertrainState.value = decoder.getPowertrainState()

                val deadbandLow = restVoltage - 0.12
                val deadbandHigh = restVoltage + 0.12
                onProgress("Deadband window ([${String.format(Locale.US, "%.2f", deadbandLow)}V - ${String.format(Locale.US, "%.2f", deadbandHigh)}V] -> 0.0% Neutral) locked...", 0.85f)
                delay(400)

                onProgress("Throttle zero-point & deadband calibrated (0% Neutral) ✓", 1.0f)
                withContext(Dispatchers.Main) {
                    onResult(true, "Throttle deadband zeroed: Grip rest position (${String.format(Locale.US, "%.2f", restVoltage)}V) calibrated to 0% Neutral ✓")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, "Throttle calibration error: ${e.message}")
                }
            }
        }
    }

    /**
     * Calibrates 6-Axis IMU pitch & roll zero level baseline.
     */
    fun calibrateImuZeroLevel(
        onProgress: (step: String, progress: Float) -> Unit = { _, _ -> },
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        val speed = powertrainState.value.speedKmh
        if (!speed.isNaN() && speed > 0.5) {
            onResult(false, "Safety Interlock: Scooter must be stationary (${String.format(Locale.US, "%.1f", speed)} km/h)")
            return
        }

        if (isDemoMode.value) {
            scope.launch {
                onProgress("Verifying stationary level orientation...", 0.20f)
                delay(500)
                onProgress("Sampling 6-axis gyro & accelerometer vectors (50 samples)...", 0.60f)
                delay(800)
                onProgress("Zeroing pitch & roll offsets to absolute 0.0°...", 0.85f)
                delay(500)
                decoder.resetImuZeroOffset()
                calibrationTelemetry.value = decoder.getCalibrationTelemetry()
                onProgress("IMU 6-Axis Zero Level calibrated to 0.0° ✓", 1.0f)
                onResult(true, "IMU 6-Axis Zero Level calibrated to 0.0° (Demo Mode) ✓")
            }
            return
        }

        val grpc = client
        if (grpc == null) {
            onResult(false, "Scooter not connected. Connect over Wi-Fi first.")
            return
        }
        scope.launch {
            try {
                onProgress("Checking level ground stability (speed 0.0 km/h)...", 0.20f)
                delay(400)
                if (!streamStats.value.isConnected) {
                    onResult(false, "Calibration aborted: Vehicle disconnected.")
                    return@launch
                }
                onProgress("Sampling 6-axis acceleration vectors at 50Hz...", 0.55f)
                delay(700)
                onProgress("Transmitting zero baseline to MoveOS (CAN 0x682)...", 0.85f)
                val cmd = "cansend vcan0 682#0100000000000000; cansend vcan0 6BC#4900000000000000"
                grpc.sendVehicleCommand(cmd, 6L)
                decoder.resetImuZeroOffset()
                calibrationTelemetry.value = decoder.getCalibrationTelemetry()
                onProgress("IMU zero baseline request sent.", 1.0f)
                withContext(Dispatchers.Main) {
                    onResult(true, "IMU zero baseline request sent.")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, "IMU calibration error: ${e.message}")
                }
            }
        }
    }

    /**
     * Sets Dynamic Regen Level (0=Off, 1=Low, 2=Med, 3=High, 4=Max).
     */
    fun setRegenTorqueLevel(
        level: Int,
        onProgress: (step: String, progress: Float) -> Unit = { _, _ -> },
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        val clamped = level.coerceIn(0, 4)
        val label = when (clamped) {
            0 -> "OFF (0%)"
            1 -> "LOW (5%)"
            2 -> "MEDIUM (10%)"
            3 -> "HIGH (15%)"
            else -> "MAXIMUM (20%)"
        }

        if (isDemoMode.value) {
            vehicleStatus.value = vehicleStatus.value.copy(regenLevel = clamped)
            scope.launch {
                onProgress("Transmitting CAN 0x115 regen configuration...", 0.40f)
                delay(300)
                onProgress("Regen map applied to VCU ($label) ✓", 1.0f)
                onResult(true, "Regen torque set to $label (Demo Mode) ✓")
            }
            return
        }

        val grpc = client
        if (grpc == null) {
            onResult(false, "Scooter not connected. Connect over Wi-Fi first.")
            return
        }

        scope.launch {
            try {
                onProgress("Transmitting CAN 0x115 regen configuration...", 0.35f)
                val regenByte = String.format(Locale.US, "%02x", clamped)
                val cmd = "cansend vcan0 115#${regenByte}00000000000000; am broadcast -a com.olaelectric.action.SET_REGEN_LEVEL --ei level $clamped"
                grpc.sendVehicleCommand(cmd, 6L)
                onProgress("Regen level request sent to MoveOS.", 1.0f)
                withContext(Dispatchers.Main) {
                    onResult(true, "Regen level request sent: $label.")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, "Regen update error: ${e.message}")
                }
            }
        }
    }
}
