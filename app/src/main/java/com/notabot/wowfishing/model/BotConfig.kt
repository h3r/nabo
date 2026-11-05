package com.notabot.wowfishing.model

import android.content.Context
import android.content.SharedPreferences
import kotlin.random.Random

data class BotConfig(
    var soundThreshold: Int = 40,
    var minRecastDelaySec: Float = 2.0f,
    var maxRecastDelaySec: Float = 3.5f,
    var castTimeoutSec: Int = 22,
    var castX: Float = 0f,
    var castY: Float = 0f,
    var reelX: Float = 0f,
    var reelY: Float = 0f,
    var autoLureEnabled: Boolean = false,
    var lureDurationMinutes: Int = 10,
    var lureX: Float = 0f,
    var lureY: Float = 0f,
    var lureApplyDelaySec: Float = 3.0f
) {
    fun getRandomRecastDelayMillis(): Long {
        val minMs = (minRecastDelaySec * 1000).toLong().coerceAtLeast(500L)
        val maxMs = (maxRecastDelaySec * 1000).toLong().coerceAtLeast(minMs)
        return if (minMs == maxMs) minMs else Random.nextLong(minMs, maxMs + 1)
    }

    fun save(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_THRESHOLD, soundThreshold)
            .putFloat(KEY_MIN_DELAY, minRecastDelaySec)
            .putFloat(KEY_MAX_DELAY, maxRecastDelaySec)
            .putInt(KEY_TIMEOUT, castTimeoutSec)
            .putFloat(KEY_CAST_X, castX)
            .putFloat(KEY_CAST_Y, castY)
            .putFloat(KEY_REEL_X, reelX)
            .putFloat(KEY_REEL_Y, reelY)
            .putBoolean(KEY_AUTO_LURE, autoLureEnabled)
            .putInt(KEY_LURE_DURATION, lureDurationMinutes)
            .putFloat(KEY_LURE_X, lureX)
            .putFloat(KEY_LURE_Y, lureY)
            .putFloat(KEY_LURE_DELAY, lureApplyDelaySec)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "nabo_prefs"
        private const val KEY_THRESHOLD = "sound_threshold"
        private const val KEY_MIN_DELAY = "min_recast_delay"
        private const val KEY_MAX_DELAY = "max_recast_delay"
        private const val KEY_TIMEOUT = "cast_timeout"
        private const val KEY_CAST_X = "cast_x"
        private const val KEY_CAST_Y = "cast_y"
        private const val KEY_REEL_X = "reel_x"
        private const val KEY_REEL_Y = "reel_y"
        private const val KEY_AUTO_LURE = "auto_lure_enabled"
        private const val KEY_LURE_DURATION = "lure_duration_minutes"
        private const val KEY_LURE_X = "lure_x"
        private const val KEY_LURE_Y = "lure_y"
        private const val KEY_LURE_DELAY = "lure_apply_delay"

        fun load(context: Context): BotConfig {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return BotConfig(
                soundThreshold = prefs.getInt(KEY_THRESHOLD, 40),
                minRecastDelaySec = prefs.getFloat(KEY_MIN_DELAY, 2.0f),
                maxRecastDelaySec = prefs.getFloat(KEY_MAX_DELAY, 3.5f),
                castTimeoutSec = prefs.getInt(KEY_TIMEOUT, 22),
                castX = prefs.getFloat(KEY_CAST_X, 0f),
                castY = prefs.getFloat(KEY_CAST_Y, 0f),
                reelX = prefs.getFloat(KEY_REEL_X, 0f),
                reelY = prefs.getFloat(KEY_REEL_Y, 0f),
                autoLureEnabled = prefs.getBoolean(KEY_AUTO_LURE, false),
                lureDurationMinutes = prefs.getInt(KEY_LURE_DURATION, 10),
                lureX = prefs.getFloat(KEY_LURE_X, 0f),
                lureY = prefs.getFloat(KEY_LURE_Y, 0f),
                lureApplyDelaySec = prefs.getFloat(KEY_LURE_DELAY, 3.0f)
            )
        }
    }
}
