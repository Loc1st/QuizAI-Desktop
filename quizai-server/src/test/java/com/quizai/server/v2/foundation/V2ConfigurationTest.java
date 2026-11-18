package com.quizai.server.v2.foundation;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class V2ConfigurationTest {
    private MockEnvironment environment(String... profiles) {
        var environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return environment.withProperty("spring.datasource.url", "jdbc:postgresql://127.0.0.1:5432/quizai_v2_dev")
                .withProperty("spring.datasource.username", "quizai_v2_dev")
                .withProperty("spring.datasource.password", "test-only-no-real-secret")
                .withProperty("spring.flyway.enabled", "false").withProperty("spring.sql.init.mode", "never")
                .withProperty("quizai.classes.join-code-key", "unit-test-key-at-least-thirty-two-bytes");
    }
    @Test void rejectsMixedAndUnselectedProfilesBeforeAnyDatasourceIsBuilt() {
        for (String[] profiles : new String[][]{{}, {"v2-dev", "dev"}, {"v2-test", "test"}, {"v2-dev", "v2-test"}}) {
            assertThrows(IllegalStateException.class, () -> V2Configuration.profile(environment(profiles)));
        }
        assertEquals("v2-dev", V2Configuration.profile(environment("v2-dev")));
    }
    @Test void rejectsAutomaticMigrationOrSqlInitializationBeforeJdbc() {
        assertThrows(IllegalStateException.class, () -> V2Configuration.v2TargetGuard(environment("v2-dev").withProperty("spring.flyway.enabled", "true")));
        assertThrows(IllegalStateException.class, () -> V2Configuration.v2TargetGuard(environment("v2-dev").withProperty("spring.sql.init.mode", "always")));
        assertDoesNotThrow(() -> V2Configuration.v2TargetGuard(environment("v2-dev")));
    }
    @Test void rejectsMissingPersistentJoinKeyAndLegacyTargetWithoutDatabaseAccess() {
        assertThrows(IllegalStateException.class, () -> V2Configuration.v2TargetGuard(environment("v2-dev").withProperty("quizai.classes.join-code-key", "")));
        assertThrows(IllegalStateException.class, () -> V2Configuration.v2TargetGuard(environment("v2-dev").withProperty("spring.datasource.url", "jdbc:postgresql://127.0.0.1:5432/quizai_dev")));
    }
    @Test void approvedSchemaContainsExactly24DistinctBusinessTables() {
        assertEquals(24, V2Readiness.TABLES.size());
        assertTrue(V2Readiness.TABLES.containsAll(java.util.Set.of("attempts", "assignment_questions", "practice_answers")));
        assertFalse(V2Readiness.TABLES.contains("flyway_schema_history"));
    }
}
