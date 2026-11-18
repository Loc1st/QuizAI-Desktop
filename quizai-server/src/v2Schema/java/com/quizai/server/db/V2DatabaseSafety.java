package com.quizai.server.db;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure fail-closed decisions, shared by the future live runner and offline tests. */
final class V2DatabaseSafety {
    private V2DatabaseSafety() { }

    record Identity(int version, String host, int port, String database, String currentUser,
                    String sessionUser, String databaseOwner, String schema, boolean schemaOwner,
                    boolean schemaUsage, boolean schemaCreate, boolean unexpectedCreateGrant) { }
    record History(int rank, String version, String type, String script, Integer checksum, boolean success) { }

    static void identity(V2SchemaPreparation.Target target, Identity actual) {
        require(actual.version() / 10000 == 18 && localServerAddress(actual.host()) && actual.port() == 5432,
                "PostgreSQL server identity rejected");
        require(target.database().equals(actual.database()) && target.database().equals(actual.currentUser())
                        && target.database().equals(actual.sessionUser()) && target.database().equals(actual.databaseOwner()),
                "Dedicated database/login/owner identity rejected");
        require("public".equals(actual.schema()) && actual.schemaOwner() && actual.schemaUsage() && actual.schemaCreate()
                && !actual.unexpectedCreateGrant(), "Public schema identity or privileges rejected");
    }

    /** PostgreSQL inet::text can include the full IPv4 host mask. Both exact spellings
     * represent the configured address; other hosts, masks, aliases and whitespace fail. */
    static boolean localServerAddress(String address) {
        return "127.0.0.1".equals(address) || "127.0.0.1/32".equals(address);
    }

    static void role(Set<String> elevatedAttributes) {
        require(elevatedAttributes.isEmpty(), "Elevated role attributes or inherited roles rejected");
    }

    static void history(List<History> expected, List<History> actual) {
        require(expected.size() == 5 && expected.equals(actual), "Expected exactly five successful V2 SQL migrations");
        for (int i = 0; i < expected.size(); i++) {
            History row = actual.get(i);
            require(row.rank() == i + 1 && Integer.toString(i + 1).equals(row.version())
                    && "SQL".equals(row.type()) && row.success() && row.checksum() != null,
                    "Malformed V2 migration history rejected");
        }
    }

    static void empty(Set<String> userObjects) {
        require(userObjects.isEmpty(), "Database without history contains user objects");
    }

    static void emptyBusinessData(Set<String> expectedTables, Map<String, Boolean> populated) {
        require(expectedTables.size() == 24 && expectedTables.equals(populated.keySet())
                        && populated.values().stream().allMatch(Boolean.FALSE::equals),
                "Deployment requires all 24 V2 business tables to contain no data");
    }

    static void catalog(Map<String, String> expected, Map<String, String> actual) {
        require(expected.equals(actual), "V2 catalog names or signatures differ from frozen sources");
    }

    enum TestStep { MIGRATION, VALIDATION, CATALOG, ACCEPTANCE_ROLLBACK, NEGATIVE_ROLLBACK, CONCURRENCY, RERUN }

    /** A later stage cannot conceal a skipped or failed earlier TEST stage. */
    static final class TestGate {
        private int completed;
        void pass(TestStep step) {
            require(step.ordinal() == completed, "TEST gates must pass in order");
            completed++;
        }
        void requirePass() {
            require(completed == TestStep.values().length, "All TEST gates must PASS before any DEV connection");
        }
    }

    static void require(boolean condition, String message) {
        if (!condition) { throw new IllegalStateException(message); }
    }
}
