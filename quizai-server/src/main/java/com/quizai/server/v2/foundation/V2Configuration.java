package com.quizai.server.v2.foundation;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import java.time.Clock;
import java.util.Arrays;

@Configuration(proxyBeanMethods = false)
@Profile({"v2-dev", "v2-test"})
public class V2Configuration {
    @Bean Clock applicationClock() { return Clock.systemUTC(); }

    @Bean static BeanFactoryPostProcessor v2TargetGuard(Environment environment) {
        String profile = profile(environment);
        V2Target.validate(profile, environment.getProperty("spring.datasource.url"),
                environment.getProperty("spring.datasource.username"), environment.getProperty("spring.datasource.password"));
        if (!"false".equalsIgnoreCase(environment.getProperty("spring.flyway.enabled"))
                || !"never".equals(environment.getProperty("spring.sql.init.mode"))) {
            throw new IllegalStateException("V2 application startup cannot migrate or initialize SQL");
        }
        if (environment.getProperty("quizai.classes.join-code-key", "").getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("Set QUIZAI_CLASS_JOIN_CODE_KEY to a persistent private key of at least 32 bytes");
        }
        return factory -> { };
    }

    @Bean(destroyMethod = "close") HikariDataSource dataSource(Environment environment) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(environment.getRequiredProperty("spring.datasource.url"));
        config.setUsername(environment.getRequiredProperty("spring.datasource.username"));
        config.setPassword(environment.getRequiredProperty("spring.datasource.password"));
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(8);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(10_000);
        config.setConnectionInitSql("SET search_path = public, pg_catalog");
        config.setPoolName("QuizAI-V2");
        HikariDataSource source = new HikariDataSource(config);
        try {
            V2Readiness.verify(source, "quizai_" + profile(environment).replace('-', '_'));
            return source;
        } catch (RuntimeException failure) {
            source.close();
            // Do not propagate driver messages/connection strings into startup logs.
            throw new IllegalStateException("V2 readiness failed (identity, owner, schema or Flyway checksum); no migration attempted");
        }
    }

    static String profile(Environment environment) {
        var profiles = Arrays.asList(environment.getActiveProfiles());
        if (profiles.contains("dev") || profiles.contains("test") || profiles.contains("v2-dev") == profiles.contains("v2-test")) {
            throw new IllegalStateException("Select exactly one V2 profile and never combine it with legacy dev/test");
        }
        return profiles.contains("v2-dev") ? "v2-dev" : "v2-test";
    }
}
