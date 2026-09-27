# REAL 0.7.3.4 - remove resources deleted in earlier checkpoints but left behind by overlay extraction.
$paths = @(
    ".\app\src\main\res\mipmap-anydpi-v26",
    ".\app\src\main\res\drawable\ic_launcher.xml",
    ".\app\src\main\res\raw\real_map_dark.json"
)
foreach ($path in $paths) {
    if (Test-Path $path) {
        Remove-Item $path -Recurse -Force
        Write-Host "Removed $path"
    }
}
Write-Host "Stale resource cleanup complete."
