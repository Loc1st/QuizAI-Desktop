package com.quizai.server.v2.foundation;

import com.quizai.server.security.SessionPrincipal;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@Profile({"v2-dev","v2-test"})
public class V2StudySummaryController {
    private final V2StudySummaryService service;
    public V2StudySummaryController(V2StudySummaryService service) { this.service=service; }
    @GetMapping("/api/classes/{id}/study-summary")
    public ResponseEntity<List<V2StudySummaryService.StudentStudySummary>> summary(@AuthenticationPrincipal SessionPrincipal actor,@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.summary(actor,id));
    }
}
