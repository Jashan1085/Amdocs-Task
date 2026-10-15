param([int] $Port = 0, [switch] $Authentication)

# Owns its temporary cluster. Does not read the application's private database settings.
$ErrorActionPreference = 'Stop'
$testRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('telecom-dao-test-' + [guid]::NewGuid().ToString('N'))
$dataDirectory = Join-Path $testRoot 'data'
$oldTestUrl = $env:TELECOM_TEST_DB_URL
$oldTestUser = $env:TELECOM_TEST_DB_USERNAME
$oldTestPassword = $env:TELECOM_TEST_DB_PASSWORD
$oldAuthUrl = $env:TELECOM_AUTH_TEST_DB_URL
$started = $false
$testExitCode = 1

foreach ($binary in @('initdb.exe', 'pg_ctl.exe', 'createdb.exe')) {
    if (-not (Get-Command $binary -ErrorAction SilentlyContinue)) {
        throw 'PostgreSQL tools (initdb, pg_ctl, createdb) must be on PATH.'
    }
}
if ($Port -eq 0) {
    $listener = New-Object System.Net.Sockets.TcpListener([System.Net.IPAddress]::Loopback, 0)
    try {
        $listener.Start()
        $Port = $listener.LocalEndpoint.Port
    } finally { $listener.Stop() }
}
if ($Port -lt 1024 -or $Port -gt 65535) { throw 'Use an available unprivileged port (1024-65535).' }

try {
    New-Item -ItemType Directory -Path $testRoot | Out-Null
    & initdb.exe -D $dataDirectory -U telecom_test -A trust --no-locale -E UTF8 *> (Join-Path $testRoot 'initdb.log')
    if ($LASTEXITCODE -ne 0) { throw 'Temporary PostgreSQL initialization failed.' }
    & pg_ctl.exe -D $dataDirectory -l (Join-Path $testRoot 'server.log') `
        -o "-p $Port -h 127.0.0.1 -c shared_buffers=16MB -c max_connections=20" -w start
    if ($LASTEXITCODE -ne 0) { throw 'Temporary PostgreSQL startup failed.' }
    $started = $true
    & createdb.exe -h 127.0.0.1 -p $Port -U telecom_test telecom_oss_dao_test
    if ($LASTEXITCODE -ne 0) { throw 'Temporary test database creation failed.' }

    $env:TELECOM_TEST_DB_URL = "jdbc:postgresql://127.0.0.1:$Port/telecom_oss_dao_test"
    $env:TELECOM_TEST_DB_USERNAME = 'telecom_test'
    $env:TELECOM_TEST_DB_PASSWORD = ''
    $testArguments = @('-B', '-ntp', '-Dtelecom.dao.integration=true')
    if ($Authentication) {
        & createdb.exe -h 127.0.0.1 -p $Port -U telecom_test telecom_oss_auth_test
        if ($LASTEXITCODE -ne 0) { throw 'Temporary authentication database creation failed.' }
        $env:TELECOM_AUTH_TEST_DB_URL = "jdbc:postgresql://127.0.0.1:$Port/telecom_oss_auth_test"
        $testArguments += '-Dtelecom.auth.integration=true'
    }
    & "$PSScriptRoot\mvn.ps1" @testArguments 'test'
    $testExitCode = $LASTEXITCODE
} finally {
    $env:TELECOM_TEST_DB_URL = $oldTestUrl
    $env:TELECOM_TEST_DB_USERNAME = $oldTestUser
    $env:TELECOM_TEST_DB_PASSWORD = $oldTestPassword
    $env:TELECOM_AUTH_TEST_DB_URL = $oldAuthUrl
    if ($started) {
        & pg_ctl.exe -D $dataDirectory -m fast -w stop
        if ($LASTEXITCODE -eq 0) { $started = $false } else { $testExitCode = 1 }
    }
    # Resolve and validate the generated target before recursive cleanup.
    if (-not $started -and (Test-Path -LiteralPath $testRoot)) {
        $resolvedTarget = (Resolve-Path -LiteralPath $testRoot).Path
        $resolvedTemp = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd('\') + '\'
        if (-not $resolvedTarget.StartsWith($resolvedTemp, [System.StringComparison]::OrdinalIgnoreCase) `
            -or (Split-Path -Leaf $resolvedTarget) -notmatch '^telecom-dao-test-[0-9a-f]{32}$') {
            throw 'Refusing cleanup: generated test directory failed workspace safety check.'
        }
        Remove-Item -LiteralPath $resolvedTarget -Recurse -Force
    }
}
exit $testExitCode
