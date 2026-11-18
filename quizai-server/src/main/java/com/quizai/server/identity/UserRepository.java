package com.quizai.server.identity;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Optional;

@Repository
@Profile({"dev", "test"})
public class UserRepository {
    private static final RowMapper<UserRecord> ROW = (rs, row) -> new UserRecord(
            rs.getObject("id", java.util.UUID.class), rs.getString("email"),
            rs.getString("password_hash"), rs.getString("role"), rs.getString("status"),
            rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserRecord> findByEmail(String normalizedEmail) {
        return jdbc.query("""
                SELECT id, email, password_hash, role, status, created_at, updated_at
                FROM users WHERE email = ?
                """, ROW, normalizedEmail).stream().findFirst();
    }

    public void insert(UserRecord user) {
        jdbc.update("""
                INSERT INTO users (id, email, password_hash, role, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, user.id(), user.email(), user.passwordHash(), user.role(), user.status(),
                Timestamp.from(user.createdAt()), Timestamp.from(user.updatedAt()));
    }
}
