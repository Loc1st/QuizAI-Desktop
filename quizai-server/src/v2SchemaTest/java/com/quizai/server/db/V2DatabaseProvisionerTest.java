package com.quizai.server.db;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Pure provisioning decisions and independent SCRAM known-answer checks; never opens JDBC. */
class V2DatabaseProvisionerTest {
    @Test void defaultPreviewAndRejectedAuthorizationNeverNeedCredentials() throws Exception {
        assertDoesNotThrow(() -> V2DatabaseProvisioner.run(new String[0], Map.of()));
        assertEquals("preview", V2DatabaseProvisioner.action(new String[0], Map.of()));
        assertThrows(IllegalStateException.class,
                () -> V2DatabaseProvisioner.run(new String[]{"provision"}, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> V2DatabaseProvisioner.action(new String[]{"drop"}, Map.of()));
        assertEquals("provision", V2DatabaseProvisioner.action(new String[]{"provision"},
                Map.of("QUIZAI_V2_PROVISION_AUTHORIZATION", "AUTHORIZE_LOCAL_V2_PROVISION")));
    }

    @Test void adminEndpointRejectsEveryAlternativeIncludingProtectedDatabases() {
        var valid = Map.of("QUIZAI_V2_ADMIN_DB_URL", "jdbc:postgresql://127.0.0.1:5432/template1",
                "QUIZAI_V2_ADMIN_DB_USERNAME", "local_operator", "QUIZAI_V2_ADMIN_DB_PASSWORD", "synthetic");
        assertDoesNotThrow(() -> V2DatabaseProvisioner.admin(valid));
        for (String rejected : Set.of("quizai_dev", "quizai_test", "quizai_practice", "postgres", "quizai_v2_test",
                "template1?user=other", "template1#fragment", "template1/")) {
            var changed = new java.util.HashMap<>(valid);
            changed.put("QUIZAI_V2_ADMIN_DB_URL", "jdbc:postgresql://127.0.0.1:5432/" + rejected);
            assertThrows(IllegalArgumentException.class, () -> V2DatabaseProvisioner.admin(changed));
        }
        var changed = new java.util.HashMap<>(valid);
        changed.put("QUIZAI_V2_ADMIN_DB_URL", "jdbc:postgresql://localhost:5432/template1");
        assertThrows(IllegalArgumentException.class, () -> V2DatabaseProvisioner.admin(changed));
        assertFalse(V2DatabaseProvisioner.admin(valid).toString().contains("synthetic"));
    }

    @Test void serverIdentityRequiresLocalPg18Template1AndUnchangedSuperuserSession() {
        assertDoesNotThrow(() -> V2DatabaseProvisioner.adminIdentity("operator",
                new V2DatabaseProvisioner.AdminIdentity(180001, "127.0.0.1", 5432, "template1", "operator", "operator", true)));
        for (var rejected : new V2DatabaseProvisioner.AdminIdentity[]{
                new V2DatabaseProvisioner.AdminIdentity(170001, "127.0.0.1", 5432, "template1", "operator", "operator", true),
                new V2DatabaseProvisioner.AdminIdentity(180001, "127.0.0.1", 5433, "template1", "operator", "operator", true),
                new V2DatabaseProvisioner.AdminIdentity(180001, "127.0.0.1", 5432, "postgres", "operator", "operator", true),
                new V2DatabaseProvisioner.AdminIdentity(180001, "127.0.0.1", 5432, "template1", "other", "operator", true),
                new V2DatabaseProvisioner.AdminIdentity(180001, "127.0.0.1", 5432, "template1", "operator", "operator", false)}) {
            assertThrows(IllegalStateException.class, () -> V2DatabaseProvisioner.adminIdentity("operator", rejected));
        }
    }

    @Test void acceptsFullHostMaskReturnedByPostgresInetTextInRuntimeDiagnostic() {
        assertDoesNotThrow(() -> V2DatabaseProvisioner.adminIdentity("postgres",
                new V2DatabaseProvisioner.AdminIdentity(180006, "127.0.0.1/32", 5432,
                        "template1", "postgres", "postgres", true)));
    }

    @Test void existingObjectsArePreservedOnlyWhenIdentityAndPrivilegesMatch() {
        var safeRole = new V2DatabaseProvisioner.RoleState(true, false, false, false, false, false, false, true);
        var safeDb = new V2DatabaseProvisioner.DatabaseState("quizai_v2_test", "UTF8", true, false);
        assertDoesNotThrow(() -> V2DatabaseProvisioner.existing("quizai_v2_test", safeRole, safeDb));
        assertDoesNotThrow(() -> V2DatabaseProvisioner.existing("quizai_v2_test", null, null));
        assertDoesNotThrow(() -> V2DatabaseProvisioner.existing("quizai_v2_test", safeRole, null));
        assertThrows(IllegalStateException.class,
                () -> V2DatabaseProvisioner.existing("quizai_v2_test", null, safeDb));
        assertThrows(IllegalStateException.class,
                () -> V2DatabaseProvisioner.existing("quizai_v2_test", safeRole,
                        new V2DatabaseProvisioner.DatabaseState("other", "UTF8", true, false)));
        for (var unsafe : new V2DatabaseProvisioner.RoleState[]{
                new V2DatabaseProvisioner.RoleState(true, true, false, false, false, false, false, true),
                new V2DatabaseProvisioner.RoleState(true, false, true, false, false, false, false, true),
                new V2DatabaseProvisioner.RoleState(true, false, false, true, false, false, false, true),
                new V2DatabaseProvisioner.RoleState(true, false, false, false, true, false, false, true),
                new V2DatabaseProvisioner.RoleState(true, false, false, false, false, true, false, true),
                new V2DatabaseProvisioner.RoleState(true, false, false, false, false, false, true, true),
                new V2DatabaseProvisioner.RoleState(false, false, false, false, false, false, false, true),
                new V2DatabaseProvisioner.RoleState(true, false, false, false, false, false, false, false)}) {
            assertThrows(IllegalStateException.class,
                    () -> V2DatabaseProvisioner.existing("quizai_v2_test", unsafe, safeDb));
        }
        for (var unsafe : new V2DatabaseProvisioner.DatabaseState[]{
                new V2DatabaseProvisioner.DatabaseState("quizai_v2_test", "LATIN1", true, false),
                new V2DatabaseProvisioner.DatabaseState("quizai_v2_test", "UTF8", false, false),
                new V2DatabaseProvisioner.DatabaseState("quizai_v2_test", "UTF8", true, true)}) {
            assertThrows(IllegalStateException.class, () -> V2DatabaseProvisioner.existing("quizai_v2_test", safeRole, unsafe));
        }
    }

    @Test void scramVerifierMatchesIndependentPbkdf2HmacSha256KnownAnswer() throws Exception {
        byte[] salt = java.util.Base64.getDecoder().decode("W22ZaJ0SNY7soEsUEjb6gQ==");
        assertEquals("SCRAM-SHA-256$4096:W22ZaJ0SNY7soEsUEjb6gQ==$WG5d8oPm3OtcPnkdi4Uo7BkeZkBFzpcXkuLmtbsT4qY=:wfPLwcE6nTWhTAmQ7tl2KeoiWGPlZqQxSrmfPwDl2dU=",
                V2DatabaseProvisioner.scram("pencil".toCharArray(), salt));
        assertThrows(IllegalArgumentException.class, () -> V2DatabaseProvisioner.scram("péncil".toCharArray(), salt));
        assertThrows(IllegalArgumentException.class, () -> V2DatabaseProvisioner.scram("bad\ninput".toCharArray(), salt));
        assertThrows(IllegalArgumentException.class, () -> V2DatabaseProvisioner.scram(new char[0], salt));
        assertThrows(IllegalArgumentException.class, () -> V2DatabaseProvisioner.scram("pencil".toCharArray(), new byte[1]));
    }

    @Test void createSqlUsesOnlyFixedNamesAndValidatedVerifierWithoutPlaintext() throws Exception {
        String verifier = V2DatabaseProvisioner.scram("synthetic-password".toCharArray(), new byte[16]);
        String sql = V2DatabaseProvisioner.createRoleSql("quizai_v2_test", verifier);
        assertTrue(sql.contains("NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS NOINHERIT LOGIN"));
        assertTrue(sql.contains(verifier));
        assertFalse(sql.contains("synthetic-password"));
        assertEquals("CREATE DATABASE \"quizai_v2_dev\" WITH OWNER = \"quizai_v2_dev\" TEMPLATE = template0 ENCODING = 'UTF8'",
                V2DatabaseProvisioner.createDatabaseSql("quizai_v2_dev"));
        for (String invalid : Set.of("postgres", "quizai_test", "quizai_dev", "quizai_practice", "quizai_v2_test\"; DROP ROLE x;--")) {
            assertThrows(IllegalArgumentException.class, () -> V2DatabaseProvisioner.createRoleSql(invalid, verifier));
            assertThrows(IllegalArgumentException.class, () -> V2DatabaseProvisioner.createDatabaseSql(invalid));
        }
        for (String invalid : Set.of("plaintext", "SCRAM-SHA-256$4096:bad$bad:bad", verifier + "';SELECT 1;--")) {
            assertThrows(IllegalArgumentException.class, () -> V2DatabaseProvisioner.createRoleSql("quizai_v2_test", invalid));
        }
    }
}
