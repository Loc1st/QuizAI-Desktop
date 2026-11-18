package com.quizai.server.v2.exams;

import com.quizai.server.security.SessionPrincipal;
import com.quizai.server.web.ApiException;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
@Profile({"v2-dev", "v2-test"})
public class ExamStore {
    private final JdbcTemplate jdbc;

    public ExamStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // Call through the Spring repository proxy; its instance fields are not the target's fields.
    public JdbcTemplate jdbc() { return jdbc; }

    void lock() {
        // Acquire the same serialization lock as V5 BEFORE taking any row lock.
        jdbc.query("SELECT pg_advisory_xact_lock(81722024001)", rs -> { while (rs.next()) {} return null; });
    }

    Instant now() { return jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class).toInstant(); }

    Map<String, Object> one(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        if (rows.isEmpty()) throw new ApiException(404, "Resource not found");
        return rows.getFirst();
    }

    void exists(String sql, Object... args) { one(sql, args); }

    void audit(UUID actor, String type, String resource, UUID id) {
        jdbc.update("INSERT INTO audit_events(actor_id,event_type,resource_type,resource_id,outcome) VALUES (?,?,?,?,'SUCCESS')",
                actor, type, resource, id);
    }

    static void role(SessionPrincipal principal, String role) {
        if (principal == null) throw new ApiException(401, "Authentication required");
        if (!role.equals(principal.role())) throw new ApiException(403, "Access denied");
    }

    static String text(String value, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max)
            throw new ApiException(400, "Invalid text value");
        return value.trim();
    }

    static Timestamp time(Instant value) { return value == null ? null : Timestamp.from(value); }
    static UUID id(Map<String, Object> row, String key) { return (UUID) row.get(key); }
    static String str(Map<String, Object> row, String key) { return (String) row.get(key); }
    static int integer(Map<String, Object> row, String key) { return ((Number) row.get(key)).intValue(); }
    static Instant instant(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : ((Timestamp) value).toInstant();
    }
    static Instant instant(ResultSet rs, String key) throws SQLException {
        Timestamp value = rs.getTimestamp(key);
        return value == null ? null : value.toInstant();
    }

    ExamDtos.Assignment assignment(Map<String, Object> r) {
        return new ExamDtos.Assignment(id(r,"id"), id(r,"class_id"), id(r,"quiz_id"), str(r,"title"),
                instant(r,"opens_at"), instant(r,"closes_at"), integer(r,"duration_minutes"), integer(r,"max_attempts"),
                str(r,"status"), instant(r,"score_release_at"), instant(r,"answer_release_at"), str(r,"answer_release_mode"));
    }

    /** Called with the global lock in a read-committed write transaction. */
    void finalizeExpired(UUID studentId, UUID assignmentId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT a.id, r.student_id FROM attempts a JOIN assignment_recipients r ON r.id=a.recipient_id
                WHERE a.status='IN_PROGRESS' AND a.deadline_at<=clock_timestamp()
                """ + (studentId == null ? "" : " AND r.student_id=?")
                + (assignmentId == null ? "" : " AND a.assignment_id=?"),
                studentId != null && assignmentId != null ? new Object[]{studentId, assignmentId}
                        : studentId != null ? new Object[]{studentId} : assignmentId != null ? new Object[]{assignmentId} : new Object[0]);
        for (Map<String,Object> row : rows) {
            jdbc.queryForList("SELECT * FROM quizai_v2_finalize_attempt(?,?,NULL,NULL)", id(row,"student_id"), id(row,"id"));
            audit(null, "ATTEMPT_TIMED_OUT", "ATTEMPT", id(row,"id"));
        }
    }
}
