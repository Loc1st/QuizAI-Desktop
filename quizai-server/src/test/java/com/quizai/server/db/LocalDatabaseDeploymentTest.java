package com.quizai.server.db;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Only exercises validation; never invokes the deployment entry point or JDBC. */
class LocalDatabaseDeploymentTest {
    @Test
    void freezesReviewedMigrationAndAcceptanceBytesAndDetectsSnapshotDrift() throws Exception {
        var manifest = LocalDatabaseDeployment.manifest();
        assertEquals(37, manifest.tables().size());
        assertEquals(10, manifest.snapshotHashes().size());
        LocalDatabaseDeployment.verifyNoDrift(manifest);
        var copy = manifest.runDirectory().resolve("migration/V1__identity_classes_catalog.sql");
        byte[] originalCopy = java.nio.file.Files.readAllBytes(copy);
        try {
            java.nio.file.Files.writeString(copy, "-- changed snapshot\n");
            assertThrows(IllegalArgumentException.class, () -> LocalDatabaseDeployment.verifyNoDrift(manifest));
        } finally {
            java.nio.file.Files.write(copy, originalCopy);
        }
        LocalDatabaseDeployment.verifyNoDrift(manifest);
    }

    @Test
    void acceptsOnlyTheDedicatedLocalTestAndDevTargets() throws Exception {
        for (String name : new String[]{"TEST", "DEV"}) {
            var target = LocalDatabaseDeployment.target(environment(name), name);
            assertEquals("quizai_" + name.toLowerCase(java.util.Locale.ROOT), target.database());
            assertFalse(target.toString().contains("synthetic-secret"));
        }
    }

    @Test
    void rejectsMissingBlankOrUnresolvedEnvironmentVariablesWithoutPrintingValues() {
        for (String field : new String[]{"URL", "USERNAME", "PASSWORD"}) {
            for (String value : new String[]{"", " ", "${UNRESOLVED}"}) {
                var env = environment("TEST");
                env.put("QUIZAI_TEST_DB_" + field, value);
                assertThrows(IllegalArgumentException.class, () -> LocalDatabaseDeployment.target(env, "TEST"));
            }
            var env = environment("TEST");
            env.remove("QUIZAI_TEST_DB_" + field);
            assertThrows(IllegalArgumentException.class, () -> LocalDatabaseDeployment.target(env, "TEST"));
        }
    }

    @Test
    void refusesAlternateDatabasesRemoteHostsPortsAndUrlCredentials() {
        for (String url : new String[]{
                "jdbc:postgresql://127.0.0.1:5432/postgres",
                "jdbc:postgresql://127.0.0.1:5432",
                "jdbc:postgresql://127.0.0.1:5432/quizai_practice",
                "jdbc:postgresql://127.0.0.1:5432/quizai_dev",
                "jdbc:postgresql://192.0.2.1:5432/quizai_test",
                "jdbc:postgresql://127.0.0.1:5433/quizai_test",
                "jdbc:postgresql://127.0.0.1/quizai_test",
                "jdbc:postgresql:quizai_test",
                "jdbc:postgresql://127.0.0.1:5432/quizai_test?password=synthetic-secret",
                "jdbc:postgresql://name:synthetic-secret@127.0.0.1:5432/quizai_test",
                "jdbc:postgresql://127.0.0.1:5432/quizai_test#fragment"}) {
            var env = environment("TEST");
            env.put("QUIZAI_TEST_DB_URL", url);
            var failure = assertThrows(IllegalArgumentException.class, () -> LocalDatabaseDeployment.target(env, "TEST"));
            assertFalse(failure.getMessage().contains("synthetic-secret"));
        }
    }

    @Test
    void refusesSuperuserOrCrossEnvironmentLoginAndAnyOtherEnvironment() {
        for (String user : new String[]{"postgres", "quizai_dev", "other"}) {
            var env = environment("TEST");
            env.put("QUIZAI_TEST_DB_USERNAME", user);
            assertThrows(IllegalArgumentException.class, () -> LocalDatabaseDeployment.target(env, "TEST"));
        }
        assertThrows(IllegalArgumentException.class, () -> LocalDatabaseDeployment.target(Map.of(), "PRODUCTION"));
    }

    private static HashMap<String, String> environment(String name) {
        String database = "quizai_" + name.toLowerCase(java.util.Locale.ROOT);
        return new HashMap<>(Map.of("QUIZAI_" + name + "_DB_URL", "jdbc:postgresql://127.0.0.1:5432/" + database,
                "QUIZAI_" + name + "_DB_USERNAME", database,
                "QUIZAI_" + name + "_DB_PASSWORD", "synthetic-secret"));
    }
}
