# QuizAI Desktop — CHANGE REQUEST CR-001: MVP tinh gọn, AI-first

**Ngày đề xuất:** 2026-11-08  
**Người đề xuất:** Chủ dự án (ý tưởng tinh gọn đồ án giữ AI, giảm áp lực offline/network)  
**Trạng thái:** `PROPOSED — AWAITING EXPLICIT ACCEPTANCE OF SCOPE/ERD V2`  
**Đính kèm:** `QUIZAI_ERD_SIMPLIFIED_24_TABLES_DRAFT_v2.0.md`  
**Không thay thế hồ sơ v1.0 khi chưa phê duyệt.**

## 1. Lý do thay đổi

Sản phẩm phục vụ **đồ án đại học** thay vì một hệ thống kiểm tra quy mô vận hành lớn. ERD cũ có 37 bảng vì hỗ trợ các tình huống offline, nộp muộn, thiết bị, candidate/result history và idempotency nâng cao. Chủ dự án muốn giảm còn 20–25 bảng, dành nguồn lực để hoàn thiện Gemini AI, JavaFX, Spring Boot, tài liệu, tự học, quản lý lớp, giao bài và báo cáo kết quả. Không quảng cáo tính năng chưa triển khai.

**Phạm vi đề xuất:** 24 bảng, online-first, AI-first. Ngôn ngữ Java, JavaFX Client/Admin, Spring Boot Server, PostgreSQL, Gemini backend, HTTPS/REST, WSS, Java Socket LAN demo giữ nguyên.

## 2. Những chức năng KHÔNG được cắt

| Mảng | Yêu cầu giữ |
|---|---|
| Tài khoản | Student/teacher tự đăng ký có quyền ngay, session, role + ownership backend |
| Lớp học | Tạo lớp, mã join, membership episodes, rời/rejoin và lịch sử |
| Tài liệu | PDF, DOCX, TXT, paste, extraction private và validate input |
| AI tạo câu hỏi | Student/teacher tạo câu MCQ từ tài liệu qua Gemini, lưu bản nháp; teacher duyệt trước giao |
| Ngân hàng và quiz | Bank theo teacher, môn/chủ đề/độ khó, version và reuse, student quiz private |
| Luyện tập AI | Practice session, lịch sử, điểm, AI giải thích sau làm, PDF tutor có lịch sử chat |
| Kiểm tra | Assignment cả lớp/chọn người, giờ mở/đóng/duration, max attempts, snapshot immutable, tự chấm, điểm cao nhất, công bố score/answers độc lập |
| Dashboard | Giáo viên chỉnh cấu hình theo buổi ôn tập hoặc kiểm tra; xem điểm/lịch sử/aggregate phù hợp quyền |
| Network/demo | HTTPS/REST, WSS thông báo an toàn, TCP Socket localhost/LAN demo riêng |
| Bảo mật tối thiểu | Backend enforces authorization; không trợ giúp AI trong assigned attempt đang làm; ẩn key/đáp án, hash mật khẩu/token |

## 3. Quyết định cũ BỊ TÁC ĐỘNG — chỉ đổi nếu phê duyệt CR

| DEC | Quy tắc cũ | Quy tắc v2 đề xuất | Tác động |
|---|---|---|---|
| DEC-012 | Làm tiếp/lưu nháp offline và đồng bộ | **HOÃN** offline draft/queue; thi online, hiển thị mất kết nối và chỉ tin autosave đã ACK | Loại local encrypted draft/recovery |
| DEC-022 | Một thiết bị active mỗi lượt | **HOÃN** binding chuyên dụng; session login vẫn được bảo vệ | Không có một-device lock |
| DEC-023 | Late client payload → PENDING_REVIEW | **HOÃN**; không nhận câu mới sau hạn | Không tạo late review pipeline |
| DEC-024 | Local offline attempt/answer draft | **HOÃN** theo DEC-012 | Không lưu nháp thi có bảo đảm offline |
| DEC-027 | Teacher duyệt/từ chối late phải có lý do | **HOÃN** do không có late review | Không cần audit quyết định late |
| DEC-028 | Teacher duyệt chuyển thiết bị | **HOÃN** | Không có device_transfer_requests/binding_epoch |
| DEC-032 | Rejected late tiêu hao attempt | **KHÔNG ÁP DỤNG** vì không còn nhánh rejected late; số lượt vẫn tiêu hao lúc start | Không thay đổi max attempts trong luồng online |
| DEC-033 | Reject gói từ thiết bị bị thu hồi trước late review | **HOÃN** do bỏ device binding/late review | Không có thứ tự kiểm tra old-binding/late |
| DEC-034 | Auto-submit khi hết giờ; offline xếp hàng sync | **GIỮ ONLINE**, server auto-finalize những câu đã ACK, kể cả bài trắng 0; **HOÃN** offline queue/ACK phục hồi | Không tự báo đã nộp khi offline; không dùng timestamp client |

**Các DEC được giữ nguyên về nghiệp vụ (25/34):** DEC-001, 002, 003, 004, 005, 006, 007, 008, 009, 010, 011, 013, 014, 015, 016, 017, 018, 019, 020, 021, 025, 026, 029, 030, 031.

**Những quyết định DB kỹ thuật v1.0 phải xem là superseded nếu CR được duyệt:** DB-15…18 (revision/idempotency multi-ingress), DB-19/20 (binding & local encrypted draft), DB-21 audit vẫn có bản gọn, DB-22/DB-22-R (candidate base/late), DB-29 (timeout bài trắng **giữ kết quả 0**, bỏ candidate source), DB-34 (canonical late), REVIEW-LATE-01 và toàn bộ late publication-score-change chỉ còn mang ý nghĩa lịch sử; REVIEW-PUBLICATION không thu hồi vẫn giữ bằng mốc first availability trên assignments. Không dùng decision log v1 cũ làm quyền triển khai các phần đã bị hoãn.

## 4. Requirements Baseline bị ảnh hưởng

| Requirement | Trạng thái đề xuất | Mô tả thay đổi |
|---|---|---|
| `REQ-EXAM-03` | DEFERRED | Một active device và teacher-approved transfer |
| `REQ-EXAM-04` | DEFERRED | Làm tiếp offline và local draft phục hồi |
| `REQ-EXAM-05` | PARTIAL | Auto-submit online server-ACKed answers giữ; queue offline hoãn |
| `REQ-EXAM-06` | DEFERRED | Late PENDING_REVIEW/teacher approve-reject |
| `REQ-EXAM-09` | SIMPLIFIED, NOT REMOVED | Basic start/submit idempotency dùng unique start request + submit id/digest trên `attempts` thay bảng receipts |
| `REQ-AI-01…06` | RETAIN | PDF/DOCX/TXT/paste generation, teacher review, PDF tutor, private practice AI, no AI during assigned attempt |
| `REQ-EXAM-01/02/07/08` | RETAIN | Time/max attempts, snapshot, best eligible score, release policies |
| `REQ-NET-01/02` | RETAIN | HTTPS/WSS và TCP LAN demo, không có độ phức tạp offline state sync |

**Nguồn gốc thay đổi:** Yêu cầu người dùng tập trung vào điểm sáng AI và đồ án, không phải khẳng định baseline v1 đã âm thầm thay đổi. `REQ-CLASS`, `REQ-PROG`, `REQ-AUTH` giữ nguyên trừ chi tiết triển khai đơn giản hơn.

## 5. Những quyết định/giới hạn quan trọng phải còn đúng

- `DEC-003`: chặn API AI khi student có assigned exam IN_PROGRESS; không tin việc chỉ ẩn nút UI.
- `DEC-007/015`: câu teacher APPROVED mới được giao, assignment snapshot immutable, câu teacher sửa không làm đổi đề đã giao.
- `DEC-011`: deadline từ giờ server, bằng `min(closes_at, started_at + duration)`.
- `DEC-013/029`: max attempts và điểm cao nhất của **các attempts đã chấm**, không có attempt hợp lệ là NULL, timeout không ACK câu nào = 0.
- `DEC-014`: score/answer release riêng, sau khi khả dụng thì không được dời/thu hồi.
- `DEC-018`: teacher chỉ có aggregate self-study, không xem dữ liệu private raw.
- `DEC-026`: teacher self-register có quyền ngay và không có SUPERADMIN.
- `DEC-030/031`: leave không xóa lịch sử, started attempt vẫn finish được.
- `AI-ASSIGNED-POST-01` và các quyết định AI-LATE-PENDING/HISTORY trước đây: **chưa đưa AI hậu bài giao vào MVP**; muốn bật thêm cần một CR xác nhận mới; mất late-review thì nhánh pending không còn ý nghĩa trong MVP v2.

## 6. Rủi ro và cách giảm thiểu

| Rủi ro | Hướng giảm thiểu |
|---|---|
| Mất mạng khi thi làm mất câu chưa ACK | Autosave online mỗi thay đổi, icon ACK/error, không hứa offline; server snapshot đã ACK khi timeout |
| JSONB snapshot thiếu relational checks | Validate JSON schema khi publish trong transaction; integration tests, không lộ correct key |
| Đề có answer release sớm và nhiều attempts | Teacher dashboard cảnh báo; chế độ nghiêm túc dùng AFTER_ASSIGNMENT_CLOSE |
| Không giữ job trạng thái Gemini | Giới hạn size/timeouts, trả lỗi rõ, server logs sanitized; UI cho retry chủ động |
| Không có chi tiết sự kiện grade/release như v1 | Audit tối thiểu + immutable attempt results, cấm sửa score after submit |
| Database v1 đã có V1–V8 | Xây v2 ở **database mới**, không `DROP`/`TRUNCATE` DB cũ, không `Flyway repair` và không sửa migration v1 |
| Đồ án yêu cầu WebSocket/TCP | Giữ riêng WSS metadata và TCP demo chạy thực tế, không sử dụng database tables không cần thiết |

## 7. Phạm vi kỹ thuật và lịch trình sau phê duyệt

1. **STOP CODE OLD SCHEMA:** Codex chưa được tiếp tục code Identity/Class/Catalog theo ERD 37 bảng nếu CR chưa quyết; các thay đổi cũ phải giữ nguyên Git/backup, không reset bừa.
2. **CR APPROVAL:** chủ dự án duyệt CR-001 và ERD v2 đúng 24 bảng, chấp nhận các tính năng hoãn và điểm đánh đổi.
3. **V2 SETUP:** chuẩn bị PostgreSQL `quizai_v2_test` và `quizai_v2_dev` tách khỏi `quizai_test`/`quizai_dev` cũ, chọn vị trí Flyway migration v2 rõ ràng; xin quyền riêng trước DB writes.
4. **V2 MIGRATIONS:** mới tạo schema 24 bảng và test ràng buộc; không đụng 8 migrations đã chạy của v1.
5. **V2 BACKEND/UI:** implement luồng demo AI/tài liệu/quizzes/assignments và REST/JavaFX; PostgreSQL integration test; WSS/TCP demo riêng.
6. **ACCEPTANCE/DEPLOY:** xem đầy đủ luồng teacher + student + Gemini + release score; đóng gói DEV và báo cáo những gì chưa làm. Production chỉ khi có quyền riêng.

## 8. Phiếu phê duyệt

**Phương án đề xuất:** `CR-001 / 24 TABLES / ONLINE-FIRST / AI-FIRST`.

- [ ] Chấp nhận hoãn các DEC bị tác động ở Mục 3 và cập nhật Requirements Baseline thành v2.0.
- [ ] Chấp nhận 24 bảng và các trade-offs JSONB và scoring trong ERD v2.0.
- [ ] Giữ 34 DEC v7 + Baseline v1 chỉ để truy vết lịch sử, không âm thầm sửa nội dung gốc.
- [ ] Giữ nguyên `quizai_dev`, `quizai_test`, migrations V1–V8 đã áp dụng, không phá dữ liệu.
- [ ] Phê duyệt **thiết kế v2** (khác với cấp quyền triển khai database v2).

**Xác nhận đề nghị người dùng trả lời khi đã đọc:**

> Tôi phê duyệt **CR-001** và **ERD tinh gọn v2.0 gồm 24 bảng** cho QuizAI Desktop. Hoãn offline, late-review và device-transfer; giữ AI, quiz tự học, dashboard giảng viên, giao bài, chấm điểm và lịch sử. Không được xóa hoặc thay đổi database v1. Chưa cấp quyền migration v2 cho đến khi có lệnh riêng.

**Trạng thái hiện tại:** PROPOSED, **không được coi là đã APPROVED trước khi người dùng xác nhận rõ ràng**.
