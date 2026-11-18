package com.quizai.server.v2.exams;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Completes offline/disconnected attempts using only server-ACKed answers, including blank attempts. */
@Component
@Profile({"v2-dev", "v2-test"})
public class ExamTimeoutFinalizer {
    private final ExamStore db;
    public ExamTimeoutFinalizer(ExamStore db) { this.db=db; }

    @Scheduled(fixedDelayString = "${quizai.v2.attempt-timeout-scan-ms:5000}", initialDelayString = "${quizai.v2.attempt-timeout-scan-ms:5000}")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void finalizeExpired() {
        db.lock();
        db.finalizeExpired(null,null);
    }
}
