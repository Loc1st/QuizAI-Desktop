# QuizAI v2 — Bộ hồ sơ đang có hiệu lực

**Trạng thái:** `CR-001 APPROVED` và `ERD v2.0 APPROVED` — xác nhận trực tiếp của chủ dự án ngày **08/11/2026**. **Mục tiêu:** 24 bảng, AI-first, online-first cho đồ án đại học.

| Thứ tự | Tài liệu | Vai trò |
|---|---|---|
| 1 | [`QUIZAI_V2_APPROVAL_RECORD_CR001_v1.0.md`](QUIZAI_V2_APPROVAL_RECORD_CR001_v1.0.md) | Biên bản phê duyệt mới nhất, giải quyết chữ `PROPOSED` / `DRAFT` trong file lịch sử |
| 2 | [`QUIZAI_ACTIVE_SCOPE_v2.md`](QUIZAI_ACTIVE_SCOPE_v2.md) | Tính năng bắt buộc, được hoãn và giới hạn phạm vi |
| 3 | [`QUIZAI_CHANGE_REQUEST_CR-001_SIMPLIFIED_MVP.md`](QUIZAI_CHANGE_REQUEST_CR-001_SIMPLIFIED_MVP.md) | Thay đổi 9 DEC và các requirement liên quan |
| 4 | [`QUIZAI_ERD_SIMPLIFIED_24_TABLES_DRAFT_v2.0.md`](QUIZAI_ERD_SIMPLIFIED_24_TABLES_DRAFT_v2.0.md) | Danh sách chính xác 24 bảng, khóa, quan hệ, JSONB snapshots |
| 5 | [`QUIZAI_SCHEMA_V2_IMPLEMENTATION_PLAN_CR001_v1.0.md`](QUIZAI_SCHEMA_V2_IMPLEMENTATION_PLAN_CR001_v1.0.md) | Triển khai/test v2 TEST trước DEV, 20 V2X và 25 V2-T |

## Quy tắc ưu tiên khi có xung đột

**(1) Quyết định chấp thuận CR-001** → **(2) nội dung CR-001 và ERD v2.0 đã duyệt** → **(3) Active Scope/implementation plan v2** → **(4) 34 DEC và Requirements Baseline v1 đối với phần không bị thay thế** → **(5) hồ sơ v1 để tra cứu lịch sử**.

## Những chức năng trọng tâm cần bảo vệ

- Gemini tạo câu MCQ từ PDF, DOCX, TXT, văn bản; giáo viên duyệt AI questions trước giao.
- Gia sư PDF, chat và giải thích AI cho **lượt tự học riêng**; AI không trợ giúp khi đang thi được giao.
- Student/teacher tự đăng ký; lớp/mã tham gia; ngân hàng đề; tự học; giảng viên giao bài và chọn nhóm sinh viên.
- Online autosave ACK, snapshot đề bất biến, hết giờ chấm câu đã ACK, bài trắng `0`, chưa có lượt chấm `NULL`, điểm cao nhất giữa các lượt hợp lệ; công bố điểm/đáp án tách biệt.
- JavaFX → REST Spring Boot → PostgreSQL; key Gemini chỉ trên backend; WSS và TCP LAN demo được giữ theo baseline.

## Ngoài phạm vi MVP v2

Offline queue/draft, late review, device binding/transfer, candidate history nâng cao và AI giải thích **sau bài giảng viên giao** (cần phê duyệt riêng nếu muốn mở rộng).

## Ranh giới dữ liệu

- Không sử dụng `quizai_dev` và `quizai_test` (v1 37 bảng) làm nơi migrate v2.
- Không sửa migrations v1 V1–V8 đã áp dụng; không tự cho rằng SQL v2 đã PASS khi mới kiểm tra tĩnh.
- Hiện trạng repo/test thực tế phải do Codex kiểm tra và báo cáo, không suy đoán từ tài liệu này.
