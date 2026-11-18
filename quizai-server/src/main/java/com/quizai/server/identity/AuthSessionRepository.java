package com.quizai.server.identity;

import com.quizai.server.security.SessionPrincipal;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile({"dev", "test"})
public class AuthSessionRepository {
    private final JdbcTemplate jdbc;

    public AuthSessionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(AuthSessionRecord session) {
        jdbc.update("""
                INSERT INTO auth_sessions
                    (id, user_id, session_token_hash, device_session_id, created_at, expires_at, revoked_at, last_seen_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, session.id(), session.userId(), session.sessionTokenHash(), session.deviceSessionId(),
                Timestamp.from(session.createdAt()), Timestamp.from(session.expiresAt()),
                timestamp(session.revokedAt()), timestamp(session.lastSeenAt()));
    }

    /** The database checks session revocation/expiry and current account status on every request. */
    public Optional<SessionPrincipal> findActivePrincipal(String tokenHash, Instant now) {
        return jdbc.query("""
                SELECT u.id AS user_id, u.email, u.role, s.id AS session_id, s.expires_at
                FROM auth_sessions s JOIN users u ON u.id = s.user_id
                WHERE s.session_token_hash = ? AND s.revoked_at IS NULL
                    AND s.expires_at > ? AND u.status = 'ACTIVE'
                    AND u.role IN ('STUDENT', 'TEACHER')
                """, (rs, row) -> new SessionPrincipal(rs.getObject("user_id", UUID.class),
                rs.getString("email"), rs.getString("role"), rs.getObject("session_id", UUID.class),
                rs.getTimestamp("expires_at").toInstant()), tokenHash, Timestamp.from(now))
                .stream().findFirst();
    }

    public void revoke(UUID sessionId, UUID userId, Instant now) {
        jdbc.update("""
                UPDATE auth_sessions SET revoked_at = ?
                WHERE id = ? AND user_id = ? AND revoked_at IS NULL
                """, Timestamp.from(now), sessionId, userId);
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
