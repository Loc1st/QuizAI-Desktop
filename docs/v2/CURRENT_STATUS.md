# QuizAI Desktop V2 — current checkpoint

Updated: 2026-11-19. Development checkpoint, not a completed product.

## Verified backend and schema

- Approved scope: CR-001 and ERD v2.0, 24 business tables, Java 21 and five Gradle modules.
- Local PostgreSQL V2 TEST and DEV have been deployed with Flyway V1–V5. Applied migrations remain unchanged.
- Real PostgreSQL DEV API E2E passed: teacher/student accounts, class join, reviewed quiz, assignment publication, immutable snapshot, autosave revisions, duplicate/concurrent submit, grading and teacher results (2026-11-13 evidence).
- Real PostgreSQL TEST study-summary integration passed on 2026-11-18: Java 21, PostgreSQL 18.6, five migration checksums validated, membership-scoped completed-practice aggregates, fixture ROLLBACK and absence confirmed.
- Latest offline verification: 208 tests passed, none failed or skipped. Native JavaFX checks also cover controller interactions with explicitly local HTTP fixtures; these are not complete GUI-to-PostgreSQL acceptance.

## Work in progress

- Client and Admin JavaFX applications start and render, but the UI remains rudimentary.
- Menus/navigation are confusing and difficult to use; some destinations reuse the same view. Improve usability without replacing the JavaFX architecture.
- Many functions still need full authenticated GUI acceptance against the real V2 backend, particularly the complete classroom-to-results workflow, release boundaries, timeout/resume and practice/tutor workflows.
- Gemini server integration and document extraction exist, but real Gemini runtime has not been accepted. No simulated AI output counts as runtime success.
- WSS notifications and the TCP demo implementation remain incomplete. Internet deployment/TLS and packaging are not accepted as complete.
- Historical UI mock resources remain in source but are not destinations in the current shell navigation.

## Next priorities

1. Simplify menus and make classroom → bank → reviewed questions → quiz → assignment → attempt → results discoverable.
2. Run and record the complete teacher/student GUI workflow with real PostgreSQL V2, then cover selected recipients, multiple-attempt maximum score and independent result releases.
3. Verify timeout, retry, resume, reordered ACKs and membership episode boundaries against the real backend.
4. Configure Gemini through private server environment and accept document generation/review, private practice explanations and PDF Tutor; maintain assigned-exam AI blocking.
5. Finish the remaining accepted networking/demo work and packaging after the core GUI workflow is usable.

## Safety and checkpoint boundaries

Keep V2 migrations V1–V5 and the approved ERD immutable. Preserve all V1 databases and history. No DROP/TRUNCATE/reset/Flyway repair or automatic data transfer. Offline, late-review and device-transfer remain deferred under CR-001.

Passwords, API keys, session tokens, HMAC keys, encrypted local checkpoints, runtime evidence, logs, caches and build output are not part of the GitHub checkpoint. Configure credentials locally; never commit them.
