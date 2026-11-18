package com.quizai.server.db;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Offline guard and release tests; no JDBC connection or Spring context. */
class V2DatabaseRunnerTest {
    @Test void catalogConcatenationCastsEveryPostgresInternalCharOperand() throws Exception {
        String runner = Files.readString(Path.of("src/v2Schema/java/com/quizai/server/db/V2DatabaseRunner.java"));
        for (String field : Set.of("c.relpersistence", "t.tgenabled", "a.attidentity", "a.attgenerated")) {
            var unsafe = java.util.regex.Pattern.compile("\\|\\|\\s*" + java.util.regex.Pattern.quote(field)
                    + "(?![a-zA-Z0-9_]|::text)");
            assertFalse(unsafe.matcher(runner).find(), "Internal PostgreSQL char must be cast before ||: " + field);
        }
    }

    @Test void defaultPreviewNeedsNoCredentialsOrAuthorization() throws Exception {
        assertEquals("preview", V2DatabaseRunner.action(new String[0], Map.of()));
        assertEquals("verifyRelease", V2DatabaseRunner.action(new String[]{"verifyRelease"}, Map.of()));
        assertEquals("verifyResumeProof", V2DatabaseRunner.action(new String[]{"verifyResumeProof"}, Map.of()));
        assertDoesNotThrow(() -> V2DatabaseRunner.run(new String[0], Map.of()));
    }

    @Test void liveActionsRequireAnExactProcessAuthorizationBeforeAnyConnection() {
        for (String action : Set.of("inspect", "deploy", "resume")) {
            assertThrows(IllegalStateException.class,
                    () -> V2DatabaseRunner.action(new String[]{action}, Map.of()));
            assertThrows(IllegalStateException.class,
                    () -> V2DatabaseRunner.action(new String[]{action}, Map.of("QUIZAI_V2_DATABASE_AUTHORIZATION", "true")));
            assertEquals(action, V2DatabaseRunner.action(new String[]{action},
                    Map.of("QUIZAI_V2_DATABASE_AUTHORIZATION", "AUTHORIZE_V2_" + action.toUpperCase(java.util.Locale.ROOT))));
        }
        for (String action : Set.of("clean", "repair", "migrate", "baseline", "targets", "sources")) {
            assertThrows(IllegalArgumentException.class,
                    () -> V2DatabaseRunner.action(new String[]{action}, Map.of()));
        }
    }

    @Test void badDevTargetStopsAuthorizedDeployBeforeAnyTestConnection() {
        var env = new java.util.HashMap<>(Map.of(
                "QUIZAI_V2_DATABASE_AUTHORIZATION", "AUTHORIZE_V2_DEPLOY",
                "QUIZAI_V2_TEST_DB_URL", "jdbc:postgresql://127.0.0.1:5432/quizai_v2_test",
                "QUIZAI_V2_TEST_DB_USERNAME", "quizai_v2_test",
                "QUIZAI_V2_TEST_DB_PASSWORD", "synthetic-secret",
                "QUIZAI_V2_DEV_DB_URL", "jdbc:postgresql://127.0.0.1:5432/quizai_dev",
                "QUIZAI_V2_DEV_DB_USERNAME", "quizai_v2_dev",
                "QUIZAI_V2_DEV_DB_PASSWORD", "synthetic-secret"));
        assertThrows(IllegalArgumentException.class, () -> V2DatabaseRunner.run(new String[]{"deploy"}, env));
        env.remove("QUIZAI_V2_DATABASE_AUTHORIZATION");
        assertThrows(IllegalStateException.class, () -> V2DatabaseRunner.run(new String[]{"deploy"}, env));
    }

    @Test void expectedObjectsIncludeTableScopedTriggerAndConstraintNames() {
        var names = V2ReleaseManifest.expectedObjects("""
                CREATE TABLE alpha (
                    id uuid PRIMARY KEY,
                    role text CHECK (role IN ('A','B')),
                    token text UNIQUE,
                    parent_id uuid REFERENCES beta(id),
                    CONSTRAINT alpha_guard CHECK (id IS NOT NULL)
                );
                CREATE INDEX alpha_parent_idx ON alpha(parent_id);
                CREATE FUNCTION guard_v2() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RETURN NEW; END; $$;
                CREATE TRIGGER shared_v2_guard BEFORE UPDATE ON alpha FOR EACH ROW EXECUTE FUNCTION guard_v2();
                CREATE TRIGGER shared_v2_guard BEFORE UPDATE ON beta FOR EACH ROW EXECUTE FUNCTION guard_v2();
                ALTER TABLE alpha ADD CONSTRAINT extra_guard CHECK (token IS NOT NULL);
                """);
        assertEquals("r", names.get("table:alpha"));
        assertEquals("p", names.get("constraint:alpha:alpha_pkey"));
        assertEquals("c", names.get("constraint:alpha:alpha_role_check"));
        assertEquals("f", names.get("constraint:alpha:alpha_parent_id_fkey"));
        assertEquals("u", names.get("constraint:alpha:alpha_token_key"));
        assertEquals("c", names.get("constraint:alpha:alpha_guard"));
        assertEquals("c", names.get("constraint:alpha:extra_guard"));
        assertEquals("i", names.get("index:alpha_parent_idx"));
        assertEquals("f", names.get("function:guard_v2"));
        assertEquals("t", names.get("trigger:alpha:shared_v2_guard"));
        assertEquals("t", names.get("trigger:beta:shared_v2_guard"));
    }

    @Test void flywayChecksumIgnoresLineEndingsAndBomButShaDetectsByteDrift() {
        byte[] unix = "one\ntwo\n".getBytes(StandardCharsets.UTF_8);
        byte[] windows = "\ufeffone\r\ntwo\r\n".getBytes(StandardCharsets.UTF_8);
        assertEquals(-1945009851, V2ReleaseManifest.flywayChecksum(unix));
        assertEquals(V2ReleaseManifest.flywayChecksum(unix), V2ReleaseManifest.flywayChecksum(windows));
        assertNotEquals(V2ReleaseManifest.sha(unix), V2ReleaseManifest.sha(windows));
    }

    @Test void frozenManifestRejectsMissingExtraOrChangedSourceAndMalformedChecksum() throws Exception {
        var manifest = V2ReleaseManifest.snapshot();
        assertDoesNotThrow(() -> V2ReleaseManifest.verifyProperties(manifest.properties(), manifest));
        var properties = manifest.properties();
        String key = "migration.1.sha256";
        properties.setProperty(key, "0".repeat(64));
        assertThrows(IllegalStateException.class, () -> V2ReleaseManifest.verifyProperties(properties, manifest));
        var malformed = manifest.properties();
        malformed.setProperty("migration.1.checksum", "broken");
        assertThrows(IllegalStateException.class, () -> V2ReleaseManifest.verifyProperties(malformed, manifest));
        var extra = manifest.properties();
        extra.setProperty("migration.6.script", "unexpected.sql");
        assertThrows(IllegalStateException.class, () -> V2ReleaseManifest.verifyProperties(extra, manifest));
    }

    @Test void frozenReleaseIncludesTheOperatorLauncherBytes() throws Exception {
        var manifest = V2ReleaseManifest.snapshot();
        String key = "source.Invoke-V2DatabaseDeployment.ps1";
        assertEquals(V2ReleaseManifest.sha(Files.readAllBytes(Path.of("Invoke-V2DatabaseDeployment.ps1"))),
                manifest.properties().getProperty(key));
        var changed = manifest.properties();
        changed.setProperty(key, "0".repeat(64));
        assertThrows(IllegalStateException.class, () -> V2ReleaseManifest.verifyProperties(changed, manifest));
    }

    @Test void existingHistoryRequiresPriorManifestAndCatalogEvidence() throws Exception {
        var release = V2ReleaseManifest.snapshot();
        Path evidence = Files.createTempFile("v2-evidence-", ".properties");
        try {
            var target = new V2SchemaPreparation.Target("TEST", "quizai_v2_test");
            assertThrows(IllegalStateException.class,
                    () -> V2DatabaseRunner.verifyEvidence(evidence, target, release, "a".repeat(64)));
            var known = new java.util.Properties();
            known.setProperty("database", "quizai_v2_test");
            known.setProperty("release_sha256", release.digest());
            known.setProperty("catalog_sha256", "a".repeat(64));
            try (var out = Files.newOutputStream(evidence)) { known.store(out, "synthetic offline evidence"); }
            assertDoesNotThrow(() -> V2DatabaseRunner.verifyEvidence(evidence, target, release, "a".repeat(64)));
            assertThrows(IllegalStateException.class,
                    () -> V2DatabaseRunner.verifyEvidence(evidence, target, release, "b".repeat(64)));
        } finally { Files.deleteIfExists(evidence); }
    }
}
