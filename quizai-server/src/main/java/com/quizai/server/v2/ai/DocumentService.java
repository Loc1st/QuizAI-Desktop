package com.quizai.server.v2.ai;

import com.quizai.server.security.SessionPrincipal;
import com.quizai.server.web.ApiException;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Service
@Profile({"v2-dev", "v2-test"})
public class DocumentService {
    record Source(UUID id, String type, String textKey) { }
    private final JdbcTemplate jdbc;
    private final DocumentExtractor extractor;
    private final PrivateDocumentStore store;

    public DocumentService(JdbcTemplate jdbc, DocumentExtractor extractor, PrivateDocumentStore store) {
        this.jdbc = jdbc; this.extractor = extractor; this.store = store;
    }

    @Transactional(readOnly = true)
    public List<StudyDtos.Document> list(SessionPrincipal actor) {
        StudyAccess.authenticated(actor);
        return jdbc.query("SELECT * FROM documents WHERE owner_id=? AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 100",
                (r,n) -> new StudyDtos.Document(r.getObject("id", UUID.class), r.getString("input_type"), r.getString("file_name"),
                        r.getString("mime_type"), r.getLong("size_bytes"), r.getString("extraction_status"), r.getTimestamp("created_at").toInstant()), actor.userId());
    }

    @Transactional
    public StudyDtos.Document paste(SessionPrincipal actor, StudyDtos.Paste request) {
        StudyAccess.authenticated(actor);
        if (request == null) throw new ApiException(400, "Text is required");
        String title = StudyAccess.text(request.title(), 200, "Title");
        String text = extractor.pasted(request.text());
        return persist(actor, "PASTED_TEXT", title, "text/plain", null, text);
    }

    @Transactional
    public StudyDtos.Document upload(SessionPrincipal actor, MultipartFile file) {
        StudyAccess.authenticated(actor);
        if (file == null || file.isEmpty()) throw new ApiException(400, "File is required");
        if (file.getSize() > DocumentExtractor.MAX_FILE_BYTES) throw new ApiException(413, "Document exceeds 5 MiB");
        String type = DocumentExtractor.inputType(file.getOriginalFilename(), file.getContentType());
        try (var input = file.getInputStream()) {
            byte[] bytes = input.readNBytes(DocumentExtractor.MAX_FILE_BYTES + 1);
            return persist(actor, type, file.getOriginalFilename(), file.getContentType(), bytes, extractor.extract(type, bytes));
        } catch (IOException e) { throw new ApiException(400, "Cannot read uploaded document"); }
    }

    Source ownedSource(SessionPrincipal actor, UUID documentId) {
        StudyAccess.authenticated(actor);
        return jdbc.query("SELECT id,input_type,extracted_text_storage_key FROM documents WHERE id=? AND owner_id=? AND deleted_at IS NULL AND extraction_status='READY'",
                (r,n) -> new Source(r.getObject("id", UUID.class), r.getString("input_type"), r.getString("extracted_text_storage_key")),
                documentId, actor.userId()).stream().findFirst().orElseThrow(StudyAccess::missing);
    }

    String text(SessionPrincipal actor, Source source) { return store.readText(actor.userId(), source.textKey()); }

    private StudyDtos.Document persist(SessionPrincipal actor, String type, String name, String mime, byte[] bytes, String text) {
        UUID id = UUID.randomUUID();
        String binaryKey = bytes == null ? null : store.write(actor.userId(), id, "bin", bytes);
        String textKey;
        try { textKey = store.write(actor.userId(), id, "txt", text.getBytes(StandardCharsets.UTF_8)); }
        catch (RuntimeException failure) { store.remove(actor.userId(), binaryKey); throw failure; }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) { store.remove(actor.userId(), binaryKey); store.remove(actor.userId(), textKey); }
                }
            });
        }
        try {
            return jdbc.queryForObject("""
                    INSERT INTO documents(id,owner_id,input_type,storage_key,extracted_text_storage_key,file_name,mime_type,size_bytes,extraction_status)
                    VALUES(?,?,?,?,?,?,?,?,'READY') RETURNING created_at
                    """, (r,n) -> new StudyDtos.Document(id, type, name, mime, bytes == null ? text.getBytes(StandardCharsets.UTF_8).length : bytes.length,
                    "READY", r.getTimestamp(1).toInstant()), id, actor.userId(), type, binaryKey, textKey, name, mime,
                    bytes == null ? text.getBytes(StandardCharsets.UTF_8).length : bytes.length);
        } catch (RuntimeException failure) {
            store.remove(actor.userId(), binaryKey); store.remove(actor.userId(), textKey); throw failure;
        }
    }
}
