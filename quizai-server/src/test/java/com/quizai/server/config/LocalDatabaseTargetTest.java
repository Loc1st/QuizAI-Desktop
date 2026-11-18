package com.quizai.server.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LocalDatabaseTargetTest {
    @Test
    void acceptsOnlyTheDedicatedLocalTargets() {
        assertDoesNotThrow(() -> LocalDatabaseTarget.validate("dev", "jdbc:postgresql://127.0.0.1:5432/quizai_dev", "quizai_dev", "synthetic"));
        assertDoesNotThrow(() -> LocalDatabaseTarget.validate("test", "jdbc:postgresql://localhost:5432/quizai_test", "quizai_test", "synthetic"));
    }

    @Test
    void refusesForbiddenDatabasesAndRemoteTargetsBeforeJdbcIsPossible() {
        for (String url : new String[]{"jdbc:postgresql://127.0.0.1:5432/postgres",
                "jdbc:postgresql://127.0.0.1:5432/quizai_practice", "jdbc:postgresql://127.0.0.1:5432/quizai_dev",
                "jdbc:postgresql://192.0.2.1:5432/quizai_test", "jdbc:postgresql://127.0.0.1:5433/quizai_test",
                "jdbc:postgresql://127.0.0.1:5432/quizai_test?password=secret", "jdbc:postgresql://127.0.0.1:5432"}) {
            var failure = assertThrows(IllegalStateException.class,
                    () -> LocalDatabaseTarget.validate("test", url, "quizai_test", "synthetic"));
            assertFalse(failure.getMessage().contains("password=secret"));
        }
    }

    @Test
    void refusesMissingCredentialsWrongLoginAndProduction() {
        assertThrows(IllegalStateException.class, () -> LocalDatabaseTarget.validate("test", null, null, null));
        assertThrows(IllegalStateException.class, () -> LocalDatabaseTarget.validate("test", "jdbc:postgresql://localhost:5432/quizai_test", "postgres", "synthetic"));
        assertThrows(IllegalStateException.class, () -> LocalDatabaseTarget.validate("test", "jdbc:postgresql://localhost:5432/quizai_test", "quizai_test", "${MISSING}"));
        assertThrows(IllegalStateException.class, () -> LocalDatabaseTarget.validate("production", "", "", ""));
    }
}
