param([switch] $Seed)

# Explicit setup only; never invoked by normal application startup or unit tests.
$setupArguments = '--spring.main.web-application-type=none --spring.sql.init.mode=never --logging.level.root=OFF --spring.main.banner-mode=off --spring.datasource.hikari.connection-timeout=5000 --spring.datasource.hikari.initialization-fail-timeout=-1'
if ($Seed) { $setupArguments += ' --telecom.setup.seed=true' }
& "$PSScriptRoot\mvn.ps1" '-B' '-ntp' 'spring-boot:run' `
    '-Dstart-class=com.amdocs.telecom.tool.DatabaseSetup' `
    "-Dspring-boot.run.arguments=$setupArguments"
exit $LASTEXITCODE
