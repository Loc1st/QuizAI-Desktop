package com.quizai.server.v2.foundation;

import com.quizai.server.identity.AuthSessionRecord;
import com.quizai.server.identity.AuthSessionRepository;
import com.quizai.server.security.SessionPrincipal;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile({"v2-dev", "v2-test"})
public class V2SessionRepository extends AuthSessionRepository {
    private final JdbcTemplate jdbc;
    public V2SessionRepository(JdbcTemplate jdbc) { super(jdbc); this.jdbc = jdbc; }

    @Override public void insert(AuthSessionRecord session) {
        jdbc.update("INSERT INTO auth_sessions(id,user_id,token_hash,created_at,expires_at) VALUES (?,?,?,?,?)",
                session.id(), session.userId(), session.sessionTokenHash(), Timestamp.from(session.createdAt()), Timestamp.from(session.expiresAt()));
    }

    @Override public Optional<SessionPrincipal> findActivePrincipal(String tokenHash, Instant now) {
        return jdbc.query("""
                SELECT u.id user_id,u.email_normalized,u.role,s.id session_id,s.expires_at
                FROM auth_sessions s JOIN users u ON u.id=s.user_id
                WHERE s.token_hash=? AND s.revoked_at IS NULL AND s.expires_at>?
                  AND u.status='ACTIVE' AND u.role IN ('STUDENT','TEACHER')
                """, (rs, row) -> new SessionPrincipal(rs.getObject("user_id", UUID.class), rs.getString("email_normalized"),
                rs.getString("role"), rs.getObject("session_id", UUID.class), rs.getTimestamp("expires_at").toInstant()),
                tokenHash, Timestamp.from(now)).stream().findFirst();
    }

    @Override public void revoke(UUID sessionId, UUID userId, Instant now) {
        jdbc.update("UPDATE auth_sessions SET revoked_at=? WHERE id=? AND user_id=? AND revoked_at IS NULL",
                Timestamp.from(now), sessionId, userId);
    }
}
