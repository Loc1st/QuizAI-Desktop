[CmdletBinding()]
param(
    [switch]$E2E,
    [switch]$KeepServer,
    [switch]$NewDemo,
    [string]$PreviousEvidence,
    [ValidateRange(1024,65535)][int]$Port = 18080
)

# Local V2 application only. No SQL, migration, provisioning or database cleanup.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
$runDirectory = Join-Path $repository ('.gradle/task004/runtime-' + (Get-Date -Format 'yyyyMMddTHHmmssfff'))
New-Item -ItemType Directory -Path $runDirectory -Force | Out-Null
$evidence = [ordered]@{ StartedAt = (Get-Date).ToString('o'); Database = 'quizai_v2_dev'; BackendStartup = 'PENDING'; ApiE2E = 'NOT_RUN'; Checks = @(); Failure = $null }
$ownedServer = $null
$baseUrl = 'http://127.0.0.1:' + $Port
$checkpointPath = Join-Path $repository '.gradle/task004/demo-checkpoint.dpapi'
$checkpoint = $null

function Save-DemoCheckpoint {
    $json = $script:checkpoint | ConvertTo-Json -Depth 60 -Compress
    $secure = ConvertTo-SecureString -String $json -AsPlainText -Force
    try { $encrypted = ConvertFrom-SecureString $secure; [IO.File]::WriteAllText(($script:checkpointPath + '.tmp'), $encrypted); Move-Item -LiteralPath ($script:checkpointPath + '.tmp') -Destination $script:checkpointPath -Force }
    finally { $secure.Dispose() }
}

function Read-DemoCheckpoint {
    $secure = ConvertTo-SecureString ([IO.File]::ReadAllText($script:checkpointPath))
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { return ([Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) | ConvertFrom-Json) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer); $secure.Dispose() }
}

function Read-PreviousDemoState([string]$Path) {
    $old = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
    if ($old.Database -ne 'quizai_v2_dev') { throw 'Previous evidence must be V2 DEV' }
    $from = [DateTimeOffset]::Parse($old.StartedAt).ToString('o')
    $until = [DateTimeOffset]::Parse($old.FinishedAt).ToString('o')
    $psql = Get-Command psql.exe -ErrorAction SilentlyContinue
    $exe = if ($null -ne $psql) { $psql.Source } else { Join-Path $env:ProgramFiles 'PostgreSQL/18/bin/psql.exe' }
    if (-not (Test-Path -LiteralPath $exe)) { throw 'PostgreSQL18 psql is required for read-only previous-demo inspection' }
    $saved = @{}
    foreach ($name in @('PGPASSWORD','PGHOST','PGPORT','PGUSER','PGDATABASE')) { $saved[$name]=[Environment]::GetEnvironmentVariable($name,'Process') }
    try {
        $env:PGPASSWORD=$env:QUIZAI_V2_DEV_DB_PASSWORD; $env:PGHOST='127.0.0.1'; $env:PGPORT='5432'; $env:PGUSER='quizai_v2_dev'; $env:PGDATABASE='quizai_v2_dev'
        $sql = @"
BEGIN READ ONLY;
SET LOCAL statement_timeout='5s';
SELECT json_build_object('identity_ok',current_database()='quizai_v2_dev' AND current_user='quizai_v2_dev' AND (SELECT pg_get_userbyid(datdba)=current_user FROM pg_database WHERE datname=current_database()),
'demo_users',(SELECT count(*) FROM users WHERE email_normalized LIKE '%@demo.quizai.local' AND created_at BETWEEN '$from'::timestamptz AND '$until'::timestamptz),
'classes',(SELECT count(*) FROM classes WHERE name LIKE 'MVP demo %' AND created_at BETWEEN '$from'::timestamptz AND '$until'::timestamptz),
'memberships',(SELECT count(*) FROM class_members m JOIN classes c ON c.id=m.class_id WHERE c.name LIKE 'MVP demo %' AND c.created_at BETWEEN '$from'::timestamptz AND '$until'::timestamptz),
'banks',(SELECT count(*) FROM question_banks b JOIN users u ON u.id=b.owner_teacher_id WHERE u.email_normalized LIKE '%@demo.quizai.local' AND u.created_at BETWEEN '$from'::timestamptz AND '$until'::timestamptz));
ROLLBACK;
"@
        $output = @(& $exe -X -q -A -t --no-password -v ON_ERROR_STOP=1 -c $sql 2>$null)
        if ($LASTEXITCODE -ne 0) { throw 'Read-only prior-demo inspection failed; no E2E writes performed' }
        $state = ($output -join "`n") | ConvertFrom-Json
        if (-not $state.identity_ok) { throw 'Prior-demo identity mismatch' }
        $state | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $script:runDirectory 'previous-demo-state.json') -Encoding UTF8
        Write-Host ('Previous demo read-only counts: users=' + $state.demo_users + '; classes=' + $state.classes + '; memberships=' + $state.memberships + '; banks=' + $state.banks)
    } finally { foreach ($name in $saved.Keys) { [Environment]::SetEnvironmentVariable($name,$saved[$name],'Process') } }
}

function Assert-Demo([bool]$Condition, [string]$Name) {
    if (-not $Condition) { throw ('Demo check failed: ' + $Name) }
    $script:evidence.Checks += $Name
}

function Read-PrivateEnvironment([string]$Name, [string]$Prompt) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($Name, 'Process'))) {
        $secure = Read-Host -Prompt $Prompt -AsSecureString
        $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
        try { [Environment]::SetEnvironmentVariable($Name, [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer), 'Process') }
        finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer); $secure.Dispose() }
    }
}

function Invoke-DemoApi([string]$Method, [string]$Path, $Body = $null, [string]$Token = '', [int]$Expected = 200) {
    # Cache only fixture creation. Retry/idempotency/submit requests always reach the server.
    $cache = $null
    if ($null -ne $script:checkpoint -and $Method -eq 'POST' -and $Path -match '^/api/(auth/register|classes|classes/join|v2/teacher/(banks|questions|quizzes|assignments)|v2/teacher/questions/[^/]+/versions)$') {
        $sha = [Security.Cryptography.SHA256]::Create()
        try { $key=[Convert]::ToBase64String($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes(($Path + ($Body | ConvertTo-Json -Depth 30 -Compress))))) } finally { $sha.Dispose() }
        $cache=@($script:checkpoint.Requests | Where-Object { $_.Key -eq $key })
        if ($cache.Count -gt 0) {
            if ($cache[0].State -ne 'DONE') { throw 'Previous creation outcome is unknown; HOLD for read-only reconciliation, never replay blindly' }
            return $cache[0].Response
        }
        $cache=[PSCustomObject]@{Key=$key;State='PENDING';Response=$null}
        $script:checkpoint.Requests=@($script:checkpoint.Requests)+@($cache); Save-DemoCheckpoint
    }
    $parameters = @{ Method = $Method; Uri = ($script:baseUrl + $Path); UseBasicParsing = $true; TimeoutSec = 40; ErrorAction = 'Stop' }
    if ($Token) { $parameters.Headers = @{ Authorization = ('Bearer ' + $Token) } }
    if ($null -ne $Body) { $parameters.ContentType = 'application/json; charset=utf-8'; $parameters.Body = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 30 -Compress)) }
    try { $response = Invoke-WebRequest @parameters }
    catch {
        $status = 0
        if ($null -ne $_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode }
        if ($status -eq $Expected) { return $null }
        # Intentionally omit request bodies, response bodies, passwords, session tokens and headers.
        throw ('HTTP check failed: ' + $Method + ' ' + $Path + '; status=' + $status + '; expected=' + $Expected)
    }
    if ([int]$response.StatusCode -ne $Expected) { throw ('Unexpected HTTP status: ' + $Method + ' ' + $Path) }
    if (-not [string]::IsNullOrWhiteSpace($response.Content)) {
        $value=$response.Content | ConvertFrom-Json
        if ($null -ne $cache) { $cache.Response=$value; $cache.State='DONE'; Save-DemoCheckpoint }
        return $value
    }
    return $null
}

function New-DemoPassword {
    $bytes = New-Object byte[] 24
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $random.GetBytes($bytes); return ('Demo-' + [Convert]::ToBase64String($bytes)) }
    finally { $random.Dispose() }
}

function Invoke-ConcurrentSubmit([string]$Path, $Body, [string]$Token) {
    Add-Type -AssemblyName System.Net.Http
    $clients = @([Net.Http.HttpClient]::new(), [Net.Http.HttpClient]::new())
    $contents = @()
    $responses = @()
    try {
        $tasks = @()
        foreach ($client in $clients) {
            $client.Timeout = [TimeSpan]::FromSeconds(40)
            $client.DefaultRequestHeaders.Authorization = [Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $Token)
            $content = [Net.Http.StringContent]::new(($Body | ConvertTo-Json -Compress), [Text.Encoding]::UTF8, 'application/json')
            $contents += $content
            $tasks += $client.PostAsync(($script:baseUrl + $Path), $content)
        }
        if (-not [Threading.Tasks.Task]::WaitAll([Threading.Tasks.Task[]]$tasks, 45000)) { throw 'Concurrent submit timed out' }
        foreach ($task in $tasks) {
            $response = $task.Result
            $responses += $response
            if ([int]$response.StatusCode -ne 200) { throw 'Concurrent submit did not return two successful responses' }
            $json = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            $json | ConvertFrom-Json
        }
    } catch { throw 'Concurrent submit verification failed; inspect local backend logs' }
    finally {
        foreach ($response in $responses) { $response.Dispose() }
        foreach ($content in $contents) { $content.Dispose() }
        foreach ($client in $clients) { $client.Dispose() }
    }
}

try {
    if ([string]::IsNullOrWhiteSpace($env:QUIZAI_V2_DEV_DB_URL)) { $env:QUIZAI_V2_DEV_DB_URL = 'jdbc:postgresql://127.0.0.1:5432/quizai_v2_dev' }
    if ([string]::IsNullOrWhiteSpace($env:QUIZAI_V2_DEV_DB_USERNAME)) { $env:QUIZAI_V2_DEV_DB_USERNAME = 'quizai_v2_dev' }
    if ($env:QUIZAI_V2_DEV_DB_URL -notmatch '^jdbc:postgresql://(127\.0\.0\.1|localhost):5432/quizai_v2_dev$' -or $env:QUIZAI_V2_DEV_DB_USERNAME -ne 'quizai_v2_dev') { throw 'Only the dedicated local V2 DEV identity is accepted by this demo' }
    Read-PrivateEnvironment 'QUIZAI_V2_DEV_DB_PASSWORD' 'Password for local quizai_v2_dev (hidden; never paste into chat)'
    if ($PreviousEvidence) { Read-PreviousDemoState $PreviousEvidence }
    Read-PrivateEnvironment 'QUIZAI_CLASS_JOIN_CODE_KEY' 'Persistent private class HMAC key, minimum32bytes (reuse on later starts; hidden)'
    if ([Text.Encoding]::UTF8.GetByteCount($env:QUIZAI_CLASS_JOIN_CODE_KEY) -lt 32) { throw 'Persistent HMAC key must contain at least32UTF8bytes' }
    if ([string]::IsNullOrWhiteSpace($env:JAVA_HOME)) { throw 'Set JAVA_HOME to Java21 before starting the demo' }
    $java = Join-Path $env:JAVA_HOME 'bin/java.exe'
    if (-not (Test-Path -LiteralPath $java)) { throw 'JAVA_HOME does not contain bin/java.exe' }
    $jars = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'build/libs') -Filter '*.jar' | Where-Object { $_.Name -notmatch '-plain\.jar$|-sources\.jar$' })
    if ($jars.Count -ne 1) { throw 'Build the server bootJar with Java21 before running this script' }
    $listener = New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback, $Port)
    try { $listener.Start() } catch { throw 'Demo port is already occupied; do not reuse an unverified backend' }
    finally { $listener.Stop() }
    $stdout = Join-Path $runDirectory 'backend.stdout.log'
    $stderr = Join-Path $runDirectory 'backend.stderr.log'
    $arguments = '-jar "' + $jars[0].FullName + '" --spring.profiles.active=v2-dev --server.port=' + $Port
    $ownedServer = Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory $PSScriptRoot -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
    $deadline = (Get-Date).AddSeconds(70)
    $started = $false
    while ((Get-Date) -lt $deadline) {
        $ownedServer.Refresh()
        if ($ownedServer.HasExited) { throw 'Backend exited during guarded startup; inspect local backend logs, do not rerun migrations' }
        if ((Test-Path -LiteralPath $stdout) -and (Select-String -LiteralPath $stdout -SimpleMatch 'Started QuizAIServerApplication' -Quiet)) { $started = $true; break }
        Start-Sleep -Milliseconds 250
    }
    Assert-Demo $started 'GUARDED_BACKEND_STARTUP'
    $evidence.BackendStartup = 'PASS'
    Write-Host ('V2 backend started at ' + $baseUrl + '; schema/history/checksums verified by startup guard')
    $env:QUIZAI_API_BASE_URL = $baseUrl
    if ($E2E) {
        # Dedicated demo resources are intentionally retained. No DELETE or SQL cleanup is performed.
        if ($NewDemo -and (Test-Path -LiteralPath $checkpointPath)) {
            Copy-Item -LiteralPath $checkpointPath -Destination ($checkpointPath + '.' + [Guid]::NewGuid().ToString('N') + '.archive')
        }
        if (-not $NewDemo -and (Test-Path -LiteralPath $checkpointPath)) { $checkpoint=Read-DemoCheckpoint }
        else { $checkpoint=[PSCustomObject]@{Suffix=[Guid]::NewGuid().ToString('N');TeacherPassword=(New-DemoPassword);StudentPassword=(New-DemoPassword);Requests=@();Window=[DateTimeOffset]::UtcNow.ToString('o');StartId=[Guid]::NewGuid().ToString();SubmitId=[Guid]::NewGuid().ToString()}; Save-DemoCheckpoint }
        $suffix = $checkpoint.Suffix
        $teacherEmail = 'teacher-' + $suffix + '@demo.quizai.local'
        $studentEmail = 'student-' + $suffix + '@demo.quizai.local'
        $teacherPassword = $checkpoint.TeacherPassword
        $studentPassword = $checkpoint.StudentPassword
        $teacher = Invoke-DemoApi 'POST' '/api/auth/register' @{ email=$teacherEmail; password=$teacherPassword; role='TEACHER' } '' 201
        $student = Invoke-DemoApi 'POST' '/api/auth/register' @{ email=$studentEmail; password=$studentPassword; role='STUDENT' } '' 201
        $teacherLogin = Invoke-DemoApi 'POST' '/api/auth/login' @{ email=$teacherEmail; password=$teacherPassword; deviceSessionId='local-api-demo' }
        $studentLogin = Invoke-DemoApi 'POST' '/api/auth/login' @{ email=$studentEmail; password=$studentPassword; deviceSessionId='local-api-demo' }
        $teacherToken = [string]$teacherLogin.token
        $studentToken = [string]$studentLogin.token
        $health = Invoke-DemoApi 'GET' '/api/health' $null $teacherToken
        Assert-Demo ($health.database -eq 'quizai_v2_dev') 'LIVE_DATABASE_IDENTITY'
        $classroom = Invoke-DemoApi 'POST' '/api/classes' @{ name=('MVP demo ' + $suffix) } $teacherToken 201
        $membership = Invoke-DemoApi 'POST' '/api/classes/join' @{ code=$classroom.joinCode } $studentToken
        Assert-Demo ($membership.id -eq $classroom.id) 'TEACHER_CLASS_STUDENT_JOIN'
        $null = Invoke-DemoApi 'POST' '/api/v2/teacher/banks' @{ name='Denied student action' } $studentToken 403
        $null = Invoke-DemoApi 'GET' '/api/v2/student/assignments' $null $teacherToken 403
        Assert-Demo $true 'ROLE_SEPARATION'
        $bank = Invoke-DemoApi 'POST' '/api/v2/teacher/banks' @{ name=('Demo bank ' + $suffix) } $teacherToken
        $question = Invoke-DemoApi 'POST' '/api/v2/teacher/questions' @{ bankId=$bank.id; stem='What is 2 + 2?'; difficulty='EASY'; options=@(@{text='4';correct=$true},@{text='5';correct=$false}) } $teacherToken
        $approved = Invoke-DemoApi 'POST' ('/api/v2/teacher/questions/' + $question.questionId + '/versions/' + $question.versionId + '/approve') $null $teacherToken
        $quiz = Invoke-DemoApi 'POST' '/api/v2/teacher/quizzes' @{ title='MVP server grading'; questionVersionIds=@($approved.versionId) } $teacherToken
        $now = [DateTimeOffset]::Parse($checkpoint.Window)
        $assignment = Invoke-DemoApi 'POST' '/api/v2/teacher/assignments' @{ classId=$classroom.id; quizId=$quiz.id; title='MVP assigned quiz'; opensAt=$now.AddMinutes(-1).ToString('o'); closesAt=$now.AddMinutes(30).ToString('o'); durationMinutes=10; maxAttempts=2; scoreReleaseAt=$now.AddMinutes(-1).ToString('o'); answerReleaseAt=$null; answerReleaseMode='AFTER_VALID_SUBMISSION'; studentIds=@($student.id) } $teacherToken
        $published = Invoke-DemoApi 'POST' ('/api/v2/teacher/assignments/' + $assignment.id + '/publish') $null $teacherToken
        Assert-Demo ($published.status -eq 'PUBLISHED') 'APPROVED_QUIZ_IMMUTABLE_ASSIGNMENT_PUBLISHED'
        $newVersion = Invoke-DemoApi 'POST' ('/api/v2/teacher/questions/' + $question.questionId + '/versions') @{ bankId=$bank.id; stem='Template edited after publication: 2 + 3?'; difficulty='EASY'; options=@(@{text='5';correct=$true},@{text='4';correct=$false}) } $teacherToken
        $null = Invoke-DemoApi 'PUT' ('/api/v2/teacher/quizzes/' + $quiz.id) @{ title='Edited template'; questionVersionIds=@($newVersion.versionId) } $teacherToken
        $startRequest = @{ startRequestId=$checkpoint.StartId }
        $attempt = Invoke-DemoApi 'POST' ('/api/v2/student/assignments/' + $assignment.id + '/attempts') $startRequest $studentToken
        $sameAttempt = Invoke-DemoApi 'POST' ('/api/v2/student/assignments/' + $assignment.id + '/attempts') $startRequest $studentToken
        Assert-Demo ($attempt.id -eq $sameAttempt.id) 'START_RETRY_IDEMPOTENCY'
        Assert-Demo ($attempt.questions[0].stem -eq 'What is 2 + 2?') 'PUBLISHED_SNAPSHOT_SURVIVES_TEMPLATE_EDIT'
        Assert-Demo ($null -eq $attempt.normalizedScore -and $null -eq $attempt.questions[0].correctOptionKey) 'ACTIVE_ATTEMPT_HIDES_SCORE_AND_KEY'
        $correctKey = [string](@($approved.options | Where-Object { $_.correct })[0].id)
        $questionId = [string]$attempt.questions[0].id
        $answerPath = '/api/v2/student/attempts/' + $attempt.id + '/answers/' + $questionId
        $answer = Invoke-DemoApi 'PUT' $answerPath @{ selectedOptionKey=$correctKey; answerRevision=0 } $studentToken
        $retryAnswer = Invoke-DemoApi 'PUT' $answerPath @{ selectedOptionKey=$correctKey; answerRevision=0 } $studentToken
        Assert-Demo ($answer.answerRevision -eq 0 -and $retryAnswer.selectedOptionKey -eq $correctKey) 'AUTOSAVE_REVISION_ACK_AND_RETRY'
        $null = Invoke-DemoApi 'PUT' $answerPath @{ selectedOptionKey=$null; answerRevision=0 } $studentToken 409
        Assert-Demo $true 'CONFLICTING_REVISION_REJECTED'
        $submitRequest = @{ submitRequestId=$checkpoint.SubmitId }
        $concurrent = @(Invoke-ConcurrentSubmit ('/api/v2/student/attempts/' + $attempt.id + '/submit') $submitRequest $studentToken)
        Assert-Demo ($concurrent.Count -eq 2 -and $concurrent[0].id -eq $concurrent[1].id -and $concurrent[0].normalizedScore -eq $concurrent[1].normalizedScore) 'TWO_CONNECTION_SUBMIT_IDEMPOTENCY'
        $submission = $concurrent[0]
        $retrySubmit = Invoke-DemoApi 'POST' ('/api/v2/student/attempts/' + $attempt.id + '/submit') $submitRequest $studentToken
        Assert-Demo ($submission.status -eq 'SUBMITTED' -and $retrySubmit.id -eq $submission.id -and [decimal]$submission.normalizedScore -eq 1) 'SERVER_GRADING_AND_SUBMIT_IDEMPOTENCY'
        $null = Invoke-DemoApi 'PUT' $answerPath @{ selectedOptionKey=$null; answerRevision=1 } $studentToken 409
        Assert-Demo $true 'FINALIZED_HISTORY_REJECTS_AUTOSAVE'
        $second = Invoke-DemoApi 'POST' ('/api/v2/student/assignments/' + $assignment.id + '/attempts') @{startRequestId=[Guid]::NewGuid().ToString()} $studentToken
        $blank = Invoke-DemoApi 'POST' ('/api/v2/student/attempts/' + $second.id + '/submit') @{submitRequestId=[Guid]::NewGuid().ToString()} $studentToken
        Assert-Demo ($blank.status -eq 'SUBMITTED' -and [decimal]$blank.normalizedScore -eq 0) 'SECOND_VALID_ATTEMPT_ZERO_SCORE'
        $results = @(Invoke-DemoApi 'GET' ('/api/v2/teacher/assignments/' + $assignment.id + '/results') $null $teacherToken)
        Assert-Demo ($results.Count -eq 1 -and @($results[0].attempts).Count -eq 2 -and [decimal]$results[0].bestScore -eq 1) 'TEACHER_SEES_HIGHEST_VALID_SCORE'
        $history = @(Invoke-DemoApi 'GET' ('/api/v2/student/assignments/' + $assignment.id + '/attempts') $null $studentToken)
        Assert-Demo ($history.Count -eq 2 -and @($history | Where-Object { $_.scoreAvailable -and [decimal]$_.normalizedScore -eq 1 }).Count -eq 1) 'STUDENT_RELEASED_RESULTS_HISTORY'
        $summary = @(Invoke-DemoApi 'GET' ('/api/classes/' + $classroom.id + '/study-summary') $null $teacherToken)
        Assert-Demo ($summary.Count -eq 1 -and $summary[0].completedSessions -eq 0 -and $null -eq $summary[0].accuracy) 'TEACHER_PRIVATE_AGGREGATE_NO_PRACTICE'
        $env:QUIZAI_DEMO_TEACHER_EMAIL = $teacherEmail
        $env:QUIZAI_DEMO_TEACHER_PASSWORD = $teacherPassword
        $env:QUIZAI_DEMO_STUDENT_EMAIL = $studentEmail
        $env:QUIZAI_DEMO_STUDENT_PASSWORD = $studentPassword
        $evidence.ApiE2E = 'PASS'
        $evidence.AssignmentId = [string]$assignment.id
        $evidence.AttemptId = [string]$attempt.id
        Write-Host 'API E2E PASS. Demo login values are only in QUIZAI_DEMO_* environment variables in this Terminal; never paste passwords into chat.'
    }
    if ($KeepServer -or -not $E2E) {
        $evidence.ServerPid = $ownedServer.Id
        Write-Host ('Backend remains running, PID=' + $ownedServer.Id + '. Use QUIZAI_API_BASE_URL in this Terminal when launching Client/Admin.')
        $ownedServer = $null
    }
} catch {
    $evidence.Failure = $_.Exception.Message
    if ($E2E) { $evidence.ApiE2E = 'FAIL' }
    throw
} finally {
    # KeepServer is honored on failure too. A subsequent launcher refuses an occupied port.
    if ($null -ne $ownedServer) {
        $ownedServer.Refresh()
        if (-not $ownedServer.HasExited) {
            if ($KeepServer) { $evidence.ServerPid=$ownedServer.Id; Write-Host ('Backend retained after E2E failure, PID=' + $ownedServer.Id) }
            else { Stop-Process -Id $ownedServer.Id }
        }
    }
    $evidence.FinishedAt = (Get-Date).ToString('o')
    $evidence | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $runDirectory 'evidence.json') -Encoding UTF8
    Write-Host ('Evidence: ' + (Join-Path $runDirectory 'evidence.json'))
}
