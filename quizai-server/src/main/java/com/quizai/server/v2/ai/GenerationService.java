package com.quizai.server.v2.ai;

import com.quizai.server.security.SessionPrincipal;
import com.quizai.server.web.ApiException;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Profile({"v2-dev", "v2-test"})
public class GenerationService {
    private final JdbcTemplate jdbc;
    private final DocumentService documents;
    private final AssignedExamAiGuard guard;
    private final GeminiClient gemini;

    public GenerationService(JdbcTemplate jdbc, DocumentService documents, AssignedExamAiGuard guard, GeminiClient gemini) {
        this.jdbc = jdbc; this.documents = documents; this.guard = guard; this.gemini = gemini;
    }

    @Transactional(timeout = 35, isolation = Isolation.READ_COMMITTED)
    public StudyDtos.Generated generate(SessionPrincipal actor, StudyDtos.Generate request) {
        StudyAccess.authenticated(actor);
        if (request == null || request.documentId() == null || request.count() < 1 || request.count() > 20) {
            throw new ApiException(400, "Choose an owned document and 1 to 20 questions");
        }
        String title = StudyAccess.text(request.title(), 200, "Quiz title");
        String difficulty = request.difficulty() == null ? "MEDIUM" : request.difficulty();
        if (!Set.of("EASY", "MEDIUM", "HARD").contains(difficulty)) throw new ApiException(400, "Invalid difficulty");
        if ("STUDENT".equals(actor.role()) && request.bankId() != null) throw new ApiException(403, "Student questions are private");
        if ("TEACHER".equals(actor.role()) && request.bankId() == null) throw new ApiException(400, "Select an owned teacher question bank");
        var source = documents.ownedSource(actor, request.documentId());
        guard.lockAndCheck(actor);
        if (request.bankId() != null && !exists("SELECT EXISTS(SELECT 1 FROM question_banks WHERE id=? AND owner_teacher_id=? AND archived_at IS NULL)", request.bankId(), actor.userId())) {
            throw StudyAccess.missing();
        }
        if (request.subjectId() != null && !exists("SELECT EXISTS(SELECT 1 FROM subjects WHERE id=? AND active)", request.subjectId())) throw new ApiException(400, "Invalid subject");
        if (request.topicId() != null && (request.subjectId() == null || !exists("SELECT EXISTS(SELECT 1 FROM topics WHERE id=? AND subject_id=? AND active)", request.topicId(), request.subjectId()))) {
            throw new ApiException(400, "Topic must belong to the selected subject");
        }
        String prompt = "Generate exactly " + request.count() + " multiple choice study questions at " + difficulty + " difficulty from the document below. "
                + "Use the document language. Every question needs 2 to 6 distinct options and exactly one correct=true. "
                + "Return a JSON object ONLY in this exact shape: {\"questions\":[{\"stem\":\"question\",\"options\":[{\"text\":\"option\",\"correct\":true},{\"text\":\"option\",\"correct\":false}]}]}. "
                + "Do not add explanations, markdown or other fields. Document data follows:\n<document>\n" + documents.text(actor, source) + "\n</document>";
        var questions = McqValidator.parse(gemini.generate(prompt, true), request.count());
        UUID quizId = UUID.randomUUID();
        jdbc.update("INSERT INTO quizzes(id,owner_id,kind,title) VALUES(?,?,?,?)", quizId, actor.userId(),
                "STUDENT".equals(actor.role()) ? "PRACTICE" : "TEACHER_QUIZ", title);
        var versionIds = new ArrayList<UUID>();
        int position = 0;
        for (var question : questions) {
            UUID questionId = UUID.randomUUID(), versionId = UUID.randomUUID();
            jdbc.update("INSERT INTO questions(id,owner_id,bank_id,source_document_id,origin) VALUES(?,?,?,?,'AI')", questionId, actor.userId(), request.bankId(), source.id());
            jdbc.update("INSERT INTO question_versions(id,question_id,version_no,stem,subject_id,topic_id,difficulty) VALUES(?,?,1,?,?,?,?)",
                    versionId, questionId, question.stem(), request.subjectId(), request.topicId(), difficulty);
            int optionPosition = 0;
            for (var option : question.options()) jdbc.update("INSERT INTO question_options(question_version_id,position,option_text,is_correct) VALUES(?,?,?,?)",
                    versionId, ++optionPosition, option.text(), option.correct());
            // Student practice can use usable drafts. Teacher versions remain DRAFT until the
            // separate owner-teacher review endpoint approves each version for assignment.
            jdbc.update("INSERT INTO quiz_questions(quiz_id,question_version_id,position) VALUES(?,?,?)", quizId, versionId, ++position);
            versionIds.add(versionId);
        }
        return new StudyDtos.Generated(quizId, List.copyOf(versionIds));
    }

    private boolean exists(String query, Object... args) { return Boolean.TRUE.equals(jdbc.queryForObject(query, Boolean.class, args)); }
}
