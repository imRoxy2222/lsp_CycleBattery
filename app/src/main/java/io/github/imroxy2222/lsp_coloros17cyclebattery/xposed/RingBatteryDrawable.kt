package io.github.imroxy2222.lsp_coloros17cyclebattery.xposed

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.oplus.systemui.statusbar.pipeline.battery.ui.drawable.BatteryContentDrawableBase
import kotlin.math.roundToInt

/**
 * 自绘环形电量 Drawable。
 *
 * 继承宿主的 BatteryContentDrawableBase（运行时由 SystemUI 类加载器解析真实类），
 * 因此 ColorOS 的电量管线可以无感知地驱动它：
 *  - setBatteryLevel(level)          -> 电量
 *  - setChargeIconId(id)             -> id > 0 表示充电中（与系统 CircleBatteryContentDrawable 判断一致）
 *  - setColors / setSpecialColor     -> 忽略，改用用户配置的三态颜色
 *
 * 该类会被加载进 SystemUI 进程，任何异常都必须内部消化，不允许抛出。
 */
class RingBatteryDrawable(
    context: Context,
    tint: Int,
    private val prefs: SharedPreferences,
) : BatteryContentDrawableBase(context, tint) {

    private val appContext: Context = context.applicationContext

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val levelPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val ringRect = RectF()

    override fun draw(canvas: Canvas) {
        try {
            val level = getBatteryLevel()
            // -1 表示电量未知，与系统行为一致：不绘制
            if (level == -1) return

            val b = bounds
            if (b.isEmpty) return

            val cfg = try {
                BatteryConfig.read(prefs)
            } catch (_: Throwable) {
                BatteryConfig.defaults()
            }

            val density = appContext.resources.displayMetrics.density
            val stroke = (cfg.strokeWidthDp * density).coerceAtLeast(1f)
            val half = stroke / 2f
            ringRect.set(b.left + half, b.top + half, b.right - half, b.bottom - half)

            val charging = getChargeIconId() > 0
            val color = when {
                charging -> cfg.colorCharging
                level <= cfg.lowLevelThreshold -> cfg.colorLow
                else -> cfg.colorNormal
            }

            // 背景轨道：同色 20% 透明度的整圆
            trackPaint.strokeWidth = stroke
            trackPaint.color = (color and 0x00FFFFFF) or 0x33000000.toInt()
            canvas.drawArc(ringRect, 0f, 360f, false, trackPaint)

            // 进度环：从顶部顺时针，level * 3.6 度
            levelPaint.strokeWidth = stroke
            levelPaint.color = color
            canvas.drawArc(ringRect, -90f, level.coerceIn(0, 100) * 3.6f, false, levelPaint)
        } catch (_: Throwable) {
            // SystemUI 进程内禁止抛出异常
        }
    }

    override fun getIntrinsicWidth(): Int = try {
        dp(BatteryConfig.read(prefs).sizeDp)
    } catch (_: Throwable) {
        dp(BatteryConfig.DEFAULT_SIZE_DP)
    }

    override fun getIntrinsicHeight(): Int = getIntrinsicWidth()

    private fun dp(v: Float): Int = (v * appContext.resources.displayMetrics.density).roundToInt()
}
