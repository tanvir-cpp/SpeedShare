<#
.SYNOPSIS
    Automated Installer for SpeedShare on Windows.
#>

$ErrorActionPreference = "Stop"

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "   SpeedShare Windows Installer" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

$InstallDir = "$env:LOCALAPPDATA\Programs\SpeedShare"
$SourceDir = "$PSScriptRoot\windows\publish"

if (-not (Test-Path $SourceDir)) {
    Write-Host "[*] Building latest release binaries..." -ForegroundColor Yellow
    dotnet publish "$PSScriptRoot\windows\SpeedShareWindows.csproj" -c Release -r win-x64 --self-contained false -o "$SourceDir" | Out-Null
}

Write-Host "[1/4] Stopping any existing SpeedShare processes..." -ForegroundColor Gray
Get-Process -Name "SpeedShareWindows" -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue

Write-Host "[2/4] Deploying application files to: $InstallDir" -ForegroundColor Gray
if (-not (Test-Path $InstallDir)) {
    New-Item -ItemType Directory -Path $InstallDir -Force | Out-Null
}
Copy-Item -Path "$SourceDir\*" -Destination $InstallDir -Recurse -Force

Write-Host "[3/4] Creating Desktop and Start Menu shortcuts..." -ForegroundColor Gray
$WshShell = New-Object -ComObject WScript.Shell
$TargetExe = "$InstallDir\SpeedShareWindows.exe"

# Desktop Shortcut
$DesktopShortcut = $WshShell.CreateShortcut("$env:USERPROFILE\Desktop\SpeedShare.lnk")
$DesktopShortcut.TargetPath = $TargetExe
$DesktopShortcut.WorkingDirectory = $InstallDir
$DesktopShortcut.Description = "SpeedShare - Ultra Fast Local Transfer"
$DesktopShortcut.IconLocation = "$TargetExe,0"
$DesktopShortcut.Save()

# Start Menu Shortcut
$StartMenuDir = "$env:APPDATA\Microsoft\Windows\Start Menu\Programs"
$StartShortcut = $WshShell.CreateShortcut("$StartMenuDir\SpeedShare.lnk")
$StartShortcut.TargetPath = $TargetExe
$StartShortcut.WorkingDirectory = $InstallDir
$StartShortcut.Description = "SpeedShare - Ultra Fast Local Transfer"
$StartShortcut.IconLocation = "$TargetExe,0"
$StartShortcut.Save()

Write-Host "[4/4] Registering in Windows (Apps & Features / Control Panel)..." -ForegroundColor Gray
$RegKey = "HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\SpeedShare"
if (-not (Test-Path $RegKey)) {
    New-Item -Path $RegKey -Force | Out-Null
}
Set-ItemProperty -Path $RegKey -Name "DisplayName" -Value "SpeedShare"
Set-ItemProperty -Path $RegKey -Name "DisplayVersion" -Value "1.1.0"
Set-ItemProperty -Path $RegKey -Name "Publisher" -Value "SpeedShare Team"
Set-ItemProperty -Path $RegKey -Name "InstallLocation" -Value $InstallDir
Set-ItemProperty -Path $RegKey -Name "UninstallString" -Value "powershell.exe -ExecutionPolicy Bypass -File `"$PSScriptRoot\Uninstall-SpeedShare.ps1`""
Set-ItemProperty -Path $RegKey -Name "DisplayIcon" -Value "$TargetExe,0"
Set-ItemProperty -Path $RegKey -Name "EstimatedSize" -Value 512 -Type DWord
Set-ItemProperty -Path $RegKey -Name "NoModify" -Value 1 -Type DWord
Set-ItemProperty -Path $RegKey -Name "NoRepair" -Value 1 -Type DWord

Write-Host "`n[+] SpeedShare has been successfully installed!" -ForegroundColor Green
Write-Host "    You can launch it from your Desktop, Start Menu, or by searching 'SpeedShare'." -ForegroundColor White
