package com.quizai.server.v2.exams;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ExamPolicyTest {
    private final Instant now = Instant.parse("2026-11-13T10:00:00Z");

    @Test void futureScoreReleaseDoesNotHideIndependentlyReleasedAnswers() {
        var release = ExamPolicy.release("SUBMITTED", now.plusSeconds(60), now.minusSeconds(1),
                "AFTER_VALID_SUBMISSION", now.plusSeconds(600), now);
        assertFalse(release.score());
        assertTrue(release.answers());
    }

    @Test void inProgressAttemptNeverRevealsCorrectAnswersUnderSubmissionMode() {
        assertFalse(ExamPolicy.release("IN_PROGRESS", now, now, "AFTER_VALID_SUBMISSION", now, now).answers());
    }

    @Test void afterCloseRequiresBothReleaseAndClosingTime() {
        assertFalse(ExamPolicy.release("SUBMITTED", now, now, "AFTER_ASSIGNMENT_CLOSE", now.plusSeconds(1), now).answers());
        assertTrue(ExamPolicy.release("TIMED_OUT", now, now, "AFTER_ASSIGNMENT_CLOSE", now, now).answers());
    }

    @Test void unreleasedScoresStayAbsentEvenForTimeoutZero() {
        var release = ExamPolicy.release("TIMED_OUT", null, null, "AFTER_ASSIGNMENT_CLOSE", now, now);
        assertFalse(release.score());
        assertFalse(release.answers());
    }

    @Test void afterCloseRecipientCanReviewAnswersWithoutStartingAnAttempt() {
        assertTrue(ExamPolicy.answersAvailable(false,now,"AFTER_ASSIGNMENT_CLOSE",now,now));
        assertFalse(ExamPolicy.answersAvailable(false,now,"AFTER_VALID_SUBMISSION",now,now));
    }

    @Test void digestUsesCanonicalOrderingAndIncludesRevisionsAndNullSelections() {
        UUID one = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID two = UUID.fromString("00000000-0000-0000-0000-000000000002");
        var first = new ExamPolicy.DigestAnswer(one, "A", 1);
        var second = new ExamPolicy.DigestAnswer(two, null, 0);
        String digest = ExamPolicy.digest(List.of(first, second));
        assertEquals(digest, ExamPolicy.digest(List.of(second, first)));
        assertTrue(digest.matches("[0-9a-f]{64}"));
        assertNotEquals(digest, ExamPolicy.digest(List.of(new ExamPolicy.DigestAnswer(one, "A", 2), second)));
        assertNotEquals(ExamPolicy.digest(List.of(second)), ExamPolicy.digest(List.of()));
    }

    @Test void highestScoreUsesOnlyFinalizedAttemptsAndNoResultsRemainNull() {
        assertNull(ExamPolicy.bestScore(List.of()));
        assertNull(ExamPolicy.bestScore(List.of(result("IN_PROGRESS",null))));
        assertEquals(new BigDecimal("0.90"),ExamPolicy.bestScore(List.of(
                result("SUBMITTED",new BigDecimal("0.50")),result("SUBMITTED",new BigDecimal("0.90")),
                result("TIMED_OUT",new BigDecimal("0.70")),result("IN_PROGRESS",BigDecimal.ONE))));
        assertEquals(BigDecimal.ZERO,ExamPolicy.bestScore(List.of(result("TIMED_OUT",BigDecimal.ZERO))));
    }

    private ExamDtos.AttemptResult result(String status,BigDecimal score) {
        return new ExamDtos.AttemptResult(UUID.randomUUID(),1,status,now,now,null,null,null,score);
    }
}
