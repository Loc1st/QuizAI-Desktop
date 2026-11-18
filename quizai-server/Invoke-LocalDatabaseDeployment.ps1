[CmdletBinding()]
param(
    [ValidateSet('inspect', 'deploy')]
    [string]$Action = 'inspect',
    [string]$JavaHome = $env:JAVA_HOME
)

$ErrorActionPreference = 'Stop'
# Invoke this script FROM the PowerShell session that already holds the secrets.
# Never echo environment values or pass credentials on a command line.
foreach ($environmentName in @('TEST', 'DEV')) {
    foreach ($field in @('URL', 'USERNAME', 'PASSWORD')) {
        $variableName = 'QUIZAI_' + $environmentName + '_DB_' + $field
        if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($variableName, 'Process'))) {
            throw ('Missing process environment variable ' + $variableName + '. Load it in this PowerShell session; do not send secrets to chat.')
        }
    }
}
if ([string]::IsNullOrWhiteSpace($JavaHome) -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe'))) {
    throw 'Provide an installed JDK 21 directory with -JavaHome, or set JAVA_HOME in this session.'
}
$javaExecutable = Join-Path $JavaHome 'bin/java.exe'
$javaVersion = (& $javaExecutable --version | Out-String)
if ($LASTEXITCODE -ne 0 -or $javaVersion -notmatch '(?m)^(?:openjdk|java) 21(?:[.\s])') {
    throw 'This deployment requires JDK 21.'
}

$previousJavaHome = $env:JAVA_HOME
$repositoryRoot = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $repositoryRoot
try {
    $env:JAVA_HOME = $JavaHome
    & .\gradlew.bat :quizai-server:deployLocalDatabases ('-PdatabaseAction=' + $Action) --no-daemon --console=plain
    if ($LASTEXITCODE -ne 0) {
        throw 'Database operation stopped. Do not clean/repair/drop a database; inspect the non-secret diagnostic and source.'
    }
} finally {
    $env:JAVA_HOME = $previousJavaHome
    Pop-Location
}
