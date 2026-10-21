param([Parameter(Mandatory=$true)][string]$Serial,
    [ValidatePattern('^\d+\.\d+$')][string]$PreviousVersion='1.2',
    [ValidatePattern('^\d+\.\d+$')][string]$NextVersion='1.3')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$adbPath = if ($env:ANDROID_HOME) { Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe' } else { Join-Path $env:USERPROFILE '.codex/cache/cam-lite-toolchain/sdk/platform-tools/adb.exe' }
if ((& $adbPath -s $Serial shell getprop ro.kernel.qemu).Trim() -ne '1') { throw 'Somente emulador descartável. O teste substitui dados por configurações fictícias.' }
function Invoke-AdbChecked {
    param([string[]]$Arguments)
    $taskOutput = & $adbPath -s $Serial @Arguments 2>&1
    $taskOutput | Write-Output
    if ($LASTEXITCODE -ne 0) { throw 'Comando adb falhou.' }
    if ('instrument' -in $Arguments -and ($taskOutput -join "`n") -notmatch 'INSTRUMENTATION_CODE:\s*0\b') { throw 'Instrumentação não confirmou sucesso.' }
}
$testApk = Join-Path $projectRoot 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
Invoke-AdbChecked -Arguments @('install','-r','-d',(Join-Path $projectRoot "dist/CamLite-Android-v$PreviousVersion.apk"))
Invoke-AdbChecked -Arguments @('install','-r','-t',$testApk)
Invoke-AdbChecked -Arguments @('shell','am','instrument','-w','-e','phase','seed','com.camlite.android.test/com.camlite.android.UpgradeInstrumentation')
Invoke-AdbChecked -Arguments @('install','-r',(Join-Path $projectRoot "dist/CamLite-Android-v$NextVersion.apk"))
Invoke-AdbChecked -Arguments @('shell','am','instrument','-w','-e','phase','check','com.camlite.android.test/com.camlite.android.UpgradeInstrumentation')
