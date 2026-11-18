package com.quizai.server.web;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Profile({"dev", "test", "v2-dev", "v2-test"})
public class HealthController {
    private final JdbcTemplate jdbc;

    public HealthController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/api/health")
    public Map<String, String> health() {
        String database = jdbc.queryForObject("SELECT current_database()", String.class);
        return Map.of("status", "UP", "database", database);
    }
}
