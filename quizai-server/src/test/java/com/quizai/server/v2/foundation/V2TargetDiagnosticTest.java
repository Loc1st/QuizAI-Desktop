package com.quizai.server.v2.foundation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class V2TargetDiagnosticTest {
    private static final String URL="jdbc:postgresql://127.0.0.1:5432/quizai_v2_test";
    @Test void missingVariablesHaveDistinctSafeDiagnosticCodes() {
        check("URL_MISSING",null,"quizai_v2_test","private-value");
        check("USERNAME_MISSING",URL,null,"private-value");
        check("PASSWORD_MISSING",URL,"quizai_v2_test",null);
        check("PASSWORD_MISSING",URL,"quizai_v2_test","  ");
    }
    @Test void wrongRoleAndUnresolvedCredentialsRemainRejectedWithoutPrintingValues() {
        check("USERNAME_MISMATCH",URL,"postgres","private-value");
        check("PASSWORD_UNRESOLVED",URL,"quizai_v2_test","${PRIVATE_VALUE}");
    }
    @Test void forbiddenTargetsRemainRejectedBeforeAnyJdbcAccess() {
        check("URL_TARGET_INVALID","jdbc:postgresql://127.0.0.1:5432/quizai_test","quizai_v2_test","private-value");
        check("URL_TARGET_INVALID",URL+"?password=private-value","quizai_v2_test","private-value");
        check("URL_FORMAT_INVALID","private-value","quizai_v2_test","private-value");
    }
    private void check(String reason,String url,String user,String password) {
        var error=assertThrows(IllegalStateException.class,()->V2Target.validate("v2-test",url,user,password));
        assertTrue(error.getMessage().contains(reason),error.getMessage());
        assertFalse(error.getMessage().contains("private-value"));
        assertFalse(error.getMessage().contains("PRIVATE_VALUE"));
        assertNull(error.getCause());
    }
}
