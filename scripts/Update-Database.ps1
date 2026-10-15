param([switch] $DevAccounts)

$migrationArguments = '--spring.main.web-application-type=none --spring.sql.init.mode=never --logging.level.root=OFF --spring.main.banner-mode=off --spring.datasource.hikari.connection-timeout=5000 --spring.datasource.hikari.initialization-fail-timeout=-1'
if ($DevAccounts) {
    $migrationArguments += ' --spring.profiles.active=dev --telecom.migrate.dev-accounts=true'
}
& "$PSScriptRoot\mvn.ps1" '-B' '-ntp' 'spring-boot:run' `
    '-Dstart-class=com.amdocs.telecom.tool.DatabaseMigrate' `
    "-Dspring-boot.run.arguments=$migrationArguments"
exit $LASTEXITCODE
