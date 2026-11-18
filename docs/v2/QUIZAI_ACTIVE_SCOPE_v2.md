# QuizAI Desktop — Active scope v2 / CR-001 APPROVED

**Ngày xác nhận:** 2026-11-08. **Phạm vi hiện hành:** ERD v2.0, **24 bảng nghiệp vụ**, **AI-first, online-first**, đồ án JavaFX + Spring Boot + PostgreSQL + Gemini.

## Source of truth

1. `QUIZAI_CHANGE_REQUEST_CR-001_SIMPLIFIED_MVP.md` — được chủ dự án phê duyệt, **thay đổi** 9 DEC và nhóm requirement về offline/device/late so với v1.
2. `QUIZAI_ERD_SIMPLIFIED_24_TABLES_DRAFT_v2.0.md` — **APPROVED** bằng xác nhận của chủ dự án; chữ DRAFT trong header là lịch sử tại lúc tạo file, không rút lại phê duyệt.
3. `QUIZAI_SCHEMA_V2_IMPLEMENTATION_PLAN_CR001_v1.0.md` — thiết kế physical/deployment, 20 V2X / 25 V2-T còn cần kiểm thử thực tế.
4. `PROJECT_DECISIONS_v7.md` + `REQUIREMENTS_BASELINE_v1.0.md` — baseline lịch sử; nếu xung đột phạm vi, **CR-001 thắng đối với phần được chấp thuận thay đổi**.
5. ERD v1.0 37 bảng, Decision Log v0.19 và P0/P1 handoff: **ARCHIVE ONLY**, không được dùng để sinh migration v2.

## Phải giữ trong MVP

- STUDENT/TEACHER đăng ký, đăng nhập; lớp học/episode tham gia, môn/chủ đề.
- Student private quiz/practice và teacher bank/quiz; version câu trắc nghiệm và duyệt câu AI trước khi giao.
- Gemini tạo câu hỏi từ PDF, DOCX, TXT, pasted text; PDF tutor + AI explanation trong lượt **tự học**.
- Assignment snapshot JSONB bất biến; cấu hình opens/closes/duration/max attempts, score/answer release riêng; online autosave có ACK; timeout chấm những câu đã ACK, bài trắng = 0; nhiều lượt, điểm cao nhất; không có lượt hợp lệ = NULL.
- JavaFX Student/Admin dùng REST của Server, không kết nối PostgreSQL trực tiếp; WSS metadata và TCP LAN demo.

## Được hoãn bởi CR-001

Offline queue/encrypted draft, late-submission review/candidates, device transfer/binding, advanced candidate history, AI explanation **sau bài giảng viên giao** (vẫn chờ CR riêng). Không tạo lại các bảng tính năng hoãn.

## Bảo vệ DB đã chạy

- **LEGACY / NO TOUCH**: PostgreSQL `quizai_test`, `quizai_dev`; Flyway V1–V8 tại `db/migration/`; 37 bảng v1 đã triển khai.
- **CURRENT v2 / NEW**: `quizai_v2_test`, `quizai_v2_dev`; chỉ dùng Flyway locations `classpath:db/migration-v2`, đánh số V1–V5 riêng; 24 bảng nghiệp vụ.
- Không DROP/TRUNCATE, Flyway repair, đổi schema v1 hoặc copy data v1 vào v2 tự động. TEST trước DEV.

## Kiểm tra tối thiểu trước demo

1. Chạy migration v2 TEST và verify **24 business tables + flyway_schema_history**, đúng 5 version/checksum.
2. Chạy transactional acceptance trên TEST, xác minh rollback; sau TEST PASS mới migrate DEV.
3. Backend DEV startup, login/test role authorization, teacher class + student join, create quiz, assign, submit, score/highest attempt.
4. Gemini thật khi có API key riêng trên Server; không tuyên bố AI PASS nếu chỉ chạy fake stub.
5. JavaFX Client/Admin thực sự gọi REST; kiểm thử timeout/answer release/no-answer-leak và bảo vệ dữ liệu.

**Không tự coi static test / compile PASS là bằng chứng PostgreSQL hay demo end-to-end PASS.**
