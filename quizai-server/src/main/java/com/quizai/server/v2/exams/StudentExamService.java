package com.quizai.server.v2.exams;

import com.quizai.server.security.SessionPrincipal;
import com.quizai.server.web.ApiException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.quizai.server.v2.exams.ExamDtos.*;
import static com.quizai.server.v2.exams.ExamStore.*;

@Service
@Profile({"v2-dev", "v2-test"})
@Transactional(isolation = Isolation.READ_COMMITTED)
public class StudentExamService {
    private final ExamStore db;
    public StudentExamService(ExamStore db) { this.db=db; }

    public List<StudentAssignment> assignments(SessionPrincipal p) {
        role(p,"STUDENT");
        db.lock();
        db.finalizeExpired(p.userId(),null);
        Instant now=db.now();
        return db.jdbc().queryForList("""
                SELECT a.*,r.id AS recipient_id,m.left_at,
                    (SELECT count(*) FROM attempts x WHERE x.recipient_id=r.id) AS attempts_used,
                    (SELECT x.id FROM attempts x WHERE x.recipient_id=r.id AND x.status='IN_PROGRESS' ORDER BY x.attempt_no LIMIT 1) AS active_attempt_id,
                    (SELECT max(x.normalized_score) FROM attempts x WHERE x.recipient_id=r.id AND x.status IN ('SUBMITTED','TIMED_OUT')) AS best_score
                FROM assignments a JOIN assignment_recipients r ON r.assignment_id=a.id
                JOIN class_members m ON m.id=r.membership_id
                WHERE r.student_id=? AND a.status='PUBLISHED' ORDER BY a.opens_at DESC,a.id
                """,p.userId()).stream().map(r -> {
            int used=integer(r,"attempts_used");
            UUID active=id(r,"active_attempt_id");
            BigDecimal best=(BigDecimal)r.get("best_score");
            var release=ExamPolicy.release(best==null?"IN_PROGRESS":"SUBMITTED",instant(r,"score_release_at"),
                    instant(r,"answer_release_at"),str(r,"answer_release_mode"),instant(r,"closes_at"),now);
            boolean answers=ExamPolicy.answersAvailable(best!=null,instant(r,"answer_release_at"),str(r,"answer_release_mode"),instant(r,"closes_at"),now);
            boolean canStart=r.get("left_at")==null && active==null && used<integer(r,"max_attempts")
                    && !now.isBefore(instant(r,"opens_at")) && now.isBefore(instant(r,"closes_at"));
            return new StudentAssignment(id(r,"id"),id(r,"recipient_id"),id(r,"class_id"),str(r,"title"),
                    instant(r,"opens_at"),instant(r,"closes_at"),integer(r,"duration_minutes"),integer(r,"max_attempts"),
                    used,canStart,active,release.score()?best:null,release.score(),answers);
        }).toList();
    }

    public Attempt start(SessionPrincipal p, UUID assignmentId, StartRequest request) {
        role(p,"STUDENT");
        if(request==null || request.startRequestId()==null) throw new ApiException(400,"Start request ID is required");
        db.lock();
        db.finalizeExpired(p.userId(),null);
        var recipient=db.one("""
                SELECT r.id FROM assignment_recipients r JOIN assignments a ON a.id=r.assignment_id
                WHERE r.assignment_id=? AND r.student_id=? AND a.status='PUBLISHED'
                """,assignmentId,p.userId());
        UUID recipientId=id(recipient,"id");
        List<UUID> existing=db.jdbc().queryForList("SELECT id FROM attempts WHERE recipient_id=? AND start_request_id=?",UUID.class,recipientId,request.startRequestId());
        if(!existing.isEmpty()) return view(p,existing.getFirst());
        if(!db.jdbc().queryForList("SELECT id FROM attempts WHERE recipient_id=? AND status='IN_PROGRESS'",UUID.class,recipientId).isEmpty())
            throw new ApiException(409,"Resume the existing in-progress attempt");
        var started=db.one("SELECT * FROM quizai_v2_start_attempt(?,?,?)",p.userId(),recipientId,request.startRequestId());
        db.audit(p.userId(),"ATTEMPT_STARTED","ATTEMPT",id(started,"id"));
        return view(p,id(started,"id"));
    }

    public Attempt get(SessionPrincipal p, UUID attemptId) {
        role(p,"STUDENT");
        db.lock();
        ownedAttempt(p,attemptId);
        db.finalizeExpired(p.userId(),null);
        return view(p,attemptId);
    }

    public List<Attempt> history(SessionPrincipal p, UUID assignmentId) {
        role(p,"STUDENT");
        db.lock();
        var recipient=db.one("SELECT id FROM assignment_recipients WHERE assignment_id=? AND student_id=?",assignmentId,p.userId());
        db.finalizeExpired(p.userId(),assignmentId);
        return db.jdbc().queryForList("SELECT id FROM attempts WHERE recipient_id=? ORDER BY attempt_no DESC",UUID.class,id(recipient,"id"))
                .stream().map(id -> view(p,id)).toList();
    }

    public List<AttemptQuestion> review(SessionPrincipal p, UUID assignmentId) {
        role(p,"STUDENT");
        db.lock();
        var row=db.one("""
                SELECT a.*,r.id AS recipient_id FROM assignments a JOIN assignment_recipients r ON r.assignment_id=a.id
                WHERE a.id=? AND r.student_id=? AND a.status='PUBLISHED'
                """,assignmentId,p.userId());
        db.finalizeExpired(p.userId(),assignmentId);
        boolean terminal=Boolean.TRUE.equals(db.jdbc().queryForObject("SELECT EXISTS(SELECT 1 FROM attempts WHERE recipient_id=? AND status IN ('SUBMITTED','TIMED_OUT'))",Boolean.class,id(row,"recipient_id")));
        if(!ExamPolicy.answersAvailable(terminal,instant(row,"answer_release_at"),str(row,"answer_release_mode"),instant(row,"closes_at"),db.now()))
            throw new ApiException(403,"Answers have not been released");
        return db.jdbc().query("SELECT id,position,snapshot_json->>'stem' AS stem,snapshot_json->>'correct_option_key' AS correct_key FROM assignment_questions WHERE assignment_id=? ORDER BY position",
                (r,n)->{
                    UUID questionId=r.getObject("id",UUID.class);
                    List<StudentOption> options=db.jdbc().query("""
                            SELECT o.value->>'key' AS option_key,o.value->>'text' AS option_text FROM assignment_questions q
                            CROSS JOIN LATERAL jsonb_array_elements(q.snapshot_json->'options') WITH ORDINALITY o(value,ord)
                            WHERE q.id=? ORDER BY o.ord
                            """,(o,i)->new StudentOption(o.getString("option_key"),o.getString("option_text")),questionId);
                    return new AttemptQuestion(questionId,r.getInt("position"),r.getString("stem"),options,null,-1,null,r.getString("correct_key"));
                },assignmentId);
    }

    public SavedAnswer save(SessionPrincipal p, UUID attemptId, UUID questionId, AnswerRequest request) {
        role(p,"STUDENT");
        if(request==null || request.answerRevision()==null || request.answerRevision()<0
                || (request.selectedOptionKey()!=null && (request.selectedOptionKey().isBlank() || request.selectedOptionKey().length()>100)))
            throw new ApiException(400,"Invalid answer or revision");
        db.lock();
        ownedAttempt(p,attemptId);
        var saved=db.one("SELECT * FROM quizai_v2_save_answer(?,?,?,?,?)",p.userId(),attemptId,questionId,request.selectedOptionKey(),request.answerRevision());
        return new SavedAnswer(id(saved,"assignment_question_id"),str(saved,"selected_option_key"),
                ((Number)saved.get("answer_revision")).longValue(),instant(saved,"server_saved_at"));
    }

    public Attempt submit(SessionPrincipal p, UUID attemptId, SubmitRequest request) {
        role(p,"STUDENT");
        if(request==null || request.submitRequestId()==null) throw new ApiException(400,"Submit request ID is required");
        db.lock();
        var prior=ownedAttempt(p,attemptId);
        List<ExamPolicy.DigestAnswer> answers=db.jdbc().query("""
                SELECT assignment_question_id,selected_option_key,answer_revision FROM attempt_answers WHERE attempt_id=?
                """,(r,n)->new ExamPolicy.DigestAnswer(r.getObject("assignment_question_id",UUID.class),r.getString("selected_option_key"),r.getLong("answer_revision")),attemptId);
        db.one("SELECT * FROM quizai_v2_finalize_attempt(?,?,?,?)",p.userId(),attemptId,request.submitRequestId(),ExamPolicy.digest(answers));
        if("IN_PROGRESS".equals(str(prior,"status"))) db.audit(p.userId(),"ATTEMPT_FINALIZED","ATTEMPT",attemptId);
        return view(p,attemptId);
    }

    private Map<String,Object> ownedAttempt(SessionPrincipal p, UUID attemptId) {
        // Continuing an existing attempt intentionally does not require current membership.
        return db.one("""
                SELECT x.* FROM attempts x JOIN assignment_recipients r ON r.id=x.recipient_id
                WHERE x.id=? AND r.student_id=?
                """,attemptId,p.userId());
    }

    private Attempt view(SessionPrincipal p, UUID attemptId) {
        var row=ownedAttempt(p,attemptId);
        var assignment=db.one("SELECT * FROM assignments WHERE id=?",id(row,"assignment_id"));
        Instant now=db.now();
        var release=ExamPolicy.release(str(row,"status"),instant(assignment,"score_release_at"),instant(assignment,"answer_release_at"),
                str(assignment,"answer_release_mode"),instant(assignment,"closes_at"),now);
        List<AttemptQuestion> questions=db.jdbc().query("""
                SELECT q.id,q.position,q.stem,a.selected_option_key,a.answer_revision,a.server_saved_at
                FROM quizai_v2_student_questions(?,?) q LEFT JOIN attempt_answers a
                    ON a.assignment_question_id=q.id AND a.attempt_id=? ORDER BY q.position
                """,(rs,n)->{
            UUID questionId=rs.getObject("id",UUID.class);
            List<StudentOption> options=db.jdbc().query("""
                    SELECT o.value->>'key' AS option_key,o.value->>'text' AS option_text
                    FROM quizai_v2_student_questions(?,?) q
                    CROSS JOIN LATERAL jsonb_array_elements(q.options) WITH ORDINALITY o(value,ord)
                    WHERE q.id=? ORDER BY o.ord
                    """,(o,i)->new StudentOption(o.getString("option_key"),o.getString("option_text")),p.userId(),attemptId,questionId);
            String key=release.answers()?db.jdbc().queryForObject("SELECT snapshot_json->>'correct_option_key' FROM assignment_questions WHERE id=?",String.class,questionId):null;
            return new AttemptQuestion(questionId,rs.getInt("position"),rs.getString("stem"),options,rs.getString("selected_option_key"),
                    rs.getObject("answer_revision")==null?-1:rs.getLong("answer_revision"),instant(rs,"server_saved_at"),key);
        },p.userId(),attemptId,attemptId);
        return new Attempt(id(row,"id"),id(row,"assignment_id"),integer(row,"attempt_no"),str(row,"status"),
                instant(row,"started_at"),instant(row,"deadline_at"),instant(row,"submitted_at"),now,release.score(),release.answers(),
                release.score()?(Integer)row.get("earned_points"):null,release.score()?(Integer)row.get("possible_points"):null,
                release.score()?(BigDecimal)row.get("normalized_score"):null,questions);
    }
}
