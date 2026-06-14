# AI 二改提示词

把本页提示词发给 AI 时，建议同时附上相关文件内容，或让 AI 先读取对应文件。不要让 AI 直接改构建产物、解包目录或 APK。

## 修改悬浮窗样式

```text
请修改 amap_auto_patch 项目的悬浮窗 UI。优先只改 runtime/src/main/java/amap/auto/patch/OverlayController.java。

目标：
1. 保持 PatchRuntime/DataModel 的接口不变。
2. 保持 modeText、primaryText、secondaryText 三层信息结构，除非确实需要新增控件。
3. 修改 buildPanel()、applyStyle()、buildSettingsView()。
4. 保证导航和红绿灯标签大小一致。
5. 保证文字变长时不会让悬浮窗频繁抖动。
6. 改完后运行 .\build-test-apk.ps1 验证 Java 编译。

请先说明会改哪些方法，再直接实现。
```

## 修改显示字段

```text
请修改 amap_auto_patch 的悬浮窗显示内容。优先改 DataModel.java 和 OverlayController.java。

目标：
1. 导航数据来自 KEY_TYPE=10001 的 AUTONAVI_STANDARD_BROADCAST_SEND。
2. 红绿灯优先来自 DataModel.fromTrafficLightWrapper()。
3. 悬浮窗仍渲染 mode/primary/secondary。
4. 如果新增分类，补齐 DataModel 分类常量、OverlayController 缓存、rememberModel、getFreshModel、DISPLAY_ORDER。
5. 保留无效红绿灯过滤，倒计时和状态都无效时不要显示。
6. 改完后运行 .\build-test-apk.ps1。

请给出修改后的字段优先级和 adb 回放命令。
```

## 恢复巡航显示

```text
请让 amap_auto_patch 的悬浮窗恢复巡航显示。

要求：
1. DataModel 现有 CATEGORY_CRUISE 不要删除。
2. 在 OverlayController.DISPLAY_ORDER 中加入 CATEGORY_CRUISE。
3. 确保导航、红绿灯、巡航按固定时间轮播。
4. 同一分类收到新数据时立即刷新。
5. 若巡航数据过期，不显示巡航。
6. 修改后运行 .\build-test-apk.ps1。
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
3. 用 apktool -r 解包目标 APK。
4. 找到 Application.onCreate 注入点。
5. 找到原悬浮窗 WindowManager.addView 替换点。
6. 找到原悬浮窗 removeView 清理点。
7. 找到红绿灯 wrapper 更新点。
8. 运行 patch.ps1 构建并修复 marker。

不要提交原 APK、改包 APK、apktool.jar、build/、dist/。
```

## 修改红绿灯 wrapper 字段

```text
请检查并修改 DataModel.fromTrafficLightWrapper() 的字段映射。

背景：
当前代码假设 wrapper.a 是灯组列表，item.d 是状态，item.e 是倒计时，item.c 是方向，item.a 是等待数量，item.f 是显示类型。

要求：
1. 保留 PatchRuntime.readIntField/readListField 的反射读取方式。
2. 如果新版本字段变化，调整字段名和评分逻辑。
3. 选择最有效的灯组，避免 status=0,countDown=0 的无效数据。
4. 在日志中保留足够信息方便实车验证。
```

## 修改 APK 构造流程

```text
请修改 amap_auto_patch 的 patch.ps1 构造流程。

要求：
1. 不改变默认安全边界：校验 SHA-256，不上传或生成仓库内二进制。
2. 继续使用 apktool d -r 保留资源。
3. 继续把 runtime smali 合并进原 classes.dex。
4. 继续 zipalign、apksigner sign、apksigner verify。
5. 真实构建继续压缩 native .so，避免 APK 变大。
6. 所有路径操作要避免误删工作区外目录。
```
