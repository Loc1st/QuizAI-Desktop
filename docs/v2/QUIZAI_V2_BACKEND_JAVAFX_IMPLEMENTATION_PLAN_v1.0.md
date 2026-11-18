# QuizAI V2 backend API / JavaFX implementation plan

> For agentic workers: execute task-by-task with test-first verification. This document prepares the next implementation phase; it does not authorize application deployment or assert database/runtime PASS.

**Goal:** implement the approved online-first, AI-first workflows on the unchanged 24-table V2 schema, retaining the five modules and TASK-002 shell/navigation.

**Architecture:** Spring Boot alone accesses PostgreSQL/Gemini. Client/Admin consume REST DTOs from common; WSS carries metadata only. Reuse the guarded V2 SQL functions for start/autosave/finalization/publication, with actor IDs obtained from the authenticated backend principal.

**Tech stack:** Java 21, existing Gradle Groovy wrapper/multi-module layout, JavaFX/FXML/CSS, Spring Boot/Security/JDBC, PostgreSQL 18, Gemini server-side.

**Spec:** CR-001, ERD v2.0, active scope and implementation-plan sections 10–11 in this directory. Approval-record addendum explains the historical PROPOSED/DRAFT labels.

**Status:** DRAFT / gate pending. Do not begin implementation until actual V2 TEST and DEV migration/catalog/acceptance evidence passes and the owner starts the application implementation phase. Missing credentials, service readiness or offline test results are not database acceptance.

## Global constraints

- Keep exactly 24 business tables; never modify V1–V8 or connect legacy `quizai_dev`, `quizai_test`, `quizai_practice`, or the `postgres` database for this track.
- No offline queue, late review, device binding/transfer, bank sharing, SUPERADMIN, teacher verification, OCR/RAG, or AI explanations after assigned exams without a separate accepted CR.
- `quizai-common` contains transport DTOs/client code only. No server repositories/entities/secrets; no JDBC from JavaFX.
- Keep default build/check offline and application startup migration-free. Real API tests are an explicit V2 TEST-only task; do not reuse legacy `integrationTest`/deployment tasks.
- Preserve Git changes; no automatic commit/reset/clean or production deployment. API and GUI runtime claims require recorded actual checks.

## Review focus

1. Coactivated legacy profiles or inherited environment variables must be rejected before datasource creation.
2. Assignment JSON and finalization output must not leak correct keys or unreleased scores.
3. Retried/concurrent start, save, submit and deadline crossings must return consistent ACK/state without additional attempts.
4. New membership episodes must not revive old recipients; private file/chat/practice data stays owner-only.
5. Reconnect/HTTP errors must not show unsaved answers as acknowledged; JavaFX IO must not block its application thread.

## 0. Runtime schema gate and evidence

- [ ] Run guarded V2 TEST migration, exact history/checksum/catalog inspection, positive/negative acceptance and two-connection serialization checks; verify rollback restored all data.
- [ ] Only after all TEST gates pass, migrate/verify/rerun DEV with the same frozen five-file release.
- [ ] Retain non-secret identity/catalog/checksum/test evidence and record limitations: serialization checks are not throughput or committed-race stress testing.

**Deliverable:** schema evidence, not an application-completion claim. The following tasks stay pending until this gate passes.

## 1. V2 runtime profiles and explicit integration test boundary

**Files:** create `quizai-server/src/main/resources/application-v2-dev.yml`, `application-v2-test.yml`; add `com/quizai/server/v2/config/V2RuntimeConfiguration.java`, `V2DatabaseReadiness.java`; add an opt-in `v2ApiIntegrationTest` source set/task in server Gradle.

**Interface:** runtime receives only `QUIZAI_V2_{DEV,TEST}_DB_*`; rejects `dev`/`test` legacy profile coactivation; Flyway and SQL init remain disabled. Readiness validates exact target/history/24-table schema before serving authenticated business requests.

- [ ] Add offline negative tests for old DB URLs, cross-environment roles, profile mixing and migration auto-enable; demonstrate failures before implementation.
- [ ] Implement the smallest explicit guarded datasource/readiness setup, sharing validation policies with the runner without invoking provisioning or migrate from startup.
- [ ] Run configuration unit tests and `build --offline`; then explicitly run TEST-only API startup/health checks and fail-closed target tests.

## 2. Auth/session and class/catalog vertical slice

**Files:** add V2 implementations under `server/v2/auth`, `server/v2/classes`, `server/v2/catalog`; keep existing legacy foundation mappings until a deliberate cutover. Reuse `SessionPrincipal`, existing API exception policy and transport records where their semantics still match.

**Current gap:** saved legacy repositories use `users.email`, `session_token_hash`, `join_code_digest`, `classes.status`, membership `created_at` and subject `catalog_version`; V2 uses `email_normalized`, `token_hash`, `join_code_hmac` and fewer fields. Do not copy the old SQL or invent absent V2 columns. Existing class/catalog services do not establish REST controller coverage.

**REST contract:** retain `/api/auth/{register,login,session,logout}` and the existing Client `/api/classes` routes when compatible. Add role/owner-scoped class/catalog controllers for the V2 profile. Preserve STUDENT/TEACHER self-registration, password/token hashes, session expiry/revocation, HMAC join-code collision handling and episode-preserving join/leave/rejoin.

- [ ] Add tests for both role registrations, invalid login/expired/revoked sessions, role/owner boundaries, HMAC normalization/collisions and repeated join/leave/rejoin.
- [ ] Implement real V2 JDBC mappings and transactions; adapt DTOs explicitly rather than pretending nonexistent archive/status fields persist. Class archival is not represented by a V2 field: do not silently add one or fabricate an ARCHIVED state.
- [ ] Add V2 API integration cases proving teacher A cannot mutate B, student cannot create teacher classes, and rejoin does not revive previous assignment recipients.
- [ ] Wire the existing Client `AuthController`/`ClassesController` through common `QuizApiClient`; add Admin auth/classes screens within the Admin module, retaining shell/sidebar/CSS/navigation ownership.
- [ ] Demo actual teacher login/create class → student login/join/leave/rejoin over REST. Record screenshots/runtime results only after both applications actually run.

## 3. Teacher questions, review and reusable quiz

**Files:** `server/v2/questions/*`, `server/v2/quizzes/*`; request/response DTOs in `common/api`; Admin controllers/FXML within its module.

**Contract:** owner-scoped banks/questions/versions/options; subject/topic/difficulty; draft → owner-teacher review; editable bank creates new versions without mutating used history. Student quiz/practice remains private. No bank collaboration or automatic multiple exam-code generator.

- [ ] Add API tests for foreign banks/documents, malformed MCQ, invalid subject/topic, duplicate positions, approval without the correct owner and immutable used versions.
- [ ] Implement transactional create/edit/review/list/filter operations using the actual V2 dictionary; keep correct-option data on the teacher-authorized path only.
- [ ] Connect Admin question/quiz screens, test error/empty states and verify edits cannot change a previously published assignment.

## 4. Assignment, autosave, finalization and release

**Files:** `server/v2/assignments/*`, `server/v2/attempts/*`; safe question/result DTOs in common; Client assignment/exam/results controllers/FXML and Admin assignment/dashboard views.

**SQL interfaces:** `quizai_v2_publish_assignment`, `quizai_v2_start_attempt`, `quizai_v2_save_answer`, `quizai_v2_finalize_attempt`, `quizai_v2_student_questions`. Backend passes authenticated actor IDs, never accepts client role/owner as authority. SQL composite results are internal; student responses apply independent score/answer release policy.

- [ ] Add tests for class/recipient ownership, snapshot source approval, manual owner-reviewed publication and missing/incorrect JSON fields. Assert response serialization has no `correct_option_key` or hidden correctness flags.
- [ ] Add opt-in PostgreSQL tests for duplicate start/submit, revision reorder/same-revision conflicts, autosave versus finalize, timeout zero, best eligible score/NULL, membership leave/rejoin and release withdrawal.
- [ ] Implement endpoints for assignment publish/list, start, per-question ACK save, submit/status and released results; use SQL functions/transactions instead of duplicating scoring logic in JavaFX.
- [ ] Add a server timeout finalizer using the same locked SQL path; test the exact deadline boundary and repeated timeout calls. Do not add offline submission queues or late payload acceptance.
- [ ] Implement JavaFX countdown from server deadline, per-answer pending/ACK/error, retry without new attempt, and refresh after reconnect. Do not claim a save/submit succeeded before the server ACK.
- [ ] Verify assigned exams offer no QuizAI assistance and no leaked answer keys before release; demo both student and teacher histories/highest score.

## 5. Document/Gemini generation and private self-study

**Files:** `server/v2/documents/*`, `server/v2/ai/*`, `server/v2/practice/*`; file transport DTOs in common; private study/document/tutor screens in Client, generation/review screens in Admin.

**Contract:** PDF/DOCX/TXT/pasted text through bounded private extraction and backend-only Gemini. Retain provenance for AI versions; teacher approval before official use. PDF tutor uses an owned PDF; practice chat/explanations are private and tied to the proper completed practice/session context.

- [ ] Test MIME/size/encoding/path traversal, foreign files/context, invalid Gemini JSON, timeouts and explicit user retry. Choose any needed DOCX dependency only during implementation with justification.
- [ ] Before every Gemini tutor/explain request, check active assigned attempts and finalize expired ones through the safe path; enforce the no-assistance rule at the backend.
- [ ] Implement generation/extraction, approval flow and private practice/history. Bound request work; do not add RAG/OCR/background job tables.
- [ ] Use fake Gemini only in tests and label it. Record live Gemini verification separately when a real server-only key is available, without exposing prompts/private documents/secrets in logs.

## 6. Reports, WSS, TCP demo and packaging

**Files:** `server/v2/reports/*`, `server/v2/notifications/*`; teacher dashboard in Admin; metadata notification handlers in Client; existing `quizai-tcp-demo` module only for the LAN/localhost demonstration.

- [ ] Test teacher aggregates include only completed practice within the relevant membership episode, never raw student documents/chat/answers.
- [ ] Implement REST refresh as the source of truth; WSS sends assignment/result metadata only. Test reconnect and ordering without private payload leakage.
- [ ] Implement/verify TCP heartbeat/ACK/framing/timeouts/multiple clients only on LAN/localhost; do not use it as Internet online status or an exam data channel.
- [ ] Run full offline build, explicit V2 TEST integration/security/timeout tests, actual Client/Admin demo, WSS/TCP checks and Windows package startup. Report every remaining gap; an SQL schema or compile PASS is not completed software.

## Exit criteria for application implementation

Teacher/student auth + class journeys, generation/review/quiz/assignment, ACK/submit/timeout/highest score/releases, private practice/PDF tutor, teacher aggregates, WSS and TCP demo must each have concrete runtime evidence. Preserve the five modules, UI shell, old data and immutable migration history. Request production deployment separately.
