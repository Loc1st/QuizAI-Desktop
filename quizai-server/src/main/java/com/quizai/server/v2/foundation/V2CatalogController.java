package com.quizai.server.v2.foundation;

import com.quizai.server.catalog.TopicView;
import com.quizai.server.security.SessionPrincipal;
import com.quizai.server.web.ApiException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

/** Shared subject taxonomy: authenticated reading, teacher creation, no global edit/delete privilege. */
@RestController
@RequestMapping("/api/catalog/subjects")
@Profile({"v2-dev", "v2-test"})
@Transactional(readOnly = true)
public class V2CatalogController {
    private final JdbcTemplate jdbc;
    public V2CatalogController(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record Subject(UUID id, String code, String name) { }
    public record Create(String code, String name) { }

    @GetMapping public List<Subject> subjects() {
        return jdbc.query("SELECT id,code,name FROM subjects WHERE active ORDER BY code,id",
                (rs, row) -> new Subject(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name")));
    }
    @GetMapping("/{id}/topics") public List<TopicView> topics(@PathVariable UUID id) {
        requireSubject(id);
        return jdbc.query("SELECT id,subject_id,code,name FROM topics WHERE subject_id=? AND active ORDER BY code,id",
                (rs, row) -> new TopicView(rs.getObject("id", UUID.class), rs.getObject("subject_id", UUID.class),
                        rs.getString("code"), rs.getString("name")), id);
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @Transactional
    public Subject create(@AuthenticationPrincipal SessionPrincipal actor, @RequestBody Create request) {
        teacher(actor);
        String code = text(request.code(), 64), name = text(request.name(), 200);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO subjects(id,code,name) VALUES (?,?,?)", id, code, name);
        return new Subject(id, code, name);
    }
    @PostMapping("/{subjectId}/topics") @ResponseStatus(HttpStatus.CREATED) @Transactional
    public TopicView topic(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID subjectId, @RequestBody Create request) {
        teacher(actor); requireSubject(subjectId);
        UUID id = UUID.randomUUID();
        String code = text(request.code(), 64), name = text(request.name(), 200);
        jdbc.update("INSERT INTO topics(id,subject_id,code,name) VALUES (?,?,?,?)", id, subjectId, code, name);
        return new TopicView(id, subjectId, code, name);
    }
    private void requireSubject(UUID id) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM subjects WHERE id=? AND active)", Boolean.class, id)))
            throw new ApiException(404, "Subject not found");
    }
    private static void teacher(SessionPrincipal actor) {
        if (actor == null) throw new ApiException(401, "Authentication required");
        if (!"TEACHER".equals(actor.role())) throw new ApiException(403, "Access denied");
    }
    private static String text(String value, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max) throw new ApiException(400, "Invalid catalog data");
        return value.trim();
    }
}
