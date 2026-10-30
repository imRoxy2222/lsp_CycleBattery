package io.github.imroxy2222.lsp_coloros17cyclebattery.xposed;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.BatteryManager;
import android.view.DisplayCutout;
import android.view.View;

/**
 * 摄像头环形电量 View（纯 Java 实现，零 kotlin-stdlib 依赖）。
 *
 * 由 MainHook 在 SystemUI 进程内通过 WindowManager 添加为一个
 * 不可触摸、不获焦、全屏透明的系统内部窗口（TYPE_STATUS_BAR_SUB_PANEL），
 * 以屏幕摄像头切孔（DisplayCutout）的中心为圆心自绘电量环。
 *
 * 状态来源：ACTION_BATTERY_CHANGED 系统广播（电量、是否充电）。
 * 配置来源：MainHook 通过反射调用 applyConfig() 推送（volatile 字段）。
 *
 * 该类运行在 SystemUI 进程，任何异常都必须内部消化，不允许抛出。
 */
public class CameraRingView extends View {

    // ---- 用户配置（volatile，由 MainHook 线程推送） ----
    private volatile float sizeDp = 60f;
    private volatile float offsetXDp = 0f;
    private volatile float offsetYDp = 0f;
    private volatile float strokeWidthDp = 2.5f;
    private volatile int colorNormal = 0xFFFFFFFF;
    private volatile int colorCharging = 0xFF34C759;
    private volatile int colorLow = 0xFFFF3B30;
    private volatile int lowLevelThreshold = 20;

    // ---- 电池状态 ----
    private volatile int level = -1;
    private volatile boolean charging = false;

    private final Paint trackPaint;
    private final Paint levelPaint;
    private final RectF ringRect = new RectF();

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            readBattery(intent);
        }
    };

    public CameraRingView(Context context) {
        super(context);
        trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        levelPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        levelPaint.setStyle(Paint.Style.STROKE);
        levelPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    /** 由 MainHook 通过反射调用，推送最新配置（即时生效） */
    public void applyConfig(float sizeDp, float offsetXDp, float offsetYDp, float strokeWidthDp,
                            int colorNormal, int colorCharging, int colorLow, int lowLevelThreshold) {
        this.sizeDp = sizeDp;
        this.offsetXDp = offsetXDp;
        this.offsetYDp = offsetYDp;
        this.strokeWidthDp = strokeWidthDp;
        this.colorNormal = colorNormal;
        this.colorCharging = colorCharging;
        this.colorLow = colorLow;
        this.lowLevelThreshold = lowLevelThreshold;
        postInvalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        try {
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            // 系统广播，sticky 注册返回值即为当前电池状态
            Intent sticky = getContext().registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            if (sticky != null) {
                readBattery(sticky);
            }
        } catch (Throwable t1) {
            try {
                getContext().registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        try {
            getContext().unregisterReceiver(batteryReceiver);
        } catch (Throwable ignored) {
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // 旋转等尺寸变化后重算摄像头位置
        postInvalidate();
    }

    private void readBattery(Intent intent) {
        try {
            int raw = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            level = (raw >= 0 && scale > 0) ? Math.round(raw * 100f / scale) : raw;
            charging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
            postInvalidate();
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        try {
            drawRing(canvas);
        } catch (Throwable ignored) {
            // SystemUI 进程内禁止抛出异常
        }
    }

    private void drawRing(Canvas canvas) {
        // -1 表示电量未知：不绘制
        if (level < 0) return;
        if (getWidth() == 0 || getHeight() == 0) return;

        float density = getResources().getDisplayMetrics().density;
        float[] center = cameraCenter();
        float cx = center[0] + offsetXDp * density;
        float cy = center[1] + offsetYDp * density;
        float stroke = Math.max(strokeWidthDp * density, 1f);
        float radius = Math.max(sizeDp * density / 2f - stroke / 2f, 1f);

        int color = charging ? colorCharging
                : (level <= lowLevelThreshold ? colorLow : colorNormal);

        ringRect.set(cx - radius, cy - radius, cx + radius, cy + radius);

        // 背景轨道：同色 20% 透明度的整圆
        trackPaint.setStrokeWidth(stroke);
        trackPaint.setColor((color & 0x00FFFFFF) | 0x33000000);
        canvas.drawArc(ringRect, 0f, 360f, false, trackPaint);

        // 进度环：从顶部顺时针，level * 3.6 度
        int clamped = Math.max(0, Math.min(level, 100));
        levelPaint.setStrokeWidth(stroke);
        levelPaint.setColor(color);
        canvas.drawArc(ringRect, -90f, clamped * 3.6f, false, levelPaint);
    }

    /** 计算摄像头切孔中心（当前屏幕坐标系），找不到切孔时回退到屏幕顶部中央 */
    private float[] cameraCenter() {
        float[] out = new float[2];
        try {
            DisplayCutout cutout = getDisplay() == null ? null : getDisplay().getCutout();
            Rect r = cutoutRect(cutout);
            if (r != null && !r.isEmpty()) {
                out[0] = r.exactCenterX();
                out[1] = r.exactCenterY();
                return out;
            }
        } catch (Throwable ignored) {
        }
        out[0] = getWidth() / 2f;
        out[1] = dp(24f);
        return out;
    }

    /** 取四边中第一个非空的切孔矩形 */
    private Rect cutoutRect(DisplayCutout cutout) {
        if (cutout == null) return null;
        Rect r = cutout.getBoundingRectTop();
        if (r != null && !r.isEmpty()) return r;
        r = cutout.getBoundingRectLeft();
        if (r != null && !r.isEmpty()) return r;
        r = cutout.getBoundingRectRight();
        if (r != null && !r.isEmpty()) return r;
        r = cutout.getBoundingRectBottom();
        if (r != null && !r.isEmpty()) return r;
        return null;
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
