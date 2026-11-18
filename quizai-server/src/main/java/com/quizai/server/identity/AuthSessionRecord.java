package com.quizai.server.identity;

import java.time.Instant;
import java.util.UUID;

/** JDBC row mapping of auth_sessions. Only a SHA-256 token digest is persisted. */
public record AuthSessionRecord(UUID id, UUID userId, String sessionTokenHash,
                                String deviceSessionId, Instant createdAt, Instant expiresAt,
                                Instant revokedAt, Instant lastSeenAt) {
    @Override
    public String toString() {
        return "AuthSessionRecord[id=" + id + ", userId=" + userId + ", expiresAt=" + expiresAt + "]";
    }
}
