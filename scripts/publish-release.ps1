<#
.SYNOPSIS
    Builds release binaries for Windows and Android, creates checksums,
    commits changes, and publishes the release to GitHub.
#>

param(
    [switch]$SkipGitPush = $false
)

$ErrorActionPreference = "Stop"
$RepoRoot = Resolve-Path "$PSScriptRoot\.."
Set-Location $RepoRoot

$Version = (Get-Content "$RepoRoot\VERSION").Trim()
$Tag = "v$Version"
$DistDir = "$RepoRoot\dist"

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host " Publishing SpeedShare Release $Tag" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

# 1. Clean / create dist directory
if (Test-Path $DistDir) {
    Remove-Item "$DistDir\*" -Recurse -Force -ErrorAction SilentlyContinue
} else {
    New-Item -ItemType Directory -Path $DistDir -Force | Out-Null
}

# 2. Build Windows Portable App
Write-Host "`n[1/5] Building Windows Portable executable..." -ForegroundColor Yellow
dotnet publish "$RepoRoot\windows\SpeedShareWindows.csproj" -c Release -r win-x64 --self-contained false -p:PublishSingleFile=true -o "$RepoRoot\windows\publish"
if ($LASTEXITCODE -ne 0) { throw "Windows app build failed with exit code $LASTEXITCODE" }
Copy-Item "$RepoRoot\windows\publish\SpeedShareWindows.exe" "$DistDir\SpeedShare-Windows-Portable.exe" -Force

# 3. Build Windows Setup Installer
Write-Host "`n[2/5] Building Windows Setup Installer..." -ForegroundColor Yellow
dotnet publish "$RepoRoot\windows-installer\SpeedShareSetup.csproj" -c Release -r win-x64 --self-contained false -p:PublishSingleFile=true -o "$RepoRoot\dist-installer"
if ($LASTEXITCODE -ne 0) { throw "Windows setup installer build failed with exit code $LASTEXITCODE" }
Copy-Item "$RepoRoot\dist-installer\SpeedShare-Setup.exe" "$DistDir\SpeedShare-Setup.exe" -Force

# 4. Build Android Release APK
Write-Host "`n[3/5] Building Android Release APK..." -ForegroundColor Yellow
Push-Location "$RepoRoot\android"
try {
    .\gradlew.bat assembleRelease
    if ($LASTEXITCODE -ne 0) { throw "Android assembleRelease failed with exit code $LASTEXITCODE" }
} finally {
    Pop-Location
}
$AndroidApkPath = "$RepoRoot\android\app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path $AndroidApkPath)) {
    throw "Release APK not found at $AndroidApkPath"
}
Copy-Item $AndroidApkPath "$DistDir\SpeedShare-$Tag.apk" -Force

# 5. Compute SHA-256 sidecars
Write-Host "`n[4/5] Computing SHA-256 sidecars..." -ForegroundColor Yellow
& "$PSScriptRoot\compute-release-hashes.ps1" -DistDir $DistDir

# 6. Git commit & push and GitHub Release
Write-Host "`n[5/5] Creating Git commit, tag, and GitHub Release..." -ForegroundColor Yellow

$ReleaseTitle = "SpeedShare $Tag — Complete UI/UX Redesign for Windows & Android"
$ReleaseNotes = @"
## 🚀 SpeedShare $Tag

A ground-up redesign of both apps around a single new identity — **"Prism"**:
adaptive light & dark themes, an electric **blue → cyan** brand system, and
native-feeling components on each platform. This is the biggest visual and
interaction update since 1.0.

### 🎨 Android (Jetpack Compose · Material 3)
- **Adaptive light / dark / system themes** with a new semantic token system — switch live from Settings.
- **Rebuilt onboarding** around the three promises: *Private by design · Direct & instant · Full line-rate speed*.
- **Redesigned Home & radar hero** with an adaptive peer-status pill and brand-gradient action bar.
- **New bottom-sheet flows** for incoming transfers and live progress, with inline success / failure results.
- **Day-grouped History** (Today / Yesterday / date) with restyled search and filter chips.
- **Rebuilt Settings** with an Appearance section, and refreshed update/network/about groups.

### 🖥️ Windows (WPF)
- **Live light & dark themes** — follow the system or toggle from the header / Settings; persisted per user.
- **New navigation**: Home / Settings rail with a proper Devices panel and drop-zone send deck.
- **Segoe Fluent icons** replace emoji throughout; every card, dialog, and overlay restyled to the brand.
- **Installer restyled** to match, following the OS theme.

### 🧩 Shared
- New **launcher icon** across Windows and Android: a blue→cyan gradient tile with a share glyph.
- Version bumped to **2.0.0** with matching Win32 manifests.

---

### 🛡️ Verified Checksums & Sidecars
Every release asset includes a cryptographic SHA-256 sidecar file for in-app integrity verification.

| Asset | Description |
|---|---|
| ``SpeedShare-$Tag.apk`` | Signed Android application package (v1+v2+v3 signing) |
| ``SpeedShare-Setup.exe`` | Windows setup installer with start menu & desktop integration |
| ``SpeedShare-Windows-Portable.exe`` | Standalone portable Windows executable |

---
**Full Changelog**: https://github.com/tanvir-cpp/SpeedShare/compare/v1.2.1...$Tag
"@

if (-not $SkipGitPush) {
    # Stage release source and metadata without including signing keys or
    # backup files that may be present in the Android keystore directory.
    git add Install-SpeedShare.ps1 Uninstall-SpeedShare.ps1 README.md VERSION protocol scripts android/app windows windows-installer windows.Tests
    git commit -m "release: SpeedShare $Tag - complete UI/UX redesign (Prism)"
    git push origin main
    
    $distFiles = Get-ChildItem "$DistDir\*" | ForEach-Object { $_.FullName }
    gh release create $Tag $distFiles --title "$ReleaseTitle" --notes "$ReleaseNotes"
    Write-Host "`n✅ Successfully released and published $Tag to GitHub!" -ForegroundColor Green
} else {
    Write-Host "`nArtifacts built in $DistDir. Git push skipped." -ForegroundColor Cyan
}
