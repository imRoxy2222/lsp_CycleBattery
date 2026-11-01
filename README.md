# 因为懒得管理密钥与密码, 已经将它们一起上传github

# 因此本仓库禁止pr, 认准action发布的release

# 环形电量

在挖孔摄像头周围绘制一圈电量环的 [LSPosed](https://github.com/LSPosed/LSPosed) 模块。

## 功能

- 电量环绘制在前置摄像头切孔周围（自动从系统 `DisplayCutout` 读取位置）
- 三态颜色可自定义：普通 / 充电 / 低电量（支持色板选择与 Hex 手动输入）
- 外观可调：环大小、水平/垂直偏移、线条粗细（滑条 + 数值手动输入）
- 所有配置即时生效（RemotePreferences 实时推送，无需重启系统界面）
- 横屏自动隐藏，竖屏自动恢复
- 可选隐藏桌面图标，从 LSPosed 管理器启动
- **不 hook 任何 SystemUI 私有类**（唯一 hook 点为框架 `Instrumentation`），对 ROM 更新免疫

## 配合系统设置

建议在系统设置中隐藏原电池图标（如 ColorOS：设置 → 通知与状态栏 → 状态栏 → 电池图标），
隐藏后 WiFi、信号等图标会自动补位，电量环独立显示在摄像头周围。

## 环境要求

- Android 15+（minSdk 35）
- LSPosed 框架（libxposed API 102）
- 作用域：系统界面（`com.android.systemui`，已静态锁定，无需手动勾选）

## 安装

1. 在 LSPosed 管理器中启用本模块
2. 重启系统界面（或 `adb shell killall com.android.systemui`）
3. 打开模块 App 调整参数

## 说明

- 电量环窗口为不可触摸的系统内部窗口，不占用悬浮窗权限，
  不会触发其他应用的触摸遮挡检测（`FLAG_WINDOW_IS_OBSCURED`）
- 锁屏界面下电量环不可见（被锁屏窗口覆盖）
