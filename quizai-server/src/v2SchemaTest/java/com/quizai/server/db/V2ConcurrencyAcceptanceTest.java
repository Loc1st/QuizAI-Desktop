package com.quizai.server.db;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Offline guard/protocol checks with JDBC doubles only; no driver or PostgreSQL connection. */
class V2ConcurrencyAcceptanceTest {
    @Test void newEmptyTestDatabaseNeedsOnlyZeroRowOrMissingEntityProbes() throws Exception {
        UUID absent = UUID.randomUUID();
        var probes = V2ConcurrencyAcceptance.probes(absent);
        assertEquals(27, probes.size());
        assertEquals(V2SchemaPreparation.inventory().tables(), probes.stream()
                .filter(p -> !p.unavailableAfterRollback()).map(p -> p.label().substring("table:".length())).collect(Collectors.toSet()));
        for (var probe : probes) {
            if (probe.unavailableAfterRollback()) assertTrue(probe.parameters().contains(absent));
            else {
                assertTrue(probe.sql().endsWith(" WHERE false"));
                assertTrue(probe.parameters().isEmpty());
            }
        }
        assertEquals(Set.of("api:start", "api:save", "api:finalize"), probes.stream()
                .filter(V2ConcurrencyAcceptance.Probe::unavailableAfterRollback).map(V2ConcurrencyAcceptance.Probe::label).collect(Collectors.toSet()));
    }

    @Test void devAndLegacyTargetsCannotRunSerializationFixtures() {
        assertDoesNotThrow(() -> V2ConcurrencyAcceptance.requireTest(new V2SchemaPreparation.Target("TEST", "quizai_v2_test")));
        for (var target : new V2SchemaPreparation.Target[]{
                new V2SchemaPreparation.Target("DEV", "quizai_v2_dev"),
                new V2SchemaPreparation.Target("TEST", "quizai_test"),
                new V2SchemaPreparation.Target("DEV", "quizai_v2_test"),
                new V2SchemaPreparation.Target("TEST", "quizai_v2_dev")}) {
            assertThrows(IllegalArgumentException.class, () -> V2ConcurrencyAcceptance.requireTest(target));
        }
        assertThrows(IllegalStateException.class, () -> V2DatabaseRunner.run(new String[]{"deploy"}, Map.of()));
    }

    @Test void onlyActualLockTimeoutCountsAsContentionEvidence() {
        assertDoesNotThrow(() -> V2ConcurrencyAcceptance.requireLockTimeout(new SQLException("sanitized", "55P03")));
        for (String state : new String[]{null, "23514", "42601", "57014", "40P01", "40001", "08006"}) {
            assertThrows(IllegalStateException.class,
                    () -> V2ConcurrencyAcceptance.requireLockTimeout(new SQLException("sanitized", state)));
        }
    }

    @Test void releasedProbesRequireExactZeroOrUnavailableEntityRejection() {
        assertDoesNotThrow(() -> V2ConcurrencyAcceptance.requireZeroRows(0));
        for (int count : new int[]{-1, 1, 2}) assertThrows(IllegalStateException.class, () -> V2ConcurrencyAcceptance.requireZeroRows(count));
        assertDoesNotThrow(() -> V2ConcurrencyAcceptance.requireUnavailable(new SQLException("sanitized", "23514")));
        for (String state : new String[]{null, "55P03", "42601", "57014", "40P01", "40001", "08006"}) {
            assertThrows(IllegalStateException.class,
                    () -> V2ConcurrencyAcceptance.requireUnavailable(new SQLException("sanitized", state)));
        }
    }

    @Test void serializationRequiresDifferentValidSessionIds() {
        assertDoesNotThrow(() -> V2ConcurrencyAcceptance.requireDistinctSessions(10, 11));
        for (int[] ids : new int[][]{{10, 10}, {0, 1}, {1, -1}}) {
            assertThrows(IllegalStateException.class, () -> V2ConcurrencyAcceptance.requireDistinctSessions(ids[0], ids[1]));
        }
    }

    @Test void lockTimeoutThenZeroRowRetryRollsBackBothSessionsAndRestoresLocalTimeouts() throws Exception {
        Connection owner = connection("quizai_v2_test");
        Connection contender = connection("quizai_v2_test");
        PreparedStatement insert = owner.prepareStatement("fixture");
        when(insert.executeUpdate()).thenReturn(1);
        PreparedStatement mutation = contender.prepareStatement("probe");
        when(mutation.executeUpdate()).thenThrow(new SQLException("sanitized", "55P03")).thenReturn(0);
        V2ConcurrencyAcceptance.contention(owner, contender, new V2ConcurrencyAcceptance.Probe("table:users", "probe", List.of(), false));
        verify(owner).rollback();
        verify(contender, times(2)).rollback();
        verify(contender.createStatement(), times(2)).execute("SET LOCAL lock_timeout='500ms'");
        verify(contender.createStatement(), times(2)).execute("SET LOCAL statement_timeout='5s'");
        verify(contender, times(2)).setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        verify(contender, times(2)).setAutoCommit(true);
        var releaseOrder = inOrder(owner, contender, mutation);
        releaseOrder.verify(mutation).executeUpdate();
        releaseOrder.verify(contender).rollback();
        releaseOrder.verify(owner).rollback();
        releaseOrder.verify(mutation).executeUpdate();
        releaseOrder.verify(contender).rollback();
        verify(owner, never()).commit();
        verify(contender, never()).commit();
    }

    @Test void missingEntityApiMustRejectAfterLockRelease() throws Exception {
        Connection owner = connection("quizai_v2_test");
        Connection contender = connection("quizai_v2_test");
        when(owner.prepareStatement("fixture").executeUpdate()).thenReturn(1);
        when(contender.prepareStatement("probe").executeQuery())
                .thenThrow(new SQLException("sanitized", "55P03"), new SQLException("sanitized", "23514"));
        assertDoesNotThrow(() -> V2ConcurrencyAcceptance.contention(owner, contender,
                new V2ConcurrencyAcceptance.Probe("api:start", "probe", List.of(), true)));
        verify(owner).rollback();
        verify(contender, times(2)).rollback();
    }

    @Test void syntaxErrorCannotPassAndStillRollsBackBothSessions() throws Exception {
        Connection owner = connection("quizai_v2_test");
        Connection contender = connection("quizai_v2_test");
        when(owner.prepareStatement("fixture").executeUpdate()).thenReturn(1);
        when(contender.prepareStatement("probe").executeUpdate()).thenThrow(new SQLException("sanitized", "42601"));
        assertThrows(IllegalStateException.class, () -> V2ConcurrencyAcceptance.contention(owner, contender,
                new V2ConcurrencyAcceptance.Probe("table:users", "probe", List.of(), false)));
        verify(owner).rollback();
        verify(contender).rollback();
    }

    @Test void wrongDatabaseFailsBeforeOwnerInsertAndRollsBackBothSessions() throws Exception {
        Connection owner = connection("quizai_dev");
        Connection contender = connection("quizai_v2_test");
        assertThrows(IllegalStateException.class, () -> V2ConcurrencyAcceptance.contention(owner, contender,
                new V2ConcurrencyAcceptance.Probe("table:users", "probe", List.of(), false)));
        verify(owner, never()).prepareStatement(anyString());
        verify(contender, never()).prepareStatement(anyString());
        verify(owner).rollback();
        verify(contender).rollback();
    }

    @Test void ownerInsertMustReallyCreateOneRowBeforeAnyProbeCanCount() throws Exception {
        Connection owner = connection("quizai_v2_test");
        Connection contender = connection("quizai_v2_test");
        assertThrows(IllegalStateException.class, () -> V2ConcurrencyAcceptance.contention(owner, contender,
                new V2ConcurrencyAcceptance.Probe("table:users", "probe", List.of(), false)));
        verify(contender, never()).prepareStatement(anyString());
        verify(owner).rollback();
        verify(contender).rollback();
    }

    @Test void successfulMissingEntityApiCannotMasqueradeAsBarrierRelease() throws Exception {
        Connection owner = connection("quizai_v2_test");
        Connection contender = connection("quizai_v2_test");
        when(owner.prepareStatement("fixture").executeUpdate()).thenReturn(1);
        when(contender.prepareStatement("probe").executeQuery())
                .thenThrow(new SQLException("sanitized", "55P03")).thenReturn(mock(ResultSet.class));
        assertThrows(IllegalStateException.class, () -> V2ConcurrencyAcceptance.contention(owner, contender,
                new V2ConcurrencyAcceptance.Probe("api:start", "probe", List.of(), true)));
        verify(owner).rollback();
        verify(contender, times(2)).rollback();
    }

    private static Connection connection(String database) throws SQLException {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet identity = mock(ResultSet.class);
        PreparedStatement mutation = mock(PreparedStatement.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT current_database()")).thenReturn(identity);
        when(identity.next()).thenReturn(true);
        when(identity.getString(1)).thenReturn(database);
        when(connection.prepareStatement(anyString())).thenReturn(mutation);
        return connection;
    }
}
