package com.quizai.server.v2.exams;

import com.quizai.server.security.SessionPrincipal;
import com.quizai.server.web.ApiException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.quizai.server.v2.exams.ExamDtos.*;
import static com.quizai.server.v2.exams.ExamStore.*;

@Service
@Profile({"v2-dev", "v2-test"})
@Transactional(isolation = Isolation.READ_COMMITTED)
public class TeacherExamService {
    private final ExamStore db;
    public TeacherExamService(ExamStore db) { this.db = db; }

    public List<Bank> banks(SessionPrincipal p) {
        role(p,"TEACHER");
        return db.jdbc().query("SELECT * FROM question_banks WHERE owner_teacher_id=? ORDER BY created_at DESC,id",
                (r,n) -> new Bank(r.getObject("id",UUID.class),r.getString("name"),instant(r,"archived_at"),instant(r,"created_at")),p.userId());
    }

    public Bank createBank(SessionPrincipal p, BankRequest request) {
        role(p,"TEACHER");
        if (request == null) throw new ApiException(400,"Bank is required");
        String name = text(request.name(),200);
        db.lock();
        UUID id = UUID.randomUUID();
        db.jdbc().update("INSERT INTO question_banks(id,owner_teacher_id,name) VALUES (?,?,?)",id,p.userId(),name);
        db.audit(p.userId(),"BANK_CREATED","QUESTION_BANK",id);
        return banks(p).stream().filter(b -> b.id().equals(id)).findFirst().orElseThrow();
    }

    public List<Question> questions(SessionPrincipal p, UUID bankId) {
        return questions(p,bankId,null,null,null);
    }

    public List<Question> questions(SessionPrincipal p, UUID bankId, UUID subjectId, UUID topicId, String difficulty) {
        role(p,"TEACHER");
        if(difficulty!=null && !List.of("EASY","MEDIUM","HARD").contains(difficulty)) throw new ApiException(400,"Invalid difficulty");
        StringBuilder sql=new StringBuilder("""
                SELECT q.id AS question_id,q.bank_id,q.origin,v.* FROM questions q
                JOIN question_versions v ON v.question_id=q.id
                WHERE q.owner_id=? AND v.version_no=(SELECT max(v2.version_no) FROM question_versions v2 WHERE v2.question_id=q.id)
                """);
        List<Object> args=new ArrayList<>(List.of(p.userId()));
        if(bankId!=null) { sql.append(" AND q.bank_id=?"); args.add(bankId); }
        if(subjectId!=null) { sql.append(" AND v.subject_id=?"); args.add(subjectId); }
        if(topicId!=null) { sql.append(" AND v.topic_id=?"); args.add(topicId); }
        if(difficulty!=null) { sql.append(" AND v.difficulty=?"); args.add(difficulty); }
        sql.append(" ORDER BY q.created_at DESC,q.id");
        List<Map<String,Object>> rows = db.jdbc().queryForList(sql.toString(),args.toArray());
        return rows.stream().map(this::question).toList();
    }

    public List<Question> versions(SessionPrincipal p, UUID questionId) {
        role(p,"TEACHER");
        db.exists("SELECT id FROM questions WHERE id=? AND owner_id=?",questionId,p.userId());
        return db.jdbc().queryForList("""
                SELECT q.id AS question_id,q.bank_id,q.origin,v.* FROM questions q
                JOIN question_versions v ON v.question_id=q.id WHERE q.id=? AND q.owner_id=? ORDER BY v.version_no DESC
                """,questionId,p.userId()).stream().map(this::question).toList();
    }

    public Question createQuestion(SessionPrincipal p, QuestionRequest request) {
        role(p,"TEACHER");
        validateQuestion(request);
        if (request.bankId() == null) throw new ApiException(400,"A teacher question bank is required");
        db.lock();
        db.exists("SELECT id FROM question_banks WHERE id=? AND owner_teacher_id=? AND archived_at IS NULL",request.bankId(),p.userId());
        validateCatalog(request);
        UUID questionId = UUID.randomUUID();
        db.jdbc().update("INSERT INTO questions(id,owner_id,bank_id,origin) VALUES (?,?,?,'MANUAL')",questionId,p.userId(),request.bankId());
        return insertVersion(p,questionId,1,request);
    }

    public Question newVersion(SessionPrincipal p, UUID questionId, QuestionRequest request) {
        role(p,"TEACHER");
        validateQuestion(request);
        db.lock();
        var q = db.one("SELECT * FROM questions WHERE id=? AND owner_id=? FOR UPDATE",questionId,p.userId());
        if (request.bankId() != null && !request.bankId().equals(id(q,"bank_id")))
            throw new ApiException(400,"Question bank cannot change");
        validateCatalog(request);
        int next = db.jdbc().queryForObject("SELECT coalesce(max(version_no),0)+1 FROM question_versions WHERE question_id=?",Integer.class,questionId);
        return insertVersion(p,questionId,next,request);
    }

    public Question approve(SessionPrincipal p, UUID questionId, UUID versionId) {
        role(p,"TEACHER");
        db.lock();
        var version = db.one("""
                SELECT v.id,v.approval_status FROM question_versions v JOIN questions q ON q.id=v.question_id
                WHERE v.id=? AND q.id=? AND q.owner_id=? FOR UPDATE OF v
                """,versionId,questionId,p.userId());
        if (!"APPROVED".equals(str(version,"approval_status"))) {
            db.jdbc().update("UPDATE question_versions SET approval_status='APPROVED',approved_by=?,approved_at=clock_timestamp() WHERE id=?",p.userId(),versionId);
            db.audit(p.userId(),"QUESTION_APPROVED","QUESTION_VERSION",versionId);
        }
        return versions(p,questionId).stream().filter(v -> v.versionId().equals(versionId)).findFirst().orElseThrow();
    }

    public List<Quiz> quizzes(SessionPrincipal p) {
        role(p,"TEACHER");
        return db.jdbc().queryForList("SELECT * FROM quizzes WHERE owner_id=? AND kind='TEACHER_QUIZ' ORDER BY created_at DESC,id",p.userId())
                .stream().map(this::quiz).toList();
    }

    public Quiz getQuiz(SessionPrincipal p, UUID quizId) {
        role(p,"TEACHER");
        return quiz(db.one("SELECT * FROM quizzes WHERE id=? AND owner_id=? AND kind='TEACHER_QUIZ'",quizId,p.userId()));
    }

    public Quiz createQuiz(SessionPrincipal p, QuizRequest request) {
        role(p,"TEACHER");
        validateQuiz(request);
        db.lock();
        validateQuizVersions(p,request.questionVersionIds());
        UUID quizId = UUID.randomUUID();
        db.jdbc().update("INSERT INTO quizzes(id,owner_id,kind,title) VALUES (?,?,'TEACHER_QUIZ',?)",quizId,p.userId(),text(request.title(),200));
        insertQuizVersions(quizId,request.questionVersionIds());
        db.audit(p.userId(),"QUIZ_CREATED","QUIZ",quizId);
        return getQuiz(p,quizId);
    }

    public Quiz updateQuiz(SessionPrincipal p, UUID quizId, QuizRequest request) {
        role(p,"TEACHER");
        validateQuiz(request);
        db.lock();
        getQuiz(p,quizId);
        validateQuizVersions(p,request.questionVersionIds());
        db.jdbc().update("DELETE FROM quiz_questions WHERE quiz_id=?",quizId);
        db.jdbc().update("UPDATE quizzes SET title=? WHERE id=?",text(request.title(),200),quizId);
        insertQuizVersions(quizId,request.questionVersionIds());
        db.audit(p.userId(),"QUIZ_UPDATED","QUIZ",quizId);
        return getQuiz(p,quizId);
    }

    public List<Assignment> assignments(SessionPrincipal p) {
        role(p,"TEACHER");
        return db.jdbc().queryForList("SELECT * FROM assignments WHERE teacher_id=? ORDER BY created_at DESC,id",p.userId())
                .stream().map(db::assignment).toList();
    }

    public Assignment createAssignment(SessionPrincipal p, AssignmentRequest request) {
        role(p,"TEACHER");
        validateAssignment(request);
        db.lock();
        db.exists("SELECT id FROM classes WHERE id=? AND teacher_id=?",request.classId(),p.userId());
        getQuiz(p,request.quizId());
        List<UUID> versions = getQuiz(p,request.quizId()).questionVersionIds();
        if (versions.isEmpty()) throw new ApiException(409,"The quiz has no questions");
        for (UUID version : versions) db.exists("""
                SELECT v.id FROM question_versions v JOIN questions q ON q.id=v.question_id
                WHERE v.id=? AND q.owner_id=? AND v.approval_status='APPROVED'
                """,version,p.userId());
        List<Map<String,Object>> members = db.jdbc().queryForList("SELECT id,student_id FROM class_members WHERE class_id=? AND left_at IS NULL ORDER BY id",request.classId());
        List<UUID> selected = request.studentIds();
        if (selected != null && !selected.isEmpty()) {
            if (new HashSet<>(selected).size()!=selected.size() || selected.stream().anyMatch(java.util.Objects::isNull)) throw new ApiException(400,"Invalid recipients");
            members = members.stream().filter(m -> selected.contains(id(m,"student_id"))).toList();
            if (members.size()!=selected.size()) throw new ApiException(409,"Recipients must be current class members");
        }
        if (members.isEmpty()) throw new ApiException(409,"At least one recipient is required");
        UUID assignmentId = UUID.randomUUID();
        db.jdbc().update("""
                INSERT INTO assignments(id,class_id,teacher_id,quiz_id,title,opens_at,closes_at,duration_minutes,max_attempts,
                    score_release_at,answer_release_at,answer_release_mode) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """,assignmentId,request.classId(),p.userId(),request.quizId(),text(request.title(),200),time(request.opensAt()),time(request.closesAt()),
                request.durationMinutes(),request.maxAttempts()==null?1:request.maxAttempts(),time(request.scoreReleaseAt()),time(request.answerReleaseAt()),mode(request.answerReleaseMode()));
        for (var member : members) db.jdbc().update("INSERT INTO assignment_recipients(assignment_id,student_id,membership_id) VALUES (?,?,?)",
                assignmentId,id(member,"student_id"),id(member,"id"));
        // Copy reviewed source versions and their exact ordered options; no mutable source is used to grade later.
        db.jdbc().update("""
                INSERT INTO assignment_questions(assignment_id,source_question_version_id,position,snapshot_json)
                SELECT ?,v.id,qq.position,jsonb_build_object('schema_version',1,'stem',v.stem,
                    'options',(SELECT jsonb_agg(jsonb_build_object('key',o.id::text,'text',o.option_text) ORDER BY o.position)
                        FROM question_options o WHERE o.question_version_id=v.id),
                    'correct_option_key',(SELECT o.id::text FROM question_options o WHERE o.question_version_id=v.id AND o.is_correct))
                FROM quiz_questions qq JOIN question_versions v ON v.id=qq.question_version_id WHERE qq.quiz_id=? ORDER BY qq.position
                """,assignmentId,request.quizId());
        db.audit(p.userId(),"ASSIGNMENT_CREATED","ASSIGNMENT",assignmentId);
        return ownedAssignment(p,assignmentId);
    }

    public Assignment publish(SessionPrincipal p, UUID assignmentId) {
        role(p,"TEACHER");
        db.lock();
        ownedAssignment(p,assignmentId);
        var published = db.one("SELECT * FROM quizai_v2_publish_assignment(?,?)",p.userId(),assignmentId);
        db.audit(p.userId(),"ASSIGNMENT_PUBLISHED","ASSIGNMENT",assignmentId);
        return db.assignment(published);
    }

    public Assignment releases(SessionPrincipal p, UUID assignmentId, ReleasesRequest request) {
        role(p,"TEACHER");
        if (request == null) throw new ApiException(400,"Release configuration is required");
        String releaseMode = mode(request.answerReleaseMode());
        db.lock();
        ownedAssignment(p,assignmentId);
        // V5 rejects changing any release that has already become available, even if never fetched.
        db.jdbc().update("UPDATE assignments SET score_release_at=?,answer_release_at=?,answer_release_mode=? WHERE id=?",
                time(request.scoreReleaseAt()),time(request.answerReleaseAt()),releaseMode,assignmentId);
        db.audit(p.userId(),"ASSIGNMENT_RELEASE_CHANGED","ASSIGNMENT",assignmentId);
        return ownedAssignment(p,assignmentId);
    }

    public List<StudentResult> results(SessionPrincipal p, UUID assignmentId) {
        role(p,"TEACHER");
        db.lock();
        ownedAssignment(p,assignmentId);
        db.finalizeExpired(null,assignmentId);
        return db.jdbc().queryForList("""
                SELECT r.id,r.student_id,u.email_normalized FROM assignment_recipients r JOIN users u ON u.id=r.student_id
                WHERE r.assignment_id=? ORDER BY u.email_normalized,r.id
                """,assignmentId).stream().map(r -> {
            var attempts = db.jdbc().query("SELECT * FROM attempts WHERE recipient_id=? ORDER BY attempt_no", (rs,n) ->
                    new AttemptResult(rs.getObject("id",UUID.class),rs.getInt("attempt_no"),rs.getString("status"),instant(rs,"started_at"),
                            instant(rs,"deadline_at"),instant(rs,"submitted_at"),(Integer)rs.getObject("earned_points"),
                            (Integer)rs.getObject("possible_points"),rs.getBigDecimal("normalized_score")),id(r,"id"));
            BigDecimal best = ExamPolicy.bestScore(attempts);
            return new StudentResult(id(r,"student_id"),str(r,"email_normalized"),attempts.size(),best,attempts);
        }).toList();
    }

    private Assignment ownedAssignment(SessionPrincipal p, UUID id) {
        return db.assignment(db.one("SELECT * FROM assignments WHERE id=? AND teacher_id=?",id,p.userId()));
    }

    private Question insertVersion(SessionPrincipal p, UUID questionId, int number, QuestionRequest r) {
        UUID versionId = UUID.randomUUID();
        db.jdbc().update("INSERT INTO question_versions(id,question_id,version_no,stem,subject_id,topic_id,difficulty) VALUES (?,?,?,?,?,?,?)",
                versionId,questionId,number,text(r.stem(),20000),r.subjectId(),r.topicId(),r.difficulty()==null?"MEDIUM":r.difficulty());
        for (int i=0;i<r.options().size();i++) {
            var o=r.options().get(i);
            db.jdbc().update("INSERT INTO question_options(question_version_id,position,option_text,is_correct) VALUES (?,?,?,?)",versionId,i+1,text(o.text(),10000),o.correct());
        }
        db.audit(p.userId(),"QUESTION_VERSION_CREATED","QUESTION_VERSION",versionId);
        return versions(p,questionId).getFirst();
    }

    private Question question(Map<String,Object> r) {
        UUID versionId=id(r,"id");
        List<QuestionOption> options=db.jdbc().query("SELECT * FROM question_options WHERE question_version_id=? ORDER BY position",(rs,n)->
                new QuestionOption(rs.getObject("id",UUID.class),rs.getInt("position"),rs.getString("option_text"),rs.getBoolean("is_correct")),versionId);
        return new Question(id(r,"question_id"),versionId,id(r,"bank_id"),integer(r,"version_no"),str(r,"stem"),id(r,"subject_id"),id(r,"topic_id"),
                str(r,"difficulty"),str(r,"approval_status"),str(r,"origin"),options);
    }

    private Quiz quiz(Map<String,Object> row) {
        return new Quiz(id(row,"id"),str(row,"title"),str(row,"status"),db.jdbc().queryForList(
                "SELECT question_version_id FROM quiz_questions WHERE quiz_id=? ORDER BY position",UUID.class,id(row,"id")));
    }

    private void validateCatalog(QuestionRequest r) {
        if (r.subjectId()!=null) db.exists("SELECT id FROM subjects WHERE id=? AND active",r.subjectId());
        if (r.topicId()!=null) db.exists("SELECT id FROM topics WHERE id=? AND subject_id=? AND active",r.topicId(),r.subjectId());
    }

    private void validateQuizVersions(SessionPrincipal p, List<UUID> ids) {
        for(UUID id:ids) db.exists("""
                SELECT v.id FROM question_versions v JOIN questions q ON q.id=v.question_id WHERE v.id=? AND q.owner_id=?
                AND (SELECT count(*) FROM question_options o WHERE o.question_version_id=v.id)>=2
                AND (SELECT count(*) FROM question_options o WHERE o.question_version_id=v.id AND o.is_correct)=1
                """,id,p.userId());
    }

    private void insertQuizVersions(UUID quiz, List<UUID> ids) {
        for(int i=0;i<ids.size();i++) db.jdbc().update("INSERT INTO quiz_questions(quiz_id,question_version_id,position) VALUES (?,?,?)",quiz,ids.get(i),i+1);
    }

    private static void validateQuestion(QuestionRequest r) {
        if(r==null) throw new ApiException(400,"Question is required");
        text(r.stem(),20000);
        if(r.topicId()!=null && r.subjectId()==null) throw new ApiException(400,"Topic requires a subject");
        if(r.difficulty()!=null && !List.of("EASY","MEDIUM","HARD").contains(r.difficulty())) throw new ApiException(400,"Invalid difficulty");
        if(r.options()==null || r.options().size()<2 || r.options().size()>10 || r.options().stream().anyMatch(java.util.Objects::isNull)
                || r.options().stream().filter(OptionInput::correct).count()!=1) throw new ApiException(400,"MCQ requires 2 to 10 options and exactly one correct option");
        r.options().forEach(o->text(o.text(),10000));
    }

    private static void validateQuiz(QuizRequest r) {
        if(r==null) throw new ApiException(400,"Quiz is required");
        text(r.title(),200);
        if(r.questionVersionIds()==null || r.questionVersionIds().isEmpty() || r.questionVersionIds().size()>200
                || r.questionVersionIds().stream().anyMatch(java.util.Objects::isNull) || new HashSet<>(r.questionVersionIds()).size()!=r.questionVersionIds().size())
            throw new ApiException(400,"Quiz requires 1 to 200 distinct question versions");
    }

    private static void validateAssignment(AssignmentRequest r) {
        if(r==null) throw new ApiException(400,"Assignment is required");
        text(r.title(),200); mode(r.answerReleaseMode());
        if(r.classId()==null || r.quizId()==null || r.opensAt()==null || r.closesAt()==null || !r.opensAt().isBefore(r.closesAt())
                || r.durationMinutes()==null || r.durationMinutes()<1 || r.durationMinutes()>1440
                || (r.maxAttempts()!=null && (r.maxAttempts()<1 || r.maxAttempts()>100))) throw new ApiException(400,"Invalid assignment configuration");
    }

    private static String mode(String value) {
        String mode=value==null?"AFTER_ASSIGNMENT_CLOSE":value;
        if(!List.of("AFTER_VALID_SUBMISSION","AFTER_ASSIGNMENT_CLOSE").contains(mode)) throw new ApiException(400,"Invalid answer release mode");
        return mode;
    }
}
