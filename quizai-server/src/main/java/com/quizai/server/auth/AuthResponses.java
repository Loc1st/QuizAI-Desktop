package com.quizai.server.auth;

import com.quizai.server.security.SessionPrincipal;

import java.time.Instant;
import java.util.UUID;

public final class AuthResponses {
    private AuthResponses() { }

    public record User(UUID id, String email, String role) {
        public static User from(SessionPrincipal principal) {
            return new User(principal.userId(), principal.email(), principal.role());
        }
    }

    public record Login(String token, Instant expiresAt, User user) {
        @Override
        public String toString() {
            return "Login[token=[REDACTED], expiresAt=" + expiresAt + ", user=" + user + "]";
        }
    }

    public record Session(Instant expiresAt, User user) { }
}
