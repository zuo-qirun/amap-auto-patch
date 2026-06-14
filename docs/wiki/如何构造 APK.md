# 如何构造 APK

本页说明 `patch.ps1` 如何从原 APK 构造改包 APK。理解这条流水线后，后续可以让 AI 修改某一步，而不是直接手工改 smali。

## 输入和校验

脚本参数：

```powershell
.\patch.ps1 -InputApk "D:\path\to\amap.apk"
```

脚本读取 profile 后会校验：

- 输入 APK 是否存在。
- keystore 是否存在。
- Android SDK 工具是否存在。
- apktool.jar 是否存在。
- 输入 APK SHA-256 是否等于 profile 的 `inputSha256`。

SHA-256 不匹配会直接停止，避免把错误版本打坏。

## 编译 runtime

源码目录：

```text
runtime/src/main/java
```

流程：

1. `javac -encoding UTF-8 -source 8 -target 8` 编译 Java。
2. `d8 --min-api profile.minApi` 转成 `classes.dex`。
3. 复制为 `runtime.dex`。

这个 runtime 包含：

- `PatchRuntime`
- `OverlayController`
- `DataModel`
- `PatchTestActivity`

## 反编译原 APK

真实构建使用：

```powershell
java -jar tools\apktool.jar d -f -r -o decoded input.apk
```

`-r` 表示保留资源，不重解 `res/`。本项目只改 smali/dex，这样可以减少资源回编失败概率。

## 应用 smali 补丁

`patch.ps1` 支持两类补丁：

- 插入补丁：在某行 `after` 后插入 smali。
- 替换补丁：把 `find` 整行替换成 `replace`。

profile 中的关键字段：

```json
"applicationPatch": {
  "file": "smali/com/autonavi/amapauto/app/MapApplicationProxy.smali",
  "after": "    invoke-super {p0}, Landroid/app/Application;->onCreate()V",
  "insert": [
    "",
    "    invoke-static {p0}, Lamap/auto/patch/PatchRuntime;->init(Landroid/content/Context;)V"
  ]
}
```

脚本带幂等检查，如果目标调用已经存在，不会重复插入。

## 合并 runtime smali

脚本把 `runtime.dex` 放进一个临时 APK，再用 apktool 解开成 smali：

```text
runtime-decoded/smali/amap/auto/patch/...
```

然后复制到原 APK 解包目录：

```text
decoded/smali/amap/auto/patch/...
```

最终 apktool 回编时，这些类会进入原 `classes.dex`。

## native 库压缩处理

真实构建会调用 `Enable-NativeLibCompression(...)`，从 `apktool.yml` 的 `doNotCompress` 里移除 `so`。原因是当前原 APK 的 native 库本身是压缩状态，如果 apktool 回编时把 `.so` 全部改成不压缩，改包体积会明显变大。

这个处理后，改包体积应接近原 APK。

## 回编、对齐、签名

流程：

1. `apktool b decoded -o patched-unsigned.apk`
2. `zipalign -f 4 patched-unsigned.apk patched-aligned.apk`
3. `apksigner sign --min-sdk-version profile.minApi ...`
4. `apksigner verify --min-sdk-version profile.minApi --verbose`
5. 复制到 `dist/`

输出名来自 profile 的 `outputName`。

## 虚拟机烟测 APK

`-EmulatorOnlyNoNativeLibs` 用于构造一个仅供安装烟测的 APK：

```powershell
.\patch.ps1 -InputApk "D:\path\to\amap.apk" -EmulatorOnlyNoNativeLibs
```

它会移除 native libs，并修改 manifest 入口方便启动测试 Activity。这个 APK 不能代表真实高德运行结果，只能用于确认安装、签名和部分 UI 能力。

更推荐用 `build-test-apk.ps1` 测 runtime UI。

## 构造失败时看哪里

- `patch.ps1`：构造流水线和错误位置。
- `profiles/*.json`：smali 文件路径和 marker。
- `%TEMP%\amap_auto_patch_build\...`：中间解包和回编目录。
- `dist/`：最终 APK 输出。

不要把中间目录、输出 APK 或原 APK 提交到仓库。
