param(
    [Parameter(Mandatory = $true)]
    [string]$RepoUrl,
    [string]$DefaultBranch = "main"
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    throw "Git client nebyl nalezen v PATH. Nainstaluj Git for Windows a spust script znovu."
}

if (-not (Test-Path .git)) {
    git init
}

# Normalize branch name
$currentBranch = git rev-parse --abbrev-ref HEAD 2>$null
if ($LASTEXITCODE -eq 0 -and $currentBranch -ne $DefaultBranch) {
    git branch -M $DefaultBranch
}

$hasOrigin = git remote get-url origin 2>$null
if ($LASTEXITCODE -eq 0) {
    git remote set-url origin $RepoUrl
} else {
    git remote add origin $RepoUrl
}

Write-Host "Remote origin nastaven na: $RepoUrl"
Write-Host "Pokud je to nový repozitár, spust ted:"
Write-Host "  git add -A"
Write-Host "  git commit -m 'Initial backup'"
Write-Host "  git push -u origin $DefaultBranch"
