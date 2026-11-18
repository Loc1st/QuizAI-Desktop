package com.quizai.server.v2.ai;

import com.quizai.server.web.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PrivateDocumentStoreTest {
    @TempDir Path root;

    @Test void storesPrivateUtf8AndRejectsTraversalAndForeignOwner() {
        var store = new PrivateDocumentStore(root);
        UUID owner = UUID.randomUUID();
        String key = store.write(owner, UUID.randomUUID(), "txt", "Tiếng Việt".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("Tiếng Việt", store.readText(owner, key));
        assertThrows(ApiException.class, () -> store.readText(UUID.randomUUID(), key));
        assertThrows(ApiException.class, () -> store.readText(owner, "../outside.txt"));
    }
}
