package com.sarthak.olacan

import android.app.Application
import android.util.Log
import com.sarthak.olacan.analyzer.BatteryHealthManager
import com.sarthak.olacan.analyzer.VehicleSelfTestManager
import com.sarthak.olacan.service.CanStreamManager
import com.sarthak.olacan.util.AppLogManager
import com.sarthak.olacan.util.FaultCatalog
import com.sarthak.olacan.util.HapticHelper
import com.sarthak.olacan.util.LogRecorder

/**
 * Custom Application class for Ola Diagnostic.
 * Installs a global crash shield and safely initializes application singletons.
 */
class OlaCanApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 1. Install Global Crash Shield (UncaughtExceptionHandler)
        val defaultUncaughtHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e("CRASH_SHIELD", "FATAL CRASH on thread [${thread?.name}]: ${throwable?.message}", throwable)
                AppLogManager.error(
                    "CRASH_SHIELD",
                    "Uncaught exception on thread [${thread?.name}]: ${throwable?.message}",
                    throwable
                )
                // Emergency flush in-flight CAN logs to Downloads
                LogRecorder.emergencySaveOnCrash(this)
            } catch (_: Throwable) {
                // Failsafe: never allow crash shield itself to throw
            }

            // Forward to default system handler
            defaultUncaughtHandler?.uncaughtException(thread, throwable)
        }

        // 2. Safe initializations wrapped in try-catch so startup never crashes
        try {
            AppLogManager.init(this)
        } catch (e: Throwable) {
            Log.e("OlaCanApp", "Failed to init AppLogManager: ${e.message}", e)
        }

        try {
            LogRecorder.recoverOrphanedLogs(this)
        } catch (e: Throwable) {
            Log.e("OlaCanApp", "Failed to recover orphaned logs: ${e.message}", e)
        }

        try {
            HapticHelper.init(this)
        } catch (e: Throwable) {
            Log.e("OlaCanApp", "Failed to init HapticHelper: ${e.message}", e)
        }

        try {
            FaultCatalog.init(this)
        } catch (e: Throwable) {
            Log.e("OlaCanApp", "Failed to init FaultCatalog: ${e.message}", e)
        }

        try {
            VehicleSelfTestManager.init(this)
        } catch (e: Throwable) {
            Log.e("OlaCanApp", "Failed to init VehicleSelfTestManager: ${e.message}", e)
        }

        try {
            BatteryHealthManager.init(this)
        } catch (e: Throwable) {
            Log.e("OlaCanApp", "Failed to init BatteryHealthManager: ${e.message}", e)
        }

        try {
            CanStreamManager.init(this)
        } catch (e: Throwable) {
            Log.e("OlaCanApp", "Failed to init CanStreamManager: ${e.message}", e)
        }
    }
}
