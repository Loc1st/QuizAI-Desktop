package com.quizai.server.v2.foundation;

import com.quizai.server.classes.*;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile({"v2-dev", "v2-test"})
public class V2ClassRepository extends ClassRepository {
    private final JdbcTemplate jdbc;
    private static final String COLUMNS = "id,teacher_id,name,join_code_hmac,join_enabled,created_at";
    private static final RowMapper<Classroom> CLASS = (rs, row) -> new Classroom(rs.getObject("id", UUID.class),
            rs.getObject("teacher_id", UUID.class), rs.getString("name"), rs.getString("join_code_hmac"),
            rs.getBoolean("join_enabled"), "ACTIVE", rs.getTimestamp("created_at").toInstant());
    private static final RowMapper<MembershipEpisode> MEMBER = (rs, row) -> new MembershipEpisode(rs.getObject("id", UUID.class),
            rs.getObject("class_id", UUID.class), rs.getObject("student_id", UUID.class), rs.getTimestamp("joined_at").toInstant(),
            rs.getTimestamp("left_at") == null ? null : rs.getTimestamp("left_at").toInstant(), rs.getTimestamp("joined_at").toInstant());

    public V2ClassRepository(JdbcTemplate jdbc) { super(jdbc); this.jdbc = jdbc; }

    private void lock() {
        jdbc.query("SELECT pg_advisory_xact_lock(81722024001)", rs -> { while (rs.next()) { } return null; });
    }
    @Override public void lockJoinCodeGeneration() { lock(); }
    @Override public void insertClass(Classroom c) {
        jdbc.update("INSERT INTO classes(" + COLUMNS + ") VALUES (?,?,?,?,?,?)", c.id(), c.teacherId(), c.name(),
                c.joinCodeDigest(), c.joinEnabled(), Timestamp.from(c.createdAt()));
    }
    @Override public List<Classroom> findOwnedClasses(UUID teacherId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM classes WHERE teacher_id=? ORDER BY created_at,id", CLASS, teacherId);
    }
    @Override public Optional<Classroom> findClass(UUID id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM classes WHERE id=?", CLASS, id).stream().findFirst();
    }
    @Override public Optional<Classroom> findClassForUpdate(UUID id) {
        lock();
        return jdbc.query("SELECT " + COLUMNS + " FROM classes WHERE id=? FOR UPDATE", CLASS, id).stream().findFirst();
    }
    @Override public Optional<Classroom> findJoinableClassForUpdate(String digest) {
        lock();
        return jdbc.query("SELECT " + COLUMNS + " FROM classes WHERE join_code_hmac=? AND join_enabled FOR UPDATE", CLASS, digest).stream().findFirst();
    }
    @Override public void updateDetails(UUID id, String name, String status) {
        if (!"ACTIVE".equals(status)) throw new com.quizai.server.web.ApiException(400, "Archiving is not part of V2 class state");
        jdbc.update("UPDATE classes SET name=? WHERE id=?", name, id);
    }
    @Override public void updateJoinCode(UUID id, String digest, boolean enabled) {
        jdbc.update("UPDATE classes SET join_code_hmac=?,join_enabled=? WHERE id=?", digest, enabled, id);
    }
    @Override public boolean enabledDigestExists(String digest) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM classes WHERE join_code_hmac=? AND join_enabled)", Boolean.class, digest));
    }
    @Override public Optional<MembershipEpisode> findActiveMembership(UUID classId, UUID studentId) { return member(classId, studentId, false); }
    @Override public Optional<MembershipEpisode> findActiveMembershipForUpdate(UUID classId, UUID studentId) {
        lock(); return member(classId, studentId, true);
    }
    private Optional<MembershipEpisode> member(UUID classId, UUID studentId, boolean locked) {
        return jdbc.query("SELECT id,class_id,student_id,joined_at,left_at FROM class_members WHERE class_id=? AND student_id=? AND left_at IS NULL"
                + (locked ? " FOR UPDATE" : ""), MEMBER, classId, studentId).stream().findFirst();
    }
    @Override public Optional<Instant> latestMembershipEnd(UUID classId, UUID studentId) {
        Timestamp last = jdbc.queryForObject("SELECT max(left_at) FROM class_members WHERE class_id=? AND student_id=?", Timestamp.class, classId, studentId);
        return Optional.ofNullable(last).map(Timestamp::toInstant);
    }
    @Override public void insertMembership(MembershipEpisode m) {
        jdbc.update("INSERT INTO class_members(id,class_id,student_id,joined_at) VALUES (?,?,?,?)", m.id(), m.classId(), m.studentId(), Timestamp.from(m.joinedAt()));
    }
    @Override public int closeMembership(UUID membershipId, Instant leftAt) {
        return jdbc.update("UPDATE class_members SET left_at=? WHERE id=? AND left_at IS NULL", Timestamp.from(leftAt), membershipId);
    }
    @Override public boolean hasMembershipHistory(UUID classId, UUID studentId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM class_members WHERE class_id=? AND student_id=?)", Boolean.class, classId, studentId));
    }
    @Override public List<ClassMemberView> findActiveMembers(UUID classId) {
        return jdbc.query("""
                SELECT m.id,m.student_id,u.email_normalized,m.joined_at FROM class_members m JOIN users u ON u.id=m.student_id
                WHERE m.class_id=? AND m.left_at IS NULL ORDER BY m.joined_at,m.id
                """, (rs, row) -> new ClassMemberView(rs.getObject("id", UUID.class), rs.getObject("student_id", UUID.class),
                rs.getString("email_normalized"), rs.getTimestamp("joined_at").toInstant()), classId);
    }
    public List<JoinedClassView> joined(UUID studentId) {
        return jdbc.query("""
                SELECT c.id,c.teacher_id,c.name,c.join_enabled,m.id membership_id,m.joined_at
                FROM classes c JOIN class_members m ON m.class_id=c.id WHERE m.student_id=? AND m.left_at IS NULL
                ORDER BY m.joined_at,c.id
                """, (rs, row) -> new JoinedClassView(rs.getObject("id", UUID.class), rs.getObject("teacher_id", UUID.class),
                rs.getString("name"), "ACTIVE", rs.getBoolean("join_enabled"), rs.getObject("membership_id", UUID.class),
                rs.getTimestamp("joined_at").toInstant()), studentId);
    }
}
