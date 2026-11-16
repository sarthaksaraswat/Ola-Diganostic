package com.sarthak.olacan.util

import android.content.Context
import com.sarthak.olacan.model.AlertSeverity
import com.sarthak.olacan.model.DtcFault
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Locale

object FaultCatalog {

    private var initialized = false
    private val faultsList = mutableListOf<DtcFault>()
    private val faultsById = mutableMapOf<String, DtcFault>()

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        try {
            context.assets.open("faults.json").use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
                val jsonString = reader.readText()
                val root = JSONObject(jsonString)
                val array = root.optJSONArray("faults") ?: return
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.optString("id", "")
                    if (id.isEmpty()) continue

                    val severityStr = obj.optString("severity", "INFO")
                    val severity = when (severityStr.uppercase(Locale.ROOT)) {
                        "CRITICAL" -> AlertSeverity.CRITICAL
                        "WARNING" -> AlertSeverity.WARNING
                        else -> AlertSeverity.INFO
                    }

                    val fault = DtcFault(
                        id = id,
                        title = obj.optString("title", id),
                        description = obj.optString("description", ""),
                        severity = severity,
                        component = obj.optString("component", "OTHER"),
                        fixable = obj.optBoolean("fixable", false),
                        postFixMessage = if (obj.isNull("postFixMessage")) null else obj.optString("postFixMessage")
                    )
                    faultsList.add(fault)
                    faultsById[id] = fault
                    faultsById[id.lowercase(Locale.ROOT)] = fault
                }
            }
            initialized = true
        } catch (_: Exception) {
            // Fallback: initialized stays false or empty
        }
    }

    fun getAllFaults(): List<DtcFault> = faultsList

    fun lookup(idOrSignal: String?): DtcFault? {
        if (idOrSignal.isNullOrBlank()) return null
        faultsById[idOrSignal]?.let { return it }
        faultsById[idOrSignal.lowercase(Locale.ROOT)]?.let { return it }
        val camel = toCamelCase(idOrSignal)
        faultsById[camel]?.let { return it }
        faultsById[camel.lowercase(Locale.ROOT)]?.let { return it }
        return null
    }

    fun toCamelCase(text: String): String {
        val parts = text.lowercase(Locale.ROOT).split("_")
        val sb = StringBuilder()
        var first = true
        for (part in parts) {
            if (part.isEmpty()) continue
            if (first) {
                sb.append(part)
                first = false
            } else {
                sb.append(part.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() })
            }
        }
        return if (sb.isEmpty()) text else sb.toString()
    }
}
