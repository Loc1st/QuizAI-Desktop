package com.quizai.server.v2.foundation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** PowerShell runner boundary tests only. Never invokes Gradle or PostgreSQL. */
@EnabledOnOs(OS.WINDOWS)
class V2IntegrationRunnerTest {
    @TempDir Path root;
    @Test void missingPasswordIsPromptedLocallyInheritedByChildAndRestoredAfterwards() throws Exception {
        prepare();
        Files.writeString(root.resolve("gradlew.bat"),"@echo off\r\nif not defined QUIZAI_V2_TEST_DB_PASSWORD exit /b 7\r\nif defined QUIZAI_V2_DEV_DB_PASSWORD exit /b 8\r\necho expected-offline-warning 1>&2\r\necho RUNNER_ENVIRONMENT_PASS\r\nexit /b 0\r\n");
        String result=run("""
                $global:prompted=$false
                function Read-Host { param($Prompt,[switch]$AsSecureString) $global:prompted=$true; ConvertTo-SecureString 'offline-private-value' -AsPlainText -Force }
                & $env:RUNNER_SCRIPT
                if (-not $global:prompted) { throw 'Missing password was not prompted' }
                foreach($name in @('QUIZAI_V2_TEST_DB_URL','QUIZAI_V2_TEST_DB_USERNAME','QUIZAI_V2_TEST_DB_PASSWORD')) {
                    if ($null -ne [Environment]::GetEnvironmentVariable($name,'Process')) { throw 'Environment was not restored' }
                }
                """);
        assertTrue(result.contains("RUNNER_ENVIRONMENT_PASS"),result);
        assertFalse(result.contains("offline-private-value"));
    }
    @Test void wrongLegacyTargetIsRejectedBeforePromptOrChildAndPreserved() throws Exception {
        prepare();
        String result=run("""
                $env:QUIZAI_V2_TEST_DB_URL='jdbc:postgresql://127.0.0.1:5432/quizai_test'
                function Read-Host { throw 'UNEXPECTED_PROMPT' }
                $rejected=$false
                try { & $env:RUNNER_SCRIPT } catch { if($_.Exception.Message -like '*target rejected*') { $rejected=$true } else { throw } }
                if (-not $rejected) { throw 'Legacy target accepted' }
                if ($env:QUIZAI_V2_TEST_DB_URL -ne 'jdbc:postgresql://127.0.0.1:5432/quizai_test') { throw 'Existing config overwritten' }
                Write-Output 'LEGACY_REJECTED'
                """);
        assertTrue(result.contains("LEGACY_REJECTED"),result);
    }
    @Test void nonzeroGradleExitStillFailsAndRestoresCredentials() throws Exception {
        prepare();
        Files.writeString(root.resolve("gradlew.bat"),"@echo off\r\nexit /b 3\r\n");
        String result=run("""
                function Read-Host { ConvertTo-SecureString 'offline-private-value' -AsPlainText -Force }
                $failed=$false
                try { & $env:RUNNER_SCRIPT } catch { if($_.Exception.Message -like '*integration FAILED*') { $failed=$true } else { throw } }
                if(-not $failed) { throw 'Failed Gradle exit was ignored' }
                if($null -ne [Environment]::GetEnvironmentVariable('QUIZAI_V2_TEST_DB_PASSWORD','Process')) { throw 'Password not restored after failure' }
                Write-Output 'NONZERO_EXIT_REJECTED'
                """);
        assertTrue(result.contains("NONZERO_EXIT_REJECTED"),result);
    }
    private void prepare() throws Exception {
        Files.createDirectories(root.resolve("quizai-server"));
        Files.copy(Path.of("Invoke-V2IntegrationTests.ps1"),root.resolve("quizai-server/Invoke-V2IntegrationTests.ps1"));
    }
    private String run(String commands) throws Exception {
        Path harness=root.resolve("harness.ps1"),output=root.resolve("output.txt");
        Files.writeString(harness,"$ErrorActionPreference='Stop'\n"+commands,StandardCharsets.UTF_8);
        var builder=new ProcessBuilder("powershell.exe","-NoProfile","-NonInteractive","-ExecutionPolicy","Bypass","-File",harness.toString()).redirectErrorStream(true).redirectOutput(output.toFile());
        builder.environment().put("RUNNER_SCRIPT",root.resolve("quizai-server/Invoke-V2IntegrationTests.ps1").toString());
        for(String kind:new String[]{"TEST","DEV"}) for(String suffix:new String[]{"URL","USERNAME","PASSWORD"}) builder.environment().remove("QUIZAI_V2_"+kind+"_DB_"+suffix);
        Process process=builder.start();
        if(!process.waitFor(20,TimeUnit.SECONDS)) { process.destroyForcibly(); fail("Offline PowerShell runner timed out"); }
        String text=Files.readString(output,StandardCharsets.UTF_8);
        assertEquals(0,process.exitValue(),text);
        return text;
    }
}
