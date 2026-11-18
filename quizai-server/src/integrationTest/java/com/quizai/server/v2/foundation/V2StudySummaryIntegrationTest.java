package com.quizai.server.v2.foundation;

import com.quizai.server.security.SessionPrincipal;
import com.quizai.server.web.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Real PostgreSQL integration. Explicit opt-in, never executed by build/check. */
class V2StudySummaryIntegrationTest {
    @Test void completedPracticeIsPrivateAndBoundedByMembershipEpisodes() throws Exception {
        String url=System.getenv("QUIZAI_V2_TEST_DB_URL"),user=System.getenv("QUIZAI_V2_TEST_DB_USERNAME"),password=System.getenv("QUIZAI_V2_TEST_DB_PASSWORD");
        System.out.println("V2_INTEGRATION_STAGE=ENVIRONMENT_GUARD; URL_PRESENT="+(url!=null && !url.isBlank())
                +"; USERNAME_PRESENT="+(user!=null && !user.isBlank())+"; PASSWORD_PRESENT="+(password!=null && !password.isBlank())
                +"; Java="+Runtime.version().feature());
        V2Target.validate("v2-test",url,user,password);
        System.out.println("V2_INTEGRATION_STAGE=ENVIRONMENT_VALIDATED; Java="+Runtime.version().feature());
        var source=new DriverManagerDataSource(url,user,password);
        V2Readiness.verify(source,"quizai_v2_test");
        System.out.println("V2_INTEGRATION_STAGE=READ_ONLY_SCHEMA_CHECKSUM_VALIDATED");
        try(var connection=source.getConnection()) {
            connection.setTransactionIsolation(java.sql.Connection.TRANSACTION_READ_COMMITTED);
            connection.setAutoCommit(false);
            UUID teacher=UUID.randomUUID(),student=UUID.randomUUID();
            try {
                var jdbc=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
                jdbc.execute("SET LOCAL statement_timeout='10s'");
                UUID group=UUID.randomUUID(),quiz=UUID.randomUUID(),version=UUID.randomUUID(),correct=UUID.randomUUID();
                for(var actor:new Object[][]{{teacher,"TEACHER"},{student,"STUDENT"}}) jdbc.update("INSERT INTO users(id,email_normalized,password_hash,role) VALUES (?,?,?,?)",actor[0],actor[0]+"@integration.quizai.local","integration-test-hash-placeholder",actor[1]);
                UUID question=UUID.randomUUID();
                jdbc.update("INSERT INTO questions(id,owner_id,origin) VALUES (?,?,'MANUAL')",question,student);
                jdbc.update("INSERT INTO question_versions(id,question_id,version_no,stem) VALUES (?,?,1,'Integration question')",version,question);
                jdbc.update("INSERT INTO question_options(id,question_version_id,position,option_text,is_correct) VALUES (?,?,1,'Yes',true),(?,?,2,'No',false)",correct,version,UUID.randomUUID(),version);
                jdbc.update("INSERT INTO quizzes(id,owner_id,kind,title) VALUES (?,?,'PRACTICE','Integration practice')",quiz,student);
                jdbc.update("INSERT INTO quiz_questions(quiz_id,question_version_id,position) VALUES (?,?,1)",quiz,version);
                complete(jdbc,student,quiz,version,correct); // Before membership: must never appear.
                jdbc.update("INSERT INTO classes(id,teacher_id,name) VALUES (?,?,'Rollback integration class')",group,teacher);
                UUID membership=UUID.randomUUID();
                jdbc.update("INSERT INTO class_members(id,class_id,student_id) VALUES (?,?,?)",membership,group,student);
                complete(jdbc,student,quiz,version,correct);
                jdbc.update("INSERT INTO practice_sessions(student_id,quiz_id) VALUES (?,?)",student,quiz); // Unfinished: excluded.
                var service=new V2StudySummaryService(jdbc);
                var principal=new SessionPrincipal(teacher,"integration@quizai.local","TEACHER",UUID.randomUUID(),Instant.now().plusSeconds(60));
                var rows=service.summary(principal,group);
                assertEquals(1,rows.size()); assertEquals(1,rows.getFirst().completedSessions());
                assertEquals(1,rows.getFirst().correctAnswers()); assertEquals(1,rows.getFirst().totalQuestions());
                assertEquals(404,assertThrows(ApiException.class,()->service.summary(new SessionPrincipal(UUID.randomUUID(),"other@quizai.local","TEACHER",UUID.randomUUID(),Instant.now()),group)).status());
                jdbc.update("UPDATE class_members SET left_at=clock_timestamp() WHERE id=?",membership);
                assertTrue(service.summary(principal,group).isEmpty());
                jdbc.update("INSERT INTO class_members(class_id,student_id) VALUES (?,?)",group,student);
                assertEquals(1,service.summary(principal,group).getFirst().completedSessions());
            } finally {
                connection.rollback();
                connection.setReadOnly(true);
                var remaining=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
                assertEquals(0,remaining.queryForObject("""
                        SELECT (SELECT count(*) FROM users WHERE id IN (?,?))
                             + (SELECT count(*) FROM classes WHERE teacher_id=?)
                             + (SELECT count(*) FROM questions WHERE owner_id=?)
                             + (SELECT count(*) FROM quizzes WHERE owner_id=?)
                             + (SELECT count(*) FROM practice_sessions WHERE student_id=?)
                             + (SELECT count(*) FROM class_members WHERE student_id=?)
                        """,Long.class,teacher,student,teacher,student,student,student,student));
                connection.rollback();
                System.out.println("V2_INTEGRATION_STAGE=FIXTURES_ROLLED_BACK_AND_ABSENT");
            }
        }
    }
    private void complete(JdbcTemplate jdbc,UUID student,UUID quiz,UUID version,UUID correct) {
        UUID session=UUID.randomUUID(),question=UUID.randomUUID();
        jdbc.update("INSERT INTO practice_sessions(id,student_id,quiz_id) VALUES (?,?,?)",session,student,quiz);
        jdbc.update("INSERT INTO practice_session_questions(id,practice_session_id,question_version_id,position) VALUES (?,?,?,1)",question,session,version);
        jdbc.update("INSERT INTO practice_answers(session_question_id,selected_option_id) VALUES (?,?)",question,correct);
        jdbc.update("UPDATE practice_sessions SET status='COMPLETED',finished_at=clock_timestamp(),total_questions=1,correct_count=1 WHERE id=?",session);
    }
}
