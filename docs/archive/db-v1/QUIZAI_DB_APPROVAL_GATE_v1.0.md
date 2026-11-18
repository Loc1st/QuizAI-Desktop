# QUIZAI — DB-05 ERD Approval Gate v1.0

**Status:** AWAITING USER APPROVAL · 2026-11-07. 12/12 FINAL confirmed A; **ERD/schema itself not approved**.

## Deliverables

- `QUIZAI_DB_DESIGN_DECISIONS_v0.18.md` — confirmed decision log; historical OPEN sections are superseded by section 19.
- `QUIZAI_DB05_ERD_FINAL_FOR_APPROVAL_v1.0.md` — standalone complete logical design for 37 proposed MVP tables, 34 DEC mapping, requirements coverage, constraints, concurrency, 30 acceptance cases.
- This gate sheet: guardrails for the next Codex handoff.

## Mandatory explicit sign-off

1. Owner approves the exact ERD v1.0 (or asks for v1.1 edits); the 12 FINAL answers alone are not ERD sign-off.
2. Owner issues a new, explicit instruction to start database implementation in DEV/TEST. Approval to design does not equal authorization to mutate DB.
3. Implementation only after both gates; no production migrations without separate readiness/backup review.

## Scope lock

- **In MVP:** 37 core logical tables covering auth/classes/documents/question banks/practice/PDF AI/assignments/attempts/scores/publication/audit.
- **Outside MVP pending Change Request:** post-assigned-attempt AI chat/explanations, history read-only/stale markers, automated multiple variants, cross-teacher bank share, academic course grading.
- **Operations still needed before production:** exact retention/backup policy, AI quotas/limit numbers by benchmark, production secrets and disaster-recovery plan. Those are not excuses to invent product scope.

## Immediate next steps after owner approval

1. Create a DEV/TEST-only Codex implementation brief for Flyway UUID PK/FK and constraints.
2. Review first schema PR before applying migration; owner decides whether further gate needed.
3. Add JPA Entities/Repositories in `quizai-server` only, use integration tests against TEST PostgreSQL.
4. Test start/timeout/late/device-transfer/score-release races, owner authorization, 0 vs NULL, membership episode and immutable snapshot.

**No SQL/migration/JPA/PostgreSQL changes created by this document.**
