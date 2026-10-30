package com.oplus.systemui.statusbar.pipeline.battery.ui.drawable;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.drawable.Drawable;

/**
 * 编译期桩（compileOnly，不会打进 APK）。
 * 运行时该类由 SystemUI 宿主 APK 提供，此处仅声明模块需要用到的公开 API。
 */
public class BatteryContentDrawableBase extends Drawable {

    public BatteryContentDrawableBase(Context context, int i) {
    }

    public int getBatteryLevel() {
        return 0;
    }

    public int getChargeIconId() {
        return 0;
    }

    public boolean getSpecialColor() {
        return false;
    }

    public void setSpecialColor(boolean z) {
    }

    public void setColors(int progressColor, int backgroundColor, int tintColor) {
    }

    public void onDestroy() {
    }

    @Override
    public void draw(Canvas canvas) {
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return 0;
    }
}
