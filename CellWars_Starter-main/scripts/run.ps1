$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$Bin = Join-Path $Root "bin"
$Src = Join-Path $Root "src"

New-Item -ItemType Directory -Force -Path $Bin | Out-Null
$Files = Get-ChildItem -Path $Src -Filter "*.java" | ForEach-Object { $_.FullName }

& javac -d $Bin $Files
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

& java -cp $Bin Simulation
exit $LASTEXITCODE
