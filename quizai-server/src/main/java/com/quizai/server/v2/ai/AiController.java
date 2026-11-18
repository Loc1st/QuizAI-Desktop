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
@RequestMapping("/api/v2/ai")
@Profile({"v2-dev", "v2-test"})
public class AiController {
    private final GenerationService generation;
    private final TutorService tutor;
    public AiController(GenerationService generation, TutorService tutor) { this.generation = generation; this.tutor = tutor; }
    @PostMapping("/generate") public ResponseEntity<StudyDtos.Generated> generate(@AuthenticationPrincipal SessionPrincipal actor, @RequestBody StudyDtos.Generate request) { return ok(generation.generate(actor, request)); }
    @PostMapping("/conversations") public ResponseEntity<StudyDtos.Conversation> create(@AuthenticationPrincipal SessionPrincipal actor, @RequestBody StudyDtos.ConversationRequest request) { return ok(tutor.create(actor, request)); }
    @GetMapping("/conversations") public ResponseEntity<List<StudyDtos.Conversation>> conversations(@AuthenticationPrincipal SessionPrincipal actor) { return ok(tutor.list(actor)); }
    @GetMapping("/conversations/{id}/messages") public ResponseEntity<List<StudyDtos.Message>> history(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID id) { return ok(tutor.messages(actor, id)); }
    @PostMapping("/conversations/{id}/messages") public ResponseEntity<StudyDtos.Message> message(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID id, @RequestBody StudyDtos.MessageRequest request) { return ok(tutor.message(actor, id, request)); }
    private static <T> ResponseEntity<T> ok(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
}
