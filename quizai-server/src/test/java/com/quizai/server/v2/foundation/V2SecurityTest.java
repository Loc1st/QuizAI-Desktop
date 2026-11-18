package com.quizai.server.v2.foundation;

import com.quizai.server.auth.AuthService;
import com.quizai.server.security.SecurityConfiguration;
import com.quizai.server.security.SessionPrincipal;
import com.quizai.server.v2.exams.ExamController;
import com.quizai.server.v2.exams.ExamDtos;
import com.quizai.server.v2.exams.StudentExamService;
import com.quizai.server.v2.exams.TeacherExamService;
import com.quizai.server.web.ApiException;
import com.quizai.server.web.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Actual MVC and security chain with no datasource, application scan, or PostgreSQL access. */
@SpringJUnitWebConfig(V2SecurityTest.WebConfig.class)
@ActiveProfiles("v2-test")
class V2SecurityTest {
    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");
    private static final String STUDENT_TOKEN = "s".repeat(43);
    private static final String TEACHER_TOKEN = "t".repeat(43);
    private static final SessionPrincipal STUDENT = principal("STUDENT");
    private static final SessionPrincipal TEACHER = principal("TEACHER");

    @Autowired WebApplicationContext context;
    @Autowired AuthService auth;
    @Autowired TeacherExamService teacher;
    @Autowired StudentExamService student;
    @Autowired JdbcTemplate jdbc;
    private MockMvc mvc;

    @BeforeEach void setUp() {
        reset(auth, teacher, student, jdbc);
        doThrow(new ApiException(401, "Invalid session")).when(auth).authenticate(anyString());
        doReturn(STUDENT).when(auth).authenticate(STUDENT_TOKEN);
        doReturn(TEACHER).when(auth).authenticate(TEACHER_TOKEN);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        assertTrue(context.getBeansOfType(DataSource.class).isEmpty(), "Offline web tests must not create a datasource");
    }

    @Test void anonymousUsersCannotReadEitherRoleRouteOrCatalog() throws Exception {
        for (String path : List.of("/api/v2/teacher/banks", "/api/v2/student/assignments", "/api/catalog/subjects")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(auth, teacher, student, jdbc);
    }

    @Test void studentBearerCannotReachTeacherController() throws Exception {
        mvc.perform(get("/api/v2/teacher/banks").header("Authorization", bearer(STUDENT_TOKEN)))
                .andExpect(status().isForbidden());
        verify(auth).authenticate(STUDENT_TOKEN);
        verifyNoInteractions(teacher, student, jdbc);
    }

    @Test void teacherBearerCannotReachStudentController() throws Exception {
        mvc.perform(get("/api/v2/student/assignments").header("Authorization", bearer(TEACHER_TOKEN)))
                .andExpect(status().isForbidden());
        verify(auth).authenticate(TEACHER_TOKEN);
        verifyNoInteractions(teacher, student, jdbc);
    }

    @Test void teacherBearerIsDeliveredAsAuthenticatedPrincipalToActualController() throws Exception {
        UUID bankId = UUID.randomUUID();
        when(teacher.banks(TEACHER)).thenReturn(List.of(new ExamDtos.Bank(bankId, "Private bank", null, NOW)));
        mvc.perform(get("/api/v2/teacher/banks").header("Authorization", bearer(TEACHER_TOKEN)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(bankId.toString()))
                .andExpect(jsonPath("$[0].name").value("Private bank"));
        verify(teacher).banks(TEACHER);
        verifyNoInteractions(student, jdbc);
    }

    @Test void studentBearerIsDeliveredAsAuthenticatedPrincipalToActualController() throws Exception {
        when(student.assignments(STUDENT)).thenReturn(List.of());
        mvc.perform(get("/api/v2/student/assignments").header("Authorization", bearer(STUDENT_TOKEN)))
                .andExpect(status().isOk()).andExpect(content().string("[]"));
        verify(student).assignments(STUDENT);
        verifyNoInteractions(teacher, jdbc);
    }

    @Test void authenticatedRolesRemainDeniedOutsideTheAllowedApiNamespaces() throws Exception {
        for (String token : List.of(STUDENT_TOKEN, TEACHER_TOKEN)) {
            mvc.perform(get("/internal/not-exposed").header("Authorization", bearer(token)))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(teacher, student, jdbc);
    }

    @SuppressWarnings("unchecked")
    @Test void catalogIsReadableByBothAuthenticatedRoles() throws Exception {
        UUID subjectId = UUID.randomUUID();
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(
                List.of(new V2CatalogController.Subject(subjectId, "JAVA", "Java")));
        for (String token : List.of(STUDENT_TOKEN, TEACHER_TOKEN)) {
            mvc.perform(get("/api/catalog/subjects").header("Authorization", bearer(token)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(subjectId.toString()))
                    .andExpect(jsonPath("$[0].code").value("JAVA"));
        }
        verify(jdbc, times(2)).query(anyString(), any(RowMapper.class));
        verifyNoInteractions(teacher, student);
    }

    @Test void invalidSessionIsRejectedBeforeControllerDispatch() throws Exception {
        mvc.perform(get("/api/v2/student/assignments").header("Authorization", bearer("expired-token")))
                .andExpect(status().isUnauthorized());
        verify(auth).authenticate("expired-token");
        verifyNoInteractions(teacher, student, jdbc);
    }

    @Test void multipleAuthorizationHeadersCannotSelectADifferentRole() throws Exception {
        mvc.perform(get("/api/v2/teacher/banks").header("Authorization", bearer(STUDENT_TOKEN), bearer(TEACHER_TOKEN)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(auth, teacher, student, jdbc);
    }

    private static String bearer(String token) { return "Bearer " + token; }

    private static SessionPrincipal principal(String role) {
        return new SessionPrincipal(UUID.randomUUID(), role.toLowerCase(java.util.Locale.ROOT) + "@example.test",
                role, UUID.randomUUID(), NOW.plusSeconds(3600));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import({SecurityConfiguration.class, ExamController.class, V2CatalogController.class, ApiExceptionHandler.class})
    static class WebConfig {
        @Bean AuthService authService() { return mock(AuthService.class); }
        @Bean TeacherExamService teacherExamService() { return mock(TeacherExamService.class); }
        @Bean StudentExamService studentExamService() { return mock(StudentExamService.class); }
        @Bean JdbcTemplate jdbcTemplate() { return mock(JdbcTemplate.class); }
        @Bean Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }
}
