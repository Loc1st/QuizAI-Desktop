[CmdletBinding()]
param([switch]$DeployV2)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Default execution is a pure preview: no credentials, Gradle or database access.
if (-not $DeployV2) {
    Write-Host 'PREVIEW only. No credentials requested and no database connection opened.'
    Write-Host 'DeployV2: offline tests + frozen manifest verification -> create only missing local V2 identities -> TEST gates -> DEV -> full build/test.'
    Write-Host 'Administration is restricted to 127.0.0.1:5432/template1. Existing identities are verified and preserved.'
    Write-Host 'Run this script with -DeployV2 to execute the authorized local V2 deployment.'
    return
}

$repository = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$wrapper = Join-Path $repository 'gradlew.bat'
$previousJavaHome = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Process')
$locationPushed = $false
$processVariables = @(
    'QUIZAI_V2_ADMIN_DB_URL', 'QUIZAI_V2_ADMIN_DB_USERNAME', 'QUIZAI_V2_ADMIN_DB_PASSWORD',
    'QUIZAI_V2_TEST_DB_URL', 'QUIZAI_V2_TEST_DB_USERNAME', 'QUIZAI_V2_TEST_DB_PASSWORD',
    'QUIZAI_V2_DEV_DB_URL', 'QUIZAI_V2_DEV_DB_USERNAME', 'QUIZAI_V2_DEV_DB_PASSWORD',
    'QUIZAI_V2_PROVISION_AUTHORIZATION', 'QUIZAI_V2_DATABASE_AUTHORIZATION'
)

function Set-ProcessSecretFromPrompt {
    param([string]$Name, [string]$Prompt)
    $secureValue = $null
    $plainValue = $null
    $buffer = [IntPtr]::Zero
    try {
        $secureValue = Read-Host -Prompt $Prompt -AsSecureString
        $buffer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureValue)
        $plainValue = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($buffer)
        if ([string]::IsNullOrWhiteSpace($plainValue)) { throw 'A required local credential was empty.' }
        [Environment]::SetEnvironmentVariable($Name, $plainValue, 'Process')
    }
    finally {
        $plainValue = $null
        if ($buffer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($buffer) }
        if ($null -ne $secureValue) { $secureValue.Dispose() }
    }
}

function Invoke-OfflineGradle {
    param([string[]]$Tasks)
    # No daemon/configuration cache retains process credentials; secrets never enter arguments.
    # Windows PowerShell can promote harmless JVM stderr warnings to terminating errors.
    # Gradle's exit code, checked below, remains the authority for success/failure.
    $previousPreference = $ErrorActionPreference
    $gradleExit = 1
    try {
        $ErrorActionPreference = 'Continue'
        & $wrapper @Tasks --offline --console=plain --no-daemon --no-configuration-cache
        $gradleExit = $LASTEXITCODE
    }
    finally { $ErrorActionPreference = $previousPreference }
    if ($gradleExit -ne 0) { throw 'Gradle stopped the V2 deployment. Review the nonsecret task output above.' }
}

try {
    Push-Location -LiteralPath $repository
    $locationPushed = $true
    # Select an installed Java 21 JDK without changing user/system configuration.
    $javaCandidates = [Collections.Generic.List[string]]::new()
    if (-not [string]::IsNullOrWhiteSpace($previousJavaHome)) { $javaCandidates.Add($previousJavaHome) }
    foreach ($jdkRoot in @((Join-Path $env:ProgramFiles 'Eclipse Adoptium'), (Join-Path $env:ProgramFiles 'Java'), (Join-Path $env:USERPROFILE '.jdks'))) {
        if (Test-Path -LiteralPath $jdkRoot -PathType Container) {
            foreach ($candidate in (Get-ChildItem -LiteralPath $jdkRoot -Directory)) { $javaCandidates.Add($candidate.FullName) }
        }
    }
    $selectedJava = $null
    foreach ($candidate in $javaCandidates) {
        $release = Join-Path $candidate 'release'
        if ((Test-Path -LiteralPath $release -PathType Leaf) -and (Test-Path -LiteralPath (Join-Path $candidate 'bin/javac.exe') -PathType Leaf)) {
            $releaseText = Get-Content -LiteralPath $release -Raw -Encoding UTF8
            if ($releaseText -match '(?m)^JAVA_VERSION="21(?:\.|\+|")') { $selectedJava = $candidate; break }
        }
    }
    if ($null -eq $selectedJava) { throw 'Java 21 JDK is required. Set JAVA_HOME to the installed Java 21 JDK and rerun.' }
    [Environment]::SetEnvironmentVariable('JAVA_HOME', $selectedJava, 'Process')

    # All preflight checks complete before asking for secrets or opening PostgreSQL.
    Invoke-OfflineGradle -Tasks @(':quizai-server:v2SchemaTest', ':quizai-server:prepareV2Schema')
    Invoke-OfflineGradle -Tasks @(':quizai-server:v2Database', '-Pv2DatabaseAction=verifyRelease')

    Write-Host 'Enter credentials locally. New V2 passwords must use printable ASCII without spaces; existing passwords are never changed.'
    Set-ProcessSecretFromPrompt -Name 'QUIZAI_V2_ADMIN_DB_USERNAME' -Prompt 'Local PostgreSQL administrator role (hidden)'
    Set-ProcessSecretFromPrompt -Name 'QUIZAI_V2_ADMIN_DB_PASSWORD' -Prompt 'Local PostgreSQL administrator password'
    Set-ProcessSecretFromPrompt -Name 'QUIZAI_V2_TEST_DB_PASSWORD' -Prompt 'Password for quizai_v2_test (existing if it already exists)'
    Set-ProcessSecretFromPrompt -Name 'QUIZAI_V2_DEV_DB_PASSWORD' -Prompt 'Password for quizai_v2_dev (existing if it already exists)'

    [Environment]::SetEnvironmentVariable('QUIZAI_V2_ADMIN_DB_URL', 'jdbc:postgresql://127.0.0.1:5432/template1', 'Process')
    foreach ($kind in @('TEST', 'DEV')) {
        $database = 'quizai_v2_' + $kind.ToLowerInvariant()
        [Environment]::SetEnvironmentVariable(('QUIZAI_V2_' + $kind + '_DB_URL'), ('jdbc:postgresql://127.0.0.1:5432/' + $database), 'Process')
        [Environment]::SetEnvironmentVariable(('QUIZAI_V2_' + $kind + '_DB_USERNAME'), $database, 'Process')
    }
    [Environment]::SetEnvironmentVariable('QUIZAI_V2_PROVISION_AUTHORIZATION', 'AUTHORIZE_LOCAL_V2_PROVISION', 'Process')
    Invoke-OfflineGradle -Tasks @(':quizai-server:provisionV2Databases', '-Pv2ProvisionAction=provision')

    # Remove administrator credentials immediately after provisioning, before any runner/build.
    foreach ($name in @('QUIZAI_V2_ADMIN_DB_URL', 'QUIZAI_V2_ADMIN_DB_USERNAME', 'QUIZAI_V2_ADMIN_DB_PASSWORD', 'QUIZAI_V2_PROVISION_AUTHORIZATION')) {
        [Environment]::SetEnvironmentVariable($name, $null, 'Process')
    }
    [Environment]::SetEnvironmentVariable('QUIZAI_V2_DATABASE_AUTHORIZATION', 'AUTHORIZE_V2_DEPLOY', 'Process')
    Invoke-OfflineGradle -Tasks @(':quizai-server:v2Database', '-Pv2DatabaseAction=deploy')

    # Keep catalog/release evidence under build/. A clean would remove rerun evidence.
    foreach ($name in $processVariables) { [Environment]::SetEnvironmentVariable($name, $null, 'Process') }
    Invoke-OfflineGradle -Tasks @('build', 'test', '--rerun-tasks')
    Write-Host 'V2 deployment and full Gradle build/test completed. API, JavaFX and Gemini runtime checks remain separate.'
}
catch {
    # Avoid serializing secret-bearing command objects or nested exceptions.
    Write-Error -Message 'V2 deployment stopped. No automatic reset, repair, drop, credential persistence or retry was performed.' -ErrorAction Continue
    throw
}
finally {
    foreach ($name in $processVariables) { [Environment]::SetEnvironmentVariable($name, $null, 'Process') }
    [Environment]::SetEnvironmentVariable('JAVA_HOME', $previousJavaHome, 'Process')
    if ($locationPushed) { Pop-Location }
}
