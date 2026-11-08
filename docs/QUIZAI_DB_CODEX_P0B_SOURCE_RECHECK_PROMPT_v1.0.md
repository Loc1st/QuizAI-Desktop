# Codex — TASK-003 DB-05 / P0B: SOURCE RESTORATION RE-CHECK — READ ONLY

**Authorization:** Read-only inspection of QuizAI Desktop repository. P0B is a continuation of P0, **not** P1. The owner has approved the exact ERD v1.0 design, but **has not authorized database implementation**.

## Ground truth and required checks

1. Inspect `docs/PROJECT_DECISIONS_v7.md`, `docs/REQUIREMENTS_BASELINE_v1.0.md`, `docs/QUIZAI_DB_DESIGN_DECISIONS_v0.18.md`, `docs/QUIZAI_DB_DESIGN_DECISIONS_v0.19.md`, `docs/QUIZAI_DB05_ERD_FINAL_FOR_APPROVAL_v1.0.md`, `docs/QUIZAI_DB_APPROVAL_GATE_v1.0.md`, `docs/QUIZAI_DB05_ERD_APPROVAL_RECORD_v1.0.md`, and `docs/QUIZAI_DB_CODEX_PHASED_HANDOFF_v1.0.md`. If any are absent, list absent files and STOP; don't reconstruct content from assumptions.
2. Calculate and compare the exact SHA-256 for:
   - ERD v1.0: `5ff37a672d9a76ce4092301f53064a850304fed0ace74d8f71fa9db766aa31ab`
   - Decision Log v0.18: `0bc5fd4ca2889512581ab8539e8191f68d79f622b6b3806650929193d6e1859b`
   - Decision Log v0.19: `fd0801fb7b0155e25648df9c02dc2388b7d8f35b07e80e99b205ba86eae884f8`
   - Approval Gate v1.0: `4bb6d2764b9df319a9487c2da95570460a93513177d3bebda40ae1177c739827`
3. Resolve **version lineage, not by file edits**: v0.19 has a retained v0.18 title and section 20 sign-off; original ERD/Approval Gate have historical `AWAITING` text, superseded by Approval Record. Do not rewrite any originals or claim that `AWAITING` cancels the subsequent user approval.
4. Read the ERD v1.0 data dictionary in full. Verify each of the **37 unique MVP table names**, exact columns, PK/FK, nullable semantics, UNIQUE/CHECK, indexes, dependency order, and acceptance tests T01–T30. Compare with 37 tables in P2-A through P2-F in handoff. List discrepancies by **table, column and document section**, not fabricated schema.
5. Pay special attention to cross-attempt FK cycle `attempts.effective_candidate_id` ↔ `submission_candidates.attempt_id`; the approved ERD's DBX-16 cross-attempt consistency safeguard must be preserved. Do not write DDL or pick a migration trick without reporting it.
6. Check architecture/phase boundaries: only `quizai-server` will use persistence; P1 will need DEV/TEST separation and fail-closed datasource/Flyway configuration; P2 is schema; P3 Entity/Repository. No post-assigned-exam AI schema until Change Request, and no teacher verification, SUPERADMIN, sharing banks or course-grade expansion.
7. Identify which previous P0 `UNKNOWN` checks are now confirmed by document evidence and which remain unknown until permitted implementation, builds or DB tests. Do not claim runtime verification without running tests.
8. Do a read-only `git status` comparison if helpful; **preserve all pre-existing user changes**, including JavaFX preview changes, staged docs and IDE settings.

## Hard prohibitions

- NO editing/creating/deleting repository files, including no automatic formatting or docs repairs.
- NO SQL, DDL, Flyway migration, JPA Entity, Repository, or database initialization.
- NO changes to Gradle, Spring Boot, application configuration or environment variables.
- NO build/test, dependency download, app startup, container startup, or PostgreSQL connection.
- NO commits, resets, checkout, stash or Git index changes.
- NO automatic progression to P1, P2 or later phases.

## Expected chat-only report

A. SHA-256 verification table for all required documents and version lineage status.  
B. Exactly 37 tables checklist with differences, if any, relative to approved ERD and handoff batches.  
C. Summary of PK/FK/unique/check/index/NULL and DBX-16 considerations, citing ERD sections.  
D. Revised GAP/UNKNOWN table differentiating design verification from missing runtime implementation.  
E. P1 scope proposal and safeguards, **NOT** an instruction to execute P1.  
F. Explicit final line: `P0B complete (or blocked); G2 implementation NOT AUTHORIZED; no files or DB changed.`

Stop when this report is complete.
