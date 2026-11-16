package com.sarthak.olacan.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object HapticHelper {

    private val _hapticsEnabled = MutableStateFlow(true)
    val hapticsEnabled = _hapticsEnabled.asStateFlow()

    fun setHapticsEnabled(enabled: Boolean, context: Context? = null) {
        _hapticsEnabled.value = enabled
        if (context != null) {
            try {
                val sp = context.getSharedPreferences("ola_can_prefs", Context.MODE_PRIVATE)
                sp.edit().putBoolean("haptics_enabled", enabled).apply()
            } catch (_: Exception) {}
        }
    }

    fun init(context: Context) {
        try {
            val sp = context.getSharedPreferences("ola_can_prefs", Context.MODE_PRIVATE)
            _hapticsEnabled.value = sp.getBoolean("haptics_enabled", true)
        } catch (_: Exception) {}
    }

    fun performClick(view: View?) {
        if (!_hapticsEnabled.value || view == null) return
        try {
            view.performHapticFeedback(
                HapticFeedbackConstants.KEYBOARD_TAP,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
        } catch (_: Exception) {
            try {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            } catch (_: Exception) {}
        }
    }

    fun triggerVibration(context: Context, durationMs: Long = 20) {
        if (!_hapticsEnabled.value) return
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (_: Exception) {}
    }
}
