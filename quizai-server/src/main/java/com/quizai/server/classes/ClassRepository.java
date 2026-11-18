package com.quizai.server.classes;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Legacy V1 mapping only, matching the existing dev/test identity repositories; not a V2 adapter. */
@Repository
@Profile({"dev", "test"})
public class ClassRepository {
    private static final String CLASS_COLUMNS = "id, teacher_id, name, join_code_digest, join_enabled, status, created_at";
    private static final RowMapper<Classroom> CLASS_ROW = (rs, row) -> new Classroom(
            rs.getObject("id", UUID.class), rs.getObject("teacher_id", UUID.class), rs.getString("name"),
            rs.getString("join_code_digest"), rs.getBoolean("join_enabled"), rs.getString("status"),
            rs.getTimestamp("created_at").toInstant());
    private static final RowMapper<MembershipEpisode> MEMBERSHIP_ROW = (rs, row) -> new MembershipEpisode(
            rs.getObject("id", UUID.class), rs.getObject("class_id", UUID.class),
            rs.getObject("student_id", UUID.class), rs.getTimestamp("joined_at").toInstant(),
            rs.getTimestamp("left_at") == null ? null : rs.getTimestamp("left_at").toInstant(),
            rs.getTimestamp("created_at").toInstant());
    private final JdbcTemplate jdbc;

    public ClassRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertClass(Classroom classroom) {
        jdbc.update("INSERT INTO classes (" + CLASS_COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?)",
                classroom.id(), classroom.teacherId(), classroom.name(), classroom.joinCodeDigest(),
                classroom.joinEnabled(), classroom.status(), Timestamp.from(classroom.createdAt()));
    }

    public List<Classroom> findOwnedClasses(UUID teacherId) {
        return jdbc.query("SELECT " + CLASS_COLUMNS + " FROM classes WHERE teacher_id = ? ORDER BY created_at, id",
                CLASS_ROW, teacherId);
    }

    public Optional<Classroom> findClass(UUID id) {
        return jdbc.query("SELECT " + CLASS_COLUMNS + " FROM classes WHERE id = ?", CLASS_ROW, id)
                .stream().findFirst();
    }

    public Optional<Classroom> findClassForUpdate(UUID id) {
        return jdbc.query("SELECT " + CLASS_COLUMNS + " FROM classes WHERE id = ? FOR UPDATE", CLASS_ROW, id)
                .stream().findFirst();
    }

    public Optional<Classroom> findJoinableClassForUpdate(String digest) {
        return jdbc.query("SELECT " + CLASS_COLUMNS + """
                 FROM classes WHERE join_code_digest = ? AND join_enabled IS TRUE AND status = 'ACTIVE' FOR UPDATE
                """, CLASS_ROW, digest).stream().findFirst();
    }

    public void updateDetails(UUID classId, String name, String status) {
        jdbc.update("UPDATE classes SET name = ?, status = ? WHERE id = ?", name, status, classId);
    }

    public void updateJoinCode(UUID classId, String digest, boolean enabled) {
        jdbc.update("UPDATE classes SET join_code_digest = ?, join_enabled = ? WHERE id = ?", digest, enabled, classId);
    }

    /** Transaction-scoped lock: code checks and inserts/rotations must share this lock. */
    public void lockJoinCodeGeneration() {
        jdbc.query("SELECT pg_advisory_xact_lock(716913042811421L::bigint)".replace("L::", "::"),
                rs -> { while (rs.next()) { /* Consume the void result. */ } return null; });
    }

    public boolean enabledDigestExists(String digest) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM classes WHERE join_code_digest = ? AND join_enabled IS TRUE)
                """, Boolean.class, digest));
    }

    public Optional<MembershipEpisode> findActiveMembership(UUID classId, UUID studentId) {
        return membership(classId, studentId, false);
    }

    public Optional<MembershipEpisode> findActiveMembershipForUpdate(UUID classId, UUID studentId) {
        return membership(classId, studentId, true);
    }

    private Optional<MembershipEpisode> membership(UUID classId, UUID studentId, boolean lock) {
        return jdbc.query("""
                SELECT id, class_id, student_id, joined_at, left_at, created_at FROM class_members
                WHERE class_id = ? AND student_id = ? AND left_at IS NULL
                """ + (lock ? " FOR UPDATE" : ""), MEMBERSHIP_ROW, classId, studentId).stream().findFirst();
    }

    public Optional<Instant> latestMembershipEnd(UUID classId, UUID studentId) {
        Timestamp end = jdbc.queryForObject("""
                SELECT max(left_at) FROM class_members WHERE class_id = ? AND student_id = ?
                """, Timestamp.class, classId, studentId);
        return Optional.ofNullable(end).map(Timestamp::toInstant);
    }

    public void insertMembership(MembershipEpisode episode) {
        jdbc.update("""
                INSERT INTO class_members (id, class_id, student_id, joined_at, left_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, episode.id(), episode.classId(), episode.studentId(), Timestamp.from(episode.joinedAt()),
                episode.leftAt() == null ? null : Timestamp.from(episode.leftAt()), Timestamp.from(episode.createdAt()));
    }

    public int closeMembership(UUID membershipId, Instant leftAt) {
        return jdbc.update("UPDATE class_members SET left_at = ? WHERE id = ? AND left_at IS NULL",
                Timestamp.from(leftAt), membershipId);
    }

    public boolean hasMembershipHistory(UUID classId, UUID studentId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM class_members WHERE class_id = ? AND student_id = ?)
                """, Boolean.class, classId, studentId));
    }

    public List<ClassMemberView> findActiveMembers(UUID classId) {
        return jdbc.query("""
                SELECT m.id, m.student_id, u.email, m.joined_at FROM class_members m
                JOIN users u ON u.id = m.student_id WHERE m.class_id = ? AND m.left_at IS NULL
                ORDER BY m.joined_at, m.id
                """, (rs, row) -> new ClassMemberView(rs.getObject("id", UUID.class),
                rs.getObject("student_id", UUID.class), rs.getString("email"),
                rs.getTimestamp("joined_at").toInstant()), classId);
    }
}
