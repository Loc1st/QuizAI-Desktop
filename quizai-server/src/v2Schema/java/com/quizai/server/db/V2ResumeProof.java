package com.quizai.server.db;

import org.springframework.boot.json.JsonParserFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

/** Recovery of this specific install that stopped before its first catalog attestation.
 * No JDBC, no migration execution, and no adoption of arbitrary existing databases. */
record V2ResumeProof(String digest, String installedRelease) {
    private static final Path RUN = Path.of("../.gradle/v2-deployment-resume/runs/20261112T215240627-0746a030");
    private static final Path ORIGINAL = Path.of("../.gradle/v2-sql42725/installed-release.properties");
    private static final String INSTALLED = "a3a455fddfad59166eba12c91b73f53d72b4fcbb71b070a064c8b61bf6f511f4";

    static V2ResumeProof current(V2ReleaseManifest release) throws Exception {
        require(Files.isRegularFile(ORIGINAL), "Original installed release proof missing");
        require(INSTALLED.equals(V2ReleaseManifest.sha(Files.readAllBytes(ORIGINAL))), "Original installed release changed");
        return verify(RUN, ORIGINAL, Path.of("build/v2-database-release"), release);
    }

    static V2ResumeProof verify(Path run, Path original, Path snapshots, V2ReleaseManifest release) throws Exception {
        Map<String, String> hashes = new TreeMap<>();
        String beforeText = read(run.resolve("runtime-state.before.json"), hashes);
        Map<String, Object> before = JsonParserFactory.getJsonParser().parseMap(beforeText);
        require(Files.isRegularFile(original), "Original installed release missing");
        byte[] bytes = Files.readAllBytes(original);
        String installed = V2ReleaseManifest.sha(bytes);
        require(installed.equals(before.get("FrozenManifestSha256")), "Failed run was not using the archived release");
        hashes.put("installed-release", installed);
        Properties prior = new Properties();
        try (var input = Files.newInputStream(original)) { prior.load(input); }
        require("quizai-v2-release-1".equals(prior.getProperty("format")), "Invalid archived release format");
        Properties current = release.properties();
        require(migrations(prior).size() == 15 && migrations(prior).equals(migrations(current)),
                "Applied migration names/checksums/SQL bytes changed; recovery refused");
        Path sql = snapshots.resolve(installed).resolve("db/migration-v2");
        require(Files.isDirectory(sql), "Original installed five-file snapshot missing");
        try (var entries = Files.list(sql)) { require(entries.count() == 5, "Unexpected installed snapshot entry"); }
        for (int i=1; i<=5; i++) {
            String script = prior.getProperty("migration." + i + ".script");
            Path file = sql.resolve(script);
            require(Files.isRegularFile(file), "Installed SQL snapshot missing");
            String sha = V2ReleaseManifest.sha(Files.readAllBytes(file));
            require(sha.equals(prior.getProperty("migration." + i + ".sha256")), "Installed SQL snapshot drift");
            hashes.put(script, sha);
        }
        List<?> targets = array(before.get("Databases"));
        require(targets.size() == 2, "Expected both original dedicated target observations");
        var seen = new java.util.HashSet<String>();
        for (Object value : targets) {
            Map<?,?> target = object(value);
            String name = String.valueOf(target.get("Database"));
            require(Set.of("quizai_v2_test","quizai_v2_dev").contains(name) && seen.add(name), "Unexpected original target");
            Map<?,?> identity = object(target.get("IdentityCatalog"));
            require(name.equals(identity.get("database")) && name.equals(identity.get("current_user"))
                            && name.equals(identity.get("session_user")) && name.equals(identity.get("owner"))
                            && Boolean.TRUE.equals(identity.get("role_safe")), "Original dedicated owner/login proof rejected");
            require(Boolean.FALSE.equals(identity.get("history_exists"))
                            && array(identity.get("business_tables")).isEmpty() && array(target.get("History")).isEmpty(),
                    "Original databases were not empty before this failed install");
        }
        String verify = read(run.resolve("verify-release.log"), hashes);
        require(verify.contains("OFFLINE verifyRelease") && verify.contains("BUILD SUCCESSFUL"), "Original release verification did not pass");
        String inspect = read(run.resolve("inspect.log"), hashes);
        require(inspect.contains("Read-only TEST inspection passed") && inspect.contains("BUILD SUCCESSFUL"), "Original TEST inspection did not pass");
        String deployment = read(run.resolve("deployment.log"), hashes);
        require(deployment.contains("Database: jdbc:postgresql://127.0.0.1:5432/quizai_v2_test (PostgreSQL 18")
                        && deployment.contains("Successfully applied 5 migrations to schema \"public\", now at version v5")
                        && deployment.contains("SQLSTATE=42725") && deployment.contains("BUILD FAILED")
                        && !deployment.contains("jdbc:postgresql://127.0.0.1:5432/quizai_v2_dev"),
                "Evidence is not the known TEST v5 catalog-query failure with DEV held");
        return new V2ResumeProof(V2ReleaseManifest.sha(hashes.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)), installed);
    }

    private static Map<String,String> migrations(Properties values) {
        Map<String,String> result = new TreeMap<>();
        values.forEach((key,value) -> { if (key.toString().startsWith("migration.")) result.put(key.toString(),value.toString()); });
        return result;
    }
    private static String read(Path path, Map<String,String> hashes) throws Exception {
        require(Files.isRegularFile(path), "Required failed-run evidence missing");
        byte[] bytes = Files.readAllBytes(path);
        hashes.put(path.getFileName().toString(),V2ReleaseManifest.sha(bytes));
        // PowerShell 5 Tee-Object writes UTF-16LE; SQL/log files can also be UTF-8.
        if (bytes.length>=2 && bytes[0]==(byte)0xff && bytes[1]==(byte)0xfe) {
            return new String(bytes,2,bytes.length-2,java.nio.charset.StandardCharsets.UTF_16LE);
        }
        String text = new String(bytes,java.nio.charset.StandardCharsets.UTF_8);
        return text.startsWith("\ufeff") ? text.substring(1) : text;
    }
    private static Map<?,?> object(Object value) { require(value instanceof Map<?,?>,"Malformed recovery object"); return (Map<?,?>) value; }
    private static List<?> array(Object value) { require(value instanceof List<?>,"Malformed recovery array"); return (List<?>) value; }
    private static void require(boolean condition, String message) { V2DatabaseSafety.require(condition,message); }
}
