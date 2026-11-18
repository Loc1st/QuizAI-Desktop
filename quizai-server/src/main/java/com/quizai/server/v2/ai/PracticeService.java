package com.quizai.server.v2.ai;

import com.quizai.server.security.SessionPrincipal;
import com.quizai.server.web.ApiException;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Profile({"v2-dev", "v2-test"})
public class PracticeService {
    private final JdbcTemplate jdbc;
    public PracticeService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public List<StudyDtos.PrivateQuiz> quizzes(SessionPrincipal actor) {
        StudyAccess.student(actor);
        return jdbc.query("""
                SELECT q.id,q.title,q.status,(SELECT count(*) FROM quiz_questions qq WHERE qq.quiz_id=q.id) question_count
                FROM quizzes q WHERE q.owner_id=? AND q.kind='PRACTICE' AND q.status<>'ARCHIVED'
                ORDER BY q.created_at DESC LIMIT 100
                """, (r,n) -> new StudyDtos.PrivateQuiz(r.getObject("id", UUID.class), r.getString("title"), r.getString("status"), r.getInt("question_count")), actor.userId());
    }

    @Transactional
    public StudyDtos.Practice start(SessionPrincipal actor, UUID quizId) {
        StudyAccess.student(actor);
        lock();
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM quizzes WHERE id=? AND owner_id=? AND kind='PRACTICE' AND status<>'ARCHIVED')", Boolean.class, quizId, actor.userId()))) throw StudyAccess.missing();
        var versions = jdbc.query("SELECT question_version_id FROM quiz_questions WHERE quiz_id=? ORDER BY position LIMIT 101",
                (r,n) -> r.getObject(1, UUID.class), quizId);
        if (versions.isEmpty() || versions.size() > 100) throw new ApiException(400, "Practice requires 1 to 100 questions");
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO practice_sessions(id,student_id,quiz_id) VALUES(?,?,?)", id, actor.userId(), quizId);
        int position = 0;
        for (UUID version : versions) jdbc.update("INSERT INTO practice_session_questions(practice_session_id,question_version_id,position) VALUES(?,?,?)", id, version, ++position);
        return get(actor, id);
    }

    @Transactional(readOnly = true)
    public List<StudyDtos.Practice> history(SessionPrincipal actor) {
        StudyAccess.student(actor);
        return jdbc.query("SELECT id FROM practice_sessions WHERE student_id=? ORDER BY started_at DESC LIMIT 50", (r,n) -> r.getObject(1, UUID.class), actor.userId())
                .stream().map(id -> summary(actor, id, false)).toList();
    }

    @Transactional(readOnly = true)
    public StudyDtos.Practice get(SessionPrincipal actor, UUID id) { return summary(actor, id, true); }

    @Transactional
    public StudyDtos.Practice answer(SessionPrincipal actor, UUID id, UUID questionId, StudyDtos.Answer request) {
        StudyAccess.student(actor);
        if (request == null) throw new ApiException(400, "Answer is required");
        lock();
        if (!"IN_PROGRESS".equals(ownedStatus(actor, id))) throw new ApiException(409, "Completed practice is immutable");
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM practice_session_questions WHERE id=? AND practice_session_id=?)", Boolean.class, questionId, id))) throw StudyAccess.missing();
        if (request.selectedOptionId() != null && !Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM question_options o JOIN practice_session_questions q ON q.question_version_id=o.question_version_id
                WHERE q.id=? AND o.id=?)
                """, Boolean.class, questionId, request.selectedOptionId()))) throw new ApiException(400, "Option does not belong to this practice question");
        jdbc.update("INSERT INTO practice_answers(session_question_id,selected_option_id) VALUES(?,?) ON CONFLICT(session_question_id) DO UPDATE SET selected_option_id=EXCLUDED.selected_option_id", questionId, request.selectedOptionId());
        return get(actor, id);
    }

    @Transactional
    public StudyDtos.Practice complete(SessionPrincipal actor, UUID id) {
        StudyAccess.student(actor);
        lock();
        if ("COMPLETED".equals(ownedStatus(actor, id))) return get(actor, id);
        jdbc.update("""
                UPDATE practice_sessions SET status='COMPLETED',finished_at=clock_timestamp(),
                total_questions=(SELECT count(*) FROM practice_session_questions WHERE practice_session_id=?),
                correct_count=(SELECT count(*) FROM practice_session_questions q JOIN practice_answers a ON a.session_question_id=q.id WHERE q.practice_session_id=? AND a.is_correct)
                WHERE id=? AND student_id=?
                """, id, id, id, actor.userId());
        return get(actor, id);
    }

    String ownedStatus(SessionPrincipal actor, UUID id) {
        StudyAccess.student(actor);
        return jdbc.query("SELECT status FROM practice_sessions WHERE id=? AND student_id=?", (r,n) -> r.getString(1), id, actor.userId())
                .stream().findFirst().orElseThrow(StudyAccess::missing);
    }

    String completedContext(SessionPrincipal actor, UUID id, UUID questionId) {
        if (!"COMPLETED".equals(ownedStatus(actor, id))) throw new ApiException(409, "Finish practice before requesting AI explanations");
        var practice = get(actor, id);
        var selected = practice.questions().stream().filter(q -> questionId == null || q.id().equals(questionId)).toList();
        if (selected.isEmpty()) throw StudyAccess.missing();
        var context = new StringBuilder();
        for (var question : selected) {
            context.append("Question: ").append(question.stem()).append('\n');
            for (var option : question.options()) context.append(option.text()).append(Boolean.TRUE.equals(option.correct()) ? " [correct]" : "").append(option.id().equals(question.selectedOptionId()) ? " [student selected]" : "").append('\n');
            if (context.length() > 100_000) throw new ApiException(413, "Select a single practice question for explanation");
        }
        return context.toString();
    }

    private StudyDtos.Practice summary(SessionPrincipal actor, UUID id, boolean includeQuestions) {
        StudyAccess.student(actor);
        var session = jdbc.query("SELECT * FROM practice_sessions WHERE id=? AND student_id=?", (r,n) -> new StudyDtos.Practice(
                r.getObject("id", UUID.class), r.getObject("quiz_id", UUID.class), r.getString("status"), r.getTimestamp("started_at").toInstant(),
                r.getTimestamp("finished_at") == null ? null : r.getTimestamp("finished_at").toInstant(),
                r.getObject("correct_count", Integer.class), r.getObject("total_questions", Integer.class), List.of()), id, actor.userId())
                .stream().findFirst().orElseThrow(StudyAccess::missing);
        if (!includeQuestions) return session;
        boolean completed = session.status().equals("COMPLETED");
        var questions = jdbc.query("""
                SELECT q.id,q.position,q.question_version_id,v.stem,a.selected_option_id FROM practice_session_questions q
                JOIN question_versions v ON v.id=q.question_version_id LEFT JOIN practice_answers a ON a.session_question_id=q.id
                WHERE q.practice_session_id=? ORDER BY q.position
                """, (r,n) -> {
            UUID version = r.getObject("question_version_id", UUID.class);
            // Correctness is projected only after completion; raw option rows never escape.
            var options = jdbc.query("SELECT id,position,option_text" + (completed ? ",is_correct" : "") + " FROM question_options WHERE question_version_id=? ORDER BY position",
                    (o,i) -> new StudyDtos.Option(o.getObject("id", UUID.class), o.getInt("position"), o.getString("option_text"), completed ? o.getBoolean("is_correct") : null), version);
            return new StudyDtos.PracticeQuestion(r.getObject("id", UUID.class), r.getInt("position"), r.getString("stem"), options, r.getObject("selected_option_id", UUID.class));
        }, id);
        return new StudyDtos.Practice(session.id(), session.quizId(), session.status(), session.startedAt(), session.finishedAt(), session.correctCount(), session.totalQuestions(), questions);
    }

    private void lock() { jdbc.execute("SELECT pg_advisory_xact_lock(81722024001)"); }
}
