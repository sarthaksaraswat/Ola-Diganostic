package com.sarthak.olacan.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.sarthak.olacan.model.CanFrame
import com.sarthak.olacan.model.VehicleAlert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.GZIPOutputStream

data class SavedLogInfo(
    val fileName: String,
    val filePath: String,
    val totalFrames: Long,
    val durationSeconds: Long,
    val fileSizeFormatted: String,
    val compressionRatio: String,
    val recordedAlertsCount: Int = 0,
    val uri: Uri?
)

/**
 * High-performance, streaming GZIP CAN logger with crash resilience and auto-recovery.
 * Compresses incoming CAN frames on-the-fly to minimize RAM and storage usage (~85% smaller).
 * Features frequent periodic flushing and automatic recovery of in-flight sessions if app is terminated.
 */
class LogRecorder {

    companion object {
        val EXCLUDED_LOG_IDS = emptySet<Int>()
        private const val FLUSH_INTERVAL_FRAMES = 500L

        @Volatile
        var activeRecorder: LogRecorder? = null

        /**
         * Emergency flush and save executed synchronously inside the global uncaught crash handler.
         */
        fun emergencySaveOnCrash(context: Context) {
            try {
                val recorder = activeRecorder
                recorder?.emergencyFlushAndClose()
                recoverOrphanedLogs(context)
            } catch (_: Exception) {}
        }

        /**
         * Recovers any in-flight unfinalized log files from cache and exports them to Downloads/OlaCAN.
         */
        fun recoverOrphanedLogs(context: Context) {
            try {
                val cacheFiles = listOf(context.cacheDir, pendingDirectory(context)).flatMap { it.listFiles().orEmpty().asList() }
                for (f in cacheFiles) {
                    if (f.name.startsWith("active_can_log_") && f.name.endsWith(".csv.gz")) {
                        if (f.length() > 0) { // Has actual CAN frame data
                            val timeTag = f.name.removePrefix("active_can_log_").removeSuffix(".csv.gz")
                            val targetName = "OlaCAN_Recovered_$timeTag.csv.gz"
                            if (saveFileToDownloadsStatic(context, f, targetName, "application/gzip") == null) continue
                            AppLogManager.info("LogRecorder", "Auto-recovered in-flight CAN log to Downloads/OlaCAN/$targetName (${f.length()} bytes)")
                        }
                        f.delete()
                    }
                }
            } catch (_: Exception) {}
        }

        private fun saveFileToDownloadsStatic(context: Context, sourceFile: File, targetName: String, mimeType: String): Uri? {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                var pending: Uri? = null
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, targetName)
                        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/OlaCAN")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    pending = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    if (pending != null) {
                        val output = resolver.openOutputStream(pending)
                            ?: error("Downloads could not open an output stream")
                        output.use { stream -> sourceFile.inputStream().use { it.copyTo(stream) } }
                        values.clear()
                        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        check(resolver.update(pending, values, null, null) == 1) {
                            "Downloads could not publish the recording"
                        }
                        return pending
                    }
                } catch (e: Exception) {
                    try { pending?.let { resolver.delete(it, null, null) } } catch (_: Exception) {}
                    AppLogManager.error("LogRecorder", "Downloads export failed", e)
                }
            } else {
                val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                try {
                    return Uri.fromFile(copyForExport(sourceFile, File(downloads, "OlaCAN"), targetName))
                } catch (e: Exception) {
                    AppLogManager.error("LogRecorder", "Downloads export failed", e)
                }
            }
            return try {
                Uri.fromFile(copyForExport(sourceFile, File(context.filesDir, "OlaCAN_Logs"), targetName))
            } catch (e: Exception) {
                AppLogManager.error("LogRecorder", "Could not export CAN log. Original kept for recovery.", e)
                null
            }
        }

        private fun pendingDirectory(context: Context) = File(context.filesDir, "OlaCAN_Pending")

        private fun copyForExport(source: File, directory: File, name: String): File {
            directory.mkdirs()
            val destination = File(directory, name)
            val temporary = File.createTempFile("ola-export-", ".tmp", directory)
            try {
                source.copyTo(temporary, overwrite = true)
                check(temporary.length() == source.length()) { "Incomplete log copy" }
                check(temporary.renameTo(destination)) { "Could not finalize log copy" }
                return destination
            } finally {
                temporary.delete()
            }
        }
    }

    private val isRecording = AtomicBoolean(false)
    private var tempFile: File? = null
    private var writeFailure: Exception? = null
    private var gzipWriter: BufferedWriter? = null

    private val frameCount = AtomicLong(0)
    private var startTimeMs: Long = 0L
    private val rawBytesEstimate = AtomicLong(0)
    private val sessionAlerts = Collections.synchronizedList(mutableListOf<VehicleAlert>())
    private var lastFlushTimeMs = 0L
    private val syncDownsampleCounters = ConcurrentHashMap<Int, AtomicLong>()

    fun isRecording(): Boolean = isRecording.get()
    fun getRecordedFrameCount(): Long = frameCount.get()
    fun getRecordedAlertsCount(): Int = sessionAlerts.size
    fun getDurationSeconds(): Long =
        if (startTimeMs > 0) (System.currentTimeMillis() - startTimeMs) / 1000 else 0L

    fun getTempFileSizeFormatted(): String {
        val f = tempFile ?: return "0 KB"
        val bytes = f.length()
        return when {
            bytes >= 1024 * 1024 -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    @Synchronized
    fun start(context: Context) {
        if (isRecording.get()) return

        try {
            // Recover any prior orphaned temp logs from crash/kill before starting new session
            recoverOrphanedLogs(context)

            val directory = pendingDirectory(context).apply { mkdirs() }
            val file = File.createTempFile("active_can_log_", ".csv.gz", directory)
            tempFile = file
            writeFailure = null
            frameCount.set(0)
            rawBytesEstimate.set(0)
            sessionAlerts.clear()
            syncDownsampleCounters.clear()
            startTimeMs = System.currentTimeMillis()
            lastFlushTimeMs = startTimeMs

            val fos = FileOutputStream(file)
            val bos = BufferedOutputStream(fos, 64 * 1024)
            val gzos = GZIPOutputStream(bos, true)
            val writer = BufferedWriter(OutputStreamWriter(gzos, StandardCharsets.UTF_8), 16 * 1024)

            gzipWriter = writer
            // Write CSV header and flush immediately so file has valid header structure
            val header = "timestamp_ms,id_hex,dlc,data_hex\n"
            writer.write(header)
            writer.flush()
            rawBytesEstimate.addAndGet(header.length.toLong())

            isRecording.set(true)
            activeRecorder = this
        } catch (e: Exception) {
            isRecording.set(false)
            try { gzipWriter?.close() } catch (_: Exception) {}
            gzipWriter = null
            tempFile = null
            activeRecorder = null
            AppLogManager.error("LogRecorder", "Failed to start CAN logging stream: ${e.message}", e)
        }
    }

    /**
     * Record a frame thread-safely. Formatted as compact CSV line:
     * timestamp_ms,id_hex,dlc,data_hex
     */
    fun recordFrame(frame: CanFrame) {
        if (!isRecording.get() || frame.id in EXCLUDED_LOG_IDS) return

        // Downsample 100 Hz MCU/VCU heartbeat sync frames (0x11C, 0x640)
        if (frame.id == 0x11C || frame.id == 0x640) {
            val syncCount = syncDownsampleCounters.getOrPut(frame.id) { AtomicLong(0) }
            if (syncCount.incrementAndGet() % 10L != 0L) {
                return
            }
        }

        val line = "${frame.timestampMs},${frame.idHex},${frame.dlc},${frame.compactDataHex}\n"

        synchronized(this) {
            if (isRecording.get() && writeFailure == null) {
                val writer = gzipWriter ?: return
                try {
                    writer.write(line)
                    val count = frameCount.incrementAndGet()
                    rawBytesEstimate.addAndGet(line.length.toLong())

                    // Frequent periodic flush (every 500 frames or 2 seconds) for crash durability
                    val now = System.currentTimeMillis()
                    if (count % FLUSH_INTERVAL_FRAMES == 0L || (now - lastFlushTimeMs) > 2000L) {
                        writer.flush()
                        lastFlushTimeMs = now
                    }
                } catch (e: Exception) {
                    writeFailure = e
                    AppLogManager.error("LogRecorder", "Could not write CAN recording", e)
                }
            }
        }
    }

    /**
     * Record an alert event thread-safely during active log.
     */
    fun recordAlert(alert: VehicleAlert) {
        if (!isRecording.get()) return
        val line = "${alert.timestampMs},ALERT,0,${alert.severity}:${alert.component}:${alert.id}\n"
        synchronized(this) {
            if (isRecording.get() && writeFailure == null) {
                val writer = gzipWriter ?: return
                try {
                    writer.write(line)
                    sessionAlerts.add(alert)
                    rawBytesEstimate.addAndGet(line.length.toLong())
                    writer.flush()
                } catch (e: Exception) {
                    writeFailure = e
                    AppLogManager.error("LogRecorder", "Could not write CAN recording", e)
                }
            }
        }
    }

    /**
     * Stops recording and saves the compressed .csv.gz file (and companion faults summary) into Downloads/OlaCAN.
     */
    suspend fun stop(context: Context): SavedLogInfo? = withContext(Dispatchers.IO) {
        synchronized(this@LogRecorder) { stopAndExport(context) }
    }

    private fun stopAndExport(context: Context): SavedLogInfo? {
        if (!isRecording.getAndSet(false)) return null
        activeRecorder = null
        val writer = gzipWriter
        gzipWriter = null
        writer?.close()
        writeFailure?.let { throw it }

        val file = tempFile ?: return null
        val duration = (System.currentTimeMillis() - startTimeMs) / 1000
        val count = frameCount.get()
        val alertsCount = sessionAlerts.size
        val compressedBytes = file.length()
        val rawBytes = rawBytesEstimate.get().coerceAtLeast(1L)
        val ratio = String.format(Locale.US, "%.1f%% saved", (1.0 - (compressedBytes.toDouble() / rawBytes.toDouble())) * 100.0)

        val timeTag = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(startTimeMs))
        val targetFileName = "OlaCAN_$timeTag.csv.gz"

        val uri = saveFileToDownloads(context, file, targetFileName, "application/gzip") ?: return null
        val finalPath = if (uri.scheme == "file") uri.path.orEmpty() else "Downloads/OlaCAN/$targetFileName"
        file.delete()
        tempFile = null

        // Save companion faults summary report if any alerts occurred during recording
        if (alertsCount > 0) {
            try {
                val faultsReport = buildString {
                    appendLine("==================================================")
                    appendLine("  OLA S1 CAN LOG - VEHICLE DIAGNOSTIC FAULTS")
                    appendLine("==================================================")
                    appendLine("Log Session Date : $timeTag")
                    appendLine("Duration         : ${duration}s")
                    appendLine("CAN Frames Logged: $count")
                    appendLine("Faults Captured  : $alertsCount")
                    appendLine("==================================================")
                    appendLine()
                    sessionAlerts.forEachIndexed { idx, a ->
                        val timeStr = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(a.timestampMs))
                        appendLine("#${idx + 1} [$timeStr] [${a.severity}] [${a.component}] ${a.title} (${a.sourceCanIdHex})")
                        appendLine("    Code       : ${a.id}")
                        appendLine("    Description: ${a.description}")
                        appendLine()
                    }
                }
                val reportFile = File(context.cacheDir, "OlaCAN_${timeTag}_faults.txt")
                reportFile.writeText(faultsReport, StandardCharsets.UTF_8)
                saveFileToDownloads(context, reportFile, "OlaCAN_${timeTag}_faults.txt", "text/plain")
                reportFile.delete()
            } catch (_: Exception) {}
        }

        val sizeFormatted = when {
            compressedBytes >= 1024 * 1024 -> String.format(Locale.US, "%.2f MB", compressedBytes / (1024.0 * 1024.0))
            compressedBytes >= 1024 -> String.format(Locale.US, "%.1f KB", compressedBytes / 1024.0)
            else -> "$compressedBytes Bytes"
        }

        return SavedLogInfo(
            fileName = targetFileName,
            filePath = finalPath,
            totalFrames = count,
            durationSeconds = duration,
            fileSizeFormatted = sizeFormatted,
            compressionRatio = ratio,
            recordedAlertsCount = alertsCount,
            uri = uri
        )
    }

    @Synchronized
    private fun emergencyFlushAndClose() {
        try {
            isRecording.set(false)
            gzipWriter?.flush()
            gzipWriter?.close()
        } catch (_: Exception) {}
        gzipWriter = null
    }

    private fun saveFileToDownloads(context: Context, sourceFile: File, targetName: String, mimeType: String): Uri? {
        return saveFileToDownloadsStatic(context, sourceFile, targetName, mimeType)
    }
}
