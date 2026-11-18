# QuizAI Desktop — Chỉ mục tài liệu hiện hành

**Phạm vi áp dụng:** Đồ án **QuizAI v2 (CR-001, AI-first / online-first, 24 bảng logic)**. Chủ dự án đã phê duyệt CR-001 và ERD v2.0 vào **08/11/2026**. Tài liệu v1 vẫn được giữ làm lịch sử và để bảo toàn database đã triển khai.

## Đọc theo thứ tự khi lập trình

1. [`v2/README.md`](v2/README.md) — lối vào tài liệu v2 và thứ tự ưu tiên.
2. [`v2/QUIZAI_V2_APPROVAL_RECORD_CR001_v1.0.md`](v2/QUIZAI_V2_APPROVAL_RECORD_CR001_v1.0.md) — ghi nhận **APPROVED** sau khi bản gốc còn mang nhãn DRAFT/PROPOSED.
3. [`v2/QUIZAI_ACTIVE_SCOPE_v2.md`](v2/QUIZAI_ACTIVE_SCOPE_v2.md) — phạm vi tính năng giữ/hoãn và ranh giới môi trường.
4. [`v2/QUIZAI_CHANGE_REQUEST_CR-001_SIMPLIFIED_MVP.md`](v2/QUIZAI_CHANGE_REQUEST_CR-001_SIMPLIFIED_MVP.md) — thay đổi so với 34 DEC và Requirements Baseline.
5. [`v2/QUIZAI_ERD_SIMPLIFIED_24_TABLES_DRAFT_v2.0.md`](v2/QUIZAI_ERD_SIMPLIFIED_24_TABLES_DRAFT_v2.0.md) — ERD **đã được duyệt**; “DRAFT” chỉ là tên lịch sử của file gốc.
6. [`v2/QUIZAI_SCHEMA_V2_IMPLEMENTATION_PLAN_CR001_v1.0.md`](v2/QUIZAI_SCHEMA_V2_IMPLEMENTATION_PLAN_CR001_v1.0.md) — thứ tự migration và kiểm thử (không tự coi là đã chạy).

## Nguồn nghiệp vụ gốc, giữ nguyên byte

- [`PROJECT_DECISIONS_v7.md`](PROJECT_DECISIONS_v7.md) — 34 DEC gốc; chỉ các mục được CR-001 chấp thuận mới bị thay thế đối với v2.
- [`REQUIREMENTS_BASELINE_v1.0.md`](REQUIREMENTS_BASELINE_v1.0.md) — baseline v1; CR-001 áp dụng cho các yêu cầu bị ảnh hưởng, không âm thầm chỉnh baseline.

## Tài liệu tham khảo / không phải nguồn triển khai v2

- [`archive/db-v1/`](archive/db-v1/) — ERD 37 bảng, Decision Logs cũ, prompt/handoff và bằng chứng triển khai v1. **Không dùng ERD hoặc Flyway v1 để sinh schema v2.**
- [`ui-previews/`](ui-previews/), [`ui-references/`](ui-references/) — tài nguyên giao diện; giữ nguyên.

## Quy tắc an toàn triển khai

- PostgreSQL legacy **`quizai_dev` / `quizai_test`** và Flyway **V1–V8** của `db/migration/` phải được giữ nguyên.
- V2 dùng riêng **`quizai_v2_dev` / `quizai_v2_test`**, migration **V1–V5** trong `db/migration-v2/`. Không trộn Flyway locations hoặc sử dụng lệnh repair/drop trên v1.
- **Phê duyệt thiết kế không đồng nghĩa ứng dụng đã được kiểm thử/chạy.** Kiểm tra PostgreSQL thật, REST, JavaFX và Gemini cần có bằng chứng riêng.
- Không commit mật khẩu, token hoặc Gemini API key; không sửa/hủy các thay đổi Git có sẵn khi sắp xếp docs.

**Quản lý phiên bản:** Không đổi tên hoặc sửa nội dung CR-001 / ERD v2 chỉ để bỏ nhãn PROPOSED/DRAFT. Dùng biên bản phê duyệt bổ sung để xác nhận hiệu lực.
