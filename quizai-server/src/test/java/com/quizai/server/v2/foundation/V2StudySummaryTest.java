package com.quizai.server.v2.foundation;

import com.quizai.server.security.SessionPrincipal;
import com.quizai.server.web.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class V2StudySummaryTest {
    private SessionPrincipal actor(String role) { return new SessionPrincipal(UUID.randomUUID(),"unit@example.test",role,UUID.randomUUID(),Instant.now().plusSeconds(60)); }
    @Test void privateAggregateRequiresTeacherBeforeAnyDatabaseRead() {
        var jdbc=mock(JdbcTemplate.class); var service=new V2StudySummaryService(jdbc);
        assertEquals(401,assertThrows(ApiException.class,()->service.summary(null,UUID.randomUUID())).status());
        assertEquals(403,assertThrows(ApiException.class,()->service.summary(actor("STUDENT"),UUID.randomUUID())).status());
        verifyNoInteractions(jdbc);
    }
    @Test void foreignClassIsHiddenBeforePrivateAggregateQuery() {
        var jdbc=mock(JdbcTemplate.class); var service=new V2StudySummaryService(jdbc);
        assertEquals(404,assertThrows(ApiException.class,()->service.summary(actor("TEACHER"),UUID.randomUUID())).status());
        verify(jdbc).queryForObject(anyString(),eq(Boolean.class),any(UUID.class),any(UUID.class));
        verifyNoMoreInteractions(jdbc);
    }
    @Test void emptyPracticeIsUnknownAccuracyAndZeroCorrectIsARealZero() {
        assertNull(V2StudySummaryService.accuracy(0,0));
        assertEquals(new java.math.BigDecimal("0.000000"),V2StudySummaryService.accuracy(0,5));
        assertEquals(new java.math.BigDecimal("0.666667"),V2StudySummaryService.accuracy(2,3));
    }
}
