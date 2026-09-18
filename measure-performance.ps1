param(
    [string]$Adb = "adb",
    [ValidatePattern('^[a-zA-Z0-9_-]+$')][string]$Label = "real"
)
$ErrorActionPreference = "Stop"
& $Adb shell dumpsys gfxinfo com.cagri.bigboscagri reset
if ($LASTEXITCODE -ne 0) { throw "ADB baglantisini kontrol edin." }
Read-Host "Telefonda Real'i acin, 20 saniye listede kaydirin. Sonra Enter'a basin"
$result = & $Adb shell dumpsys gfxinfo com.cagri.bigboscagri framestats
if ($LASTEXITCODE -ne 0) { throw "Olcum alinamadi." }
$path = Join-Path $PSScriptRoot "frames-$Label.txt"
$result | Set-Content -Encoding UTF8 $path
$result | Select-String 'Total frames|Janky|percentile|Missed Vsync|Slow UI|Slow issue|deadline'
Write-Host "Kaydedildi: $path"
