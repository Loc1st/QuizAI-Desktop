package com.quizai.server.classes;

import com.quizai.server.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;

@Component
@Profile({"dev", "test", "v2-dev", "v2-test"})
public class JoinCodeCodec {
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();

    public JoinCodeCodec(@Value("${quizai.classes.join-code-key:}") String key) {
        this.key = key == null || key.isBlank() ? new byte[0] : key.getBytes(StandardCharsets.UTF_8);
    }

    public String generateCode() {
        requireKey();
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return HexFormat.of().withUpperCase().formatHex(bytes);
    }

    public String digest(String code) {
        requireKey();
        String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[0-9A-F]{32}")) throw new ApiException(400, "Invalid join code");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(normalized.getBytes(StandardCharsets.US_ASCII)));
        } catch (GeneralSecurityException unavailable) {
            throw new ApiException(503, "Join codes are unavailable");
        }
    }

    private void requireKey() {
        if (key.length < 32) throw new ApiException(503, "Join codes are unavailable");
    }
}
