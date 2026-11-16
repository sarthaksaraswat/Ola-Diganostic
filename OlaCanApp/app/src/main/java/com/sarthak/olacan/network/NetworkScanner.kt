package com.sarthak.olacan.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

data class DiscoveredScooter(
    val ip: String,                // Primary endpoint to connect (IPv6 link-local preferred for MoveOS bypass)
    val ipv4: String = "",          // Companion IPv4 address (e.g. "192.168.43.152" for display)
    val ipv6: String = "",          // Companion IPv6 address (e.g. "fe80::...%wlan0")
    val mac: String = "",           // Physical hardware MAC address (e.g. "3a:2b:7c:06:5d:a8")
    val scope: Int? = null,
    val isPortOpen: Boolean = false,
    val interfaceName: String = "",
    val source: String = "",
    val latencyMs: Long = 0L,
    val displayName: String = ""
)

object NetworkScanner {

    private var multicastLock: WifiManager.MulticastLock? = null

    private fun endpointAuthority(input: String): String {
        var authority = input.trim()
        for (prefix in listOf("http://", "https://", "grpc://", "ipv6:", "//")) {
            if (authority.startsWith(prefix, ignoreCase = true)) {
                authority = authority.substring(prefix.length).trim()
            }
        }
        return authority
    }

    fun normalizeHost(host: String): String {
        val authority = endpointAuthority(host)
        val bareHost = when {
            authority.startsWith("[") -> authority.substringAfter("[").substringBefore("]")
            authority.count { it == ':' } == 1 -> authority.substringBefore(":")
            else -> authority
        }
        return bareHost.replace("%25", "%").trim()
    }

    fun extractPort(host: String, defaultPort: Int = 50005): Int {
        val authority = endpointAuthority(host)
        val suffix = when {
            authority.startsWith("[") -> authority.substringAfter("]", "")
            authority.count { it == ':' } == 1 -> ":" + authority.substringAfter(":")
            else -> ""
        }
        require(suffix.isEmpty() || suffix.startsWith(":")) { "Use [IPv6]:port or hostname:port." }
        if (suffix.isEmpty()) return defaultPort
        return suffix.drop(1).toIntOrNull()?.takeIf { it in 1..65535 }
            ?: throw IllegalArgumentException("Port must be between 1 and 65535.")
    }

    fun formatEndpoint(host: String, port: Int): String {
        if (port == 50005) return host
        return if (host.contains(':')) "[$host]:$port" else "$host:$port"
    }

    /**
     * Converts MAC address (e.g. 3a:2b:7c:06:5d:a8) into SLAAC EUI-64 link-local IPv6 address (fe80::...).
     */
    fun macToIpv6(mac: String): String? {
        val clean = mac.replace("[:-]".toRegex(), "").trim()
        if (clean.length != 12) return null
        return try {
            val bytes = clean.chunked(2).map { it.toInt(16) }
            val b0 = (bytes[0] xor 0x02).toString(16).padStart(2, '0')
            val b1 = bytes[1].toString(16).padStart(2, '0')
            val b2 = bytes[2].toString(16).padStart(2, '0')
            val b3 = bytes[3].toString(16).padStart(2, '0')
            val b4 = bytes[4].toString(16).padStart(2, '0')
            val b5 = bytes[5].toString(16).padStart(2, '0')
            "fe80::$b0$b1:${b2}ff:fe$b3:$b4$b5"
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Reconstructs physical hardware MAC address from a SLAAC EUI-64 link-local IPv6 address.
     * Fully supports compressed ("::") and expanded IPv6 notations.
     */
    fun extractMacFromEui64(ipv6: String): String? {
        return try {
            val clean = ipv6.substringBefore("%").trim('[', ']').lowercase()
            if (!clean.startsWith("fe80")) return null

            val parts = clean.split(":")
            val expanded = if (clean.contains("::")) {
                val before = clean.substringBefore("::").split(":").filter { it.isNotEmpty() }
                val after = clean.substringAfter("::").split(":").filter { it.isNotEmpty() }
                val missing = 8 - (before.size + after.size)
                before + List(missing) { "0" } + after
            } else {
                parts
            }

            if (expanded.size != 8) return null

            // Interface identifier resides in the last 4 groups (expanded[4..7])
            val g4 = expanded[4].padStart(4, '0')
            val g5 = expanded[5].padStart(4, '0')
            val g6 = expanded[6].padStart(4, '0')
            val g7 = expanded[7].padStart(4, '0')

            val b0 = g4.substring(0, 2).toInt(16) xor 0x02
            val b1 = g4.substring(2, 4).toInt(16)
            val b2 = g5.substring(0, 2).toInt(16)
            val midFf = g5.substring(2, 4).toInt(16)
            val midFe = g6.substring(0, 2).toInt(16)
            val b3 = g6.substring(2, 4).toInt(16)
            val b4 = g7.substring(0, 2).toInt(16)
            val b5 = g7.substring(2, 4).toInt(16)

            if (midFf == 0xFF && midFe == 0xFE) {
                String.format(java.util.Locale.US, "%02x:%02x:%02x:%02x:%02x:%02x", b0, b1, b2, b3, b4, b5)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    fun findMacForIp(ip: String): String {
        val norm = normalizeHost(ip)
        for (dev in scanArpFile()) {
            if (normalizeHost(dev.ip) == norm && dev.mac.isNotBlank()) return dev.mac
        }
        for (dev in scanIpNeigh()) {
            if (normalizeHost(dev.ip) == norm && dev.mac.isNotBlank()) return dev.mac
        }
        return ""
    }

    fun testPort(ip: String, port: Int = 50005, scope: Int? = null, timeoutMs: Int = 750): Boolean {
        if (ip.isBlank()) return false
        val trimmed = ip.trim()
        val rawIp = trimmed.split("%")[0].trim('[', ']')
        val explicitScope = if (trimmed.contains("%")) trimmed.substringAfter("%").trim() else scope?.toString()

        // 1. Try with explicit scope if present
        if (!explicitScope.isNullOrBlank()) {
            try {
                val hostWithScope = "$rawIp%$explicitScope"
                val address = InetAddress.getByName(hostWithScope)
                Socket().use { socket ->
                    socket.tcpNoDelay = true
                    socket.connect(InetSocketAddress(address, port), timeoutMs)
                    return true
                }
            } catch (_: Exception) {}
        }

        // 2. Try raw IP directly (IPv4 or global IPv6)
        try {
            val address = InetAddress.getByName(rawIp)
            Socket().use { socket ->
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(address, port), timeoutMs)
                return true
            }
        } catch (_: Exception) {}

        // 3. If IPv6 link-local (fe80::), test active Wi-Fi and Hotspot interfaces
        if (rawIp.startsWith("fe80", ignoreCase = true)) {
            val ifaces = try {
                val list = mutableListOf<String>()
                val netIfaces = NetworkInterface.getNetworkInterfaces()
                while (netIfaces != null && netIfaces.hasMoreElements()) {
                    val iface = netIfaces.nextElement()
                    if (iface.isUp && !iface.isLoopback) {
                        val n = iface.name
                        if (n.startsWith("ap") || n.startsWith("wlan") || n.startsWith("swlan") || n.startsWith("p2p") || n.startsWith("softap")) {
                            list.add(n)
                        }
                    }
                }
                list
            } catch (_: Exception) {
                listOf("ap0", "wlan0", "swlan0")
            }

            for (iface in ifaces) {
                if (iface == explicitScope) continue
                try {
                    val address = InetAddress.getByName("$rawIp%$iface")
                    Socket().use { socket ->
                        socket.tcpNoDelay = true
                        socket.connect(InetSocketAddress(address, port), timeoutMs)
                        return true
                    }
                } catch (_: Exception) {}
            }
        }

        return false
    }

    /**
     * Resolves the exact working target host string and scope index for a given IP/host.
     * If an IPv6 link-local address had a stale interface or no interface, probes active interfaces
     * to find the one where TCP port 50005 is actually open.
     */
    fun findWorkingTarget(ip: String, port: Int = 50005, scope: Int? = null, timeoutMs: Int = 750): Pair<String, Int?>? {
        if (ip.isBlank()) return null
        val trimmed = ip.trim()
        val rawIp = trimmed.split("%")[0].trim('[', ']')
        val explicitScope = if (trimmed.contains("%")) trimmed.substringAfter("%").trim() else scope?.toString()

        // 1. Try with explicit scope if present
        if (!explicitScope.isNullOrBlank()) {
            try {
                val hostWithScope = "$rawIp%$explicitScope"
                val address = InetAddress.getByName(hostWithScope)
                Socket().use { socket ->
                    socket.tcpNoDelay = true
                    socket.connect(InetSocketAddress(address, port), timeoutMs)
                    val scopeIdx = explicitScope.toIntOrNull() ?: NetworkInterface.getByName(explicitScope)?.index
                    return Pair(hostWithScope, scopeIdx)
                }
            } catch (_: Exception) {}
        }

        // 2. Try raw IP directly (IPv4 or global IPv6)
        if (!rawIp.startsWith("fe80", ignoreCase = true)) {
            try {
                val address = InetAddress.getByName(rawIp)
                Socket().use { socket ->
                    socket.tcpNoDelay = true
                    socket.connect(InetSocketAddress(address, port), timeoutMs)
                    return Pair(rawIp, null)
                }
            } catch (_: Exception) {}
        }

        // 3. If IPv6 link-local (fe80::), test active Wi-Fi and Hotspot interfaces
        if (rawIp.startsWith("fe80", ignoreCase = true)) {
            val ifaces = try {
                val list = mutableListOf<String>()
                val netIfaces = NetworkInterface.getNetworkInterfaces()
                while (netIfaces != null && netIfaces.hasMoreElements()) {
                    val iface = netIfaces.nextElement()
                    if (iface.isUp && !iface.isLoopback) {
                        val n = iface.name
                        if (n.startsWith("ap") || n.startsWith("wlan") || n.startsWith("swlan") || n.startsWith("p2p") || n.startsWith("softap")) {
                            list.add(n)
                        }
                    }
                }
                list
            } catch (_: Exception) {
                listOf("ap0", "wlan0", "swlan0")
            }

            for (iface in ifaces) {
                if (iface == explicitScope) continue
                try {
                    val hostWithScope = "$rawIp%$iface"
                    val address = InetAddress.getByName(hostWithScope)
                    Socket().use { socket ->
                        socket.tcpNoDelay = true
                        socket.connect(InetSocketAddress(address, port), timeoutMs)
                        val scopeIdx = NetworkInterface.getByName(iface)?.index
                        return Pair(hostWithScope, scopeIdx)
                    }
                } catch (_: Exception) {}
            }
        }

        return null
    }

    fun getWifiGatewayIp(context: Context): String? {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val dhcp = wm?.dhcpInfo ?: return null
            val gateway = dhcp.gateway
            if (gateway != 0) {
                String.format(
                    java.util.Locale.US,
                    "%d.%d.%d.%d",
                    gateway and 0xFF,
                    (gateway shr 8) and 0xFF,
                    (gateway shr 16) and 0xFF,
                    (gateway shr 24) and 0xFF
                )
            } else null
        } catch (_: Exception) {
            null
        }
    }

    fun getPhoneWifiIp(context: Context): String? {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ip = wm?.connectionInfo?.ipAddress ?: return null
            if (ip != 0) {
                String.format(
                    java.util.Locale.US,
                    "%d.%d.%d.%d",
                    ip and 0xFF,
                    (ip shr 8) and 0xFF,
                    (ip shr 16) and 0xFF,
                    (ip shr 24) and 0xFF
                )
            } else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Inspects active network interfaces (Wi-Fi STA, Hotspot AP, USB tethering)
     * to return all current site-local IPv4 addresses assigned to the phone.
     */
    fun getActiveLocalIpv4Addresses(): List<String> {
        val ips = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces != null && interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isUp && !iface.isLoopback) {
                    val addrs = iface.inetAddresses
                    while (addrs.hasMoreElements()) {
                        val addr = addrs.nextElement()
                        if (addr is java.net.Inet4Address && !addr.isLoopbackAddress && addr.isSiteLocalAddress) {
                            val hostAddr = addr.hostAddress ?: ""
                            if (hostAddr.isNotBlank() && hostAddr != "127.0.0.1" && !ips.contains(hostAddr)) {
                                ips.add(hostAddr)
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return ips
    }

    /**
     * Resolves the primary local IP address of the phone, whether connected to Wi-Fi
     * or operating as a Mobile Hotspot AP (e.g. 192.168.43.1).
     */
    fun getPhoneLocalIp(context: Context): String? {
        val wifiIp = getPhoneWifiIp(context)
        if (!wifiIp.isNullOrBlank() && wifiIp != "0.0.0.0") return wifiIp
        return getActiveLocalIpv4Addresses().firstOrNull()
    }

    fun getAnyActiveWifiInterface(): String? {
        try {
            val netIfaces = NetworkInterface.getNetworkInterfaces()
            while (netIfaces != null && netIfaces.hasMoreElements()) {
                val iface = netIfaces.nextElement()
                if (iface.isUp && !iface.isLoopback) {
                    val name = iface.name
                    if (name.startsWith("ap") || name.startsWith("wlan") ||
                        name.startsWith("swlan") || name.startsWith("p2p") ||
                        name.startsWith("softap")
                    ) {
                        return name
                    }
                }
            }
        } catch (_: Exception) {}
        return "wlan0"
    }

    fun getActiveWifiInterfaces(context: Context): Set<String> {
        val ifaces = mutableSetOf<String>()
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            cm?.allNetworks?.forEach { network ->
                val caps = cm.getNetworkCapabilities(network) ?: return@forEach
                val link = cm.getLinkProperties(network) ?: return@forEach
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    link.interfaceName?.let { ifaces.add(it) }
                }
            }
        } catch (_: Exception) {}

        try {
            val netIfaces = NetworkInterface.getNetworkInterfaces()
            while (netIfaces != null && netIfaces.hasMoreElements()) {
                val iface = netIfaces.nextElement()
                if (iface.isUp && !iface.isLoopback) {
                    val name = iface.name
                    if (name.startsWith("ap") || name.startsWith("wlan") ||
                        name.startsWith("swlan") || name.startsWith("p2p") ||
                        name.startsWith("softap")
                    ) {
                        ifaces.add(name)
                    }
                }
            }
        } catch (_: Exception) {}

        ifaces.addAll(listOf("ap0", "wlan0", "swlan0", "wlan1"))
        return ifaces
    }

    internal fun sharesNetworkPrefix(peer: InetAddress, local: InetAddress, prefixLength: Int): Boolean {
        val peerBytes = peer.address
        val localBytes = local.address
        if (peerBytes.size != localBytes.size || prefixLength !in 1..(localBytes.size * 8)) return false

        val wholeBytes = prefixLength / 8
        for (index in 0 until wholeBytes) {
            if (peerBytes[index] != localBytes[index]) return false
        }

        val remainingBits = prefixLength % 8
        if (remainingBits == 0) return true
        val mask = 0xFF shl (8 - remainingBits)
        return ((peerBytes[wholeBytes].toInt() xor localBytes[wholeBytes].toInt()) and mask) == 0
    }

    /** Checks the resolved connection address against active Wi-Fi and hotspot links. */
    fun isOnWifiLink(context: Context, peer: InetAddress): Boolean {
        if (peer.isLoopbackAddress || peer.isAnyLocalAddress || peer.isMulticastAddress) return false

        return getActiveWifiInterfaces(context).any { name ->
            try {
                val wifi = NetworkInterface.getByName(name) ?: return@any false
                if (!wifi.isUp || wifi.isLoopback || wifi.isPointToPoint) return@any false
                if (peer is java.net.Inet6Address && peer.isLinkLocalAddress && peer.scopeId != wifi.index) {
                    return@any false
                }
                wifi.interfaceAddresses.any { link ->
                    val local = link.address
                    peer != local && peer != link.broadcast &&
                        sharesNetworkPrefix(peer, local, link.networkPrefixLength.toInt())
                }
            } catch (_: java.net.SocketException) {
                false
            } catch (_: SecurityException) {
                false
            }
        }
    }

    /**
     * Resolves whatever host the user typed or had saved into the best connection target.
     * Uses direct resolution without raw socket probing on port 50005 (matching S1Mods pattern)
     * so that MoveOS's single-connection daemon is never reset or knocked into TIME_WAIT.
     */
    fun resolveBestHostForConnect(context: Context?, inputHost: String): Pair<String, Int?> {
        var clean = normalizeHost(inputHost)
        if (clean.isBlank()) return Pair("", null)

        // 1. If it's a MAC address:
        if (clean.contains(":") && !clean.contains("::") && clean.split(":").size == 6) {
            val derived = macToIpv6(clean)
            if (derived != null) {
                clean = derived
            }
        }

        // 2. If it's an IPv6 link-local address:
        if (clean.startsWith("fe80", ignoreCase = true)) {
            val rawIp = clean.substringBefore("%")
            val existingScope = if (clean.contains("%")) clean.substringAfter("%") else null
            val ifaceName = when {
                existingScope != null && existingScope.toIntOrNull() == null && NetworkInterface.getByName(existingScope) != null -> existingScope
                context != null -> getActiveWifiInterfaces(context).firstOrNull() ?: "wlan0"
                else -> "wlan0"
            }
            val target = "$rawIp%$ifaceName"
            val scopeIndex = NetworkInterface.getByName(ifaceName)?.index ?: existingScope?.toIntOrNull()
            return Pair(target, scopeIndex)
        }

        // 3. IPv4 address (e.g. 192.168.43.152 or 192.168.1.134) or IPv6 global address:
        // Connect directly over gRPC as S1Mods does, without pre-socket port probing!
        return Pair(clean, null)
    }

    /**
     * Rapid (< 400ms) multicast ping to detect any active MoveOS scooter on link-local IPv6.
     * Useful for instant auto-healing if direct IPv4 connection gets dropped by MoveOS firewall.
     */
    suspend fun quickFindIpv6Scooter(context: Context): DiscoveredScooter? = withContext(Dispatchers.IO) {
        acquireMulticastLock(context)
        try {
            val ifaces = getActiveWifiInterfaces(context)
            for (iface in ifaces) {
                val output = runMulticastPing(iface)
                val neighbors = parsePingOutput(output, iface)
                for (scooter in neighbors) {
                    if (testPort(scooter.ip, 50005, scooter.scope, 750)) {
                        val mac = scooter.mac.ifBlank { extractMacFromEui64(scooter.ip) ?: "" }
                        return@withContext scooter.copy(
                            mac = mac,
                            ipv6 = scooter.ip,
                            isPortOpen = true
                        )
                    }
                }
            }
        } finally {
            releaseMulticastLock()
        }
        null
    }

    /**
     * Scans for ALL Ola scooters active on the local network (whether on the same Wi-Fi router
     * or connected as clients to this phone's mobile hotspot).
     * Deduplicates strictly by physical vehicle identity so each physical scooter appears
     * exactly once, uniting its companion IPv4 and IPv6 addresses and preferring IPv6 for gRPC.
     */
    suspend fun scanForAllScooters(context: Context): List<DiscoveredScooter> = withContext(Dispatchers.IO) {
        val scootersList = mutableListOf<DiscoveredScooter>()

        fun tryAddScooter(candidate: DiscoveredScooter) {
            val normIp = normalizeHost(candidate.ip)
            val isCandidateIpv6 = candidate.ip.contains(":") || candidate.ipv6.isNotBlank()
            val candidateMac = candidate.mac.ifBlank {
                if (candidate.ip.contains(":")) extractMacFromEui64(candidate.ip) ?: ""
                else findMacForIp(candidate.ip)
            }.lowercase()

            val start = System.currentTimeMillis()
            val portOpen = candidate.isPortOpen || testPort(candidate.ip, 50005, candidate.scope, 750)
            if (!portOpen) return
            val latency = (System.currentTimeMillis() - start).coerceAtLeast(1L)

            // Deduplicate: Check if an existing vehicle matches by MAC, IP, or companion addresses
            val existingIndex = scootersList.indexOfFirst { existing ->
                if (candidateMac.isNotBlank() && existing.mac.isNotBlank() && existing.mac.equals(candidateMac, ignoreCase = true)) {
                    return@indexOfFirst true
                }
                if (normalizeHost(existing.ip).equals(normIp, ignoreCase = true)) {
                    return@indexOfFirst true
                }
                if (existing.ipv4.isNotBlank() && !isCandidateIpv6 && existing.ipv4 == normIp) {
                    return@indexOfFirst true
                }
                if (existing.ipv6.isNotBlank() && isCandidateIpv6 && normalizeHost(existing.ipv6).equals(normIp, ignoreCase = true)) {
                    return@indexOfFirst true
                }
                false
            }

            if (existingIndex != -1) {
                val existing = scootersList[existingIndex]
                val mergedIpv6 = when {
                    isCandidateIpv6 -> candidate.ip
                    existing.ipv6.isNotBlank() -> existing.ipv6
                    existing.ip.contains(":") -> existing.ip
                    candidateMac.isNotBlank() -> macToIpv6(candidateMac) ?: ""
                    else -> ""
                }
                val mergedIpv4 = when {
                    !isCandidateIpv6 -> candidate.ip
                    existing.ipv4.isNotBlank() -> existing.ipv4
                    !existing.ip.contains(":") -> existing.ip
                    else -> ""
                }
                // ALWAYS prefer IPv6 link-local as primary gRPC target because MoveOS customer mode blocks IPv4
                val primaryTarget = if (mergedIpv6.isNotBlank()) mergedIpv6 else (mergedIpv4.ifBlank { candidate.ip })

                scootersList[existingIndex] = existing.copy(
                    ip = primaryTarget,
                    ipv4 = mergedIpv4,
                    ipv6 = mergedIpv6,
                    mac = if (existing.mac.isNotBlank()) existing.mac else candidateMac,
                    isPortOpen = true,
                    latencyMs = minOf(existing.latencyMs.takeIf { it > 0 } ?: latency, latency),
                    interfaceName = candidate.interfaceName.ifBlank { existing.interfaceName },
                    scope = candidate.scope ?: existing.scope
                )
                return
            }

            // New physical scooter entry
            val targetIpv6 = if (isCandidateIpv6) candidate.ip else (if (candidateMac.isNotBlank()) macToIpv6(candidateMac) ?: "" else "")
            val targetIpv4 = if (!isCandidateIpv6) candidate.ip else ""
            val primaryTarget = if (targetIpv6.isNotBlank()) targetIpv6 else candidate.ip

            scootersList.add(
                candidate.copy(
                    ip = primaryTarget,
                    ipv4 = targetIpv4,
                    ipv6 = targetIpv6,
                    mac = candidateMac,
                    isPortOpen = true,
                    latencyMs = latency
                )
            )
        }

        val localIps = getActiveLocalIpv4Addresses()
        val phoneIp = getPhoneLocalIp(context)
        val allPhoneIps = (listOfNotNull(phoneIp) + localIps).toSet()

        // TIER 1: Instant Link-Local IPv6 Multicast Discovery (< 350ms)
        // MoveOS responds to IPv6 multicast ping immediately, and port 50005 is NEVER blocked over IPv6.
        acquireMulticastLock(context)
        try {
            val ifaces = getActiveWifiInterfaces(context)
            for (iface in ifaces) {
                val output = runMulticastPing(iface)
                val neighbors = parsePingOutput(output, iface)
                for (scooter in neighbors) {
                    tryAddScooter(scooter)
                }
            }
        } finally {
            releaseMulticastLock()
        }

        // If IPv6 scooter(s) were found on port 50005, return immediately (matching S1Mods)!
        // This avoids duplicate entries caused by sweeping IPv4 addresses when ARP cannot resolve MAC.
        if (scootersList.isNotEmpty()) {
            return@withContext scootersList
        }

        // TIER 2: Match Connected Hotspot Clients in ARP & Neighbor table (< 50ms)
        // When a scooter joins the phone's hotspot or Wi-Fi, the kernel logs its dynamic DHCP IP and MAC.
        val arpDevices = scanArpFile().filter { it.ip !in allPhoneIps }
        for (dev in arpDevices) {
            tryAddScooter(dev.copy(source = "Hotspot Client (ARP)"))
        }

        val ipNeighs = scanIpNeigh().filter { it.ip !in allPhoneIps }
        for (dev in ipNeighs) {
            tryAddScooter(dev.copy(source = "Hotspot Client (Neigh)"))
        }

        // TIER 3: Probe Wi-Fi Gateway & MoveOS Factory Diagnostic IP (< 150ms)
        val gateway = getWifiGatewayIp(context)
        if (!gateway.isNullOrBlank() && gateway != "0.0.0.0" && gateway !in allPhoneIps) {
            tryAddScooter(DiscoveredScooter(ip = gateway, source = "Wi-Fi Gateway"))
        }

        if ("192.168.1.134" !in allPhoneIps) {
            tryAddScooter(DiscoveredScooter(ip = "192.168.1.134", source = "MoveOS Default"))
        }

        // TIER 4: Complete Dynamic Subnet Sweep across active phone subnets (< 1.2s)
        // Runs if no scooter has been found or if companion IPv4 has not been resolved yet
        if (scootersList.isEmpty() || scootersList.any { it.ipv4.isBlank() }) {
            val prefixes = (allPhoneIps + listOfNotNull(gateway)).mapNotNull { ip ->
                if (ip.contains(".")) ip.substringBeforeLast(".") + "." else null
            }.distinct().toMutableList()

            if (prefixes.isEmpty()) {
                prefixes.addAll(listOf("192.168.43.", "192.168.1.", "172.20.10.", "192.168.49."))
            }

            for (prefix in prefixes) {
                val dhcpPool = (100..200).map { "$prefix$it" }
                val lowerPool = (2..99).map { "$prefix$it" }
                val upperPool = (201..254).map { "$prefix$it" }
                val allCandidates = (dhcpPool + lowerPool + upperPool)
                    .filter { it !in allPhoneIps && it != gateway && it != "192.168.1.134" }

                for (chunk in allCandidates.chunked(32)) {
                    val defs = chunk.map { ip ->
                        async {
                            val start = System.currentTimeMillis()
                            if (testPort(ip, 50005, null, 200)) {
                                val lat = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                                val mac = findMacForIp(ip)
                                DiscoveredScooter(
                                    ip = ip,
                                    mac = mac,
                                    isPortOpen = true,
                                    latencyMs = lat,
                                    source = "Subnet Sweep"
                                )
                            } else null
                        }
                    }
                    defs.mapNotNull { it.await() }.forEach { hit ->
                        tryAddScooter(hit)
                    }
                }
            }
        }

        scootersList.mapIndexed { index, scooter ->
            val num = index + 1
            val resolvedName = com.sarthak.olacan.util.VehicleNameManager.resolveVehicleName(
                context = context,
                hostOrIp = scooter.ip,
                ipv4 = scooter.ipv4.ifBlank { null },
                mac = scooter.mac.ifBlank { null },
                fallbackIndex = num
            )
            scooter.copy(
                displayName = resolvedName
            )
        }
    }

    suspend fun scanForScooter(context: Context): DiscoveredScooter? = withContext(Dispatchers.IO) {
        scanForAllScooters(context).firstOrNull()
    }

    private fun acquireMulticastLock(context: Context) {
        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("OlaCanMulticastLock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (_: Exception) {}
    }

    private fun releaseMulticastLock() {
        try {
            multicastLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {}
        multicastLock = null
    }

    private fun runMulticastPing(iface: String): String {
        return try {
            val process = ProcessBuilder("ping6", "-c", "2", "-w", "2", "ff02::1%$iface")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            process.waitFor()
            output
        } catch (_: Exception) {
            try {
                val process = ProcessBuilder("ping", "-6", "-c", "2", "-W", "2", "ff02::1%$iface")
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
                process.waitFor()
                output
            } catch (_: Exception) {
                ""
            }
        }
    }

    private fun parsePingOutput(output: String, fallbackIface: String): List<DiscoveredScooter> {
        val neighbors = mutableListOf<DiscoveredScooter>()
        if (output.isBlank()) return neighbors

        for (line in output.lines()) {
            val trimmed = line.trim()
            if (trimmed.contains("bytes from", ignoreCase = true)) {
                try {
                    val afterFrom = trimmed.substringAfter("from ", "").trim()
                    val ipToken = afterFrom.split("\\s+".toRegex()).firstOrNull() ?: ""
                    val cleanToken = ipToken.removeSuffix(":").trim()

                    if (cleanToken.isNotBlank() && cleanToken.contains(":")) {
                        val rawIp = cleanToken.substringBefore("%").trim()
                        val iface = if (cleanToken.contains("%")) cleanToken.substringAfter("%").trim() else fallbackIface
                        if (rawIp.startsWith("fe80", ignoreCase = true)) {
                            val target = if (iface.isNotBlank()) "$rawIp%$iface" else rawIp
                            val scopeIndex = NetworkInterface.getByName(iface)?.index
                            val mac = extractMacFromEui64(target) ?: ""
                            neighbors.add(
                                DiscoveredScooter(
                                    ip = target,
                                    ipv6 = target,
                                    mac = mac,
                                    scope = scopeIndex,
                                    interfaceName = iface,
                                    source = "multicast_ping"
                                )
                            )
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        return neighbors
    }

    private fun scanIpNeigh(): List<DiscoveredScooter> {
        val result = mutableListOf<DiscoveredScooter>()
        try {
            val process = Runtime.getRuntime().exec("ip neigh")
            BufferedReader(InputStreamReader(process.inputStream)).useLines { lines ->
                lines.forEach { line ->
                    val parts = line.split("\\s+".toRegex())
                    if (parts.size >= 4) {
                        val ip = parts[0]
                        val devIdx = parts.indexOf("dev")
                        val ifaceName = if (devIdx != -1 && devIdx + 1 < parts.size) parts[devIdx + 1] else ""
                        val scope = NetworkInterface.getByName(ifaceName)?.index

                        val lladdrIdx = parts.indexOf("lladdr")
                        val mac = if (lladdrIdx != -1 && lladdrIdx + 1 < parts.size) parts[lladdrIdx + 1].trim() else ""

                        val isIpv6 = ip.contains(":")
                        result.add(
                            DiscoveredScooter(
                                ip = ip,
                                ipv4 = if (!isIpv6) ip else "",
                                ipv6 = if (isIpv6) ip else "",
                                mac = mac,
                                scope = scope,
                                interfaceName = ifaceName,
                                source = "ip_neigh"
                            )
                        )

                        if (mac.isNotBlank() && !isIpv6) {
                            val derivedIpv6 = macToIpv6(mac)
                            if (derivedIpv6 != null) {
                                val target = if (ifaceName.isNotBlank()) "$derivedIpv6%$ifaceName" else derivedIpv6
                                result.add(
                                    DiscoveredScooter(
                                        ip = target,
                                        ipv4 = ip,
                                        ipv6 = target,
                                        mac = mac,
                                        scope = scope,
                                        interfaceName = ifaceName,
                                        source = "mac_slaac_neigh"
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return result
    }

    private fun scanArpFile(): List<DiscoveredScooter> {
        val result = mutableListOf<DiscoveredScooter>()
        try {
            val file = java.io.File("/proc/net/arp")
            if (file.exists() && file.canRead()) {
                file.forEachLine { line ->
                    val parts = line.split("\\s+".toRegex())
                    if (parts.size >= 4 && parts[0] != "IP" && parts[0].contains(".")) {
                        val ip = parts[0]
                        val mac = if (parts.size >= 4 && parts[3] != "00:00:00:00:00:00") parts[3].trim() else ""
                        val iface = if (parts.size >= 6) parts[5].trim() else ""
                        result.add(
                            DiscoveredScooter(
                                ip = ip,
                                ipv4 = ip,
                                mac = mac,
                                interfaceName = iface,
                                source = "proc_arp"
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}
        return result
    }
}
