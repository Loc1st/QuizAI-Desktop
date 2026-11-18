package com.quizai.server.v2.foundation;

import org.junit.jupiter.api.Test;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class V2TargetTest {
    private void validate(String profile, String url, String user, String password) throws Exception {
        try {
            Class.forName("com.quizai.server.v2.foundation.V2Target")
                    .getMethod("validate", String.class, String.class, String.class, String.class)
                    .invoke(null, profile, url, user, password);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException cause) throw cause;
            throw failure;
        }
    }

    @Test void acceptsOnlyDedicatedLocalV2Identity() {
        for (String profile : List.of("v2-dev", "v2-test")) {
            String db = "quizai_" + profile.replace('-', '_');
            assertDoesNotThrow(() -> validate(profile, "jdbc:postgresql://127.0.0.1:5432/" + db, db, "runtime-only-secret"));
        }
    }

    @Test void rejectsLegacyRemoteAndConnectionParameterBypassesBeforeJdbc() {
        for (String url : List.of("jdbc:postgresql://127.0.0.1:5432/quizai_dev",
                "jdbc:postgresql://127.0.0.1:5432/quizai_v2_test",
                "jdbc:postgresql://remote.example:5432/quizai_v2_dev",
                "jdbc:postgresql://127.0.0.1:5432/quizai_v2_dev?currentSchema=other",
                "jdbc:postgresql://user:secret@127.0.0.1:5432/quizai_v2_dev")) {
            assertThrows(IllegalStateException.class, () -> validate("v2-dev", url, "quizai_v2_dev", "runtime-only-secret"));
        }
    }

    @Test void rejectsMissingOrAdministrativeCredentialsWithoutEchoingSecrets() {
        for (String user : List.of("postgres", "quizai_dev", "")) {
            var failure = assertThrows(IllegalStateException.class, () -> validate("v2-dev",
                    "jdbc:postgresql://127.0.0.1:5432/quizai_v2_dev", user, "never-log-this-value"));
            assertFalse(failure.getMessage().contains("never-log-this-value"));
        }
        assertThrows(IllegalStateException.class, () -> validate("v2-dev", "jdbc:postgresql://127.0.0.1:5432/quizai_v2_dev", "quizai_v2_dev", ""));
    }
}
