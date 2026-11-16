package com.sarthak.olacan.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.sarthak.olacan.analyzer.BatteryHealthReport
import com.sarthak.olacan.analyzer.RideDiagnosticReport
import com.sarthak.olacan.analyzer.formatRideMetric
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfReportGenerator {

    /**
     * Generates a high-resolution, vector PDF diagnostic certificate from a RideDiagnosticReport.
     * Returns the content Uri ready to share via Android share sheet.
     */
    fun generatePdfReport(context: Context, report: RideDiagnosticReport): File {
        val document = PdfDocument()

        // Standard A4 dimensions: 595 x 842 points
        val pageWidth = 595
        val pageHeight = 842
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val margin = 36f
        val contentWidth = pageWidth - (margin * 2)

        // Palette definitions
        val cHeaderBg = Color.rgb(15, 23, 42)      // #0F172A
        val cPrimaryCyan = Color.rgb(6, 182, 212)   // #06B6D4
        val cAccentGreen = Color.rgb(16, 185, 129)  // #10B981
        val cGreenBg = Color.rgb(220, 252, 231)     // #DCFCE7
        val cWarningAmber = Color.rgb(217, 119, 6)  // #D97706
        val cAmberBg = Color.rgb(254, 243, 199)     // #FEF3C7
        val cDangerRed = Color.rgb(220, 38, 38)     // #DC2626
        val cRedBg = Color.rgb(254, 226, 226)       // #FEE2E2
        val cCardBg = Color.rgb(248, 250, 252)      // #F8FAFC
        val cBorder = Color.rgb(226, 232, 240)      // #E2E8F0
        val cTextPrimary = Color.rgb(15, 23, 42)    // #0F172A
        val cTextSecondary = Color.rgb(71, 85, 105) // #475569
        val cTextMuted = Color.rgb(148, 163, 184)   // #94A3B8

        var curY = margin

        // 1. TOP HEADER BANNER
        val headerHeight = 56f
        paint.color = cHeaderBg
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(margin, curY, margin + contentWidth, curY + headerHeight), 8f, 8f, paint)

        // Title
        paint.color = Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 14f
        canvas.drawText("OLA S1 PRO - CAN RIDE DIAGNOSTIC REPORT", margin + 14f, curY + 22f, paint)

        // Subtitle details
        paint.color = cPrimaryCyan
        paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        paint.textSize = 8.5f
        val durMins = report.durationSeconds / 60
        val durSecs = report.durationSeconds % 60
        val framesFormatted = String.format(Locale.US, "%,d", report.totalFrames)
        canvas.drawText(
            "Log: ${report.fileName}  |  Frames: $framesFormatted  |  Duration: ${durMins}m ${durSecs}s  |  Date: ${report.startTimeFormatted}",
            margin + 14f,
            curY + 40f,
            paint
        )

        curY += headerHeight + 10f

        // 2. EXECUTIVE VERDICT BANNER
        val hasDegradedCell = report.cells.any { it.condition == "CRITICAL DEGRADED" }
        val hasClamp = report.socClampOccurrences > 0
        val hasHighTemp = report.maxPduTempC > 85.0 || report.maxMotorTempC >= 115.0 || report.maxPackTempC > 50.0

        val verdictBoxHeight = 58f
        val needsAttention = report.hasWarning || report.hasIncompleteData
        val bannerBg = when {
            report.hasCriticalFault -> cRedBg
            needsAttention -> cAmberBg
            else -> cGreenBg
        }
        val bannerBorder = when {
            report.hasCriticalFault -> cDangerRed
            needsAttention -> cWarningAmber
            else -> cAccentGreen
        }

        paint.color = bannerBg
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(margin, curY, margin + contentWidth, curY + verdictBoxHeight), 6f, 6f, paint)

        paint.color = bannerBorder
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f
        canvas.drawRoundRect(RectF(margin, curY, margin + contentWidth, curY + verdictBoxHeight), 6f, 6f, paint)

        // Colored status indicator stripe
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(margin, curY, margin + 6f, curY + verdictBoxHeight), 6f, 6f, paint)

        // Verdict title
        paint.color = bannerBorder
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 10f
        canvas.drawText(report.verdict, margin + 14f, curY + 16f, paint)

        // Verdict explanation line 1
        paint.color = cTextPrimary
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 8f

        if (hasDegradedCell) {
            val degraded = report.cells.filter { it.condition == "CRITICAL DEGRADED" }
            val worst = degraded.maxByOrNull { it.internalResistanceMOhm } ?: report.cells.first()
            val text1 = "• Critical degradation: Cell ${String.format(Locale.US, "%02d", worst.channel)} Ri = ${String.format(Locale.US, "%.2f", worst.internalResistanceMOhm)} mΩ (sagged to ${String.format(Locale.US, "%.2f", worst.minVoltageV)}V under load)."
            canvas.drawText(text1, margin + 14f, curY + 31f, paint)
        } else {
            val detail = if (report.hasIncompleteData) "Some measurements are missing or the log ended early."
                else "The verdict covers the telemetry captured in this log."
            canvas.drawText(detail, margin + 14f, curY + 31f, paint)
        }

        val text2 = if (report.bmsCellDipFaultsCount > 0) {
            "BMS cell-undervoltage fault frames recorded: ${report.bmsCellDipFaultsCount}."
        } else if (hasClamp) {
            "• MoveOS 1% Limp Clamp triggered ${report.socClampOccurrences} time(s) (${String.format(Locale.US, "%.1f", report.socClampDurationSeconds)}s total) to protect weak cell from shutdown."
        } else if (hasHighTemp) {
            "• High thermal stress recorded during ride: Motor stator reached ${formatRideMetric(report.maxMotorTempC, 1, "°C")}, PDU at ${formatRideMetric(report.maxPduTempC, 1, "°C")}."
        } else {
            ""
        }
        canvas.drawText(text2, margin + 14f, curY + 45f, paint)

        curY += verdictBoxHeight + 10f

        // 3. POWERTRAIN & PERFORMANCE (4 Cards Grid)
        val cardW = (contentWidth - 10f) / 2f
        val cardH = 46f

        // Top Row: Speed & Distance | Peak Power
        drawMetricBox(canvas, paint, margin, curY, cardW, cardH, cCardBg, cBorder,
            "SPEED & DISTANCE",
            "${String.format(Locale.US, "%.1f", report.maxSpeedKmh)} km/h Top",
            "Avg: ${String.format(Locale.US, "%.1f", report.avgMovingSpeedKmh)} km/h  |  Dist: ${String.format(Locale.US, "%.2f", report.distanceKm)} km  |  ${String.format(Locale.US, "%.0f", report.maxMotorRpm)} RPM",
            cPrimaryCyan, cTextSecondary, cTextMuted)

        drawMetricBox(canvas, paint, margin + cardW + 10f, curY, cardW, cardH, cCardBg, cBorder,
            "PEAK POWER DRAW (DISCHARGE)",
            "${formatRideMetric(report.peakDischargePowerKw, 2, " kW")}  (${formatRideMetric(report.peakDischargeHorsepower, 1, " HP")})",
            "Motor draw: ${String.format(Locale.US, "%.1f", report.peakDischargeCurrentA)}A  |  Min Pack V: ${formatRideMetric(report.minPackVoltageV, 1, " V")}",
            cPrimaryCyan, cTextSecondary, cTextMuted)

        curY += cardH + 6f

        // Second Row: Peak Regen | Cell Imbalance
        drawMetricBox(canvas, paint, margin, curY, cardW, cardH, cCardBg, cBorder,
            "PEAK REGEN RECUPERATION",
            "+${formatRideMetric(report.peakRegenPowerKw, 2, " kW")}  (+${String.format(Locale.US, "%.1f", report.peakRegenCurrentA)}A)",
            "Recuperative braking charging current  |  Max Pack V: ${formatRideMetric(report.maxPackVoltageV, 1, " V")}",
            cAccentGreen, cTextSecondary, cTextMuted)

        val deltaColor = if (report.peakLoadCellDeltaMv > 400.0) cDangerRed else if (report.peakLoadCellDeltaMv > 150.0) cWarningAmber else cAccentGreen
        drawMetricBox(canvas, paint, margin + cardW + 10f, curY, cardW, cardH, cCardBg, cBorder,
            "PACK IMBALANCE & VOLTAGE SPAN",
            "Rest: ${formatRideMetric(report.restingCellDeltaMv, 0, " mV")}  |  Load: ${formatRideMetric(report.peakLoadCellDeltaMv, 0, " mV")}",
            "SOC: ${String.format(Locale.US, "%.1f", report.startSoc)}% -> ${String.format(Locale.US, "%.1f", report.endSoc)}%  |  Span: ${formatRideMetric(report.minPackVoltageV, 1, " V")} - ${formatRideMetric(report.maxPackVoltageV, 1, " V")}",
            deltaColor, cTextSecondary, cTextMuted)

        curY += cardH + 12f

        // 4. 14S SERIES NMC CELL BREAKDOWN TABLE
        paint.color = cTextPrimary
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 9.5f
        canvas.drawText("14-SERIES NMC CELL VOLTAGES & DYNAMIC IMPEDANCE (R_i)", margin, curY, paint)
        curY += 6f

        val tableHeaderH = 16f
        paint.color = Color.rgb(241, 245, 249) // light slate
        paint.style = Paint.Style.FILL
        canvas.drawRect(margin, curY, margin + contentWidth, curY + tableHeaderH, paint)

        // Table Header Columns
        paint.color = cTextSecondary
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 7.5f
        val colCell = margin + 8f
        val colRest = margin + 70f
        val colMin = margin + 160f
        val colSag = margin + 250f
        val colRi = margin + 340f
        val colStatus = margin + 440f

        canvas.drawText("CHANNEL", colCell, curY + 11f, paint)
        canvas.drawText("RESTING V", colRest, curY + 11f, paint)
        canvas.drawText("MIN LOAD V", colMin, curY + 11f, paint)
        canvas.drawText("PEAK SAG", colSag, curY + 11f, paint)
        canvas.drawText("IMPEDANCE (Ri)", colRi, curY + 11f, paint)
        canvas.drawText("CONDITION", colStatus, curY + 11f, paint)

        curY += tableHeaderH

        // Rows for all 14 cells
        val rowH = 14.5f
        report.cells.forEachIndexed { idx, cell ->
            val isEven = (idx % 2 == 0)
            if (isEven) {
                paint.color = Color.rgb(250, 250, 250)
                paint.style = Paint.Style.FILL
                canvas.drawRect(margin, curY, margin + contentWidth, curY + rowH, paint)
            }

            val isDegraded = cell.condition == "CRITICAL DEGRADED"
            val isHighSag = cell.condition == "HIGH SAG"
            val statusColor = when {
                isDegraded -> cDangerRed
                isHighSag -> cWarningAmber
                else -> cAccentGreen
            }

            paint.color = cTextPrimary
            paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            paint.textSize = 7.5f
            canvas.drawText("Cell ${String.format(Locale.US, "%02d", cell.channel)}", colCell, curY + 10.5f, paint)

            paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            paint.color = cTextSecondary
            canvas.drawText(String.format(Locale.US, "%.3f V", cell.restingVoltageV), colRest, curY + 10.5f, paint)

            paint.color = if (isDegraded || isHighSag) statusColor else cTextPrimary
            canvas.drawText(String.format(Locale.US, "%.3f V", cell.minVoltageV), colMin, curY + 10.5f, paint)
            canvas.drawText("${cell.maxSagMv.toInt()} mV", colSag, curY + 10.5f, paint)
            canvas.drawText(String.format(Locale.US, "%.2f mΩ", cell.internalResistanceMOhm), colRi, curY + 10.5f, paint)

            // Status Badge
            paint.color = when {
                isDegraded -> cRedBg
                isHighSag -> cAmberBg
                else -> cGreenBg
            }
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(RectF(colStatus, curY + 2f, colStatus + 62f, curY + rowH - 2f), 3f, 3f, paint)

            paint.color = statusColor
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 6.5f
            canvas.drawText(cell.condition, colStatus + 4f, curY + 9.5f, paint)

            curY += rowH
        }

        curY += 10f

        // 5. MOVEOS SAFETY DERATING & INTERLOCKS
        paint.color = cTextPrimary
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 9.5f
        canvas.drawText("MOVEOS SAFETY INTERLOCKS & DERATING OCCURRENCES", margin, curY, paint)
        curY += 6f

        val derateBoxH = 50f
        paint.color = cCardBg
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(margin, curY, margin + contentWidth, curY + derateBoxH), 6f, 6f, paint)
        paint.color = cBorder
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(RectF(margin, curY, margin + contentWidth, curY + derateBoxH), 6f, 6f, paint)

        paint.style = Paint.Style.FILL
        val dCol1 = margin + 12f
        val dCol2 = margin + 270f
        val dY1 = curY + 14f
        val dY2 = curY + 28f
        val dY3 = curY + 42f

        drawDerateItem(canvas, paint, dCol1, dY1, "1% False-Empty Clamp:", "${report.socClampOccurrences}x (${String.format(Locale.US, "%.1f", report.socClampDurationSeconds)}s limp)", report.socClampOccurrences > 0, cDangerRed, cTextSecondary)
        drawDerateItem(canvas, paint, dCol2, dY1, "Cell Sag Derate (0x122 b2):", "${report.bmsCellDipFaultsCount} frames", report.bmsCellDipFaultsCount > 0, cDangerRed, cTextSecondary)
        drawDerateItem(canvas, paint, dCol1, dY2, "Spread Sag Warning (0x122 b3):", "${report.bmsLoadSagWarningsCount} frames", report.bmsLoadSagWarningsCount > 0, cWarningAmber, cTextSecondary)
        drawDerateItem(canvas, paint, dCol2, dY2, "High Discharge Derate (0x122 b4):", "${report.bmsHighDischargeDerateCount} frames", report.bmsHighDischargeDerateCount > 0, cWarningAmber, cTextSecondary)
        drawDerateItem(canvas, paint, dCol1, dY3, "Regen Inhibit (0x122 b5):", "${report.bmsRegenInhibitCount} frames", report.bmsRegenInhibitCount > 0, cWarningAmber, cTextSecondary)

        curY += derateBoxH + 10f

        // 6. THERMAL STRESS & COMPONENT PEAKS
        paint.color = cTextPrimary
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 9.5f
        canvas.drawText("THERMAL STRESS & COMPONENT HOTSPOTS", margin, curY, paint)
        curY += 6f

        val tCardW = (contentWidth - 18f) / 4f
        val tCardH = 44f

        val motorColor = when {
            report.maxMotorTempC >= 115.0 -> cDangerRed
            report.maxMotorTempC >= 95.0 -> cWarningAmber
            else -> cAccentGreen
        }
        val motorStatus = when {
            report.maxMotorTempC >= 115.0 -> "Cutoff (≥115°C)"
            report.maxMotorTempC >= 95.0 -> "Derate (≥95°C)"
            else -> "Normal (<92°C)"
        }

        val pduColor = if (report.maxPduTempC > 85.0) cDangerRed else cPrimaryCyan
        val afeColor = if (report.maxAfeTempC > 80.0) cWarningAmber else cPrimaryCyan
        val packColor = when {
            report.maxPackTempC >= 55.0 || report.maxPackThermalGradientC >= 10.0 -> cDangerRed
            report.maxPackTempC >= 48.0 -> cWarningAmber
            else -> cAccentGreen
        }

        drawThermalCard(canvas, paint, margin, curY, tCardW, tCardH, "PACK CORE (P${report.hottestProbeIdx})", "${formatRideMetric(report.maxPackTempC, 1, "°C")}", "ΔT: ${String.format(Locale.US, "%.1f", report.maxPackThermalGradientC)}°C", packColor, cCardBg, cBorder, cTextMuted)
        drawThermalCard(canvas, paint, margin + tCardW + 6f, curY, tCardW, tCardH, "MOTOR STATOR", "${formatRideMetric(report.maxMotorTempC, 1, "°C")}", motorStatus, motorColor, cCardBg, cBorder, cTextMuted)
        drawThermalCard(canvas, paint, margin + (tCardW + 6f) * 2, curY, tCardW, tCardH, "PDU POWER UNIT", "${formatRideMetric(report.maxPduTempC, 1, "°C")}", if (report.maxPduTempC > 85.0) "Throttling" else "Normal", pduColor, cCardBg, cBorder, cTextMuted)
        drawThermalCard(canvas, paint, margin + (tCardW + 6f) * 3, curY, tCardW, tCardH, "BMS AFE IC", "${formatRideMetric(report.maxAfeTempC, 1, "°C")}", if (report.maxAfeTempC > 80.0) "High Temp" else "Normal", afeColor, cCardBg, cBorder, cTextMuted)

        curY += tCardH + 14f

        // 7. FOOTER
        paint.color = cBorder
        paint.strokeWidth = 1f
        canvas.drawLine(margin, curY, margin + contentWidth, curY, paint)

        paint.color = cTextMuted
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 7f
        val genTime = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.US).format(Date())
        canvas.drawText("Generated by Ola Diagnostic  •  MoveOS 5.x Automotive Telemetry Engine  •  $genTime", margin, curY + 12f, paint)
        canvas.drawText("Page 1 of 1", margin + contentWidth - 45f, curY + 12f, paint)

        document.finishPage(page)

        // Save PDF to cache directory for instant sharing
        val safeTime = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val pdfFile = File(context.cacheDir, "OlaCAN_Diagnosis_$safeTime.pdf")
        try {
            FileOutputStream(pdfFile).use { out ->
                document.writeTo(out)
            }
        } finally {
            try {
                document.close()
            } catch (_: Exception) {}
        }

        return pdfFile
    }

    /**
     * Generates a high-resolution, vector PDF State-of-Health (SOH) Certificate from a BatteryHealthReport.
     */
    fun generateBatteryHealthPdf(context: Context, report: BatteryHealthReport): File {
        val document = PdfDocument()

        val pageWidth = 595
        val pageHeight = 842
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val margin = 36f
        val contentWidth = pageWidth - (margin * 2)

        val cHeaderBg = Color.rgb(15, 23, 42)      // #0F172A
        val cPrimaryCyan = Color.rgb(6, 182, 212)   // #06B6D4
        val cAccentGreen = Color.rgb(16, 185, 129)  // #10B981
        val cGreenBg = Color.rgb(220, 252, 231)     // #DCFCE7
        val cWarningAmber = Color.rgb(217, 119, 6)  // #D97706
        val cAmberBg = Color.rgb(254, 243, 199)     // #FEF3C7
        val cDangerRed = Color.rgb(220, 38, 38)     // #DC2626
        val cRedBg = Color.rgb(254, 226, 226)       // #FEE2E2
        val cCardBg = Color.rgb(248, 250, 252)      // #F8FAFC
        val cBorder = Color.rgb(226, 232, 240)      // #E2E8F0
        val cTextPrimary = Color.rgb(15, 23, 42)    // #0F172A
        val cTextSecondary = Color.rgb(71, 85, 105) // #475569
        val cTextMuted = Color.rgb(148, 163, 184)   // #94A3B8

        var curY = margin

        // 1. TOP HEADER BANNER
        val headerHeight = 56f
        paint.color = cHeaderBg
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(margin, curY, margin + contentWidth, curY + headerHeight), 8f, 8f, paint)

        paint.color = Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 14f
        canvas.drawText("OLA S1 PRO - BATTERY STATE-OF-HEALTH CERTIFICATE", margin + 14f, curY + 22f, paint)

        paint.color = cPrimaryCyan
        paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        paint.textSize = 8.5f
        val framesFormatted = String.format(Locale.US, "%,d", report.totalFrames)
        canvas.drawText(
            "Unified Log: ${report.logFileName}  |  Frames: $framesFormatted  |  Duration: ${report.durationFormatted}  |  Tested: ${report.testDateFormatted}",
            margin + 14f,
            curY + 40f,
            paint
        )

        curY += headerHeight + 10f

        // 2. EXECUTIVE SOH GRADE BANNER
        val isGood = report.calculatedSohPercent >= 88.0
        val isFair = report.calculatedSohPercent in 80.0..87.99
        val bannerBg = when {
            isGood -> cGreenBg
            isFair -> cAmberBg
            else -> cRedBg
        }
        val bannerBorder = when {
            isGood -> cAccentGreen
            isFair -> cWarningAmber
            else -> cDangerRed
        }

        val verdictBoxHeight = 58f
        paint.color = bannerBg
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(margin, curY, margin + contentWidth, curY + verdictBoxHeight), 6f, 6f, paint)

        paint.color = bannerBorder
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f
        canvas.drawRoundRect(RectF(margin, curY, margin + contentWidth, curY + verdictBoxHeight), 6f, 6f, paint)

        paint.style = Paint.Style.FILL
        paint.color = bannerBorder
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 14f
        canvas.drawText(report.healthGrade, margin + 14f, curY + 24f, paint)

        paint.color = cTextSecondary
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 8.5f
        val sohSummary = "Calculated SOH: ${String.format(Locale.US, "%.1f", report.calculatedSohPercent)}%  •  Tested Usable: ${String.format(Locale.US, "%.2f", report.estimatedFullCapacityKwh)} kWh / ${String.format(Locale.US, "%.2f", report.factoryUsableWh / 1000.0)} kWh Usable (3.98 Gross)  •  Efficiency: ${String.format(Locale.US, "%.1f", report.batteryEfficiencyPercent)}%"
        canvas.drawText(sohSummary, margin + 14f, curY + 44f, paint)

        curY += verdictBoxHeight + 12f

        // Helper section drawer
        fun drawSectionTitle(title: String) {
            paint.color = cTextPrimary
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 9.5f
            canvas.drawText(title, margin, curY + 9f, paint)

            paint.color = cBorder
            paint.strokeWidth = 0.8f
            canvas.drawLine(margin, curY + 14f, margin + contentWidth, curY + 14f, paint)
            curY += 20f
        }

        // 3. CAPACITY & DISCHARGE TEST RESULTS
        drawSectionTitle("1. PACK CAPACITY & SOH ASSESSMENT")
        val cardW = (contentWidth - 12f) / 3f
        val cardH = 46f

        drawMetricBox(canvas, paint, margin, curY, cardW, cardH, cCardBg, cBorder, "MEASURED USABLE CAPACITY", "${String.format(Locale.US, "%.2f", report.estimatedFullCapacityKwh)} kWh", "${String.format(Locale.US, "%.0f", report.estimatedFullCapacityWh)} Wh calculated", cTextPrimary, cTextSecondary, cTextMuted)
        drawMetricBox(canvas, paint, margin + cardW + 6f, curY, cardW, cardH, cCardBg, cBorder, "FACTORY BASELINE", "${String.format(Locale.US, "%.2f", report.factoryUsableWh / 1000.0)} kWh", "3.70 Usable / 3.98 Gross", cTextPrimary, cTextSecondary, cTextMuted)
        drawMetricBox(canvas, paint, margin + (cardW + 6f) * 2, curY, cardW, cardH, cCardBg, cBorder, "MEASURED SOH", "${String.format(Locale.US, "%.1f", report.calculatedSohPercent)} %", report.healthGrade.take(14), bannerBorder, cTextSecondary, cTextMuted)

        curY += cardH + 6f

        drawMetricBox(canvas, paint, margin, curY, cardW, cardH, cCardBg, cBorder, "TEST SOC RANGE", "${String.format(Locale.US, "%.1f", report.startSoc)}% -> ${String.format(Locale.US, "%.1f", report.endSoc)}%", "Discharged: ${String.format(Locale.US, "%.1f", report.deltaSoc)}% SOC", cTextPrimary, cTextSecondary, cTextMuted)
        drawMetricBox(canvas, paint, margin + cardW + 6f, curY, cardW, cardH, cCardBg, cBorder, "PACK VOLTAGE SPAN", "${String.format(Locale.US, "%.1f", report.startBusVoltageV)}V -> ${String.format(Locale.US, "%.1f", report.endBusVoltageV)}V", "Min ${String.format(Locale.US, "%.1f", report.minBusVoltageV)}V | Max ${String.format(Locale.US, "%.1f", report.maxBusVoltageV)}V", cTextPrimary, cTextSecondary, cTextMuted)
        drawMetricBox(canvas, paint, margin + (cardW + 6f) * 2, curY, cardW, cardH, cCardBg, cBorder, "NET AMP-HOURS DISCHARGED", "${String.format(Locale.US, "%.2f", report.netDischargeAh)} Ah", "Net Coulomb integration", cTextPrimary, cTextSecondary, cTextMuted)

        curY += cardH + 12f

        // 4. ENERGY & LOSSES INTEGRATION
        drawSectionTitle("2. POWER INTEGRATION & THERMAL LOSSES")

        drawMetricBox(canvas, paint, margin, curY, cardW, cardH, cCardBg, cBorder, "GROSS DISCHARGE ENERGY", "${String.format(Locale.US, "%.2f", report.grossDischargeKwh)} kWh", "${String.format(Locale.US, "%.0f", report.grossDischargeWh)} Wh into motor", cTextPrimary, cTextSecondary, cTextMuted)
        drawMetricBox(canvas, paint, margin + cardW + 6f, curY, cardW, cardH, cCardBg, cBorder, "REGENERATIVE RECOVERY", "${String.format(Locale.US, "%.2f", report.regenKwh)} kWh", "+${String.format(Locale.US, "%.1f", report.regenContributionPercent)}% regen recapture", cAccentGreen, cTextSecondary, cTextMuted)
        drawMetricBox(canvas, paint, margin + (cardW + 6f) * 2, curY, cardW, cardH, cCardBg, cBorder, "NET DELIVERED ENERGY", "${String.format(Locale.US, "%.2f", report.netEnergyOutputKwh)} kWh", "${String.format(Locale.US, "%.0f", report.netEnergyOutputWh)} Wh net delivered", cPrimaryCyan, cTextSecondary, cTextMuted)

        curY += cardH + 6f

        drawMetricBox(canvas, paint, margin, curY, cardW, cardH, cCardBg, cBorder, "INTERNAL I²R DISSIPATION LOSS", "${String.format(Locale.US, "%.1f", report.estimatedInternalLossWh)} Wh", "Pack Ri ~35mΩ internal heat", cWarningAmber, cTextSecondary, cTextMuted)
        drawMetricBox(canvas, paint, margin + cardW + 6f, curY, cardW, cardH, cCardBg, cBorder, "DC ELECTROCHEMICAL EFFICIENCY", "${String.format(Locale.US, "%.1f", report.batteryEfficiencyPercent)} %", "Thermal energy retention", cAccentGreen, cTextSecondary, cTextMuted)
        drawMetricBox(canvas, paint, margin + (cardW + 6f) * 2, curY, cardW, cardH, cCardBg, cBorder, "ODOMETER & CONSUMPTION", "${String.format(Locale.US, "%.1f", report.totalDistanceKm)} km", "${String.format(Locale.US, "%.1f", report.avgConsumptionWhPerKm)} Wh/km efficiency", cTextPrimary, cTextSecondary, cTextMuted)

        curY += cardH + 12f

        // 5. CELL INTEGRITY & MOVEOS STATUS
        drawSectionTitle("3. CELL BALANCE & MOVEOS DERATE INTEGRITY")
        val cCardW = (contentWidth - 18f) / 4f

        drawMetricBox(canvas, paint, margin, curY, cCardW, cardH, cCardBg, cBorder, "WEAKEST CELL CHANNEL", "Cell #${report.worstCellChannel}", "Largest sag profile", cTextPrimary, cTextSecondary, cTextMuted)
        drawMetricBox(canvas, paint, margin + cCardW + 6f, curY, cCardW, cardH, cCardBg, cBorder, "MIN CELL VOLTAGE", "${String.format(Locale.US, "%.3f", report.minCellVoltageRecordedV)} V", "Floor during load peaks", if (report.minCellVoltageRecordedV < 2.8) cDangerRed else cTextPrimary, cTextSecondary, cTextMuted)
        drawMetricBox(canvas, paint, margin + (cCardW + 6f) * 2, curY, cCardW, cardH, cCardBg, cBorder, "MOVEOS 1% CLAMPS", "${report.socClampOccurrences}", if (report.socClampOccurrences == 0) "None (Clean run)" else "Sag protection active", if (report.socClampOccurrences > 0) cDangerRed else cAccentGreen, cTextSecondary, cTextMuted)
        drawMetricBox(canvas, paint, margin + (cCardW + 6f) * 3, curY, cCardW, cardH, cCardBg, cBorder, "BMS SAG FAULTS", "${report.bmsCellSagFaultsCount}", if (report.bmsCellSagFaultsCount == 0) "Zero fault events" else "Low voltage cutoffs", if (report.bmsCellSagFaultsCount > 0) cDangerRed else cAccentGreen, cTextSecondary, cTextMuted)

        curY += cardH + 14f

        // 6. PACK HARDWARE VS CHASSIS CYCLES & SESSIONS
        drawSectionTitle("4. PACK HARDWARE VS CHASSIS LIFETIME CYCLES")

        val packCyclesVal = if (report.packHardwareCycles > 0) "${report.packHardwareCycles} Cycles" else "---"
        val chassisCyclesVal = if (report.vehicleChassisCycles > 0) "${report.vehicleChassisCycles} Cycles" else "---"

        drawMetricBox(canvas, paint, margin, curY, cCardW, cardH, cCardBg, cBorder, "PACK HARDWARE CYCLES", packCyclesVal, "BMS EEPROM (This Pack)", cPrimaryCyan, cTextSecondary, cTextMuted)
        drawMetricBox(canvas, paint, margin + cCardW + 6f, curY, cCardW, cardH, cCardBg, cBorder, "CHASSIS LIFETIME CYCLES", chassisCyclesVal, "VCU Scooter Lifetime", cTextPrimary, cTextSecondary, cTextMuted)
        drawMetricBox(canvas, paint, margin + (cCardW + 6f) * 2, curY, cCardW, cardH, cCardBg, cBorder, "AC HOME SESSIONS", "${report.acChargeSessions}", "Standard 750W Home AC", cAccentGreen, cTextSecondary, cTextMuted)
        drawMetricBox(canvas, paint, margin + (cCardW + 6f) * 3, curY, cCardW, cardH, cCardBg, cBorder, "DC FAST HYPERCHARGE", "${report.dcFastChargeSessions}", "High C-Rate DC Fast", cWarningAmber, cTextSecondary, cTextMuted)

        curY += cardH + 14f

        // 7. RECOMMENDATIONS BANNER
        val recBoxH = 50f
        paint.color = cCardBg
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(margin, curY, margin + contentWidth, curY + recBoxH), 6f, 6f, paint)

        paint.color = cBorder
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(RectF(margin, curY, margin + contentWidth, curY + recBoxH), 6f, 6f, paint)

        paint.style = Paint.Style.FILL
        paint.color = cTextPrimary
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 8.5f
        canvas.drawText("TECHNICAL ADVISORY & BEST PRACTICES", margin + 10f, curY + 16f, paint)

        paint.color = cTextSecondary
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 7.5f
        val advisory = when {
            report.calculatedSohPercent >= 92.0 -> "Pack chemistry shows excellent health and minimal degradation. Continue standard 20%-80% charging habits for maximum pack longevity."
            report.calculatedSohPercent >= 85.0 -> "Pack capacity is within normal operational aging bounds. Regularly balance cells by charging to 100% overnight once a week."
            else -> "Capacity reduction detected. Consider requesting BMS balancing service at Ola Care if cell imbalance exceeds 80mV at resting voltage."
        }
        canvas.drawText(advisory, margin + 10f, curY + 34f, paint)

        curY += recBoxH + 14f

        // 7. FOOTER
        paint.color = cBorder
        paint.strokeWidth = 1f
        canvas.drawLine(margin, curY, margin + contentWidth, curY, paint)

        paint.color = cTextMuted
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 7f
        val genTime = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.US).format(Date())
        canvas.drawText("Generated by Ola Diagnostic  •  MoveOS 5.x SOH Engine  •  $genTime", margin, curY + 12f, paint)
        canvas.drawText("Page 1 of 1", margin + contentWidth - 45f, curY + 12f, paint)

        document.finishPage(page)

        val safeTime = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val pdfFile = File(context.cacheDir, "OlaCAN_BatteryHealth_$safeTime.pdf")
        try {
            FileOutputStream(pdfFile).use { out ->
                document.writeTo(out)
            }
        } finally {
            try {
                document.close()
            } catch (_: Exception) {}
        }

        return pdfFile
    }

    private fun drawMetricBox(
        canvas: Canvas,
        paint: Paint,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        bg: Int,
        border: Int,
        title: String,
        value: String,
        sub: String,
        valColor: Int,
        subColor: Int,
        titleColor: Int
    ) {
        paint.style = Paint.Style.FILL
        paint.color = bg
        canvas.drawRoundRect(RectF(x, y, x + w, y + h), 6f, 6f, paint)
        paint.style = Paint.Style.STROKE
        paint.color = border
        paint.strokeWidth = 1f
        canvas.drawRoundRect(RectF(x, y, x + w, y + h), 6f, 6f, paint)

        paint.style = Paint.Style.FILL
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 7f
        paint.color = titleColor
        canvas.drawText(title, x + 10f, y + 12f, paint)

        paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        paint.textSize = 10f
        paint.color = valColor
        canvas.drawText(value, x + 10f, y + 26f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 6.5f
        paint.color = subColor
        canvas.drawText(sub, x + 10f, y + 38f, paint)
    }

    private fun drawThermalCard(
        canvas: Canvas,
        paint: Paint,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        title: String,
        temp: String,
        sub: String,
        valColor: Int,
        bg: Int,
        border: Int,
        titleColor: Int
    ) {
        paint.style = Paint.Style.FILL
        paint.color = bg
        canvas.drawRoundRect(RectF(x, y, x + w, y + h), 6f, 6f, paint)
        paint.style = Paint.Style.STROKE
        paint.color = border
        paint.strokeWidth = 1f
        canvas.drawRoundRect(RectF(x, y, x + w, y + h), 6f, 6f, paint)

        paint.style = Paint.Style.FILL
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 6.5f
        paint.color = titleColor
        canvas.drawText(title, x + 8f, y + 12f, paint)

        paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        paint.textSize = 11f
        paint.color = valColor
        canvas.drawText(temp, x + 8f, y + 27f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 6.5f
        paint.color = valColor
        canvas.drawText(sub, x + 8f, y + 38f, paint)
    }

    private fun drawDerateItem(
        canvas: Canvas,
        paint: Paint,
        x: Float,
        y: Float,
        label: String,
        value: String,
        isWarning: Boolean,
        warnColor: Int,
        labelColor: Int
    ) {
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 7.5f
        paint.color = labelColor
        canvas.drawText(label, x, y, paint)

        val textWidth = paint.measureText(label)
        paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        paint.color = if (isWarning) warnColor else Color.rgb(16, 185, 129)
        canvas.drawText("  $value", x + textWidth, y, paint)
    }
}
