package io.github.imroxy2222.lsp_coloros17cyclebattery.xposed

import android.content.SharedPreferences

/**
 * 环形电量配置键与默认值。
 * 同一份代码同时被模块 App（写入 RemotePreferences）和
 * SystemUI 进程内的 Hook（只读 RemotePreferences）使用。
 */
object BatteryConfig {

    /** RemotePreferences 分组名，两端必须一致 */
    const val PREF_GROUP = "ring_battery_settings"

    const val KEY_ENABLED = "enabled"
    const val KEY_SIZE_DP = "size_dp"
    const val KEY_OFFSET_X_DP = "offset_x_dp"
    const val KEY_OFFSET_Y_DP = "offset_y_dp"
    const val KEY_STROKE_DP = "stroke_width_dp"
    const val KEY_COLOR_NORMAL = "color_normal"
    const val KEY_COLOR_CHARGING = "color_charging"
    const val KEY_COLOR_LOW = "color_low"
    const val KEY_LOW_LEVEL = "low_level_threshold"

    const val DEFAULT_ENABLED = true
    const val DEFAULT_SIZE_DP = 18f
    const val DEFAULT_OFFSET_X_DP = 0f
    const val DEFAULT_OFFSET_Y_DP = 0f
    const val DEFAULT_STROKE_DP = 2.5f
    const val DEFAULT_COLOR_NORMAL = 0xFFFFFFFF.toInt()
    const val DEFAULT_COLOR_CHARGING = 0xFF34C759.toInt()
    const val DEFAULT_COLOR_LOW = 0xFFFF3B30.toInt()
    const val DEFAULT_LOW_LEVEL = 20

    /** 电量环尺寸上下限（dp） */
    const val SIZE_MIN_DP = 10f
    const val SIZE_MAX_DP = 48f

    /** 位置偏移上下限（dp） */
    const val OFFSET_MIN_DP = -24f
    const val OFFSET_MAX_DP = 24f

    /** 线条粗细上下限（dp） */
    const val STROKE_MIN_DP = 1f
    const val STROKE_MAX_DP = 10f

    /** 低电量阈值上下限（百分比） */
    const val LOW_LEVEL_MIN = 5
    const val LOW_LEVEL_MAX = 50

    data class Cfg(
        val enabled: Boolean = DEFAULT_ENABLED,
        val sizeDp: Float = DEFAULT_SIZE_DP,
        val offsetXDp: Float = DEFAULT_OFFSET_X_DP,
        val offsetYDp: Float = DEFAULT_OFFSET_Y_DP,
        val strokeWidthDp: Float = DEFAULT_STROKE_DP,
        val colorNormal: Int = DEFAULT_COLOR_NORMAL,
        val colorCharging: Int = DEFAULT_COLOR_CHARGING,
        val colorLow: Int = DEFAULT_COLOR_LOW,
        val lowLevelThreshold: Int = DEFAULT_LOW_LEVEL,
    )

    fun defaults(): Cfg = Cfg()

    fun read(prefs: SharedPreferences): Cfg = Cfg(
        enabled = prefs.getBoolean(KEY_ENABLED, DEFAULT_ENABLED),
        sizeDp = prefs.getFloat(KEY_SIZE_DP, DEFAULT_SIZE_DP),
        offsetXDp = prefs.getFloat(KEY_OFFSET_X_DP, DEFAULT_OFFSET_X_DP),
        offsetYDp = prefs.getFloat(KEY_OFFSET_Y_DP, DEFAULT_OFFSET_Y_DP),
        strokeWidthDp = prefs.getFloat(KEY_STROKE_DP, DEFAULT_STROKE_DP),
        colorNormal = prefs.getInt(KEY_COLOR_NORMAL, DEFAULT_COLOR_NORMAL),
        colorCharging = prefs.getInt(KEY_COLOR_CHARGING, DEFAULT_COLOR_CHARGING),
        colorLow = prefs.getInt(KEY_COLOR_LOW, DEFAULT_COLOR_LOW),
        lowLevelThreshold = prefs.getInt(KEY_LOW_LEVEL, DEFAULT_LOW_LEVEL),
    )
}
