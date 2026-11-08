# Codex Prompt P0 — QuizAI Desktop DB Read-only Audit

**PROMPT AN TOÀN — CHỈ KIỂM TRA, KHÔNG VIẾT FILE**

```text
Bạn là Codex được giao TASK-003 / DB-05, P0: READ-ONLY REPOSITORY AUDIT cho QuizAI Desktop.

Đã phê duyệt THIẾT KẾ ERD v1.0, nhưng chủ dự án CHƯA CHO PHÉP triển khai database hoặc sửa repository. Hãy tuyệt đối KHÔNG tạo, sửa, xóa bất kỳ file nào; KHÔNG tạo SQL/DDL/Flyway migration/JPA Entity/Repository; KHÔNG chỉnh Gradle/config; KHÔNG cài thư viện; KHÔNG truy cập hay kết nối PostgreSQL; KHÔNG chạy build/test nếu tạo ra output/cache mới; KHÔNG thực hiện commit, push hoặc tự sửa lỗi. Chỉ dùng các thao tác đọc như git status, git log, danh sách file, đọc source/config/tài liệu, grep. Nếu một lệnh có thể gây ghi ra đĩa, không chạy.

SOURCE OF TRUTH — TÌM TRONG docs/ HOẶC YÊU CẦU CHỦ DỰ ÁN CUNG CẤP NẾU CHƯA ĐƯỢC COPY VÀO PROJECT:
1. PROJECT_DECISIONS_v7.md — 34 DEC frozen.
2. REQUIREMENTS_BASELINE_v1.0.md — MVP frozen.
3. QUIZAI_DB_DESIGN_DECISIONS_v0.18.md — FINAL-01..12=A.
4. QUIZAI_DB05_ERD_FINAL_FOR_APPROVAL_v1.0.md — ERD đã được phê duyệt, 37 bảng logic.
5. QUIZAI_DB05_ERD_APPROVAL_RECORD_v1.0.md — phê duyệt ERD, KHÔNG phê duyệt triển khai.

SHA-256 ERD được phê duyệt: 5ff37a672d9a76ce4092301f53064a850304fed0ace74d8f71fa9db766aa31ab

VIỆC CẦN LÀM:
A. Chỉ đọc, xác định cấu trúc repository thực tế, trạng thái git, module quizai-server/quizai-client/quizai-admin/quizai-common/quizai-tcp-demo nếu hiện diện; không giả định file/path có thật.
B. Đọc cấu hình Java/Gradle/Spring Boot/PostgreSQL hiện có, xác minh framework, dependency JPA/Flyway, phiên bản và nguy cơ trùng schema/migration. Không tự thay đổi.
C. So checklist 37 bảng logic, thứ tự phụ thuộc FK, UUID, PostgreSQL DEV/TEST độc lập, Flyway, Hibernate ddl-auto validate/none, ownership, unique, immutability, late review/idempotency, publication first available.
D. Đánh dấu rõ phần OUT OF MVP: AI chat sau bài giảng viên giao và các cột đặc thù assigned attempt/candidate context chưa được Change Request duyệt; không đưa vào schema.
E. Chỉ ra các khác biệt thực tế giữa repository và ERD. Nếu thấy vấn đề đòi thay schema/DEC/REQ, lập BLOCKER/CHANGE REQUEST PROPOSAL, không tự sửa.

OUTPUT CHỈ TRONG CHAT:
1. Repository inventory + git status + source docs availability/checksum.
2. Bảng MATCH/MISMATCH/UNKNOWN giữa project và ERD (ưu tiên các invariant quan trọng).
3. Danh sách rủi ro/blocker có file:line nếu có.
4. Đề xuất diff cho P1 (tên file dự kiến, không tạo file), cùng lệnh test dự kiến nhưng KHÔNG chạy.
5. Kết thúc chính xác: “P0 complete — no files changed, no SQL/migrations/JPA created, no PostgreSQL accessed. Awaiting explicit P1 authorization.”

Nếu các file nguồn chưa có trong repo: dừng trước khi suy diễn schema, báo thiếu file và đề nghị copy vào docs/. Không tự tìm một phiên bản khác để thay thế.
```
