# QuizAI TASK-003 — P1 audit + P2-A 6-table source package (v1.0)

**Purpose:** reduce back-and-forth: audit P1 and prepare all six foundation tables plus negative tests in one code batch. All files are code/source or one instruction; **do not move this ZIP into `docs/`**.

This ZIP is a reviewed *offline proposed patch*, not a claim that the user's IntelliJ repository or PostgreSQL has been modified. Only the approved ERD v1.0 is authoritative. Existing migration V1 must never be overwritten.

## Files

- `quizai-server/src/main/resources/db/migration/V1__identity_classes_catalog.sql` — 6 tables, FK, check, partial uniques, membership episode immutability trigger. No DB connection/seed.
- `tests/P2A_ROLLBACK_CONTRACT_TEST.sql` — manual PostgreSQL TEST-only negative and positive tests; run only after separate owner authorization, uses `BEGIN`/`ROLLBACK`.
- `scripts/p1_final_audit.py` — readonly audit of the actual local repository; validates checksum, dependency declarations, YAML (with optional PyYAML), profile isolation; does not run Java/DB.
- `scripts/verify_package.py` — offline SQL invariant validator.
- `tests/test_p1_audit.py` — unit tests for the auditor (synthetic local fixtures).
- `CODEX_ONE_PASS_PROMPT.md` — one-pass instruction for Codex in IntelliJ.

## Run without touching PostgreSQL

```bash
python scripts/verify_package.py
python -m unittest discover -s tests -v
python scripts/p1_final_audit.py --repo /absolute/path/to/QuizAI-Desktop
```

On Windows, use `py` instead of `python` when appropriate.

`p1_final_audit.py` must run in a repository with actual P1 changes; static package tests are not proof that the user's P1 configuration has passed. It does not read environment variables, download dependencies or use network access. The Gradle build should be run by Codex in the actual repository only when checked safe.

## Apply into IntelliJ project

Paste the content of `CODEX_ONE_PASS_PROMPT.md` into your *new Codex session*. Give Codex access to this ZIP or unpack to an external temporary folder. It should compare with the actual ERD and current repository, then create only allowed source files. **Do not copy files blindly or enable Flyway.**

## Gates

- G1 ERD DESIGN: APPROVED (file SHA256 `5ff37a672d9a76ce4092301f53064a850304fed0ace74d8f71fa9db766aa31ab`).
- P1 CONFIG: previously reported Gradle compile PASS; actual P1 read-only audit still required on the user's repository.
- P2-A SQL: source prepared **offline**, NOT migrated; no runtime DB test.
- Database execution and production access: NOT AUTHORIZED.
- P2-B…P6: NOT AUTHORIZED. AI after assigned attempt: excluded from MVP pending Change Request.

## Review before database execution

- Verify the physical type of `join_code_digest` is appropriate for future HMAC and coordinate normalization with backend.
- Verify `users.status`, `classes.status` allowed values in application service before shipping security behavior; ERD leaves value catalog open.
- Negative tests for role mismatches (`classes.teacher_id` must reference a TEACHER; membership.student_id must reference STUDENT) must run in the service integration phase. DBX-03 relies on app guards under current ERD.
- Run rollback contract tests **only against a separately provisioned TEST database** after the migration is deliberately executed. The tests are not a replacement for live PostgreSQL/role/concurrency tests.
