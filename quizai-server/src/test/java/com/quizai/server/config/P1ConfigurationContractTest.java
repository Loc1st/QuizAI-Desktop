package com.quizai.server.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Static source checks only: no Spring context, environment lookup or database access. */
class P1ConfigurationContractTest {
    private static final String DATASOURCE =
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration";
    private static final String FLYWAY =
            "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration";
    private static final String SAFE_BASE = """
            spring:
              autoconfigure:
                exclude:
                  - org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
                  - org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration
              datasource:
                embedded-database-connection: none
              flyway:
                enabled: false
                clean-disabled: true
                baseline-on-migrate: false
              sql:
                init:
                  mode: never
              jpa:
                generate-ddl: false
                hibernate:
                  ddl-auto: none
            """;

    @Test
    void actualDefaultConfigurationDisablesDatabaseInitialization() throws IOException {
        assertTrue(baseErrors(parse(read("src/main/resources/application.yml"))).isEmpty());
    }

    @Test
    void actualDevAndTestConfigurationsUseIsolatedRequiredEnvironmentVariables() throws IOException {
        Map<String, Object> base = parse(read("src/main/resources/application.yml"));
        for (String environment : List.of("DEV", "TEST")) {
            String path = environment.equals("DEV")
                    ? "src/main/resources/application-dev.yml"
                    : "src/test/resources/application-test.yml";
            List<String> errors = profileErrors(base, parse(read(path)), environment);
            assertTrue(errors.isEmpty(), () -> String.join("; ", errors));
        }
    }

    @Test
    void environmentDocumentNamesAllSixRequiredVariables() throws IOException {
        String document = Files.readString(moduleRoot().resolve("../docs/archive/db-v1/QUIZAI_DB_P1_ENVIRONMENT_v1.0.md"));
        assertTrue(documentErrors(document).isEmpty());
        assertEquals(List.of("Missing documentation for QUIZAI_TEST_DB_PASSWORD"),
                documentErrors(environmentNames().replace("QUIZAI_TEST_DB_PASSWORD", "")));
    }

    @Test
    void safeSyntheticProfilesInheritInitializationGuards() {
        Map<String, Object> base = parse(SAFE_BASE);
        assertTrue(baseErrors(base).isEmpty());
        for (String environment : List.of("DEV", "TEST")) {
            List<String> errors = profileErrors(base, parse(profile(environment)), environment);
            assertTrue(errors.isEmpty(), () -> String.join("; ", errors));
        }
    }

    @Test
    void profileExclusionListReplacesDefaultListWithoutMutatingIt() {
        Map<String, Object> base = parse(SAFE_BASE);
        Map<String, Object> effective = merge(base, parse(profile("DEV")));
        assertEquals(List.of(FLYWAY), at(effective, "spring", "autoconfigure", "exclude"));
        assertEquals(List.of(DATASOURCE, FLYWAY), at(base, "spring", "autoconfigure", "exclude"));
        assertEquals(false, at(effective, "spring", "flyway", "enabled"));
    }

    @Test
    void rejectsDefaultDatasourceAndCommentOnlyExclusions() {
        String configured = SAFE_BASE.replace("embedded-database-connection: none",
                "embedded-database-connection: none\n    url: jdbc:postgresql://localhost/fixture");
        assertError(baseErrors(parse(configured)), "Default datasource.url must be absent");
        assertError(baseErrors(parse(SAFE_BASE.replace("- " + DATASOURCE, "# " + DATASOURCE))),
                "Default exclusions must contain exactly the Boot 4 datasource and Flyway classes");
        assertError(baseErrors(parse(SAFE_BASE.replace("enabled: false", "enabled: true"))),
                "Default spring.flyway.enabled must be false");
    }

    @Test
    void rejectsUnsafeProfileInitializationOverrides() {
        Map<String, Object> base = parse(SAFE_BASE);
        Map<String, String> overrides = Map.of(
                "flyway:\n    enabled: true", "DEV spring.flyway.enabled must be false",
                "sql:\n    init:\n      mode: always", "DEV spring.sql.init.mode must be never",
                "jpa:\n    hibernate:\n      ddl-auto: create", "DEV spring.jpa.hibernate.ddl-auto must be none or validate",
                "flyway:\n    clean-disabled: false", "DEV spring.flyway.clean-disabled must be true",
                "flyway:\n    baseline-on-migrate: true", "DEV spring.flyway.baseline-on-migrate must be false");
        overrides.forEach((override, error) -> assertError(
                profileErrors(base, parse(profile("DEV") + "  " + override + "\n"), "DEV"), error));
    }

    @Test
    void rejectsCrossEnvironmentVariablesForEveryDatasourceField() {
        for (String environment : List.of("DEV", "TEST")) {
            String other = environment.equals("DEV") ? "TEST" : "DEV";
            for (String suffix : List.of("URL", "USERNAME", "PASSWORD")) {
                String source = profile(environment).replace("QUIZAI_" + environment + "_DB_" + suffix,
                        "QUIZAI_" + other + "_DB_" + suffix);
                assertTrue(profileErrors(parse(SAFE_BASE), parse(source), environment).stream()
                        .anyMatch(error -> error.contains("exactly ${QUIZAI_" + environment + "_DB_" + suffix + "}")));
            }
        }
    }

    @Test
    void rejectsMissingVariablesFallbackDefaultsAndHardcodedCredentials() {
        for (String environment : List.of("DEV", "TEST")) {
            for (String field : List.of("url", "username", "password")) {
                String source = profile(environment);
                String missing = source.replaceAll("(?m)^    " + field + ":.*\\R", "");
                assertTrue(profileErrors(parse(SAFE_BASE), parse(missing), environment).stream()
                        .anyMatch(error -> error.startsWith(environment + " datasource." + field)));
                String fallback = source.replaceFirst("(?m)(^    " + field + ": \\$\\{[^}]+)(})", "$1:fallback$2");
                assertTrue(profileErrors(parse(SAFE_BASE), parse(fallback), environment).stream()
                        .anyMatch(error -> error.startsWith(environment + " datasource." + field)));
                String hardcoded = source.replaceAll("(?m)^    " + field + ":.*$", "    " + field + ": fixture-value");
                assertTrue(profileErrors(parse(SAFE_BASE), parse(hardcoded), environment).stream()
                        .anyMatch(error -> error.startsWith(environment + " datasource." + field)));
            }
            assertError(profileErrors(parse(SAFE_BASE),
                    parse(profile(environment).replace("_DB_USERNAME}", "_DB_USER}")), environment),
                    environment + " datasource.username must be exactly ${QUIZAI_" + environment + "_DB_USERNAME}");
        }
    }

    @Test
    void rejectsWrongDriverAndProfileExclusions() {
        assertError(profileErrors(parse(SAFE_BASE),
                        parse(profile("DEV").replace("org.postgresql.Driver", "fixture.Driver")), "DEV"),
                "DEV datasource.driver-class-name must be org.postgresql.Driver");
        assertError(profileErrors(parse(SAFE_BASE),
                        parse(profile("DEV").replace("- " + FLYWAY, "- " + DATASOURCE)), "DEV"),
                "DEV exclusions must contain only the Boot 4 Flyway class");
    }

    @Test
    void rejectsAutomaticProfileActivationInDefaultAndProfileFiles() {
        for (String setting : List.of("active: dev", "active: [dev, test]", "default: dev", "include: test")) {
            assertError(baseErrors(parse(SAFE_BASE + "  profiles:\n    " + setting + "\n")),
                    "Default must not configure automatic profiles");
            assertError(profileErrors(parse(SAFE_BASE),
                            parse(profile("DEV") + "  profiles:\n    " + setting + "\n"), "DEV"),
                    "DEV must not configure automatic profiles");
        }
    }

    @Test
    void rejectsMalformedDuplicateAndMultipleYamlDocuments() {
        assertThrows(YAMLException.class, () -> parse("spring: [\n"));
        assertThrows(YAMLException.class, () -> parse("spring:\n  flyway:\n    enabled: false\n    enabled: true\n"));
        assertThrows(YAMLException.class, () -> parse("spring:\n  flyway: {}\nspring:\n  datasource: {}\n"));
        assertThrows(YAMLException.class, () -> parse(SAFE_BASE + "---\nspring: {}\n"));
        assertThrows(IllegalArgumentException.class, () -> parse("- spring\n"));
    }

    private static List<String> baseErrors(Map<String, Object> base) {
        List<String> errors = new ArrayList<>();
        Object exclusions = at(base, "spring", "autoconfigure", "exclude");
        if (!(exclusions instanceof List<?> list) || list.size() != 2
                || !list.containsAll(List.of(DATASOURCE, FLYWAY))) {
            errors.add("Default exclusions must contain exactly the Boot 4 datasource and Flyway classes");
        }
        for (String field : List.of("url", "username", "password", "driver-class-name")) {
            if (at(base, "spring", "datasource", field) != null) {
                errors.add("Default datasource." + field + " must be absent");
            }
        }
        initializationErrors(base, "Default", errors);
        automaticProfileErrors(base, "Default", errors);
        return errors;
    }

    private static List<String> profileErrors(Map<String, Object> base, Map<String, Object> profile,
                                               String environment) {
        List<String> errors = new ArrayList<>(baseErrors(base));
        Map<String, Object> effective = merge(base, profile);
        require(effective, List.of(FLYWAY), environment + " exclusions must contain only the Boot 4 Flyway class",
                errors, "spring", "autoconfigure", "exclude");
        require(effective, "org.postgresql.Driver", environment + " datasource.driver-class-name must be org.postgresql.Driver",
                errors, "spring", "datasource", "driver-class-name");
        for (String field : List.of("url", "username", "password")) {
            String expected = "${QUIZAI_" + environment + "_DB_" + field.toUpperCase(java.util.Locale.ROOT) + "}";
            require(effective, expected, environment + " datasource." + field + " must be exactly " + expected,
                    errors, "spring", "datasource", field);
        }
        initializationErrors(effective, environment, errors);
        automaticProfileErrors(profile, environment, errors);
        return errors;
    }

    private static void initializationErrors(Map<String, Object> configuration, String label, List<String> errors) {
        require(configuration, "none", label + " embedded database selection must be disabled", errors,
                "spring", "datasource", "embedded-database-connection");
        require(configuration, false, label + " spring.flyway.enabled must be false", errors,
                "spring", "flyway", "enabled");
        require(configuration, true, label + " spring.flyway.clean-disabled must be true", errors,
                "spring", "flyway", "clean-disabled");
        require(configuration, false, label + " spring.flyway.baseline-on-migrate must be false", errors,
                "spring", "flyway", "baseline-on-migrate");
        require(configuration, "never", label + " spring.sql.init.mode must be never", errors,
                "spring", "sql", "init", "mode");
        require(configuration, false, label + " spring.jpa.generate-ddl must be false", errors,
                "spring", "jpa", "generate-ddl");
        Object ddl = at(configuration, "spring", "jpa", "hibernate", "ddl-auto");
        if (!Objects.equals("none", ddl) && !Objects.equals("validate", ddl)) {
            errors.add(label + " spring.jpa.hibernate.ddl-auto must be none or validate");
        }
    }

    private static void automaticProfileErrors(Map<String, Object> configuration, String label, List<String> errors) {
        if (at(configuration, "spring", "profiles") != null) {
            errors.add(label + " must not configure automatic profiles");
        }
    }

    private static void require(Map<String, Object> configuration, Object expected, String error,
                                List<String> errors, String... path) {
        if (!Objects.equals(expected, at(configuration, path))) {
            errors.add(error);
        }
    }

    private static Object at(Map<String, Object> configuration, String... path) {
        Object value = configuration;
        for (String key : path) {
            if (!(value instanceof Map<?, ?> mapping)) {
                return null;
            }
            value = mapping.get(key);
        }
        return value;
    }

    private static Map<String, Object> parse(String source) {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        return stringMap(new Yaml(new SafeConstructor(options)).load(source));
    }

    private static Map<String, Object> stringMap(Object value) {
        if (!(value instanceof Map<?, ?> mapping)) {
            throw new IllegalArgumentException("YAML document must be a mapping");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        mapping.forEach((key, item) -> {
            if (!(key instanceof String name)) {
                throw new IllegalArgumentException("YAML keys must be strings");
            }
            result.put(name, item);
        });
        return result;
    }

    private static Map<String, Object> merge(Map<String, Object> base, Map<String, Object> profile) {
        Map<String, Object> merged = new LinkedHashMap<>(base);
        profile.forEach((key, value) -> {
            Object inherited = merged.get(key);
            merged.put(key, inherited instanceof Map<?, ?> && value instanceof Map<?, ?>
                    ? merge(stringMap(inherited), stringMap(value)) : value);
        });
        return merged;
    }

    private static String profile(String environment) {
        return """
                spring:
                  autoconfigure:
                    exclude:
                      - org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration
                  datasource:
                    driver-class-name: org.postgresql.Driver
                    url: ${QUIZAI_%1$s_DB_URL}
                    username: ${QUIZAI_%1$s_DB_USERNAME}
                    password: ${QUIZAI_%1$s_DB_PASSWORD}
                """.formatted(environment);
    }

    private static List<String> documentErrors(String document) {
        List<String> errors = new ArrayList<>();
        for (String name : environmentNames().split("\n")) {
            if (!document.contains(name)) {
                errors.add("Missing documentation for " + name);
            }
        }
        return errors;
    }

    private static String environmentNames() {
        return """
                QUIZAI_DEV_DB_URL
                QUIZAI_DEV_DB_USERNAME
                QUIZAI_DEV_DB_PASSWORD
                QUIZAI_TEST_DB_URL
                QUIZAI_TEST_DB_USERNAME
                QUIZAI_TEST_DB_PASSWORD
                """;
    }

    private static void assertError(List<String> errors, String expected) {
        assertTrue(errors.contains(expected), () -> "Expected: " + expected + "; actual: " + errors);
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(moduleRoot().resolve(relativePath));
    }

    private static Path moduleRoot() {
        return Files.isRegularFile(Path.of("src/main/resources/application.yml"))
                ? Path.of(".") : Path.of("quizai-server");
    }
}
