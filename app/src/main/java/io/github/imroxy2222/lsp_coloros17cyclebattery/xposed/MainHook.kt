package io.github.imroxy2222.lsp_coloros17cyclebattery.xposed

import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import dalvik.system.PathClassLoader
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.io.File
import java.lang.ref.WeakReference
import kotlin.math.roundToInt
import org.luckypray.dexkit.DexKitBridge

/**
 * 模块入口（libxposed API 102，META-INF/xposed/java_init.list 注册）。
 *
 * 作用域（META-INF/xposed/scope.list + module.prop 的 staticScope=true）被静态锁定为
 * com.android.systemui，用户无法在 LSPosed 中勾选其他应用。
 *
 * Hook 点（ColorOS 17 SystemUI）：
 *  1. BatteryContentDrawableFactory#createContentDrawable(Context, int, int)
 *     —— 电量图标的工厂方法，返回我们的 RingBatteryDrawable 接管绘制。
 *  2. StatBatteryMeterView#setBatteryStyleDrawable(BatteryContentDrawableBase)
 *     —— 拿到承载视图，应用用户配置的大小与位置。
 *
 * 若类名被混淆导致按名查找失败，回退到 DexKit 按结构搜索。
 * 所有 Hook 均设置 ExceptionMode.PROTECTIVE 并逐层 try-catch，保证 SystemUI 不崩溃。
 */
class MainHook : XposedModule() {

    companion object {
        private const val TAG = "CycleBattery"
        private const val TARGET_PACKAGE = "com.android.systemui"

        private const val DRAWABLE_CLASS =
            "io.github.imroxy2222.lsp_coloros17cyclebattery.xposed.RingBatteryDrawable"
        private const val FACTORY_CLASS =
            "com.oplus.systemui.statusbar.pipeline.battery.ui.drawable.BatteryContentDrawableFactory"
        private const val VIEW_CLASS =
            "com.oplus.systemui.statusbar.pipeline.battery.ui.view.StatBatteryMeterView"
        private const val VIEW_METHOD = "setBatteryStyleDrawable"
        private const val FACTORY_METHOD = "createContentDrawable"

        /** 系统圆环 drawable 的日志 TAG，用作 DexKit 字符串特征 */
        private const val CIRCLE_TAG = "CircleBatteryContentDrawable"
    }

    private val prefs: SharedPreferences by lazy {
        getRemotePreferences(BatteryConfig.PREF_GROUP)
    }

    /** 模块 dex 中的 RingBatteryDrawable（父加载器为 SystemUI 的 ClassLoader） */
    private var ringDrawableClass: Class<*>? = null

    /** 最近一次 hook 到的电量视图，配置变化时重新应用大小/位置 */
    private var lastBatteryView: WeakReference<View?> = WeakReference(null)

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        super.onModuleLoaded(param)
        log(Log.INFO, TAG, "module loaded, process=${param.processName}")
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        super.onPackageLoaded(param)
        if (param.packageName != TARGET_PACKAGE) return
        try {
            hookSystemUI(param)
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "hookSystemUI failed", t)
        }
    }

    // ------------------------------------------------------------------
    // Hook 编排
    // ------------------------------------------------------------------

    private fun hookSystemUI(param: PackageLoadedParam) {
        val hostCl = param.defaultClassLoader
        val moduleInfo = moduleApplicationInfo

        // 模块 APK 的类加载器，父加载器指向 SystemUI，
        // 使 RingBatteryDrawable 能解析到宿主真实的 BatteryContentDrawableBase
        val moduleCl = PathClassLoader(moduleInfo.sourceDir, moduleInfo.nativeLibraryDir, hostCl)
        ringDrawableClass = try {
            moduleCl.loadClass(DRAWABLE_CLASS)
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "cannot load RingBatteryDrawable from module apk", t)
            return
        }

        registerPrefsListener()
        hookDrawableFactory(hostCl, param.applicationInfo?.sourceDir)
        hookBatteryView(hostCl)
    }

    private fun hookDrawableFactory(hostCl: ClassLoader, hostApk: String?) {
        try {
            val factoryCls = findFactoryClass(hostCl, hostApk)
            val method = factoryCls?.declaredMethods?.firstOrNull {
                it.name == FACTORY_METHOD && it.parameterTypes.size == 3
            }
            if (method == null) {
                log(Log.WARN, TAG, "$FACTORY_METHOD not found, ring battery disabled")
                return
            }
            hook(method)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        return try {
                            val cfg = readCfg()
                            if (!cfg.enabled) {
                                // 用户关闭：放行原始逻辑
                                chain.proceed()
                            } else {
                                newRingDrawable(chain.getArg(0) as Context)
                            }
                        } catch (t: Throwable) {
                            log(Log.WARN, TAG, "factory hook fallback to original", t)
                            try {
                                chain.proceed()
                            } catch (e: Throwable) {
                                null
                            }
                        }
                    }
                })
            log(Log.INFO, TAG, "hooked factory: $method")
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "hook factory failed", t)
        }
    }

    private fun hookBatteryView(hostCl: ClassLoader) {
        try {
            val viewCls = hostCl.loadClass(VIEW_CLASS)
            val method = viewCls.declaredMethods.firstOrNull {
                it.name == VIEW_METHOD && it.parameterTypes.size == 1
            }
            if (method == null) {
                log(Log.WARN, TAG, "$VIEW_METHOD not found, size/position adjust disabled")
                return
            }
            hook(method)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val result = try {
                            chain.proceed()
                        } catch (t: Throwable) {
                            null
                        }
                        try {
                            val view = chain.thisObject as? View
                            if (view != null) {
                                lastBatteryView = WeakReference(view)
                                applyViewConfig(view)
                            }
                        } catch (t: Throwable) {
                            log(Log.WARN, TAG, "apply view config failed", t)
                        }
                        return result
                    }
                })
            log(Log.INFO, TAG, "hooked $VIEW_CLASS#$VIEW_METHOD")
        } catch (t: Throwable) {
            log(Log.WARN, TAG, "hook battery view failed", t)
        }
    }

    // ------------------------------------------------------------------
    // 配置
    // ------------------------------------------------------------------

    private fun readCfg(): BatteryConfig.Cfg = try {
        BatteryConfig.read(prefs)
    } catch (_: Throwable) {
        BatteryConfig.defaults()
    }

    private fun registerPrefsListener() {
        try {
            prefs.registerOnSharedPreferenceChangeListener { _, _ ->
                try {
                    lastBatteryView.get()?.let { applyViewConfig(it) }
                } catch (_: Throwable) {
                }
            }
        } catch (t: Throwable) {
            log(Log.WARN, TAG, "register prefs listener failed", t)
        }
    }

    private fun newRingDrawable(ctx: Context): Drawable {
        val cls = ringDrawableClass
            ?: throw IllegalStateException("RingBatteryDrawable not loaded")
        val ctor = cls.getConstructor(
            Context::class.java,
            Int::class.javaPrimitiveType,
            SharedPreferences::class.java,
        )
        return ctor.newInstance(ctx, 0, prefs) as Drawable
    }

    // ------------------------------------------------------------------
    // 视图大小 / 位置
    // ------------------------------------------------------------------

    private fun applyViewConfig(root: View) {
        val cfg = readCfg()
        if (!cfg.enabled) return
        val target = findRingImageView(root) ?: return
        val density = target.resources.displayMetrics.density
        val sizePx = (cfg.sizeDp * density).roundToInt().coerceAtLeast(1)

        val lp = target.layoutParams
        if (lp != null && (lp.width != sizePx || lp.height != sizePx)) {
            lp.width = sizePx
            lp.height = sizePx
            target.layoutParams = lp
        }
        target.translationX = cfg.offsetXDp * density
        target.translationY = cfg.offsetYDp * density
        target.invalidate()
    }

    private fun findRingImageView(root: View): ImageView? {
        if (root is ImageView && root.drawable?.javaClass?.name == DRAWABLE_CLASS) {
            return root
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findRingImageView(root.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    // ------------------------------------------------------------------
    // 类查找（直接类名优先，混淆时回退 DexKit）
    // ------------------------------------------------------------------

    private fun findFactoryClass(hostCl: ClassLoader, hostApk: String?): Class<*>? {
        return try {
            hostCl.loadClass(FACTORY_CLASS)
        } catch (first: Throwable) {
            log(Log.WARN, TAG, "factory not found by name, trying DexKit", first)
            if (hostApk == null) return null
            try {
                initDexKit()
                val bridge = DexKitBridge.create(hostApk) ?: return null
                bridge.use {
                    // 第一步：用日志 TAG 字符串特征定位系统圆环 drawable 类
                    val circle = bridge.findClass {
                        matcher {
                            usingStrings(CIRCLE_TAG)
                        }
                    }?.firstOrNull() ?: return null
                    log(Log.INFO, TAG, "DexKit found circle drawable: ${circle.name}")

                    // 第二步：定位工厂类 —— 拥有 (Context, int, int) -> 圆环drawable 的方法
                    val factory = bridge.findClass {
                        matcher {
                            methods {
                                add {
                                    paramTypes("android.content.Context", "int", "int")
                                    returnType = circle.name
                                }
                            }
                        }
                    }?.firstOrNull() ?: return null
                    log(Log.INFO, TAG, "DexKit found factory: ${factory.name}")
                    hostCl.loadClass(factory.name)
                }
            } catch (t: Throwable) {
                log(Log.ERROR, TAG, "DexKit fallback failed", t)
                null
            }
        }
    }

    private fun initDexKit() {
        // 优先显式加载模块 APK 解压出的 so，失败再走 loadLibrary
        val so = File(moduleApplicationInfo.nativeLibraryDir, "libdexkit.so")
        if (so.exists()) {
            System.load(so.absolutePath)
        } else {
            System.loadLibrary("dexkit")
        }
    }
}
