package io.github.imroxy2222.lsp_coloros17cyclebattery

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.imroxy2222.lsp_coloros17cyclebattery.ui.theme.ColorOS17CycleBatteryTheme
import io.github.imroxy2222.lsp_coloros17cyclebattery.xposed.BatteryConfig
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ColorOS17CycleBatteryTheme {
                SettingsScreen()
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    var prefs by remember { mutableStateOf<SharedPreferences?>(null) }
    var serviceBound by remember { mutableStateOf(false) }
    var cfg by remember { mutableStateOf(BatteryConfig.defaults()) }

    DisposableEffect(Unit) {
        val listener = object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                serviceBound = true
                try {
                    val p = service.getRemotePreferences(BatteryConfig.PREF_GROUP)
                    prefs = p
                    cfg = BatteryConfig.read(p)
                    p.registerOnSharedPreferenceChangeListener { sp, _ ->
                        cfg = BatteryConfig.read(sp)
                    }
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
            }

            override fun onServiceDied(service: XposedService) {
                serviceBound = false
                prefs = null
            }
        }
        XposedServiceHelper.registerListener(listener)
        onDispose { }
    }

    fun commit(block: (SharedPreferences.Editor) -> Unit) {
        prefs?.edit()?.let { editor ->
            block(editor)
            editor.apply()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "ColorOS 17 环形电量",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )

        if (!serviceBound) {
            StatusCard(
                title = "未连接到 Xposed 服务",
                detail = "请在 LSPosed 中启用本模块（作用域已静态锁定为系统界面），然后重启\"系统界面\"应用即可生效，无需重启手机。",
                error = true,
            )
        } else {
            StatusCard(
                title = "已连接 Xposed 服务",
                detail = "作用域已静态锁定为 com.android.systemui（SystemUI），无需手动勾选。",
                error = false,
            )
        }
        // ---------------- 开关 ----------------
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("启用摄像头电量环", modifier = Modifier.weight(1f), fontSize = 16.sp)
                Switch(
                    checked = cfg.enabled,
                    onCheckedChange = { v ->
                        cfg = cfg.copy(enabled = v)
                        commit { it.putBoolean(BatteryConfig.KEY_ENABLED, v) }
                    },
                )
            }
        }
        StatusCard(
            title = "隐藏原电池图标",
            detail = "请在系统设置（或 ColorOS 的状态栏设置）中隐藏电池图标，隐藏后 WiFi、信号等图标会自动补位。本模块不改动系统图标，只负责在摄像头周围绘制电量环。",
            error = false,
        )

        // ---------------- 大小 / 位置 / 线宽 ----------------
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("外观（圆心 = 摄像头切孔中心）", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                SliderRow(
                    label = "环外径",
                    value = cfg.sizeDp,
                    range = BatteryConfig.SIZE_MIN_DP..BatteryConfig.SIZE_MAX_DP,
                    decimals = 0,
                    unit = "dp",
                ) { v ->
                    cfg = cfg.copy(sizeDp = v)
                    commit { it.putFloat(BatteryConfig.KEY_SIZE_DP, v) }
                }
                SliderRow(
                    label = "水平偏移（右为正）",
                    value = cfg.offsetXDp,
                    range = BatteryConfig.OFFSET_MIN_DP..BatteryConfig.OFFSET_MAX_DP,
                    decimals = 1,
                    unit = "dp",
                ) { v ->
                    cfg = cfg.copy(offsetXDp = v)
                    commit { it.putFloat(BatteryConfig.KEY_OFFSET_X_DP, v) }
                }
                SliderRow(
                    label = "垂直偏移（下为正）",
                    value = cfg.offsetYDp,
                    range = BatteryConfig.OFFSET_MIN_DP..BatteryConfig.OFFSET_MAX_DP,
                    decimals = 1,
                    unit = "dp",
                ) { v ->
                    cfg = cfg.copy(offsetYDp = v)
                    commit { it.putFloat(BatteryConfig.KEY_OFFSET_Y_DP, v) }
                }
                SliderRow(
                    label = "线条粗细",
                    value = cfg.strokeWidthDp,
                    range = BatteryConfig.STROKE_MIN_DP..BatteryConfig.STROKE_MAX_DP,
                    decimals = 1,
                    unit = "dp",
                ) { v ->
                    cfg = cfg.copy(strokeWidthDp = v)
                    commit { it.putFloat(BatteryConfig.KEY_STROKE_DP, v) }
                }
                SliderRow(
                    label = "低电量阈值",
                    value = cfg.lowLevelThreshold.toFloat(),
                    range = BatteryConfig.LOW_LEVEL_MIN.toFloat()..BatteryConfig.LOW_LEVEL_MAX.toFloat(),
                    decimals = 0,
                    unit = "%",
                ) { v ->
                    val i = v.roundToInt()
                    cfg = cfg.copy(lowLevelThreshold = i)
                    commit { it.putInt(BatteryConfig.KEY_LOW_LEVEL, i) }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // ---------------- 三态颜色 ----------------
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("状态颜色", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                ColorRow(
                    label = "普通状态",
                    current = cfg.colorNormal,
                ) { c ->
                    cfg = cfg.copy(colorNormal = c)
                    commit { it.putInt(BatteryConfig.KEY_COLOR_NORMAL, c) }
                }
                ColorRow(
                    label = "充电状态",
                    current = cfg.colorCharging,
                ) { c ->
                    cfg = cfg.copy(colorCharging = c)
                    commit { it.putInt(BatteryConfig.KEY_COLOR_CHARGING, c) }
                }
                ColorRow(
                    label = "低电量状态",
                    current = cfg.colorLow,
                ) { c ->
                    cfg = cfg.copy(colorLow = c)
                    commit { it.putInt(BatteryConfig.KEY_COLOR_LOW, c) }
                }
            }
        }

        StatusCard(
            title = "提示",
            detail = "所有参数（大小/位置/粗细/颜色）均即时生效，无需重启系统界面。电量与充电状态由系统电池广播驱动。电量环不响应触摸，不影响任何应用的正常操作。",
            error = false,
        )
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    decimals: Int,
    unit: String,
    onCommit: (Float) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    // 拖动期间使用本地值，松手才写入，避免频繁 IPC
    var local by remember(value) { mutableStateOf(value) }
    var text by remember(value) { mutableStateOf(formatNum(value, decimals)) }

    fun commitText() {
        val parsed = text.toFloatOrNull()?.coerceIn(range.start, range.endInclusive)
        if (parsed != null) {
            local = parsed
            onCommit(parsed)
        }
        text = formatNum(local, decimals)
        focusManager.clearFocus()
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontSize = 14.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { input ->
                        // 仅允许数字、小数点、负号
                        if (input.matches(Regex("^-?[0-9]*\\.?[0-9]*$"))) {
                            text = input
                        }
                    },
                    modifier = Modifier.width(96.dp),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { commitText() }),
                    trailingIcon = { Text(unit, fontSize = 11.sp) },
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(formatNum(local, decimals), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
        Slider(
            value = local,
            onValueChange = {
                local = it
                text = formatNum(it, decimals)
            },
            valueRange = range,
            onValueChangeFinished = { onCommit(local) },
        )
    }
}

private fun formatNum(v: Float, decimals: Int): String =
    if (decimals == 0) {
        v.roundToInt().toString()
    } else {
        String.format(Locale.US, "%.${decimals}f", v)
    }

@Composable
private fun ColorRow(label: String, current: Int, onPick: (Int) -> Unit) {
    val focusManager = LocalFocusManager.current
    var hex by remember(current) { mutableStateOf("#%06X".format(current and 0xFFFFFF)) }
    var invalid by remember { mutableStateOf(false) }

    fun commitHex() {
        val cleaned = hex.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
        if (!cleaned.matches(Regex("^[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$"))) {
            invalid = true
            return
        }
        val argb = cleaned.toLong(16)
        // 6 位视为不透明；8 位为 AARRGGBB
        val color = (if (cleaned.length == 6) argb or 0xFF000000L else argb).toInt()
        onPick(color)
        hex = "#%06X".format(color and 0xFFFFFF)
        invalid = false
        focusManager.clearFocus()
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color(current))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(label, fontSize = 14.sp)
            }
            OutlinedTextField(
                value = hex,
                onValueChange = {
                    hex = it
                    invalid = false
                },
                modifier = Modifier.width(150.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                label = { Text("Hex", fontSize = 11.sp) },
                isError = invalid,
                supportingText = if (invalid) {
                    { Text("格式：34C759 / #34C759 / FF34C759", fontSize = 10.sp) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { commitHex() }),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PALETTE.forEach { c ->
                val selected = c == current
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(c))
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = CircleShape,
                        )
                        .clickable { onPick(c) },
                )
            }
        }
    }
}

@Composable
private fun StatusCard(title: String, detail: String, error: Boolean) {
    val container = if (error) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Text(detail, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

private val PALETTE = longArrayOf(
    0xFFFFFFFF, 0xFF000000, 0xFF9E9E9E,
    0xFFF44336, 0xFFE91E63, 0xFF9C27B0,
    0xFF673AB7, 0xFF3F51B5, 0xFF2196F3,
    0xFF00BCD4, 0xFF009688, 0xFF4CAF50,
    0xFF8BC34A, 0xFFFFC107, 0xFFFF9800,
    0xFFFF5722, 0xFF34C759, 0xFFFF3B30,
).map { it.toInt() }
