package com.quizai.server.v2.ai;

import com.quizai.server.security.SessionPrincipal;
import com.quizai.server.web.ApiException;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Component
@Profile({"v2-dev", "v2-test"})
public class AssignedExamAiGuard {
    private final JdbcTemplate jdbc;
    public AssignedExamAiGuard(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockAndCheck(SessionPrincipal actor) {
        StudyAccess.authenticated(actor);
        // The immutable V2 schema serializes all mutations on this lock. Keeping it through
        // the bounded (25s) provider call prevents an exam start racing the AI check. The
        // tradeoff is that other V2 writes can wait for that call; transaction cap is 35s.
        jdbc.execute("SELECT pg_advisory_xact_lock(81722024001)");
        var expired = jdbc.query("""
                SELECT a.id FROM attempts a JOIN assignment_recipients r ON r.id=a.recipient_id
                WHERE r.student_id=? AND a.status='IN_PROGRESS' AND a.deadline_at<=clock_timestamp()
                ORDER BY a.id
                """, (r,n) -> r.getObject(1, UUID.class), actor.userId());
        for (UUID attempt : expired) jdbc.queryForObject("SELECT id FROM quizai_v2_finalize_attempt(?,?,NULL,NULL)", UUID.class, actor.userId(), attempt);
        Boolean active = jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM attempts a JOIN assignment_recipients r ON r.id=a.recipient_id
                WHERE r.student_id=? AND a.status='IN_PROGRESS')
                """, Boolean.class, actor.userId());
        if (Boolean.TRUE.equals(active)) throw new ApiException(409, "AI assistance is disabled while an assigned exam is in progress");
    }
}
