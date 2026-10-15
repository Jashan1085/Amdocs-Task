# Run after scripts/mvn.ps1 package. Only this helper's own Java process is stopped.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$jarPath = Join-Path $projectRoot 'target\telecom-assurance-0.0.1-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $jarPath)) { throw 'Build the runnable JAR before verification.' }
$candidates = @($env:TELECOM_JAVA_HOME, $env:JAVA_HOME)
foreach ($jdkRoot in @("$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Java", "$env:USERPROFILE\.jdks")) {
    if (Test-Path -LiteralPath $jdkRoot) { $candidates += (Get-ChildItem -LiteralPath $jdkRoot -Directory | Sort-Object Name -Descending).FullName }
}
$javaPath = $null
foreach ($candidate in $candidates) {
    if (-not $candidate) { continue }
    $compiler = Join-Path $candidate 'bin\javac.exe'
    if (Test-Path -LiteralPath $compiler) {
        $version = & $compiler -version 2>&1
        if ($LASTEXITCODE -eq 0 -and "$version" -match 'javac 1\.8\.') { $javaPath = Join-Path $candidate 'bin\java.exe'; break }
    }
}
if (-not $javaPath) { throw 'Java 8 JDK not found; set TELECOM_JAVA_HOME.' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead($jarPath)
try {
    foreach ($entry in $archive.Entries) {
        if ($entry.FullName -match '(^|/)(application-local\.properties|dev-accounts\.properties|\.env)$') { throw 'Private configuration found in packaged JAR.' }
    }
    $compiled = $archive.GetEntry('BOOT-INF/classes/com/amdocs/telecom/TelecomApplication.class')
    if (-not $compiled) { throw 'Runnable Boot class missing.' }
    $stream = $compiled.Open()
    try {
        $header = New-Object byte[] 8
        if ($stream.Read($header, 0, 8) -ne 8 -or $header[6] -ne 0 -or $header[7] -ne 52) { throw 'Application is not Java 8 bytecode.' }
    } finally { $stream.Dispose() }
} finally { $archive.Dispose() }
Write-Output 'Package inspection: SUCCESS (Java 8 bytecode; private configuration excluded)'
Push-Location $projectRoot
try {
    & $javaPath '-cp' 'target/classes' 'com.amdocs.telecom.assessment.SampleExports' 'docs/samples'
    if ($LASTEXITCODE -ne 0) { throw 'Synthetic sample export generation failed.' }
} finally { Pop-Location }
$listener = New-Object System.Net.Sockets.TcpListener([System.Net.IPAddress]::Loopback, 0)
try { $listener.Start(); $port = $listener.LocalEndpoint.Port } finally { $listener.Stop() }
$runId = [Guid]::NewGuid().ToString('N')
$stdoutPath = Join-Path $projectRoot "target\delivery-$runId.stdout.log"
$stderrPath = Join-Path $projectRoot "target\delivery-$runId.stderr.log"
$appProcess = $null
try {
    $launchArguments = @('-jar', ('"' + $jarPath + '"'), '--spring.profiles.active=dev', "--server.port=$port", '--server.address=127.0.0.1',
        '--telecom.sla.monitor.enabled=false', '--telecom.jobs.enabled=false', '--spring.datasource.hikari.read-only=true',
        '--spring.main.banner-mode=off', '--logging.level.root=OFF')
    $appProcess = Start-Process -FilePath $javaPath -ArgumentList $launchArguments -WorkingDirectory $projectRoot -WindowStyle Hidden `
        -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath -PassThru
    $ready = $false
    $until = [DateTime]::UtcNow.AddSeconds(45)
    while ([DateTime]::UtcNow -lt $until -and -not $appProcess.HasExited) {
        try {
            $page = Invoke-WebRequest -Uri "http://127.0.0.1:$port/login" -UseBasicParsing -TimeoutSec 2
            if ($page.StatusCode -eq 200 -and $page.Content -match 'Continue to verification' -and $page.Content -match 'captchaId') { $ready = $true; break }
        } catch { }
        Start-Sleep -Milliseconds 250
    }
    if (-not $ready) { throw 'Packaged application did not serve the expected login page; inspect ignored delivery logs.' }
    $request = [System.Net.HttpWebRequest]::Create("http://127.0.0.1:$port/manager/dashboard")
    $request.AllowAutoRedirect = $false
    $request.Timeout = 3000
    $response = $request.GetResponse()
    try {
        if ([int]$response.StatusCode -ne 302 -or $response.Headers['Location'] -notmatch '/login$') { throw 'Unauthenticated workspace did not redirect to login.' }
    } finally { $response.Dispose() }
    Write-Output 'Packaged smoke test: SUCCESS (login/CAPTCHA and protected-route redirect; background jobs disabled, datasource read-only)'
} finally {
    if ($appProcess -and -not $appProcess.HasExited) {
        Stop-Process -Id $appProcess.Id -Force
        $appProcess.WaitForExit(5000) | Out-Null
    }
}
