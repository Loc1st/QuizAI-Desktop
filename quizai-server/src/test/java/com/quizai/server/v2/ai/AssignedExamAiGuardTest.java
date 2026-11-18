package com.quizai.server.v2.ai;

import com.quizai.server.security.SessionPrincipal;
import com.quizai.server.web.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** JDBC boundary fixture only; real SQL locking/finalization needs the opt-in V2 TEST DB. */
class AssignedExamAiGuardTest {
    @Test void expiresViaGuardedFunctionBeforeAllowingAnyAiRequest() {
        var jdbc = new GuardJdbc(false);
        new AssignedExamAiGuard(jdbc).lockAndCheck(student());
        assertEquals(List.of("lock", "expired", "finalize", "active"), jdbc.events);
        assertEquals(jdbc.expired, jdbc.finalized);
    }

    @Test void activeAssignedExamBlocksGenerationBeforeProviderInvocation() {
        var jdbc = new GuardJdbc(true);
        var actor = student();
        var documents = new DocumentService(jdbc, null, null) {
            @Override Source ownedSource(SessionPrincipal ignored, UUID id) { return new Source(id, "TXT", "unused"); }
        };
        var gemini = new GeminiClient(null, null, "unused", "test", java.time.Duration.ofSeconds(1)) {
            @Override String generate(String prompt, boolean mcq) { throw new AssertionError("Provider must never be called during an assigned exam"); }
        };
        var service = new GenerationService(jdbc, documents, new AssignedExamAiGuard(jdbc), gemini);
        assertEquals(409, assertThrows(ApiException.class, () -> service.generate(actor,
                new StudyDtos.Generate(UUID.randomUUID(), "Quiz", 1, null, null, null, "MEDIUM"))).status());
    }

    @Test void teacherCannotReadStudentPracticeOrTutorHistory() {
        var teacher = new SessionPrincipal(UUID.randomUUID(), "teacher@example.test", "TEACHER", UUID.randomUUID(), Instant.now().plusSeconds(60));
        assertEquals(403, assertThrows(ApiException.class, () -> new PracticeService(null).get(teacher, UUID.randomUUID())).status());
        assertEquals(403, assertThrows(ApiException.class, () -> new TutorService(null, null, null, null, null).messages(teacher, UUID.randomUUID())).status());
        assertEquals(401, assertThrows(ApiException.class, () -> new AssignedExamAiGuard(null).lockAndCheck(null)).status());
    }

    @Test void teacherGenerationRequiresBankBeforeAnyProviderOrDatabaseAccess() {
        var teacher = new SessionPrincipal(UUID.randomUUID(), "teacher@example.test", "TEACHER", UUID.randomUUID(), Instant.now().plusSeconds(60));
        var service = new GenerationService(null, null, null, null);
        assertEquals(400, assertThrows(ApiException.class, () -> service.generate(teacher,
                new StudyDtos.Generate(UUID.randomUUID(), "Quiz", 1, null, null, null, "MEDIUM"))).status());
    }

    @Test void foreignDocumentAndConversationAreRejectedBeforeProviderInvocation() {
        var jdbc = new GuardJdbc(false) {
            @Override public <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) {
                if (sql.contains("FROM attempts")) return super.query(sql, mapper, args);
                return List.of(); // No resource exists under this actor's owner predicate.
            }
        };
        var gemini = new GeminiClient(null, null, "unused", "test", java.time.Duration.ofSeconds(1)) {
            @Override String generate(String prompt, boolean mcq) { throw new AssertionError("Foreign context must not reach Gemini"); }
        };
        var documents = new DocumentService(jdbc, null, null);
        var guard = new AssignedExamAiGuard(jdbc);
        var generation = new GenerationService(jdbc, documents, guard, gemini);
        assertEquals(404, assertThrows(ApiException.class, () -> generation.generate(student(),
                new StudyDtos.Generate(UUID.randomUUID(), "Quiz", 1, null, null, null, "MEDIUM"))).status());
        var tutor = new TutorService(jdbc, documents, new PracticeService(jdbc), guard, gemini);
        assertEquals(404, assertThrows(ApiException.class, () -> tutor.message(student(), UUID.randomUUID(), new StudyDtos.MessageRequest("Explain", null))).status());
    }

    @Test void malformedProviderOutputIsRejectedBeforeAnyQuestionWrite() {
        var jdbc = new GuardJdbc(false) {
            @Override public int update(String sql, Object... args) { throw new AssertionError("Validate provider output before writing questions"); }
        };
        var documents = new DocumentService(jdbc, null, null) {
            @Override Source ownedSource(SessionPrincipal ignored, UUID id) { return new Source(id, "TXT", "unused"); }
            @Override String text(SessionPrincipal ignored, Source source) { return "Java class"; }
        };
        var gemini = new GeminiClient(null, null, "unused", "test", java.time.Duration.ofSeconds(1)) {
            @Override String generate(String prompt, boolean mcq) { return "{\"questions\":[]}"; }
        };
        var generation = new GenerationService(jdbc, documents, new AssignedExamAiGuard(jdbc), gemini);
        assertEquals(502, assertThrows(ApiException.class, () -> generation.generate(student(),
                new StudyDtos.Generate(UUID.randomUUID(), "Quiz", 1, null, null, null, "MEDIUM"))).status());
    }

    private static SessionPrincipal student() { return new SessionPrincipal(UUID.randomUUID(), "student@example.test", "STUDENT", UUID.randomUUID(), Instant.now().plusSeconds(60)); }

    private static class GuardJdbc extends JdbcTemplate {
        final boolean active;
        final UUID expired = UUID.randomUUID();
        UUID finalized;
        final List<String> events = new ArrayList<>();
        GuardJdbc(boolean active) { this.active = active; }
        @Override public void execute(String sql) { events.add("lock"); assertTrue(sql.contains("pg_advisory_xact_lock(81722024001)")); }
        @Override @SuppressWarnings("unchecked") public <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) {
            events.add("expired"); assertTrue(sql.contains("deadline_at<=clock_timestamp()")); return (List<T>) List.of(expired);
        }
        @Override @SuppressWarnings("unchecked") public <T> T queryForObject(String sql, Class<T> type, Object... args) {
            if (type == UUID.class) { events.add("finalize"); assertTrue(sql.contains("quizai_v2_finalize_attempt(?,?,NULL,NULL)")); finalized = (UUID) args[1]; return (T) finalized; }
            events.add("active"); return (T) Boolean.valueOf(active);
        }
    }
}
