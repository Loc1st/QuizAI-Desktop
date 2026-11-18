package com.quizai.server.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Offline source contracts only: never invokes Flyway, JDBC or a Spring context. */
class P2ASourceContractTest {
    private static final String MIGRATION = "db/migration/V1__identity_classes_catalog.sql";
    private static final String MANUAL_TEST = "db/P2A_ROLLBACK_CONTRACT_TEST.sql";
    private static final String ERD_SHA =
            "5ff37a672d9a76ce4092301f53064a850304fed0ace74d8f71fa9db766aa31ab";
    private static final Map<String, Set<String>> COLUMNS = Map.of(
            "users", Set.of("id", "email", "password_hash", "role", "status", "created_at", "updated_at"),
            "auth_sessions", Set.of("id", "user_id", "session_token_hash", "device_session_id",
                    "created_at", "expires_at", "revoked_at", "last_seen_at"),
            "classes", Set.of("id", "teacher_id", "name", "join_code_digest", "join_enabled", "status", "created_at"),
            "class_members", Set.of("id", "class_id", "student_id", "joined_at", "left_at", "created_at"),
            "subjects", Set.of("id", "code", "name", "active", "catalog_version"),
            "topics", Set.of("id", "subject_id", "code", "name", "active"));

    @Test
    void sourceMatchesApprovedChecksumAndExactlySixDictionaryTables() throws Exception {
        Path working = Path.of("").toAbsolutePath();
        Path root = Files.isRegularFile(working.resolve("settings.gradle")) ? working : working.getParent();
        byte[] erd = ApprovedLegacyErd.signedBytes(Files.readAllBytes(root.resolve(ApprovedLegacyErd.ARCHIVED_PATH)));
        assertEquals(ERD_SHA, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(erd)));

        Map<String, String> tables = tables(resource(MIGRATION));
        assertEquals(COLUMNS.keySet(), tables.keySet());
        for (var table : tables.entrySet()) {
            Set<String> columns = Pattern.compile("(?m)^\\s+([a-z_]+)\\s+[A-Z]+\\b")
                    .matcher(table.getValue()).results().map(match -> match.group(1)).collect(Collectors.toSet());
            assertEquals(COLUMNS.get(table.getKey()), columns, table.getKey());
            contains(table.getValue(), "id\\s+UUID PRIMARY KEY");
        }
        assertEquals(38, COLUMNS.values().stream().mapToInt(Set::size).sum());
    }

    @Test
    void emailJoinCodesAndCatalogHaveTheirApprovedUniquenessScopes() throws IOException {
        String sql = resource(MIGRATION);
        contains(sql, "email = lower\\(btrim\\(email\\)\\) AND length\\(email\\) > 0");
        contains(sql, "CONSTRAINT uq_users_email UNIQUE \\(email\\)");
        contains(sql, "role IN \\('STUDENT', 'TEACHER'\\)");
        contains(sql, "CREATE UNIQUE INDEX uq_classes_enabled_join_code_digest\\s+"
                + "ON classes \\(join_code_digest\\) WHERE join_enabled IS TRUE;");
        assertFalse(Pattern.compile("(?i)join_code_digest\\s+TEXT\\s+UNIQUE").matcher(sql).find());
        contains(sql, "CONSTRAINT ck_classes_enabled_code CHECK \\(NOT join_enabled OR join_code_digest IS NOT NULL\\)");
        contains(sql, "CONSTRAINT uq_subjects_code UNIQUE \\(code\\)");
        contains(sql, "CONSTRAINT uq_topics_subject_code UNIQUE \\(subject_id, code\\)");
    }

    @Test
    void membershipSourceClosesEpisodesWithoutReopeningOrReassigningHistory() throws IOException {
        String sql = resource(MIGRATION);
        contains(sql, "CREATE UNIQUE INDEX uq_class_members_active\\s+"
                + "ON class_members \\(class_id, student_id\\) WHERE left_at IS NULL;");
        contains(sql, "CHECK \\(left_at IS NULL OR left_at > joined_at\\)");
        contains(sql, "ON class_members \\(student_id, class_id, joined_at, left_at\\)");
        contains(sql, "CREATE FUNCTION quizai_guard_membership_episode\\(\\)");
        for (String field : Set.of("id", "class_id", "student_id", "joined_at", "created_at")) {
            contains(sql, "NEW\\." + field + " IS DISTINCT FROM OLD\\." + field);
        }
        contains(sql, "IF OLD\\.left_at IS NOT NULL AND NEW\\.left_at IS DISTINCT FROM OLD\\.left_at THEN");
        contains(sql, "CREATE TRIGGER trg_class_members_episode_guard\\s+BEFORE UPDATE ON class_members\\s+"
                + "FOR EACH ROW EXECUTE FUNCTION quizai_guard_membership_episode\\(\\);");
    }

    @Test
    void foreignKeysAndTimestampsPreserveTheSixTableScopeWithoutSeedOrDestructiveSql() throws IOException {
        String sql = resource(MIGRATION);
        Map<String, String> tables = tables(sql);
        contains(tables.get("auth_sessions"), "user_id\\s+UUID NOT NULL REFERENCES users\\(id\\) ON DELETE RESTRICT");
        contains(tables.get("classes"), "teacher_id\\s+UUID NOT NULL REFERENCES users\\(id\\) ON DELETE RESTRICT");
        contains(tables.get("class_members"), "class_id\\s+UUID NOT NULL REFERENCES classes\\(id\\) ON DELETE RESTRICT");
        contains(tables.get("class_members"), "student_id\\s+UUID NOT NULL REFERENCES users\\(id\\) ON DELETE RESTRICT");
        contains(tables.get("topics"), "subject_id\\s+UUID NOT NULL REFERENCES subjects\\(id\\) ON DELETE RESTRICT");
        contains(tables.get("auth_sessions"), "expires_at\\s+TIMESTAMPTZ NOT NULL");
        contains(tables.get("auth_sessions"), "CHECK \\(expires_at > created_at\\)");
        for (String table : Set.of("users", "auth_sessions", "classes", "class_members")) {
            contains(tables.get(table), "created_at\\s+TIMESTAMPTZ NOT NULL");
        }
        String statements = sql.replaceAll("(?m)--[^\\r\\n]*", "");
        assertFalse(Pattern.compile("(?i)\\bON DELETE CASCADE\\b").matcher(statements).find());
        assertFalse(Pattern.compile("(?im)^\\s*(?:INSERT|UPDATE|DELETE|DROP|TRUNCATE|ALTER)\\b")
                .matcher(statements).find(), "Migration must contain no seeds or destructive statements");
        assertFalse(Pattern.compile("(?i)\\bCREATE\\s+(?:DATABASE|ROLE|USER|EXTENSION|SCHEMA)\\b")
                .matcher(statements).find());
    }

    @Test
    void manualDatabaseTestsRemainRollbackOnlySourceOutsideMigrationLocations() throws IOException {
        String sql = resource(MANUAL_TEST);
        assertTrue(sql.startsWith("-- MANUAL DEV/TEST ONLY."));
        contains(sql, "(?m)^BEGIN;");
        contains(sql, "(?m)^ROLLBACK;\\s*\\z");
        contains(sql, "DO \\$quizai_test\\$");
        for (String negativeCase : Set.of("email normalization", "duplicate email", "enabled join digest uniqueness",
                "two active membership", "closed membership episode", "duplicate subject code",
                "duplicate topic code", "invalid session expiry")) {
            assertTrue(sql.contains("FAIL " + negativeCase), negativeCase);
        }
        assertFalse(Pattern.compile("(?im)^\\s*COMMIT\\s*;").matcher(sql).find());
        assertNull(getClass().getClassLoader().getResource("db/migration/P2A_ROLLBACK_CONTRACT_TEST.sql"));
    }

    private static Map<String, String> tables(String sql) {
        Map<String, String> tables = new LinkedHashMap<>();
        var matcher = Pattern.compile("(?ms)^CREATE TABLE\\s+([a-z_]+)\\s*\\((.*?)^\\);").matcher(sql);
        while (matcher.find()) {
            assertNull(tables.put(matcher.group(1), matcher.group(2)), "Duplicate table declaration");
        }
        return tables;
    }

    private static void contains(String text, String expression) {
        assertTrue(Pattern.compile(expression).matcher(text).find(), expression);
    }

    private static String resource(String name) throws IOException {
        try (var input = P2ASourceContractTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(input, "Missing reviewed SQL source: " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
