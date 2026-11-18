package com.quizai.server.db;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

/** A reproducible offline release. A live action refuses changed source bytes. */
final class V2ReleaseManifest {
    static final Path FILE = Path.of("src/v2Schema/resources/db/v2-release.properties");
    static final Path MIGRATIONS = Path.of("src/main/resources/db/migration-v2");
    static final Path TEST_SQL = Path.of("src/test/resources/db/v2");
    private final Map<String, byte[]> files;
    private final Properties values;
    private final Map<String, String> objects;
    private final List<V2DatabaseSafety.History> history;

    private V2ReleaseManifest(Map<String, byte[]> files, Properties values,
                              Map<String, String> objects, List<V2DatabaseSafety.History> history) {
        this.files = Collections.unmodifiableMap(files);
        this.values = values;
        this.objects = Collections.unmodifiableMap(objects);
        this.history = List.copyOf(history);
    }

    static V2ReleaseManifest snapshot() throws Exception {
        var inventory = V2SchemaPreparation.inventory();
        Map<String, byte[]> files = new TreeMap<>();
        Properties values = new Properties();
        Map<String, String> objects = new TreeMap<>();
        List<V2DatabaseSafety.History> history = new ArrayList<>();
        values.setProperty("format", "quizai-v2-release-1");
        int rank = 0;
        for (String script : inventory.hashes().keySet()) {
            byte[] bytes = Files.readAllBytes(MIGRATIONS.resolve(script));
            files.put(MIGRATIONS.resolve(script).toString().replace('\\', '/'), bytes);
            rank++;
            String prefix = "migration." + rank + ".";
            values.setProperty(prefix + "script", script);
            values.setProperty(prefix + "sha256", sha(bytes));
            values.setProperty(prefix + "checksum", Integer.toString(flywayChecksum(bytes)));
            history.add(new V2DatabaseSafety.History(rank, Integer.toString(rank), "SQL", script, flywayChecksum(bytes), true));
            expectedObjects(new String(bytes, StandardCharsets.UTF_8)).forEach((key, type) -> {
                String old = objects.putIfAbsent(key, type);
                V2DatabaseSafety.require(old == null || old.equals(type), "Conflicting source object types");
            });
        }
        for (String filename : List.of("v2_catalog_check.sql", "v2_transactional_acceptance.sql", "v2_negative_acceptance.sql")) {
            Path path = TEST_SQL.resolve(filename);
            files.put(path.toString().replace('\\', '/'), Files.readAllBytes(path));
        }
        try (var sources = Files.list(Path.of("src/v2Schema/java/com/quizai/server/db"))) {
            for (Path source : sources.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                files.put(source.toString().replace('\\', '/'), Files.readAllBytes(source));
            }
        }
        Path launcher = Path.of("Invoke-V2DatabaseDeployment.ps1");
        files.put(launcher.toString(), Files.readAllBytes(launcher));
        files.forEach((path, bytes) -> values.setProperty("source." + path, sha(bytes)));
        objects.forEach((key, type) -> values.setProperty("object." + key, type));
        return new V2ReleaseManifest(files, values, objects, history);
    }

    static V2ReleaseManifest frozen() throws Exception {
        V2ReleaseManifest current = snapshot();
        Properties frozen = new Properties();
        V2DatabaseSafety.require(Files.isRegularFile(FILE), "Freeze the V2 release offline before live actions");
        try (var input = Files.newInputStream(FILE)) { frozen.load(input); }
        verifyProperties(frozen, current);
        return current;
    }

    static void freeze() throws Exception {
        var release = snapshot();
        Files.createDirectories(FILE.getParent());
        // Sorted ASCII keys/values, no clock or absolute paths: repeated freezes are byte-identical.
        Files.writeString(FILE, release.canonical(), StandardCharsets.UTF_8);
        System.out.println("OFFLINE freezeRelease: " + FILE + "; SHA256=" + release.digest());
        System.out.println("Five V2 SQL checksums and all runner/acceptance source hashes frozen; no PostgreSQL connection.");
    }

    static void verifyProperties(Properties frozen, V2ReleaseManifest current) {
        V2DatabaseSafety.require(frozen.equals(current.values), "Frozen V2 release differs from current sources");
    }

    void unchanged() throws Exception {
        verifyProperties(values, snapshot());
        Path snapshot = Path.of("build/v2-database-release", digest(), "db/migration-v2");
        if (Files.exists(snapshot)) { migrationSnapshot(); }
    }
    Properties properties() { Properties result = new Properties(); result.putAll(values); return result; }
    Map<String, String> objects() { return objects; }
    List<V2DatabaseSafety.History> history() { return history; }
    String digest() { return sha(canonical().getBytes(StandardCharsets.UTF_8)); }
    byte[] bytes(Path path) {
        byte[] bytes = files.get(path.toString().replace('\\', '/'));
        V2DatabaseSafety.require(bytes != null, "Unfrozen source requested");
        return bytes.clone();
    }
    String sql(String filename) { return new String(bytes(TEST_SQL.resolve(filename)), StandardCharsets.UTF_8); }

    Path migrationSnapshot() throws Exception {
        Path directory = Path.of("build/v2-database-release", digest(), "db/migration-v2").toAbsolutePath();
        Files.createDirectories(directory);
        for (var row : history) {
            Path source = MIGRATIONS.resolve(row.script());
            Path destination = directory.resolve(row.script());
            byte[] bytes = bytes(source);
            if (Files.exists(destination)) {
                V2DatabaseSafety.require(sha(Files.readAllBytes(destination)).equals(sha(bytes)), "Migration snapshot drift");
            } else { Files.write(destination, bytes, java.nio.file.StandardOpenOption.CREATE_NEW); }
        }
        try (var entries = Files.list(directory)) {
            V2DatabaseSafety.require(entries.count() == 5, "Unexpected migration snapshot entry");
        }
        return directory;
    }

    private String canonical() {
        var sorted = new TreeMap<String, String>();
        values.forEach((key, value) -> sorted.put(key.toString(), value.toString()));
        var text = new StringBuilder("# Frozen offline V2 release; never runtime verification evidence.\n");
        sorted.forEach((key, value) -> text.append(key.replace(":", "\\:")).append('=').append(value).append('\n'));
        return text.toString();
    }

    static String sha(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    /** Flyway 11 SQL CRC32: UTF-8, first-line BOM removed, no line terminators. */
    static int flywayChecksum(byte[] bytes) {
        CRC32 crc = new CRC32();
        try (var reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8))) {
            String line; boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (first && line.startsWith("\ufeff")) { line = line.substring(1); }
                first = false;
                crc.update(line.getBytes(StandardCharsets.UTF_8));
            }
        } catch (java.io.IOException impossible) { throw new IllegalStateException(impossible); }
        return (int) crc.getValue();
    }

    /** Restricted parser for this five-file DDL, including PostgreSQL-generated constraint names. */
    static Map<String, String> expectedObjects(String sql) {
        Map<String, String> objects = new TreeMap<>();
        String withoutComments = sql.replaceAll("(?m)--[^\\r\\n]*", "");
        var tables = Pattern.compile("(?ims)^\\s*CREATE TABLE\\s+([a-z_][a-z0-9_]*)\\s*\\((.*?)^\\s*\\);").matcher(withoutComments);
        while (tables.find()) {
            String table = tables.group(1);
            objects.put("table:" + table, "r");
            for (String field : commaParts(tables.group(2))) {
                String line = field.trim().replaceAll("\\s+", " ");
                var named = Pattern.compile("^CONSTRAINT ([a-z_][a-z0-9_]*) (.+)$", Pattern.CASE_INSENSITIVE).matcher(line);
                if (named.matches()) { constraint(objects, table, named.group(1), named.group(2)); continue; }
                var column = Pattern.compile("^([a-z_][a-z0-9_]*) (.+)$").matcher(line);
                V2DatabaseSafety.require(column.matches(), "Unsupported V2 table declaration");
                String name = column.group(1), definition = column.group(2);
                if (definition.contains("PRIMARY KEY")) { constraint(objects, table, table + "_pkey", "PRIMARY KEY"); }
                if (Pattern.compile("\\bUNIQUE\\b").matcher(definition).find()) { constraint(objects, table, table + "_" + name + "_key", "UNIQUE"); }
                if (definition.contains("REFERENCES")) { constraint(objects, table, table + "_" + name + "_fkey", "FOREIGN KEY"); }
                if (definition.contains("CHECK")) { constraint(objects, table, table + "_" + name + "_check", "CHECK"); }
            }
        }
        var alters = Pattern.compile("(?im)^\\s*ALTER TABLE\\s+([a-z_][a-z0-9_]*)\\s+ADD CONSTRAINT\\s+([a-z_][a-z0-9_]*)\\s+(CHECK|UNIQUE|FOREIGN KEY|PRIMARY KEY)").matcher(withoutComments);
        while (alters.find()) { constraint(objects, alters.group(1), alters.group(2), alters.group(3)); }
        var indexes = Pattern.compile("(?im)^\\s*CREATE (?:UNIQUE )?INDEX ([a-z_][a-z0-9_]*)\\s+ON").matcher(withoutComments);
        while (indexes.find()) { objects.put("index:" + indexes.group(1), "i"); }
        var functions = Pattern.compile("(?im)^\\s*CREATE (?:OR REPLACE )?FUNCTION ([a-z_][a-z0-9_]*)\\s*\\(").matcher(withoutComments);
        while (functions.find()) { objects.put("function:" + functions.group(1), "f"); }
        var triggers = Pattern.compile("(?ims)^\\s*CREATE (?:CONSTRAINT )?TRIGGER ([a-z_][a-z0-9_]*)\\s+(.*?)\\bON\\s+([a-z_][a-z0-9_]*)\\b").matcher(withoutComments);
        while (triggers.find()) { objects.put("trigger:" + triggers.group(3) + ":" + triggers.group(1), "t"); }
        return objects;
    }

    private static void constraint(Map<String, String> result, String table, String name, String definition) {
        String type = definition.startsWith("PRIMARY KEY") ? "p" : definition.startsWith("UNIQUE") ? "u"
                : definition.startsWith("FOREIGN KEY") ? "f" : definition.startsWith("CHECK") ? "c" : null;
        V2DatabaseSafety.require(type != null, "Unsupported V2 constraint declaration");
        result.put("constraint:" + table + ":" + name, type);
    }

    private static List<String> commaParts(String body) {
        List<String> parts = new ArrayList<>(); int start = 0, depth = 0; boolean quoted = false;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '\'') { if (quoted && i + 1 < body.length() && body.charAt(i + 1) == '\'') { i++; } else { quoted = !quoted; } }
            if (!quoted) {
                if (c == '(') { depth++; } else if (c == ')') { depth--; }
                else if (c == ',' && depth == 0) { parts.add(body.substring(start, i)); start = i + 1; }
            }
        }
        parts.add(body.substring(start)); return parts;
    }
}
