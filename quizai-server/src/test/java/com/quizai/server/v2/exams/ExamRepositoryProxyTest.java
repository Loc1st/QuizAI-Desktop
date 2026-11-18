package com.quizai.server.v2.exams;

import com.quizai.server.security.SessionPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExamRepositoryProxyTest {
    @Test void actualSpringRepositoryProxySupportsBankCreationAndListing() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("v2-test");
            JdbcTemplate jdbc = mock(JdbcTemplate.class);
            context.registerBean(JdbcTemplate.class, () -> jdbc);
            context.registerBean(PersistenceExceptionTranslationPostProcessor.class);
            context.register(ExamStore.class, TeacherExamService.class);
            context.refresh();
            assertTrue(AopUtils.isAopProxy(context.getBean(ExamStore.class)));
            assertSame(jdbc, context.getBean(ExamStore.class).jdbc());
            var actor = new SessionPrincipal(UUID.randomUUID(), "teacher@unit.test", "TEACHER", UUID.randomUUID(), Instant.now().plusSeconds(60));
            assertDoesNotThrow(() -> context.getBean(TeacherExamService.class).banks(actor));
            var created = new java.util.concurrent.atomic.AtomicReference<UUID>();
            doAnswer(call -> { created.set(call.getArgument(1)); return 1; }).when(jdbc).update(
                    org.mockito.ArgumentMatchers.startsWith("INSERT INTO question_banks"), any(), any(), any());
            doAnswer(call -> java.util.List.of(new ExamDtos.Bank(created.get(), "Proxy regression", null, Instant.now())))
                    .when(jdbc).query(org.mockito.ArgumentMatchers.startsWith("SELECT * FROM question_banks"),
                            org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<ExamDtos.Bank>>any(), any(UUID.class));
            assertEquals("Proxy regression", context.getBean(TeacherExamService.class)
                    .createBank(actor, new ExamDtos.BankRequest("Proxy regression")).name());
        }
    }
}
