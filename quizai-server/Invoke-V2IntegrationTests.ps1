[CmdletBinding()]
param()

# Windows PowerShell5.1: TEST-only credentials, hidden prompt, no provisioning/migrations.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
$saved = @{}
foreach ($name in @('QUIZAI_V2_TEST_DB_URL','QUIZAI_V2_TEST_DB_USERNAME','QUIZAI_V2_TEST_DB_PASSWORD')) {
    $saved[$name] = [Environment]::GetEnvironmentVariable($name,'Process')
}
try {
    if ([string]::IsNullOrWhiteSpace($env:QUIZAI_V2_TEST_DB_URL)) { $env:QUIZAI_V2_TEST_DB_URL='jdbc:postgresql://127.0.0.1:5432/quizai_v2_test' }
    if ([string]::IsNullOrWhiteSpace($env:QUIZAI_V2_TEST_DB_USERNAME)) { $env:QUIZAI_V2_TEST_DB_USERNAME='quizai_v2_test' }
    if ($env:QUIZAI_V2_TEST_DB_URL -notmatch '^jdbc:postgresql://(127\.0\.0\.1|localhost|\[::1\]):5432/quizai_v2_test$' -or $env:QUIZAI_V2_TEST_DB_USERNAME -cne 'quizai_v2_test') {
        throw 'V2 integration target rejected before credential prompt: dedicated local quizai_v2_test only; values withheld'
    }
    if ([string]::IsNullOrWhiteSpace($env:QUIZAI_V2_TEST_DB_PASSWORD)) {
        $secure=Read-Host -Prompt 'Password for local quizai_v2_test (hidden; do not send to chat)' -AsSecureString
        $pointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
        try { $env:QUIZAI_V2_TEST_DB_PASSWORD=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
        finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer); $secure.Dispose() }
    }
    if ([string]::IsNullOrWhiteSpace($env:QUIZAI_V2_TEST_DB_PASSWORD) -or $env:QUIZAI_V2_TEST_DB_PASSWORD.Contains('${')) { throw 'V2 TEST password missing or unresolved; value withheld' }
    # Fresh Gradle invocation inherits this process environment. Do not pass secrets via -P/-D or command arguments.
    Write-Host 'Running real V2 TEST integration; identity/schema/checksum before fixtures, rollback required. DEV variables are not needed.'
    & (Join-Path $repository 'gradlew.bat') -p $repository :quizai-server:v2IntegrationTest --no-daemon --rerun-tasks --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'V2 PostgreSQL integration FAILED; inspect integrationTest XML for the guard/stage code; do not repair/reset' }
} finally {
    foreach ($name in $saved.Keys) { [Environment]::SetEnvironmentVariable($name,$saved[$name],'Process') }
}
