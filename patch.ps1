param(
    [Parameter(Mandatory = $true)]
    [string]$InputApk,

    [string]$Profile = "profiles\9.1.0.600087.json",
    [string]$DecodedSource = "",
    [string]$ApktoolJar = "tools\apktool.jar",
    [string]$OutDir = "dist",
    [string]$WorkDir = "",
    [switch]$EmulatorOnlyNoNativeLibs,
    [string]$Keystore = "..\amap_companion\debug.keystore",
    [string]$KeyAlias = "androiddebugkey",
    [string]$KsPass = "android",
    [string]$KeyPass = "android"
)

$ErrorActionPreference = "Stop"

function Check-Last($name) {
    if ($LASTEXITCODE -ne 0) {
        throw "$name failed with exit $LASTEXITCODE"
    }
}

function Resolve-FullPath($path) {
    $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($path)
}

function Get-LatestAndroidDir($parent, $prefix) {
    if (!(Test-Path -LiteralPath $parent)) {
        throw "Android SDK directory not found: $parent"
    }
    $dirs = Get-ChildItem -LiteralPath $parent -Directory |
        Where-Object { $_.Name -like "$prefix*" } |
        Sort-Object {
            $versionText = $_.Name.Substring($prefix.Length)
            $versionText = $versionText -replace '[^\d\.].*$', ''
            try { [version]$versionText } catch { [version]'0.0.0' }
        } -Descending
    if (!$dirs) {
        throw "No Android SDK component found in $parent"
    }
    $dirs[0].FullName
}

function Resolve-AndroidTools() {
    $sdk = if ($env:ANDROID_HOME) {
        $env:ANDROID_HOME
    } elseif ($env:ANDROID_SDK_ROOT) {
        $env:ANDROID_SDK_ROOT
    } else {
        "C:\Users\zuoqirun\AppData\Local\Android\Sdk"
    }
    $buildTools = if ($env:ANDROID_BUILD_TOOLS_VERSION) {
        Join-Path (Join-Path $sdk "build-tools") $env:ANDROID_BUILD_TOOLS_VERSION
    } else {
        Get-LatestAndroidDir (Join-Path $sdk "build-tools") ""
    }
    $platformName = if ($env:ANDROID_PLATFORM) {
        $env:ANDROID_PLATFORM
    } elseif ($env:ANDROID_PLATFORM_VERSION) {
        "android-$env:ANDROID_PLATFORM_VERSION"
    } else {
        $null
    }
    $platformDir = if ($platformName) {
        Join-Path (Join-Path $sdk "platforms") $platformName
    } else {
        Get-LatestAndroidDir (Join-Path $sdk "platforms") "android-"
    }

    $isWindows = $PSVersionTable.Platform -eq "Win32NT" -or $env:OS -eq "Windows_NT"
    $exeSuffix = if ($isWindows) { ".exe" } else { "" }
    $scriptSuffix = if ($isWindows) { ".bat" } else { "" }

    $tools = [ordered]@{
        aapt = Join-Path $buildTools "aapt$exeSuffix"
        d8 = Join-Path $buildTools "d8$scriptSuffix"
        zipalign = Join-Path $buildTools "zipalign$exeSuffix"
        apksigner = Join-Path $buildTools "apksigner$scriptSuffix"
        androidJar = Join-Path $platformDir "android.jar"
    }
    foreach ($tool in $tools.GetEnumerator()) {
        if (!(Test-Path -LiteralPath $tool.Value)) {
            throw "Required Android tool not found: $($tool.Value)"
        }
    }
    $tools
}

function Reset-Directory($path, $root) {
    $full = [System.IO.Path]::GetFullPath($path).TrimEnd([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar)
    $rootFull = [System.IO.Path]::GetFullPath($root).TrimEnd([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar)
    $comparison = if ($env:OS -eq "Windows_NT") { [System.StringComparison]::OrdinalIgnoreCase } else { [System.StringComparison]::Ordinal }
    if (!$full.StartsWith($rootFull + [System.IO.Path]::DirectorySeparatorChar, $comparison)) {
        throw "Refusing to reset outside build root: $full"
    }
    if (Test-Path -LiteralPath $full) {
        Remove-Item -LiteralPath $full -Recurse -Force
    }
    New-Item -ItemType Directory -Force -Path $full | Out-Null
}

function Read-Text($path) {
    [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
}

function Write-Text($path, $text) {
    [System.IO.File]::WriteAllText($path, $text, (New-Object System.Text.UTF8Encoding($false)))
}

function Apply-InsertPatch($decodedDir, $patch, $needleForIdempotency, $label) {
    $file = Join-Path $decodedDir $patch.file
    if (!(Test-Path -LiteralPath $file)) {
        throw "$label patch file not found: $file"
    }
    $content = Read-Text $file
    if ($content.Contains($needleForIdempotency)) {
        Write-Host "[patch] $label already present"
        return
    }
    $startIndex = 0
    if ($patch.PSObject.Properties.Name -contains "scope") {
        $scope = [string]$patch.scope
        $startIndex = $content.IndexOf($scope, [System.StringComparison]::Ordinal)
        if ($startIndex -lt 0) {
            throw "$label patch scope not found in $file"
        }
    }
    $after = [string]$patch.after
    $markerIndex = $content.IndexOf($after, $startIndex, [System.StringComparison]::Ordinal)
    if ($markerIndex -lt 0) {
        throw "$label patch marker not found in $file"
    }
    $insertText = ([string[]]$patch.insert) -join "`r`n"
    $insertAt = $markerIndex + $after.Length
    $content = $content.Substring(0, $insertAt) + "`r`n" + $insertText + $content.Substring($insertAt)
    Write-Text $file $content
    Write-Host "[patch] applied $label"
}

function Apply-ReplacePatch($decodedDir, $patch, $label) {
    $file = Join-Path $decodedDir $patch.file
    if (!(Test-Path -LiteralPath $file)) {
        throw "$label patch file not found: $file"
    }
    $content = Read-Text $file
    $find = [string]$patch.find
    $replace = [string]$patch.replace
    if ($content.Contains($replace)) {
        Write-Host "[patch] $label already present"
        return
    }
    if (!$content.Contains($find)) {
        throw "$label patch marker not found in $file"
    }
    $content = $content.Replace($find, $replace)
    Write-Text $file $content
    Write-Host "[patch] applied $label"
}

function Build-RuntimeDex($root, $tools, $profileConfig, $outDexPath, $runtimeBuild) {
    $runtimeSrc = Join-Path $root "runtime\src\main\java"
    $classesDir = Join-Path $runtimeBuild "classes"
    $dexDir = Join-Path $runtimeBuild "dex"
    $runtimeParent = Split-Path -Parent $runtimeBuild
    New-Item -ItemType Directory -Force -Path $runtimeParent | Out-Null
    Reset-Directory $runtimeBuild $runtimeParent
    New-Item -ItemType Directory -Force -Path $classesDir, $dexDir | Out-Null

    $sources = Get-ChildItem -LiteralPath $runtimeSrc -Recurse -File -Filter *.java |
        ForEach-Object { $_.FullName }
    if (!$sources) {
        throw "No runtime Java sources found under $runtimeSrc"
    }

    javac -encoding UTF-8 -source 8 -target 8 -classpath $tools.androidJar -d $classesDir $sources
    Check-Last "javac runtime"

    $classFiles = Get-ChildItem -LiteralPath $classesDir -Recurse -File -Filter *.class |
        ForEach-Object { $_.FullName }
    & $tools.d8 --lib $tools.androidJar --min-api ([int]$profileConfig.minApi) --output $dexDir $classFiles
    Check-Last "d8 runtime"

    $dex = Join-Path $dexDir "classes.dex"
    if (!(Test-Path -LiteralPath $dex)) {
        throw "Runtime dex not generated: $dex"
    }
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $outDexPath) | Out-Null
    Copy-Item -LiteralPath $dex -Destination $outDexPath -Force
}

function Add-ZipEntry($zipPath, $sourceFile, $entryName) {
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::Open($zipPath, [System.IO.Compression.ZipArchiveMode]::Update)
    try {
        $existing = $zip.GetEntry($entryName)
        if ($existing) {
            $existing.Delete()
        }
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
            $zip,
            $sourceFile,
            $entryName,
            [System.IO.Compression.CompressionLevel]::Optimal
        ) | Out-Null
    } finally {
        $zip.Dispose()
    }
}

function Remove-ZipEntriesByPrefix($zipPath, $prefix) {
    $entries = @(& $script:AndroidAapt list $zipPath | Where-Object { $_.StartsWith($prefix, [System.StringComparison]::Ordinal) })
    if (!$entries -or $entries.Count -eq 0) {
        Write-Host "[patch] no entries with prefix $prefix"
        return
    }
    & $script:AndroidAapt r $zipPath $entries
    Check-Last "aapt remove $prefix"
    Write-Host "[patch] removed $($entries.Count) entries with prefix $prefix"
}

function Enable-NativeLibCompression($decodedDir) {
    $apktoolYml = Join-Path $decodedDir "apktool.yml"
    if (!(Test-Path -LiteralPath $apktoolYml)) {
        throw "apktool.yml not found: $apktoolYml"
    }
    $lines = [System.Collections.Generic.List[string]]::new()
    $removed = 0
    foreach ($line in [System.IO.File]::ReadLines($apktoolYml, [System.Text.Encoding]::UTF8)) {
        if ($line -match '^\s*-\s*so\s*$') {
            $removed++
            continue
        }
        $lines.Add($line)
    }
    if ($removed -gt 0) {
        [System.IO.File]::WriteAllLines($apktoolYml, $lines, (New-Object System.Text.UTF8Encoding($false)))
        Write-Host "[patch] enabled compression for native libraries"
    } else {
        Write-Host "[patch] native library compression already enabled"
    }
}

function Merge-RuntimeSmali($apktoolJar, $runtimeDex, $decodedDir, $workRoot) {
    $runtimeApk = Join-Path $workRoot "runtime-classes.apk"
    $runtimeDecoded = Join-Path $workRoot "runtime-decoded"
    if (Test-Path -LiteralPath $runtimeApk) {
        Remove-Item -LiteralPath $runtimeApk -Force
    }
    Add-ZipEntry $runtimeApk $runtimeDex "classes.dex"
    java -jar $apktoolJar d -f -r -o $runtimeDecoded $runtimeApk
    Check-Last "apktool decode runtime dex"

    $runtimeSmali = Join-Path $runtimeDecoded "smali\amap"
    if (!(Test-Path -LiteralPath $runtimeSmali)) {
        throw "Runtime smali not found after decode: $runtimeSmali"
    }
    $targetSmali = Join-Path $decodedDir "smali\amap"
    if (Test-Path -LiteralPath $targetSmali) {
        Remove-Item -LiteralPath $targetSmali -Recurse -Force
    }
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $targetSmali) | Out-Null
    Copy-Item -LiteralPath $runtimeSmali -Destination $targetSmali -Recurse -Force
    Write-Host "[patch] merged runtime smali into classes.dex"
}

function Configure-EmulatorManifest($decodedDir) {
    $manifestPath = Join-Path $decodedDir "AndroidManifest.xml"
    if (!(Test-Path -LiteralPath $manifestPath)) {
        throw "AndroidManifest.xml not found: $manifestPath"
    }
    $text = Read-Text $manifestPath
    $text = $text -replace 'android:appComponentFactory="[^"]*"\s+', ''
    $text = $text -replace 'android:name="com\.autonavi\.amapauto\.app\.MapApplicationProxy"', 'android:name="android.app.Application"'

    if ($text -notmatch 'amap\.auto\.patch\.PatchTestActivity') {
        $activity = @'
        <activity android:exported="true" android:launchMode="singleTask" android:name="amap.auto.patch.PatchTestActivity" android:theme="@style/Theme.Background">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>
'@
        $text = $text -replace '(<application\b[^>]*>)', "`$1`r`n$activity"
    }

    $text = $text -replace '(?s)<intent-filter>\s*<action android:name="android\.intent\.action\.MAIN"/>\s*<category android:name="android\.intent\.category\.LAUNCHER"/>\s*<category android:name="android\.intent\.category\.APP_MAPS"/>\s*<category android:name="android\.intent\.category\.DEFAULT"/>\s*</intent-filter>', ''
    Write-Text $manifestPath $text
    Write-Host "[patch] configured emulator-only test manifest"
}

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$profilePath = Resolve-FullPath (Join-Path $root $Profile)
$profileConfig = Get-Content -LiteralPath $profilePath -Raw -Encoding UTF8 | ConvertFrom-Json
$inputFull = Resolve-FullPath $InputApk
$apktoolFull = Resolve-FullPath (Join-Path $root $ApktoolJar)
$outFull = Resolve-FullPath (Join-Path $root $OutDir)
$keystoreFull = Resolve-FullPath (Join-Path $root $Keystore)
$tools = Resolve-AndroidTools
$script:AndroidAapt = $tools.aapt

if (!(Test-Path -LiteralPath $inputFull)) {
    throw "Input APK not found: $inputFull"
}
if (!(Test-Path -LiteralPath $keystoreFull)) {
    throw "Keystore not found: $keystoreFull"
}

$hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $inputFull).Hash.ToUpperInvariant()
if ($hash -ne ([string]$profileConfig.inputSha256).ToUpperInvariant()) {
    throw "Input APK SHA-256 mismatch. Expected $($profileConfig.inputSha256), got $hash"
}

$buildRoot = Join-Path $root "build"
$workBase = if ($WorkDir) {
    Resolve-FullPath $WorkDir
} elseif ($env:AMAP_PATCH_BUILD_ROOT) {
    Resolve-FullPath $env:AMAP_PATCH_BUILD_ROOT
} else {
    Join-Path ([System.IO.Path]::GetTempPath()) "amap_auto_patch_build"
}
$workRoot = Join-Path $workBase $profileConfig.id
$decodedDir = Join-Path $workRoot "decoded"
$dexInjectDir = Join-Path $workRoot "dex-inject"
$runtimeDex = Join-Path $dexInjectDir "runtime.dex"
$runtimeBuild = Join-Path $workRoot "runtime-build"
$unsignedApk = Join-Path $workRoot "patched-unsigned.apk"
$strippedApk = Join-Path $workRoot "patched-stripped.apk"
$alignedApk = Join-Path $workRoot "patched-aligned.apk"
$signedTempApk = Join-Path $workRoot "patched-signed.apk"
$outputName = [string]$profileConfig.outputName
if ($EmulatorOnlyNoNativeLibs) {
    $outputName = $outputName -replace '\.apk$', '_emulator_no_native.apk'
}
$signedApk = Join-Path $outFull $outputName

New-Item -ItemType Directory -Force -Path $workBase | Out-Null
Reset-Directory $workRoot $workBase
New-Item -ItemType Directory -Force -Path $outFull, $dexInjectDir | Out-Null

Write-Host "[patch] build runtime dex"
Build-RuntimeDex $root $tools $profileConfig $runtimeDex $runtimeBuild

if ($DecodedSource) {
    $decodedSourceFull = Resolve-FullPath $DecodedSource
    if (!(Test-Path -LiteralPath (Join-Path $decodedSourceFull "apktool.yml"))) {
        throw "Decoded source does not look like an apktool directory: $decodedSourceFull"
    }
    Write-Host "[patch] copy decoded source $decodedSourceFull"
    Copy-Item -LiteralPath $decodedSourceFull -Destination $decodedDir -Recurse -Force
} else {
    if (!(Test-Path -LiteralPath $apktoolFull)) {
        throw "apktool.jar not found: $apktoolFull. Put apktool.jar in tools or pass -ApktoolJar."
    }
    Write-Host "[patch] decode APK"
    if ($EmulatorOnlyNoNativeLibs) {
        java -jar $apktoolFull d -f -resm keep -o $decodedDir $inputFull
    } else {
        java -jar $apktoolFull d -f -r -o $decodedDir $inputFull
    }
    Check-Last "apktool decode"
}

Apply-InsertPatch $decodedDir $profileConfig.applicationPatch "Lamap/auto/patch/PatchRuntime;->init" "application init"
Apply-ReplacePatch $decodedDir $profileConfig.hostFloatWindowPatch "host float window replacement"
Apply-InsertPatch $decodedDir $profileConfig.trafficLightPatch "Lamap/auto/patch/PatchRuntime;->onTrafficLightWrapper" "traffic light wrapper"
if ($profileConfig.PSObject.Properties.Name -contains "hostFloatWindowCleanupPatch") {
    Apply-InsertPatch $decodedDir $profileConfig.hostFloatWindowCleanupPatch "Lamap/auto/patch/PatchRuntime;->removePatchOverlay" "host float window cleanup"
}
Merge-RuntimeSmali $apktoolFull $runtimeDex $decodedDir $workRoot
if ($EmulatorOnlyNoNativeLibs) {
    Configure-EmulatorManifest $decodedDir
} else {
    Enable-NativeLibCompression $decodedDir
}

if (!(Test-Path -LiteralPath $apktoolFull)) {
    throw "apktool.jar not found: $apktoolFull. It is required to rebuild the decoded APK."
}

Write-Host "[patch] rebuild APK"
java -jar $apktoolFull b $decodedDir -o $unsignedApk
Check-Last "apktool build"

$packageInputApk = $unsignedApk
if ($EmulatorOnlyNoNativeLibs) {
    Write-Host "[patch] remove native libs for emulator-only smoke test"
    Copy-Item -LiteralPath $unsignedApk -Destination $strippedApk -Force
    Remove-ZipEntriesByPrefix $strippedApk "lib/"
    $packageInputApk = $strippedApk
}

Write-Host "[patch] zipalign"
& $tools.zipalign -f 4 $packageInputApk $alignedApk
Check-Last "zipalign"

Write-Host "[patch] sign"
& $tools.apksigner sign `
    --min-sdk-version ([int]$profileConfig.minApi) `
    --ks $keystoreFull `
    --ks-key-alias $KeyAlias `
    --ks-pass "pass:$KsPass" `
    --key-pass "pass:$KeyPass" `
    --out $signedTempApk `
    $alignedApk
Check-Last "apksigner sign"

Write-Host "[patch] verify"
& $tools.apksigner verify --min-sdk-version ([int]$profileConfig.minApi) --verbose $signedTempApk
Check-Last "apksigner verify"

Copy-Item -LiteralPath $signedTempApk -Destination $signedApk -Force
Write-Host "[patch] done: $signedApk"
