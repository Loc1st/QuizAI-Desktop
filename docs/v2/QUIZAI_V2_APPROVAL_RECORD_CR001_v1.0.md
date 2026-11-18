# QuizAI Desktop — Biên bản phê duyệt CR-001 / ERD v2.0

**Ngày phê duyệt:** 08/11/2026 (Asia/Bangkok)  
**Người phê duyệt:** Chủ dự án, xác nhận trực tiếp trong hội thoại  
**Trạng thái:** **APPROVED — phạm vi MVP và ERD logic v2.0**, gồm **24 bảng nghiệp vụ**.

## 1. Nội dung được phê duyệt

Chủ dự án đã xác nhận: *“Tôi phê duyệt Change Request CR-001 và ERD tinh gọn v2.0 gồm 24 bảng cho QuizAI Desktop. Giữ AI tạo câu hỏi, PDF tutor, tự học có AI, dashboard giảng viên, lớp học, giao bài, nhiều lượt và điểm cao nhất; hoãn offline, late-review và device-transfer. Giữ nguyên database quizai_dev/quizai_test và migrations V1–V8 đã áp dụng.”*

Phê duyệt này **thay thế hướng triển khai schema v1 (37 bảng) đối với sản phẩm v2**, nhưng không xóa hồ sơ và dữ liệu v1.

## 2. Tài liệu được xác nhận

- `QUIZAI_CHANGE_REQUEST_CR-001_SIMPLIFIED_MVP.md` — bản gốc có dòng trạng thái **PROPOSED** do được tạo trước phê duyệt; quyết định chủ dự án ở trên là bằng chứng phê duyệt sau đó.
- `QUIZAI_ERD_SIMPLIFIED_24_TABLES_DRAFT_v2.0.md` — bản gốc có từ **DRAFT** trong tên và tiêu đề, nhưng đã được duyệt là **ERD v2.0**. Nội dung file gốc không tự bị sửa vì thay đổi trạng thái.
- `QUIZAI_ACTIVE_SCOPE_v2.md` và `QUIZAI_SCHEMA_V2_IMPLEMENTATION_PLAN_CR001_v1.0.md` — tài liệu phạm vi và triển khai bổ trợ, không được vượt quá CR-001 và ERD đã duyệt.
- `PROJECT_DECISIONS_v7.md` và `REQUIREMENTS_BASELINE_v1.0.md` — giữ nguyên; áp dụng CR-001 như phần điều chỉnh đã được phê duyệt.

## 3. Quy tắc triển khai và bảo toàn dữ liệu

| Đối tượng | Quy định |
|---|---|
| DB v1 | Giữ nguyên `quizai_dev` và `quizai_test`, mỗi DB 37 bảng theo Flyway V1–V8; không DROP/TRUNCATE, repair hoặc sửa migrations đã áp dụng |
| DB v2 | Môi trường riêng `quizai_v2_test` và `quizai_v2_dev`; chỉ áp dụng migration v2 tại `db/migration-v2/`, kiểm thử TEST trước DEV |
| Chức năng trọng tâm | Tài khoản/lớp/quiz/giao bài/tự học/PDF tutor/Gemini tạo câu hỏi; giới hạn thời gian, nhiều lượt, lịch sử và điểm cao nhất |
| Hoãn | Offline, late review, device transfer, candidate history nâng cao |
| Không tự thêm | AI giải thích sau bài giảng viên giao (cần CR riêng), SUPERADMIN, teacher verification, course grade không có baseline |

## 4. Giới hạn của phê duyệt và bằng chứng triển khai

- **APPROVED** ở đây chỉ xác nhận **phạm vi và ERD v2**. Bản ghi này không khẳng định PostgreSQL v2 đã chạy migration thành công, backend/API/JavaFX đã kết nối dữ liệu thật hoặc Gemini đã kiểm thử thực tế.
- Mọi công việc triển khai phải kiểm tra đúng database, người dùng, schema và Flyway location; tuyệt đối không trỏ profile v2 tới `quizai_dev` hoặc `quizai_test`.
- Việc bổ sung/sắp xếp docs trong đợt hiện tại **không cấp thêm quyền ghi PostgreSQL**. Những hành động chạy migration, seed dữ liệu hoặc triển khai ứng dụng cần được kiểm soát theo yêu cầu thực thi riêng.
- Giữ Git status/index và nội dung file lịch sử; không tự xóa, reset hoặc ghi đè nội dung tài liệu đã có.

## 5. Trạng thái lúc bàn giao

- **CR-001:** APPROVED.
- **ERD v2.0:** APPROVED.
- **Bộ hồ sơ v2:** nguồn đang có hiệu lực, lưu ở `docs/v2/`.
- **Chạy PostgreSQL v2 / kiểm thử / tích hợp ứng dụng:** phải dựa vào báo cáo kiểm tra thực tế mới nhất, không suy diễn PASS từ phê duyệt thiết kế.
