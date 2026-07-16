$ErrorActionPreference = 'Stop'

$expectedHash = '5dd222b9e266d2ac2d63b3dad4983eb05caaf5a247d7dfb82aaeba47ea774cc8'
$modRoot = (Resolve-Path $PSScriptRoot).Path
$modFolder = Split-Path $modRoot -Leaf
$gameRoot = (Resolve-Path (Join-Path $modRoot '..\..')).Path
$vmparams = Join-Path $gameRoot 'vmparams'
$coreJar = Join-Path $gameRoot 'starsector-core\starfarer_obf.jar'
$agentJar = Join-Path $modRoot 'agent\StarsectorMapOptimizerAgent.jar'
$agentArg = "-javaagent:../mods/$modFolder/agent/StarsectorMapOptimizerAgent.jar"

if (-not (Test-Path -LiteralPath $agentJar -PathType Leaf)) {
    throw "Agent JAR not found: $agentJar"
}
if (-not (Test-Path -LiteralPath $vmparams -PathType Leaf)) {
    throw "vmparams not found: $vmparams`nThe mod must be inside <Starsector>\mods\$modFolder."
}
if ($modFolder -ne 'StarsectorMapOptimizer') {
    Write-Warning "The mod folder is named '$modFolder'. The generated javaagent path will use that name; do not rename it after installation."
}
if ($modFolder -match '\s') {
    throw "The mod folder path contains whitespace. Rename the folder to 'StarsectorMapOptimizer' and run this installer again."
}

if (Test-Path -LiteralPath $coreJar -PathType Leaf) {
    $actualHash = (Get-FileHash -LiteralPath $coreJar -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualHash -ne $expectedHash) {
        Write-Warning "starfarer_obf.jar does not match 0.98a-RC8. Expected $expectedHash, found $actualHash. The agent will fail open and skip patches unless compatibility.requireKnownGameJar=false."
    } else {
        Write-Host 'Verified Starsector 0.98a-RC8 core JAR.' -ForegroundColor Green
    }
} else {
    Write-Warning "Core JAR not found at $coreJar. The agent will perform its own check at startup."
}

$content = [System.IO.File]::ReadAllText($vmparams)
if ($content.Contains($agentArg)) {
    Write-Host 'The Map Optimizer javaagent is already present in vmparams.' -ForegroundColor Yellow
    Write-Host $agentArg
    exit 0
}
if ($content -notmatch '(?i)-noverify') {
    Write-Warning 'vmparams does not contain -noverify. Vanilla 0.98a-RC8 normally has it; keep it enabled because the obfuscated core contains identifiers rejected by full bytecode verification.'
}

$match = [regex]::Match($content, '^\s*(?:"[^"]*javaw?\.exe"|[^\s]*javaw?\.exe)\s+', [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
if (-not $match.Success) {
    throw 'Could not find java.exe/javaw.exe at the beginning of vmparams. No changes were made.'
}

$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backup = "$vmparams.smo-backup-$timestamp"
Copy-Item -LiteralPath $vmparams -Destination $backup -Force
$newContent = $content.Substring(0, $match.Index) + $match.Value + $agentArg + ' ' + $content.Substring($match.Index + $match.Length)
$encoding = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($vmparams, $newContent, $encoding)

Write-Host 'Installed Starsector Map Optimizer javaagent.' -ForegroundColor Green
Write-Host "vmparams backup: $backup"
Write-Host "Added: $agentArg"
Write-Host 'The telemetry javaagent may remain in the same vmparams; multiple -javaagent options are supported.'
