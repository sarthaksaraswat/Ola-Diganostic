package com.sarthak.olacan.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.sarthak.olacan.service.CanStreamManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale

data class AppLogEntry(
    val timestampMs: Long = System.currentTimeMillis(),
    val level: String, // "INFO", "WARN", "ERROR", "CRASH", "CAN", "GRPC"
    val tag: String,
    val message: String
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestampMs))

    override fun toString(): String = "[$formattedTime] [$level] [$tag] $message"
}

object AppLogManager {

    private const val MAX_LOG_ENTRIES = 500
    private const val LOG_FILE_NAME = "ola_app_events.log"
    private const val CRASH_FILE_NAME = "latest_crash.txt"
    private const val PREFS_NAME = "ola_crash_tracking_prefs"

    private val logExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("AppLogManager", "Log coroutine error: ${throwable.message}", throwable)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + logExceptionHandler)
    private val buffer = Collections.synchronizedList(mutableListOf<AppLogEntry>())

    private val _liveLogs = MutableStateFlow<List<AppLogEntry>>(emptyList())
    val liveLogs = _liveLogs.asStateFlow()

    private val _lastCrashReport = MutableStateFlow<String?>(null)
    val lastCrashReport = _lastCrashReport.asStateFlow()

    private var appContext: Context? = null
    private var isInitialized = false
    private var appLaunchTimestampMs: Long = 0L

    init {
        log("INFO", "AppLogManager", "App Log Analytics engine instantiated")
    }

    /**
     * Initializes disk persistence, restores previous logs, checks for previous crashes,
     * and installs a global uncaught exception crash recorder.
     */
    fun init(context: Context) {
        if (isInitialized) return
        val app = context.applicationContext
        appContext = app
        isInitialized = true
        appLaunchTimestampMs = System.currentTimeMillis()

        // 1. Restore previous logs from disk
        restoreLogsFromDisk(app)

        // 2. Check for crash from previous session
        checkPreviousCrash(app)

        // 3. Install Global Uncaught Exception Handler
        installCrashHandler(app)

        info("AppLogManager", "App Log Analytics initialized with disk persistence & crash recovery")
    }

    private fun restoreLogsFromDisk(context: Context) {
        try {
            val logFile = File(context.filesDir, LOG_FILE_NAME)
            if (logFile.exists() && logFile.length() > 0) {
                val lines = logFile.readLines(StandardCharsets.UTF_8).takeLast(250)
                for (line in lines) {
                    val entry = parseLogLine(line)
                    if (entry != null) {
                        buffer.add(0, entry)
                    }
                }
                while (buffer.size > MAX_LOG_ENTRIES) {
                    buffer.removeAt(buffer.size - 1)
                }
                _liveLogs.value = buffer.toList()
            }
        } catch (_: Exception) {}
    }

    private fun checkPreviousCrash(context: Context) {
        try {
            val crashFile = File(context.filesDir, CRASH_FILE_NAME)
            if (crashFile.exists() && crashFile.length() > 0) {
                val crashText = crashFile.readText(StandardCharsets.UTF_8)
                _lastCrashReport.value = crashText
                log("CRASH", "CrashWatcher", "Previous crash detected from prior session! Details captured.")
            }
        } catch (_: Exception) {}
    }

    private fun installCrashHandler(context: Context) {
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val stackTrace = Log.getStackTraceString(throwable)
                val crashTimeMs = System.currentTimeMillis()
                val timeSinceLaunchSec = (crashTimeMs - appLaunchTimestampMs) / 1000L

                val crashReport = buildString {
                    appendLine("================================================================================")
                    appendLine("                      OLA DIAGNOSTIC - UNCAUGHT CRASH REPORT")
                    appendLine("================================================================================")
                    appendLine("Crash Time         : ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS z", Locale.US).format(Date(crashTimeMs))}")
                    appendLine("Seconds Since Boot : ${timeSinceLaunchSec}s")
                    appendLine("Thread             : ${thread.name} (id: ${thread.id})")
                    appendLine("Exception Class    : ${throwable.javaClass.name}")
                    appendLine("Exception Message  : ${throwable.message ?: "None"}")
                    appendLine("Device             : ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
                    appendLine("Android OS         : Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    appendLine("--------------------------------------------------------------------------------")
                    appendLine("STACK TRACE:")
                    appendLine(stackTrace)
                    appendLine("================================================================================")
                }

                // Write synchronously to disk so file is 100% written before process termination
                val crashFile = File(context.filesDir, CRASH_FILE_NAME)
                crashFile.writeText(crashReport, StandardCharsets.UTF_8)

                // Track if crash occurred immediately on launch (< 15 seconds)
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().apply {
                    putLong("last_crash_timestamp", crashTimeMs)
                    putBoolean("crashed_on_launch", timeSinceLaunchSec < 15L)
                    apply()
                }

                // Append synchronous crash entry to persistent log
                val logFile = File(context.filesDir, LOG_FILE_NAME)
                logFile.appendText("[${SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())}] [CRASH] [${thread.name}] ${throwable.javaClass.simpleName}: ${throwable.message}\n")

                // Flush & emergency save active in-flight CAN logs
                LogRecorder.emergencySaveOnCrash(context)
            } catch (_: Exception) {}

            // Forward to Android system handler
            originalHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Checks if the app crashed immediately after launch in the previous session.
     * Used by CanStreamManager to temporarily disable aggressive auto-connect on launch to break crash loops.
     */
    fun hasRecentCrashOnLaunch(context: Context): Boolean {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val crashedOnLaunch = prefs.getBoolean("crashed_on_launch", false)
            val lastCrash = prefs.getLong("last_crash_timestamp", 0L)
            val isRecent = (System.currentTimeMillis() - lastCrash) < (24 * 60 * 60 * 1000L) // within 24h
            crashedOnLaunch && isRecent
        } catch (_: Exception) {
            false
        }
    }

    fun clearCrashReport() {
        val ctx = appContext ?: return
        try {
            val crashFile = File(ctx.filesDir, CRASH_FILE_NAME)
            if (crashFile.exists()) {
                crashFile.delete()
            }
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
            _lastCrashReport.value = null
            info("CrashWatcher", "Crash report cleared by user")
        } catch (_: Exception) {}
    }

    fun log(level: String, tag: String, message: String) {
        val entry = AppLogEntry(level = level, tag = tag, message = message)
        buffer.add(0, entry)
        if (buffer.size > MAX_LOG_ENTRIES) {
            buffer.removeAt(buffer.size - 1)
        }
        _liveLogs.value = buffer.toList()

        // Android Logcat
        when (level) {
            "ERROR", "CRASH" -> Log.e(tag, message)
            "WARN" -> Log.w(tag, message)
            else -> Log.i(tag, message)
        }

        // Asynchronously persist to internal storage
        appContext?.let { ctx ->
            scope.launch {
                try {
                    val logFile = File(ctx.filesDir, LOG_FILE_NAME)
                    if (logFile.length() > 1024 * 1024) { // Truncate if exceeds 1MB
                        val recent = logFile.readLines().takeLast(300)
                        logFile.writeText(recent.joinToString("\n") + "\n", StandardCharsets.UTF_8)
                    }
                    PrintWriter(FileWriter(logFile, true)).use { out ->
                        out.println(entry.toString())
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun info(tag: String, message: String) = log("INFO", tag, message)
    fun warn(tag: String, message: String) = log("WARN", tag, message)
    fun error(tag: String, message: String, throwable: Throwable? = null) {
        val fullMsg = if (throwable != null) "$message (${throwable.javaClass.simpleName}: ${throwable.message})" else message
        log("ERROR", tag, fullMsg)
    }

    fun clearLogs() {
        buffer.clear()
        _liveLogs.value = emptyList()
        appContext?.let { ctx ->
            try {
                File(ctx.filesDir, LOG_FILE_NAME).delete()
            } catch (_: Exception) {}
        }
        log("INFO", "AppLogManager", "Diagnostic event logs cleared")
    }

    private fun parseLogLine(line: String): AppLogEntry? {
        try {
            // Format: [12:09:47.123] [INFO] [Tag] Message
            val parts = line.split(" ", limit = 4)
            if (parts.size >= 4 && parts[0].startsWith("[") && parts[1].startsWith("[") && parts[2].startsWith("[")) {
                val level = parts[1].removePrefix("[").removeSuffix("]")
                val tag = parts[2].removePrefix("[").removeSuffix("]")
                val message = parts[3]
                return AppLogEntry(level = level, tag = tag, message = message)
            }
        } catch (_: Exception) {}
        return null
    }

    /**
     * Reads recent Android logcat lines for the current process.
     */
    suspend fun captureLogcat(linesCount: Int = 1000): String = withContext(Dispatchers.IO) {
        val pid = android.os.Process.myPid()
        val sb = StringBuilder()
        try {
            val process = ProcessBuilder("logcat", "-d", "-v", "time", "-t", linesCount.toString())
                .redirectErrorStream(true)
                .start()
            BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8)).useLines { lines ->
                lines.forEach { line ->
                    if (line.contains("$pid") || line.contains("OlaCan") || line.contains("MoveOS") || line.contains("grpc") || line.contains("FATAL") || line.contains("AndroidRuntime")) {
                        sb.appendLine(line)
                    }
                }
            }
        } catch (e: Exception) {
            sb.appendLine("Failed to dump logcat: ${e.message}")
        }
        sb.toString()
    }

    /**
     * Builds a comprehensive diagnostic bundle containing device specs, telemetry snapshot,
     * crash history, app events, and system logcat.
     */
    suspend fun generateDiagnosticBundle(context: Context): File = withContext(Dispatchers.IO) {
        val timeTag = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "OlaCAN_Diagnostics_$timeTag.txt"
        val file = File(context.cacheDir, fileName)

        val stats = CanStreamManager.streamStats.value
        val battery = CanStreamManager.batteryState.value
        val powertrain = CanStreamManager.powertrainState.value
        val versions = CanStreamManager.ecuVersions.value
        val alerts = CanStreamManager.recordedAlerts.value
        val isRecording = CanStreamManager.isRecording.value

        val crashReport = _lastCrashReport.value
        val logcatContent = captureLogcat(1200)

        val report = buildString {
            appendLine("================================================================================")
            appendLine("                     OLA S1 DIAGNOSTIC APP - SYSTEM LOG REPORT")
            appendLine("================================================================================")
            appendLine("Generated At       : ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS z", Locale.US).format(Date())}")
            appendLine("App Version        : 2.0 (Ola Diagnostic)")
            appendLine("Device Model       : ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("Android OS Version : Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Process PID        : ${android.os.Process.myPid()}")
            appendLine()

            if (!crashReport.isNullOrBlank()) {
                appendLine("********************************************************************************")
                appendLine("                  PREVIOUS RECORDED APP CRASH REPORT DETECTED")
                appendLine("********************************************************************************")
                appendLine(crashReport)
                appendLine("********************************************************************************")
                appendLine()
            }

            appendLine("--------------------------------------------------------------------------------")
            appendLine("1. CONNECTION & CAN TELEMETRY SNAPSHOT")
            appendLine("--------------------------------------------------------------------------------")
            appendLine("Connection Status  : ${if (stats.isConnected) "CONNECTED" else "DISCONNECTED"}")
            appendLine("Target Host        : ${stats.connectedHost}")
            appendLine("Roundtrip Latency  : ${stats.latencyMs} ms")
            appendLine("Stream Rate (FPS)  : ${String.format(Locale.US, "%.1f", stats.framesPerSecond)} frames/sec")
            appendLine("Total CAN Frames   : ${stats.totalFrames}")
            appendLine("Distinct CAN IDs   : ${stats.distinctIdsCount}")
            appendLine("CAN Log Recording  : ${if (isRecording) "ACTIVE" else "IDLE"}")
            appendLine("Status Message     : ${stats.statusMessage}")
            appendLine()

            appendLine("--------------------------------------------------------------------------------")
            appendLine("2. VEHICLE ECU FIRMWARE IDENTIFIERS")
            appendLine("--------------------------------------------------------------------------------")
            appendLine("MoveOS Software    : ${versions.moveOsVersion.ifBlank { "N/A" }}")
            appendLine("VCU Firmware       : ${versions.vcuFirmware.ifBlank { "N/A" }}")
            appendLine("BMS Firmware       : ${versions.bmsFirmware.ifBlank { "N/A" }}")
            appendLine("MCU Firmware       : ${versions.mcuFirmware.ifBlank { "N/A" }}")
            appendLine("HU Hardware/SW     : ${versions.huFirmware.ifBlank { "N/A" }}")
            appendLine()

            appendLine("--------------------------------------------------------------------------------")
            appendLine("3. LIVE POWERTRAIN & BATTERY STATE")
            appendLine("--------------------------------------------------------------------------------")
            appendLine("Drive Mode         : ${powertrain.driveMode}")
            appendLine("Speed / Wheel      : ${powertrain.speedKmh} km/h (RPM: ${powertrain.motorRpm})")
            appendLine("Throttle Input     : ${powertrain.throttlePercent}% (${powertrain.throttleVoltage} V)")
            appendLine("Battery Pack SOC   : ${battery.socPercent}% (Display: ${battery.displaySocPercent}%)")
            appendLine("Battery Pack SOH   : ${battery.packSohPercent}%")
            appendLine("Pack Total Voltage : ${battery.busVoltageV} V")
            appendLine("Pack Current / Pwr : ${battery.batteryCurrentA} A (${battery.powerKw} kW)")
            appendLine("Cell Spread (Δ)    : ${battery.cellDeltaMv} mV")
            val cellsList = battery.cellVoltagesV.joinToString(", ") { String.format(Locale.US, "%.3fV", it) }
            appendLine("14S NMC Cells      : [$cellsList]")
            appendLine()

            appendLine("--------------------------------------------------------------------------------")
            appendLine("4. CAPTURED ACTIVE & RECORDED DTC FAULTS (${alerts.size} Total)")
            appendLine("--------------------------------------------------------------------------------")
            if (alerts.isEmpty()) {
                appendLine("No active or stored DTC faults recorded.")
            } else {
                alerts.forEachIndexed { i, a ->
                    appendLine("#${i + 1} [${SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(a.timestampMs))}] [${a.severity}] [${a.component}] ${a.title}")
                    appendLine("    Code       : ${a.id} (CAN: ${a.sourceCanIdHex})")
                    appendLine("    Description: ${a.description}")
                }
            }
            appendLine()

            appendLine("--------------------------------------------------------------------------------")
            appendLine("5. IN-APP PERSISTENT EVENT LOGS (${buffer.size} Events)")
            appendLine("--------------------------------------------------------------------------------")
            buffer.reversed().forEach { entry ->
                appendLine(entry.toString())
            }
            appendLine()

            appendLine("--------------------------------------------------------------------------------")
            appendLine("6. SYSTEM LOGCAT OUTPUT (FILTERED FOR APP & VEHICLE DAEMON)")
            appendLine("--------------------------------------------------------------------------------")
            appendLine(logcatContent)
            appendLine("================================================================================")
            appendLine("                             END OF DIAGNOSTIC REPORT")
            appendLine("================================================================================")
        }

        file.writeText(report, StandardCharsets.UTF_8)
        file
    }

    /**
     * Generates report and launches the system share sheet.
     */
    suspend fun shareDiagnosticReport(context: Context) = withContext(Dispatchers.Main) {
        try {
            val file = withContext(Dispatchers.IO) { generateDiagnosticBundle(context) }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Ola S1 App Diagnostic Log (${file.name})")
                putExtra(Intent.EXTRA_TEXT, "Attached is the Ola Diagnostic App Log and Crash Analysis for debugging.")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Diagnostic Logs")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to share logs: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Copies recent event logs to clipboard.
     */
    fun copyLogsToClipboard(context: Context) {
        try {
            val text = buffer.joinToString("\n") { it.toString() }
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("OlaAppLogs", text)
            clipboard?.setPrimaryClip(clip)
            Toast.makeText(context, "Diagnostic logs copied to clipboard!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to copy logs: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
