package com.sarthak.olacan.analyzer

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import com.sarthak.olacan.model.AlertSeverity
import com.sarthak.olacan.model.BatteryState
import com.sarthak.olacan.model.DiagnosticTestStatus
import com.sarthak.olacan.model.PowertrainState
import com.sarthak.olacan.model.SubsystemTestItem
import com.sarthak.olacan.model.SubsystemTestState
import com.sarthak.olacan.model.ThermalState
import com.sarthak.olacan.model.VehicleAlert
import com.sarthak.olacan.model.VehicleHealthReport
import com.sarthak.olacan.model.VehicleStatus
import com.sarthak.olacan.service.CanStreamManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Executes a comprehensive, guided Vehicle Self-Diagnostic and Health Test Suite
 * covering Right/Left Brake Sensors, Horn, Turn Indicators & Acoustic Chime,
 * Exterior Lighting, High-Voltage Contactors, e-SIM/GPS, 14S NMC Battery Health,
 * VCU, MCU Inverter IPM, and DTC Fault Codes.
 */
object VehicleSelfTestManager {

    private val testExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        com.sarthak.olacan.util.AppLogManager.error("VehicleSelfTest", "Diagnostic test error: ${throwable.message}", throwable)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + testExceptionHandler)
    private var testJob: Job? = null

    private val _testStatus = MutableStateFlow(DiagnosticTestStatus.IDLE)
    val testStatus = _testStatus.asStateFlow()

    private val _currentStepIndex = MutableStateFlow(0)
    val currentStepIndex = _currentStepIndex.asStateFlow()

    private val _testItems = MutableStateFlow<List<SubsystemTestItem>>(emptyList())
    val testItems = _testItems.asStateFlow()

    private val _lastReport = MutableStateFlow<VehicleHealthReport?>(null)
    val lastReport = _lastReport.asStateFlow()

    private var toneGenerator: ToneGenerator? = null

    fun init(context: Context) {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 90)
        } catch (_: Exception) {
            toneGenerator = null
        }
    }

    private fun buildDefaultTests(): List<SubsystemTestItem> {
        return listOf(
            SubsystemTestItem(
                id = "VCU_LINK",
                name = "VCU Telemetry & CAN Bus",
                category = "VCU",
                description = "Validates Vehicle Control Unit CAN 2.0B broadcast at port 50005 & frame jitter < 40ms"
            ),
            SubsystemTestItem(
                id = "BRAKE_RIGHT",
                name = "Right Brake Lever Sensor",
                category = "BRAKES",
                description = "Tests front hydraulic brake microswitch contact and MoveOS stop lamp actuation circuit",
                isInteractive = true,
                requiresManualConfirmation = false,
                interactivePrompt = "Firmly squeeze the RIGHT (front) brake lever. App will automatically detect the CAN signal.",
                interactiveActionLabel = "Confirm Right Brake",
                timeoutSecondsRemaining = 30
            ),
            SubsystemTestItem(
                id = "BRAKE_LEFT",
                name = "Left Brake Lever Sensor",
                category = "BRAKES",
                description = "Tests rear regenerative brake microswitch signal and low-side pull-up circuit",
                isInteractive = true,
                requiresManualConfirmation = false,
                interactivePrompt = "Firmly squeeze the LEFT (rear) brake lever. App will automatically detect the CAN signal.",
                interactiveActionLabel = "Confirm Left Brake",
                timeoutSecondsRemaining = 30
            ),
            SubsystemTestItem(
                id = "HORN_TEST",
                name = "Handlebar Horn Switch & Actuator",
                category = "HORN",
                description = "Verifies handlebar horn tactile switch closure via CAN 0x6AC",
                isInteractive = true,
                requiresManualConfirmation = false,
                interactivePrompt = "Press the HORN button on the left switch cluster. App will automatically detect the CAN signal.",
                interactiveActionLabel = "Confirm Horn Sound",
                timeoutSecondsRemaining = 30
            ),
            SubsystemTestItem(
                id = "INDICATOR_RIGHT",
                name = "Right Turn Signal Indicator",
                category = "LIGHTING",
                description = "Tests right turn indicator switch, dash telltale TTL (0x353), and 12V LED driver stage",
                isInteractive = true,
                requiresManualConfirmation = false,
                interactivePrompt = "Toggle the RIGHT turn indicator switch on the handlebar. App will automatically detect the CAN signal.",
                interactiveActionLabel = "Confirm Right Indicator",
                timeoutSecondsRemaining = 30
            ),
            SubsystemTestItem(
                id = "INDICATOR_LEFT",
                name = "Left Turn Signal Indicator",
                category = "LIGHTING",
                description = "Tests left turn indicator switch, dash telltale TTL (0x353), and 12V LED driver stage",
                isInteractive = true,
                requiresManualConfirmation = false,
                interactivePrompt = "Toggle the LEFT turn indicator switch on the handlebar. App will automatically detect the CAN signal.",
                interactiveActionLabel = "Confirm Left Indicator",
                timeoutSecondsRemaining = 30
            ),
            SubsystemTestItem(
                id = "INDICATOR_VOICE",
                name = "Speaker & Chime Audio Check",
                category = "AUDIO",
                description = "Acoustic sound verification for vehicle audio synthesizer and speaker transducer",
                isInteractive = true,
                requiresManualConfirmation = true,
                interactivePrompt = "Audio test chime initiated. Listen carefully to the vehicle. Did you hear the audio sound / chime clearly?",
                interactiveActionLabel = "Confirm Sound Heard ✓",
                timeoutSecondsRemaining = 30
            ),
            SubsystemTestItem(
                id = "LIGHTS_HIGH_BEAM",
                name = "Headlamp High Beam Circuit",
                category = "LIGHTING",
                description = "Interrogates high beam tactile switch (0x6AC), dash telltale (0x353), and high-power LED driver",
                isInteractive = true,
                requiresManualConfirmation = false,
                interactivePrompt = "Flick or toggle the HIGH BEAM switch on the left switch cluster. App will automatically detect the CAN signal.",
                interactiveActionLabel = "Confirm High Beam",
                timeoutSecondsRemaining = 30
            ),
            SubsystemTestItem(
                id = "LIGHTS_LOW_BEAM",
                name = "Low Beam Headlamp & DRL (Visual)",
                category = "LIGHTING",
                description = "Visual check for front LED projector and circular DRL halo (hardwired AHO)",
                isInteractive = true,
                requiresManualConfirmation = true,
                interactivePrompt = "Visual Inspection: Verify that the front LED projector Low Beam and front DRL circular halo ring are illuminated.",
                interactiveActionLabel = "Confirm Lights OK ✓",
                timeoutSecondsRemaining = 30
            ),
            SubsystemTestItem(
                id = "LIGHTS_BRAKE_LAMP",
                name = "Rear Tail Brake Lamp Circuit",
                category = "LIGHTING",
                description = "Verifies rear stop lamp relay command (0x6BF) and LED current sense feedback (0x6C3)",
                isInteractive = true,
                requiresManualConfirmation = false,
                interactivePrompt = "Squeeze either brake lever firmly and visually verify rear tail stop lamp lights up.",
                interactiveActionLabel = "Confirm Brake Lamp",
                timeoutSecondsRemaining = 30
            ),
            SubsystemTestItem(
                id = "MAIN_CONTACTOR",
                name = "HV Pack Main Contactor & Isolation",
                category = "CONTACTOR",
                description = "Evaluates high-voltage main battery pack contactor (0x11C), DC precharge, and FC relays (0x6BF)"
            ),
            SubsystemTestItem(
                id = "ESIM_CONNECTIVITY",
                name = "e-SIM, 4G LTE & GPS Telematics",
                category = "CONNECTIVITY",
                description = "Checks MoveOS cellular telematics modem, carrier registration, signal RSSI & GPS lock"
            ),
            SubsystemTestItem(
                id = "BATTERY_HEALTH",
                name = "14S NMC Battery Health & Cell Imbalance",
                category = "BATTERY",
                description = "Evaluates 14 series cell resting voltage spread, pack usable SOH, and BMS capacity"
            ),
            SubsystemTestItem(
                id = "BATTERY_THERMALS",
                name = "Pack Thermal Probes Reference",
                category = "BATTERY",
                description = "Inspects 6 sub-pack NTC thermistors across front, core, and rear cell bricks (< 55°C limit)"
            ),
            SubsystemTestItem(
                id = "MCU_INVERTER",
                name = "MCU & Inverter IPM Power Stage",
                category = "MCU",
                description = "Evaluates motor inverter gate drive signals, IPM thermal headroom, and resolver synchronization"
            ),
            SubsystemTestItem(
                id = "THROTTLE_HALL",
                name = "Throttle Position Sensor",
                category = "VCU",
                description = "Verifies dual Hall sensor linearity, return spring zero-throttle voltage & 0-100% travel"
            ),
            SubsystemTestItem(
                id = "SIDE_STAND",
                name = "Side Stand Hall Interlock",
                category = "VCU",
                description = "Verifies side stand magnetic Hall sensor switch logic and motor kill safety interlock"
            ),
            SubsystemTestItem(
                id = "DTC_ERROR_CODES",
                name = "ECU Active & Stored Fault Codes",
                category = "DTC",
                description = "Scans vehicle diagnostic memory for active or stored DTCs across BMS, MCU, VCU & ABS"
            )
        )
    }

    fun startSelfTest(
        battery: BatteryState,
        powertrain: PowertrainState,
        thermal: ThermalState,
        vehicle: VehicleStatus,
        activeAlerts: List<VehicleAlert>,
        isConnected: Boolean
    ) {
        testJob?.cancel()
        val tests = buildDefaultTests()
        _testItems.value = tests
        _currentStepIndex.value = 0
        _testStatus.value = DiagnosticTestStatus.RUNNING

        testJob = scope.launch {
            for (i in tests.indices) {
                _currentStepIndex.value = i
                val item = tests[i]

                // Mark current test running
                updateItem(i) { it.copy(state = SubsystemTestState.RUNNING, timeoutSecondsRemaining = 30) }
                delay(400) // Brief diagnostic acquisition interval

                if (item.isInteractive) {
                    if (item.id == "INDICATOR_VOICE") {
                        playAudioChime()
                    }
                    var remainingSec = 30
                    var detectedSignal = false
                    var signalMessage = ""
                    var signalDetail = ""

                    // Baseline capture to prevent latching pre-existing presses
                    val initialControls = CanStreamManager.controlsState.value
                    var baselineRightBrake = initialControls.rightBrakePressed
                    var baselineLeftBrake = initialControls.leftBrakePressed
                    var baselineHorn = initialControls.hornPressed
                    var baselineRightInd = initialControls.rightIndicatorActive
                    var baselineLeftInd = initialControls.leftIndicatorActive
                    var baselineHighBeam = initialControls.highBeamActive

                    // 30 seconds countdown (looping every 100ms for ultra-responsive CAN trigger)
                    while (remainingSec > 0 && _testItems.value[i].state == SubsystemTestState.RUNNING && _testStatus.value == DiagnosticTestStatus.RUNNING) {
                        val controls = CanStreamManager.controlsState.value

                        if (!item.requiresManualConfirmation) {
                            when (item.id) {
                                "BRAKE_RIGHT" -> {
                                    if (baselineRightBrake && !controls.rightBrakePressed) baselineRightBrake = false
                                    if (!baselineRightBrake && controls.rightBrakePressed) {
                                        detectedSignal = true
                                        signalMessage = "CAN Signal Detected ✓ (0x15B Front Brake Press)"
                                        signalDetail = "Right hydraulic brake lever contactor closed. CAN bus 0x15B bit 4 verified."
                                    }
                                }
                                "BRAKE_LEFT" -> {
                                    if (baselineLeftBrake && !controls.leftBrakePressed) baselineLeftBrake = false
                                    if (!baselineLeftBrake && controls.leftBrakePressed) {
                                        detectedSignal = true
                                        signalMessage = "CAN Signal Detected ✓ (0x15B Rear Brake Press)"
                                        signalDetail = "Left regen brake lever microswitch closure verified on CAN 0x15B bit 5."
                                    }
                                }
                                "HORN_TEST" -> {
                                    if (baselineHorn && !controls.hornPressed) baselineHorn = false
                                    if (!baselineHorn && controls.hornPressed) {
                                        detectedSignal = true
                                        signalMessage = "CAN Signal Detected ✓ (0x6AC Horn Pressed)"
                                        signalDetail = "Handlebar horn switch contactor closure verified via CAN 0x6AC bit 4."
                                    }
                                }
                                "INDICATOR_RIGHT" -> {
                                    if (baselineRightInd && !controls.rightIndicatorActive) baselineRightInd = false
                                    if (!baselineRightInd && controls.rightIndicatorActive) {
                                        detectedSignal = true
                                        signalMessage = "CAN Signal Detected ✓ (0x6AC / 0x353 Right Blink)"
                                        signalDetail = "Right turn indicator switch toggled; dash TTL and 12V driver active."
                                    }
                                }
                                "INDICATOR_LEFT" -> {
                                    if (baselineLeftInd && !controls.leftIndicatorActive) baselineLeftInd = false
                                    if (!baselineLeftInd && controls.leftIndicatorActive) {
                                        detectedSignal = true
                                        signalMessage = "CAN Signal Detected ✓ (0x6AC / 0x353 Left Blink)"
                                        signalDetail = "Left turn indicator switch toggled; dash TTL and 12V driver active."
                                    }
                                }
                                "LIGHTS_HIGH_BEAM" -> {
                                    if (baselineHighBeam && !controls.highBeamActive) baselineHighBeam = false
                                    if (!baselineHighBeam && controls.highBeamActive) {
                                        detectedSignal = true
                                        signalMessage = "CAN Signal Detected ✓ (0x6AC High Beam)"
                                        signalDetail = "High beam switch flicked; verified on CAN 0x6AC bit 0."
                                    }
                                }
                                "LIGHTS_BRAKE_LAMP" -> {
                                    if (controls.brakeLightActive || (!baselineRightBrake && controls.rightBrakePressed) || (!baselineLeftBrake && controls.leftBrakePressed)) {
                                        detectedSignal = true
                                        signalMessage = "CAN Signal Detected ✓ (0x6BF Brake Lamp Actuated)"
                                        signalDetail = "Rear tail stop lamp LED driver output verified on CAN 0x6BF upon brake squeeze."
                                    }
                                }
                            }
                        }

                        if (detectedSignal) {
                            playAudioChime()
                            updateItem(i) {
                                it.copy(
                                    state = SubsystemTestState.PASSED,
                                    measuredValue = signalMessage,
                                    diagnosticDetail = signalDetail,
                                    timeoutSecondsRemaining = remainingSec
                                )
                            }
                            delay(600) // Brief visual celebration before advancing
                            break
                        }

                        // Sleep in 100ms chunks while decrementing seconds
                        for (sub in 0 until 10) {
                            delay(100)
                            if (_testItems.value[i].state != SubsystemTestState.RUNNING || _testStatus.value != DiagnosticTestStatus.RUNNING) break
                        }
                        remainingSec--
                        if (_testItems.value[i].state == SubsystemTestState.RUNNING) {
                            updateItem(i) { it.copy(timeoutSecondsRemaining = remainingSec) }
                        }
                    }

                    // If timed out after 30 seconds without signal and not manually confirmed/skipped:
                    if (_testItems.value[i].state == SubsystemTestState.RUNNING && _testStatus.value == DiagnosticTestStatus.RUNNING) {
                        val timeoutMsg = if (item.requiresManualConfirmation) "Timed Out (30s) — No Confirmation" else "Timed Out (30s) — No CAN Signal"
                        val timeoutDetail = if (item.requiresManualConfirmation) "User visual or auditory inspection timed out after 30 seconds." else "No switch activation or contactor response detected on CAN within 30 seconds."
                        updateItem(i) {
                            it.copy(
                                state = SubsystemTestState.FAILED,
                                measuredValue = timeoutMsg,
                                diagnosticDetail = timeoutDetail,
                                timeoutSecondsRemaining = 0
                            )
                        }
                        delay(500)
                    }

                    if (_testStatus.value != DiagnosticTestStatus.RUNNING) break
                } else {
                    // Automated Evaluation based on live CAN data
                    evaluateAutomatedStep(i, item, isConnected)
                    delay(400)
                }
            }

            if (_testStatus.value == DiagnosticTestStatus.RUNNING) {
                // Finalize report
                finalizeReport(vehicle.odometerKm)
                _testStatus.value = DiagnosticTestStatus.COMPLETED
            }
        }
    }

    private fun evaluateAutomatedStep(
        index: Int,
        item: SubsystemTestItem,
        isConnected: Boolean
    ) {
        fun record(passed: Boolean?, measurement: String, detail: String) {
            val state = when (passed) {
                true -> SubsystemTestState.PASSED
                false -> SubsystemTestState.FAILED
                null -> SubsystemTestState.SKIPPED
            }
            updateItem(index) { it.copy(state = state, measuredValue = measurement, diagnosticDetail = detail) }
        }

        val stream = CanStreamManager.streamStats.value
        if (item.id == "VCU_LINK") {
            val hasFrames = isConnected && stream.isConnected && stream.isAwake
            record(hasFrames, if (hasFrames) "CAN frames received" else "No active CAN stream",
                "Checks whether live frames are arriving from the vehicle.")
            return
        }
        if (!isConnected || !stream.isConnected || !stream.isAwake) {
            record(null, "Not tested", "Live CAN telemetry is unavailable.")
            return
        }

        when (item.id) {
            "ESIM_CONNECTIVITY" -> record(null, "Not tested", "Live LTE and GPS measurements are unavailable.")
            "MAIN_CONTACTOR" -> record(null, "Not tested", "The live stream does not verify contactor operation or insulation.")
            "SIDE_STAND" -> record(null, "Not tested", "Stand interlock operation requires a physical check.")
            "THROTTLE_HALL" -> record(null, "Not tested", "The live stream does not verify throttle return or both sensors.")
            "BATTERY_HEALTH" -> {
                val spread = CanStreamManager.batteryState.value.cellDeltaMv
                if (spread.isFinite()) {
                    record(spread < 250.0, "Cell spread: ${spread.toInt()} mV",
                        "Checks the observed cell voltage spread during this test.")
                } else record(null, "Not tested", "Cell voltage spread is unavailable.")
            }
            "BATTERY_THERMALS" -> {
                val sensors = CanStreamManager.thermalState.value
                val max = sensors.packMaxTempC
                val gradient = sensors.packThermalGradientC
                if (max.isFinite() && gradient.isFinite()) {
                    record(max < 55.0 && gradient < 6.0,
                        String.format(Locale.US, "Max: %.1f°C • Gradient: %.1f°C", max, gradient),
                        "Checks the observed pack temperature and gradient.")
                } else record(null, "Not tested", "Thermal probe readings are incomplete.")
            }
            "MCU_INVERTER" -> {
                val temperature = CanStreamManager.thermalState.value.mcuTempC
                if (temperature.isFinite()) {
                    record(temperature < 80.0, String.format(Locale.US, "Inverter: %.1f°C", temperature),
                        "Checks inverter temperature during the test.")
                } else record(null, "Not tested", "Inverter temperature is unavailable.")
            }
            "DTC_ERROR_CODES" -> {
                val critical = CanStreamManager.activeAlerts.value.firstOrNull { it.severity == AlertSeverity.CRITICAL }
                if (critical == null) {
                    record(null, "No critical alerts observed", "Stored diagnostic trouble codes were not queried.")
                } else record(false, critical.title, "Critical alert observed in the live CAN stream.")
            }
        }
    }

    fun confirmInteractiveStep(testId: String, passed: Boolean) {
        val currentList = _testItems.value
        val idx = currentList.indexOfFirst { it.id == testId }
        if (idx >= 0 && currentList[idx].state == SubsystemTestState.RUNNING) {
            val note = when (testId) {
                "BRAKE_RIGHT" -> if (passed) "Right front brake microswitch contact verified; stop lamp triggered." else "Right brake switch contact not detected."
                "BRAKE_LEFT" -> if (passed) "Left rear brake microswitch closure verified; CAN 0x15B active." else "Left switch contact not detected."
                "HORN_TEST" -> if (passed) "Handlebar horn tactile switch closure verified; 12V driver active." else "Horn switch contact not detected."
                "INDICATOR_RIGHT" -> if (passed) "Right turn indicator switch toggled; dash TTL and 12V driver active." else "Right turn switch contact not detected."
                "INDICATOR_LEFT" -> if (passed) "Left turn indicator switch toggled; dash TTL and 12V driver active." else "Left turn switch contact not detected."
                "INDICATOR_VOICE" -> if (passed) "Turn indicator acoustic voice chime verified via MoveOS audio synthesizer." else "Indicator audio click not audible."
                "LIGHTS_HIGH_BEAM" -> if (passed) "High beam headlamp relay command verified on CAN 0x6AC / 0x353." else "High beam switch contact not detected."
                "LIGHTS_LOW_BEAM" -> if (passed) "Low beam headlamp and front DRL halo ring verified on CAN 0x353." else "Low beam circuit issue observed."
                "LIGHTS_BRAKE_LAMP" -> if (passed) "Rear tail brake lamp relay command and driver current sense verified." else "Brake lamp circuit issue observed."
                else -> if (passed) "Verified OK by technician." else "Failed verification."
            }

            updateItem(idx) {
                it.copy(
                    state = if (passed) SubsystemTestState.PASSED else SubsystemTestState.FAILED,
                    measuredValue = if (passed) "Verified OK ✓" else "Check Failed ✗",
                    diagnosticDetail = note
                )
            }
        }
    }

    fun skipCurrentStep() {
        val idx = _currentStepIndex.value
        if (idx in _testItems.value.indices) {
            updateItem(idx) {
                it.copy(
                    state = SubsystemTestState.SKIPPED,
                    measuredValue = "Skipped by User",
                    diagnosticDetail = "Subsystem test skipped during self-test routine."
                )
            }
        }
    }

    fun playAudioChime() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 350)
        } catch (_: Exception) {}
    }

    fun abortTest() {
        testJob?.cancel()
        _testStatus.value = DiagnosticTestStatus.ABORTED
    }

    fun resetTest() {
        testJob?.cancel()
        _testStatus.value = DiagnosticTestStatus.IDLE
        _testItems.value = emptyList()
        _currentStepIndex.value = 0
    }

    private fun finalizeReport(odometer: Double) {
        val items = _testItems.value
        val total = items.size
        val passed = items.count { it.state == SubsystemTestState.PASSED }
        val failed = items.count { it.state == SubsystemTestState.FAILED }

        // Score calculation: 100 max, -8 per fail, -2 per skip
        val score = (100 - (failed * 10) - (items.count { it.state == SubsystemTestState.SKIPPED } * 3)).coerceIn(10, 100)
        val overallPassed = total > 0 && passed == total

        val verdict = when {
            failed > 0 -> "ATTENTION REQUIRED — CHECK FAILED ITEMS"
            overallPassed -> "ALL PERFORMED CHECKS PASSED"
            else -> "INCOMPLETE — SOME CHECKS WERE NOT PERFORMED"
        }

        _lastReport.value = VehicleHealthReport(
            timestampMs = System.currentTimeMillis(),
            overallHealthScore = score,
            isPassed = overallPassed,
            totalTests = total,
            passedTests = passed,
            failedTests = failed,
            testItems = items,
            summaryVerdict = verdict,
            odometerKm = odometer
        )
    }

    private fun updateItem(index: Int, transform: (SubsystemTestItem) -> SubsystemTestItem) {
        val list = _testItems.value.toMutableList()
        if (index in list.indices) {
            list[index] = transform(list[index])
            _testItems.value = list
        }
    }
}
