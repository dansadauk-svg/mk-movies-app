$ErrorActionPreference = "Stop"
$keyPath = Join-Path $PSScriptRoot "..\kb-movies-release-key.jks"

if (-not (Test-Path $keyPath)) {
    throw "Place kb-movies-release-key.jks in the project root before running this script."
}

$base64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($keyPath))
Set-Clipboard -Value $base64
Write-Host "KB_KEYSTORE_BASE64 copied to the clipboard. Paste it into the GitHub Actions secret."
