package com.quizai.server.security;

import com.quizai.server.auth.AuthService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfiguration {
    @Bean
    @Profile({"dev", "test", "v2-dev", "v2-test"})
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /** Suppresses Spring Boot's generated user/password; authentication uses database sessions only. */
    @Bean
    UserDetailsService disabledUserDetailsService() {
        return username -> { throw new UsernameNotFoundException("Password authentication is unavailable"); };
    }

    @Bean
    @Profile({"dev", "test", "v2-dev", "v2-test"})
    SecurityFilterChain applicationSecurity(HttpSecurity http, AuthService auth, Clock clock) throws Exception {
        configureStateless(http);
        var bearer = new BearerSessionFilter(auth);
        http.addFilterBefore(bearer, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new SecurityRateLimitFilter(clock), BearerSessionFilter.class)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers("/api/v2/teacher/**").hasRole("TEACHER")
                        .requestMatchers("/api/v2/student/**").hasRole("STUDENT")
                        .requestMatchers("/api/v2/**").hasAnyRole("STUDENT", "TEACHER")
                        .requestMatchers(HttpMethod.POST, "/api/classes/join").hasRole("STUDENT")
                        .requestMatchers(HttpMethod.GET, "/api/classes/joined").hasRole("STUDENT")
                        .requestMatchers(HttpMethod.POST, "/api/classes/*/leave").hasRole("STUDENT")
                        .requestMatchers(HttpMethod.GET, "/api/classes/*").hasAnyRole("STUDENT", "TEACHER")
                        .requestMatchers("/api/classes", "/api/classes/**").hasRole("TEACHER")
                        .requestMatchers("/api/catalog/**").hasAnyRole("STUDENT", "TEACHER")
                        .requestMatchers(HttpMethod.GET, "/api/auth/session", "/api/health").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout").authenticated()
                        .anyRequest().denyAll());
        return http.build();
    }

    @Bean
    @Profile("!dev & !test & !v2-dev & !v2-test")
    SecurityFilterChain disabledApplicationSecurity(HttpSecurity http) throws Exception {
        configureStateless(http);
        return http.authorizeHttpRequests(authorize -> authorize.anyRequest().denyAll()).build();
    }

    private static void configureStateless(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> SecurityJson.unauthorized(response))
                        .accessDeniedHandler((request, response, exception) -> SecurityJson.write(response, 403, "Access denied")));
    }
}
