<#
.SYNOPSIS
    Compute SHA-256 of release assets and emit a "release.sha256"
    sidecar that the in-app updater can fetch and verify against.

.DESCRIPTION
    For every .apk and .exe in the dist directory, this script
    writes a sibling .sha256 file containing the hex SHA-256 of the
    asset. The Android UpdateChecker and Windows UpdateCheckerService
    look for these sidecars on the GitHub release and verify the
    downloaded binary's hash before installing it.

    Run this BEFORE `gh release create` / `gh release upload`.
#>

param(
    [string]$DistDir = "$PSScriptRoot\..\dist"
)

if (-not (Test-Path $DistDir)) {
    Write-Error "Dist directory not found: $DistDir"
    exit 1
}

Push-Location $DistDir
try {
    Get-ChildItem -File | Where-Object { $_.Extension -in ".apk", ".exe" } | ForEach-Object {
        $hash = (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        $sidecar = "$($_.FullName).sha256"
        # Sidecar format: "hex  filename" (sha256sum style).
        # Android UpdateChecker parser takes the first whitespace-separated
        # token; the filename is for human auditing.
        Set-Content -Path $sidecar -Value "$hash  $($_.Name)" -NoNewline
        Write-Host "  $($_.Name): $hash"
    }
}
finally {
    Pop-Location
}

Write-Host ""
Write-Host "Done. Upload the .sha256 files alongside the binaries:"
Write-Host "  gh release upload v1.1.2 dist\*"
