# QuizAI Desktop — DB-05 ERD v1.0 Approval Record

**Ngày xác nhận:** 2026-11-07  
**Dự án:** QuizAI Desktop · TASK-003 / DB-05  
**GATE 1 — ERD DESIGN:** **APPROVED**  
**GATE 2 — DATABASE IMPLEMENTATION:** **NOT AUTHORIZED**  
**GATE 3 — PRODUCTION DEPLOYMENT:** **NOT AUTHORIZED**

## 1. Lệnh phê duyệt của chủ dự án

> “Phê duyệt ERD v1.0 cho QuizAI Desktop. Chưa cho phép triển khai database; chuẩn bị hướng dẫn Codex thực hiện theo từng giai đoạn.”

Việc phê duyệt này áp dụng **chính xác** cho file `QUIZAI_DB05_ERD_FINAL_FOR_APPROVAL_v1.0.md` hiện có. Không tự nâng thành phê duyệt SQL/DDL, migration, entity, repository, cấu hình kết nối PostgreSQL, hoặc ứng dụng thực tế.

## 2. Baseline được đóng băng bằng checksum

| Tài liệu | Trạng thái | SHA-256 |
|---|---|---|
| `QUIZAI_DB05_ERD_FINAL_FOR_APPROVAL_v1.0.md` | **ERD APPROVED** | `5ff37a672d9a76ce4092301f53064a850304fed0ace74d8f71fa9db766aa31ab` |
| `QUIZAI_DB_DESIGN_DECISIONS_v0.18.md` | **12 FINAL CONFIRMED A; source thiết kế** | `0bc5fd4ca2889512581ab8539e8191f68d79f622b6b3806650929193d6e1859b` |
| `PROJECT_DECISIONS_v7.md` | **34 DEC FROZEN** | `d99d1c7aeae173954beb110420422ba664bc76a37419dd7a070052202ee419a3` |
| `REQUIREMENTS_BASELINE_v1.0.md` | **MVP FROZEN** | `f3d25763ac3c14042754a223c1ec4b330f0c1efa3d857ba196e90fbd2cc92c2a` |

**Ghi chú phiên bản:** Trong chính ERD v1.0 và phiếu `QUIZAI_DB_APPROVAL_GATE_v1.0.md`, trạng thái cũ “AWAITING USER APPROVAL” là trạng thái ở thời điểm tài liệu được tạo. **Hồ sơ phê duyệt này ghi nhận việc em phê duyệt sau đó**, chỉ thay đổi *trạng thái phê duyệt*, **không chỉnh sửa byte/nội dung của ERD đã ký duyệt**. Bất kỳ nội dung schema thay đổi đáng kể phải ra bản v1.1 và được phê duyệt lại.

## 3. Phạm vi được phê duyệt ở mức thiết kế

- **37 bảng logic MVP** trong data dictionary mục 3 của ERD v1.0, cùng các PK/FK/UNIQUE/CHECK/index, state machines, quyền truy cập, audit, idempotency, privacy, concurrency và acceptance matrix mục 4–11.
- **UUID**, **Flyway**, PostgreSQL **DEV/TEST riêng**, file storage private ngoài DB, database chỉ thuộc `quizai-server`.
- Snapshot bất biến; bài timeout trắng là 0 hợp lệ; không có attempt eligible là grade `NULL`; late candidate accepted có thể hạ/tăng điểm; điểm assignment = MAX giữa eligible attempts, không MAX giữa candidates của cùng attempt.
- Public score/answers độc lập, chống thu hồi quyền đã khả dụng; membership episode không khôi phục quyền cũ khi rejoin; thiết bị bị thu hồi phải bị chặn trước kiểm tra late.

## 4. Phạm vi KHÔNG được phê duyệt triển khai

- **Mọi thay đổi mã nguồn/repository hoặc database do lời xác nhận này đơn thuần**: chưa có lệnh bắt đầu triển khai.
- **AI giải thích/chat sau bài giảng viên giao** (`AI-ASSIGNED-POST-01`, `AI-LATE-PENDING`, `REVIEW-AI-HISTORY-PENDING`, `REVIEW-AI-HISTORY-VERSION`): đã chốt hành vi thiết kế nhưng phải có **Change Request** riêng trước khi thêm vào MVP; không tạo `assigned_attempt_id`, `context_submission_candidate_id` cho AI hậu bài giao trong migration MVP.
- Tự động sinh nhiều mã đề, chia sẻ ngân hàng teacher, tính điểm học kỳ/toàn môn, xác minh teacher, OCR, web/mobile, TCP Internet.
- Production, retention định lượng, data deletion và chính sách backup production vẫn cần cổng vận hành riêng.

## 5. Cổng được phép thực hiện ngay

**P0 — READ-ONLY REPOSITORY INSPECTION**: Có thể gửi cho Codex để đọc cấu trúc project, `git status`, Gradle/module config, kiểm tra tài liệu, xác định xung đột và báo cáo kế hoạch. **Không sửa bất kỳ file nào, không cài dependency, không chạy migration, không tạo DB, không kết nối hay thay đổi PostgreSQL, không tạo SQL/DDL/JPA/Repository.**

Để bắt đầu P1/P2..., chủ dự án phải cấp lệnh riêng nêu **giai đoạn, phạm vi mã nguồn, môi trường DEV/TEST được phép, và tiêu chí dừng**. Phê duyệt P0 hoặc ERD không cấp phép tự chạy P1/P2.

## 6. Tài liệu handoff

- `QUIZAI_DB_CODEX_PHASED_HANDOFF_v1.0.md`: playbook và prompt độc lập theo từng giai đoạn, kiểm soát quyền thao tác.
- `QUIZAI_DB_CODEX_P0_READONLY_PROMPT_v1.0.md`: prompt P0 có thể dán vào Codex ngay.

**Audit:** Không có SQL, Flyway migration, JPA Entity, Repository hoặc thay đổi PostgreSQL nào được tạo bởi việc ghi nhận phê duyệt này.
