# QuizAI Desktop — Codex Phased Database Handoff v1.0

**2026-11-07 · TASK-003 / DB-05 · STATUS: PREPARATION / NO IMPLEMENTATION AUTHORIZATION**

## 0. Lệnh phê duyệt và giới hạn phạm vi

Chủ dự án đã **phê duyệt ERD v1.0 để làm cơ sở thiết kế** nhưng nói rõ **“Chưa cho phép triển khai database; chuẩn bị hướng dẫn Codex thực hiện theo từng giai đoạn.”**

- **G-DESIGN = APPROVED**: file `QUIZAI_DB05_ERD_FINAL_FOR_APPROVAL_v1.0.md` đúng byte, SHA-256: `5ff37a672d9a76ce4092301f53064a850304fed0ace74d8f71fa9db766aa31ab`.
- **G-IMPLEMENT = HOLD**: không thay đổi Gradle, source, config database, SQL/DDL, Flyway, JPA, Repository, PostgreSQL khi chưa có phê duyệt giai đoạn bằng lệnh riêng.
- **G-PRODUCTION = HOLD**: cần phê duyệt độc lập, retention/backup/security/restore trước vận hành thực tế.
- **P0 read-only** được phép chuẩn bị ngay; các prompt P1–P6 dưới đây **chỉ là mẫu dùng trong tương lai**, không phải lệnh thực thi.

### 0.1. Source of truth và cách giải quyết mâu thuẫn

1. `../../PROJECT_DECISIONS_v7.md`: 34 DEC frozen.
2. `../../REQUIREMENTS_BASELINE_v1.0.md`: tính năng MVP frozen.
3. `QUIZAI_DB_DESIGN_DECISIONS_v0.18.md`: DB-01…34 và FINAL-01…12, toàn bộ lựa chọn A CONFIRMED; những OPEN lịch sử bị quyết định sau thay thế.
4. `QUIZAI_DB05_ERD_FINAL_FOR_APPROVAL_v1.0.md`: **schema/ERD cụ thể được duyệt**.
5. `QUIZAI_DB05_ERD_APPROVAL_RECORD_v1.0.md`: trạng thái phê duyệt sau khi ERD được phát hành; phiếu ERD gốc còn dòng AWAITING là thông tin lịch sử, **không có nghĩa nó chưa được chủ dự án duyệt**.

Nếu một tài liệu trên thiếu trong repository, **dừng**, nêu đúng tên cần bổ sung; không sáng tạo thiết kế thay thế. Nếu dự kiến thay đổi 37 bảng logic, PK/FK/UNIQUE bất biến, release policy hoặc trạng thái nghiệp vụ: STOP, đề xuất ERD v1.1 + chủ dự án phê duyệt lại. Các sai khác thuần kỹ thuật trong biểu diễn phải được báo cáo; không tự suy diễn quyền triển khai.

### 0.2. Ranh giới module và dữ liệu

| Module | Vai trò khi được phép triển khai |
|---|---|
| `quizai-server` | **Duy nhất** chứa JDBC/JPA/Flyway, PostgreSQL access, transaction, security checks, Gemini key backend |
| `quizai-client` | JavaFX student, REST/WSS, local encrypted draft; **không** kết nối PostgreSQL |
| `quizai-admin` | JavaFX teacher, REST/WSS; **không** kết nối PostgreSQL |
| `quizai-common` | DTO và enum an toàn khi chia sẻ; **không** JPA Entity, Repository, DB credentials |
| `quizai-tcp-demo` | LAN/localhost demo riêng; **không** có bảng nghiệp vụ QuizAI và không truy cập database Internet |

Phụ thuộc project thực tế phải được Codex xác minh trong P0; tên module ở đây là kiến trúc đã định, không khẳng định các file build hiện đang như vậy.

### 0.3. Những bất biến phải qua mọi giai đoạn

- UUID PK/FK; Flyway có version, **không** Hibernate `ddl-auto=create`/`update`; PostgreSQL DEV, TEST tách riêng, secrets từ environment, least privilege.
- Chỉ server nắm thời gian, tính deadline `min(assignment.closes_at, attempt.started_at + duration)`; client timestamp không là bằng chứng nộp đúng hạn.
- Snapshot assignment bất biến, question version approved không in-place edit; `assignment_recipients.membership_id` trỏ **membership episode gốc**; rejoin không cấp lại quyền start bài cũ, rời lớp không chặn attempt đang làm.
- Start tiêu hao lượt; server timeout khi 0 ACK vẫn là score **0 hợp lệ**; không có eligible attempt => grade **NULL**.
- Base candidate bất biến `CLIENT_ON_TIME` **hoặc** `SERVER_TIMEOUT`; một canonical `CLIENT_LATE`; late pending không tính điểm; late ACCEPT thay effective candidate, có thể tăng/giảm điểm; late REJECT giữ timeout. Assignment grade là MAX giữa **effective scores của các attempt eligible**, không MAX các candidates trong cùng attempt.
- Binding cũ bị thu hồi phải bị từ chối **trước** xét late; retry cùng request/digest trả response cũ, payload khác bị conflict; ACK chỉ sau commit.
- `score_release_at` và `answer_release_at` độc lập, NULL là chưa công bố; first availability không thể bị rút lại, kể cả chưa ai xem; score sau late review vẫn được cập nhật và audit.
- Owner/role authorization ở backend; TEACHER không xem raw private practice/docs/chats; chỉ aggregate theo membership interval. Không cho gọi AI trong assigned attempt IN_PROGRESS.
- File PDF/DOCX/TXT/extracted text private ngoài DB, DB lưu opaque storage key; student offline draft chỉ local encrypted.
- **Chưa triển khai** AI chat/explain **sau bài giảng viên giao** (`AI-ASSIGNED-POST-01`, `AI-LATE-PENDING`, `REVIEW-AI-HISTORY-PENDING`, `REVIEW-AI-HISTORY-VERSION`) dù thiết kế UX đã chốt: cần Change Request; không đưa `assigned_attempt_id` và `context_submission_candidate_id` vào AI schema MVP. Không teacher verification, tự động nhiều mã đề, bank sharing, course-grade, OCR, web/mobile.

## 1. Stage map — từng giai đoạn cần gate riêng

| Pha | Mục tiêu | Quyền hiện tại | Điều kiện chuyển pha |
|---|---|---|---|
| **P0** | Đọc repo, đối chiếu ERD, báo cáo chênh lệch | **ĐƯỢC PHÉP**: read-only | Báo cáo P0 để chủ dự án xem |
| **P1** | Chuẩn bị Gradle/server Flyway, DEV/TEST config | **HOLD** | Chủ dự án nói rõ cho phép sửa repo P1 DEV/TEST |
| **P2-A…F** | Flyway migrations theo 6 batch FK | **HOLD** | Chủ dự án duyệt P1 và cấp phép P2 từng batch/PR |
| **P3** | JPA Entity + Repository mapping `quizai-server` | **HOLD** | P2 được review/chấp nhận trên DEV/TEST; cấp lệnh P3 |
| **P4** | Transactional services, security & REST contracts | **HOLD** | Cấp lệnh P4 riêng; không tự mở rộng UI/AI |
| **P5** | Integration/concurrency/security/acceptance tests | **HOLD** | Cấp lệnh P5; chỉ DEV/TEST |
| **P6** | Preproduction review/backup/restore/retention | **HOLD** | Chủ dự án chốt production policy; không được deploy production tự động |

Không chạy tuần tự tự động qua nhiều phase. Một lệnh cấp phép pha này **không** tự cấp phép pha kế.

## 2. Prompt dùng ngay — P0 READ ONLY

Dùng nguyên văn nội dung file `QUIZAI_DB_CODEX_P0_READONLY_PROMPT_v1.0.md`. Đầu ra chỉ là báo cáo trên chat, không ghi file. P0 phải kiểm kê trạng thái git và thực tế các module, phát hiện migration/config cũ, đối chiếu 37 bảng và report sai khác, không tự sửa.

## 3. Prompt P1 — chuẩn bị môi trường (TEMPLATE, CHƯA ĐƯỢC PHÉP)

```text
TASK-003 / DB-05 — P1 DEV/TEST ENV BOOTSTRAP.
CHỈ THỰC HIỆN NẾU trong cuộc hội thoại hiện tại đã có lệnh RIÊNG từ chủ dự án cho phép triển khai P1 và chỉ P1. Nếu chưa có, dừng và báo "P1 NOT AUTHORIZED".

Trước tiên đọc approval record, ERD approved v1.0 và báo cáo P0. Chỉ sửa những file nằm trong phạm vi được duyệt và báo git diff từng file. Thiết lập cấu hình PostgreSQL DEV và TEST độc lập cho quizai-server, Flyway với versioning và cấu hình Hibernate không được tự tạo/đổi schema. Dùng env var/secret placeholder, không commit password, token hay Gemini key; không tác động production. Kiểm tra version dependency tương thích với Java 21, Spring Boot/Gradle thực tế; không giả định version.

GIAI ĐOẠN NÀY KHÔNG ĐƯỢC tạo SQL/DDL/migration, DB schema, bảng, JPA Entity/Repository, không thực thi migration, không kết nối production; nếu quyền cấp không bao gồm kết nối DEV/TEST thì chỉ tạo config/diff không kết nối. Không sửa quizai-client/admin/common/tcp-demo trừ khi chủ dự án cho phép phạm vi cụ thể.

Đầu ra: danh sách file thay đổi, git diff summary, cách cấu hình env an toàn, lệnh kiểm tra không rò secret, kết quả kiểm tra thực tế, các blocker. DỪNG SAU P1, chờ duyệt P2-A.
```

## 4. P2 — Flyway migrations chia thành 6 batch (TEMPLATE, CHƯA ĐƯỢC PHÉP)

**Không có SQL trong playbook này.** Mỗi batch chỉ được triển khai sau một lệnh riêng hoặc phạm vi được chủ dự án duyệt rõ; không tạo cả 37 bảng một lần bằng một prompt tổng.

| Batch | Bảng được phép trong batch khi có authorization | Count | Trọng tâm review |
|---|---|---:|---|
| **P2-A Identity/Class/Catalog** | `users`, `auth_sessions`, `classes`, `class_members`, `subjects`, `topics` | 6 | UUID, auth hashes, class join code digest, episode unique active, catalog seed policy |
| **P2-B Document/Questions/Quiz** | `documents`, `document_extractions`, `ai_generations`, `question_banks`, `questions`, `question_versions`, `question_options`, `quizzes`, `quiz_questions` | 9 | private storage key, owner, immutable question version, FK subject/topic, teacher bank |
| **P2-C Practice/PDF AI** | `practice_sessions`, `practice_session_questions`, `practice_answers`, `ai_conversations`, `ai_messages` | 5 | completed aggregate, frozen practice questions, one practice chat/session, PDF owner; **no assigned AI** |
| **P2-D Assignment/Snapshot/Release** | `assignments`, `assignment_recipients`, `assignment_question_snapshots`, `assignment_snapshot_options`, `assignment_publication_events` | 5 | membership episode FK, immutable snapshots, release first available, no score leakage |
| **P2-E Attempt/Device** | `attempts`, `attempt_answers`, `attempt_device_bindings`, `device_transfer_requests` | 4 | max attempts, ACK working answers, revocation, one active binding, deadline persisted |
| **P2-F Submission/History/Audit** | `submission_candidates`, `submission_candidate_answers`, `late_submission_reviews`, `submission_scores`, `attempt_result_events`, `assignment_recipient_score_events`, `idempotency_receipts`, `audit_events` | 8 | immutable candidates, same-attempt effective FK, nullable score, late reviews, event audit, idempotency |
| **TOTAL** | **37 distinct tables** | **37** | Must match approved ERD v1.0 inventory |

**Đặc biệt:** `attempts.effective_candidate_id → submission_candidates.id` tạo vòng FK. Trong P2-E tạo nullable field/appropriate staging nếu phù hợp, nhưng chỉ bổ sung constraint cross-attempt nhất quán sau khi bảng candidates có ở P2-F. Mọi phương án FK ghép/đảm bảo same-attempt phải đối chiếu DBX-16 và được review; không để service-only if FK feasible without conflicts.

### Prompt mẫu cho MỖI batch P2-X

```text
TASK-003 / DB-05 — FLYWAY MIGRATION [P2-X].
BLOCK UNLESS there is an explicit current owner authorization for this exact P2-X batch and DEV/TEST environment. Read approved ERD v1.0, Decision Log v0.18, approved record, P0/P1 review. Work only in quizai-server and only on the approved list of tables/constraints for this batch. Preserve frozen DEC and immutable histories. Do not add deferred post-assigned-exam AI.

Before coding: report the proposed migration filenames, DDL object inventory, dependency/order, unique/check/foreign keys, indexes and any ambiguity. If the proposed schema differs materially from ERD v1.0, STOP and request v1.1 approval.

After authorized implementation: present changed file list, migration checksums/ordering, DEV/TEST execution/test outcomes only for environments explicitly allowed, clean schema validation and rollback/restore instructions. Never touch production; never execute destructive commands on an existing DB. STOP after this batch; do not auto-continue to the next P2 batch, P3, or service implementation.
```

### Required constraints review per batch

- P2-A: email normalized uniqueness, one active membership/class/student, teacher & class owner authorization, never store plaintext join code/session token.
- P2-B: immutable approved question versions, question option belongs to question, teacher bank primary owner, extracted text private.
- P2-C: `practice_answers` option belongs to version, UQ session-question, one chat/session; no teacher read raw private data.
- P2-D: immutable snapshot questions/options, exactly one correct option at publication, assignment recipient tied to original class membership episode, independent score/answer release with `FIRST_AVAILABLE` append-only.
- P2-E: one active binding/attempt, unique `(recipient_id,attempt_no)` and start request ID, attempt deadline min rule, token hash with revoke-before-late guard at service layer.
- P2-F: at most one canonical base and late/attempt (per DBX-13), receipts dedupe request ID+payload digest, required review reason, scores normalized, correct candidate linked to attempt, no cascade delete of history; late ACCEPT can reduce score.

**Note:** Một số bất biến (đúng 1 đáp án ở trạng thái APPROVED, quyền owner, event first-available concurrency, candidate same attempt, payload digest, deadline ingress) cần phối hợp giữa constraint DB và transaction/service. Không tuyên bố CHECK thuần SQL xử lý được cross-row/cross-table. Với mọi lựa chọn giải pháp, nêu cách kiểm thử để chứng minh bất biến.

## 5. Prompt P3 — Entities và Repositories (TEMPLATE, CHƯA ĐƯỢC PHÉP)

```text
TASK-003 / DB-05 — P3 JPA MAPPING, AFTER OWNER AUTHORIZATION ONLY.
Verify P2-A…F migrations were reviewed and accepted in DEV/TEST. Do not generate new DDL and do not change schema merely to satisfy JPA. Implement JPA Entities and Repositories only in quizai-server, one-to-one mapping of approved columns/enums/UUID/TIMESTAMPTZ, conservative fetch behavior, explicit transaction semantics, no cascade remove on historical rows. quizai-common remains DTO/enum only.

For immutable candidate/snapshot/score/review/audit history, enforce append-only at domain/repository boundaries and back it with DB constraints where approved; do not return raw correct options before release authorization. Distinguish nullable grade from 0; don't use float for normalized scores if ERD specifies exact precision.

Deliver mapping table -> Entity, repo methods with owner filters, schema-validation result and integration test results against TEST DB. STOP after P3, do not implement business APIs or deploy automatically.
```

## 6. Prompt P4 — transactional service & authorization (TEMPLATE, CHƯA ĐƯỢC PHÉP)

```text
TASK-003 / DB-05 — P4 SERVICES, AFTER OWNER AUTHORIZATION ONLY.
Read ERD v1.0 sections 5–8 and acceptance matrix T01–T30. Implement only authorized backend service/API contracts, with transaction/row locks/unique constraints/idempotency. Include start attempt, autosave ACK, canonical on-time and SERVER_TIMEOUT, late candidate + teacher reasoned review, device transfer and revoke-first, final grade recomputation, release non-retraction and per-owner authorization. No AI post-assigned-attempt feature without Change Request. No unrequested JavaFX rewrites.

Preserve server-trusted durable ingress time, deadline calculation, original membership episode, immutable snapshots/candidates and audit. Explicitly test collision cases. If a race cannot be resolved under approved schema, STOP, report transaction timeline and request review instead of silently changing schema.

Return changed files, API behavior, transaction diagram, tests, remaining blockers; STOP before P5.
```

## 7. Prompt P5 — acceptance/integration/security (TEMPLATE, CHƯA ĐƯỢC PHÉP)

```text
TASK-003 / DB-05 — P5 DEV/TEST VERIFICATION, AFTER OWNER AUTHORIZATION ONLY.
Use TEST PostgreSQL separate from DEV. Execute approved DB/unit/integration/concurrency/security tests mapped to ERD T01–T30; report PASS/FAIL/SKIP individually with evidence. Cover two concurrent Starts, autosave/timeout/late race, old binding revoked, late ACCEPT grade decrease, NULL vs 0, first availability non-retraction, owner-private AI/doc/practice, leave+rejoin membership, 7/30 day completed practice aggregation, idempotency and secret non-disclosure.

No test may connect to production. Do not hide failures or mark unexecuted tests PASS. For blockers propose targeted fixes and wait for approval if they touch approved schema/business decisions. Return traceability matrix DEC/REQ/ERD -> tests and a GO/NO-GO recommendation; STOP before P6.
```

## 8. Prompt P6 — preproduction readiness (TEMPLATE, CHƯA ĐƯỢC PHÉP)

```text
TASK-003 / DB-05 — P6 RELEASE READINESS REVIEW ONLY, AFTER OWNER AUTHORIZATION.
Review prior migration/restore reports, class/role permissions, token secrets, AI cost, quotas, DB indexes, timeout jobs, backup/restore evidence, data retention and data deletion compliance. Require explicit owner decisions for concrete retention/backup/DR policies before production; no production connection, migration or deployment unless owner separately and unambiguously authorizes production action.

Deliver a release readiness decision sheet with unresolved operational gates. STOP — do not deploy.
```

## 9. Mẫu báo cáo Codex bắt buộc sau mỗi pha

```text
PHASE: P<id>
AUTHORIZATION QUOTE: <exact user command authorizing this phase, or MISSING>
SOURCE ERD: QUIZAI_DB05_ERD_FINAL_FOR_APPROVAL_v1.0.md
ERD SHA-256 VERIFIED: 5ff37a672d9a76ce4092301f53064a850304fed0ace74d8f71fa9db766aa31ab
SCOPE PERMITTED: <exact modules/files/environments>
GIT STATUS BEFORE/AFTER: <summary>
FILES CREATED/MODIFIED/DELETED: <paths>
DDL/MIGRATION/JPA CREATED: <yes/no, paths>
POSTGRESQL TOUCHED: <none / TEST / DEV; never production unless separately authorized>
CHECKS: <PASS/FAIL/NOT RUN with evidence>
DEC/REQ/ERD CONFLICTS: <none or details>
BLOCKERS + RISKS: <details>
NEXT STEP REQUEST: <specific next phase; STOP until approved>
```

## 10. Điều kiện thoát cuối cùng và tài liệu viện dẫn

- **Bước có thể thực hiện ngay:** Chỉ đưa prompt P0 read-only cho Codex để Codex báo cáo hiện trạng repo. Nếu muốn Codex tạo báo cáo dưới dạng file trong repo, chủ dự án phải cấp phép ghi file riêng.
- **Bước tuyệt đối chưa làm:** P1–P6, SQL/DDL, Flyway, JPA, Repository, sửa PostgreSQL, thay đổi Gradle, gọi Gemini trong bài thi hoặc thêm AI hậu bài giao.
- Đầu ra tài liệu này chỉ là **hướng dẫn**, không phải mã SQL hay migration; các câu lệnh trong khối ```text``` là **prompt mẫu**, không tự chạy trong project.
- Nếu muốn sửa ERD v1.0, xuất phiên bản mới và xin phê duyệt thay vì sửa thầm file đã duyệt hoặc bỏ qua checksum.

**KẾT LUẬN:** ERD APPROVED; IMPLEMENTATION HOLD. Codex được chuẩn bị/kiểm tra P0 read-only, mọi pha còn lại chờ lệnh riêng của chủ dự án.
