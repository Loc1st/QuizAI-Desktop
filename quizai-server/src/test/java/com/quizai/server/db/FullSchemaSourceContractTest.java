package com.quizai.server.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Offline source contracts; PostgreSQL execution and concurrency need separate acceptance tests. */
class FullSchemaSourceContractTest {
    private static final String[] MIGRATIONS = {
            "V1__identity_classes_catalog.sql", "V2__documents_questions_quizzes.sql",
            "V3__practice_and_private_ai.sql", "V4__assignments_snapshots_publication.sql",
            "V5__attempts_device_bindings.sql", "V6__submission_review_results_audit.sql",
            "V7__read_models_and_release_guards.sql", "V8__cross_row_integrity_guards.sql"
    };
    // Independently transcribed from the signed ERD v1.0 dictionary, section 3.
    private static final Map<String, Set<String>> DICTIONARY = dictionary();

    @Test
    void all37TablesHaveExactlyTheSignedDictionaryColumnsWithoutChangingV1() throws Exception {
        Path working = Path.of("").toAbsolutePath();
        Path root = Files.isRegularFile(working.resolve("settings.gradle")) ? working : working.getParent();
        assertEquals("5ff37a672d9a76ce4092301f53064a850304fed0ace74d8f71fa9db766aa31ab",
                sha256(ApprovedLegacyErd.signedBytes(Files.readAllBytes(root.resolve(ApprovedLegacyErd.ARCHIVED_PATH)))));
        assertEquals("fd550d78a7df8e780e7b39dd09a00040ce379cf97720a3edcedf00885c16ef0e",
                sha256(resourceBytes(MIGRATIONS[0])), "Previously delivered V1 bytes must remain unchanged");

        Map<String, String> actualTables = new LinkedHashMap<>();
        for (String name : MIGRATIONS) {
            var declarations = Pattern.compile("(?ms)^CREATE TABLE\\s+([a-z_]+)\\s*\\((.*?)^\\);")
                    .matcher(resource(name));
            while (declarations.find()) {
                assertNull(actualTables.put(declarations.group(1), declarations.group(2)),
                        "Duplicate table declaration: " + declarations.group(1));
            }
        }
        assertEquals(37, DICTIONARY.size());
        assertEquals(DICTIONARY.keySet(), actualTables.keySet());
        for (var table : actualTables.entrySet()) {
            Set<String> actualColumns = Pattern.compile("(?m)^\\s+([a-z_]+)\\s+[A-Z]+\\b")
                    .matcher(table.getValue()).results().map(match -> match.group(1)).collect(Collectors.toSet());
            assertEquals(DICTIONARY.get(table.getKey()), actualColumns, table.getKey());
            assertTrue(Pattern.compile("\\bid\\s+UUID PRIMARY KEY\\b").matcher(table.getValue()).find(),
                    "Business primary key must be UUID: " + table.getKey());
        }
    }

    @Test
    void completedExtractionVersionsRejectUpdatesAndDeletion() throws IOException {
        String sql = resource(MIGRATIONS[1]);
        contains(function(sql, "quizai_guard_complete_extraction"), "IF OLD.status='COMPLETE' THEN");
        contains(sql, "BEFORE UPDATE OR DELETE ON document_extractions");
        contains(sql, "EXECUTE FUNCTION quizai_guard_complete_extraction()");
    }

    @Test
    void teacherApprovalRequiresClassificationAndFirstUseLocksTheVersion() throws IOException {
        String sql = resource(MIGRATIONS[1]);
        String approval = function(sql, "quizai_guard_question_version");
        contains(approval, "OLD.approval_status='APPROVED' OR used");
        contains(approval, "FROM quiz_questions WHERE question_version_id=OLD.id");
        contains(approval, "FROM practice_session_questions WHERE question_version_id=OLD.id");
        contains(approval, "owner_role='TEACHER'");
        contains(approval, "NEW.subject_id IS NULL OR NEW.approved_by IS DISTINCT FROM owner_uuid");
        contains(approval, "WHERE id=bank_uuid AND owner_teacher_id=owner_uuid");
        contains(function(sql, "quizai_lock_quiz_question_version"),
                "FROM question_versions WHERE id=NEW.question_version_id FOR UPDATE");
        contains(sql, "BEFORE INSERT OR UPDATE ON quiz_questions");
    }

    @Test
    void practiceRowsPreserveTheirOriginalQuestionListAndFinalizedResults() throws IOException {
        String sql = resource(MIGRATIONS[2]);
        String session = function(sql, "quizai_guard_practice_session");
        String list = function(sql, "quizai_guard_practice_question_list");
        String answer = function(sql, "quizai_guard_practice_answer_state");
        contains(session, "TG_OP='INSERT'");
        contains(session, "NEW.status<>'IN_PROGRESS'");
        contains(session, "OLD.status<>'IN_PROGRESS'");
        for (String field : Set.of("id", "student_id", "quiz_id", "started_at")) {
            contains(session, "NEW." + field + " IS DISTINCT FROM OLD." + field);
        }
        contains(sql, "BEFORE INSERT OR UPDATE OR DELETE ON practice_sessions");
        contains(sql, "BEFORE INSERT OR UPDATE OR DELETE ON practice_session_questions");
        contains(sql, "BEFORE INSERT OR UPDATE OR DELETE ON practice_answers");
        contains(list, "IF TG_OP<>'INSERT' THEN");
        contains(list, "FROM question_versions WHERE id=NEW.question_version_id FOR UPDATE");
        for (String guard : new String[]{list, answer}) {
            contains(guard, "WHERE id=session_uuid FOR UPDATE");
            contains(guard, "IF session_status<>'IN_PROGRESS' THEN");
        }
        contains(answer, "NEW.session_question_id IS DISTINCT FROM OLD.session_question_id");
        contains(sql, "EXECUTE FUNCTION quizai_guard_practice_question_list()");
        contains(sql, "EXECUTE FUNCTION quizai_guard_practice_answer_state()");
    }

    @Test
    void assignmentRecipientsCannotSwitchTheirOriginalMembershipOrIdentity() throws IOException {
        String sql = resource(MIGRATIONS[3]);
        String recipient = function(sql, "quizai_guard_recipient_membership");
        contains(recipient, "IF TG_OP<>'INSERT' THEN");
        contains(sql, "BEFORE INSERT OR UPDATE OR DELETE ON assignment_recipients");
        contains(recipient, "m.id=NEW.membership_id AND m.student_id=NEW.student_id");
        contains(recipient, "m.joined_at<=NEW.assigned_at");
    }

    @Test
    void publishedAssignmentsCannotBypassValidationOrMoveSnapshotsToAnotherParent() throws IOException {
        String sql = resource(MIGRATIONS[3]);
        String publication = function(sql, "quizai_guard_assignment_publish");
        String snapshot = function(sql, "quizai_guard_published_snapshot");
        contains(publication, "IF TG_OP='INSERT' THEN");
        contains(publication, "IF NEW.status<>'DRAFT' THEN");
        contains(publication, "IF OLD.status<>'DRAFT' AND NEW.status='DRAFT' THEN");
        contains(sql, "BEFORE INSERT OR UPDATE ON assignments");
        contains(snapshot, "NEW.assignment_id IS DISTINCT FROM OLD.assignment_id");
        contains(snapshot, "NEW.question_snapshot_id IS DISTINCT FROM OLD.question_snapshot_id");
        contains(snapshot, "FROM assignments WHERE id=assignment_uuid FOR UPDATE");
        contains(snapshot, "IF assignment_status<>'DRAFT' THEN");
        contains(sql, "BEFORE INSERT OR UPDATE OR DELETE ON assignment_question_snapshots");
        contains(sql, "BEFORE INSERT OR UPDATE OR DELETE ON assignment_snapshot_options");
    }

    @Test
    void scoreHistoryDistinguishesAbsentScoresFromPartialNullOrZeroScores() throws IOException {
        String sql = resource(MIGRATIONS[5]);
        for (String prefix : Set.of("old", "new")) {
            contains(sql, "(" + prefix + "_earned IS NULL AND " + prefix + "_possible IS NULL) OR");
            contains(sql, "(" + prefix + "_earned IS NOT NULL AND " + prefix + "_possible IS NOT NULL");
            contains(sql, prefix + "_earned>=0 AND " + prefix + "_possible>0 AND "
                    + prefix + "_earned<=" + prefix + "_possible");
        }
        contains(sql, "FOREIGN KEY(id,effective_candidate_id) REFERENCES submission_candidates(attempt_id,id)");
        contains(sql, "DEFERRABLE INITIALLY IMMEDIATE");
    }

    private static Map<String, Set<String>> dictionary() {
        Map<String, Set<String>> tables = new LinkedHashMap<>();
        columns(tables, "users", "id email password_hash role status created_at updated_at");
        columns(tables, "auth_sessions", "id user_id session_token_hash device_session_id created_at expires_at revoked_at last_seen_at");
        columns(tables, "classes", "id teacher_id name join_code_digest join_enabled status created_at");
        columns(tables, "class_members", "id class_id student_id joined_at left_at created_at");
        columns(tables, "subjects", "id code name active catalog_version");
        columns(tables, "topics", "id subject_id code name active");
        columns(tables, "documents", "id owner_id input_type storage_key filename mime_type size_bytes deleted_at created_at");
        columns(tables, "document_extractions", "id document_id extraction_no text_storage_key checksum extractor_version status created_at");
        columns(tables, "ai_generations", "id requested_by source_document_id source_extraction_id request_type status created_at");
        columns(tables, "question_banks", "id owner_teacher_id name archived_at created_at");
        columns(tables, "questions", "id owner_id bank_id source_generation_id source_document_id created_at");
        columns(tables, "question_versions", "id question_id version_no stem subject_id topic_id difficulty approval_status approved_by approved_at");
        columns(tables, "question_options", "id question_version_id position option_text is_correct");
        columns(tables, "quizzes", "id owner_id quiz_type title status created_at archived_at");
        columns(tables, "quiz_questions", "id quiz_id question_version_id position");
        columns(tables, "practice_sessions", "id student_id quiz_id started_at finished_at status correct_count total_questions");
        columns(tables, "practice_session_questions", "id practice_session_id question_version_id position");
        columns(tables, "practice_answers", "id session_question_id selected_option_id answered_at is_correct");
        columns(tables, "assignments", "id class_id teacher_id source_quiz_id status opens_at closes_at duration_minutes max_attempts score_release_at answer_release_at answer_release_mode policy_revision");
        columns(tables, "assignment_recipients", "id assignment_id student_id membership_id assigned_at");
        columns(tables, "assignment_question_snapshots", "id assignment_id source_question_version_id position stem difficulty");
        columns(tables, "assignment_snapshot_options", "id question_snapshot_id position option_text is_correct");
        columns(tables, "assignment_publication_events", "id assignment_id release_kind first_available_at recorded_at witness_recipient_id policy_revision source_event");
        columns(tables, "attempts", "id recipient_id attempt_no start_request_id started_at deadline_at phase last_applied_revision effective_candidate_id");
        columns(tables, "attempt_answers", "id attempt_id question_snapshot_id selected_option_id last_revision updated_at");
        columns(tables, "attempt_device_bindings", "id attempt_id binding_version auth_session_id token_hash bound_at revoked_at");
        columns(tables, "device_transfer_requests", "id attempt_id requested_by new_auth_session_id status reviewed_by reviewed_at review_reason");
        columns(tables, "submission_candidates", "id attempt_id kind server_received_at captured_at source_revision payload_digest created_at");
        columns(tables, "submission_candidate_answers", "id candidate_id question_snapshot_id selected_option_id");
        columns(tables, "late_submission_reviews", "id candidate_id teacher_id decision reason reviewed_at");
        columns(tables, "submission_scores", "id candidate_id earned_points possible_points normalized_score scored_at");
        columns(tables, "attempt_result_events", "id attempt_id old_candidate_id new_candidate_id reason_code actor_id changed_at");
        columns(tables, "assignment_recipient_score_events", "id recipient_id old_best_attempt_id new_best_attempt_id old_earned old_possible new_earned new_possible cause_result_event_id actor_id changed_at");
        columns(tables, "idempotency_receipts", "id attempt_id operation request_id payload_digest durable_received_at state response_ref processed_at");
        columns(tables, "audit_events", "id actor_id assignment_id attempt_id event_type event_at outcome metadata_sanitized");
        columns(tables, "ai_conversations", "id owner_student_id conversation_type practice_session_id source_pdf_document_id created_at");
        columns(tables, "ai_messages", "id conversation_id role content practice_session_question_id created_at");
        return tables;
    }

    private static void columns(Map<String, Set<String>> tables, String table, String names) {
        tables.put(table, Arrays.stream(names.split(" ")).collect(Collectors.toSet()));
    }

    private static void contains(String text, String expected) {
        assertTrue(normalize(text).contains(normalize(expected)), expected);
    }

    private static String function(String sql, String name) {
        var declaration = Pattern.compile("(?is)CREATE\\s+(?:OR\\s+REPLACE\\s+)?FUNCTION\\s+"
                + Pattern.quote(name) + "\\s*\\(\\)\\s+RETURNS\\s+TRIGGER\\s+LANGUAGE\\s+plpgsql\\s+AS\\s+\\$\\$(.*?)\\$\\$;")
                .matcher(sql);
        assertTrue(declaration.find(), "Missing trigger function: " + name);
        return declaration.group(1);
    }

    private static String normalize(String text) {
        return text.replaceAll("(?m)--[^\\r\\n]*", "").replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private static String resource(String name) throws IOException {
        return new String(resourceBytes(name), StandardCharsets.UTF_8);
    }

    private static byte[] resourceBytes(String name) throws IOException {
        try (var input = FullSchemaSourceContractTest.class.getClassLoader()
                .getResourceAsStream("db/migration/" + name)) {
            assertNotNull(input, "Missing migration source: " + name);
            return input.readAllBytes();
        }
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }
}
