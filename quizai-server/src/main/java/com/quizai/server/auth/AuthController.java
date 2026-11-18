package com.quizai.server.auth;

import com.quizai.server.security.SessionPrincipal;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Profile({"dev", "test", "v2-dev", "v2-test"})
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponses.User> register(@Valid @RequestBody AuthRequests.Register request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(auth.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponses.Login> login(@Valid @RequestBody AuthRequests.Login request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(auth.login(request));
    }

    @GetMapping("/session")
    public ResponseEntity<AuthResponses.Session> session(@AuthenticationPrincipal SessionPrincipal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new AuthResponses.Session(principal.expiresAt(), AuthResponses.User.from(principal)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal SessionPrincipal principal) {
        auth.logout(principal);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
