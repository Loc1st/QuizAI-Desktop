package com.quizai.server.security;

import java.time.Instant;
import java.util.UUID;

/** Safe authenticated identity. It contains no password or bearer token. */
public record SessionPrincipal(UUID userId, String email, String role,
                               UUID sessionId, Instant expiresAt) {
}
