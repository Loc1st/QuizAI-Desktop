package com.quizai.server.v2.foundation;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Executes only extracted HTTP helpers against localhost fixtures, never PostgreSQL or a real server. */
@EnabledOnOs(OS.WINDOWS)
class V2DemoRunnerTest {
    @TempDir Path temporary;
    private final Path demo = Path.of("Invoke-V2MvpDemo.ps1").toAbsolutePath();

    @Test void encryptedCheckpointReusesFixtureAndHoldsAnUnknownCreationOutcome() throws Exception {
        AtomicInteger creations = new AtomicInteger();
        try (var fixture = new Fixture()) {
            fixture.server.createContext("/api/v2/teacher/banks", exchange -> {
                creations.incrementAndGet(); exchange.getRequestBody().readAllBytes();
                reply(exchange, 200, "{\"id\":\"same-bank\"}");
            });
            var result = run(extractHelpers() + """
                    $script:checkpointPath = Join-Path $env:TEMP ([Guid]::NewGuid().ToString() + '.dpapi')
                    try {
                        $script:checkpoint = [PSCustomObject]@{ Requests=@(); Password='private-fixture-value' }
                        $one = Invoke-DemoApi 'POST' '/api/v2/teacher/banks' @{name='one'} 'offline-session-token'
                        if ([IO.File]::ReadAllText($script:checkpointPath).Contains('private-fixture-value')) { throw 'Plaintext checkpoint' }
                        $script:checkpoint=Read-DemoCheckpoint
                        $two = Invoke-DemoApi 'POST' '/api/v2/teacher/banks' @{name='one'} 'offline-session-token'
                        if ($one.id -ne $two.id) { throw 'Fixture identity changed' }
                        $script:checkpoint.Requests[0].State='PENDING'
                        $held=$false
                        try { $null=Invoke-DemoApi 'POST' '/api/v2/teacher/banks' @{name='one'} 'offline-session-token' }
                        catch { if ($_.Exception.Message -like '*HOLD*') { $held=$true } else { throw } }
                        if (-not $held) { throw 'Unknown creation was replayed' }
                    } finally { Remove-Item -LiteralPath $script:checkpointPath -ErrorAction SilentlyContinue }
                    """, Map.of("DEMO_BASE_URL", fixture.url()));
            assertEquals(0, result.exitCode(), result.output());
            assertEquals(1, creations.get());
        }
    }

    @Test void expected409ReturnsNormallyAndUnexpectedErrorsOmitBodiesAndTokens() throws Exception {
        List<String> methods = new CopyOnWriteArrayList<>();
        try (var fixture = new Fixture()) {
            fixture.server.createContext("/conflict", exchange -> {
                methods.add(exchange.getRequestMethod());
                exchange.getRequestBody().readAllBytes();
                reply(exchange, 409, "{\"message\":\"private-server-detail\"}");
            });
            fixture.server.createContext("/unexpected", exchange -> reply(exchange, 500, "{\"message\":\"private-server-detail\"}"));
            var result = run(extractHelpers() + """
                    $result = Invoke-DemoApi 'PUT' '/conflict' @{ answerRevision=0 } 'offline-session-token' 409
                    if ($null -ne $result) { throw 'Expected error status must return no response payload' }
                    $caught = $false
                    try { $null = Invoke-DemoApi 'GET' '/unexpected' $null 'offline-session-token' 200 }
                    catch {
                        $caught = $true
                        if ($_.Exception.Message -ne 'HTTP check failed: GET /unexpected; status=500; expected=200') { throw 'Unexpected sanitized error format' }
                    }
                    if (-not $caught) { throw 'Unexpected status was not rejected' }
                    Write-Output 'HTTP_STATUS_HANDLING_PASS'
                    """, Map.of("DEMO_BASE_URL", fixture.url()));
            assertEquals(0, result.exitCode(), result.output());
            assertTrue(result.output().contains("HTTP_STATUS_HANDLING_PASS"), result.output());
            assertFalse(result.output().contains("private-server-detail"));
            assertFalse(result.output().contains("offline-session-token"));
            assertEquals(List.of("PUT"), methods);
        }
    }

    @Test void concurrentSubmitUsesTwoRequestsWithTheSameBodyAndPreservesBothResponses() throws Exception {
        List<String> bodies = new CopyOnWriteArrayList<>();
        List<String> authorization = new CopyOnWriteArrayList<>();
        CountDownLatch arrivals = new CountDownLatch(2);
        AtomicInteger overlappingRequests = new AtomicInteger();
        try (var fixture = new Fixture()) {
            fixture.server.createContext("/submit", exchange -> {
                bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                authorization.add(exchange.getRequestHeaders().getFirst("Authorization"));
                arrivals.countDown();
                try {
                    if (arrivals.await(5, TimeUnit.SECONDS)) overlappingRequests.incrementAndGet();
                } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                reply(exchange, 200, "{\"id\":\"00000000-0000-0000-0000-000000000001\",\"normalizedScore\":1.000000}");
            });
            var result = run(extractHelpers() + """
                    $values = @(Invoke-ConcurrentSubmit '/submit' @{ submitRequestId='00000000-0000-0000-0000-000000000002' } 'offline-session-token')
                    if ($values.Count -ne 2) { throw 'Concurrent submit lost a response' }
                    if ($values[0].id -ne $values[1].id -or [decimal]$values[0].normalizedScore -ne 1 -or [decimal]$values[1].normalizedScore -ne 1) { throw 'Concurrent submit changed response identity or score' }
                    Write-Output 'CONCURRENT_SUBMIT_PASS'
                    """, Map.of("DEMO_BASE_URL", fixture.url()));
            assertEquals(0, result.exitCode(), result.output());
            assertTrue(result.output().contains("CONCURRENT_SUBMIT_PASS"), result.output());
            assertEquals(2, bodies.size());
            assertEquals(bodies.getFirst(), bodies.getLast());
            assertTrue(bodies.getFirst().contains("00000000-0000-0000-0000-000000000002"));
            assertEquals(List.of("Bearer offline-session-token", "Bearer offline-session-token"), authorization);
            assertEquals(2, overlappingRequests.get(), "Both independent HTTP clients must reach the fixture before either response");
        }
    }

    @Test void legacyTargetStopsBeforeCredentialPromptsOrServerLaunch() throws Exception {
        Path copiedScript = temporary.resolve("isolated-repository/quizai-server/Invoke-V2MvpDemo.ps1");
        Files.createDirectories(copiedScript.getParent());
        Files.copy(demo, copiedScript);
        var result = run("""
                $ErrorActionPreference = 'Stop'
                $global:prompted = $false
                $global:launched = $false
                function Read-Host { $global:prompted = $true; throw 'Unexpected credential prompt' }
                function Start-Process { $global:launched = $true; throw 'Unexpected process launch' }
                $rejected = $false
                try { & $env:WRONG_TARGET_SCRIPT -E2E }
                catch {
                    if ($_.Exception.Message -ne 'Only the dedicated local V2 DEV identity is accepted by this demo') { throw }
                    $rejected = $true
                }
                if (-not $rejected -or $global:prompted -or $global:launched) { throw 'Legacy target was not rejected before runtime actions' }
                Write-Output 'LEGACY_TARGET_REJECTED_BEFORE_RUNTIME'
                """, Map.of("WRONG_TARGET_SCRIPT", copiedScript.toString(),
                        "QUIZAI_V2_DEV_DB_URL", "jdbc:postgresql://127.0.0.1:5432/quizai_dev",
                        "QUIZAI_V2_DEV_DB_USERNAME", "quizai_dev"));
        assertEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("LEGACY_TARGET_REJECTED_BEFORE_RUNTIME"), result.output());
        try (var paths = Files.walk(temporary.resolve("isolated-repository"))) {
            Path evidence = paths.filter(p -> p.getFileName().toString().equals("evidence.json")).findFirst().orElseThrow();
            String json = Files.readString(evidence, StandardCharsets.UTF_8);
            assertTrue(json.contains("PENDING"));
            assertTrue(json.contains("FAIL"));
            assertFalse(json.contains("ServerPid"));
        }
    }

    private String extractHelpers() {
        return """
                $ErrorActionPreference = 'Stop'
                Set-StrictMode -Version Latest
                $tokens = $null
                $errors = $null
                $ast = [System.Management.Automation.Language.Parser]::ParseFile($env:DEMO_SCRIPT,[ref]$tokens,[ref]$errors)
                if (@($errors).Count -ne 0) { throw 'Demo script does not parse under Windows PowerShell' }
                foreach ($name in @('Invoke-DemoApi','Invoke-ConcurrentSubmit','Save-DemoCheckpoint','Read-DemoCheckpoint')) {
                    $functions = @($ast.FindAll({ param($node) $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq $name },$true))
                    if ($functions.Count -ne 1) { throw ('Missing helper: ' + $name) }
                    Invoke-Expression $functions[0].Extent.Text
                }
                $script:baseUrl = $env:DEMO_BASE_URL
                $script:checkpoint = $null
                """;
    }

    private Run run(String source, Map<String,String> environment) throws Exception {
        Path harness = Files.createTempFile(temporary, "demo-http-test-", ".ps1");
        Files.writeString(harness, source, StandardCharsets.UTF_8);
        Path output = Files.createTempFile(temporary, "demo-http-output-", ".txt");
        ProcessBuilder command = new ProcessBuilder("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass", "-File", harness.toString());
        command.redirectErrorStream(true).redirectOutput(output.toFile());
        command.environment().put("DEMO_SCRIPT", demo.toString());
        command.environment().putAll(environment);
        command.environment().remove("QUIZAI_V2_DEV_DB_PASSWORD");
        command.environment().remove("QUIZAI_CLASS_JOIN_CODE_KEY");
        Process process = command.start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("Offline PowerShell fixture timed out");
        }
        return new Run(process.exitValue(), Files.readString(output, StandardCharsets.UTF_8));
    }

    private static void reply(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        try (var output = exchange.getResponseBody()) { output.write(body); }
        exchange.close();
    }

    private record Run(int exitCode, String output) {}

    private static final class Fixture implements AutoCloseable {
        final HttpServer server;
        final java.util.concurrent.ExecutorService workers = Executors.newCachedThreadPool();
        Fixture() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(workers);
            server.start();
        }
        String url() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
        @Override public void close() { server.stop(0); workers.shutdownNow(); }
    }
}
