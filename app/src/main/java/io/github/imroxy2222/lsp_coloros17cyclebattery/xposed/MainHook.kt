package io.github.imroxy2222.lsp_coloros17cyclebattery.xposed

import android.app.Application
import android.app.Instrumentation
import android.content.Context
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import dalvik.system.PathClassLoader
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * 模块入口（libxposed API 102，META-INF/xposed/java_init.list 注册）。
 *
 * 作用域（META-INF/xposed/scope.list + module.prop 的 staticScope=true）被静态锁定为
 * com.android.systemui，用户无法在 LSPosed 中勾选其他应用。
 *
 * 实现方式：**不 hook 任何宿主（SystemUI）类**。
 *  - 唯一的 Hook 是框架方法 Instrumentation#callApplicationOnCreate，
 *    它永远不会被混淆，仅在 SystemUI 进程内生效，用于拿到 Application 上下文和启动时机。
 *  - 随后在 SystemUI 进程内通过 WindowManager 添加一个全屏透明、不可触摸的系统内部
 *    窗口（TYPE_STATUS_BAR_SUB_PANEL），由 CameraRingView 以摄像头切孔为中心绘制电量环。
 *  - 隐藏系统电池图标请使用 ColorOS 自带的设置项，其他状态栏图标会自然补位。
 *
 * 该方案不需要 DexKit；Hook 设置 ExceptionMode.PROTECTIVE 并逐层 try-catch，
 * 保证 SystemUI 不崩溃。
 */
class MainHook : XposedModule() {

    companion object {
        private const val TAG = "CycleBattery"
        private const val TARGET_PACKAGE = "com.android.systemui"

        /** 模块自绘视图类（由模块 dex 经 PathClassLoader 加载） */
        private const val RING_VIEW_CLASS =
            "io.github.imroxy2222.lsp_coloros17cyclebattery.xposed.CameraRingView"
    }

    private val prefs: SharedPreferences by lazy {
        getRemotePreferences(BatteryConfig.PREF_GROUP)
    }

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    /** 模块 dex 中的 CameraRingView 类 */
    private var ringViewClass: Class<*>? = null

    /** SystemUI Application 上下文（Application onCreate 后可用） */
    private var appContext: Context? = null

    /** 当前已添加的悬浮窗视图 */
    private var overlayView: View? = null

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        super.onModuleLoaded(param)
        log(Log.INFO, TAG, "module loaded, process=${param.processName}")
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        super.onPackageLoaded(param)
        if (param.packageName != TARGET_PACKAGE) return
        try {
            val moduleInfo = moduleApplicationInfo
            // 模块 APK 的类加载器（父加载器指向宿主，便于将来解析宿主类）
            val moduleCl = PathClassLoader(
                moduleInfo.sourceDir,
                moduleInfo.nativeLibraryDir,
                param.defaultClassLoader,
            )
            ringViewClass = try {
                moduleCl.loadClass(RING_VIEW_CLASS)
            } catch (t: Throwable) {
                log(Log.ERROR, TAG, "cannot load CameraRingView from module apk", t)
                return
            }

            registerPrefsListener()
            hookApplicationCreate()
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "hookSystemUI failed", t)
        }
    }

    // ------------------------------------------------------------------
    // Hook：SystemUI Application 创建完成（拿到上下文的启动时机）
    // ------------------------------------------------------------------

    private fun hookApplicationCreate() {
        try {
            val method = Instrumentation::class.java.getDeclaredMethod(
                "callApplicationOnCreate",
                Application::class.java,
            )
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
                            val app = chain.getArg(0) as? Application
                            if (app != null) {
                                appContext = app.applicationContext
                                syncOverlay(app.applicationContext)
                            }
                        } catch (t: Throwable) {
                            log(Log.WARN, TAG, "sync overlay failed", t)
                        }
                        return result
                    }
                })
            log(Log.INFO, TAG, "hooked Instrumentation#callApplicationOnCreate")
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "hook application create failed", t)
        }
    }

    // ------------------------------------------------------------------
    // 悬浮窗管理
    // ------------------------------------------------------------------

    private fun registerPrefsListener() {
        try {
            prefs.registerOnSharedPreferenceChangeListener { _, _ ->
                try {
                    val ctx = appContext
                    if (ctx != null) syncOverlay(ctx)
                } catch (_: Throwable) {
                }
            }
        } catch (t: Throwable) {
            log(Log.WARN, TAG, "register prefs listener failed", t)
        }
    }

    /** 按当前配置同步悬浮窗：开启则创建/更新，关闭则移除 */
    private fun syncOverlay(context: Context) {
        mainHandler.post {
            try {
                syncOverlayInternal(context.applicationContext)
            } catch (t: Throwable) {
                log(Log.WARN, TAG, "sync overlay internal failed", t)
            }
        }
    }

    private fun syncOverlayInternal(ctx: Context) {
        val cfg = readCfg()
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        if (!cfg.enabled) {
            overlayView?.let { view ->
                try {
                    wm.removeView(view)
                } catch (_: Throwable) {
                }
            }
            overlayView = null
            return
        }

        val view = overlayView
        if (view != null) {
            applyViewConfig(view)
            return
        }

        val cls = ringViewClass ?: return
        val created = try {
            val ctor = cls.getConstructor(Context::class.java)
            ctor.newInstance(ctx) as View
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "create CameraRingView failed", t)
            return
        }

        try {
            wm.addView(created, buildParams())
            overlayView = created
            log(Log.INFO, TAG, "camera ring overlay added")
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "addView failed", t)
            return
        }
        applyViewConfig(created)
    }

    /**
     * 全屏透明系统窗口参数：
     *  - 类型用系统内部窗口（SystemUI 进程持有 INTERNAL_SYSTEM_WINDOW 权限），
     *    不走 TYPE_APPLICATION_OVERLAY，不占悬浮窗权限、不出现在"显示在应用上层"列表
     *  - NOT_TOUCHABLE 保证不遮挡触摸事件，其他应用的
     *    FLAG_WINDOW_IS_OBSCURED 检测不会触发
     */
    private fun buildParams(): WindowManager.LayoutParams {
        val type = try {
            val f = WindowManager.LayoutParams::class.java.getField("TYPE_STATUS_BAR_SUB_PANEL")
            f.getInt(null)
        } catch (_: Throwable) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            title = "CycleBatteryRing"
        }
    }

    /** 通过反射把配置推送到 CameraRingView（纯 Java 类，避免 Kotlin Intrinsics 问题） */
    private fun applyViewConfig(view: View) {
        try {
            val cfg = readCfg()
            val m = view.javaClass.getMethod(
                "applyConfig",
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            )
            m.invoke(
                view,
                cfg.sizeDp,
                cfg.offsetXDp,
                cfg.offsetYDp,
                cfg.strokeWidthDp,
                cfg.colorNormal,
                cfg.colorCharging,
                cfg.colorLow,
                cfg.lowLevelThreshold,
            )
        } catch (t: Throwable) {
            log(Log.WARN, TAG, "apply view config failed", t)
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
}
