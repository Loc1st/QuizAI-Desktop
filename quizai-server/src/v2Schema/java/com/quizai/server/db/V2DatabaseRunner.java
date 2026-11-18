package com.quizai.server.db;

import org.flywaydb.core.Flyway;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;

/** Explicit operator entry point. Default preview and freezeRelease are entirely offline.
 * Live actions require a fresh, explicit process authorization; never called by startup/build/check.
 * This class never creates databases/roles, baselines, repairs, cleans or reads legacy databases. */
public final class V2DatabaseRunner {
    private static final Path EVIDENCE = Path.of("build/v2-database-deployment");
    private V2DatabaseRunner() { }

    public static void main(String[] args) {
        try { run(args, System.getenv()); }
        catch (Exception failure) {
            String state = failure instanceof SQLException sql ? "; SQLSTATE=" + sql.getSQLState() : "";
            // JDBC exception messages, SQL payloads, credentials and nested causes are never printed.
            System.err.println("STOP: V2 runner failed (" + failure.getClass().getSimpleName() + state + "). DEV remains HOLD unless all TEST gates passed.");
            System.exit(1);
        }
    }

    static String action(String[] args, Map<String, String> env) {
        String action = args.length == 0 ? "preview" : args.length == 1 ? args[0] : "";
        if (!Set.of("preview", "freezeRelease", "verifyRelease", "verifyResumeProof", "inspect", "deploy", "resume").contains(action)) {
            throw new IllegalArgumentException("Choose preview, freezeRelease, verifyRelease, verifyResumeProof, inspect, deploy or resume");
        }
        if (Set.of("inspect", "deploy", "resume").contains(action)) {
            V2DatabaseSafety.require(("AUTHORIZE_V2_" + action.toUpperCase(Locale.ROOT))
                    .equals(env.get("QUIZAI_V2_DATABASE_AUTHORIZATION")), "Explicit process authorization required");
        }
        return action;
    }

    static void run(String[] args, Map<String, String> env) throws Exception {
        String action = action(args, env);
        if (action.equals("preview")) {
            V2SchemaPreparation.prepare("sources", Map.of());
            System.out.println("V2 runner preview: no database access. Future live authorization is separate; DEV requires every TEST gate.");
            return;
        }
        if (action.equals("freezeRelease")) { V2ReleaseManifest.freeze(); return; }
        if (action.equals("verifyRelease")) {
            V2ReleaseManifest.frozen().unchanged();
            System.out.println("OFFLINE verifyRelease: frozen V2 source/checksum manifest matches; no PostgreSQL connection.");
            return;
        }
        if (action.equals("verifyResumeProof")) {
            V2ReleaseManifest release = V2ReleaseManifest.frozen();
            V2ResumeProof proof = V2ResumeProof.current(release);
            release.unchanged();
            System.out.println("OFFLINE TEST v5 recovery proof verified: installed release=" + proof.installedRelease()
                    + "; proof=" + proof.digest() + "; no PostgreSQL connection.");
            return;
        }
        // Both exact dedicated endpoints must be valid BEFORE the first TEST connection.
        TargetCredentials test = credentials(env, "TEST");
        TargetCredentials dev = credentials(env, "DEV");
        V2ReleaseManifest release = V2ReleaseManifest.frozen();
        if (action.equals("inspect")) {
            try (Connection connection = test.open()) { inspect(connection, test, release); }
            System.out.println("Read-only TEST inspection passed. DEV remains HOLD until full TEST deployment/acceptance gates.");
            return;
        }
        var gate = new V2DatabaseSafety.TestGate();
        V2ResumeProof recovery = action.equals("resume") ? V2ResumeProof.current(release) : null;
        if (recovery == null) { apply(test, release); }
        else { resumeAppliedTest(test, release, recovery); }
        gate.pass(V2DatabaseSafety.TestStep.MIGRATION);
        validate(test, release);
        gate.pass(V2DatabaseSafety.TestStep.VALIDATION);
        verifiedCatalog(test, release);
        gate.pass(V2DatabaseSafety.TestStep.CATALOG);
        rollbackAcceptance(test, release, "v2_transactional_acceptance.sql");
        gate.pass(V2DatabaseSafety.TestStep.ACCEPTANCE_ROLLBACK);
        rollbackAcceptance(test, release, "v2_negative_acceptance.sql");
        gate.pass(V2DatabaseSafety.TestStep.NEGATIVE_ROLLBACK);
        release.unchanged();
        concurrencyAcceptance(test, release);
        gate.pass(V2DatabaseSafety.TestStep.CONCURRENCY);
        if (recovery == null) { rerun(test, release); }
        else { verifyAppliedWithoutMigrate(test, release); }
        gate.pass(V2DatabaseSafety.TestStep.RERUN);
        release.unchanged();
        if (recovery != null) { V2DatabaseSafety.require(recovery.equals(V2ResumeProof.current(release)), "Recovery evidence drift"); }
        gate.requirePass(); // There is deliberately no DEV connection before this line.
        apply(dev, release);
        validate(dev, release);
        verifiedCatalog(dev, release);
        rerun(dev, release);
        release.unchanged();
        System.out.println("V2 TEST " + (recovery == null ? "migration" : "existing-v5 recovery without TEST migration")
                + "/validation/catalog/rollback/negative/concurrency/rerun and DEV migration/catalog/rerun passed.");
        System.out.println("Evidence: " + EVIDENCE + "; no API, JavaFX or Gemini verification is implied.");
    }

    private static void resumeAppliedTest(TargetCredentials test, V2ReleaseManifest release, V2ResumeProof proof) throws Exception {
        V2DatabaseSafety.require("TEST".equals(test.target().environment()), "Recovery is TEST only");
        release.unchanged();
        // Unlike deploy/apply, no Flyway migrate call exists on this TEST recovery path.
        validate(test, release);
        try (Connection connection = test.open()) {
            V2DatabaseSafety.history(release.history(), history(connection));
            Catalog catalog = catalog(connection);
            V2DatabaseSafety.catalog(release.objects(), catalog.names());
            emptyBusinessData(connection, release); // Never assume the failed run left no fixtures.
            executeSql(connection, release.sql("v2_catalog_check.sql"));
            V2DatabaseSafety.require(proof.equals(V2ResumeProof.current(release)), "Recovery proof drift");
            Path known = evidence(test.target());
            if (Files.exists(known)) { verifyEvidence(known, test.target(), release, catalog.digest()); }
            else {
                // First local attestation after the known initial catalog query failed. This
                // requires archived exact SQL + successful original install proof and all live
                // read-only checks above. Never replace a conflicting existing attestation.
                evidence(test.target(), release, catalog, true);
            }
        }
        verifiedCatalog(test, release);
        System.out.println("TEST v5 recovery read-only inspection passed: exact history/checksums/catalog, 24 empty business tables; no TEST migration executed.");
    }

    private static void verifyAppliedWithoutMigrate(TargetCredentials test, V2ReleaseManifest release) throws Exception {
        release.unchanged();
        try (Connection connection = test.open()) {
            String before = dataFingerprint(connection);
            Catalog original = catalog(connection);
            validate(test, release);
            V2DatabaseSafety.require(flyway(test, release, false).info().pending().length == 0, "Recovery found pending migrations");
            emptyBusinessData(connection, release);
            V2DatabaseSafety.require(before.equals(dataFingerprint(connection)) && original.digest().equals(catalog(connection).digest()), "Recovery validation changed data/catalog");
        }
        verifiedCatalog(test, release);
    }

    static TargetCredentials credentials(Map<String, String> env, String name) {
        V2SchemaPreparation.Target target = V2SchemaPreparation.target(env, name);
        String prefix = "QUIZAI_V2_" + name + "_DB_";
        return new TargetCredentials(target, env.get(prefix + "URL"), env.get(prefix + "USERNAME"), env.get(prefix + "PASSWORD"));
    }

    static final class TargetCredentials {
        private final V2SchemaPreparation.Target target;
        private final String url, username, password;
        private TargetCredentials(V2SchemaPreparation.Target target, String url, String username, String password) {
            this.target = target; this.url = url; this.username = username; this.password = password;
        }
        V2SchemaPreparation.Target target() { return target; }
        Connection open() throws Exception {
            Properties properties = new Properties();
            properties.setProperty("user", username);
            properties.setProperty("password", password);
            properties.setProperty("ApplicationName", "QuizAI-V2-explicit-runner");
            properties.setProperty("connectTimeout", "5");
            properties.setProperty("socketTimeout", "90");
            Connection connection = DriverManager.getConnection(url, properties);
            try {
                connection.setReadOnly(true);
                identity(connection, target);
                return connection;
            } catch (Exception failure) { connection.close(); throw failure; }
        }
        @Override public String toString() { return target.toString(); }
    }

    private static void identity(Connection connection, V2SchemaPreparation.Target target) throws Exception {
        String sql = """
                SELECT current_setting('server_version_num')::int, inet_server_addr()::text, inet_server_port(),
                  current_database(), current_user, session_user, pg_get_userbyid(d.datdba), current_schema(),
                  (pg_get_userbyid(n.nspowner) = current_user OR pg_get_userbyid(n.nspowner) = 'pg_database_owner'),
                  has_schema_privilege(current_user, 'public', 'USAGE'),
                  has_schema_privilege(current_user, 'public', 'CREATE'),
                  EXISTS (SELECT 1 FROM aclexplode(coalesce(n.nspacl, acldefault('n', n.nspowner))) a
                    WHERE a.privilege_type = 'CREATE' AND a.grantee NOT IN
                      (d.datdba, (SELECT oid FROM pg_roles WHERE rolname='pg_database_owner')))
                FROM pg_database d JOIN pg_namespace n ON n.nspname='public' WHERE d.datname=current_database()
                """;
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            V2DatabaseSafety.require(rows.next(), "Missing public schema or database identity");
            V2DatabaseSafety.identity(target, new V2DatabaseSafety.Identity(rows.getInt(1), rows.getString(2), rows.getInt(3),
                    rows.getString(4), rows.getString(5), rows.getString(6), rows.getString(7), rows.getString(8),
                    rows.getBoolean(9), rows.getBoolean(10), rows.getBoolean(11), rows.getBoolean(12)));
            V2DatabaseSafety.require(!rows.next(), "Ambiguous database identity");
        }
        Set<String> attributes = new LinkedHashSet<>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("""
                SELECT rolsuper, rolcreatedb, rolcreaterole, rolreplication, rolbypassrls, rolcanlogin,
                  EXISTS (SELECT 1 FROM pg_auth_members m WHERE m.member=r.oid OR m.roleid=r.oid),
                  EXISTS (SELECT 1 FROM pg_database d, LATERAL aclexplode(coalesce(d.datacl, acldefault('d',d.datdba))) a
                    WHERE d.datname=current_database() AND a.privilege_type='CREATE' AND a.grantee<>d.datdba)
                FROM pg_roles r WHERE rolname=current_user
                """)) {
            V2DatabaseSafety.require(rows.next(), "Missing dedicated login role");
            String[] names = {"superuser", "createdb", "createrole", "replication", "bypassrls"};
            for (int i = 0; i < names.length; i++) { if (rows.getBoolean(i + 1)) { attributes.add(names[i]); } }
            if (!rows.getBoolean(6)) { attributes.add("nologin"); }
            if (rows.getBoolean(7)) { attributes.add("membership"); }
            if (rows.getBoolean(8)) { attributes.add("unexpected-database-create-grant"); }
        }
        V2DatabaseSafety.role(attributes);
    }

    /** Existing history is accepted only with previous local release/catalog evidence. */
    private static boolean inspect(Connection connection, TargetCredentials target, V2ReleaseManifest release) throws Exception {
        release.unchanged();
        boolean history;
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT to_regclass('public.flyway_schema_history') IS NOT NULL")) {
            rows.next(); history = rows.getBoolean(1);
        }
        if (!history) { V2DatabaseSafety.empty(userObjects(connection)); return false; }
        V2DatabaseSafety.history(release.history(), history(connection));
        Catalog catalog = catalog(connection);
        V2DatabaseSafety.catalog(release.objects(), catalog.names());
        emptyBusinessData(connection, release);
        verifyEvidence(evidence(target.target()), target.target(), release, catalog.digest());
        return true;
    }

    private static void emptyBusinessData(Connection connection, V2ReleaseManifest release) throws SQLException {
        Set<String> tables = new LinkedHashSet<>();
        release.objects().keySet().stream().filter(key -> key.startsWith("table:"))
                .map(key -> key.substring("table:".length())).forEach(tables::add);
        Map<String, Boolean> populated = new TreeMap<>();
        for (String table : tables) {
            V2DatabaseSafety.require(table.matches("[a-z_][a-z0-9_]*"), "Invalid frozen table identifier");
            try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                    "SELECT EXISTS (SELECT 1 FROM public.\"" + table + "\")")) {
                rows.next(); populated.put(table, rows.getBoolean(1));
            }
        }
        V2DatabaseSafety.emptyBusinessData(tables, populated);
    }

    static void verifyEvidence(Path path, V2SchemaPreparation.Target target, V2ReleaseManifest release, String catalog) throws Exception {
        V2DatabaseSafety.require(Files.isRegularFile(path), "Existing V2 history requires prior local catalog evidence");
        Properties known = new Properties();
        try (var input = Files.newInputStream(path)) { known.load(input); }
        V2DatabaseSafety.require(target.database().equals(known.getProperty("database"))
                        && release.digest().equals(known.getProperty("release_sha256"))
                        && catalog.equals(known.getProperty("catalog_sha256")),
                "Existing V2 history/catalog does not match prior frozen release evidence");
    }

    private static void apply(TargetCredentials target, V2ReleaseManifest release) throws Exception {
        try (Connection connection = target.open()) { inspect(connection, target, release); }
        release.unchanged();
        Flyway flyway = flyway(target, release, true);
        flyway.migrate();
        release.unchanged();
        // Persist the first known catalog only after fresh install and exact structural/history checks.
        try (Connection connection = target.open()) {
            V2DatabaseSafety.history(release.history(), history(connection));
            Catalog catalog = catalog(connection);
            V2DatabaseSafety.catalog(release.objects(), catalog.names());
            emptyBusinessData(connection, release);
            evidence(target.target(), release, catalog);
        }
    }

    private static Flyway flyway(TargetCredentials target, V2ReleaseManifest release, boolean writable) throws Exception {
        Path snapshot = release.migrationSnapshot();
        // The filesystem release contains exactly these five frozen V2 files, never legacy migration/.
        return V2SchemaPreparation.flywayConfiguration().locations("filesystem:" + snapshot.toString().replace('\\', '/'))
                .dataSource(new GuardedDataSource(target, writable)).load();
    }

    private static void validate(TargetCredentials target, V2ReleaseManifest release) throws Exception {
        release.unchanged();
        flyway(target, release, false).validate();
        try (Connection connection = target.open()) { V2DatabaseSafety.history(release.history(), history(connection)); }
    }

    private static void verifiedCatalog(TargetCredentials target, V2ReleaseManifest release) throws Exception {
        try (Connection connection = target.open()) {
            Catalog catalog = catalog(connection);
            V2DatabaseSafety.catalog(release.objects(), catalog.names());
            verifyEvidence(evidence(target.target()), target.target(), release, catalog.digest());
            executeSql(connection, release.sql("v2_catalog_check.sql"));
        }
    }

    private static void rerun(TargetCredentials target, V2ReleaseManifest release) throws Exception {
        try (Connection connection = target.open()) {
            Catalog before = catalog(connection); String data = dataFingerprint(connection);
            var result = flyway(target, release, true).migrate();
            V2DatabaseSafety.require(result.migrationsExecuted == 0, "Rerun unexpectedly executed migrations");
            validate(target, release);
            V2DatabaseSafety.require(before.digest().equals(catalog(connection).digest()) && data.equals(dataFingerprint(connection)), "Rerun changed catalog or data");
        }
        verifiedCatalog(target, release);
    }

    private static void rollbackAcceptance(TargetCredentials target, V2ReleaseManifest release, String filename) throws Exception {
        V2DatabaseSafety.require("TEST".equals(target.target().environment()), "Fixtures restricted to dedicated TEST");
        release.unchanged();
        try (Connection connection = target.open()) {
            V2DatabaseSafety.require("quizai_v2_test".equals(connection.getCatalog()), "Fixture target rejected");
            String before = dataFingerprint(connection);
            String catalogBefore = catalog(connection).digest();
            String sql = release.sql(filename);
            // Runner owns the transaction; source scripts retain standalone BEGIN/ROLLBACK for operator use.
            sql = sql.replaceAll("(?im)^\\s*(BEGIN|ROLLBACK)\\s*;\\s*$", "");
            V2DatabaseSafety.require(!PatternSupport.TRANSACTION_CONTROL.matcher(sql).find(), "Acceptance script contains transaction control");
            connection.setReadOnly(false);
            connection.setAutoCommit(false);
            Exception failure = null;
            try { executeSql(connection, sql); }
            catch (Exception rejected) { failure = rejected; }
            finally { connection.rollback(); connection.setAutoCommit(true); connection.setReadOnly(true); }
            V2DatabaseSafety.require(before.equals(dataFingerprint(connection)) && catalogBefore.equals(catalog(connection).digest()), "TEST rollback did not restore all data/catalog");
            if (failure != null) { throw failure; }
        }
    }

    private static void executeSql(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.setQueryTimeout(90);
            boolean result = statement.execute(sql);
            while (true) {
                if (result) { try (var rows = statement.getResultSet()) { while (rows.next()) { /* Drain every result. */ } } }
                else if (statement.getUpdateCount() == -1) { break; }
                result = statement.getMoreResults();
            }
        }
    }

    private static void concurrencyAcceptance(TargetCredentials test, V2ReleaseManifest release) throws Exception {
        String beforeData, beforeCatalog;
        try (Connection connection = test.open()) {
            beforeData = dataFingerprint(connection); beforeCatalog = catalog(connection).digest();
        }
        Exception failure = null;
        try { V2ConcurrencyAcceptance.verify(test); }
        catch (Exception rejected) { failure = rejected; }
        finally {
            try (Connection connection = test.open()) {
                V2DatabaseSafety.require(beforeData.equals(dataFingerprint(connection)) && beforeCatalog.equals(catalog(connection).digest()),
                        "Concurrency fixtures did not restore all TEST data/catalog");
            }
        }
        release.unchanged();
        if (failure != null) { throw failure; }
    }

    private static List<V2DatabaseSafety.History> history(Connection connection) throws SQLException {
        List<V2DatabaseSafety.History> result = new ArrayList<>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT installed_rank,version,type,script,checksum,success FROM public.flyway_schema_history ORDER BY installed_rank")) {
            while (rows.next()) { result.add(new V2DatabaseSafety.History(rows.getInt(1), rows.getString(2), rows.getString(3), rows.getString(4), (Integer) rows.getObject(5), rows.getBoolean(6))); }
        }
        return result;
    }

    private static Path evidence(V2SchemaPreparation.Target target) { return EVIDENCE.resolve(target.database() + ".properties"); }
    private static void evidence(V2SchemaPreparation.Target target, V2ReleaseManifest release, Catalog catalog) throws Exception {
        evidence(target, release, catalog, false);
    }
    private static void evidence(V2SchemaPreparation.Target target, V2ReleaseManifest release, Catalog catalog, boolean firstOnly) throws Exception {
        Files.createDirectories(EVIDENCE);
        Properties evidence = new Properties();
        evidence.setProperty("database", target.database());
        evidence.setProperty("release_sha256", release.digest());
        evidence.setProperty("catalog_sha256", catalog.digest());
        evidence.setProperty("catalog_verified_at", java.time.Instant.now().toString());
        evidence.setProperty("scope", "catalog-and-five-migration-history-only");
        var options = firstOnly ? new java.nio.file.OpenOption[]{java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE}
                : new java.nio.file.OpenOption[]{java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING, java.nio.file.StandardOpenOption.WRITE};
        try (var out = Files.newOutputStream(evidence(target), options)) { evidence.store(out, "Local prior catalog evidence; does not assert TEST acceptance or application readiness"); }
    }

    private record Catalog(Map<String, String> names, String digest) { }

    private static Catalog catalog(Connection connection) throws Exception {
        Map<String, String> names = new TreeMap<>();
        Map<String, String> signatures = new TreeMap<>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("""
                SELECT 'table:'||c.relname, c.relkind::text, c.relkind::text||'|'||c.relpersistence::text||'|'||c.relrowsecurity||'|'||c.relforcerowsecurity
                  FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                  WHERE n.nspname='public' AND c.relkind NOT IN ('i','I') AND c.relname<>'flyway_schema_history'
                UNION ALL SELECT 'constraint:'||c.relname||':'||k.conname, k.contype::text,
                    pg_get_constraintdef(k.oid,true)||'|'||k.convalidated||'|'||k.condeferrable||'|'||k.condeferred
                  FROM pg_constraint k JOIN pg_class c ON c.oid=k.conrelid JOIN pg_namespace n ON n.oid=c.relnamespace
                  WHERE n.nspname='public' AND c.relname<>'flyway_schema_history' AND k.contype<>'n'
                UNION ALL SELECT 'index:'||i.relname, 'i', pg_get_indexdef(i.oid)||'|'||x.indisvalid||'|'||x.indisready
                  FROM pg_index x JOIN pg_class i ON i.oid=x.indexrelid JOIN pg_namespace n ON n.oid=i.relnamespace
                  WHERE n.nspname='public' AND x.indrelid<>'public.flyway_schema_history'::regclass
                    AND NOT EXISTS(SELECT 1 FROM pg_constraint k WHERE k.conindid=i.oid)
                UNION ALL SELECT 'function:'||p.proname, p.prokind::text, pg_get_functiondef(p.oid)
                  FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public'
                UNION ALL SELECT 'trigger:'||c.relname||':'||t.tgname, 't', pg_get_triggerdef(t.oid,true)||'|'||t.tgenabled::text
                  FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid JOIN pg_namespace n ON n.oid=c.relnamespace
                  WHERE n.nspname='public' AND NOT t.tgisinternal
                """)) {
            while (rows.next()) {
                V2DatabaseSafety.require(names.putIfAbsent(rows.getString(1), rows.getString(2)) == null, "Duplicate or overloaded catalog object");
                signatures.put(rows.getString(1), rows.getString(3));
            }
        }
        // Includes column types/defaults/nullability and PG18 named NOT NULL constraints in the fingerprint.
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("""
                SELECT c.relname||':'||a.attnum, a.attname||'|'||format_type(a.atttypid,a.atttypmod)||'|'||a.attnotnull||'|'||a.attidentity::text||'|'||a.attgenerated::text||'|'||coalesce(pg_get_expr(d.adbin,d.adrelid),'')
                FROM pg_attribute a JOIN pg_class c ON c.oid=a.attrelid JOIN pg_namespace n ON n.oid=c.relnamespace
                LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum
                WHERE n.nspname='public' AND c.relkind IN ('r','p') AND a.attnum>0 AND NOT a.attisdropped
                UNION ALL SELECT 'not-null:'||c.relname||':'||k.conname, pg_get_constraintdef(k.oid,true)
                FROM pg_constraint k JOIN pg_class c ON c.oid=k.conrelid JOIN pg_namespace n ON n.oid=c.relnamespace
                WHERE n.nspname='public' AND k.contype='n'
                UNION ALL SELECT 'history-constraint:'||k.conname, pg_get_constraintdef(k.oid,true)
                FROM pg_constraint k WHERE k.conrelid='public.flyway_schema_history'::regclass
                UNION ALL SELECT 'history-index:'||i.relname, pg_get_indexdef(i.oid)||'|'||x.indisvalid||'|'||x.indisready
                FROM pg_index x JOIN pg_class i ON i.oid=x.indexrelid WHERE x.indrelid='public.flyway_schema_history'::regclass
                """)) {
            while (rows.next()) { signatures.put("column:" + rows.getString(1), rows.getString(2)); }
        }
        Set<String> unexpected = userObjects(connection);
        unexpected.removeIf(key -> key.startsWith("relation:public:") || key.startsWith("function:public:")
                || key.startsWith("constraint:public:") || key.startsWith("trigger:public:"));
        V2DatabaseSafety.require(unexpected.isEmpty(), "Unexpected non-V2 user object category");
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("""
                SELECT count(*) FROM (
                  SELECT c.relowner AS owner FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public'
                  UNION ALL SELECT p.proowner FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public'
                ) x WHERE pg_get_userbyid(owner)<>current_user
                """)) { rows.next(); V2DatabaseSafety.require(rows.getLong(1) == 0, "Unexpected object owner"); }
        return new Catalog(names, V2ReleaseManifest.sha(signatures.toString().getBytes(StandardCharsets.UTF_8)));
    }

    /** All user object categories must be empty before first migration, including non-public schemas. */
    private static Set<String> userObjects(Connection connection) throws SQLException {
        Set<String> result = new LinkedHashSet<>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("""
                WITH user_ns AS (SELECT oid,nspname FROM pg_namespace WHERE nspname<>'information_schema' AND nspname !~ '^pg_')
                SELECT 'schema:'||nspname FROM user_ns WHERE nspname<>'public'
                UNION ALL SELECT 'relation:'||n.nspname||':'||c.relname FROM pg_class c JOIN user_ns n ON n.oid=c.relnamespace
                UNION ALL SELECT 'function:'||n.nspname||':'||p.proname FROM pg_proc p JOIN user_ns n ON n.oid=p.pronamespace
                UNION ALL SELECT 'type:'||n.nspname||':'||t.typname FROM pg_type t JOIN user_ns n ON n.oid=t.typnamespace WHERE t.typrelid=0 AND t.typelem=0
                UNION ALL SELECT 'constraint:'||n.nspname||':'||k.conname FROM pg_constraint k JOIN user_ns n ON n.oid=k.connamespace
                UNION ALL SELECT 'trigger:'||n.nspname||':'||t.tgname FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid JOIN user_ns n ON n.oid=c.relnamespace WHERE NOT t.tgisinternal
                UNION ALL SELECT 'extension:'||extname FROM pg_extension WHERE extname<>'plpgsql'
                UNION ALL SELECT 'event-trigger:'||evtname FROM pg_event_trigger
                UNION ALL SELECT 'policy:'||p.polname FROM pg_policy p
                UNION ALL SELECT 'publication:'||pubname FROM pg_publication
                UNION ALL SELECT 'subscription:'||subname FROM pg_subscription WHERE subdbid=(SELECT oid FROM pg_database WHERE datname=current_database())
                UNION ALL SELECT 'foreign-server:'||srvname FROM pg_foreign_server
                UNION ALL SELECT 'foreign-data-wrapper:'||fdwname FROM pg_foreign_data_wrapper
                UNION ALL SELECT 'operator:'||o.oprname FROM pg_operator o JOIN user_ns n ON n.oid=o.oprnamespace
                UNION ALL SELECT 'operator-class:'||o.opcname FROM pg_opclass o JOIN user_ns n ON n.oid=o.opcnamespace
                UNION ALL SELECT 'operator-family:'||o.opfname FROM pg_opfamily o JOIN user_ns n ON n.oid=o.opfnamespace
                UNION ALL SELECT 'collation:'||c.collname FROM pg_collation c JOIN user_ns n ON n.oid=c.collnamespace
                UNION ALL SELECT 'conversion:'||c.conname FROM pg_conversion c JOIN user_ns n ON n.oid=c.connamespace
                UNION ALL SELECT 'text-search-config:'||c.cfgname FROM pg_ts_config c JOIN user_ns n ON n.oid=c.cfgnamespace
                UNION ALL SELECT 'text-search-dictionary:'||c.dictname FROM pg_ts_dict c JOIN user_ns n ON n.oid=c.dictnamespace
                UNION ALL SELECT 'text-search-parser:'||c.prsname FROM pg_ts_parser c JOIN user_ns n ON n.oid=c.prsnamespace
                UNION ALL SELECT 'text-search-template:'||c.tmplname FROM pg_ts_template c JOIN user_ns n ON n.oid=c.tmplnamespace
                UNION ALL SELECT 'cast:'||c.oid FROM pg_cast c WHERE c.oid>=16384
                UNION ALL SELECT 'transform:'||t.oid FROM pg_transform t WHERE t.oid>=16384
                UNION ALL SELECT 'large-object:'||m.oid FROM pg_largeobject_metadata m
                UNION ALL SELECT 'language:'||lanname FROM pg_language WHERE lanname NOT IN ('internal','c','sql','plpgsql')
                """)) { while (rows.next()) { result.add(rows.getString(1)); } }
        return result;
    }

    private static String dataFingerprint(Connection connection) throws Exception {
        var inventory = V2SchemaPreparation.inventory();
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        for (String table : inventory.tables().stream().sorted().toList()) {
            // Table names come only from the immutable, approved 24-table allowlist.
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT row_to_json(t)::text FROM public." + table + " t ORDER BY id")) {
                digest.update(table.getBytes(StandardCharsets.UTF_8));
                while (rows.next()) {
                    byte[] row = rows.getString(1).getBytes(StandardCharsets.UTF_8);
                    digest.update(java.nio.ByteBuffer.allocate(4).putInt(row.length).array()); digest.update(row);
                }
            }
        }
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT row_to_json(t)::text FROM public.flyway_schema_history t ORDER BY installed_rank")) {
            while (rows.next()) {
                byte[] row = rows.getString(1).getBytes(StandardCharsets.UTF_8);
                digest.update(java.nio.ByteBuffer.allocate(4).putInt(row.length).array()); digest.update(row);
            }
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    private static final class PatternSupport {
        private static final java.util.regex.Pattern TRANSACTION_CONTROL = java.util.regex.Pattern.compile(
                "(?im)^\\s*(COMMIT|ROLLBACK|START TRANSACTION|BEGIN TRANSACTION)\\s*;");
    }

    private static final class GuardedDataSource implements DataSource {
        private final TargetCredentials target; private final boolean writable;
        GuardedDataSource(TargetCredentials target, boolean writable) { this.target = target; this.writable = writable; }
        @Override public Connection getConnection() throws SQLException {
            try {
                Connection connection = target.open();
                try { if (writable) { connection.setReadOnly(false); } return connection; }
                catch (SQLException failure) { connection.close(); throw failure; }
            }
            catch (SQLException failure) { throw failure; }
            catch (Exception failure) { throw new SQLException("V2 identity rejected", "08004"); }
        }
        @Override public Connection getConnection(String user, String password) throws SQLException { throw new SQLException("Credential override rejected"); }
        @Override public PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(PrintWriter writer) throws SQLException { throw new SQLException("JDBC logging rejected"); }
        @Override public void setLoginTimeout(int seconds) { }
        @Override public int getLoginTimeout() { return 5; }
        @Override public Logger getParentLogger() { return Logger.getLogger("com.quizai.server.db.v2"); }
        @Override public <T> T unwrap(Class<T> type) throws SQLException { throw new SQLException("Unwrap rejected"); }
        @Override public boolean isWrapperFor(Class<?> type) { return false; }
    }
}
