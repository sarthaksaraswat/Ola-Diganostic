package com.sarthak.olacan.model

/**
 * Models for the Complete Vehicle Self-Diagnostic and Health Test Suite.
 */

enum class DiagnosticTestStatus {
    IDLE,
    RUNNING,
    COMPLETED,
    ABORTED
}

enum class SubsystemTestState {
    PENDING,
    RUNNING,
    PASSED,
    FAILED,
    SKIPPED
}

data class SubsystemTestItem(
    val id: String,
    val name: String,
    val category: String, // "BRAKES", "HORN", "LIGHTING", "AUDIO", "CONNECTIVITY", "BATTERY", "VCU", "MCU", "DTC", "CONTACTOR"
    val description: String,
    val isInteractive: Boolean = false,
    val requiresManualConfirmation: Boolean = false,
    val interactivePrompt: String = "",
    val interactiveActionLabel: String = "",
    var state: SubsystemTestState = SubsystemTestState.PENDING,
    var measuredValue: String = "",
    var diagnosticDetail: String = "",
    var timeoutSecondsRemaining: Int = 30
)

data class VehicleHealthReport(
    val timestampMs: Long = System.currentTimeMillis(),
    val overallHealthScore: Int = 100, // 0 - 100
    val isPassed: Boolean = true,
    val totalTests: Int = 0,
    val passedTests: Int = 0,
    val failedTests: Int = 0,
    val testItems: List<SubsystemTestItem> = emptyList(),
    val summaryVerdict: String = "ALL SYSTEMS NOMINAL",
    val vehicleModel: String = "Ola S1 Pro Gen 2",
    val odometerKm: Double = 0.0
)
