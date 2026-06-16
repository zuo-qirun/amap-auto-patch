# AMap Auto Patch

把一个最小化的自定义悬浮窗运行时注入到指定高德地图车机版 APK 内，用自定义悬浮窗替换高德原悬浮窗。目标是让导航、红绿灯等信息直接显示在改版高德里，不再依赖同时安装另一个伴随应用。

当前支持：

- 高德车机版：`9.1.0.600087`
- 包名：`com.autonavi.amapClone`
- 输入 APK SHA-256：见 `profiles/9.1.0.600087.json`
- 输出：`dist/高德地图_9.1.0.600087_amap_auto_patch_signed.apk`

本仓库只保存补丁器、运行时代码和文档，不保存原始 APK、改包 APK、apktool.jar、签名产物或构建产物。请不要公开分发第三方改包 APK。

## 开源协议

本项目代码和文档使用 GPL-3.0 协议发布，详见 `LICENSE`。第三方 APK、第三方工具和高德地图本体不属于本仓库授权范围。

## 文档

完整 Wiki 同步保存在 `docs/wiki/`，GitHub Wiki 发布后也可以从仓库 Wiki 入口阅读：

- `Home.md`：项目概览
- `方法介绍.md`：注入方法、运行时入口、数据来源
- `如何构建.md`：环境、依赖、构建命令
- `如何二改悬浮窗样式.md`：修改布局、配色、尺寸、设置面板
- `如何二改显示信息.md`：修改显示字段、轮播逻辑、红绿灯解析
- `广播协议与字段.md`：标准广播中本项目用到的功能和 extra 字段
- `如何适配新版本.md`：为新高德版本制作 profile
- `如何构造 APK.md`：反编译、注入、回编、对齐、签名、验证流程
- `测试与排错.md`：测试壳、adb 回放、常见问题
- `AI 二改提示词.md`：给 AI 继续改 UI/数据/适配点的提示词模板

## 快速构建

准备 Android SDK build-tools，确保可用 `d8`、`zipalign`、`apksigner`。另需自行提供 apktool：

```text
amap_auto_patch/tools/apktool.jar
```

或者运行时传入：

```powershell
.\patch.ps1 -ApktoolJar C:\path\to\apktool.jar
```

执行：

```powershell
cd D:\Github\红绿灯\amap_auto_patch
.\patch.ps1 -InputApk "D:\Github\红绿灯\(✓)Auto_9.1.0.600087_(Clone红绿灯).apk"
```

## 当前悬浮窗行为

- 替换高德原悬浮窗，不额外安装伴随应用。
- 导航和红绿灯两个分类每 3 秒轮播。
- 同一分类收到新数据时立即刷新当前显示。
- 巡航数据仍会解析和缓存，但当前不参与显示。
- 无效红绿灯数据会过滤，例如 `status=0,countDown=0`。
- 长按悬浮窗打开设置面板，支持启用开关、大小、样式切换。
- 拖动悬浮窗后会保存位置。

## 伴侣整合版

当前构建会把 `amap_companion` 的主要功能合入高德同一个 APK 和同一个进程内：

- 保留 `AMap Companion` 独立桌面入口，用于打开设置、诊断、插件市场和广播回放。
- 保留 `OverlayService`，高德原悬浮窗创建时会启动这个服务并显示自定义悬浮窗。
- 目标应用包名固定为当前高德包名，不再选择外部高德应用。
- 移除 APK 更新、下载已改高德、安装器和 `FileProvider` 相关功能。
- companion 资源会合并到高德资源表；运行时通过当前包名动态解析布局、图标和控件 ID。

如果继续从 `amap_companion` 同步代码，需要同步 `companion/src/main/java` 与 `companion/src/main/res`，并保留上述集成限制。

## 实现概要

- `runtime/` 和 `companion/` 一起编译为 dex，再通过 apktool 转成 smali，合并到原 `classes.dex`。
- `MapApplicationProxy.onCreate()` 注入 `PatchRuntime.init(context)`。
- 原悬浮窗 `WindowManager.addView(...)` 替换为 `PatchRuntime.replaceHostFloatWindow(...)`。
- 原悬浮窗关闭时调用 `PatchRuntime.removePatchOverlay()`，避免自定义悬浮窗残留。
- 红绿灯 wrapper 入口注入 `PatchRuntime.onTrafficLightWrapper(...)`，直接读取高德内部红绿灯对象。
- 导航、巡航、车道、前后台状态通过 `AUTONAVI_STANDARD_BROADCAST_SEND` 动态广播接收。
- apktool 全量解码时可能把部分动画资源解成 `false` 占位，脚本会生成最小空动画 XML 修复重打包。

## 虚拟机验证

真实高德 APK 只包含 `armeabi-v7a` native 库，普通 x86/x86_64 Android Emulator 不能作为真实运行环境。运行时 UI 可先用测试壳验证：

```powershell
.\build-test-apk.ps1
adb -s emulator-5554 install -r -t .\dist\amap_auto_patch_vm_test.apk
adb -s emulator-5554 shell appops set amap.auto.patch.test SYSTEM_ALERT_WINDOW allow
adb -s emulator-5554 shell monkey -p amap.auto.patch.test -c android.intent.category.LAUNCHER 1
adb -s emulator-5554 shell am broadcast -a AUTONAVI_STANDARD_BROADCAST_SEND --ei KEY_TYPE 60073 --ei trafficLightStatus 2 --ei redLightCountDownSeconds 12 --ei dir 1 --ei lightsCount 2
```

真实改包 APK 仍需在 ARM/ARMv7 兼容车机或虚拟环境中最终验证。
