package com.quizai.server.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SessionTokensTest {
    @Test
    void tokensHave256BitsAreDistinctAndDigestIsStableSha256() {
        SessionTokens tokens = new SessionTokens();
        String first = tokens.issue();
        String second = tokens.issue();
        assertTrue(first.matches("[A-Za-z0-9_-]{43}"));
        assertNotEquals(first, second);
        assertEquals(32, java.util.Base64.getUrlDecoder().decode(first).length);
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", tokens.hash("abc"));
        assertEquals(tokens.hash(first), tokens.hash(first));
        assertNotEquals(first, tokens.hash(first));
    }
}
