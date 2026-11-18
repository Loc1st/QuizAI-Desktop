package com.quizai.server.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

@Component
@Profile({"dev", "test"})
public class DatabaseReadiness implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final Environment environment;

    public DatabaseReadiness(JdbcTemplate jdbc, Environment environment) {
        this.jdbc = jdbc;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments ignored) {
        String database = Arrays.asList(environment.getActiveProfiles()).contains("dev") ? "quizai_dev" : "quizai_test";
        Boolean identity = jdbc.queryForObject("""
                SELECT current_database()=? AND current_user=? AND pg_get_userbyid(d.datdba)=current_user
                  AND current_setting('server_version_num')::int / 10000 = 18
                  AND NOT r.rolsuper AND inet_server_port()=5432
                FROM pg_database d JOIN pg_roles r ON r.rolname=current_user WHERE d.datname=current_database()
                """, Boolean.class, database, database);
        Integer tables = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema='public' AND table_type='BASE TABLE' AND table_name<>'flyway_schema_history'
                """, Integer.class);
        Set<String> versions = Set.copyOf(jdbc.queryForList("SELECT version FROM public.flyway_schema_history WHERE success AND type='SQL'", String.class));
        Integer history = jdbc.queryForObject("SELECT count(*) FROM public.flyway_schema_history", Integer.class);
        if (!Boolean.TRUE.equals(identity) || !Integer.valueOf(37).equals(tables) || history == null || history != 8
                || !versions.equals(Set.of("1", "2", "3", "4", "5", "6", "7", "8"))) {
            throw new IllegalStateException("Local database identity/schema readiness check failed; no migration/repair attempted");
        }
    }
}
