package com.quizai.server.db;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Explicit local operator entry point. Creates only missing V2 roles/databases; never alters
 * existing objects. The single administrative connection always uses template1, never postgres.
 * This tool is separate from application startup and the TEST-first schema deployment runner. */
public final class V2DatabaseProvisioner {
    private static final String ADMIN_URL = "jdbc:postgresql://127.0.0.1:5432/template1";
    private static final Set<String> NAMES = Set.of("quizai_v2_test", "quizai_v2_dev");
    private static final long LOCK = 81722024002L;
    private static final int ITERATIONS = 4096;
    private V2DatabaseProvisioner() { }

    public static void main(String[] args) {
        try { run(args, System.getenv()); }
        catch (Exception failure) {
            String state = failure instanceof SQLException sql ? "; SQLSTATE=" + sql.getSQLState() : "";
            // Never print SQL/error payloads, verifier/passwords, administrator identity or causes.
            System.err.println("STOP: V2 provisioning failed (" + failure.getClass().getSimpleName() + state
                    + "). Existing objects were not altered. No schema deployment was started by this tool.");
            System.exit(1);
        }
    }

    static String action(String[] args, Map<String, String> env) {
        String value = args.length == 0 ? "preview" : args.length == 1 ? args[0] : "";
        if (!Set.of("preview", "provision").contains(value)) { throw new IllegalArgumentException("Choose preview or provision"); }
        if (value.equals("provision")) {
            V2DatabaseSafety.require("AUTHORIZE_LOCAL_V2_PROVISION".equals(env.get("QUIZAI_V2_PROVISION_AUTHORIZATION")),
                    "Explicit local provisioning authorization required");
        }
        return value;
    }

    static void run(String[] args, Map<String, String> env) throws Exception {
        if (action(args, env).equals("preview")) {
            System.out.println("V2 provisioning PREVIEW: no credentials needed; no database connection.");
            System.out.println("Explicit provision uses only 127.0.0.1:5432/template1 and creates missing quizai_v2_test/quizai_v2_dev identities.");
            return;
        }
        AdminCredentials admin = admin(env);
        // Validate BOTH exact dedicated targets and the frozen release before any connection.
        V2SchemaPreparation.target(env, "TEST");
        V2SchemaPreparation.target(env, "DEV");
        V2ReleaseManifest release = V2ReleaseManifest.frozen();
        release.unchanged();
        try (Connection connection = admin.open()) {
            connection.setReadOnly(true);
            identity(connection, admin.username);
            boolean locked = false;
            try {
                try (var query = connection.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
                    query.setLong(1, LOCK);
                    try (var rows = query.executeQuery()) {
                        V2DatabaseSafety.require(rows.next() && rows.getBoolean(1), "Another provisioning session is active");
                        locked = true;
                    }
                }
                Map<String, State> before = new LinkedHashMap<>();
                // Reject mismatches in either environment before creating anything.
                for (String name : new String[]{"quizai_v2_test", "quizai_v2_dev"}) {
                    State state = inspect(connection, name);
                    existing(name, state.role(), state.database());
                    before.put(name, state);
                }
                // Generate all needed verifiers offline before the first CREATE. Existing passwords
                // are never rewritten or validated as ASCII; the runner checks their authentication.
                Map<String, String> verifiers = new LinkedHashMap<>();
                for (var entry : before.entrySet()) {
                    if (entry.getValue().role() == null) {
                        String kind = entry.getKey().endsWith("test") ? "TEST" : "DEV";
                        char[] password = env.get("QUIZAI_V2_" + kind + "_DB_PASSWORD").toCharArray();
                        byte[] salt = new byte[16];
                        new SecureRandom().nextBytes(salt);
                        try { verifiers.put(entry.getKey(), scram(password, salt)); }
                        finally { Arrays.fill(password, '\0'); Arrays.fill(salt, (byte) 0); }
                    }
                }
                release.unchanged();
                connection.setReadOnly(false);
                V2DatabaseSafety.require(connection.getAutoCommit(), "CREATE DATABASE requires autocommit");
                for (var entry : before.entrySet()) {
                    String name = entry.getKey();
                    // Reinspect immediately before DDL. Out-of-band changes fail closed; no ALTER,
                    // retry with different credentials, owner repair, drop or cleanup is attempted.
                    V2DatabaseSafety.require(entry.getValue().equals(inspect(connection, name)), "Provisioning catalog changed");
                    if (entry.getValue().role() == null) { execute(connection, createRoleSql(name, verifiers.get(name))); }
                    if (entry.getValue().database() == null) { execute(connection, createDatabaseSql(name)); }
                    State after = inspect(connection, name);
                    V2DatabaseSafety.require(after.role() != null && after.database() != null, "Provisioned identity missing");
                    existing(name, after.role(), after.database());
                    System.out.println(name + ": dedicated identity/owner verified; existing objects preserved.");
                }
                release.unchanged();
            } finally {
                if (locked) {
                    try (var query = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
                        query.setLong(1, LOCK);
                        try (var rows = query.executeQuery()) { rows.next(); }
                    }
                }
            }
        }
    }

    static AdminCredentials admin(Map<String, String> env) {
        String url = required(env, "QUIZAI_V2_ADMIN_DB_URL");
        String username = required(env, "QUIZAI_V2_ADMIN_DB_USERNAME");
        String password = required(env, "QUIZAI_V2_ADMIN_DB_PASSWORD");
        if (!ADMIN_URL.equals(url) || NAMES.contains(username)) {
            throw new IllegalArgumentException("Administrative endpoint must be exact local template1 with a separate operator role");
        }
        return new AdminCredentials(username, password);
    }

    private static String required(Map<String, String> env, String key) {
        String value = env.get(key);
        if (value == null || value.isBlank() || value.contains("${")) { throw new IllegalArgumentException("Missing provisioning variable " + key); }
        return value;
    }

    static final class AdminCredentials {
        private final String username, password;
        private AdminCredentials(String username, String password) { this.username = username; this.password = password; }
        private Connection open() throws SQLException {
            Properties properties = new Properties();
            properties.setProperty("user", username);
            properties.setProperty("password", password);
            properties.setProperty("ApplicationName", "QuizAI-V2-explicit-provisioner");
            properties.setProperty("connectTimeout", "5");
            properties.setProperty("socketTimeout", "90");
            properties.setProperty("options", "-c search_path=pg_catalog");
            try { return DriverManager.getConnection(ADMIN_URL, properties); }
            finally { properties.clear(); }
        }
        @Override public String toString() { return "Local template1 provisioning credentials [redacted]"; }
    }

    record AdminIdentity(int version, String host, int port, String database, String currentUser,
                         String sessionUser, boolean superuser) { }

    static void adminIdentity(String expectedUsername, AdminIdentity actual) {
        // A superuser is necessary to assign a fresh role as DB owner without introducing any
        // role memberships. No administrative rights are ever granted to the dedicated roles.
        V2DatabaseSafety.require(actual.version() / 10000 == 18 && V2DatabaseSafety.localServerAddress(actual.host())
                        && actual.port() == 5432 && "template1".equals(actual.database())
                        && expectedUsername.equals(actual.currentUser()) && expectedUsername.equals(actual.sessionUser())
                        && actual.superuser(), "Administrative PostgreSQL identity or permissions rejected");
    }

    private static void identity(Connection connection, String username) throws SQLException {
        try (var query = connection.prepareStatement("""
                SELECT current_setting('server_version_num')::int, inet_server_addr()::text, inet_server_port(),
                    current_database(), current_user, session_user, rolsuper
                FROM pg_roles WHERE rolname = ?
                """)) {
            query.setString(1, username);
            try (var rows = query.executeQuery()) {
                V2DatabaseSafety.require(rows.next(), "Administrative role missing");
                adminIdentity(username, new AdminIdentity(rows.getInt(1), rows.getString(2), rows.getInt(3),
                        rows.getString(4), rows.getString(5), rows.getString(6), rows.getBoolean(7)));
                V2DatabaseSafety.require(!rows.next(), "Ambiguous administrative role");
            }
        }
    }

    record RoleState(boolean login, boolean superuser, boolean createdb, boolean createrole,
                     boolean replication, boolean bypassrls, boolean membership, boolean unexpired) { }
    record DatabaseState(String owner, String encoding, boolean allowConnections, boolean template) { }
    private record State(RoleState role, DatabaseState database) { }

    static void existing(String name, RoleState role, DatabaseState database) {
        identifier(name);
        if (role != null) {
            V2DatabaseSafety.require(role.login() && !role.superuser() && !role.createdb() && !role.createrole()
                    && !role.replication() && !role.bypassrls() && !role.membership() && role.unexpired(),
                    "Existing V2 role attributes rejected; no changes made");
        }
        if (database != null) {
            V2DatabaseSafety.require(role != null && name.equals(database.owner()) && "UTF8".equals(database.encoding())
                            && database.allowConnections() && !database.template(),
                    "Existing V2 database identity rejected; no changes made");
        }
    }

    private static State inspect(Connection connection, String name) throws SQLException {
        identifier(name);
        RoleState role = null;
        DatabaseState database = null;
        try (var query = connection.prepareStatement("""
                SELECT rolcanlogin, rolsuper, rolcreatedb, rolcreaterole, rolreplication, rolbypassrls,
                    EXISTS(SELECT 1 FROM pg_auth_members m WHERE m.member = r.oid OR m.roleid = r.oid),
                    (rolvaliduntil IS NULL OR rolvaliduntil > CURRENT_TIMESTAMP)
                FROM pg_roles r WHERE rolname = ?
                """)) {
            query.setString(1, name);
            try (var rows = query.executeQuery()) {
                if (rows.next()) {
                    role = new RoleState(rows.getBoolean(1), rows.getBoolean(2), rows.getBoolean(3), rows.getBoolean(4),
                            rows.getBoolean(5), rows.getBoolean(6), rows.getBoolean(7), rows.getBoolean(8));
                    V2DatabaseSafety.require(!rows.next(), "Ambiguous V2 role");
                }
            }
        }
        try (var query = connection.prepareStatement("""
                SELECT pg_get_userbyid(datdba), pg_encoding_to_char(encoding), datallowconn, datistemplate
                FROM pg_database WHERE datname = ?
                """)) {
            query.setString(1, name);
            try (var rows = query.executeQuery()) {
                if (rows.next()) {
                    database = new DatabaseState(rows.getString(1), rows.getString(2), rows.getBoolean(3), rows.getBoolean(4));
                    V2DatabaseSafety.require(!rows.next(), "Ambiguous V2 database");
                }
            }
        }
        return new State(role, database);
    }

    private static String identifier(String name) {
        if (!NAMES.contains(name)) { throw new IllegalArgumentException("Only exact dedicated V2 names allowed"); }
        return '"' + name + '"';
    }

    static String createDatabaseSql(String name) {
        String quoted = identifier(name);
        return "CREATE DATABASE " + quoted + " WITH OWNER = " + quoted + " TEMPLATE = template0 ENCODING = 'UTF8'";
    }

    static String createRoleSql(String name, String verifier) {
        String quoted = identifier(name);
        // Utility DDL does not support password bind parameters. This strict canonical format
        // permits only SCRAM verifier characters, never a plaintext SQL password or SQL escape.
        if (verifier == null || !verifier.matches("SCRAM-SHA-256\\$4096:[A-Za-z0-9+/]{22}==\\$[A-Za-z0-9+/]{43}=:[A-Za-z0-9+/]{43}=")) {
            throw new IllegalArgumentException("Only a generated SCRAM verifier is permitted");
        }
        return "CREATE ROLE " + quoted
                + " WITH NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS NOINHERIT LOGIN PASSWORD '" + verifier + "'";
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.setQueryTimeout(60);
            statement.execute(sql);
        }
    }

    /** PostgreSQL SCRAM-SHA-256 storage format, using a 128-bit salt and standard PBKDF2.
     * Printable ASCII is already SASLprep-normalized. Reject other newly created passwords
     * rather than silently hashing a different Unicode value than the PostgreSQL driver uses. */
    static String scram(char[] password, byte[] salt) throws Exception {
        if (password == null || password.length == 0 || salt == null || salt.length != 16) {
            throw new IllegalArgumentException("Nonempty password and 16-byte salt required");
        }
        for (char value : password) {
            if (value < 0x21 || value > 0x7e) { throw new IllegalArgumentException("New V2 passwords must use printable ASCII without spaces"); }
        }
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, 256);
        byte[] salted = null, client = null, stored = null, server = null;
        try {
            salted = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(salted, "HmacSHA256"));
            client = mac.doFinal("Client Key".getBytes(StandardCharsets.US_ASCII));
            stored = MessageDigest.getInstance("SHA-256").digest(client);
            server = mac.doFinal("Server Key".getBytes(StandardCharsets.US_ASCII));
            Base64.Encoder encoder = Base64.getEncoder();
            return "SCRAM-SHA-256$" + ITERATIONS + ":" + encoder.encodeToString(salt) + "$"
                    + encoder.encodeToString(stored) + ":" + encoder.encodeToString(server);
        } finally {
            spec.clearPassword();
            for (byte[] value : new byte[][]{salted, client, stored, server}) {
                if (value != null) { Arrays.fill(value, (byte) 0); }
            }
        }
    }
}
