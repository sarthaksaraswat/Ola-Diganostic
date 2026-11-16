package com.sarthak.olacan.util

import android.content.Context
import android.content.SharedPreferences
import com.sarthak.olacan.network.NetworkScanner
import java.util.Locale

object VehicleNameManager {

    private const val PREFS_NAME = "vehicle_nicknames_prefs"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun cleanKey(key: String): String {
        return key.trim().lowercase(Locale.ROOT)
            .replace("%wlan0", "")
            .replace("%eth0", "")
            .replace("%25wlan0", "")
            .trim('[', ']')
    }

    fun getCustomName(context: Context, key: String): String? {
        if (key.isBlank()) return null
        val ck = cleanKey(key)
        val name = getPrefs(context).getString(ck, null)
        return if (!name.isNullOrBlank()) name.trim() else null
    }

    fun setCustomName(context: Context, key: String, name: String) {
        if (key.isBlank()) return
        val ck = cleanKey(key)
        val trimmed = name.trim()
        val editor = getPrefs(context).edit()
        if (trimmed.isEmpty()) {
            editor.remove(ck)
        } else {
            editor.putString(ck, trimmed)
        }
        editor.apply()
    }

    /**
     * Resolves a human-friendly vehicle name for any host, MAC, or IP.
     * Priority:
     * 1. User custom nickname for this MAC
     * 2. User custom nickname for this IPv4
     * 3. User custom nickname for this IPv6
     * 4. Smart default with IPv4 (e.g. "Ola S1 (192.168.0.4)")
     * 5. Smart default with MAC short suffix (e.g. "Ola S1 [68:AF]")
     */
    fun resolveVehicleName(
        context: Context,
        hostOrIp: String,
        ipv4: String? = null,
        mac: String? = null,
        fallbackIndex: Int = 1
    ): String {
        // 1. Check custom name by MAC
        if (!mac.isNullOrBlank()) {
            val custom = getCustomName(context, mac)
            if (!custom.isNullOrBlank()) return custom
        }

        // 2. Check custom name by IPv4
        if (!ipv4.isNullOrBlank()) {
            val custom = getCustomName(context, ipv4)
            if (!custom.isNullOrBlank()) return custom
        }

        // 3. Check custom name by raw host/IP
        if (hostOrIp.isNotBlank()) {
            val custom = getCustomName(context, hostOrIp)
            if (!custom.isNullOrBlank()) return custom
        }

        // 4. Try resolving MAC from ARP cache or SLAAC EUI-64 if IPv6
        val resolvedMac = if (!mac.isNullOrBlank()) mac else {
            val fromArp = try { NetworkScanner.findMacForIp(hostOrIp) } catch (_: Exception) { "" }
            if (fromArp.isNotBlank()) fromArp else NetworkScanner.extractMacFromEui64(hostOrIp)
        }
        if (!resolvedMac.isNullOrBlank()) {
            val custom = getCustomName(context, resolvedMac)
            if (!custom.isNullOrBlank()) {
                val ipDisplay = if (!ipv4.isNullOrBlank()) ipv4 else if (!hostOrIp.contains(":")) hostOrIp else null
                return if (ipDisplay != null && !custom.contains(ipDisplay)) "$custom ($ipDisplay)" else custom
            }
        }

        // 5. Generate smart recognizable label
        val shortMac = if (!resolvedMac.isNullOrBlank()) {
            val parts = resolvedMac.split(":")
            if (parts.size >= 2) "${parts[parts.size - 2]}:${parts.last()}".uppercase(Locale.ROOT) else resolvedMac.uppercase(Locale.ROOT)
        } else ""

        val ipDisplay = if (!ipv4.isNullOrBlank()) {
            ipv4
        } else if (!hostOrIp.contains(":")) {
            hostOrIp
        } else null

        return when {
            ipDisplay != null && shortMac.isNotBlank() -> "Ola S1 ($ipDisplay • $shortMac)"
            ipDisplay != null -> "Ola S1 ($ipDisplay)"
            shortMac.isNotBlank() -> "Ola S1 #$fallbackIndex ($shortMac)"
            hostOrIp.isNotBlank() && !hostOrIp.contains(":") -> "Ola S1 ($hostOrIp)"
            else -> "Ola S1 #$fallbackIndex"
        }
    }
}
