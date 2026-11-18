package com.quizai.server.db;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Offline checks only: never starts Spring, Flyway or a JDBC connection. */
class V2SchemaPreparationTest {
    @Test
    void migrationInventoryMatchesAllApprovedErdTablesAndGroups() throws Exception {
        var inventory = V2SchemaPreparation.inventory();
        assertEquals(5, inventory.hashes().size());
        assertEquals(java.util.List.of(6, 7, 5, 6, 0), inventory.tableCounts());
        String erd = Files.readString(Path.of("../docs/v2/QUIZAI_ERD_SIMPLIFIED_24_TABLES_DRAFT_v2.0.md"));
        var names = Pattern.compile("(?m)^\\| \\d+ \\| `([a-z_]+)`").matcher(erd)
                .results().map(m -> m.group(1)).collect(java.util.stream.Collectors.toSet());
        assertEquals(24, names.size());
        assertEquals(names, inventory.tables());
    }

    @Test
    void flywayConfigurationHasOneV2LocationAndNoAutomaticSchemaCreation() {
        var config = V2SchemaPreparation.flywayConfiguration();
        assertEquals(1, config.getLocations().length);
        assertEquals("classpath:db/migration-v2", config.getLocations()[0].getDescriptor());
        assertArrayEquals(new String[]{"public"}, config.getSchemas());
        assertEquals("public", config.getDefaultSchema());
        assertTrue(config.isCleanDisabled());
        assertFalse(config.isBaselineOnMigrate());
        assertFalse(config.isCreateSchemas());
        assertFalse(config.isOutOfOrder());
        assertTrue(config.isValidateMigrationNaming());
        assertTrue(config.isFailOnMissingLocations());
        assertNull(config.getDataSource(), "Offline configuration must not install a datasource");
    }

    @Test
    void allLogicalErdColumnsAndForeignKeysReferToDeclaredV2Columns() throws Exception {
        String erd = Files.readString(Path.of("../docs/v2/QUIZAI_ERD_SIMPLIFIED_24_TABLES_DRAFT_v2.0.md"));
        var sql = new StringBuilder();
        try (var files = Files.list(Path.of("src/main/resources/db/migration-v2"))) {
            for (Path file : files.sorted().toList()) { sql.append(Files.readString(file)).append('\n'); }
        }
        Map<String, Set<String>> columns = new HashMap<>();
        var tables = Pattern.compile("(?ms)^CREATE TABLE\\s+([a-z_]+)\\s*\\((.*?)^\\);").matcher(sql);
        while (tables.find()) {
            columns.put(tables.group(1), Pattern.compile("(?m)^    ([a-z_]+) [a-z]")
                    .matcher(tables.group(2)).results().map(m -> m.group(1))
                    .collect(java.util.stream.Collectors.toSet()));
            assertTrue(tables.group(2).contains("id uuid PRIMARY KEY DEFAULT gen_random_uuid()"));
        }
        assertEquals(24, columns.size());
        var dictionary = Pattern.compile("(?m)^\\| `([a-z_]+)` \\| ([^|]+) \\|").matcher(erd);
        int checked = 0;
        while (dictionary.find()) {
            String name = dictionary.group(1);
            if (!columns.containsKey(name)) { continue; }
            var required = Pattern.compile("`([a-z_]+)\\??`").matcher(dictionary.group(2))
                    .results().map(m -> m.group(1)).collect(java.util.stream.Collectors.toSet());
            assertTrue(columns.get(name).containsAll(required), name + " lacks ERD logical columns: " + required);
            checked++;
        }
        assertEquals(24, checked);
        var references = Pattern.compile("REFERENCES ([a-z_]+)\\(([^)]+)\\)").matcher(sql);
        while (references.find()) {
            String parent = references.group(1);
            assertNotNull(columns.get(parent), "Unknown parent: " + parent);
            for (String col : references.group(2).split(",")) {
                assertTrue(columns.get(parent).contains(col.trim()), parent + "." + col);
            }
        }
        String statements = sql.toString().replaceAll("(?m)--[^\\n]*", "");
        assertFalse(Pattern.compile("(?i)\\b(DROP|TRUNCATE|REPAIR|CREATE\\s+DATABASE)\\b")
                .matcher(statements).find());
    }

    @Test
    @SuppressWarnings("unchecked")
    void defaultApplicationStartupStillExcludesBothDatabaseAndFlyway() throws Exception {
        Map<String, Object> yaml = new org.yaml.snakeyaml.Yaml()
                .load(Files.readString(Path.of("src/main/resources/application.yml")));
        var spring = (Map<String, Object>) yaml.get("spring");
        var flyway = (Map<String, Object>) spring.get("flyway");
        var auto = (Map<String, Object>) spring.get("autoconfigure");
        assertEquals(false, flyway.get("enabled"));
        assertEquals(Set.of("org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
                        "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"),
                Set.copyOf((java.util.List<String>) auto.get("exclude")));
    }

    @Test
    void acceptsOnlyDedicatedV2TargetsWithoutExposingSecrets() {
        for (String name : Set.of("TEST", "DEV")) {
            var target = V2SchemaPreparation.target(environment(name), name);
            assertEquals("quizai_v2_" + name.toLowerCase(java.util.Locale.ROOT), target.database());
            assertFalse(target.toString().contains("synthetic-secret"));
        }
    }

    @Test
    void rejectsLegacyAndAmbiguousEndpointsBeforeAnyConnection() {
        for (String url : new String[]{
                "jdbc:postgresql://127.0.0.1:5432/quizai_dev",
                "jdbc:postgresql://127.0.0.1:5432/quizai_test",
                "jdbc:postgresql://127.0.0.1:5432/postgres",
                "jdbc:postgresql://127.0.0.1:5432/quizai_v2_dev",
                "jdbc:postgresql://localhost:5432/quizai_v2_test",
                "jdbc:postgresql://192.0.2.1:5432/quizai_v2_test",
                "jdbc:postgresql://127.0.0.1:5433/quizai_v2_test",
                "jdbc:postgresql://127.0.0.1:5432/quizai_v2_test?currentSchema=other",
                "jdbc:postgresql://user:synthetic-secret@127.0.0.1:5432/quizai_v2_test",
                "jdbc:postgresql://127.0.0.1:5432/quizai_v2_test#fragment",
                "jdbc:postgresql://127.0.0.1:5432/%71uizai_v2_test"}) {
            var env = environment("TEST");
            env.put("QUIZAI_V2_TEST_DB_URL", url);
            var failure = assertThrows(IllegalArgumentException.class, () -> V2SchemaPreparation.target(env, "TEST"));
            assertFalse(failure.getMessage().contains("synthetic-secret"));
        }
    }

    @Test
    void neverFallsBackToLegacyVariablesOrCrossEnvironmentRoles() {
        assertThrows(IllegalArgumentException.class, () -> V2SchemaPreparation.target(Map.of(
                "QUIZAI_TEST_DB_URL", "jdbc:postgresql://127.0.0.1:5432/quizai_test",
                "QUIZAI_TEST_DB_USERNAME", "quizai_test", "QUIZAI_TEST_DB_PASSWORD", "synthetic-secret"), "TEST"));
        for (String user : Set.of("postgres", "quizai_test", "quizai_v2_dev", "other")) {
            var env = environment("TEST");
            env.put("QUIZAI_V2_TEST_DB_USERNAME", user);
            assertThrows(IllegalArgumentException.class, () -> V2SchemaPreparation.target(env, "TEST"));
        }
        assertThrows(IllegalArgumentException.class, () -> V2SchemaPreparation.target(Map.of(), "PROD"));
        for (String field : Set.of("URL", "USERNAME", "PASSWORD")) {
            for (String invalid : new String[]{"", " ", "${UNRESOLVED}"}) {
                var env = environment("TEST");
                env.put("QUIZAI_V2_TEST_DB_" + field, invalid);
                assertThrows(IllegalArgumentException.class, () -> V2SchemaPreparation.target(env, "TEST"));
            }
        }
    }

    @Test
    void refusesDatabaseActionsInsteadOfSilentlyExecutingThem() {
        for (String action : Set.of("deploy", "migrate", "inspect", "repair", "clean")) {
            assertThrows(IllegalArgumentException.class, () -> V2SchemaPreparation.prepare(action, Map.of()));
        }
    }

    private static HashMap<String, String> environment(String name) {
        String database = "quizai_v2_" + name.toLowerCase(java.util.Locale.ROOT);
        return new HashMap<>(Map.of("QUIZAI_V2_" + name + "_DB_URL", "jdbc:postgresql://127.0.0.1:5432/" + database,
                "QUIZAI_V2_" + name + "_DB_USERNAME", database,
                "QUIZAI_V2_" + name + "_DB_PASSWORD", "synthetic-secret"));
    }
}
