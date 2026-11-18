# TASK-003 / DB-05 — SINGLE-PASS P1 REVIEW + P2-A SOURCE IMPLEMENTATION

**Chủ dự án yêu cầu gom công việc trong một đợt. ERD v1.0 G1 APPROVED; không mặc định G2 database access authorized.** Lệnh này cho phép **sửa source để hoàn thiện P2-A 6 bảng**, nhưng **KHÔNG cấp quyền kết nối hay thay đổi một PostgreSQL instance**. Khi có sai lệch ERD/P1 hoặc file migration V1 đã tồn tại, STOP/report thay vì ghi đè.

## 1. Read first and protect user work
- Read `../docs/PROJECT_DECISIONS_v7.md`, `../docs/REQUIREMENTS_BASELINE_v1.0.md`, `../docs/archive/db-v1/QUIZAI_DB_DESIGN_DECISIONS_v0.19.md`, `../docs/archive/db-v1/QUIZAI_DB05_ERD_FINAL_FOR_APPROVAL_v1.0.md` and approval record.
- Check SHA-256 of ERD is `5ff37a672d9a76ce4092301f53064a850304fed0ace74d8f71fa9db766aa31ab`.
- `git status --short` at start and end; do not reset, stage, commit, delete, move, modify old docs, or touch UI preview changes.
- Check actual P1 five files, not just previous report. Inspect Gradle/YAML syntax and fail-closed DEV/TEST configuration. Use the supplied `scripts/p1_final_audit.py --repo .` if available (PyYAML optional). Run `./gradlew :quizai-server:compileJava` (or Windows `gradlew.bat`) only if it does not start the server or touch DB. If FAIL, STOP and show errors.

## 2. In the same run, implement the P2-A migration source
- Add only `quizai-server/src/main/resources/db/migration/V1__identity_classes_catalog.sql` for `users`, `auth_sessions`, `classes`, `class_members`, `subjects`, `topics` in dependency order. Use the reviewed migration in this package as starting code, compare every column with approved ERD §3 and DBX-01..05/18/22/23. **Never blindly overwrite an existing V1 migration**.
- Enforce normalized email uniqueness, role STUDENT/TEACHER, one active membership episode, no reopening completed episodes, enabled-only join code uniqueness, subject & topic uniqueness, foreign keys and NOT NULL/CHECK as designed.
- `join_code_digest` requires keyed HMAC in a future service layer, NOT plaintext and NOT raw SHA; the DB stores digest only.
- DBX-03 role ownership, request rate limits and API authorization are service-level guards NOT completed by this migration; report explicitly.
- Do NOT create any other 31 tables, additional business columns, startup seed, fake users, Entity/Repository/service/controller or PDF/assigned-attempt AI feature.
- Do not set `spring.flyway.enabled=true` or run Flyway migrate automatically. Do not start Postgres, Docker, Spring Boot or connect to any DEV/TEST/production DB.

## 3. Test once at end of batch
- Run offline `scripts/verify_package.py` or equivalent tests against SQL source, run offline unit tests for P1 audit if copied to project, and rerun `:quizai-server:compileJava`.
- Add `tests/P2A_ROLLBACK_CONTRACT_TEST.sql` from package as a **manual TEST-DB-only test**; DO NOT execute it until owner separately authorizes PostgreSQL TEST access. It is wrapped in BEGIN/ROLLBACK.
- Ensure source diff changes only the requested P2-A migration and test utilities; leave P1 five files as-is unless a demonstrated P1 bug requires an explicitly described fix.
- Report at end: (a) P1 PASS/FAIL based on actual files, (b) filenames + diff summary, (c) 6/6 table evidence, (d) offline test results and compile result, (e) untested PostgreSQL runtime behavior, (f) any design deviations, (g) G2 DB execution still NOT AUTHORIZED.

## STOP CONDITIONS
- Approved ERD checksum mismatch, missing source, incompatible existing V1, P1 unsafe, attempted DB connection, unexpected Gradle startup task, or source scope violation.
- Stop after P2-A **source creation and offline checks**. No P2-B or later work unless separately authorized.
