param(
    [string]$Message = "backup: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    throw "Git client nebyl nalezen v PATH."
}

if (-not (Test-Path .git)) {
    throw "Tahle složka není git repozitár."
}

git add -A

$status = git status --porcelain
if (-not $status) {
    Write-Host "No changes to backup."
    exit 0
}

git commit -m $Message

git push origin HEAD

Write-Host "Backup hotový."
