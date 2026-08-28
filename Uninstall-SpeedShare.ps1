<#
.SYNOPSIS
    Automated Uninstaller for SpeedShare on Windows.
#>

$ErrorActionPreference = "SilentlyContinue"

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "   SpeedShare Windows Uninstaller" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

$InstallDir = "$env:LOCALAPPDATA\Programs\SpeedShare"

Write-Host "[1/4] Terminating running SpeedShare processes..." -ForegroundColor Gray
Get-Process -Name "SpeedShareWindows" -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue

Write-Host "[2/4] Removing Desktop and Start Menu shortcuts..." -ForegroundColor Gray
$DesktopShortcut = "$env:USERPROFILE\Desktop\SpeedShare.lnk"
if (Test-Path $DesktopShortcut) { Remove-Item -Path $DesktopShortcut -Force }

$StartShortcut = "$env:APPDATA\Microsoft\Windows\Start Menu\Programs\SpeedShare.lnk"
if (Test-Path $StartShortcut) { Remove-Item -Path $StartShortcut -Force }

Write-Host "[3/4] Removing Windows Registry entries..." -ForegroundColor Gray
$RegKey = "HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\SpeedShare"
if (Test-Path $RegKey) { Remove-Item -Path $RegKey -Recurse -Force }

Write-Host "[4/4] Removing application files from: $InstallDir..." -ForegroundColor Gray
if (Test-Path $InstallDir) {
    Remove-Item -Path $InstallDir -Recurse -Force
}

Write-Host "`n[+] SpeedShare has been completely uninstalled from your PC." -ForegroundColor Green
