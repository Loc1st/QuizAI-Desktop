# QuizAI Desktop — Kế hoạch triển khai Schema v2 (24 bảng)

**Tài liệu:** `QUIZAI_SCHEMA_V2_IMPLEMENTATION_PLAN_CR001_v1.0.md`  
**Ngày:** 2026-11-08  
**TASK:** TASK-003 / DB-05  
**Quyết định chủ dự án:** **CR-001 APPROVED; ERD v2.0 / 24 bảng APPROVED** theo xác nhận trực tiếp trong cuộc trò chuyện ngày 2026-11-08.  
**Quyền thực thi hiện tại (TASK-003 / CR-001, 12/11/2026):** chủ dự án đã cho phép triển khai PostgreSQL 18 V2 **cục bộ, TEST trước và DEV sau khi mọi TEST gate PASS**. Chỉ tạo role/database `quizai_v2_test` và `quizai_v2_dev` còn thiếu; không tự sửa đối tượng đã tồn tại, không production. Credentials nhập trong PowerShell local, không chat/Git/log. Bảo vệ `quizai_dev`, `quizai_test`, `quizai_practice`, database `postgres` và migrations v1 V1–V8. Mục 12 là addendum hiện hành; mục 9–10 giữ lịch sử trước khi cấp quyền này.
**Mục tiêu:** một đồ án AI-first, online-first, chạy được JavaFX Client/Admin → Spring Boot → PostgreSQL, Gemini xử lý tài liệu/quiz/tutor; không quảng cáo các chức năng offline/lateness/device transfer chưa làm.

> **Quản trị phiên bản:** Trong bản nguồn `QUIZAI_ERD_SIMPLIFIED_24_TABLES_DRAFT_v2.0.md` và `QUIZAI_CHANGE_REQUEST_CR-001_SIMPLIFIED_MVP.md` vẫn còn dòng trạng thái `DRAFT / PROPOSED / AWAITING`. Đó là trạng thái **tại thời điểm tạo file**, không phải quyết định mới. Quyết định chấp thuận của chủ dự án **đã thay thế** trạng thái chờ duyệt. **Không sửa byte hai file đã phát hành chỉ để thay tiêu đề**; dùng tài liệu này làm approval addendum và thực hiện các hiệu chỉnh vật lý qua change-control nếu làm thay đổi nghiệp vụ.

## 1. Phạm vi chính thức (baseline v2 overlay)

### Giữ trong MVP v2

1. Tự đăng ký STUDENT/TEACHER, teacher có quyền ngay nhưng không có SUPERADMIN; hash mật khẩu, hash session token, phân quyền role + owner ở backend.
2. Lớp giảng viên, mã tham gia HMAC, một teacher/class; học viên nhiều lớp; rời lớp giữ lịch sử và rejoin tạo membership episode mới.
3. Môn và chủ đề global catalog; ngân hàng riêng của teacher, question version, đáp án MCQ; duyệt từng câu AI trước khi giảng viên giao.
4. PDF/DOCX/TXT/văn bản dán → trích xuất private → Gemini tạo MCQ; student private quizzes và practice; PDF tutor + practice explain/chat, lịch sử chat thuộc owner.
5. Dashboard teacher tạo assignment cho toàn lớp/nhóm chọn, cấu hình giờ mở/đóng, duration, max attempts, thời gian công bố điểm/đáp án và answer release mode; immutable snapshot options/answer JSONB.
6. Autosave online server-ACK, timeout tự chốt từ câu đã ACK; nộp nhiều lượt, điểm cao nhất trong attempts terminal hợp lệ; không có attempt = **NULL**, timeout trắng = **0**.
7. Teacher xem bài giao, điểm từng lượt và thống kê tự học **tổng hợp** trong đúng kỳ tham gia lớp; không đọc private documents, chats/quizzes/answers raw.
8. HTTPS/REST, WSS thông báo metadata và Java Socket TCP demo LAN/localhost độc lập với database; JavaFX Client/Admin không truy cập PostgreSQL trực tiếp.

### Hoãn/cắt theo CR-001, không âm thầm tái đưa vào schema

- Offline attempt draft/sync queue, late payload review/teacher decision, late candidate/result substitution, one-active-device attempt binding, teacher-approved device transfer và idempotency receipt phức tạp.
- Không có bảng `ai_generations` job nền, RAG/OCR scan, tự động trộn nhiều mã đề, chia sẻ teacher bank, điểm học phần toàn khóa, SUPERADMIN.
- **AI giải thích *sau bài giảng viên giao* (`AI-ASSIGNED-POST-01`) vẫn ngoài MVP**, cần CR riêng. AI giải thích trong **lượt tự học** và PDF tutor là MVP.
- Client offline hiển thị chưa có ACK; không nhận đáp án mới sau deadline; server tự chốt phần đã nhận, không hứa khôi phục bản chưa ACK.

### Truy vết 34 DEC và Requirement Baseline

**DEC hoãn/thay đổi qua CR-001:** DEC-012, 022, 023, 024, 027, 028, 032, 033, 034. DEC-032 trở thành không áp dụng (không có late rejected); DEC-034 giữ phần timeout online, hoãn queue offline. **25 DEC còn lại giữ yêu cầu kinh doanh.**  
**REQ bị tác động:** REQ-EXAM-03, 04, 06 hoãn; REQ-EXAM-05 một phần; REQ-EXAM-09 giản lược idempotency. **REQ-AI-01…06 vẫn phải giữ nguyên.**  
Trên repository, **không chỉnh** `PROJECT_DECISIONS_v7.md` hoặc `REQUIREMENTS_BASELINE_v1.0.md`; ghi bản baseline v2 dẫn chiếu CR-001 nếu cần để Codex tra cứu. Log v0.19 và ERD v1.0 giữ vai trò lịch sử, không làm nguồn DDL v2.

## 2. Bản kê **đúng 24 bảng**: 4 nhóm tạo bảng V1–V4 và V5 integrity guards

| Đợt mới | Số | Bảng | Phụ thuộc chính |
|---|---:|---|---|
| V2-A / Flyway V1 — Identity/Class/Catalog | 6 | `users`, `auth_sessions`, `classes`, `class_members`, `subjects`, `topics` | users → classes/members, subjects → topics |
| V2-B / Flyway V2 — Documents/Questions/Quiz | 7 | `documents`, `question_banks`, `questions`, `question_versions`, `question_options`, `quizzes`, `quiz_questions` | owner/users, subject/topic, question → version → options |
| V2-C / Flyway V3 — Practice/AI | 5 | `practice_sessions`, `practice_session_questions`, `practice_answers`, `ai_conversations`, `ai_messages` | B/quizzes → practice → practice questions → answers; practice/documents → AI conversations → messages |
| V2-D / Flyway V4 — Assignment/Attempt/Audit | 6 | `assignments`, `assignment_recipients`, `assignment_questions`, `attempts`, `attempt_answers`, `audit_events` | classes/quizzes → assignment → recipient/snapshot → attempts/answers |
| Flyway V5 — Integrity guards | 0 | Không tạo bảng nghiệp vụ; bổ sung functions, CHECK và triggers | Phụ thuộc các bảng đã tạo trong V1–V4 |
| **Tổng** | **24** | **24 tên bảng logic, không được thêm bảng thứ 25** | Flyway history là bảng kỹ thuật ngoài tổng trên |

**Thứ tự tạo FK** tuân theo dependency *thực*, không bắt buộc dịch nguyên thứ tự ở sơ đồ Mermaid. Tất cả bảng nghiệp vụ dùng UUID PK, FK cùng kiểu; thời điểm lưu `TIMESTAMPTZ` do server quyết định. Không xây lại 13 bảng bị hoãn của v1.

**Có 4 nhóm tạo 24 bảng, nhưng có 5 migration Flyway V1–V5.** V5 không tạo bảng thứ 25. Sau triển khai thật phải có đúng 24 bảng nghiệp vụ và 5 bản ghi migration thành công; `flyway_schema_history` là bảng kỹ thuật riêng.

## 3. Kế hoạch database và Flyway — cách ly tuyệt đối với v1

| Môi trường | Database | Quyền |
|---|---|---|
| Schema v1 đã chạy | `quizai_test`, `quizai_dev` | **READ-ONLY/NO TOUCH** trong toàn bộ quy trình v2 |
| Schema v2 dự kiến | `quizai_v2_test`, `quizai_v2_dev` | **CHƯA TẠO, CHƯA CẤP QUYỀN GHI** |

1. **Chuẩn bị offline trước**: đối chiếu source SQL với data dictionary, index/constraint, API guards và test plans ở mục 4; tích hợp source không đồng nghĩa được chạy SQL. Các thiếu sót review ở mục 10 phải được xử lý trước lần migrate đầu.
2. Khi chủ dự án cấp lệnh riêng cho setup v2, mới tạo hai **database mới** cùng role/credentials riêng, dùng tên/URL env độc lập, ví dụ `QUIZAI_V2_TEST_DB_URL`, `QUIZAI_V2_DEV_DB_URL` (+ USERNAME/PASSWORD). Không dùng mặc định các env `QUIZAI_TEST_DB_*` / `QUIZAI_DEV_DB_*` của v1.
3. **Bộ migration v2 mới** tại `quizai-server/src/main/resources/db/migration-v2/` gồm `V1__v2_identity_class_catalog.sql`, `V2__v2_documents_questions_quizzes.sql`, `V3__v2_practice_and_ai.sql`, `V4__v2_assignments_attempts_audit.sql`, `V5__v2_integrity_guards.sql`. V1–V4 tạo 24 bảng; V5 bổ sung integrity guards, không tạo bảng nghiệp vụ mới. Numbering/checksums riêng cho **hai DB v2 mới rỗng**. **Không tái sử dụng hoặc sửa V1–V8 v1 trong `db/migration/`.**
4. Bật Flyway qua **runner/profile v2 tường minh**, chặn startup bình thường tự migrate. Guard bắt buộc: đúng host `127.0.0.1:5432` (nếu máy thật vẫn như hiện tại), database name trong allowlist `{quizai_v2_test,quizai_v2_dev}`, role thích hợp, schema rỗng hoặc Flyway history đúng expected; **fail-closed** nếu phát hiện tên DB cũ, history/chữ ký không khớp hoặc bảng lạ.
5. Flyway v2 phải đặt `locations` tường minh chỉ đến `db/migration-v2`, **không scan thư mục v1**. Kiểm tra settings hiệu lực từ Gradle/Spring Boot trước khi chạy, kể cả test runner.
6. **TEST trước:** inspect read-only → migrate v2 TEST (sau cấp quyền) → verify 24 tables + Flyway history → negative cases và transaction rollback → rerun idempotency/migration validation.
7. **DEV sau:** chỉ nếu TEST PASS → inspect v2 DEV → migrate DEV → verify catalog/checksums → chạy smoke REST/backend trên DEV. Phần fixture/acceptance sửa dữ liệu chỉ chạy TEST trong transaction rollback.
8. V1/v2 chỉ coexist độc lập; không `DROP`, `TRUNCATE`, `Flyway repair`, đổi OWNER hàng loạt, reset Git, hoặc di chuyển dữ liệu tự động. Nếu database cũ có dữ liệu quan trọng, mở kế hoạch chuyển dữ liệu riêng và xác minh từng bảng.

## 4. Rà soát ràng buộc vật lý — **phải khóa trước khi viết migration v2**

Các mục dưới đây là **cách hiện thực kỹ thuật đề xuất** để giữ bất biến của ERD v2 đã duyệt. Chúng **không được thêm tính năng hoặc bảng**. Kỹ sư phải chứng minh các giải pháp phù hợp PostgreSQL 18, không nhầm mọi cross-table check là `CHECK` đơn giản.

| Mã | Đối tượng | Bất biến / biện pháp kiểm tra | Phương án vật lý/transaction đề xuất |
|---|---|---|---|
| V2X-01 | `users`, `auth_sessions` | Email không phân biệt hoa thường; role và token hash | Lưu normalized lowercase email, UNIQUE; token hash UNIQUE, expiry/revoked_at; mật khẩu hash mạnh ở service, rate-limit |
| V2X-02 | `classes` | `teacher_id` đúng TEACHER; join code HMAC không lộ plaintext; mã đang bật duy nhất | FK teacher; role/owner guard service; **partial unique** join_code_hmac WHERE join_enabled; HMAC key ổn định; thử rotate/disable |
| V2X-03 | `class_members`, `assignment_recipients` | Một episode active/cặp; rejoin tạo episode mới, không khôi phục quyền start assignment cũ | Partial unique `(class_id,student_id) WHERE left_at IS NULL`; receipt giữ `membership_id` episode ban đầu; cross-row class/student guard |
| V2X-04 | `subjects/topics`, `question_versions` | Catalog toàn hệ thống; topic phải thuộc đúng subject | UQ code, UQ(subject_id,code); FK chủ đề, composite FK/transaction để bảo toàn topic–subject |
| V2X-05 | `documents` | Metadata/private file only, soft-delete | `storage_key` opaque, không lưu file bytes/text extraction trong PostgreSQL; owner checks + path traversal guard; MIME/size/quotas cấu hình server |
| V2X-06 | `questions`/`question_banks` | Một primary bank nếu teacher, teacher không chia sẻ; student private không gắn teacher bank | FK + owner equality/role guard; archive bank không cascade xóa questions/snapshots |
| V2X-07 | `question_versions`, `question_options` | Đúng 1 option khi dùng, tối thiểu 2 lựa chọn, version đã duyệt bất biến | UNIQUE `(question_id,version_no)`, UNIQUE `(version_id,position)`, partial unique correct/ version; ít nhất 2 + có 1 correct kiểm transaction tại APPROVE; tạo version mới khi sửa |
| V2X-08 | Practice snapshot | Tham chiếu question_version ổn định cả với private student quiz | Khi version được đưa vào quiz/practice phải khóa nội dung hoặc sửa bằng version mới; `practice_session_questions` unique(session,position); ownership cross-FK |
| V2X-09 | `practice_answers` | Option được chọn thuộc version câu của session; chỉ COMPLETED được tính thống kê | Cross-FK/trigger hoặc transaction kiểm selected_option; UQ(session_question_id); `finished_at` chỉ trên COMPLETED; report filter đúng membership episodes |
| V2X-10 | `ai_conversations`/`ai_messages` | Một chat/practice session; PDF tutor chỉ PDF owner; chat riêng tư | XOR practice_session_id/pdf_document_id theo conversation_type, partial unique practice; owner check service + FK; message append-only, no teacher read raw |
| V2X-11 | `assignments`/`assignment_recipients` | Teacher owns class/quiz; recipient gắn episode đúng class/student | FK, composite FK hoặc guarded transaction, UQ(assignment_id,student_id); teacher không giao quiz student/private khác |
| V2X-12 | `assignment_questions` | JSONB snapshot immutable sau publish; đúng key MCQ, ẩn correct key | Validate schema/version/options/unique keys/correct key khi publish; prevent update/delete sau publish; **API whitelist `stem/options`** (không serialize `snapshot_json` cho student) |
| V2X-13 | `attempts` | Không tạo vượt max_attempts; một kết quả terminal per attempt; retry start không trùng | UNIQUE(recipient_id,attempt_no), UNIQUE(recipient_id,start_request_id); khóa hàng recipient khi start; deadline server `min(close,start+duration)` |
| V2X-14 | `attempt_answers` | Upsert ACK có revision, thuộc chính assignment của attempt; không ghi sau deadline | UQ(attempt_id,assignment_question_id), validate option key nằm trong snapshot; khóa attempt+revision trong transaction, trả ACK sau COMMIT |
| V2X-15 | Submit/timeout | Nộp đúng hạn, timeout trắng 0, không có attempt NULL, retries idempotent | Transaction khóa attempt; `submit_request_id` + digest trên attempt; `earned_points>=0`, `possible_points>0`, `earned<=possible`; finalized row immutable; timeout worker/tác vụ on-demand |
| V2X-16 | Scores | MAX chỉ giữa attempts terminal hợp lệ, không giữa candidate; precision điểm | `normalized_score NUMERIC(9,6)` **đề xuất kỹ thuật** hoặc tính bằng earned/possible với rounding thống nhất; CHECK [0,1]; no eligible attempt → NULL; attempt trắng 0 |
| V2X-17 | Publication | Score/answer độc lập; `NULL` là chưa public; release không bị thu hồi sau first eligibility | `score_first_available_at` và `answer_first_available_at` monotonic + audit; guard cập nhật lịch bằng lock và **tính quyền khả dụng ngay tại thời điểm sửa**, không dựa riêng worker hoặc lần GET; tương thích `AFTER_VALID_SUBMISSION`/`AFTER_ASSIGNMENT_CLOSE` |
| V2X-18 | Permission/AI | Không AI tutor trong attempt được giao đang IN_PROGRESS | Chặn backend trước gọi Gemini, kể cả đường chat practice/PDF; student chỉ owner; teacher chỉ aggregate practice gắn membership interval |
| V2X-19 | Audit/lifecycle | Lưu sự kiện quan trọng nhưng không lộ secrets | `audit_events.metadata_sanitized` allowlist field, append-only; no cascade delete attempt/snapshot/chat history; retention cần chốt trước production |
| V2X-20 | Database safety | Tránh vô tình chạy v1 migrations vào v2 và ngược lại | Profiles + exact JDBC URL allowlist, Flyway locations v2 tường minh, versioned checksums, scripts inspect/deploy tách biệt; test negative wrong DB |

### 4.1. Các vấn đề chỉ ở lớp physical design (không đổi 24 bảng)

- **Tên trường v2 có thể khác v1:** `classes.join_code_hmac`, `quizzes.kind`, `assignment_questions.snapshot_json`, `attempts.submit_request_id`, v.v. Không copy nguyên JPA/JDBC row mapper v1 chỉ vì tên bảng trùng.
- `assignment_questions.snapshot_json`: ví dụ `schema_version`, `stem`, `options[{key,text}]`, `correct_option_key`; server tạo DTO riêng không lộ key. Trigger/transaction bảo vệ **sau publish**, còn row draft cần chỉnh được trước publish.
- **Thời điểm quyền công bố** cần tiêu chí rõ: score chỉ available khi có điểm terminal và đã đến `score_release_at`; answer phải đạt `answer_release_at`, `answer_release_mode`, điều kiện bài kết thúc theo từng recipient; khi ai đó đã đủ điều kiện xem thì không được postpone/retract đối với họ, dù chưa nhấn xem.
- **Chống race** (publish, start, submit, timeout, autosave, change release) là yêu cầu bắt buộc, không thể chỉ dùng boolean UI. Khi có quyết định viết DDL, mỗi transaction cần lock order cố định tránh deadlock.
- **AI PDF:** trích text từ nguồn private do owner upload; kiểm quyền file/mime/size, rate-limit Gemini, không lưu API key trong DB/client.
- **V2X-16 số `NUMERIC(9,6)` là một đề xuất vật lý**, không phải thay đổi số bảng hay quy tắc điểm. Khi lập migration cần ghi rõ quy tắc ROUND và kiểm độ nhất quán UI/SQL.

## 5. Kiểm thử bắt buộc trước khi tuyên bố DB v2 PASS

| ID | Kịch bản | Kỳ vọng |
|---|---|---|
| V2-T01 | Chạy v2 runner nhằm vào `quizai_dev` cũ | Bị chặn **trước bất kỳ ghi DB** |
| V2-T02 | `flyway_schema_history` cũ hoặc database v2 không rỗng bất ngờ | Dừng, không repair/drop |
| V2-T03 | Migrate v2 TEST → validate → rerun | 24 business tables + history, checksums khớp, rerun không thay đổi dữ liệu |
| V2-T04 | Email khác hoa thường trùng | DB từ chối hoặc normalize về một unique |
| V2-T05 | Join code trùng khi enabled, mã disabled, rotate | Unique enabled, mã disabled không join được |
| V2-T06 | Tham gia/rời/rejoin lớp | Episode mới; recipient assignment cũ không hồi sinh start quyền |
| V2-T07 | Teacher A thử chỉnh lớp/bank của B | Backend 403/404 theo chính sách, không truy cập dữ liệu |
| V2-T08 | Approve version thiếu/multiple correct hoặc <2 options | Không approve/publish được; approved version bất biến |
| V2-T09 | Quiz đã dùng version, teacher sửa câu | Phiên luyện tập và assignment cũ không đổi |
| V2-T10 | Student đang thi gọi API raw snapshot | Không nhận `correct_option_key` dưới bất kỳ đường API nào |
| V2-T11 | Chèn selected option của câu khác/session khác | Bị từ chối hoặc rollback, không có đáp án sai liên kết |
| V2-T12 | Gemini student/teacher tài liệu riêng & PDF tutor | Chỉ own input; teacher duyệt AI-generated trước giao; không expose API key |
| V2-T13 | Practice chat lần 1 vs lần 2 | Một conversation mỗi practice_session; tách context/lịch sử |
| V2-T14 | Teacher xem thống kê student sau rời lớp | Chỉ completed trong episode liên quan; không xem tài liệu/chat/câu trả lời raw |
| V2-T15 | Start đồng thời, retry cùng start_request_id | Không vượt max_attempts, không trùng attempt |
| V2-T16 | Autosave trễ/reorder/duplicate; mất mạng | Server-ACKed revision có hiệu lực, bản chưa ACK không báo đã lưu |
| V2-T17 | Submit/timeout chạy song song | Chính xác 1 terminal result, không sửa score sau finalized |
| V2-T18 | Timeout không câu được ACK | Điểm hợp lệ 0; recipient chưa có attempt được chấm → NULL |
| V2-T19 | Nộp bài sau deadline | Chặn đáp án mới, không late review; attempt timeout dùng answer đã ACK |
| V2-T20 | 3 attempts điểm 50%, 90%, 70% | Final score 90%, lịch sử giữ đủ 3 attempts |
| V2-T21 | Score/answer release độc lập, mode khác nhau | Chỉ trả kết quả/đáp án đúng thời điểm và điều kiện |
| V2-T22 | Sửa lịch khi bất kỳ recipient đã đủ điều kiện xem | Không thu hồi/postpone quyền đã khả dụng dù chưa GET |
| V2-T23 | AI during assigned attempt IN_PROGRESS | Server chặn mọi Gemini tutor/explain endpoints |
| V2-T24 | DB rollback acceptance fixtures | Dữ liệu test không tồn tại sau rollback |
| V2-T25 | JavaFX Client/Admin + REST + WSS/TCP | Demo thật, không dựa hoàn toàn fixture; TCP chỉ LAN/localhost |

**Trạng thái hiện tại:** chỉ là **test specification**, không có case nào được báo PASS runtime. V1 T01–T30 test cases không tự chứng minh v2 đã PASS.

## 6. Phân tích dữ liệu v1 → v2

- **23 bảng giữ tên** nhưng nhiều cột/vòng đời thay đổi; không coi dữ liệu v1 có thể copy nguyên hàng.
- **1 bảng đổi tên:** `assignment_question_snapshots` → `assignment_questions`, đồng thời `assignment_snapshot_options` được pack có kiểm soát thành JSONB; test version/snapshot answer count.
- **13 bảng bỏ/gộp/hoãn**, gồm `document_extractions`, `ai_generations`, `assignment_snapshot_options`, `assignment_publication_events`, `attempt_device_bindings`, `device_transfer_requests`, `submission_candidates`, `submission_candidate_answers`, `late_submission_reviews`, `submission_scores`, `attempt_result_events`, `assignment_recipient_score_events`, `idempotency_receipts`.
- **Tuyệt đối không tự chuyển dữ liệu** từ v1. Trước khi vận hành v2 DEV, kiểm tra read-only xem v1 có dữ liệu kinh doanh thật hay chưa. Nếu có, lập mapping ETL riêng, có backup + đối soát + xin phép; nếu chỉ schema rỗng vẫn **giữ nguyên** v1 cho tới khi chủ dự án quyết định dọn về sau.

## 7. Triển khai code sau khi được cấp quyền theo giai đoạn

| Gate | Nội dung | Đầu ra cần chứng minh | Quyền hiện tại |
|---|---|---|---|
| **V2-0** | Approval CR-001/ERD v2.0 + freeze track v1 | Approval addendum này, source hashes, 24-table inventory | **ĐÃ DUYỆT THIẾT KẾ**, không chạy code |
| **V2-1** | Review physical design + cấu hình v2 có guard | DDL contract, migration map, env policy, negative test wrong DB | **ĐƯỢC CHUẨN BỊ OFFLINE**, chưa cho phép kết nối DB |
| **V2-2** | Tạo hai DB v2/test roles mới | Inspect identity và empty DB; không đụng v1 | **HOLD** |
| **V2-3** | Tích hợp Flyway V1–V5 v2 và source tests | 24-table inventory, package hashes, no modification v1 | **ĐƯỢC TÍCH HỢP SOURCE**, live readiness vẫn HOLD theo mục 10 |
| **V2-4** | Migration v2 TEST → acceptance → v2 DEV | Actual Flyway history/checksums, catalog 24 bảng, test evidence | **HOLD** |
| **V2-5** | Backend services/API, Gemini/document, JavaFX UI/WSS/TCP demo | Real REST, integrations, user journeys and tests | **HOLD** |
| **V2-6** | Đóng gói Windows DEV, smoke test và báo cáo | Repeatable run/install, known limitations, backup plan | **HOLD** |

**Điểm dừng bắt buộc ở từng gate:** Codex gửi `git status/diff`, file changed, counts, test PASS/FAIL **thực**, bản checksum migration, môi trường đã dùng, chức năng chưa đạt; không được tự tiến gate tiếp hoặc báo app đã xong khi mới có schema. Không đòi người dùng nhập credentials vào chat; cấp qua PowerShell process và store cục bộ hợp lệ.

## 8. Chỉ dẫn lịch sử — **READ-ONLY trước đợt chuẩn bị 12/11/2026**

Phần trích dẫn dưới đây giữ để truy vết. Quyền hiện hành nằm ở đầu tài liệu và mục 12; không dùng prompt lịch sử để chạy DB hay triển khai API/UI.

> **TASK-003 / CR-001 — CHUYỂN THIẾT KẾ SANG SCHEMA V2, READ-ONLY AUDIT**
>
> Chủ dự án đã phê duyệt CR-001 và ERD v2.0 gồm 24 bảng. Đọc `QUIZAI_CHANGE_REQUEST_CR-001_SIMPLIFIED_MVP.md`, `QUIZAI_ERD_SIMPLIFIED_24_TABLES_DRAFT_v2.0.md` và `QUIZAI_SCHEMA_V2_IMPLEMENTATION_PLAN_CR001_v1.0.md` cùng hai nguồn DEC/Baseline v1 lịch sử.
>
> Không triển khai/sửa PostgreSQL, không tạo SQL/migration/code; **không sửa `quizai_dev`, `quizai_test`, V1–V8 của v1**. Đọc Git status; kiểm kê code P3/P4-A v1 đang làm; xác định file/service/DTO nào cần adapt hoặc tạm dừng. Đối chiếu 24 bảng và V2X-01..20, chỉ báo cáo delta và kế hoạch migrate v2 sau này. Không tự động sửa/stash/commit/reset Git; dừng chờ chủ dự án cấp quyền.

## 9. Gate lịch sử trước khi cấp quyền DB (quyền hiện hành: mục 12)

- [x] Chủ dự án **đã phê duyệt CR-001**.
- [x] Chủ dự án **đã phê duyệt ERD v2.0 đúng 24 bảng**.
- [x] Chủ dự án yêu cầu **giữ nguyên database v1 và Flyway V1–V8**.
- [ ] Chấp thuận bản DDL/physical constraints/migration locations v2 sau review.
- [ ] Cấp quyền tạo 2 database v2 và roles.
- [x] Cấp quyền tích hợp source V1–V5 v2, kiểm tra offline và chuẩn bị guard theo yêu cầu TASK-003 / CR-001 ngày 12/11/2026.
- [ ] Cấp quyền chạy migration v2 TEST/DEV (chưa được cấp).
- [ ] Cấp quyền tích hợp API/UI và kiểm thử v2.

**STOP DATABASE:** Đợt này chỉ chuẩn bị source và kiểm tra offline. Không sửa PostgreSQL, không tạo database/role, không migrate và không triển khai API/UI v2. Không coi bộ SQL đề xuất là đã được PostgreSQL thực thi/kiểm chứng.

## 10. Bàn giao chuẩn bị schema V2 — 12/11/2026

### 10.1. Phạm vi tích hợp và lệnh offline

- Nguồn: `D:\QuizAI-V2-Package\QUIZAI_V2_FAST_TRACK_v1.0`. Kiểm SHA-256 toàn gói trước tích hợp; chỉ thêm file chưa có, không ghi đè checksum khác. Năm SQL migration được giữ nguyên byte từ gói để truy vết review, **chưa chứng nhận sẵn sàng migrate**.
- Hai SQL kiểm thử của gói được đặt tại `quizai-server/src/test/resources/db/v2/`; chưa thực thi SQL nào. Catalog check của gói chỉ kiểm đếm 5 migration thành công, **không thay thế** kiểm chính xác version/script/checksum/history của live runner.
- `:quizai-server:v2SchemaTest` là bộ kiểm tra offline riêng; `:quizai-server:prepareV2Schema` mặc định chỉ in inventory/SHA-256. Các source set `v2Schema`/`v2SchemaTest` tách khỏi test API v1 đang tích hợp, không khởi tạo Spring context/JDBC và không chạy trong startup ứng dụng.
- Cấu hình Flyway chuẩn bị trong `V2SchemaPreparation.flywayConfiguration()` có đúng một location `classpath:db/migration-v2`, schema `public`, `createSchemas=false`, `cleanDisabled=true`, `baselineOnMigrate=false`, `outOfOrder=false`, strict migration naming. **Không gắn datasource, không gọi load/migrate/validate/info**. Flyway mặc định của ứng dụng vẫn bị tắt; không thêm profile startup kết nối v2 trong đợt này.
- `-Pv2PreparationAction=targets` chỉ kiểm biến `QUIZAI_V2_TEST_DB_*` và `QUIZAI_V2_DEV_DB_*` rồi in preview không secret. Chỉ chấp nhận URL chính xác `jdbc:postgresql://127.0.0.1:5432/quizai_v2_test` / `quizai_v2_dev` và login tương ứng; từ chối biến thiếu/rỗng/unresolved, fallback v1, query/userinfo/fragment, host/port khác, role chéo và DB cũ. Đây là **kiểm môi trường offline**, không chứng minh identity/owner thực trong PostgreSQL.
- Không cung cấp action truy cập DB trong công cụ chuẩn bị. `deploy`, `inspect`, `migrate`, `repair`, `clean` đều bị từ chối. Không dùng `deployLocalDatabases`, `integrationTest`, profile `dev`/`test` cũ hoặc `Invoke-LocalDatabaseDeployment.ps1` cho V2: các đường này thuộc v1.

Chạy bằng Java 21, từ root repository (không cần credentials cho `sources`):

```powershell
.\gradlew.bat :quizai-server:v2SchemaTest :quizai-server:prepareV2Schema :quizai-server:bootJar --offline --console=plain
# Tùy chọn: chỉ preview khi đã cấp QUIZAI_V2_* trong cùng process, không đưa secret vào CLI/chat:
.\gradlew.bat :quizai-server:prepareV2Schema -Pv2PreparationAction=targets --offline --console=plain
```

### 10.2. Đối chiếu toàn bộ source với ERD/CR-001 và V2X-01…20

Inventory khớp 24 tên bảng, 4 nhóm 6/7/5/6, UUID PK, các cột logic, FK và JSONB snapshot; không tạo lại 13 bảng đã hoãn. Đây là kiểm source, không phải PostgreSQL parser/runtime hoặc chứng minh concurrency.

| V2X | Kết quả review source và phần cần kiểm chứng tiếp |
|---|---|
| 01–04 | Có email normalized/unique, token hash, role STUDENT/TEACHER, teacher/student triggers, enabled join-code unique, active membership unique, topic-subject composite FK. Hash mật khẩu/token, HMAC và kiểm quyền API vẫn thuộc backend. |
| 05–06 | Documents chỉ metadata/storage key; bank owner và source document owner có FK/trigger. Chưa có đầy đủ guard biến đổi owner/context phía parent; private file, giới hạn input và chính sách primary bank cần service/kiểm thử. |
| 07–08 | Approval kiểm >=2 options/1 correct; version/options APPROVED bất biến; quiz-question owner guard. **Version DRAFT đã được dùng trong practice vẫn có thể sửa**, practice question list chưa khóa; không đủ bảo đảm lịch sử practice bất biến. Reviewer mới chỉ cần role TEACHER, chưa kiểm đúng owner của câu hỏi. |
| 09–10 | Practice option thuộc version; conversation XOR, unique/session, PDF owner/type, AI message question-context có guard. Chưa khóa practice/answers sau COMPLETED và chưa append-only ai_messages; quyền owner/student cần service. |
| 11–12 | Assignment owner/class/teacher quiz, recipient episode và snapshot source APPROVED có guard. Snapshot publish bất biến; **JSON validator có nhánh NULL khi thiếu key**; provenance nullable có thể bỏ qua approval source nên publish service phải validate đầy đủ. |
| 13–14 | Unique start request/attempt number, recipient lock, max attempts, deadline derivation; answer lock, assignment/option/revision checks. Bảo vệ SQL UPDATE/DELETE đường khác, giờ server, race start/leave, retry ACK và idempotency còn cần kiểm thử/service. |
| 15–16 | Terminal attempt update bị khóa; score NUMERIC(9,6), ratio CHECK. **Terminal/practice result CHECK còn cho NULL lọt qua**; chưa có finalize worker/MAX service trong bộ SQL; không coi timeout trắng/NULL/MAX là đã chạy PASS. |
| 17–18 | Release guard tính eligibility lúc sửa lịch; AI assistant message guard khi exam chưa hết hạn. Lock/race release và chặn mọi đường gọi Gemini trước request còn thuộc service; DB trigger không thay thế authorization/API projection. |
| 19–20 | Audit append-only có trigger; sanitize metadata cần service. Cấu hình/target guard offline V2 đã tách khỏi v1; identity, catalog/history/checksum và transaction rollback thật vẫn HOLD. |

**Điều kiện bắt buộc trước lần migrate đầu (không đổi nghiệp vụ CR-001):**

1. Siết `quizai_v2_snapshot_valid`: thiếu `schema_version`, `stem`, `options`, `correct_option_key` hoặc option thiếu key/text phải trả **false**, không NULL. Hiện các phép so sánh với NULL và `CHECK(function(...))` có thể chấp nhận JSON thiếu trường. Thêm negative tests từng trường thiếu/null/sai kiểu.
2. Siết `practice_final_shape_ck` và `attempt_result_ck`: trạng thái terminal/COMPLETED phải có count/points/normalized score **IS NOT NULL** trước so sánh. PostgreSQL CHECK coi UNKNOWN là hợp lệ; score NULL phải chỉ tồn tại trước khi chốt. Thêm negative tests terminal NULL/partial NULL và timeout trắng đúng 0.
3. Khóa version/list/answers đã dùng trong practice, context ownership và reviewer ownership; làm rõ guard SQL và trách nhiệm transaction service để teacher/student private lịch sử không bị thay đổi ngoài ý muốn. Kiểm DELETE/reparent và thay owner ở parent, không chỉ đường UPDATE thông thường.
4. Kiểm soát server timestamps, tính điểm từ snapshot/answers đã ACK, retry submit/timeout và lock order; test hai connection thật cho approval/options, publish/snapshot, start/leave, submit/timeout, release eligibility. Bộ acceptance trong gói mới là smoke, chưa bao phủ V2-T01…25.

Cụ thể, trigger trên `attempts` và `attempt_answers` hiện chỉ bắt INSERT/UPDATE: DELETE có thể xóa câu đã chốt, rồi xóa attempt và làm sai lịch sử/số lượt. Guard thời gian còn tin `started_at`/`submitted_at` được truyền vào; timestamp backdated cần bị chặn ở service/DDL phù hợp, không chỉ kiểm công thức deadline. Release guard đọc attempts nhưng finalize chưa giữ chung assignment lock, cần kiểm thử race eligibility trước khi triển khai thật.

Các thiếu sót này được ghi nhận, **không âm thầm sửa bộ SQL gốc** trong đợt tích hợp. Khi được giao xử lý physical guards trước lần áp dụng đầu, sửa source v2 có test và cập nhật manifest; nếu đã có applied history thì dùng migration mới, không sửa checksum đã áp dụng.

### 10.3. Quy trình PostgreSQL thật — TEST trước, DEV sau (CHƯA THỰC HIỆN)

1. Cần lệnh cho phép DB riêng của chủ dự án; xử lý các điều kiện 10.2 và build/test blockers trước. Tạo riêng DB/login `quizai_v2_test`, `quizai_v2_dev` bằng công cụ admin do chủ dự án quản lý; không đổi role/owner/ACL của DB v1. Credentials chỉ trong process/secret store, không chat/Git/command line/log.
2. Cần bổ sung **live runner V2 riêng**; công cụ `prepareV2Schema` hiện không kết nối DB. Runner phải tái dùng cấu hình một location và target validation ở 10.1, resolve cả TEST/DEV trước mọi kết nối, không nhận override v1 hoặc Spring profile/location tùy ý. Không tái sử dụng runner 37 bảng.
3. Sau khi URL/login đã hợp lệ mới inspect read-only **TEST**: server PostgreSQL 18, `inet_server_addr/port` đúng 127.0.0.1:5432, `current_database/current_user/session_user` đúng target, database owner đúng login riêng, role không superuser/createdb/createrole/replication/bypassrls; schema public đúng quyền dự kiến, không dùng SET ROLE/search_path tùy ý. Thất bại bất kỳ điều kiện nào → dừng trước ghi.
4. DB mới phải rỗng các object do người dùng tạo, không chỉ đếm tables: kiểm schemas, tables/partitions, views/materialized views, sequences, functions, triggers, extensions ngoài allowlist. DB không có history nhưng có object lạ → dừng. DB có history chỉ cho phép đúng V1–V5 V2 đã thành công, script/checksum khớp manifest/Flyway validate và catalog đúng 24 bảng; không tự baseline/repair/clean hoặc hoàn tất lịch sử lạ/partial.
5. Freeze bản SQL + manifest hash trước chạy, chặn thay đổi source trong đợt. Chỉ sau identity/empty-state PASS mới migrate **TEST** bằng V1–V5 V2; verify chính xác 24 bảng nghiệp vụ + history 5 SQL migrations, versions/scripts/checksums, PK/FK/index/CHECK/functions/triggers; validate và rerun không drift. Mọi lỗi → dừng, không tự sửa database.
6. Chỉ TEST được chạy `v2_transactional_acceptance.sql` và negative/concurrency suite bổ sung. Runner phải kiểm DB TEST ngay trước fixtures, dùng transaction rollback cả khi lỗi và xác minh count/data trước/sau không đổi. Không đưa fixture vào DEV. Lưu evidence không secret, gồm target identity, migration source/checksums và kết quả thực.
7. Chỉ khi toàn bộ TEST PASS và source không đổi mới inspect **DEV** với cùng identity/empty-or-exact-history guard; migrate DEV, verify catalog/history/checksum và idempotency. DEV lỗi không được quay sang v1 hoặc repair/drop. Đợt này **chưa chạy bước 1–7**, chưa có bằng chứng TEST/DEV runtime PASS.

**Trạng thái bàn giao:** source integration/offline preparation; **LIVE MIGRATION HOLD**. Cấu hình startup backend v2, API/JavaFX/Gemini và PostgreSQL parser/runtime/integration/concurrency là các bước có quyền riêng, không suy ra từ compile/static PASS.


## 11. Hardening và full build — source hiện hành sau khi tiếp tục phiên

Chủ dự án đã cho phép sửa source/migrations V2 **chưa áp dụng** và kiểm thử offline. Mục 10 ghi lại lần review trước hardening; các lỗi source được liệt kê ở đó đã được xử lý bằng CHECK tường minh, validator JSON false-on-NULL, guard lịch sử/owner, thời gian server và hàm start/save/finalize/publish. Chưa kết nối PostgreSQL; không được coi test source/parser là runtime acceptance.

- V1–V4 vẫn tạo 6/7/5/6 bảng; V5 chỉ bổ sung hàm/constraint/trigger. Manifest hiện hành ở `quizai-server/src/v2Schema/resources/db/v2-release.properties` thay thế checksum gói nháp cho V2; V1–V8 cũ giữ nguyên.
- V2 cố ý tuần tự hóa thao tác ghi bằng advisory transaction lock `81722024001`, lấy ở BEFORE STATEMENT và trước row locks trong các hàm nghiệp vụ; dùng READ COMMITTED. Đây là đánh đổi throughput cho MVP, cần đo tải sau này. Autosave retry cùng revision/payload trả ACK cũ; submit retry cùng request/digest trả kết quả cũ. Deadline được lấy lại khi ghi; finalize xử lý việc deadline vượt qua giữa hai lần đọc bằng timeout từ đáp án đã ACK.
- `source_question_version_id=NULL` giữ đường soạn snapshot thủ công theo ERD: giảng viên sở hữu tự rà soát và publish qua `quizai_v2_publish_assignment`. Snapshot từ pipeline AI phải giữ nguồn version đã APPROVED; SQL không thể suy ra nguồn AI bị backend cố tình bỏ provenance. Tham số actor của các hàm phải lấy từ principal đã xác thực tại backend, không tin role/actor do client khai.
- Runner `:quizai-server:v2Database` mặc định preview offline; `-Pv2DatabaseAction=freezeRelease` chỉ đóng băng source. Live inspect/deploy cần lệnh DB riêng và process authorization chính xác, endpoint/role V2, identity PG18, database rỗng hoặc history/catalog có bằng chứng cũ đúng manifest. Runner chạy từ bản sao đúng năm file V2 đã freeze trong thư mục `db/migration-v2`, không quét V1. Build/check không phụ thuộc action live.
- Acceptance rollback, negative SQL và harness hai connection đã chuẩn bị nhưng **chưa chạy trên PostgreSQL**. Harness hiện dùng INSERT fixture trong transaction rollback của từng connection và probe khóa trên cả 24 bảng cùng start/save/finalize; không cần fixture committed hoặc UUID nhập tay. Nó kiểm tra statement/API serialization và nhả khóa sau rollback ở cả hai thứ tự connection; các invariant retry/revision/deadline được kiểm tra riêng trong SQL acceptance. Đây không phải stress test committed races hoặc bằng chứng throughput. Bất kỳ TEST gate nào chưa PASS thì DEV giữ HOLD.

Điều kiện còn lại trước triển khai thật: DB/login V2 và credentials nhập local, rồi migration/validate/catalog/rollback/negative/concurrency/rerun TEST có bằng chứng thật trước DEV. Quyền local DB đã được cấp tại mục 12. Backend/API/UI/Gemini V2 runtime vẫn cần nghiệm thu riêng.

## 12. Triển khai local V2 được cấp phép — chưa có credentials/runtime evidence

- PostgreSQL service `postgresql-x64-18` đang chạy; client 18.6 và `pg_isready` tại `127.0.0.1:5432` xác nhận đang nhận kết nối. Đây chưa phải authenticated identity/schema check. Phiên agent chưa có biến credentials V2/admin hoặc pgpass; chưa tạo role/database, chưa chạy JDBC, SQL acceptance hay migration.
- Chạy `quizai-server/Invoke-V2DatabaseDeployment.ps1 -DeployV2` trong PowerShell local. Mặc định không có switch chỉ preview. Script chọn Java 21, chạy offline tests và `verifyRelease` trước khi hỏi credentials bằng `Read-Host -AsSecureString`; chỉ dùng process environment rồi xóa trong `finally`. Không tự freeze lại để hợp thức hóa source thay đổi.
- Provisioner riêng chỉ kết nối chính xác `jdbc:postgresql://127.0.0.1:5432/template1` bằng admin local; không dùng database `postgres`. Preflight cả hai danh tính, chỉ CREATE thứ còn thiếu, role không quyền quản trị/không membership, database owner trùng tên. Password role mới là SCRAM verifier sinh cục bộ; để tránh khác biệt SASLprep, mật khẩu mới dùng ASCII in được không có khoảng trắng. Mật khẩu đang tồn tại không bị thay đổi.
- Runner kiểm tra cả TEST/DEV endpoint trước kết nối đầu tiên, nhưng chỉ kết nối DEV khi toàn bộ TEST gate đã PASS. Database chưa có history phải rỗng mọi user object; history có sẵn phải đúng năm V2 migration/checksum, catalog và bằng chứng local của release đã freeze, đồng thời **24 bảng nghiệp vụ không có dữ liệu**. Có dữ liệu, owner sai, history lạ/partial hoặc catalog drift thì dừng; không cleanup/repair/baseline/drop.
- Release manifest đóng băng năm SQL V2, acceptance SQL, toàn bộ Java runner/provisioner và launcher PowerShell. Cùng release/checksum dùng cho TEST và DEV; không thay migration V2 trong đợt triển khai này. Bằng chứng catalog/history lưu tại `quizai-server/build/v2-database-deployment/`; output TEST gates phải PASS thực tế trước khi báo triển khai thành công.
- Kế hoạch backend/JavaFX: [QUIZAI_V2_BACKEND_JAVAFX_IMPLEMENTATION_PLAN_v1.0.md](QUIZAI_V2_BACKEND_JAVAFX_IMPLEMENTATION_PLAN_v1.0.md). Đây là bản chuẩn bị có gate runtime, chưa triển khai ứng dụng hoặc nghiệm thu UI/API.

## Phụ lục A — Checksums tài liệu được đối chiếu

| File | SHA-256 hiện tại trong working copy |
|---|---|
| `PROJECT_DECISIONS_v7.md` | `d99d1c7aeae173954beb110420422ba664bc76a37419dd7a070052202ee419a3` |
| `REQUIREMENTS_BASELINE_v1.0.md` | `f3d25763ac3c14042754a223c1ec4b330f0c1efa3d857ba196e90fbd2cc92c2a` |
| `QUIZAI_DB05_ERD_FINAL_FOR_APPROVAL_v1.0.md` | `5ff37a672d9a76ce4092301f53064a850304fed0ace74d8f71fa9db766aa31ab` |
| `QUIZAI_DB_DESIGN_DECISIONS_v0.19.md` | `fd0801fb7b0155e25648df9c02dc2388b7d8f35b07e80e99b205ba86eae884f8` |
| `QUIZAI_CHANGE_REQUEST_CR-001_SIMPLIFIED_MVP.md` | `281b73a6c053fb518574ce55b749dab5caa89f63869d5fa460579acf85485217` |
| `QUIZAI_ERD_SIMPLIFIED_24_TABLES_DRAFT_v2.0.md` | `1096367f1b0f01b7df3091150e20cc01023d92ecb4881b734f6cf7ddd415c49c` |

> SHA-256 chỉ dùng để kiểm soát phiên bản file tài liệu; **không** chứng minh PostgreSQL đã được cập nhật ở v2.
