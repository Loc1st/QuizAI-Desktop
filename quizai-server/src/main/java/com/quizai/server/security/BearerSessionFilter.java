package com.quizai.server.security;

import com.quizai.server.auth.AuthService;
import com.quizai.server.web.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** Created only inside the security chain, avoiding a second servlet-filter registration. */
public final class BearerSessionFilter extends OncePerRequestFilter {
    private final AuthService auth;

    public BearerSessionFilter(AuthService auth) {
        this.auth = auth;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        var headers = request.getHeaders("Authorization");
        if (headers == null || !headers.hasMoreElements()) {
            chain.doFilter(request, response);
            return;
        }
        String header = headers.nextElement();
        if (headers.hasMoreElements() || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            SecurityJson.unauthorized(response);
            return;
        }
        SessionPrincipal principal;
        try {
            principal = auth.authenticate(header.substring(7));
        } catch (ApiException invalidSession) {
            SecurityContextHolder.clearContext();
            SecurityJson.unauthorized(response);
            return;
        }
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + principal.role()))));
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }
}
