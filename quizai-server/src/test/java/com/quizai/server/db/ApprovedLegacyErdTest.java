package com.quizai.server.db;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class ApprovedLegacyErdTest {
    @Test
    void reversesOnlyTheTwoDocumentedArchiveReferences() {
        String archived = "`../../PROJECT_DECISIONS_v7.md` / `../../REQUIREMENTS_BASELINE_v1.0.md`\r\n"
                + "`../../OTHER.md` / ../../PROJECT_DECISIONS_v7.md / unchanged content\r\n";
        String signed = "`PROJECT_DECISIONS_v7.md` / `REQUIREMENTS_BASELINE_v1.0.md`\r\n"
                + "`../../OTHER.md` / ../../PROJECT_DECISIONS_v7.md / unchanged content\r\n";
        assertArrayEquals(signed.getBytes(StandardCharsets.UTF_8),
                ApprovedLegacyErd.signedBytes(archived.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void preservesOriginalReferencesWhitespaceAndSubstantiveContentBytes() {
        byte[] source = "\uFEFF`PROJECT_DECISIONS_v7.md`  nội dung đã ký\r\n\n".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(source, ApprovedLegacyErd.signedBytes(source));
    }
}
