package com.quizai.server.v2.foundation;

import com.quizai.server.security.SessionPrincipal;
import com.quizai.server.web.ApiException;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
@Profile({"v2-dev","v2-test"})
@Transactional(readOnly=true)
public class V2StudySummaryService {
    private final JdbcTemplate jdbc;
    public V2StudySummaryService(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public record StudentStudySummary(UUID studentId,String email,long completedSessions,long totalQuestions,long correctAnswers,BigDecimal accuracy) { }

    public List<StudentStudySummary> summary(SessionPrincipal actor,UUID classId) {
        if(actor==null) throw new ApiException(401,"Authentication required");
        if(!"TEACHER".equals(actor.role())) throw new ApiException(403,"Access denied");
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM classes WHERE id=? AND teacher_id=?)",Boolean.class,classId,actor.userId())))
            throw new ApiException(404,"Class not found");
        // Current class members only; each completed session counts once even across rejoin episodes.
        // The aggregate never reads private documents, quizzes, answers or conversation contents.
        return jdbc.query("""
                SELECT u.id,u.email_normalized,count(p.id) completed_sessions,
                       coalesce(sum(p.total_questions),0) total_questions,coalesce(sum(p.correct_count),0) correct_answers
                FROM users u
                LEFT JOIN practice_sessions p ON p.student_id=u.id AND p.status='COMPLETED'
                  AND EXISTS(SELECT 1 FROM class_members episode WHERE episode.class_id=? AND episode.student_id=u.id
                    AND p.finished_at>=episode.joined_at AND (episode.left_at IS NULL OR p.finished_at<episode.left_at))
                WHERE EXISTS(SELECT 1 FROM class_members current_member WHERE current_member.class_id=?
                  AND current_member.student_id=u.id AND current_member.left_at IS NULL)
                GROUP BY u.id,u.email_normalized ORDER BY u.email_normalized,u.id
                """,(rs,row)->new StudentStudySummary(rs.getObject("id",UUID.class),rs.getString("email_normalized"),
                rs.getLong("completed_sessions"),rs.getLong("total_questions"),rs.getLong("correct_answers"),
                accuracy(rs.getLong("correct_answers"),rs.getLong("total_questions"))),classId,classId);
    }
    static BigDecimal accuracy(long correct,long total) {
        return total==0?null:BigDecimal.valueOf(correct).divide(BigDecimal.valueOf(total),6,RoundingMode.HALF_UP);
    }
}
