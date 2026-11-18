package com.quizai.server.db;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/** Regression source contracts. Real PostgreSQL execution is a separate, unexecuted gate. */
class V2HardeningSourceContractTest {
    private static String sql(int version) throws Exception {
        try (var files = Files.list(Path.of("src/main/resources/db/migration-v2"))) {
            return Files.readString(files.filter(p -> p.getFileName().toString().startsWith("V" + version + "__"))
                    .findFirst().orElseThrow());
        }
    }
    private static String function(String name) throws Exception {
        var match = Pattern.compile("(?s)CREATE FUNCTION " + name + "\\(.*?AS \\$\\$(.*?)\\$\\$;").matcher(sql(5));
        assertTrue(match.find(), "Missing function " + name);
        return match.group(1);
    }
    @Test void terminalResultsRequireExplicitNonNullCountsAndScores() throws Exception {
        for (String field : new String[]{"correct_count", "total_questions"}) {
            assertTrue(sql(3).contains(field + " IS NOT NULL"));
        }
        for (String field : new String[]{"earned_points", "possible_points", "normalized_score"}) {
            assertTrue(sql(4).contains(field + " IS NOT NULL"));
        }
    }
    @Test void snapshotValidatorRejectsUnknownAndNeverReturnsSqlNull() throws Exception {
        String body = function("quizai_v2_snapshot_valid");
        assertTrue(body.contains("IS DISTINCT FROM"));
        assertTrue(body.contains("coalesce(answer_key = ANY(seen), false)"));
        assertTrue(sql(5).contains("CHECK (quizai_v2_snapshot_valid(snapshot_json) IS TRUE)"));
        assertTrue(body.contains("jsonb_object_keys"), "Only the approved option fields may be retained");
    }
    @Test void finalizedAttemptsAndSavedAnswersAlsoProtectDelete() throws Exception {
        assertTrue(sql(5).contains("BEFORE INSERT OR UPDATE OR DELETE ON attempts"));
        assertTrue(sql(5).contains("BEFORE INSERT OR UPDATE OR DELETE ON attempt_answers"));
        assertTrue(function("quizai_v2_attempt_guard").contains("TG_OP = 'DELETE'"));
        assertTrue(function("quizai_v2_attempt_answer_guard").contains("TG_OP = 'DELETE'"));
    }
    @Test void practiceVersionsListsAndCompletedResultsAreFrozen() throws Exception {
        assertTrue(function("quizai_v2_version_guard").contains("quizai_v2_version_used(OLD.id)"));
        assertTrue(function("quizai_v2_version_used").contains("practice_session_questions"));
        assertTrue(function("quizai_v2_options_guard").contains("quizai_v2_version_used"));
        assertTrue(sql(5).contains("BEFORE INSERT OR UPDATE OR DELETE ON practice_sessions"));
        assertTrue(sql(5).contains("BEFORE INSERT OR UPDATE OR DELETE ON practice_session_questions"));
        assertTrue(sql(5).contains("BEFORE INSERT OR UPDATE OR DELETE ON practice_answers"));
    }
    @Test void startSaveAndFinalizeShareAssignmentLockAndUseServerClock() throws Exception {
        for (String name : new String[]{"quizai_v2_start_attempt", "quizai_v2_save_answer", "quizai_v2_finalize_attempt"}) {
            String body = function(name);
            assertTrue(body.contains("quizai_v2_lock_assignment"), name);
            assertTrue(body.contains("p_student"), name);
        }
        String finalize = function("quizai_v2_finalize_attempt");
        assertTrue(finalize.contains("submit_request_id IS NOT DISTINCT FROM p_request"));
        assertTrue(finalize.contains("submitted_digest IS NOT DISTINCT FROM p_digest"));
        assertTrue(finalize.contains("clock_timestamp()"));
        assertTrue(finalize.contains("EXCEPTION WHEN check_violation"));
        assertTrue(finalize.contains("status='TIMED_OUT'"));
        assertTrue(finalize.contains("snapshot_json->>'correct_option_key'"));
        String save = function("quizai_v2_save_answer");
        assertTrue(save.contains("p_revision = saved.answer_revision"));
        assertTrue(save.contains("saved.selected_option_key IS NOT DISTINCT FROM p_option"));
    }
    @Test void studentProjectionBuildsWhitelistedFieldsInsteadOfReturningSnapshot() throws Exception {
        String body = function("quizai_v2_student_questions");
        assertTrue(body.contains("jsonb_build_object('key'"));
        assertFalse(body.contains("correct_option_key"));
        assertFalse(body.contains("SELECT q.snapshot_json"));
        assertTrue(body.contains("p_student"));
    }
    @Test void ownershipAndAppendOnlyAiAreEnforced() throws Exception {
        assertTrue(function("quizai_v2_version_guard").contains("NEW.approved_by IS DISTINCT FROM owner_id"));
        assertTrue(sql(5).contains("BEFORE UPDATE OR DELETE ON ai_messages"));
        assertTrue(sql(5).contains("BEFORE UPDATE OR DELETE ON audit_events"));
        String publication = function("quizai_v2_assignment_guard");
        assertTrue(publication.contains("populated assignment parent immutable"));
        assertTrue(publication.contains("assignment_recipients WHERE assignment_id=OLD.id"));
        assertTrue(publication.contains("assignment_questions WHERE assignment_id=OLD.id"));
        assertTrue(function("quizai_v2_publish_assignment").contains("a.teacher_id IS DISTINCT FROM p_teacher"));
        assertTrue(function("quizai_v2_session_guard").contains("NEW.user_id,NEW.token_hash,NEW.expires_at"));
    }
    @Test void standaloneCatalogPinsTheSameFiveFlywayChecksumsAsFrozenSources() throws Exception {
        String catalog = Files.readString(Path.of("src/test/resources/db/v2/v2_catalog_check.sql"));
        var manifest = V2ReleaseManifest.snapshot();
        String crc = manifest.history().stream().map(h -> h.checksum().toString()).collect(java.util.stream.Collectors.joining(","));
        assertTrue(catalog.contains("expected_checksums integer[] := ARRAY[" + crc + "]"));
        assertTrue(catalog.contains("versions IS DISTINCT FROM ARRAY['1','2','3','4','5']"));
        assertTrue(catalog.contains("uuid_primary_keys<>24"));
        assertTrue(function("quizai_v2_attempt_answer_guard").contains("IF accepted_at>=a.deadline_at"));
    }
}
