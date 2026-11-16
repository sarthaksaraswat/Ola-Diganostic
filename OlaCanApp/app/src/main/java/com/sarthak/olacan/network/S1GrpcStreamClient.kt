package com.sarthak.olacan.network

import android.content.Context
import android.util.Base64
import com.sarthak.olacan.model.CanFrame
import com.sarthak.olacan.model.EcuVersions
import io.grpc.CallOptions
import io.grpc.ClientCall
import io.grpc.ManagedChannel
import io.grpc.MethodDescriptor
import io.grpc.okhttp.OkHttpChannelBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern

/**
 * Direct high-performance gRPC client for MoveOS Root Diagnostics Service (TCP Port 50005).
 * Built with rock-solid socket management matching S1Mods for instant disconnect and reconnect.
 */
class S1GrpcStreamClient(
    val host: String,
    val port: Int = 50005,
    val scope: Int? = null
) {
    private var channel: ManagedChannel? = null
    @Volatile private var connectedPeer: InetAddress? = null
    private var activeStreamCall: ClientCall<ByteArray, ByteArray>? = null
    private val isClosedManually = AtomicBoolean(false)

    private object ByteArrayMarshaller : MethodDescriptor.Marshaller<ByteArray> {
        override fun stream(value: ByteArray): InputStream = ByteArrayInputStream(value)
        override fun parse(stream: InputStream): ByteArray = stream.readBytes()
    }

    private fun descriptor(method: String, type: MethodDescriptor.MethodType = MethodDescriptor.MethodType.UNARY): MethodDescriptor<ByteArray, ByteArray> =
        MethodDescriptor.newBuilder<ByteArray, ByteArray>()
            .setType(type)
            .setFullMethodName(MethodDescriptor.generateFullMethodName("wifican.WifiCanRpc", method))
            .setRequestMarshaller(ByteArrayMarshaller)
            .setResponseMarshaller(ByteArrayMarshaller)
            .build()

    private val executeMethodDescriptor = descriptor("ExecuteADBCommand")
    private val canStreamMethodDescriptor = descriptor("CanDumpStream", MethodDescriptor.MethodType.SERVER_STREAMING)

    /**
     * Normalizes and resolves the target host for OkHttpChannelBuilder.forAddress.
     * Follows S1Mods resolution pattern.
     */
    fun resolveTargetHost(): String {
        var h = NetworkScanner.normalizeHost(host)

        // Convert MAC to IPv6 if entered as MAC
        if (h.contains(":") && !h.contains("::") && h.split(":").size == 6) {
            NetworkScanner.macToIpv6(h)?.let { h = it }
        }

        // If fe80 link-local, resolve current active interface if missing or stale
        if (h.startsWith("fe80", ignoreCase = true)) {
            val raw = h.substringBefore("%")
            val existingScope = if (h.contains("%")) h.substringAfter("%") else null
            val activeIface = NetworkScanner.getAnyActiveWifiInterface() ?: "wlan0"
            val validIface = if (existingScope != null && try { java.net.NetworkInterface.getByName(existingScope)?.isUp == true } catch (_: Exception) { false }) {
                existingScope
            } else {
                activeIface
            }
            h = "$raw%$validIface"
        }

        return try {
            InetAddress.getByName(h).hostAddress ?: h
        } catch (_: Exception) {
            h
        }
    }

    /**
     * Connects and verifies channel responsiveness using OkHttpChannelBuilder.forAddress.
     * Cleanly shuts down any previous channel first so reconnects never collide.
     * Configures TCP HTTP/2 KeepAlive to detect zombie connections and Wi-Fi drops in real-time.
     */
    suspend fun connect(timeoutSeconds: Long = 5L): Boolean = withContext(Dispatchers.IO) {
        close()
        isClosedManually.set(false)

        val resolvedHost = resolveTargetHost()
        val peer = try { InetAddress.getByName(resolvedHost) } catch (_: java.net.UnknownHostException) { null }
        val targetHost = peer?.hostAddress ?: resolvedHost
        try {
            val ch = OkHttpChannelBuilder.forAddress(targetHost, port)
                .usePlaintext()
                .maxInboundMessageSize(64 * 1024 * 1024)
                .keepAliveTime(10, TimeUnit.SECONDS)
                .keepAliveTimeout(4, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true)
                .build()
            channel = ch

            // Quick connectivity verification using id -u (matching S1Mods)
            val (status, out) = executeRaw("id -u", timeoutSeconds)
            if (status in 0..1 || out.isNotBlank()) {
                connectedPeer = peer
                true
            } else {
                close()
                false
            }
        } catch (_: Exception) {
            close()
            false
        }
    }

    fun isOnLocalWifi(context: Context): Boolean =
        connectedPeer?.let { NetworkScanner.isOnWifiLink(context, it) } == true

    suspend fun executeRaw(command: String, timeoutSeconds: Long = 15L): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            val ch = channel ?: throw IllegalStateException("Channel not connected")
            val payload = ProtoUtils.buildCommandPayload(command)

            val call = ch.newCall(executeMethodDescriptor, CallOptions.DEFAULT.withDeadlineAfter(timeoutSeconds, TimeUnit.SECONDS))
            val resultHolder = CompletableResult<ByteArray>()
            activePendingResult = resultHolder

            call.start(object : ClientCall.Listener<ByteArray>() {
                override fun onMessage(message: ByteArray) {
                    resultHolder.complete(message)
                }

                override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                    if (!status.isOk && !resultHolder.isDone()) {
                        resultHolder.completeExceptionally(status.asRuntimeException())
                    }
                }
            }, io.grpc.Metadata())

            call.request(1)
            call.sendMessage(payload)
            call.halfClose()

            try {
                ProtoUtils.decodeResponse(resultHolder.await(timeoutSeconds * 1000L))
            } finally {
                activePendingResult = null
            }
        }

    suspend fun shell(command: String, timeoutSeconds: Long = 20L): Pair<Int, String> {
        val b64 = Base64.encodeToString(command.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        val wrapped = "sh -c echo\${IFS}$b64|base64\${IFS}-d|sh"
        return executeRaw(wrapped, timeoutSeconds)
    }

    suspend fun getSwVersion(): String = withContext(Dispatchers.IO) {
        try {
            val ch = channel ?: return@withContext ""
            val md = descriptor("GetSwVersion")
            val call = ch.newCall(md, CallOptions.DEFAULT.withDeadlineAfter(8L, TimeUnit.SECONDS))
            val resultHolder = CompletableResult<ByteArray>()

            call.start(object : ClientCall.Listener<ByteArray>() {
                override fun onMessage(message: ByteArray) {
                    resultHolder.complete(message)
                }

                override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                    if (!resultHolder.isDone()) {
                        if (!status.isOk) {
                            resultHolder.completeExceptionally(status.asRuntimeException())
                        } else {
                            resultHolder.complete(ByteArray(0))
                        }
                    }
                }
            }, io.grpc.Metadata())

            call.request(1)
            call.sendMessage(ByteArray(0))
            call.halfClose()

            val rawBytes = resultHolder.await(8000L)
            ProtoUtils.decodeResponse(rawBytes).second
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Checks if the detected MoveOS build meets baseline compatibility (MoveOS 4.0.5 and above).
     * Based on MoveOS compatibility verification logic in S1Mods 1.4.
     */
    fun isMoveOsSupported(versionStr: String): Boolean {
        val match = Regex("""(\d+)(?:\.(\d+))?(?:\.(\d+))?""").find(versionStr) ?: return false
        val major = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return false
        val minor = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
        val patch = match.groupValues.getOrNull(3)?.toIntOrNull() ?: 0

        return when {
            major > 4 -> true
            major == 4 -> if (minor > 0) true else patch >= 5
            else -> false
        }
    }

    /**
     * Reads the official vehicle model name (e.g. "S1 Pro Gen 2", "S1 Pro Gen 1", "S1 Air").
     * Queries MoveOS vehicle range preferences, falling back to Android build properties.
     */
    suspend fun fetchVehicleModel(): String = withContext(Dispatchers.IO) {
        try {
            val xml = shell("cat /data/data/com.olaelectric.vehiclerangeservice/shared_prefs/com.olaelectric.dataanalyser.xml", 8L).second
            val variant = Regex("""VEHICLE_VARIANT[^>]*>([^<]+)<""", RegexOption.IGNORE_CASE).find(xml)?.groupValues?.get(1)
                ?: Regex("""name="[^"]*VARIANT[^"]*">([^<]+)<""", RegexOption.IGNORE_CASE).find(xml)?.groupValues?.get(1)
            val raw = (variant ?: try { shell("getprop ro.product.model", 6L).second } catch (_: Exception) { "" }).trim()
            val clean = raw.replace("_", " ")
            val base = when {
                clean.contains("S1 Pro", ignoreCase = true) -> "S1 Pro"
                clean.contains("S1 Air", ignoreCase = true) -> "S1 Air"
                clean.contains("S1 X", ignoreCase = true) -> "S1 X"
                clean.contains("S1", ignoreCase = true) -> "S1"
                clean.isNotBlank() -> clean
                else -> "Ola S1"
            }
            val gen = Regex("""gen\s*(\d+)""", RegexOption.IGNORE_CASE).find(clean)?.groupValues?.get(1)
            if (gen != null && !base.contains("Gen", ignoreCase = true)) "$base Gen $gen" else base
        } catch (_: Exception) {
            "Ola S1"
        }
    }

    suspend fun fetchEcuVersions(): EcuVersions = withContext(Dispatchers.IO) {
        val versions = mutableMapOf<String, String>()
        try {
            // Broadened regex to capture MoveOS 4.0.5+ through 5.x releases
            val (_, logcat) = shell("logcat -d -v threadtime -t 5000 | grep -E 'VDP - V [0-9]|Firmware Version'", 12L)
            val p = Pattern.compile("(VCU Firmware Version|VCU Bootloader Version|BMS Firmware Version|BMS Bootloader Version|MCU Firmware Version|HU Firmware Version|HU Bootloader Version|BLE Version|HBC Version)\\s*:\\s*(.+)")
            for (line in logcat.lines()) {
                val m = p.matcher(line)
                if (m.find()) {
                    val key = m.group(1)?.trim() ?: ""
                    val value = m.group(2)?.trim() ?: ""
                    if (key.isNotBlank()) versions[key] = value
                }
            }
        } catch (_: Exception) {}

        val sw = getSwVersion()
        val model = fetchVehicleModel()
        val supported = isMoveOsSupported(sw)

        EcuVersions(
            vcuFirmware = versions["VCU Firmware Version"] ?: "",
            vcuBootloader = versions["VCU Bootloader Version"] ?: "",
            bmsFirmware = versions["BMS Firmware Version"] ?: "",
            bmsBootloader = versions["BMS Bootloader Version"] ?: "",
            mcuFirmware = versions["MCU Firmware Version"] ?: "",
            huFirmware = versions["HU Firmware Version"] ?: "",
            huBootloader = versions["HU Bootloader Version"] ?: "",
            bleVersion = versions["BLE Version"] ?: "",
            hbcVersion = versions["HBC Version"] ?: "",
            moveOsVersion = sw,
            modelName = model,
            isMoveOsSupported = supported
        )
    }

    /**
     * Starts listening to CanDumpStream (unfiltered vcan0 tap) asynchronously.
     * Uses ClientCall.cancel() on shutdown to send an immediate HTTP/2 RST_STREAM,
     * freeing the port 50005 socket on MoveOS instantly so reconnection never hangs.
     */
    fun startCanStream(
        onFrameReceived: (CanFrame) -> Unit,
        onStreamEnded: (String) -> Unit
    ) {
        stopCanStream()

        val ch = channel ?: run {
            onStreamEnded("Channel not connected")
            return
        }

        isClosedManually.set(false)

        val call = ch.newCall(canStreamMethodDescriptor, CallOptions.DEFAULT)
        activeStreamCall = call

        call.start(object : ClientCall.Listener<ByteArray>() {
            override fun onMessage(packetBytes: ByteArray) {
                try {
                    val frameBytes = ProtoUtils.extractCanFrameBytes(packetBytes)
                    if (frameBytes != null && frameBytes.size >= 16) {
                        val frame = CanFrame.parse(frameBytes)
                        if (frame != null) {
                            onFrameReceived(frame)
                        }
                    }
                } catch (e: Throwable) {
                    com.sarthak.olacan.util.AppLogManager.warn("GrpcClient", "Error processing incoming frame packet: ${e.message}")
                } finally {
                    try {
                        call.request(1)
                    } catch (_: Throwable) {}
                }
            }

            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                activeStreamCall = null
                if (!isClosedManually.get()) {
                    onStreamEnded(status.description ?: status.code.name)
                }
            }
        }, io.grpc.Metadata())

        call.request(1)
        call.sendMessage(ByteArray(0))
        call.halfClose()
    }

    fun stopCanStream() {
        isClosedManually.set(true)
        try {
            activeStreamCall?.cancel("User disconnected stream", null)
        } catch (_: Exception) {}
        activeStreamCall = null
    }

    private var activePendingResult: CompletableResult<*>? = null

    fun close() {
        connectedPeer = null
        isClosedManually.set(true)
        stopCanStream()
        activePendingResult?.cancel("Client channel closed")
        activePendingResult = null
        try {
            val ch = channel
            if (ch != null && !ch.isShutdown) {
                ch.shutdownNow()
            }
        } catch (_: Exception) {}
        channel = null
    }

    private class CompletableResult<T> {
        private val lock = Object()
        private var result: T? = null
        private var exception: Throwable? = null
        private var done = false

        fun cancel(reason: String) {
            synchronized(lock) {
                if (!done) {
                    exception = java.util.concurrent.CancellationException(reason)
                    done = true
                    lock.notifyAll()
                }
            }
        }

        fun complete(value: T) {
            synchronized(lock) {
                result = value
                done = true
                lock.notifyAll()
            }
        }

        fun completeExceptionally(e: Throwable) {
            synchronized(lock) {
                exception = e
                done = true
                lock.notifyAll()
            }
        }

        fun isDone(): Boolean = synchronized(lock) { done }

        fun await(timeoutMs: Long = 10000L): T {
            synchronized(lock) {
                val deadline = System.currentTimeMillis() + timeoutMs
                while (!done) {
                    val remaining = deadline - System.currentTimeMillis()
                    if (remaining <= 0) {
                        throw IllegalStateException("Call timed out after ${timeoutMs}ms")
                    }
                    lock.wait(remaining)
                }
                exception?.let { throw it }
                return result ?: throw IllegalStateException("Result is null")
            }
        }
    }
}
