package com.quizai.server.v2.foundation;

import com.quizai.server.identity.UserRecord;
import com.quizai.server.identity.UserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile({"v2-dev", "v2-test"})
public class V2UserRepository extends UserRepository {
    private final JdbcTemplate jdbc;
    public V2UserRepository(JdbcTemplate jdbc) { super(jdbc); this.jdbc = jdbc; }

    @Override public Optional<UserRecord> findByEmail(String email) {
        return jdbc.query("SELECT id,email_normalized,password_hash,role,status,created_at FROM users WHERE email_normalized=?",
                (rs, row) -> new UserRecord(rs.getObject("id", UUID.class), rs.getString("email_normalized"),
                        rs.getString("password_hash"), rs.getString("role"), rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("created_at").toInstant()), email)
                .stream().findFirst();
    }

    @Override public void insert(UserRecord user) {
        jdbc.update("INSERT INTO users(id,email_normalized,password_hash,role,status,created_at) VALUES (?,?,?,?,?,?)",
                user.id(), user.email(), user.passwordHash(), user.role(), user.status(), Timestamp.from(user.createdAt()));
    }
}
