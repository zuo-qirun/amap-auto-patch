param(
    [string]$OutDir = "dist",
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

function Get-LatestAndroidDir($parent, $prefix) {
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

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { "C:\Users\zuoqirun\AppData\Local\Android\Sdk" }
$buildTools = if ($env:ANDROID_BUILD_TOOLS_VERSION) { Join-Path (Join-Path $sdk "build-tools") $env:ANDROID_BUILD_TOOLS_VERSION } else { Get-LatestAndroidDir (Join-Path $sdk "build-tools") "" }
$platformDir = if ($env:ANDROID_PLATFORM) { Join-Path (Join-Path $sdk "platforms") $env:ANDROID_PLATFORM } else { Get-LatestAndroidDir (Join-Path $sdk "platforms") "android-" }
$androidJar = Join-Path $platformDir "android.jar"
$aapt = Join-Path $buildTools "aapt.exe"
$d8 = Join-Path $buildTools "d8.bat"
$zipalign = Join-Path $buildTools "zipalign.exe"
$apksigner = Join-Path $buildTools "apksigner.bat"

$work = Join-Path ([System.IO.Path]::GetTempPath()) "amap_auto_patch_test_app"
$gen = Join-Path $work "gen"
$classes = Join-Path $work "classes"
$dex = Join-Path $work "dex"
$unsigned = Join-Path $work "test-unsigned.apk"
$aligned = Join-Path $work "test-aligned.apk"
$outFull = Join-Path $root $OutDir
$signed = Join-Path $outFull "amap_auto_patch_vm_test.apk"

Remove-Item -LiteralPath $work -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $gen, $classes, $dex, $outFull | Out-Null

& $aapt package -f -m -J $gen -M (Join-Path $root "test_app\AndroidManifest.xml") -S (Join-Path $root "test_app\res") -I $androidJar
Check-Last "aapt generate R"

$sources = @()
$sources += Get-ChildItem -LiteralPath (Join-Path $root "runtime\src\main\java") -Recurse -File -Filter *.java | ForEach-Object { $_.FullName }
$sources += Get-ChildItem -LiteralPath $gen -Recurse -File -Filter *.java | ForEach-Object { $_.FullName }

javac -encoding UTF-8 -source 8 -target 8 -classpath $androidJar -d $classes $sources
Check-Last "javac test app"

$classFiles = Get-ChildItem -LiteralPath $classes -Recurse -File -Filter *.class | ForEach-Object { $_.FullName }
& $d8 --lib $androidJar --min-api 23 --output $dex $classFiles
Check-Last "d8 test app"

& $aapt package -f -M (Join-Path $root "test_app\AndroidManifest.xml") -S (Join-Path $root "test_app\res") -I $androidJar -F $unsigned $dex
Check-Last "aapt package test app"

& $zipalign -f 4 $unsigned $aligned
Check-Last "zipalign test app"

& $apksigner sign `
    --min-sdk-version 23 `
    --ks (Join-Path $root $Keystore) `
    --ks-key-alias $KeyAlias `
    --ks-pass "pass:$KsPass" `
    --key-pass "pass:$KeyPass" `
    --out $signed `
    $aligned
Check-Last "apksigner sign test app"

& $apksigner verify --verbose $signed
Check-Last "apksigner verify test app"

Write-Host "[test-app] done: $signed"
