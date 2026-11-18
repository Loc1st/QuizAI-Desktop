package com.quizai.server.db;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Offline preparation only. Deliberately has no datasource, JDBC or migrate/validate calls. */
public final class V2SchemaPreparation {
    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration-v2");
    private static final List<String> FILES = List.of("V1__v2_identity_class_catalog.sql",
            "V2__v2_documents_questions_quizzes.sql", "V3__v2_practice_and_ai.sql",
            "V4__v2_assignments_attempts_audit.sql", "V5__v2_integrity_guards.sql");
    private static final Set<String> TABLES = Set.of("users", "auth_sessions", "classes", "class_members",
            "subjects", "topics", "documents", "question_banks", "questions", "question_versions",
            "question_options", "quizzes", "quiz_questions", "practice_sessions", "practice_session_questions",
            "practice_answers", "ai_conversations", "ai_messages", "assignments", "assignment_recipients",
            "assignment_questions", "attempts", "attempt_answers", "audit_events");

    private V2SchemaPreparation() { }

    public static void main(String[] args) {
        try {
            if (args.length != 1) { throw new IllegalArgumentException("Select sources or targets only"); }
            prepare(args[0], System.getenv());
        } catch (Exception failure) {
            // Never include environment values, connection strings or nested exception messages.
            System.err.println("STOP: offline V2 preparation failed (" + failure.getClass().getSimpleName() + ")");
            System.exit(1);
        }
    }

    static void prepare(String action, Map<String, String> environment) throws Exception {
        if (!Set.of("sources", "targets").contains(action)) {
            throw new IllegalArgumentException("Only offline sources/targets actions exist; database access is disabled");
        }
        Inventory inventory = inventory();
        if (action.equals("targets")) {
            // Resolve BOTH targets before emitting a preview. No fallback to legacy variables.
            Target test = target(environment, "TEST");
            Target dev = target(environment, "DEV");
            System.out.println("Preview order: " + test + " -> acceptance/rollback PASS -> " + dev);
        }
        var config = flywayConfiguration();
        System.out.println("Flyway location: " + config.getLocations()[0].getDescriptor());
        System.out.println("V1-V4 business tables: " + inventory.tableCounts() + "; total=" + inventory.tables().size());
        inventory.hashes().forEach((name, hash) -> System.out.println("SHA256 " + name + " " + hash));
        System.out.println("OFFLINE ONLY: no PostgreSQL connection or migration. Live readiness: HOLD; see implementation plan section 10.");
    }

    static FluentConfiguration flywayConfiguration() {
        // No dataSource is installed here. Loading/running Flyway needs a separate authorized live runner.
        return Flyway.configure().locations("classpath:db/migration-v2")
                .schemas("public").defaultSchema("public").createSchemas(false)
                .cleanDisabled(true).baselineOnMigrate(false).outOfOrder(false)
                .validateMigrationNaming(true).failOnMissingLocations(true);
    }

    static Target target(Map<String, String> environment, String name) {
        if (!Set.of("TEST", "DEV").contains(name)) { throw new IllegalArgumentException("Only V2 TEST/DEV"); }
        String prefix = "QUIZAI_V2_" + name + "_DB_";
        String database = "quizai_v2_" + name.toLowerCase(Locale.ROOT);
        String url = required(environment, prefix + "URL");
        String username = required(environment, prefix + "USERNAME");
        required(environment, prefix + "PASSWORD");
        // Exact string equality rejects URL options, userinfo, escapes, multi-host and alternate schemas.
        if (!("jdbc:postgresql://127.0.0.1:5432/" + database).equals(url) || !database.equals(username)) {
            throw new IllegalArgumentException("V2 target must use its exact dedicated database/login on 127.0.0.1:5432");
        }
        return new Target(name, database);
    }

    private static String required(Map<String, String> environment, String key) {
        String value = environment.get(key);
        if (value == null || value.isBlank() || value.contains("${")) {
            throw new IllegalArgumentException("Missing required V2 variable " + key);
        }
        return value;
    }

    static Inventory inventory() throws Exception {
        List<String> actual;
        try (var files = Files.list(MIGRATIONS)) {
            actual = files.map(p -> p.getFileName().toString()).sorted().toList();
        }
        if (!actual.equals(FILES)) { throw new IllegalArgumentException("Expected exactly the five V2 SQL files"); }
        Set<String> tables = new LinkedHashSet<>();
        List<Integer> counts = new ArrayList<>();
        Map<String, String> hashes = new LinkedHashMap<>();
        for (String filename : FILES) {
            byte[] bytes = Files.readAllBytes(MIGRATIONS.resolve(filename));
            String sql = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            var matcher = Pattern.compile("(?im)^CREATE TABLE\\s+([a-z_]+)\\s*\\(").matcher(sql);
            int count = 0;
            while (matcher.find()) {
                if (!tables.add(matcher.group(1))) { throw new IllegalArgumentException("Duplicate V2 business table"); }
                count++;
            }
            counts.add(count);
            hashes.put(filename, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        }
        if (!counts.equals(List.of(6, 7, 5, 6, 0)) || !tables.equals(TABLES)) {
            throw new IllegalArgumentException("V2 must create exactly the approved 24 tables in V1-V4, none in V5");
        }
        return new Inventory(Set.copyOf(tables), List.copyOf(counts), java.util.Collections.unmodifiableMap(hashes));
    }

    record Target(String environment, String database) {
        @Override public String toString() { return environment + ":127.0.0.1:5432/" + database + " (dedicated owner)"; }
    }
    record Inventory(Set<String> tables, List<Integer> tableCounts, Map<String, String> hashes) { }
}
