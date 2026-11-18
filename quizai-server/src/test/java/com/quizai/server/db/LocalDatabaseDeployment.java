package com.quizai.server.db;

import org.flywaydb.core.Flyway;

import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

/** Explicit operator entry point. Never discovered or run by JUnit or Spring. */
public final class LocalDatabaseDeployment {
    private static final String ERD_SHA = "5ff37a672d9a76ce4092301f53064a850304fed0ace74d8f71fa9db766aa31ab";
    private static final Set<String> VERSIONS = Set.of("1", "2", "3", "4", "5", "6", "7", "8");
    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");
    private static final Path TEST_SQL = Path.of("src/test/resources/db");

    private LocalDatabaseDeployment() { }

    public static void main(String[] args) {
        try {
            if (args.length != 1 || !Set.of("inspect", "deploy").contains(args[0])) {
                throw new IllegalArgumentException("Action must be inspect or deploy");
            }
            // Resolve BOTH environments before making ANY connection.
            Target test = target(System.getenv(), "TEST");
            Target dev = target(System.getenv(), "DEV");
            Manifest manifest = manifest();
            boolean deploy = args[0].equals("deploy");
            if (!deploy) {
                inspect(test, manifest);
                inspect(dev, manifest);
                System.out.println("INSPECT ONLY: no migration or fixture executed.");
                return;
            }
            // A failed TEST inspection, migration, or acceptance never reaches DEV.
            migrate(test, manifest);
            verify(test, true, manifest);
            migrate(dev, manifest);
            verify(dev, false, manifest);
            verifyNoDrift(manifest);
            Path report = Path.of("build/database-deployment/verified.properties");
            Files.createDirectories(report.getParent());
            Properties verified = new Properties();
            verified.setProperty("verified_at", java.time.Instant.now().toString());
            verified.setProperty("test_database", "quizai_test");
            verified.setProperty("dev_database", "quizai_dev");
            verified.setProperty("business_tables", "37");
            verified.setProperty("flyway_versions", "1,2,3,4,5,6,7,8");
            manifest.snapshotHashes().forEach(verified::setProperty);
            try (var output = Files.newOutputStream(report)) {
                verified.store(output, "Only written after TEST acceptance and DEV catalog verification succeed");
            }
            System.out.println("VERIFIED: TEST acceptance + DEV catalog; 37 business tables each, Flyway V1-V8.");
            System.out.println("Evidence: " + report);
        } catch (Exception failure) {
            // Do not dump JDBC URLs, environment values, SQL payloads or nested exceptions.
            String safe = failure instanceof IllegalArgumentException ? failure.getMessage()
                    : failure instanceof SQLException sql ? "Database check failed; SQLSTATE=" + sql.getSQLState()
                    : "Deployment stopped (" + failure.getClass().getSimpleName() + "); no automatic repair/clean.";
            System.err.println("STOP: " + safe);
            System.exit(1);
        }
    }

    static Target target(Map<String, String> environment, String name) throws Exception {
        if (!Set.of("TEST", "DEV").contains(name)) {
            throw new IllegalArgumentException("Only TEST and DEV are authorized");
        }
        String prefix = "QUIZAI_" + name + "_DB_";
        String url = required(environment, prefix + "URL");
        String username = required(environment, prefix + "USERNAME");
        String password = required(environment, prefix + "PASSWORD");
        String database = name.equals("TEST") ? "quizai_test" : "quizai_dev";
        if (!url.startsWith("jdbc:postgresql://")) {
            throw new IllegalArgumentException(name + " URL must use an explicit PostgreSQL TCP endpoint");
        }
        URI parsed;
        try {
            parsed = URI.create(url.substring("jdbc:".length()));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(name + " URL is invalid (value withheld)");
        }
        String host = parsed.getHost();
        if (host == null || !Set.of("127.0.0.1", "localhost", "[::1]", "::1").contains(host)
                || parsed.getPort() != 5432 || !("/" + database).equals(parsed.getPath())
                || parsed.getUserInfo() != null || parsed.getRawQuery() != null || parsed.getFragment() != null) {
            throw new IllegalArgumentException(name + " must target only the verified local PostgreSQL port 5432 and " + database
                    + "; URL credentials, options, alternate hosts/databases are refused");
        }
        for (InetAddress address : InetAddress.getAllByName(host.replace("[", "").replace("]", ""))) {
            if (!address.isLoopbackAddress()) {
                throw new IllegalArgumentException(name + " hostname does not resolve exclusively to loopback");
            }
        }
        if (!username.equals(database)) {
            throw new IllegalArgumentException(name + " must use its dedicated owner/login " + database);
        }
        return new Target(name, url, username, password, database);
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank() || value.contains("${")) {
            throw new IllegalArgumentException("Missing/blank required environment variable " + name);
        }
        return value;
    }

    static Manifest manifest() throws Exception {
        Path erdPath = Path.of("..").resolve(ApprovedLegacyErd.ARCHIVED_PATH);
        byte[] erd = Files.readAllBytes(erdPath);
        if (!sha(ApprovedLegacyErd.signedBytes(erd)).equals(ERD_SHA)) {
            throw new IllegalArgumentException("Approved ERD checksum mismatch");
        }
        List<Path> files;
        try (var stream = Files.list(MIGRATIONS)) {
            files = stream.filter(p -> p.getFileName().toString().matches("V.*\\.sql")).sorted().toList();
        }
        if (files.size() != 8) {
            throw new IllegalArgumentException("Expected exactly V1-V8 migration files");
        }
        if (!sha(Files.readAllBytes(files.getFirst())).equals(
                "fd550d78a7df8e780e7b39dd09a00040ce379cf97720a3edcedf00885c16ef0e")) {
            throw new IllegalArgumentException("Existing approved V1 checksum changed; do not overwrite history");
        }
        Set<String> tables = new HashSet<>();
        Set<String> functions = new HashSet<>();
        Set<String> views = new HashSet<>();
        Map<String, byte[]> captured = new LinkedHashMap<>();
        Map<Path, String> sourceHashes = new LinkedHashMap<>();
        // Check the signed content above; freeze the actual archived bytes for drift checks.
        sourceHashes.put(erdPath, sha(erd));
        for (int i = 0; i < files.size(); i++) {
            String filename = files.get(i).getFileName().toString();
            if (!filename.startsWith("V" + (i + 1) + "__")) {
                throw new IllegalArgumentException("Unexpected or duplicated Flyway migration version");
            }
            byte[] bytes = Files.readAllBytes(files.get(i));
            String sql = new String(bytes, StandardCharsets.UTF_8);
            captured.put("migration/" + filename, bytes);
            sourceHashes.put(files.get(i), sha(bytes));
            collect(sql, "TABLE", tables);
            collect(sql, "FUNCTION", functions);
            collect(sql, "VIEW", views);
        }
        if (tables.size() != 37 || !tables.containsAll(Set.of("users", "attempts", "submission_candidates", "audit_events"))) {
            throw new IllegalArgumentException("Migration inventory must contain the approved 37 tables");
        }
        for (String name : List.of("postgresql_catalog_verification.sql", "postgresql_transactional_acceptance.sql")) {
            Path source = TEST_SQL.resolve(name);
            byte[] bytes = Files.readAllBytes(source);
            captured.put("tests/" + name, bytes);
            sourceHashes.put(source, sha(bytes));
        }
        Path snapshots = Path.of("build/database-deployment");
        Files.createDirectories(snapshots);
        Path runDirectory = Files.createTempDirectory(snapshots, "run-");
        Map<String, String> snapshotHashes = new LinkedHashMap<>();
        for (var entry : captured.entrySet()) {
            Path destination = runDirectory.resolve(entry.getKey());
            Files.createDirectories(destination.getParent());
            Files.write(destination, entry.getValue(), java.nio.file.StandardOpenOption.CREATE_NEW);
            snapshotHashes.put(entry.getKey(), sha(entry.getValue()));
        }
        Manifest manifest = new Manifest(Set.copyOf(tables), Set.copyOf(functions), Set.copyOf(views),
                Map.copyOf(sourceHashes), Map.copyOf(snapshotHashes), runDirectory);
        verifyNoDrift(manifest);
        return manifest;
    }

    static void verifyNoDrift(Manifest manifest) throws Exception {
        for (var entry : manifest.sourceHashes().entrySet()) {
            if (!sha(Files.readAllBytes(entry.getKey())).equals(entry.getValue())) {
                throw new IllegalArgumentException("Source changed during deployment; stop before further database work");
            }
        }
        for (var entry : manifest.snapshotHashes().entrySet()) {
            if (!sha(Files.readAllBytes(manifest.runDirectory().resolve(entry.getKey()))).equals(entry.getValue())) {
                throw new IllegalArgumentException("Deployment snapshot changed; stop before further database work");
            }
        }
    }

    private static void collect(String sql, String kind, Set<String> names) {
        Pattern.compile("(?im)^\\s*CREATE\\s+(?:OR\\s+REPLACE\\s+)?" + kind + "\\s+([a-z_][a-z_0-9]*)\\b")
                .matcher(sql).results().forEach(match -> names.add(match.group(1)));
    }

    private static String sha(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    private static Connection connect(Target target) throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", target.username());
        properties.setProperty("password", target.password());
        properties.setProperty("connectTimeout", "5");
        properties.setProperty("socketTimeout", "60");
        properties.setProperty("ApplicationName", "QuizAI-authorized-local-deployment");
        return DriverManager.getConnection(target.url(), properties);
    }

    private static void inspect(Target target, Manifest manifest) throws Exception {
        verifyNoDrift(manifest);
        boolean history;
        try (Connection connection = connect(target)) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement(); var result = statement.executeQuery("""
                    SELECT current_database(), current_user, current_setting('server_version_num')::int,
                           r.rolsuper, pg_get_userbyid(d.datdba), inet_server_port(), r.rolcreatedb, r.rolcreaterole
                    FROM pg_roles r JOIN pg_database d ON d.datname=current_database()
                    WHERE r.rolname=current_user
                    """)) {
                if (!result.next() || !target.database().equals(result.getString(1))
                        || !target.username().equals(result.getString(2)) || result.getInt(3) / 10000 != 18
                        || result.getBoolean(4) || !target.username().equals(result.getString(5))
                        || result.getInt(6) != 5432 || result.getBoolean(7) || result.getBoolean(8)) {
                    throw new IllegalArgumentException(target.name() + " database/server/owner/least-privilege identity check failed");
                }
            }
            Set<String> schemas = names(connection, "SELECT nspname FROM pg_namespace WHERE nspname !~ '^pg_' AND nspname<>'information_schema'");
            if (!schemas.equals(Set.of("public"))) {
                throw new IllegalArgumentException(target.name() + " contains an unexpected user schema; inspect manually");
            }
            Set<String> tables = names(connection, "SELECT tablename FROM pg_tables WHERE schemaname='public'");
            history = tables.remove("flyway_schema_history");
            if (!manifest.tables().containsAll(tables) || (!history && !tables.isEmpty())) {
                throw new IllegalArgumentException(target.name() + " contains unknown tables or untracked schema; no write allowed");
            }
            Set<String> functions = names(connection, "SELECT p.proname FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public'");
            Set<String> views = names(connection, "SELECT viewname FROM pg_views WHERE schemaname='public'");
            Set<String> otherRelations = names(connection, "SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind IN ('S','m','f')");
            Set<String> otherTypes = names(connection, "SELECT t.typname FROM pg_type t JOIN pg_namespace n ON n.oid=t.typnamespace WHERE n.nspname='public' AND t.typtype IN ('e','d')");
            if ((!history && (!functions.isEmpty() || !views.isEmpty()))
                    || !manifest.functions().containsAll(functions) || !manifest.views().containsAll(views)
                    || !otherRelations.isEmpty() || !otherTypes.isEmpty()) {
                throw new IllegalArgumentException(target.name() + " contains unknown functions/views; no write allowed");
            }
            for (String table : tables) {
                // Identifiers come exclusively from reviewed source; never from request/environment input.
                try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT EXISTS(SELECT 1 FROM public.\"" + table + "\" LIMIT 1)")) {
                    rows.next();
                    if (rows.getBoolean(1)) {
                        throw new IllegalArgumentException(target.name() + " already contains business data in " + table + "; no write allowed");
                    }
                }
            }
            if (history) {
                try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT version, success, type FROM public.flyway_schema_history ORDER BY installed_rank")) {
                    Set<String> seen = new HashSet<>();
                    while (rows.next()) {
                        if (!VERSIONS.contains(rows.getString(1)) || !rows.getBoolean(2)
                                || !"SQL".equals(rows.getString(3)) || !seen.add(rows.getString(1))) {
                            throw new IllegalArgumentException(target.name() + " contains unexpected/failed Flyway history; no repair attempted");
                        }
                    }
                }
            }
            connection.rollback();
            System.out.println(target.name() + ": PG18 identity verified; business tables=" + tables.size()
                    + ", business data empty, history=" + history);
        }
        if (history) {
            flyway(target, manifest).validate();
            System.out.println(target.name() + ": existing Flyway history checksums validated");
        }
    }

    private static Set<String> names(Connection connection, String sql) throws SQLException {
        Set<String> names = new HashSet<>();
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            while (result.next()) {
                names.add(result.getString(1));
            }
        }
        return names;
    }

    private static Flyway flyway(Target target, Manifest manifest) {
        return Flyway.configure().dataSource(target.url(), target.username(), target.password())
                .locations("filesystem:" + manifest.runDirectory().resolve("migration").toAbsolutePath().toString().replace('\\', '/'))
                .defaultSchema("public").schemas("public").createSchemas(false)
                .cleanDisabled(true).baselineOnMigrate(false).validateOnMigrate(true).load();
    }

    private static void migrate(Target target, Manifest manifest) throws Exception {
        inspect(target, manifest);
        verifyNoDrift(manifest);
        Flyway flyway = flyway(target, manifest);
        flyway.migrate();
        verifyNoDrift(manifest);
        flyway.validate();
        Set<String> applied = new HashSet<>();
        for (var migration : flyway.info().applied()) {
            if (!migration.getState().isApplied() || migration.getVersion() == null) {
                throw new IllegalArgumentException(target.name() + " unexpected Flyway migration state");
            }
            applied.add(migration.getVersion().getVersion());
            System.out.println(target.name() + " Flyway V" + migration.getVersion() + " checksum=" + migration.getChecksum());
        }
        if (!applied.equals(VERSIONS)) {
            throw new IllegalArgumentException(target.name() + " did not apply exactly V1-V8");
        }
    }

    private static void verify(Target target, boolean acceptance, Manifest manifest) throws Exception {
        verifyNoDrift(manifest);
        try (Connection connection = connect(target)) {
            connection.setAutoCommit(false);
            try {
                System.out.println(target.name() + ": running catalog verification");
                String catalog = Files.readString(manifest.runDirectory().resolve("tests/postgresql_catalog_verification.sql"));
                try (var statement = connection.createStatement()) {
                    statement.execute(catalog);
                }
                if (acceptance) {
                    System.out.println("TEST: running transactional acceptance (fixtures will be rolled back)");
                    String sql = Files.readString(manifest.runDirectory().resolve("tests/postgresql_transactional_acceptance.sql"));
                    if (!Pattern.compile("(?m)^BEGIN;\\s*$").matcher(sql).find()
                            || !Pattern.compile("(?m)^ROLLBACK;\\s*\\z").matcher(sql).find()
                            || Pattern.compile("(?im)^\\s*COMMIT\\s*;").matcher(sql).find()) {
                        throw new IllegalArgumentException("TEST acceptance must be BEGIN/ROLLBACK-only");
                    }
                    sql = sql.replaceFirst("(?m)^BEGIN;\\s*", "").replaceFirst("(?m)^ROLLBACK;\\s*\\z", "");
                    try (var statement = connection.createStatement()) {
                        statement.execute(sql);
                    }
                }
            } finally {
                connection.rollback();
            }
        }
        verifyNoDrift(manifest);
        System.out.println(target.name() + ": catalog PASS" + (acceptance ? ", transactional acceptance PASS (ROLLED BACK)" : ""));
    }

    record Manifest(Set<String> tables, Set<String> functions, Set<String> views,
                    Map<Path, String> sourceHashes, Map<String, String> snapshotHashes, Path runDirectory) { }

    record Target(String name, String url, String username, String password, String database) {
        @Override public String toString() { return name + " " + database + " (credentials withheld)"; }
    }
}
