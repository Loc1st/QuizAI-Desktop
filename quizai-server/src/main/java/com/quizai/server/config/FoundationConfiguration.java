package com.quizai.server.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import java.time.Clock;
import java.util.Arrays;

@Configuration(proxyBeanMethods = false)
@Profile({"dev", "test"})
public class FoundationConfiguration {
    @Bean
    Clock applicationClock() { return Clock.systemUTC(); }

    @Bean
    static BeanFactoryPostProcessor localDatabaseTargetGuard(Environment environment) {
        // Runs before datasource/repository instantiation; reject wrong targets before JDBC.
        var active = Arrays.asList(environment.getActiveProfiles());
        if (active.contains("dev") == active.contains("test")) {
            throw new IllegalStateException("Select exactly one authorized profile: dev or test");
        }
        LocalDatabaseTarget.validate(active.contains("dev") ? "dev" : "test",
                environment.getProperty("spring.datasource.url"),
                environment.getProperty("spring.datasource.username"),
                environment.getProperty("spring.datasource.password"));
        if (!"false".equalsIgnoreCase(environment.getProperty("spring.flyway.enabled"))
                || !"never".equals(environment.getProperty("spring.sql.init.mode"))) {
            throw new IllegalStateException("Application startup must not execute migrations or SQL initialization");
        }
        return beanFactory -> { };
    }
}
