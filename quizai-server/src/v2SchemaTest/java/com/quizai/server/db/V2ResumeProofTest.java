package com.quizai.server.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

/** Offline recovery provenance tests; never opens JDBC or depends on operator evidence. */
class V2ResumeProofTest {
    @TempDir Path temp;

    @Test void exactFailedInstallEvidenceAndUnchangedFiveSqlFilesPermitRecoveryInspection() throws Exception {
        var f = fixture();
        assertDoesNotThrow(() -> V2ResumeProof.verify(f.run, f.original, f.snapshots, f.release));
    }
    @Test void partialInstallOrDevMigrationCannotBecomeRecoveryProof() throws Exception {
        var f = fixture();
        Files.writeString(f.run.resolve("deployment.log"), "Successfully applied 4 migrations; SQLSTATE=42725; BUILD FAILED");
        assertThrows(IllegalStateException.class, () -> V2ResumeProof.verify(f.run,f.original,f.snapshots,f.release));
        Files.writeString(f.run.resolve("deployment.log"), deployment() + "\njdbc:postgresql://127.0.0.1:5432/quizai_v2_dev");
        assertThrows(IllegalStateException.class, () -> V2ResumeProof.verify(f.run,f.original,f.snapshots,f.release));
    }
    @Test void preExistingBusinessDataOrHistoryCannotBeTreatedAsAFreshFailedInstall() throws Exception {
        var f = fixture();
        var path = f.run.resolve("runtime-state.before.json");
        Files.writeString(path, Files.readString(path).replace("\"history_exists\":false", "\"history_exists\":true"));
        assertThrows(IllegalStateException.class, () -> V2ResumeProof.verify(f.run,f.original,f.snapshots,f.release));
    }
    @Test void changedOriginalManifestOrSqlSnapshotFailsClosed() throws Exception {
        var f = fixture();
        Files.writeString(f.original, "changed");
        assertThrows(IllegalStateException.class, () -> V2ResumeProof.verify(f.run,f.original,f.snapshots,f.release));
    }
    @Test void changedInstalledSqlBytesFailClosedEvenWithMatchingHistoryNames() throws Exception {
        var f = fixture();
        var sql = f.snapshots.resolve(f.sha).resolve("db/migration-v2").resolve(f.release.history().getFirst().script());
        Files.writeString(sql, Files.readString(sql) + "\n-- drift");
        assertThrows(IllegalStateException.class, () -> V2ResumeProof.verify(f.run,f.original,f.snapshots,f.release));
    }
    @Test void missingRequiredRunEvidenceIsNotSilentlyAdopted() throws Exception {
        var f = fixture();
        Files.delete(f.run.resolve("inspect.log"));
        assertThrows(IllegalStateException.class, () -> V2ResumeProof.verify(f.run,f.original,f.snapshots,f.release));
    }
    @Test void alteredAppliedChecksumIsRejectedEvenIfArchiveHashIsUpdated() throws Exception {
        var f = fixture();
        Properties changed = f.release.properties();
        changed.setProperty("migration.1.checksum", "123");
        try (var out = Files.newOutputStream(f.original)) { changed.store(out,"changed history fixture"); }
        String sha = V2ReleaseManifest.sha(Files.readAllBytes(f.original));
        Path before = f.run.resolve("runtime-state.before.json");
        Files.writeString(before,Files.readString(before).replace(f.sha,sha));
        var failure = assertThrows(IllegalStateException.class,
                () -> V2ResumeProof.verify(f.run,f.original,f.snapshots,f.release));
        assertTrue(failure.getMessage().contains("Applied migration"));
    }
    @Test void windowsPowerShellUtf16LogsRetainRecoveryProvenance() throws Exception {
        var f = fixture();
        for (String name : new String[]{"verify-release.log","inspect.log","deployment.log"}) {
            Path file=f.run.resolve(name);
            byte[] text=Files.readString(file).getBytes(java.nio.charset.StandardCharsets.UTF_16LE);
            byte[] bom=new byte[text.length+2];bom[0]=(byte)0xff;bom[1]=(byte)0xfe;
            System.arraycopy(text,0,bom,2,text.length);Files.write(file,bom);
        }
        assertDoesNotThrow(() -> V2ResumeProof.verify(f.run,f.original,f.snapshots,f.release));
    }

    private Fixture fixture() throws Exception {
        var release = V2ReleaseManifest.snapshot();
        Path run = Files.createDirectories(temp.resolve("run"));
        Path original = temp.resolve("original.properties");
        Properties properties = release.properties();
        try (var out = Files.newOutputStream(original)) { properties.store(out, "offline fixture"); }
        String sha = V2ReleaseManifest.sha(Files.readAllBytes(original));
        Path snapshots = Files.createDirectories(temp.resolve("snapshots"));
        Path sql = Files.createDirectories(snapshots.resolve(sha).resolve("db/migration-v2"));
        for (var row : release.history()) { Files.write(sql.resolve(row.script()),release.bytes(V2ReleaseManifest.MIGRATIONS.resolve(row.script()))); }
        String entries = "";
        for (String name : new String[]{"quizai_v2_test","quizai_v2_dev"}) {
            if (!entries.isEmpty()) entries += ",";
            entries += """
                {"Database":"%s","History":[],"IdentityCatalog":{"database":"%s",
                "current_user":"%s","session_user":"%s","owner":"%s","role_safe":true,
                "history_exists":false,"business_tables":[]}}
                """.formatted(name,name,name,name,name);
        }
        Files.writeString(run.resolve("runtime-state.before.json"),
                "{\"FrozenManifestSha256\":\""+sha+"\",\"Databases\":["+entries+"]}");
        Files.writeString(run.resolve("verify-release.log"), "OFFLINE verifyRelease: frozen V2 source/checksum manifest matches\nBUILD SUCCESSFUL");
        Files.writeString(run.resolve("inspect.log"), "Read-only TEST inspection passed.\nBUILD SUCCESSFUL");
        Files.writeString(run.resolve("deployment.log"), deployment());
        return new Fixture(run,original,snapshots,release,sha);
    }
    private String deployment() {
        return "Database: jdbc:postgresql://127.0.0.1:5432/quizai_v2_test (PostgreSQL 18.6)\n"
                + "Successfully applied 5 migrations to schema \"public\", now at version v5\n"
                + "STOP: V2 runner failed (PSQLException; SQLSTATE=42725).\nBUILD FAILED\n";
    }
    private record Fixture(Path run, Path original, Path snapshots, V2ReleaseManifest release, String sha) { }
}
