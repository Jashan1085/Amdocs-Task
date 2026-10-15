param([int] $Port = 0)
& "$PSScriptRoot\Test-DaoIntegration.ps1" -Port $Port -Authentication
exit $LASTEXITCODE
