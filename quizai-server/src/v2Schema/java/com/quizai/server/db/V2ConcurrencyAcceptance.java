package com.quizai.server.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Opt-in TEST checks for the MVP's global transaction serialization barrier.
 * A real INSERT owns the lock; zero-row DML and unavailable-entity API calls must wait.
 * No committed fixtures are needed, including on a newly migrated empty TEST database.
 * These checks prove statement/API serialization and lock release after rollback only.
 * The separate SQL acceptance gates test valid invariants and sequential idempotency;
 * neither suite is evidence of committed races, load capacity or production throughput.
 */
final class V2ConcurrencyAcceptance {
    private static final List<String> TABLES = List.of("users", "auth_sessions", "classes", "class_members",
            "subjects", "topics", "documents", "question_banks", "questions", "question_versions",
            "question_options", "quizzes", "quiz_questions", "practice_sessions", "practice_session_questions",
            "practice_answers", "ai_conversations", "ai_messages", "assignments", "assignment_recipients",
            "assignment_questions", "attempts", "attempt_answers", "audit_events");
    private static final String OWNER_INSERT = "INSERT INTO public.users(id,email_normalized,password_hash,role) VALUES(?,?,?,'STUDENT')";

    private V2ConcurrencyAcceptance() { }

    static void verify(V2DatabaseRunner.TargetCredentials target) throws Exception {
        requireTest(target.target());
        Map<String, String> before;
        try (Connection connection = target.open()) {
            requireTestDatabase(connection);
            before = fingerprint(connection);
        }
        Exception failure = null;
        try (Connection first = target.open(); Connection second = target.open()) {
            // open() validates the full dedicated server/database/login/schema identity each time.
            requireDistinctSessions(backendPid(first), backendPid(second));
            UUID absent = UUID.randomUUID();
            requireAbsent(first, absent);
            requireAbsent(second, absent);
            for (Probe probe : probes(absent)) {
                contention(first, second, probe);
                contention(second, first, probe);
            }
        } catch (Exception exception) {
            failure = exception;
            throw exception;
        } finally {
            try (Connection connection = target.open()) {
                requireTestDatabase(connection);
                if (!before.equals(fingerprint(connection))) {
                    throw new IllegalStateException("HOLD: TEST data/history differs after serialization rollbacks");
                }
            } catch (Exception verificationFailure) {
                if (failure != null) failure.addSuppressed(verificationFailure);
                else throw verificationFailure;
            }
        }
        System.out.println("TEST statement/API serialization and rollback lock-release checks passed: 54 lock timeouts, 24 table barriers and 3 APIs with both connection roles; all writes rolled back. Valid invariant/idempotency SQL is checked separately; committed-race/load proof was not performed.");
    }

    static void requireTest(V2SchemaPreparation.Target target) {
        if (!"TEST".equals(target.environment()) || !"quizai_v2_test".equals(target.database())) {
            throw new IllegalArgumentException("Serialization probes may run ONLY on exact V2 TEST target");
        }
    }

    static List<Probe> probes(UUID absent) {
        List<Probe> result = new ArrayList<>();
        for (String table : TABLES) {
            // BEFORE STATEMENT must serialize even when the statement cannot match a row.
            result.add(new Probe("table:" + table, "UPDATE public." + table + " SET id=id WHERE false", List.of(), false));
        }
        result.add(new Probe("api:start", "SELECT public.quizai_v2_start_attempt(?,?,?)", List.of(absent, absent, UUID.randomUUID()), true));
        result.add(new Probe("api:save", "SELECT public.quizai_v2_save_answer(?,?,?,?,?)", List.of(absent, absent, absent, "1", 1L), true));
        result.add(new Probe("api:finalize", "SELECT public.quizai_v2_finalize_attempt(?,?,?,?)", List.of(absent, absent, UUID.randomUUID(), "a".repeat(64)), true));
        return List.copyOf(result);
    }

    static void contention(Connection owner, Connection contender, Probe probe) throws SQLException {
        try {
            beginRollbackOnly(owner);
            beginRollbackOnly(contender);
            UUID fixture = UUID.randomUUID();
            try (PreparedStatement insert = owner.prepareStatement(OWNER_INSERT)) {
                insert.setQueryTimeout(8);
                insert.setObject(1, fixture);
                insert.setString(2, "v2.serialization." + fixture + "@example.invalid");
                insert.setString(3, "x".repeat(64));
                if (insert.executeUpdate() != 1) throw new IllegalStateException("Serialization owner INSERT did not create one rollback-only row");
            }
            try {
                execute(contender, probe);
                throw new IllegalStateException("Expected second connection to block during " + probe.label());
            } catch (SQLException blocked) {
                requireLockTimeout(blocked);
            }
        } finally {
            // Includes setup failures and aborted contenders. No test path may commit a write.
            try { rollback(contender); } finally { rollback(owner); }
        }
        try {
            // SET LOCAL values disappeared at rollback, so reapply both bounded timeouts.
            beginRollbackOnly(contender);
            try {
                requireZeroRows(execute(contender, probe));
                if (probe.unavailableAfterRollback()) {
                    throw new IllegalStateException("Unavailable API entity unexpectedly accepted after rollback");
                }
            } catch (SQLException released) {
                if (!probe.unavailableAfterRollback()) throw released;
                requireUnavailable(released);
            }
        } finally {
            rollback(contender);
        }
    }

    private static void rollback(Connection connection) throws SQLException {
        if (!connection.getAutoCommit()) {
            connection.rollback();
            // With no active transaction, this cannot commit fixture data. It lets the next
            // identity SELECT finish before pgjdbc sets isolation for a fresh transaction.
            connection.setAutoCommit(true);
        }
    }

    private static void beginRollbackOnly(Connection connection) throws SQLException {
        requireTestDatabase(connection);
        connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        connection.setReadOnly(false);
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET LOCAL lock_timeout='500ms'");
            statement.execute("SET LOCAL statement_timeout='5s'");
        }
    }

    private static void requireTestDatabase(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("SELECT current_database()")) {
            if (!rows.next() || !"quizai_v2_test".equals(rows.getString(1))) {
                throw new IllegalStateException("HOLD: serialization connection is not quizai_v2_test");
            }
        }
    }

    private static int backendPid(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("SELECT pg_backend_pid()")) {
            if (!rows.next()) throw new IllegalStateException("Missing PostgreSQL session identity");
            return rows.getInt(1);
        }
    }

    static void requireDistinctSessions(int first, int second) {
        if (first <= 0 || second <= 0 || first == second) {
            throw new IllegalStateException("Serialization requires two independent PostgreSQL sessions");
        }
    }

    private static void requireAbsent(Connection connection, UUID absent) throws SQLException {
        for (String table : TABLES) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT EXISTS(SELECT 1 FROM public." + table + " WHERE id=?)")) {
                statement.setObject(1, absent);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next() || rows.getBoolean(1)) {
                        throw new IllegalStateException("HOLD: unavailable-entity serialization UUID is already present");
                    }
                }
            }
        }
    }

    static void requireLockTimeout(SQLException failure) {
        // Syntax, constraints, statement timeouts and deadlocks are not serialization evidence.
        if (!"55P03".equals(failure.getSQLState())) {
            throw new IllegalStateException("Contender failed without expected PostgreSQL lock timeout", failure);
        }
    }

    static void requireUnavailable(SQLException failure) {
        if (!"23514".equals(failure.getSQLState())) {
            throw new IllegalStateException("Released API probe failed without expected unavailable-entity rejection", failure);
        }
    }

    static void requireZeroRows(int count) {
        if (count != 0) throw new IllegalStateException("Released table probe must change exactly zero rows");
    }

    private static int execute(Connection connection, Probe probe) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(probe.sql())) {
            statement.setQueryTimeout(8);
            for (int i = 0; i < probe.parameters().size(); i++) statement.setObject(i + 1, probe.parameters().get(i));
            if (probe.unavailableAfterRollback()) {
                try (ResultSet rows = statement.executeQuery()) { while (rows.next()) { /* drain */ } }
                return -1; // API success is never an acceptable unavailable-entity outcome.
            }
            return statement.executeUpdate();
        }
    }

    private static Map<String, String> fingerprint(Connection connection) throws SQLException {
        Map<String, String> result = new LinkedHashMap<>();
        for (String table : TABLES) fingerprint(connection, result, table, "id");
        fingerprint(connection, result, "flyway_schema_history", "installed_rank");
        return Map.copyOf(result);
    }

    private static void fingerprint(Connection connection, Map<String, String> result, String table, String order) throws SQLException {
        // Fixed identifiers; only counts/hashes leave the connection, never fixture contents.
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(
                "SELECT count(*),md5(coalesce(string_agg(to_jsonb(t)::text,'' ORDER BY t." + order + "),'')) FROM public." + table + " t")) {
            if (!rows.next()) throw new IllegalStateException("Missing TEST data fingerprint");
            result.put(table, rows.getLong(1) + ":" + rows.getString(2));
        }
    }

    record Probe(String label, String sql, List<Object> parameters, boolean unavailableAfterRollback) { }
}
