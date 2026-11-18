package com.quizai.server.v2.ai;

import com.quizai.server.security.SessionPrincipal;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v2/practice")
@Profile({"v2-dev", "v2-test"})
public class PracticeController {
    private final PracticeService practice;
    public PracticeController(PracticeService practice) { this.practice = practice; }
    @GetMapping("/quizzes") public ResponseEntity<List<StudyDtos.PrivateQuiz>> quizzes(@AuthenticationPrincipal SessionPrincipal actor) { return ok(practice.quizzes(actor)); }
    @GetMapping public ResponseEntity<List<StudyDtos.Practice>> history(@AuthenticationPrincipal SessionPrincipal actor) { return ok(practice.history(actor)); }
    @PostMapping("/{quizId}/start") public ResponseEntity<StudyDtos.Practice> start(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID quizId) { return ok(practice.start(actor, quizId)); }
    @GetMapping("/{sessionId}") public ResponseEntity<StudyDtos.Practice> get(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID sessionId) { return ok(practice.get(actor, sessionId)); }
    @PutMapping("/{sessionId}/answers/{questionId}") public ResponseEntity<StudyDtos.Practice> answer(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID sessionId, @PathVariable UUID questionId, @RequestBody StudyDtos.Answer request) { return ok(practice.answer(actor, sessionId, questionId, request)); }
    @PostMapping("/{sessionId}/complete") public ResponseEntity<StudyDtos.Practice> complete(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID sessionId) { return ok(practice.complete(actor, sessionId)); }
    private static <T> ResponseEntity<T> ok(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
}
