# Fast incremental build, install, and instant app launch
Write-Host "⚡ Compiling changes and installing to phone..." -ForegroundColor Cyan
.\gradlew installDebug --offline --parallel --build-cache

if ($LASTEXITCODE -eq 0) {
    Write-Host "🚀 Launching ProofOfWork on device..." -ForegroundColor Green
    adb shell am start -n com.proofofwork/.MainActivity
} else {
    Write-Host "⚠️ Offline build failed, trying with network cache..." -ForegroundColor Yellow
    .\gradlew installDebug --parallel --build-cache
    adb shell am start -n com.proofofwork/.MainActivity
}
