package com.quizai.server.db;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** Source-only archive compatibility; the signed content is otherwise byte-sensitive. */
final class ApprovedLegacyErd {
    static final Path ARCHIVED_PATH = Path.of("docs/archive/db-v1/QUIZAI_DB05_ERD_FINAL_FOR_APPROVAL_v1.0.md");

    private ApprovedLegacyErd() { }

    static byte[] signedBytes(byte[] archivedBytes) {
        // Archiving changed only these two relative references. Preserve every other byte,
        // including line endings, so the original independently signed SHA-256 still applies.
        return new String(archivedBytes, StandardCharsets.UTF_8)
                .replace("`../../PROJECT_DECISIONS_v7.md`", "`PROJECT_DECISIONS_v7.md`")
                .replace("`../../REQUIREMENTS_BASELINE_v1.0.md`", "`REQUIREMENTS_BASELINE_v1.0.md`")
                .getBytes(StandardCharsets.UTF_8);
    }
}
