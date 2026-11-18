package com.quizai.server.classes;

import com.quizai.server.web.ApiException;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class JoinCodeCodecTest {
    private static final String KEY = "0123456789abcdef0123456789abcdef";
    private static final String CODE = "0123456789ABCDEF0123456789ABCDEF";

    @Test
    void digestUsesHmacSha256OnTrimmedUppercaseCode() throws Exception {
        JoinCodeCodec codec = new JoinCodeCodec(KEY);
        Mac reference = Mac.getInstance("HmacSHA256");
        reference.init(new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected = HexFormat.of().formatHex(reference.doFinal(CODE.getBytes(StandardCharsets.US_ASCII)));

        assertEquals(expected, codec.digest("  " + CODE.toLowerCase(java.util.Locale.ROOT) + "  "));
        assertNotEquals(expected, new JoinCodeCodec(KEY + "different").digest(CODE));
    }

    @Test
    void generatedCodeCarries128RandomBitsAsUrlSafeUppercaseHex() {
        JoinCodeCodec codec = new JoinCodeCodec(KEY);
        String first = codec.generateCode();
        String second = codec.generateCode();

        assertTrue(first.matches("[0-9A-F]{32}"));
        assertTrue(second.matches("[0-9A-F]{32}"));
        assertNotEquals(first, second);
        assertTrue(codec.digest(first).matches("[0-9a-f]{64}"));
    }

    @Test
    void missingOrShortKeyDisablesOnlyJoinCodeOperations() {
        for (String key : new String[] {"", " ".repeat(32), "short", "a".repeat(31)}) {
            JoinCodeCodec codec = new JoinCodeCodec(key);
            ApiException error = assertThrows(ApiException.class, codec::generateCode);
            assertEquals(503, error.status());
            assertEquals(503, assertThrows(ApiException.class, () -> codec.digest(CODE)).status());
            assertFalse(error.getMessage().contains(key.isBlank() ? "never-present-marker" : key));
        }
    }

    @Test
    void minimumKeyLengthCountsUtf8BytesRatherThanCharacters() {
        JoinCodeCodec codec = new JoinCodeCodec("é".repeat(16));
        assertTrue(codec.digest(CODE).matches("[0-9a-f]{64}"));
    }

    @Test
    void invalidCodesHaveAStableValidationError() {
        JoinCodeCodec codec = new JoinCodeCodec(KEY);
        for (String invalid : new String[] {"", "F".repeat(31), "F".repeat(33), "Z".repeat(32)}) {
            assertEquals(400, assertThrows(ApiException.class, () -> codec.digest(invalid)).status());
        }
        assertEquals(400, assertThrows(ApiException.class, () -> codec.digest(null)).status());
    }
}
