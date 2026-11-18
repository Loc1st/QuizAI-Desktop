package com.quizai.server.v2.exams;

import java.nio.ByteBuffer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

final class ExamPolicy {
    private ExamPolicy() {}

    record Release(boolean score, boolean answers) {}
    record DigestAnswer(UUID questionId, String selectedOptionKey, long revision) {}

    static Release release(String status, Instant scoreAt, Instant answerAt, String mode,
                           Instant closesAt, Instant now) {
        boolean terminal = "SUBMITTED".equals(status) || "TIMED_OUT".equals(status);
        boolean score = terminal && scoreAt != null && !now.isBefore(scoreAt);
        boolean answers = terminal && answersAvailable(terminal,answerAt,mode,closesAt,now);
        return new Release(score, answers);
    }

    static boolean answersAvailable(boolean hasTerminalAttempt, Instant answerAt, String mode,
                                    Instant closesAt, Instant now) {
        return answerAt != null && !now.isBefore(answerAt)
                && (("AFTER_VALID_SUBMISSION".equals(mode) && hasTerminalAttempt)
                || ("AFTER_ASSIGNMENT_CLOSE".equals(mode) && !now.isBefore(closesAt)));
    }

    static BigDecimal bestScore(List<ExamDtos.AttemptResult> attempts) {
        return attempts.stream().filter(a -> "SUBMITTED".equals(a.status()) || "TIMED_OUT".equals(a.status()))
                .map(ExamDtos.AttemptResult::normalizedScore).filter(java.util.Objects::nonNull)
                .max(BigDecimal::compareTo).orElse(null);
    }

    /** Digest is calculated from server-ACKed data; clients never assert what the server received. */
    static String digest(List<DigestAnswer> answers) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            answers.stream().sorted(Comparator.comparing(a -> a.questionId().toString())).forEach(a -> {
                add(digest, a.questionId().toString());
                add(digest, a.selectedOptionKey());
                digest.update(ByteBuffer.allocate(Long.BYTES).putLong(a.revision()).array());
            });
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static void add(MessageDigest digest, String value) {
        byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value == null ? -1 : bytes.length).array());
        digest.update(bytes);
    }
}
