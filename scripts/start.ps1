$ErrorActionPreference = 'Stop'
$jar = Join-Path (Split-Path $PSScriptRoot -Parent) 'target/filepilot-0.1.0.jar'
& java -jar $jar @args
exit $LASTEXITCODE
