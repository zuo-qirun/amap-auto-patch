# AI 二改提示词

把本页提示词发给 AI 时，建议同时附上相关文件内容，或让 AI 先读取对应文件。不要让 AI 直接改构建产物、解包目录或 APK。

## 修改悬浮窗样式

```text
请修改 amap_auto_patch 项目的整合版悬浮窗 UI。优先改 companion/src/main/java/com/autonavi/companion/OverlayService.java 和 companion/src/main/res/layout/*.xml，不要直接改 smali 或 APK。

目标：
1. 保持 PatchRuntime/PatchBridge 的桥接接口不变。
2. 保持 MainActivity、DiagnosticActivity、OverlayService 的组件名不变。
3. 修改 OverlayService 的面板构建/渲染逻辑，或修改 companion 布局 XML。
4. 保证导航和红绿灯标签大小一致。
5. 保证文字变长时不会让悬浮窗频繁抖动。
6. 改完后运行 .\patch.ps1 构建真实改包，至少确认 Java 编译、apktool build、apksigner verify 通过。

请先说明会改哪些方法，再直接实现。
```

## 修改显示字段

```text
请修改 amap_auto_patch 整合版的悬浮窗显示内容。优先改 companion/src/main/java/com/autonavi/companion/OverlayService.java；红绿灯 wrapper 字段映射在 companion/src/main/java/com/autonavi/companion/PatchBridge.java。

目标：
1. 导航数据来自 KEY_TYPE=10001 的 AUTONAVI_STANDARD_BROADCAST_SEND。
2. 红绿灯优先来自 PatchBridge.onTrafficLightWrapper() 转出的 lightsData extra。
3. 保留 OverlayService 现有广播接收、缓存、轮播和渲染结构。
4. 如果新增分类，补齐 OverlayService 的缓存、过期判断、轮播和渲染逻辑。
5. 保留无效红绿灯过滤，倒计时和状态都无效时不要显示。
6. 改完后运行 .\patch.ps1。

请给出修改后的字段优先级和 adb 回放命令。
```

## 恢复巡航显示

```text
请让 amap_auto_patch 的悬浮窗恢复巡航显示。

要求：
1. 不删除 OverlayService 现有巡航解析和缓存逻辑。
2. 在 OverlayService 的显示/轮播判断中恢复巡航分类。
3. 确保导航、红绿灯、巡航按固定时间轮播。
4. 同一分类收到新数据时立即刷新。
5. 若巡航数据过期，不显示巡航。
6. 修改后运行 .\patch.ps1。
```

## 适配新高德版本

```text
请帮我为 amap_auto_patch 适配一个新的高德车机版 APK。

请先读取：
1. profiles/9.1.0.600087.json
2. patch.ps1
3. docs/wiki/如何适配新版本.md

目标：
1. 新建 profiles/<version>.json。
2. 计算输入 APK SHA-256。
3. 用 apktool 完整解包目标 APK，因为整合版需要合并 companion 资源。
4. 找到 Application.onCreate 注入点。
5. 找到原悬浮窗 WindowManager.addView 替换点。
6. 找到原悬浮窗 removeView 清理点。
7. 找到红绿灯 wrapper 更新点。
8. 运行 patch.ps1 构建并修复 marker。

不要提交原 APK、改包 APK、apktool.jar、build/、dist/。
```

## 修改红绿灯 wrapper 字段

```text
请检查并修改 companion/src/main/java/com/autonavi/companion/PatchBridge.java 中 onTrafficLightWrapper()/wrapperToExtras() 的字段映射。

背景：
当前代码假设 wrapper.a 是灯组列表，item.d 是状态，item.e 是倒计时，item.c 是方向，item.a 是等待数量，item.f 是显示类型。

要求：
1. 保留 PatchBridge 的反射读取方式。
2. 如果新版本字段变化，调整字段名和评分逻辑。
3. 选择最有效的灯组，避免 status=0,countDown=0 的无效数据。
4. 在日志中保留足够信息方便实车验证。
```

## 修改 APK 构造流程

```text
请修改 amap_auto_patch 的 patch.ps1 构造流程。

要求：
1. 不改变默认安全边界：校验 SHA-256，不上传或生成仓库内二进制。
2. 继续完整解码资源并合并 companion/src/main/res。
3. 继续把 runtime 和 companion smali 合并进原 classes.dex。
4. 继续 zipalign、apksigner sign、apksigner verify。
5. 真实构建继续压缩 native .so，避免 APK 变大。
6. 所有路径操作要避免误删工作区外目录。
```
