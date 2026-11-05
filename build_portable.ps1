# Portable Android build script without requiring Administrator privileges
param(
    [switch]$SkipSdkInstall
)

$ErrorActionPreference = "Stop"
$projectRoot = $PSScriptRoot
$toolsDir = Join-Path $projectRoot "tools"

if (-not (Test-Path $toolsDir)) {
    New-Item -ItemType Directory -Path $toolsDir | Out-Null
}

function Download-WithCurl {
    param([string]$Url, [string]$OutFile)
    Write-Host "Downloading $Url -> $OutFile" -ForegroundColor Cyan
    & curl.exe -L --retry 5 --retry-delay 2 -C - -o "$OutFile" "$Url"
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to download $Url (exit code $LASTEXITCODE)"
    }
}

# 1. Portable OpenJDK 17 Setup
$jdkDir = Join-Path $toolsDir "jdk-17"
$jdkZip = Join-Path $toolsDir "openjdk-17.zip"
$jdkUrl = "https://aka.ms/download-jdk/microsoft-jdk-17.0.10-windows-x64.zip"

if (-not (Test-Path (Join-Path $jdkDir "bin\java.exe"))) {
    Write-Host "[1/4] Setting up Portable OpenJDK 17..." -ForegroundColor Cyan
    if (-not (Test-Path $jdkZip)) {
        Download-WithCurl -Url $jdkUrl -OutFile $jdkZip
    }
    Write-Host "Extracting OpenJDK 17..." -ForegroundColor Cyan
    $tempExtract = Join-Path $toolsDir "jdk_temp"
    if (Test-Path $tempExtract) { Remove-Item -Recurse -Force $tempExtract }
    Expand-Archive -Path $jdkZip -DestinationPath $tempExtract -Force
    $extractedFolder = Get-ChildItem -Path $tempExtract -Directory | Select-Object -First 1
    if (Test-Path $jdkDir) { Remove-Item -Recurse -Force $jdkDir }
    Move-Item -Path $extractedFolder.FullName -Destination $jdkDir
    Remove-Item -Recurse -Force $tempExtract
    Remove-Item -Force $jdkZip
}

$env:JAVA_HOME = $jdkDir
$env:PATH = "$jdkDir\bin;$env:PATH"

# 2. Portable Android Command-line Tools Setup
$androidHome = Join-Path $toolsDir "android-sdk"
$cmdlineToolsDir = Join-Path $androidHome "cmdline-tools\latest"
$cmdlineZip = Join-Path $toolsDir "cmdline-tools.zip"
$cmdlineUrl = "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"

if (-not (Test-Path (Join-Path $cmdlineToolsDir "bin\sdkmanager.bat"))) {
    Write-Host "[2/4] Setting up Android Command-line Tools..." -ForegroundColor Cyan
    if (-not (Test-Path $cmdlineZip)) {
        Download-WithCurl -Url $cmdlineUrl -OutFile $cmdlineZip
    }
    Write-Host "Extracting Android Command-line Tools..." -ForegroundColor Cyan
    $tempExtract = Join-Path $toolsDir "cmdline_temp"
    if (Test-Path $tempExtract) { Remove-Item -Recurse -Force $tempExtract }
    Expand-Archive -Path $cmdlineZip -DestinationPath $tempExtract -Force
    New-Item -ItemType Directory -Path (Split-Path $cmdlineToolsDir -Parent) -Force | Out-Null
    if (Test-Path $cmdlineToolsDir) { Remove-Item -Recurse -Force $cmdlineToolsDir }
    Move-Item -Path (Join-Path $tempExtract "cmdline-tools") -Destination $cmdlineToolsDir
    Remove-Item -Recurse -Force $tempExtract
    Remove-Item -Force $cmdlineZip
}

$env:ANDROID_HOME = $androidHome
$env:PATH = "$cmdlineToolsDir\bin;$androidHome\platform-tools;$env:PATH"

# Write local.properties for Gradle
$localProps = Join-Path $projectRoot "local.properties"
$escapedSdkPath = $androidHome.Replace('\', '\\')
Set-Content -Path $localProps -Value "sdk.dir=$escapedSdkPath"

# 3. Portable Gradle 8.5 Setup
$gradleDir = Join-Path $toolsDir "gradle-8.5"
$gradleZip = Join-Path $toolsDir "gradle-8.5-bin.zip"
$gradleUrl = "https://services.gradle.org/distributions/gradle-8.5-bin.zip"

if (-not (Test-Path (Join-Path $gradleDir "bin\gradle.bat"))) {
    Write-Host "[3/4] Setting up Portable Gradle 8.5..." -ForegroundColor Cyan
    if (-not (Test-Path $gradleZip)) {
        Download-WithCurl -Url $gradleUrl -OutFile $gradleZip
    }
    Write-Host "Extracting Gradle 8.5..." -ForegroundColor Cyan
    Expand-Archive -Path $gradleZip -DestinationPath $toolsDir -Force
    Remove-Item -Force $gradleZip
}

$gradleBat = Join-Path $gradleDir "bin\gradle.bat"

# 4. Accept Licenses and Install Android 34 SDK
if (-not $SkipSdkInstall) {
    if (-not (Test-Path (Join-Path $androidHome "platforms\android-34"))) {
        Write-Host "[4/4] Installing Android SDK platform-34 and build-tools..." -ForegroundColor Cyan
        $sdkManager = Join-Path $cmdlineToolsDir "bin\sdkmanager.bat"
        cmd.exe /c "for /l %i in (1,1,20) do @echo y" | & "$sdkManager" --sdk_root="$androidHome" --licenses
        & "$sdkManager" --sdk_root="$androidHome" "platforms;android-34" "build-tools;34.0.0"
    }
}

# 5. Compile the APK using Portable Gradle
Write-Host "Compiling debug APK..." -ForegroundColor Cyan
Set-Location $projectRoot
& "$gradleBat" assembleDebug --no-daemon

$targetApk = Join-Path $projectRoot "app\build\outputs\apk\debug\app-debug.apk"
if (Test-Path $targetApk) {
    Write-Host "`nSUCCESS! APK generated at:" -ForegroundColor Green
    Write-Host "$targetApk" -ForegroundColor Yellow
} else {
    Write-Host "`nCompilation failed or APK not found." -ForegroundColor Red
}
