package com.quizai.server.catalog;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Legacy foundation mapping, matching the existing dev/test identity repositories and V1 contract. */
@Repository
@Profile({"dev", "test"})
public class CatalogRepository {
    private final JdbcTemplate jdbc;

    public CatalogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<SubjectView> findActiveSubjects() {
        return jdbc.query("""
                SELECT id, code, name, catalog_version FROM subjects WHERE active IS TRUE ORDER BY code, id
                """, (rs, row) -> new SubjectView(rs.getObject("id", UUID.class), rs.getString("code"),
                rs.getString("name"), rs.getInt("catalog_version")));
    }

    public boolean activeSubjectExists(UUID subjectId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM subjects WHERE id = ? AND active IS TRUE)", Boolean.class, subjectId));
    }

    public List<TopicView> findActiveTopics(UUID subjectId) {
        return jdbc.query("""
                SELECT t.id, t.subject_id, t.code, t.name FROM topics t
                JOIN subjects s ON s.id = t.subject_id
                WHERE t.subject_id = ? AND t.active IS TRUE AND s.active IS TRUE ORDER BY t.code, t.id
                """, (rs, row) -> new TopicView(rs.getObject("id", UUID.class),
                rs.getObject("subject_id", UUID.class), rs.getString("code"), rs.getString("name")), subjectId);
    }
}
