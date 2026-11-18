package com.quizai.server.db;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Pure guard tests: no driver, JDBC connection, Spring context or database fixtures. */
class V2DatabaseSafetyTest {
    @Test void acceptsOnlyExactPostgres18IdentityAndDedicatedOwner() {
        var target = new V2SchemaPreparation.Target("TEST", "quizai_v2_test");
        assertDoesNotThrow(() -> V2DatabaseSafety.identity(target, identity()));
        for (var invalid : List.of(
                new V2DatabaseSafety.Identity(170006, "127.0.0.1", 5432, "quizai_v2_test", "quizai_v2_test", "quizai_v2_test", "quizai_v2_test", "public", true, true, true, false),
                new V2DatabaseSafety.Identity(180006, "127.0.0.1", 5432, "quizai_test", "quizai_v2_test", "quizai_v2_test", "quizai_v2_test", "public", true, true, true, false),
                new V2DatabaseSafety.Identity(180006, "127.0.0.1", 5432, "quizai_v2_test", "postgres", "quizai_v2_test", "quizai_v2_test", "public", true, true, true, false),
                new V2DatabaseSafety.Identity(180006, "127.0.0.1", 5432, "quizai_v2_test", "quizai_v2_test", "postgres", "quizai_v2_test", "public", true, true, true, false),
                new V2DatabaseSafety.Identity(180006, "127.0.0.1", 5432, "quizai_v2_test", "quizai_v2_test", "quizai_v2_test", "postgres", "public", true, true, true, false),
                new V2DatabaseSafety.Identity(180006, "127.0.0.1", 5432, "quizai_v2_test", "quizai_v2_test", "quizai_v2_test", "quizai_v2_test", "other", true, true, true, false),
                new V2DatabaseSafety.Identity(180006, "127.0.0.1", 5432, "quizai_v2_test", "quizai_v2_test", "quizai_v2_test", "quizai_v2_test", "public", false, true, true, false),
                new V2DatabaseSafety.Identity(180006, "127.0.0.1", 5432, "quizai_v2_test", "quizai_v2_test", "quizai_v2_test", "quizai_v2_test", "public", true, false, true, false),
                new V2DatabaseSafety.Identity(180006, "127.0.0.1", 5432, "quizai_v2_test", "quizai_v2_test", "quizai_v2_test", "quizai_v2_test", "public", true, true, false, false),
                new V2DatabaseSafety.Identity(180006, "127.0.0.1", 5432, "quizai_v2_test", "quizai_v2_test", "quizai_v2_test", "quizai_v2_test", "public", true, true, true, true))) {
            assertThrows(IllegalStateException.class, () -> V2DatabaseSafety.identity(target, invalid));
        }
    }

    @Test void refusesEveryPowerfulRoleAttribute() {
        for (String privilege : Set.of("superuser", "createdb", "createrole", "replication", "bypassrls", "membership")) {
            assertThrows(IllegalStateException.class, () -> V2DatabaseSafety.role(Set.of(privilege)));
        }
        assertDoesNotThrow(() -> V2DatabaseSafety.role(Set.of()));
    }

    @Test void dedicatedTestAndDevAcceptPostgresFullHostMask() {
        for (String environment : List.of("TEST", "DEV")) {
            String database = "quizai_v2_" + environment.toLowerCase(java.util.Locale.ROOT);
            var target = new V2SchemaPreparation.Target(environment, database);
            assertDoesNotThrow(() -> V2DatabaseSafety.identity(target,
                    new V2DatabaseSafety.Identity(180006, "127.0.0.1/32", 5432,
                            database, database, database, database, "public", true, true, true, false)));
        }
    }

    @Test void neitherAdminNorDedicatedGuardAcceptsOtherHostsOrNetworkMasks() {
        for (String host : new String[]{null, "", "127.0.0.1/0", "127.0.0.1/8", "127.0.0.1/24",
                "127.0.0.1/032", "127.0.0.1/128", "127.0.0.2/32", "127.0.0.10/32", "localhost",
                "::1", "::ffff:127.0.0.1", "127.0.0.1 ", "127.0.0.1/32\n", "127.0.0.1/32?x=1"}) {
            assertThrows(IllegalStateException.class, () -> V2DatabaseProvisioner.adminIdentity("operator",
                    new V2DatabaseProvisioner.AdminIdentity(180006, host, 5432,
                            "template1", "operator", "operator", true)));
            assertThrows(IllegalStateException.class, () -> V2DatabaseSafety.identity(
                    new V2SchemaPreparation.Target("TEST", "quizai_v2_test"),
                    new V2DatabaseSafety.Identity(180006, host, 5432, "quizai_v2_test", "quizai_v2_test",
                            "quizai_v2_test", "quizai_v2_test", "public", true, true, true, false)));
        }
    }

    @Test void historyMustBeFiveSuccessfulSqlMigrationsInOrderWithExactChecksums() {
        var expected = history();
        assertDoesNotThrow(() -> V2DatabaseSafety.history(expected, expected));
        var partial = new ArrayList<>(expected);
        partial.removeLast();
        assertThrows(IllegalStateException.class, () -> V2DatabaseSafety.history(expected, partial));
        for (var invalid : List.of(
                new V2DatabaseSafety.History(1, "1", "SQL", "wrong.sql", 101, true),
                new V2DatabaseSafety.History(1, "1", "BASELINE", "V1.sql", 101, true),
                new V2DatabaseSafety.History(1, "1", "SQL", "V1.sql", 102, true),
                new V2DatabaseSafety.History(1, "1", "SQL", "V1.sql", 101, false),
                new V2DatabaseSafety.History(1, null, "SQL", "V1.sql", 101, true))) {
            var actual = new ArrayList<>(expected);
            actual.set(0, invalid);
            assertThrows(IllegalStateException.class, () -> V2DatabaseSafety.history(expected, actual));
        }
        var reordered = new ArrayList<>(expected);
        java.util.Collections.swap(reordered, 0, 1);
        assertThrows(IllegalStateException.class, () -> V2DatabaseSafety.history(expected, reordered));
    }

    @Test void emptyGuardRejectsEveryUnexpectedObjectCategory() {
        assertDoesNotThrow(() -> V2DatabaseSafety.empty(Set.of()));
        for (String key : Set.of("schema:other", "relation:view", "relation:sequence", "function:helper()",
                "extension:hstore", "type:enum", "trigger:unknown", "event-trigger:unknown", "policy:unknown")) {
            assertThrows(IllegalStateException.class, () -> V2DatabaseSafety.empty(Set.of(key)));
        }
    }

    @Test void knownCatalogRejectsMissingAndAdditionalObjectsAndChangedSignatures() {
        var expected = Map.of("table:users", "r", "function:guard()", "trigger|plpgsql|frozen-body");
        assertDoesNotThrow(() -> V2DatabaseSafety.catalog(expected, expected));
        assertThrows(IllegalStateException.class, () -> V2DatabaseSafety.catalog(expected, Map.of("table:users", "r")));
        assertThrows(IllegalStateException.class, () -> V2DatabaseSafety.catalog(expected, Map.of(
                "table:users", "r", "function:guard()", "trigger|plpgsql|tampered-body")));
        var unknown = new java.util.HashMap<>(expected);
        unknown.put("view:extra", "v");
        assertThrows(IllegalStateException.class, () -> V2DatabaseSafety.catalog(expected, unknown));
    }

    @Test void knownHistoryStillRequiresAllBusinessTablesToContainNoData() {
        var expected = java.util.stream.IntStream.range(0, 24).mapToObj(i -> "table_" + i)
                .collect(java.util.stream.Collectors.toSet());
        var populated = new java.util.HashMap<String, Boolean>();
        expected.forEach(name -> populated.put(name, false));
        assertDoesNotThrow(() -> V2DatabaseSafety.emptyBusinessData(expected, populated));
        populated.put("table_0", true);
        assertThrows(IllegalStateException.class, () -> V2DatabaseSafety.emptyBusinessData(expected, populated));
        populated.put("table_0", false);
        populated.remove("table_23");
        assertThrows(IllegalStateException.class, () -> V2DatabaseSafety.emptyBusinessData(expected, populated));
    }

    @Test void devRequiresEveryTestGateAndCannotBeUnlockedByPartialSuccess() {
        var gates = new V2DatabaseSafety.TestGate();
        for (var step : V2DatabaseSafety.TestStep.values()) {
            assertThrows(IllegalStateException.class, gates::requirePass);
            gates.pass(step);
        }
        assertDoesNotThrow(gates::requirePass);
        var invalid = new V2DatabaseSafety.TestGate();
        assertThrows(IllegalStateException.class, () -> invalid.pass(V2DatabaseSafety.TestStep.CONCURRENCY));
        assertThrows(IllegalStateException.class, invalid::requirePass);
    }

    private static V2DatabaseSafety.Identity identity() {
        return new V2DatabaseSafety.Identity(180006, "127.0.0.1", 5432, "quizai_v2_test", "quizai_v2_test", "quizai_v2_test",
                "quizai_v2_test", "public", true, true, true, false);
    }
    private static List<V2DatabaseSafety.History> history() {
        return java.util.stream.IntStream.rangeClosed(1, 5)
                .mapToObj(i -> new V2DatabaseSafety.History(i, Integer.toString(i), "SQL", "V" + i + ".sql", 100 + i, true)).toList();
    }
}
