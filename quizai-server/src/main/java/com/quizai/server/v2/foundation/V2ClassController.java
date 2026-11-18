package com.quizai.server.v2.foundation;

import com.quizai.server.classes.*;
import com.quizai.server.security.SessionPrincipal;
import com.quizai.server.web.ApiException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/classes")
@Profile({"v2-dev", "v2-test"})
public class V2ClassController {
    private final ClassService service;
    private final V2ClassRepository repository;
    public V2ClassController(ClassService service, V2ClassRepository repository) { this.service = service; this.repository = repository; }
    public record Create(String name) { }
    public record Update(String name, String status) { }
    public record Join(String code) { }
    public record Enabled(Boolean enabled) { }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ClassView create(@AuthenticationPrincipal SessionPrincipal actor, @RequestBody Create request) { return service.create(actor, request.name()); }
    @GetMapping public List<ClassView> owned(@AuthenticationPrincipal SessionPrincipal actor) { return service.listOwned(actor); }
    @GetMapping("/{id}") public ClassView get(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID id) { return service.get(actor, id); }
    @PatchMapping("/{id}") public ClassView update(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID id, @RequestBody Update request) {
        if (request.status() != null && !"ACTIVE".equals(request.status())) throw new ApiException(400, "Unsupported V2 class state");
        return service.update(actor, id, request.name(), null);
    }
    @PutMapping("/{id}/join-code") public ClassView enabled(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID id, @RequestBody Enabled request) {
        if (request.enabled() == null) throw new ApiException(400, "Join enabled flag is required");
        return service.setJoinEnabled(actor, id, request.enabled());
    }
    @PostMapping("/{id}/join-code/rotate") public ClassView rotate(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID id) { return service.rotateJoinCode(actor, id); }
    @GetMapping("/{id}/members") public List<ClassMemberView> members(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID id) { return service.members(actor, id); }
    @PostMapping("/join") @Transactional
    public JoinedClassView join(@AuthenticationPrincipal SessionPrincipal actor, @RequestBody Join request) {
        JoinedClassView joined = service.join(actor, request.code());
        // V5 assigns joined_at from PostgreSQL. Return the persisted membership episode.
        return repository.joined(actor.userId()).stream().filter(c -> c.id().equals(joined.id())).findFirst()
                .orElseThrow(() -> new ApiException(404, "Membership not found"));
    }
    @GetMapping("/joined") @Transactional(readOnly = true)
    public List<JoinedClassView> joined(@AuthenticationPrincipal SessionPrincipal actor) {
        if (actor == null) throw new ApiException(401, "Authentication required");
        if (!"STUDENT".equals(actor.role())) throw new ApiException(403, "Access denied");
        return repository.joined(actor.userId());
    }
    @PostMapping("/{id}/leave") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID id) { service.leave(actor, id); }
}
