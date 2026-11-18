package com.quizai.server.identity;

import java.time.Instant;
import java.util.UUID;

/** JDBC row mapping of the approved users table; never serialized by controllers. */
public record UserRecord(UUID id, String email, String passwordHash, String role,
                         String status, Instant createdAt, Instant updatedAt) {
    @Override
    public String toString() {
        return "UserRecord[id=" + id + ", role=" + role + ", status=" + status + "]";
    }
}
