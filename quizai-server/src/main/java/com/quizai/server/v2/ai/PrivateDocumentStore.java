package com.quizai.server.v2.ai;

import com.quizai.server.web.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;

/** Opaque UUID keys only. Nothing in this directory is exposed as a static resource. */
@Component
@Profile({"v2-dev", "v2-test"})
public class PrivateDocumentStore {
    private final Path root;

    @Autowired
    public PrivateDocumentStore(Environment environment) {
        this(Path.of(environment.getProperty("QUIZAI_V2_DOCUMENT_DIR", ".quizai-private-documents")));
    }

    PrivateDocumentStore(Path root) { this.root = root.toAbsolutePath().normalize(); }

    String write(UUID owner, UUID document, String extension, byte[] content) {
        if (!extension.equals("txt") && !extension.equals("bin")) throw new IllegalArgumentException("Invalid private extension");
        String key = owner + "/" + document + "." + extension;
        try {
            Path target = resolve(owner, key);
            Files.createDirectories(target.getParent());
            rejectSymlinks(target);
            Files.write(target, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return key;
        } catch (IOException e) { throw new ApiException(503, "Private document storage is unavailable"); }
    }

    String readText(UUID owner, String key) {
        try {
            Path path = resolve(owner, key);
            if (Files.size(path) > DocumentExtractor.MAX_TEXT_CHARS * 4L) throw new ApiException(413, "Stored document exceeds limit");
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) { throw new ApiException(503, "Private document text is unavailable"); }
    }

    void remove(UUID owner, String key) {
        if (key == null) return;
        try { Files.deleteIfExists(resolve(owner, key)); }
        catch (IOException ignored) { /* An orphan is private; never mask the original transaction failure. */ }
    }

    private Path resolve(UUID owner, String key) {
        if (owner == null || key == null || !key.matches("[0-9a-f-]{36}/[0-9a-f-]{36}\\.(txt|bin)")
                || !key.startsWith(owner + "/")) throw new ApiException(404, "Document not found");
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) throw new ApiException(404, "Document not found");
        rejectSymlinks(path);
        return path;
    }

    private static void rejectSymlinks(Path target) {
        for (Path part = target; part != null; part = part.getParent()) {
            if (Files.isSymbolicLink(part)) throw new ApiException(503, "Private storage must not contain symbolic links");
        }
    }
}
