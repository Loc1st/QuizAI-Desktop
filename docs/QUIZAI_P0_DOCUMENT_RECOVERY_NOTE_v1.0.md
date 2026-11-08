# QuizAI Desktop — P0 document recovery and version lineage v1.0

**TASK:** TASK-003 / DB-05  
**Purpose:** Reconcile the read-only P0 audit's missing source documents.  
**Scope:** Documentation handoff only; **no database implementation authorization**.  
**Date:** 2026-11-07.

## 1. What P0 found

The Codex P0 read-only inventory completed without editing repository files or accessing PostgreSQL. The detailed ERD validation was blocked because the approved ERD file was absent from the repository. P0 also found two referenced design documents missing: Decision Log v0.18 and Approval Gate v1.0. The existing v0.19 Decision Log starts with a v0.18 heading. These are documentation handoff/version-lineage issues, not a verified schema defect.

**No inference about the physical PostgreSQL database is permitted** because P0 did not connect to it.

## 2. Files to add under `QuizAI-Desktop/docs/` (exact original bytes)

| Source filename | SHA-256 | Action |
|---|---|---|
| `QUIZAI_DB05_ERD_FINAL_FOR_APPROVAL_v1.0.md` | `5ff37a672d9a76ce4092301f53064a850304fed0ace74d8f71fa9db766aa31ab` | Add; immutable approved ERD |
| `QUIZAI_DB_DESIGN_DECISIONS_v0.18.md` | `0bc5fd4ca2889512581ab8539e8191f68d79f622b6b3806650929193d6e1859b` | Add; prior Decision Log |
| `QUIZAI_DB_APPROVAL_GATE_v1.0.md` | `4bb6d2764b9df319a9487c2da95570460a93513177d3bebda40ae1177c739827` | Add; historical pre-approval gate document |

These files were verified byte-for-byte in the package's source environment. Copy them into `docs/` without editing their contents. If a same-named file already exists locally, check its hash first and do not blindly overwrite.

Existing supporting documents **to retain unchanged**:

| Filename | SHA-256 |
|---|---|
| `QUIZAI_DB_DESIGN_DECISIONS_v0.19.md` | `fd0801fb7b0155e25648df9c02dc2388b7d8f35b07e80e99b205ba86eae884f8` |
| `PROJECT_DECISIONS_v7.md` | `d99d1c7aeae173954beb110420422ba664bc76a37419dd7a070052202ee419a3` |
| `REQUIREMENTS_BASELINE_v1.0.md` | `f3d25763ac3c14042754a223c1ec4b330f0c1efa3d857ba196e90fbd2cc92c2a` |

## 3. Version and approval precedence

1. `QUIZAI_DB_DESIGN_DECISIONS_v0.18.md` records FINAL-01…FINAL-12 = A (historical predecessor).
2. `QUIZAI_DB_DESIGN_DECISIONS_v0.19.md` retains that material and **appends section 20** with ERD sign-off. Its top heading still reads v0.18 because of append-only consolidation. **The actual current version is v0.19** by filename and final section. Do not alter the existing approved-checksum files merely to clean up headings.
3. The original ERD v1.0 and the original Approval Gate v1.0 say `AWAITING USER APPROVAL` because they were authored *before* the user approved the ERD. That historical state is **superseded by the subsequent** `QUIZAI_DB05_ERD_APPROVAL_RECORD_v1.0.md` and Decision Log v0.19 §20. **The ERD is approved as a design baseline only.**
4. Approved schema source of truth: **exact** `QUIZAI_DB05_ERD_FINAL_FOR_APPROVAL_v1.0.md` with the SHA-256 in §2. The earlier ERD drafts and Decision Log OPEN history are not a license to expand the schema or reinterpret approved decisions.
5. `AI-ASSIGNED-POST-01` and related post-assigned-exam AI functionality remain **outside MVP** until an explicit Change Request is approved.

## 4. Re-audit, do not implement

After the project owner copies the documents into `docs/`, run the attached **`QUIZAI_DB_CODEX_P0B_SOURCE_RECHECK_PROMPT_v1.0.md`** in Codex. Its allowed scope: read/check hashes, verify the 37-table ERD dictionary, map proposed migration batches to the ERD, identify any remaining design/report discrepancies; report only in chat. **No file writes, builds, tests, dependency installs, DB connections, SQL/migration/JPA or P1**.

**Stop gates:**
- G1 — ERD DESIGN: **APPROVED** (exact SHA-256 as above).
- G2 — DATABASE IMPLEMENTATION: **NOT AUTHORIZED**.
- G3 — PRODUCTION DEPLOYMENT: **NOT AUTHORIZED**.

P1 (Gradle/config/Flyway infrastructure) requires its own explicit later instruction after the owner reviews the completed P0B report.

## 5. Local copy/check example (PowerShell, read-only verification)

From the project root, after copying the three files into `docs/`:

```powershell
Get-FileHash .\docs\QUIZAI_DB05_ERD_FINAL_FOR_APPROVAL_v1.0.md -Algorithm SHA256
Get-FileHash .\docs\QUIZAI_DB_DESIGN_DECISIONS_v0.18.md -Algorithm SHA256
Get-FileHash .\docs\QUIZAI_DB_APPROVAL_GATE_v1.0.md -Algorithm SHA256
```

Compare against §2. No SQL, migration, JPA Entity/Repository, or PostgreSQL work is part of this document recovery.
