param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $MavenArgs = @('test')
)

$ErrorActionPreference = 'Stop'
$previousJavaHome = $env:JAVA_HOME
$projectRoot = Split-Path -Parent $PSScriptRoot

function Test-Java8Jdk([string] $Candidate) {
    if (-not $Candidate -or -not (Test-Path -LiteralPath (Join-Path $Candidate 'bin\javac.exe'))) {
        return $false
    }
    $version = & (Join-Path $Candidate 'bin\javac.exe') -version 2>&1
    return ($LASTEXITCODE -eq 0 -and "$version" -match 'javac 1\.8\.')
}

try {
    $selectedJdk = $null
    foreach ($candidate in @($env:TELECOM_JAVA_HOME, $env:JAVA_HOME)) {
        if (Test-Java8Jdk $candidate) {
            $selectedJdk = $candidate
            break
        }
    }
    if (-not $selectedJdk) {
        foreach ($jdkRoot in @("$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Java", "$env:USERPROFILE\.jdks")) {
            if (Test-Path -LiteralPath $jdkRoot) {
                foreach ($candidate in (Get-ChildItem -LiteralPath $jdkRoot -Directory | Sort-Object Name -Descending)) {
                    if (Test-Java8Jdk $candidate.FullName) {
                        $selectedJdk = $candidate.FullName
                        break
                    }
                }
            }
            if ($selectedJdk) { break }
        }
    }
    if (-not $selectedJdk) {
        throw 'Java 8 JDK not found. Set TELECOM_JAVA_HOME to a JDK 8 directory (not its bin folder).'
    }
    $env:JAVA_HOME = $selectedJdk
    Push-Location $projectRoot
    try {
        & mvn.cmd @MavenArgs
        $mavenExitCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }
} finally {
    $env:JAVA_HOME = $previousJavaHome
}
exit $mavenExitCode
