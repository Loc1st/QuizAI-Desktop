# TASK-003 / DB-05 — FULL DATABASE + BACKEND INTEGRATION + DEV/TEST DEPLOYMENT

**Chủ dự án cấp phép triển khai trong phạm vi DEV và TEST để tạo database thật, triển khai backend MVP và chạy kiểm thử. PRODUCTION KHÔNG ĐƯỢC CẤP PHÉP.** Đây là một đợt công việc lớn: hãy thực hiện tuần tự bên trong một phiên Codex, ghi kết quả vào một báo cáo tổng hợp cuối cùng thay vì dừng sau từng file; dừng ngay khi gặp blocker về an toàn, schema không nhất quán hoặc không có credentials/môi trường được phép.

## Nguyên tắc không thương lượng

- `QUIZAI_DB05_ERD_FINAL_FOR_APPROVAL_v1.0.md` SHA-256 `5ff37a672d9a76ce4092301f53064a850304fed0ace74d8f71fa9db766aa31ab`, 34 DEC frozen, Requirements Baseline và Decision Log v0.19 là nguồn nghiệp vụ. **Không tự sửa ERD đã duyệt.**
- Package `QUIZAI_DB_FULL_DEV_BUNDLE_v1.0` là **mã đề xuất** chưa được chạy PostgreSQL runtime. Không coi nó đã production-ready. Đọc `../../../README.md` và kiểm tra từng migration trước khi tích hợp.
- Tình trạng trước đó: P1 config complete, P2-A V1 source complete và Gradle test 17/17 PASS (theo báo cáo phiên trước), **chưa chạy PostgreSQL**. Kiểm tra lại source thực tế, không lấy báo cáo cũ làm bằng chứng runtime.
- Không sửa/reset/clean/stage/commit thay đổi Git có sẵn nếu chưa có lệnh commit. Không đổi JavaFX UI previews và file trong `docs` trừ khi thực sự cần sửa đường dẫn, có giải thích.
- Chỉ `quizai-server` truy cập PostgreSQL; `quizai-client` và `quizai-admin` qua REST/WSS; `quizai-common` DTO/enum; `quizai-tcp-demo` độc lập.
- Không có AI giải thích hậu bài giảng viên giao trong MVP khi chưa có Change Request; không tạo `assigned_attempt_id` hay `context_submission_candidate_id`.
- Flyway SQL init và database connection **không bật ở profile mặc định**. DEV/TEST credentials riêng, không commit credentials. Không chạm hoặc kiểm tra production.

## Giai đoạn 0 — kiểm kê đúng source và bảo vệ dữ liệu

1. `git status --short` và hash ERD, backup các file mà lượt code này sẽ sửa trong thư mục tạm **ngoài repository** (không reset).
2. Kiểm tra source V1 hiện hữu và migration V2 hiện hữu (nếu có). **Nếu bất kỳ migration trùng số phiên bản nhưng khác byte, dừng bước schema**, xuất diff và giải thích; tuyệt đối không ghi đè migration đã từng chạy.
3. Chạy `python scripts/safe_merge_source.py --repo <PATH>` từ package ở chế độ dry-run; chỉ `--apply` khi đã đối chiếu đầy đủ file, Git status và ERD. Nếu đang có V2 riêng, xác định kế hoạch hợp lệ thay vì force.
4. Kiểm tra chính xác P1 YAML/Gradle và startup fail-closed với profile mặc định. Dừng nếu đường dẫn DB có nguy cơ trỏ sang production hoặc DEV/TEST cùng database.

## Giai đoạn 1 — triển khai database PostgreSQL DEV/TEST thật

1. Chỉ dùng PostgreSQL DEV/TEST do chủ dự án cung cấp hoặc tạo **mới trên máy DEV** bằng `compose.postgres-dev-test.yml`. Trước khi tạo, kiểm tra Docker/Windows paths, port 5433/5434 và các volume đã có; không xóa volume, không drop database.
2. Bắt buộc credentials tách biệt, lấy từ môi trường ngoài Git; không chụp/log password; không hạ quyền bảo vệ. Nếu chưa có Docker/PostgreSQL hoặc secrets, báo blocker; **không tự sử dụng production**.
3. Review và dùng Flyway V1–V8 trong `../../../quizai-server/src/main/resources/db/migration`. V1 nguyên byte. V2–V6 hoàn thành 37 bảng; V7/V8 chỉ functions/view/guards. Check PostgreSQL version và constraints trước khi chạy.
4. Migrations chỉ được bật **tạm thời** trong profile `dev`/`test` đã xác minh host, port và DB; tuyệt đối không bật global profile. Xác nhận Flyway history schema, validate/checksum và 37 bảng.
5. Chạy `tests/postgresql_catalog_verification.sql` rồi `tests/postgresql_transactional_acceptance.sql` trên **TEST database**; transaction phải ROLLBACK. Nếu lỗi, sửa file nguồn chưa applied; nếu migration đã applied, dùng migration version tiếp theo có review thay vì sửa lịch sử âm thầm.
6. Database OWNER/app least privilege; không hardcode key/HMAC; không seed password demo vào DB. Cần có DB schema dump và backup/restore thử trên TEST trước khi xem là ready.

## Giai đoạn 2 — code thật đầy đủ nghiệp vụ MVP trong source hiện hành

1. Xem package/app đã tồn tại; sử dụng Java 21, Spring Boot 4.0.9, JdbcTemplate/JPA tùy kiến trúc đã duyệt. Không đưa Entity vào Common/Client/Admin/TCP. File `QuizaiReadModelsJdbc.java` trong package chỉ là adapter read-only độc lập, cần tích hợp đúng package/beans thật, tránh lộ identity từ request body.
2. Tạo Spring Security authentication, register STUDENT/TEACHER, hash password, session revoke/expiry, HMAC join code, rate limit, RBAC + per-record owner/recipient validation, audit sanitized. Không thêm SUPERADMIN/teacher verification.
3. Lớp: teacher own classes, student join/leave/rejoin bằng episode; bảo vệ assignment của episode cũ. CRUD teacher bank/quiz; student private practice quiz; version DRAFT→approve, không sửa approved hoặc snapshot sau giao.
4. Documents PDF/DOCX/TXT/paste: validate size, MIME và path, lưu file/text private ngoài PostgreSQL; Gemini key backend ENV. AI generation DRAFT cần teacher review. PDF Tutor và practice AI chat private; ngăn **mọi lối gọi QuizAI AI khi assigned attempt đang IN_PROGRESS**. Nếu Gemini key chưa được cấp, trả lỗi có ý nghĩa và đánh dấu integration pending, không giả lập thành công.
5. Practice finish/lịch sử/aggregate `COMPLETED`, membership episode `[joined_at,left_at)`, cửa sổ thống kê `[from,to)`; teacher chỉ aggregate, không thấy private document/quiz/answer/chat.
6. Assignment dashboard config opens/closes/duration/max attempts, independent score/answer releases, answer_release_mode; snapshot immutable, scoped recipients, no unstarted old rights after rejoin. Score release/answer release first-available không thu hồi dù chưa ai GET.
7. Attempt workflow: authenticated active device session, start consumes attempt, min(start+duration,closes), autosave ACK revision+request id, encrypted client-local draft, one active binding, teacher device transfer revokes old before any write. Timeout uses only ACKed checkpoint; 0 answered => 0 valid, no eligible attempt=>NULL. Base CLIENT_ON_TIME XOR SERVER_TIMEOUT, one late candidate, pending review with reason/audit; late ACCEPT may **increase or decrease** effective score. Highest grade is across eligible **attempts**, never MAX between candidates within one attempt.
8. Add WSS notifications only as authorized metadata, never send unreleased score/correct answer. Desktop JavaFX views must call backend and display actual data; do not leave fixture-only paths falsely presented as working.
9. Any unmet function must be explicitly reported with code/test evidence, not quietly marked completed.

## Giai đoạn 3 — test cuối và triển khai DEV

1. Chạy `:quizai-server:compileJava`, `:quizai-server:test`, integration tests on isolated TEST PostgreSQL, concurrency and negative RBAC/privacy tests; use ERD T01–T30 + DBX-16 cross-attempt negative case, plus real API/JDBC tests.
2. Run JavaFX client/admin navigation tests and integration against DEV backend; Gemini/TCP only if test environment/keys are available. Verify `git diff --check`, Flyway history, Spring profiles, DB seed/fixture isolation and no secrets in Git/logs.
3. Nếu **mọi gate cần thiết** PASS, start Spring Boot **DEV local** (127.0.0.1), confirm health/API and JavaFX connectivity. Đây là **triển khai thử nghiệm DEV**, không phải production.
4. Nếu bất kỳ blocking test FAIL hoặc thiếu source/key/DB, dừng trước deploy và nêu chính xác còn thiếu. Không tự đổi config production hay bịa kết quả PASS.

**Báo cáo cuối cùng trong một lượt:** số bảng applied trong DEV/TEST, Flyway version+checksum, danh sách API/services/UI tích hợp, tổng tests PASS/FAIL/skipped, T01–T30 coverage, health endpoint thật, các blockers, lệnh chạy app trên Windows, git diff; kết luận rõ **FULL DEV READY / PARTIAL / BLOCKED**. Không tuyên bố production deployed.
