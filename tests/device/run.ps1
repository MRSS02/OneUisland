param(
    [string]$SdkRoot = "$env:LOCALAPPDATA/Android/Sdk",
    [string]$JavaRoot = $env:JAVA_HOME
)

$ErrorActionPreference = 'Stop'
if (!$JavaRoot) { throw 'Set JAVA_HOME or pass -JavaRoot (JDK 17+).' }
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$probeRoot = Join-Path $repoRoot 'build/shade-probe'
$androidJar = Join-Path $SdkRoot 'platforms/android-36/android.jar'
$adb = Join-Path $SdkRoot 'platform-tools/adb.exe'
$d8 = Join-Path $SdkRoot 'build-tools/36.0.0/d8.bat'
$remoteJar = '/data/local/tmp/dynamic-island-shade-probe.jar'

function Invoke-Checked([string]$Program, [string[]]$Arguments) {
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Program exited with $LASTEXITCODE" }
}

Push-Location $repoRoot
$previousJavaRoot = $env:JAVA_HOME
try {
    $env:JAVA_HOME = $JavaRoot
    Invoke-Checked './gradlew.bat' @(':app:bundleDebugClassesToCompileJar', ':stubs:jar', '--console=plain')
    New-Item -ItemType Directory -Force -Path "$probeRoot/classes", "$probeRoot/dex" | Out-Null
    $classPath = "$androidJar;app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes;stubs/build/classes/java/main"
    Invoke-Checked "$JavaRoot/bin/javac.exe" @('--release', '17', '-classpath', $classPath, '-d', "$probeRoot/classes", 'tests/device/ShadeLiveCardsProbe.java')
    Invoke-Checked "$JavaRoot/bin/jar.exe" @('--create', '--file', "$probeRoot/probe.jar", '-C', "$probeRoot/classes", '.')
    Invoke-Checked $d8 @('--min-api', '35', '--lib', $androidJar, '--output', "$probeRoot/dex", "$probeRoot/probe.jar",
        'app/build/intermediates/compile_app_classes_jar/debug/bundleDebugClassesToCompileJar/classes.jar', 'stubs/build/libs/stubs.jar')
    Invoke-Checked "$JavaRoot/bin/jar.exe" @('--create', '--file', "$probeRoot/probe-dex.jar", '-C', "$probeRoot/dex", 'classes.dex')
    Invoke-Checked $adb @('shell', 'rm', '-f', $remoteJar)
    Invoke-Checked $adb @('push', "$probeRoot/probe-dex.jar", $remoteJar)
    Invoke-Checked $adb @('shell', 'chmod', '444', $remoteJar)
    Invoke-Checked $adb @('shell', "CLASSPATH=$remoteJar app_process /system/bin dev.flexy.dynamicisland.xposed.ShadeLiveCardsProbe")
} finally {
    & $adb shell rm -f $remoteJar
    $env:JAVA_HOME = $previousJavaRoot
    Pop-Location
}
