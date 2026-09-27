param(
    [switch]$Install,
    [switch]$Test,
    [switch]$Optimized
)

$ErrorActionPreference = "Stop"
$projectRoot = $PSScriptRoot
$gradleVersion = "8.11.1"
$gradleFolder = Join-Path $projectRoot ".gradle-dist\gradle-$gradleVersion"
$gradleBat = Join-Path $gradleFolder "bin\gradle.bat"

function Find-AndroidSdk {
    $localProperties = Join-Path $projectRoot "local.properties"
    if (Test-Path $localProperties) {
        $sdkLine = Get-Content $localProperties | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
        if ($sdkLine) {
            $value = ($sdkLine -replace '^sdk\.dir=', '') -replace '\\:', ':' -replace '\\\\', '\'
            if (Test-Path $value) { return $value }
        }
    }

    if ($env:ANDROID_HOME -and (Test-Path $env:ANDROID_HOME)) { return $env:ANDROID_HOME }
    if ($env:ANDROID_SDK_ROOT -and (Test-Path $env:ANDROID_SDK_ROOT)) { return $env:ANDROID_SDK_ROOT }

    $defaultSdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
    if (Test-Path $defaultSdk) { return $defaultSdk }

    throw "Android SDK bulunamadi. Onceki projenizin local.properties dosyasini bu klasore kopyalayin veya ANDROID_HOME yolunu ayarlayin."
}

function Configure-Java {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) { return }

    $studioJbrCandidates = @(
        (Join-Path $env:ProgramFiles "Android\Android Studio\jbr"),
        (Join-Path $env:LOCALAPPDATA "Programs\Android Studio\jbr")
    )
    foreach ($candidate in $studioJbrCandidates) {
        if (Test-Path (Join-Path $candidate "bin\java.exe")) {
            $env:JAVA_HOME = $candidate
            return
        }
    }

    try {
        $null = & java -version 2>&1
    } catch {
        throw "Java 17 bulunamadi. JAVA_HOME yolunu kontrol edin."
    }
}

function Set-LocalPropertySdkDir {
    param(
        [string]$Path,
        [string]$SdkForGradle
    )
    $sdkEntry = "sdk.dir=$SdkForGradle"
    $utf8NoBom = New-Object System.Text.UTF8Encoding $false

    if (Test-Path $Path) {
        $lines = [System.IO.File]::ReadAllLines($Path)
        $hasSdk = $false
        $updated = @(
            foreach ($line in $lines) {
                if ($line -match '^\s*sdk\.dir\s*=') {
                    if (-not $hasSdk) {
                        $sdkEntry
                        $hasSdk = $true
                    }
                } else {
                    $line
                }
            }
        )
        if (-not $hasSdk) {
            $updated += $sdkEntry
        }
        [System.IO.File]::WriteAllLines($Path, [string[]]$updated, $utf8NoBom)
    } else {
        [System.IO.File]::WriteAllLines($Path, [string[]]@($sdkEntry), $utf8NoBom)
    }
}

Configure-Java
$sdkPath = Find-AndroidSdk
$sdkForGradle = $sdkPath -replace '\\', '/'
Set-LocalPropertySdkDir -Path (Join-Path $projectRoot "local.properties") -SdkForGradle $sdkForGradle


if (-not (Test-Path (Join-Path $sdkPath "platforms\android-36"))) {
    throw "SDK Platform 36 bulunamadi. local.properties SDK yolunu kontrol edin veya Android 16 / API 36'yi kurun."
}

if (-not (Test-Path $gradleBat)) {
    $distributionRoot = Join-Path $projectRoot ".gradle-dist"
    $zipPath = Join-Path $distributionRoot "gradle-$gradleVersion-bin.zip"
    New-Item -ItemType Directory -Force -Path $distributionRoot | Out-Null
    Write-Host "Gradle $gradleVersion indiriliyor..."
    Invoke-WebRequest "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip" -OutFile $zipPath -UseBasicParsing
    Expand-Archive -Path $zipPath -DestinationPath $distributionRoot -Force
    Remove-Item $zipPath
}

Push-Location $projectRoot
try {
    $variant = if ($Optimized) { "Optimized" } else { "Debug" }
    $gradleTasks = @("assemble$variant")
    if ($Test) { $gradleTasks += ":app:test${variant}UnitTest" }
    & $gradleBat --no-daemon @gradleTasks --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle build basarisiz. Yukaridaki 'e:' veya 'What went wrong' bolumune bakin." }
} finally {
    Pop-Location
}

$variantDir = if ($Optimized) { "optimized" } else { "debug" }
$builtApk = Join-Path $projectRoot "app\build\outputs\apk\$variantDir\app-$variantDir.apk"
$outputName = if ($Optimized) { "Real-optimized.apk" } else { "Real-debug.apk" }
$outputApk = Join-Path $projectRoot $outputName
Copy-Item $builtApk $outputApk -Force
Write-Host ""
Write-Host "HAZIR: $outputApk" -ForegroundColor Green

if ($Install) {
    $adbCandidates = @(
        (Join-Path $sdkPath "platform-tools\adb.exe"),
        "C:\Users\CAGRI\Downloads\platform-tools-latest-windows\platform-tools\adb.exe"
    )
    $adb = $adbCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
    if (-not $adb) { throw "adb.exe bulunamadi." }
    & $adb install -r $outputApk
    if ($LASTEXITCODE -ne 0) { throw "APK kurulamadi." }
}
