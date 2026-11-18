package com.quizai.server.v2.ai;

import com.quizai.server.security.SessionPrincipal;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/v2/documents")
@Profile({"v2-dev", "v2-test"})
public class DocumentController {
    private final DocumentService documents;
    public DocumentController(DocumentService documents) { this.documents = documents; }
    @GetMapping public ResponseEntity<List<StudyDtos.Document>> list(@AuthenticationPrincipal SessionPrincipal actor) { return ok(documents.list(actor)); }
    @PostMapping("/paste") public ResponseEntity<StudyDtos.Document> paste(@AuthenticationPrincipal SessionPrincipal actor, @RequestBody StudyDtos.Paste request) { return ok(documents.paste(actor, request)); }
    @PostMapping(value = "/upload", consumes = "multipart/form-data") public ResponseEntity<StudyDtos.Document> upload(@AuthenticationPrincipal SessionPrincipal actor, @RequestPart("file") MultipartFile file) { return ok(documents.upload(actor, file)); }
    private static <T> ResponseEntity<T> ok(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
}
