package com.quizai.server.v2.exams;

import com.quizai.server.security.SessionPrincipal;
import com.quizai.server.web.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.quizai.server.v2.exams.ExamDtos.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Offline service behavior tests. PostgreSQL constraint/concurrency proofs belong in live V2 tests. */
class ExamServiceTest {
    private static final Instant NOW=Instant.parse("2026-11-13T10:00:00Z");
    private static final UUID STUDENT=UUID.randomUUID(), ATTEMPT=UUID.randomUUID(), ASSIGNMENT=UUID.randomUUID(), QUESTION=UUID.randomUUID(), RECIPIENT=UUID.randomUUID();

    @Test void unauthenticatedAndWrongRoleRequestsFailBeforeDatabaseUse() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        var db=new ExamStore(jdbc);
        var teacher=new TeacherExamService(db);
        var student=new StudentExamService(db);
        assertEquals(401,assertThrows(ApiException.class,()->teacher.banks(null)).status());
        assertEquals(403,assertThrows(ApiException.class,()->teacher.banks(principal("STUDENT"))).status());
        assertEquals(403,assertThrows(ApiException.class,()->student.assignments(principal("TEACHER"))).status());
        assertEquals(403,assertThrows(ApiException.class,()->student.submit(principal("TEACHER"),ATTEMPT,new SubmitRequest(UUID.randomUUID()))).status());
        verifyNoInteractions(jdbc);
    }

    @Test void invalidMcqCannotReachDatabase() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        var service=new TeacherExamService(new ExamStore(jdbc));
        var invalid=new QuestionRequest(UUID.randomUUID(),"Question",null,null,"MEDIUM",
                List.of(new OptionInput("One",true),new OptionInput("Two",true)));
        assertEquals(400,assertThrows(ApiException.class,()->service.createQuestion(principal("TEACHER"),invalid)).status());
        verifyNoInteractions(jdbc);
    }

    @Test void duplicateQuizVersionsAreRejectedBeforeDatabaseMutation() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        UUID version=UUID.randomUUID();
        var service=new TeacherExamService(new ExamStore(jdbc));
        assertEquals(400,assertThrows(ApiException.class,()->service.createQuiz(principal("TEACHER"),
                new QuizRequest("Quiz",List.of(version,version)))).status());
        verifyNoInteractions(jdbc);
    }

    @Test void malformedAutosaveAndSubmitIdsAreRejectedBeforeDatabaseMutation() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        var service=new StudentExamService(new ExamStore(jdbc));
        assertEquals(400,assertThrows(ApiException.class,()->service.save(principal("STUDENT"),ATTEMPT,QUESTION,new AnswerRequest("A",-1L))).status());
        assertEquals(400,assertThrows(ApiException.class,()->service.submit(principal("STUDENT"),ATTEMPT,new SubmitRequest(null))).status());
        verifyNoInteractions(jdbc);
    }

    @Test void studentProjectionHidesPointsAndKeysWhenReleaseIsInFuture() {
        var db=new ProjectionStore("SUBMITTED",NOW.plusSeconds(1),NOW.plusSeconds(1));
        var result=new StudentExamService(db).get(principal("STUDENT"),ATTEMPT);
        assertFalse(result.scoreAvailable());
        assertFalse(result.answerAvailable());
        assertNull(result.earnedPoints());
        assertNull(result.possiblePoints());
        assertNull(result.normalizedScore());
        assertNull(result.questions().getFirst().correctOptionKey());
        assertEquals("option-a",result.questions().getFirst().selectedOptionKey());
        assertEquals(3,result.questions().getFirst().answerRevision());
        assertEquals(List.of(new StudentOption("option-a","A"),new StudentOption("option-b","B")),result.questions().getFirst().options());
        assertEquals(0,db.jdbcSpy.correctKeyReads);
    }

    @Test void answerReleaseDoesNotExposeUnreleasedScore() {
        var db=new ProjectionStore("SUBMITTED",NOW.plusSeconds(1),NOW);
        var result=new StudentExamService(db).get(principal("STUDENT"),ATTEMPT);
        assertNull(result.normalizedScore());
        assertEquals("option-a",result.questions().getFirst().correctOptionKey());
        assertEquals(1,db.jdbcSpy.correctKeyReads);
    }

    @Test void activeAttemptNeverReadsCorrectKeyEvenAfterScheduledRelease() {
        var db=new ProjectionStore("IN_PROGRESS",NOW,NOW);
        var result=new StudentExamService(db).get(principal("STUDENT"),ATTEMPT);
        assertNull(result.questions().getFirst().correctOptionKey());
        assertNull(result.earnedPoints());
        assertEquals(0,db.jdbcSpy.correctKeyReads);
    }

    @Test void releasedBlankTimeoutHasZeroDistinctFromUnreleasedNull() {
        var db=new ProjectionStore("TIMED_OUT",NOW,NOW);
        db.attempt.put("earned_points",0);
        db.attempt.put("normalized_score",new BigDecimal("0.000000"));
        var result=new StudentExamService(db).get(principal("STUDENT"),ATTEMPT);
        assertEquals(0,result.earnedPoints());
        assertEquals(new BigDecimal("0.000000"),result.normalizedScore());
    }

    @Test void sameStartRequestReturnsExistingTerminalAttemptWithoutCreatingAnother() {
        var db=new ProjectionStore("SUBMITTED",NOW,NOW);
        db.jdbcSpy.existingStart=true;
        var result=new StudentExamService(db).start(principal("STUDENT"),ASSIGNMENT,new StartRequest(UUID.randomUUID()));
        assertEquals(ATTEMPT,result.id());
        assertEquals("SUBMITTED",result.status());
        // The fixture rejects any start function call. Retry resolves the original identity first.
    }

    @Test void distinctStartRequestCannotConsumeAnotherAttemptWhileOneIsActive() {
        var db=new ProjectionStore("IN_PROGRESS",NOW,NOW);
        db.jdbcSpy.activeStart=true;
        assertEquals(409,assertThrows(ApiException.class,()->new StudentExamService(db).start(
                principal("STUDENT"),ASSIGNMENT,new StartRequest(UUID.randomUUID()))).status());
    }

    private static SessionPrincipal principal(String role) {
        return new SessionPrincipal(STUDENT,"student@example.test",role,UUID.randomUUID(),NOW.plusSeconds(3600));
    }

    private static final class ProjectionStore extends ExamStore {
        final Map<String,Object> attempt=new HashMap<>();
        final Map<String,Object> assignment=new HashMap<>();
        final ProjectionJdbc jdbcSpy;
        ProjectionStore(String status,Instant scoreAt,Instant answerAt) { this(new ProjectionJdbc(),status,scoreAt,answerAt); }
        ProjectionStore(ProjectionJdbc jdbc,String status,Instant scoreAt,Instant answerAt) {
            super(jdbc); jdbcSpy=jdbc;
            attempt.put("id",ATTEMPT); attempt.put("assignment_id",ASSIGNMENT); attempt.put("status",status);
            attempt.put("attempt_no",1); attempt.put("started_at",Timestamp.from(NOW.minusSeconds(60)));
            attempt.put("deadline_at",Timestamp.from(NOW.plusSeconds(60))); attempt.put("submitted_at",Timestamp.from(NOW));
            attempt.put("earned_points",1); attempt.put("possible_points",2); attempt.put("normalized_score",new BigDecimal("0.500000"));
            assignment.put("score_release_at",Timestamp.from(scoreAt)); assignment.put("answer_release_at",Timestamp.from(answerAt));
            assignment.put("answer_release_mode","AFTER_VALID_SUBMISSION"); assignment.put("closes_at",Timestamp.from(NOW.plusSeconds(600)));
        }
        @Override void lock() {}
        @Override Instant now() { return NOW; }
        @Override void finalizeExpired(UUID studentId,UUID assignmentId) {}
        @Override Map<String,Object> one(String sql,Object... args) {
            if(sql.contains("FROM assignment_recipients r")) {
                assertEquals(ASSIGNMENT,args[0]);
                assertEquals(STUDENT,args[1]);
                return Map.of("id",RECIPIENT);
            }
            if(sql.contains("FROM attempts")) {
                assertEquals(ATTEMPT,args[0]);
                assertEquals(STUDENT,args[1]); // Ownership predicate must use the authenticated actor.
                return attempt;
            }
            if(sql.contains("FROM assignments")) return assignment;
            throw new AssertionError("Unexpected query: "+sql);
        }
    }

    private static final class ProjectionJdbc extends JdbcTemplate {
        int correctKeyReads;
        boolean existingStart,activeStart;
        @Override public <T> List<T> queryForList(String sql,Class<T> type,Object... args) {
            assertTrue(sql.contains("FROM attempts WHERE recipient_id=?"));
            assertEquals(RECIPIENT,args[0]);
            boolean found=sql.contains("start_request_id")?existingStart:activeStart;
            return found?List.of(type.cast(ATTEMPT)):List.of();
        }
        @Override public <T> List<T> query(String sql,RowMapper<T> mapper,Object... args) {
            try {
                if(sql.contains("CROSS JOIN LATERAL")) return List.of(mapper.mapRow(option("option-a","A"),0),mapper.mapRow(option("option-b","B"),1));
                assertTrue(sql.contains("quizai_v2_student_questions"));
                ResultSet row=mock(ResultSet.class);
                when(row.getObject("id",UUID.class)).thenReturn(QUESTION);
                when(row.getInt("position")).thenReturn(1);
                when(row.getString("stem")).thenReturn("Frozen question");
                when(row.getString("selected_option_key")).thenReturn("option-a");
                when(row.getObject("answer_revision")).thenReturn(3L);
                when(row.getLong("answer_revision")).thenReturn(3L);
                when(row.getTimestamp("server_saved_at")).thenReturn(Timestamp.from(NOW.minusSeconds(1)));
                return List.of(mapper.mapRow(row,0));
            } catch(SQLException error) { throw new AssertionError(error); }
        }
        @Override public <T> T queryForObject(String sql,Class<T> type,Object... args) {
            assertTrue(sql.contains("correct_option_key"));
            correctKeyReads++;
            return type.cast("option-a");
        }
        private ResultSet option(String key,String text) throws SQLException {
            ResultSet rs=mock(ResultSet.class);
            when(rs.getString("option_key")).thenReturn(key);
            when(rs.getString("option_text")).thenReturn(text);
            return rs;
        }
    }
}
