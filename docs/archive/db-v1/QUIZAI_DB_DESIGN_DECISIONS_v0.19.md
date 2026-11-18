# QUIZAI_DB_DESIGN_DECISIONS v0.18 — CURRENT (2026-11-07)

> FINAL-01…FINAL-12 đã được người dùng xác nhận A; phần lịch sử v0.17 giữ nguyên bên dưới, mục 19 có ưu tiên nếu có trạng thái OPEN cũ. Không cho phép implementation khi chưa duyệt ERD.

# QuizAI Desktop — DB Design Decision Log v0.17 (CURRENT CONSOLIDATED)

**TASK-003 / DB-05 — 2026-11-07.** Kế thừa toàn văn v0.16. Quyết định mới nhất: **REVIEW-AI-HISTORY-VERSION=A — CONFIRMED**. Các câu hỏi triển khai còn lại được gom vào **FINAL DESIGN REVIEW**, chưa được mặc nhiên chốt. **Không cấp phép tạo database, SQL/DDL, migration, JPA Entity hoặc Repository.**

**Thứ tự ưu tiên:** `../../PROJECT_DECISIONS_v7.md` → `../../REQUIREMENTS_BASELINE_v1.0.md` → các quyết định thiết kế đã CONFIRMED trong log này → ERD draft. Chức năng AI sau bài do giảng viên giao (`AI-ASSIGNED-POST-01`) vẫn cần Change Control nếu đưa vào MVP.

# QuizAI Desktop — DB Design Decision Log v0.16 (CURRENT CONSOLIDATED)

**TASK:** TASK-003 / DB-05 ERD review. **Mới v0.16:** `REVIEW-AI-HISTORY-PENDING=A` (CONFIRMED) — khi `CLIENT_LATE PENDING_REVIEW`, chủ sở hữu được đọc **các tin nhắn AI đã lưu** của attempt ở chế độ chỉ đọc (nếu không đang thi chính thức); tuyệt đối không gửi tin nhắn, yêu cầu giải thích, regenerate hoặc gọi Gemini mới. Không trả lời lại từ dữ liệu hiện thời hay tự cập nhật đáp án. **Kế thừa v0.15:** `AI-LATE-PENDING=A` (CONFIRMED) — chặn AI giải thích theo attempt khi có `CLIENT_LATE PENDING_REVIEW`, kể cả khi có bản `SERVER_TIMEOUT` hợp lệ; mở lại sau quyết định duyệt/từ chối **và** đáp án được công bố. `AI-ASSIGNED-POST-01=A` vẫn ở cổng Change Control trước khi đưa chức năng hậu thi vào MVP.  **Ngày:** 2026-11-07.  **Trạng thái:** DB-01…DB-34, DB-22-R, DB-TIME-01, AI-UX-01, REVIEW-LATE-01=A và REVIEW-PUBLICATION-01=C và REVIEW-PUBLICATION-CHANGE=A, REVIEW-CLASS-01=A và REVIEW-PUBLICATION-SCORE-CHANGE=A được xác nhận ở mức quyết định thiết kế, **ngoại trừ** các DESIGN OPEN riêng được đánh dấu bên dưới. **KHÔNG PHÊ DUYỆT SQL/DDL, Flyway/Liquibase migration, JPA Entity, repository, cấu hình DB hoặc sửa PostgreSQL.**

## 0. Quy tắc ưu tiên và lịch sử

1. `../../PROJECT_DECISIONS_v7.md` — DEC-001…DEC-034, nghiệp vụ đã khóa.
2. `../../REQUIREMENTS_BASELINE_v1.0.md` — baseline MVP hiện hành.
3. **File này** — quyết định thiết kế đã được người dùng chấp nhận; **mục 16 của v0.16 là quyết định mới nhất về quyền đọc lại chat khi late pending; mục 15 của v0.15 tiếp tục có hiệu lực về KHÓA AI MỚI; mục 14 giữ mô hình AI sau bài giao**; không được suy diễn thành mở rộng tính năng. Nội dung **mục 13 là kết luận hiện hành về việc không thu hồi ĐIỂM đã công bố**; mục 12 quy định quyền giao bài khi tái tham gia lớp; mục 11 quy định lịch công bố ĐÁP ÁN, mục 10 quy định hai chế độ công bố.
4. `QUIZAI_DB_DESIGN_DECISIONS_v0.12.md` và các bản v0.1–v0.11 — bản lịch sử chi tiết, **không dùng** câu chữ trạng thái OPEN cũ nếu đã được giải quyết ở đây.
5. `QUIZAI_DB05_ERD_DRAFT_v0.11.md` — **bản dự thảo ERD**, mọi mục không ghi CONFIRMED vẫn đang chờ review.

Ghi chú: đây là bản **hợp nhất trạng thái hiện hành**, không xóa/chỉnh sửa hai file quyết định sản phẩm gốc. Các phiên bản v0.1…v0.6 được giữ để truy vết, không phải nguồn trạng thái mới hơn.

## 1. Decision register — 34 lựa chọn DB, AI-UX-01 và quy tắc thời gian

| ID | Trạng thái | Nội dung đã xác nhận | Hệ quả thiết kế chính |
|---|---|---|---|
| DB-01 | CONFIRMED | Sinh viên tham gia nhiều lớp từ nhiều giảng viên | `class_members` N:M và lịch sử rời lớp |
| DB-02 | CONFIRMED | Mỗi lớp một giảng viên sở hữu, giảng viên có nhiều lớp | `classes.teacher_id`; owner checks |
| DB-03 | CONFIRMED | Join lớp ngay khi mã hợp lệ | Không tạo workflow duyệt join; kiểm soát mã và rate limit |
| DB-04 | CONFIRMED | Mã lớp không hết hạn mặc định, cho phép đổi/vô hiệu | Join code active và lịch sử membership độc lập |
| DB-05 | CONFIRMED | Giao cả lớp hoặc chọn sinh viên thuộc lớp | `assignment_recipients` freeze tại thời điểm giao |
| DB-06 | CONFIRMED | File gốc trong private storage ngoài PostgreSQL | Metadata và quyền trong `documents` |
| DB-07 | CONFIRMED (MVP implementation) | Trắc nghiệm một đáp án đúng | `question_options` / snapshot; đúng một lựa chọn đúng trước khi phát hành |
| DB-08 | CONFIRMED | Giảng viên có thể tạo nhiều ngân hàng riêng | `question_banks` theo owner, không chia sẻ giữa teacher |
| DB-09 | CONFIRMED | `subjects` / `topics` là danh mục chung | Global catalog ≠ bank shared; quyền chỉnh sửa đã chốt tại DB-30=A (vận hành seed, teacher chỉ chọn) |
| DB-10 | CONFIRMED | AI tạo DRAFT, teacher sửa và APPROVED | `question_versions`/`approved_by`/`approved_at` |
| DB-11 | CONFIRMED | Sửa câu đã duyệt tạo phiên bản mới | Snapshot đề đã giao bất biến |
| DB-12 | CONFIRMED | Text trích xuất ở private storage, DB lưu khóa tham chiếu | `document_extractions` |
| DB-13 | CONFIRMED | Lưu lịch sử chat AI trong PostgreSQL | `ai_conversations`, `ai_messages` owner-only |
| DB-14 | CONFIRMED | Ẩn/xóa tài liệu không xóa lịch sử câu hỏi/đề | Soft deletion + cleanup riêng; không cascade lịch sử |
| DB-15 | CONFIRMED | Autosave khi trả lời và theo định kỳ, có ACK | Working `attempt_answers`; ACK autosave ≠ ACK nộp |
| DB-16 | CONFIRMED | Mỗi đáp án một hàng | Unique `(attempt_id, question_snapshot_id)` |
| DB-17 | CONFIRMED | Revision toàn attempt và request ID | Commit tuần tự, chống duplicate/out-of-order; payload nhất quán |
| DB-18 | CONFIRMED | Auth session + attempt token + binding version | Token hash, thu hồi binding trước mọi write |
| DB-19 | CONFIRMED | Sinh viên đăng nhập máy mới gửi request; teacher duyệt | Không reset deadline, không recover dữ liệu chưa sync từ máy cũ |
| DB-20 | CONFIRMED | File draft mã hóa tại client theo từng attempt | Ghi nguyên tử; không biến thành full offline app |
| DB-21 | CONFIRMED | `audit_events` riêng cho hành động nhạy cảm | Append-only, sanitized; nộp/trùng/transfer/review/old binding |
| DB-22 | CONFIRMED (B) | Hết hạn server tự tạo SERVER_TIMEOUT từ checkpoint đã ACK trước hạn | Candidate bất biến, job có thể chạy trễ phải ghi actual run time |
| DB-23 | CONFIRMED (A) | Mỗi câu có trọng số bằng nhau | Earned = số đúng, possible = số câu theo snapshot |
| DB-24 | CONFIRMED (A) | Lưu điểm đạt được và điểm tối đa | Thang 10/% chỉ là cách quy đổi khi hiển thị |
| DB-25 | CONFIRMED (A) | Điểm và đáp án công bố theo thời điểm riêng | Server enforce score/answer release riêng; NULL semantics được chốt tại DB-33=A |
| DB-26 | CONFIRMED (A) | Thống kê tổng hợp tự học theo số phiên, câu trả lời, tỷ lệ đúng, kết quả | Không lộ quiz/chat/tài liệu riêng cho teacher |
| DB-27 | CONFIRMED (A) | Toàn lịch sử + bộ lọc 7/30 ngày | Cần thống nhất timezone/đầu mốc truy vấn |
| DB-28 | CONFIRMED (A) | Teacher chỉ xem thống kê tự học đến thời điểm student rời lớp | Áp dụng từng membership interval `[joined_at,left_at)` |
| DB-29 | **CONFIRMED (A)** | Attempt đã start hợp lệ, hết giờ và **không có câu trả lời server ACK**: server tự nộp **bài trắng hợp lệ, điểm 0, tiêu hao 1 lượt** | Có SERVER_TIMEOUT bất biến với 0 answers; earned=0, possible=số câu đề >0; `effective_candidate_id` trỏ timeout khi chưa có late được duyệt |
| DB-30 | CONFIRMED (A) | Danh mục môn/chủ đề global được quản lý bằng seed/cập nhật vận hành; TEACHER chỉ đọc/chọn | Không có chức năng chỉnh sửa catalog toàn hệ thống cho TEACHER; không tạo SUPERADMIN |
| DB-31 | CONFIRMED (A) | Tái tham gia lớp tạo membership interval mới, không kích hoạt lại row cũ | Giữ history; unique chỉ một membership ACTIVE `(class_id, student_id)`; quyền start căn cứ đúng interval được giao |
| DB-32 | CONFIRMED (A) | Mỗi câu hỏi teacher thuộc một ngân hàng chính; phiên bản được tái sử dụng ở nhiều quiz | `questions.bank_id` nullable cho câu hỏi cá nhân student; teacher question thuộc đúng bank của owner; `quiz_questions` N:M |
| DB-33 | CONFIRMED (A) | `score_release_at` và `answer_release_at` nullable và độc lập; NULL = chưa công bố tới khi teacher thiết lập | Backend kiểm tra hai quyền release độc lập; cập nhật lịch phải audit; không để lộ đáp án trong attempt đang thi |
| DB-34 | CONFIRMED (A) | Tối đa một `CLIENT_LATE` canonical mỗi attempt; retry không tạo bản khác | Unique on attempt/kind; cùng payload trả lại canonical; payload khác sau khi đã có late canonical → conflict |
| AI-UX-01 | CONFIRMED (A) | Mỗi `practice_session` có tối đa một AI conversation; các câu trong cùng lượt dùng chung mạch chat, lượt mới tạo chat riêng | `ai_conversations.practice_session_id` optional FK + unique cho loại PRACTICE_SESSION; `ai_messages` có tham chiếu câu đang hỏi đã xác thực trong lượt luyện tập; hỗ trợ cả câu bỏ trống |
| DB-TIME-01 | CONFIRMED | `deadline_at=min(started_at+duration, closes_at)`; 5 phút vào sớm/đóng muộn là ví dụ, không bắt buộc | Server time; chuyển máy không pause/reset |
| DB-22-R | CONFIRMED (A) | Late candidate được duyệt thay nguồn điểm; bị từ chối thì giữ SERVER_TIMEOUT hợp lệ, không dùng bản bị từ chối | Một attempt/một lượt; hai candidate immutable; result event khi đổi nguồn |
| REVIEW-LATE-01 | **CONFIRMED (A)** | Khi đã có `CLIENT_ON_TIME` do sinh viên chủ động nộp và server commit/ACK thành công, không nhận **bản nộp mới có payload khác** để thay kết quả, dù gửi sau hạn | Retry cùng request/digest chỉ trả ACK cũ; payload khác bị `ALREADY_FINALIZED` + audit, không tạo `CLIENT_LATE`; late hợp lệ sau `SERVER_TIMEOUT` vẫn `PENDING_REVIEW` |
| REVIEW-PUBLICATION-01 | **CONFIRMED (C)** | Giảng viên chọn chính sách xem đáp án theo từng assignment: `AFTER_VALID_SUBMISSION` hoặc `AFTER_ASSIGNMENT_CLOSE`; thời gian công bố đáp án vẫn độc lập và nullable; bài PRACTICE xem lời giải + AI sau khi hoàn thành | `assignments.answer_release_mode` (logic), `answer_release_at` gate; server chặn answer/AI trong attempt thi đang làm; cảnh báo rò đáp án với `max_attempts>1` |

### 1.1. Điều DB-29 **không** có nghĩa

- Chỉ có **attempt đã bắt đầu thành công** mới được auto-submit; không tạo attempt hay score 0 cho sinh viên chưa bắt đầu.
- **0 điểm hợp lệ** ≠ **không có điểm**. Khi không có attempt eligible, điểm tổng kết là NULL/“chưa có điểm”, không auto gán 0.
- Candidate timeout trắng vẫn là **bản nộp đúng hạn** theo chính sách DB-22=B. Nếu bản `CLIENT_LATE` hợp lệ đến sau đó, vẫn xét duyệt như DEC-023/027.
- Nếu late bị REJECTED, bản trắng hợp lệ còn hiệu lực với score 0 theo DB-22-R=A. Nếu late được ACCEPTED, candidate late thay nguồn điểm hiệu lực, giữ lịch sử score 0 cũ. Không tính 2 lượt.
- Nếu attempt đã được `CLIENT_ON_TIME` submit đúng hạn, job timeout không được tạo thêm bản SERVER_TIMEOUT canonical; nếu worker chạy lại, phải idempotent.

## 2. State machine logic — tách ba trục trạng thái

**Đây là state model đề xuất để review, không ép tất cả nhãn thành PostgreSQL ENUM.**

### 2.1. Trục Attempt (chỉ server là nguồn sự thật)

- `IN_PROGRESS`: attempt được tạo sau kiểm tra assignment, class membership lúc start, max_attempts và binding.
- `ON_TIME_FINALIZED`: đã có một **canonical on-time** `CLIENT_ON_TIME` hoặc `SERVER_TIMEOUT`; attempt không còn được autosave tiếp sau deadline hoặc submit on-time lần khác.
- `LATE_ONLY_PENDING_REVIEW`: trạng thái ngoại lệ nếu chưa materialize được on-time candidate, nhưng đã nhận một late candidate hợp lệ; job recover phải cố tạo timeout từ checkpoint lịch sử. Không được dùng trạng thái này để tránh tạo bài trắng DB-29.
- `FINISHED_WITH_EFFECTIVE_RESULT` (projection): có candidate eligible và chấm được; nguồn điểm được chọn có thể được thay đổi **chỉ** theo review có quyền/audit (DB-22-R=A). Không khuyến nghị dùng `REJECTED_LATE` làm một trạng thái terminal duy nhất trên attempt.

### 2.2. Trục Submission Candidate (bất biến)

- `CLIENT_ON_TIME`: bản final đến server trong hạn (trusted server time), duy nhất một bản on-time canonical.
- `SERVER_TIMEOUT`: server tạo từ autosave đã ACK trước hạn **hoặc rỗng 0 câu** tại deadline; duy nhất một bản on-time canonical.
- `CLIENT_LATE`: bản final client gửi sau deadline từ binding còn hiệu lực; nếu nhận thì `PENDING_REVIEW` → `ACCEPTED_LATE` hoặc `REJECTED_LATE` với lý do không rỗng.
- `submission_candidate_answers` là snapshot bất biến, không gắn sửa live autosave sau khi finalize. Điểm/nguồn điểm cũng có history bất biến.

### 2.3. Trục Client cục bộ

- `EDITING_ONLINE`: người học đang làm bài; autosave có ACK theo revision.
- `EDITING_OFFLINE`: vẫn được làm trong thời gian theo client countdown đã đồng bộ từ server; ghi file mã hóa.
- `PENDING_SYNC`: hết hạn offline, đã khóa nội dung và xếp hàng final; **chưa có submission ACK**.
- `SERVER_CONFIRMED`: đã nhận ACK trả trạng thái tương ứng từ server (`SUBMITTED_ON_TIME` hoặc `PENDING_REVIEW` hoặc kết quả review); autosave ACK không đủ để gắn nhãn này.
- `REVOKED_BINDING`: máy cũ bị thu hồi; draft còn local có thể tồn tại nhưng **server không chấp nhận** gửi/cập nhật. Không auto merge sang máy mới.

### 2.4. Chuyển trạng thái hợp lệ (logic)

| Tình huống | Server attempt/candidate | Client / Effective result |
|---|---|---|
| Start hợp lệ trong window | Tạo 1 attempt IN_PROGRESS; cố định deadline & binding | Countdown từ server; không tạo attempt khi reconnect |
| Autosave hợp lệ trước deadline | Transaction cập nhật working answers + revision, ACK sau commit | Chỉ đánh dấu revision đó đã sync |
| Client final đến trước/hạn | Canonical CLIENT_ON_TIME, immutable | Điểm theo candidate nếu eligible; không thêm timeout |
| Deadline, chưa có final on-time; đã ACK N câu | Canonical SERVER_TIMEOUT với N câu; N có thể bằng **0** theo DB-29 | Điểm hiệu lực theo SERVER_TIMEOUT; client offline vẫn PENDING_SYNC |
| Final đến sau deadline từ binding hiện hành | Candidate CLIENT_LATE PENDING_REVIEW, không sửa timeout | Late chưa tính điểm; có on-time thì giữ nguồn điểm cũ |
| Teacher ACCEPT late + reason | Review/audit immutable, effective_candidate đổi late | Cập nhật projection có result event; không đổi candidate gốc |
| Teacher REJECT late + reason | Review/audit immutable, giữ canonical on-time | Score on-time giữ nguyên, kể cả timeout trắng 0 |
| Binding cũ gửi dữ liệu sau transfer | Từ chối **trước** late policy; audit; không tạo candidate | Báo phiên bị thu hồi; deadline không reset |
| Retry cùng request ID + digest | Trả cùng receipt; không tạo 2 candidate | Dùng kết quả ACK cũ |
| Retry cùng ID nhưng digest khác | Conflict; không ghi mới | Không overwrite dữ liệu đã chốt |

## 3. Giao dịch, thời điểm và idempotency — kiểm tra trước DDL

1. **startAttempt:** lock `assignment_recipient` hoặc kỹ thuật tương đương; check opens_at ≤ server_now < closes_at, class membership còn active để **start**; unique `(recipient_id,attempt_no)`; create attempt + active binding atomically. Retry/reconnect không tăng lượt; rời lớp không cản tiếp tục/nộp attempt đã start.
2. **autosave:** authenticate, check attempt token, binding epoch và deadline trong transaction; validate question option cùng snapshot; kiểm tra request ID + digest và revision tuần tự; chỉ ACK sau commit. Khuyến nghị full answer-set snapshot theo revision hoặc semantics delta có thể chứng minh không mất đáp án; lựa chọn payload cuối cùng chưa khóa. Không nhận autosave quá hạn để biến thành bản on-time.
3. **timeout:** lock attempt; nếu có CLIENT_ON_TIME canonical, không tạo SERVER_TIMEOUT. Nếu không, snapshot working answers đã **commit và ACK theo hạn**, kể cả tập rỗng. Lưu `deadline_at` đúng và `job_executed_at` thực tế; scheduler trễ không được giả mạo đã chạy lúc deadline. Đảm bảo recover idempotent nếu service restart; không dùng late payload làm on-time data.
4. **final/late:** kiểm tra **binding trước** xét giờ nhận/late; sử dụng trusted server received time và transaction lock để phân loại. Late candidate có ID riêng khác retry; không sửa candidate immutable hoặc nộp thêm attempt. Policy số late candidate canonical vẫn DESIGN OPEN.
5. **approve/reject:** kiểm tra TEACHER sở hữu assignment, candidate đang PENDING_REVIEW, reason nonblank; write once, append audit; ACCEPT chuyển effective score/event, REJECT fallback đúng on-time. Nếu có điểm đã công bố, ghi event thay đổi và bảo đảm truy xuất lịch sử; không tiết lộ answers trước `answer_release_at`.
6. **transfer:** request từ thiết bị mới của chính student; teacher approval lock attempt/binding; revoke binding_version cũ trước khi bind mới; không gia hạn deadline; draft cũ không thể gửi hoặc hòa giải.
7. **score:** DB-23/24 `earned_points=correct_count`, `possible_points=total_snapshot_questions (>0)`; normalized chỉ phục vụ thống kê/UI. Điểm tổng kết theo DEC-029 là `MAX` một eligible effective score của từng attempt; không eligible thì NULL, có candidate hợp lệ trắng thì 0.

### 3.1. Các tình huống cạnh tranh cần chặn

- Final on-time đến đồng thời với timeout job: tối đa **1 on-time canonical** theo trusted ingress time và lock; không tạo 2 điểm cùng hiệu lực.
- Late gửi trước khi timeout worker chạy: recover SERVER_TIMEOUT từ checkpoint đã ACK/được commit trước deadline, **kể cả rỗng**, rồi xử lý late độc lập.
- Transfer và sync đồng thời: serialize binding authorization với ghi candidate; máy cũ không được thắng race sau revoke.
- ACK network bị mất: server đã commit thì retry cùng key/payload phải trả receipt cũ, không tái chấm hay thay nguồn điểm.
- Update/revision sai thứ tự: không ghi đè snapshot mới bằng bản cũ; `request_id` giúp dedup chứ không tự giải quyết conflict revision.
- Offline draft bị thay đổi hoặc đồng hồ client sai: không xem timestamps client là chứng cứ nhận bài đúng hạn; late vẫn review.

## 4. Ma trận điểm — DB-22-R=A + DB-29=A

| Trường hợp | On-time canonical | Late candidate | Effective result | Attempt count |
|---|---|---|---|---|
| Chưa start attempt | Không có | Không có | **Chưa có điểm** | 0 |
| Start rồi hết giờ, không ACK câu nào | SERVER_TIMEOUT trắng | Không có | **0 hợp lệ** | 1 |
| Start, có câu on-time | CLIENT_ON_TIME hoặc SERVER_TIMEOUT | Không có | Điểm từ on-time | 1 |
| Có timeout trắng và late PENDING_REVIEW | SERVER_TIMEOUT 0 | PENDING_REVIEW | **0 đang hiệu lực**, late chưa tính | 1 |
| Timeout trắng, late ACCEPTED_LATE | SERVER_TIMEOUT 0 | ACCEPTED_LATE | Điểm từ late, có audit chuyển nguồn | 1 |
| Timeout trắng, late REJECTED_LATE | SERVER_TIMEOUT 0 | REJECTED_LATE | **0** từ timeout, late không tính | 1 |
| Timeout N câu, late ACCEPTED_LATE | SERVER_TIMEOUT N câu | ACCEPTED_LATE | Điểm late; on-time giữ nguyên trong lịch sử | 1 |
| Timeout N câu, late REJECTED_LATE | SERVER_TIMEOUT N câu | REJECTED_LATE | Điểm on-time | 1 |
| Không có on-time hợp lệ do lỗi dữ liệu/kịch bản ngoại lệ | Không có | REJECTED_LATE | Chưa có điểm hợp lệ; phải audit nguyên nhân lỗi | 1 nếu attempt thực sự đã start |
| Binding cũ bị revoke gửi late | On-time (nếu có) | Không tạo mới | Không thay đổi | 1 |

**Giới hạn:** `DB-29` yêu cầu job/recovery luôn tạo timeout trắng cho attempt đã start khi hết hạn nếu không có on-time final; trường hợp không có on-time candidate thuộc diện lỗi/recovery, không phải luồng thông thường được phép bỏ qua điểm 0. Không đồng nhất điểm 0 với số câu đúng = 0 của một attempt chưa bao giờ được tạo.

## 5. Đối chiếu lại nghiệp vụ đã khóa

| Nguồn | Tác động DB-29 + DB-22-R |
|---|---|
| DEC-013 | Đếm attempt tạo hợp lệ; blank/timeout/late rejected đều tiêu hao 1 lượt; retry không tạo lượt mới |
| DEC-029 | `MAX` của **eligible effective attempt**; 0 từ blank là hợp lệ; không eligible -> NULL |
| DEC-032 | REJECTED_LATE không đóng góp điểm, attempt vẫn tiêu hao. Khi đã có on-time hợp lệ, REJECT bản late không vô hiệu hóa on-time theo DB-22-R=A |
| DEC-034 | Hết giờ tự nộp **những gì đã trả lời**, kể cả 0 câu; offline queue và không báo nộp thành công trước ACK |
| DEC-023/027 | Late từ phiên còn hiệu lực -> PENDING_REVIEW và quyết định có reason; không tự chấp nhận |
| DEC-033 | Epoch/binding cũ bị từ chối trước late-check và trước khi tạo candidate |
| DEC-014 / DB-25 | Điểm & đáp án công bố riêng, server kiểm soát; thay điểm sau late review phải có lịch sử |
| DEC-015 | Câu hỏi/đáp án nguồn chấm là snapshot đóng băng của assignment |
| REQ-EXAM-09 | Retry cùng attempt_id/key/digest idempotent; không sửa candidate đã chốt; teacher review là **một thao tác nghiệp vụ khác** với audit |

## 6. DESIGN OPEN — cập nhật theo DB-30…DB-34 và AI-UX-01

**Đã giải quyết:** DB-30, DB-31, DB-32, DB-33, DB-34, AI-UX-01 đều **CONFIRMED (A)**. Các mô tả cũ trong v0.7 đặt DB-30…34 ở trạng thái OPEN không còn hiệu lực.

| Mã | Điểm thiết kế còn cần review (KHÔNG phải chức năng đã chốt) | Hướng xem xét / ảnh hưởng |
|---|---|---|
| REVIEW-CLASS-01 | Assignment recipient cũ khi sinh viên rời lớp rồi vào lại | Đề xuất recipient gắn `membership_id` (episode) và không tự phục hồi quyền bắt đầu bài cũ; cần xác nhận nếu muốn hành vi khác |
| REVIEW-LATE-01 | **ĐÃ CHỐT A** — bản mới sau `CLIENT_ON_TIME` đã commit/ACK bị từ chối | Chỉ retry dữ liệu cũ được trả receipt; không mở candidate late để sửa bài. Xem mục 9 |
| REVIEW-CATALOG | Quản trị seed catalog: mã ổn định, ngừng sử dụng, rename | TEACHER chỉ chọn; thao tác vận hành không tự tạo thêm role/quyền admin |
| REVIEW-PUBLICATION-CHANGE | **Còn mở** — đổi/dời lịch công bố sau khi đã có người xem | Audit và không giả định có thể thu hồi thông tin đã thấy; chờ quyết định chính sách |
| REVIEW-ACHIEVEMENT | **Còn mở** — bài giao “không tính thành tích” có tách khỏi điểm tổng kết hay không | Không tạo cột/flag `is_graded`, `counts_to_grade`, `assessment_type` khi chưa quyết định hoặc có CR phù hợp |
| REVIEW-AI | Hạn mức hội thoại, retention và cách dựng context khi chuyển từ câu A sang câu B; câu chưa chọn đáp án có thể không có `practice_answers` | Chat phải theo đúng `practice_session_id`, liên kết bằng câu thuộc lượt làm kể cả bỏ trống; không dùng câu trả lời thi chính thức |
| REVIEW-OPS | TTL attempt token; mã hóa/xóa draft client; retention chat/file/audit; backup | Cần xác định trước code bảo mật và cleanup |
| REVIEW-TX | Job timeout chạy muộn, race submit, unique canonical late, transaction idempotency | Bắt buộc giải thích/kiểm thử; chưa có SQL |
| REVIEW-PRIV | Mốc thời gian thống kê 7/30 ngày và nhiều membership episode | Lọc aggregate theo các interval hợp lệ, không trả raw chat/docs/quizzes |

### 6.1. Phân biệt thiết kế đã xác nhận với đề xuất kỹ thuật

- `DB-31=A`: chắc chắn phải giữ các lần tham gia như những **khoảng riêng**. Việc liên kết `assignment_recipients` với đúng membership episode là giải pháp đề xuất để không vô tình mở bài cũ; chi tiết FK chờ review ERD.
- `DB-33=A`: `NULL` nghĩa là **chưa công bố** đối với từng loại dữ liệu. Điểm và đáp án có thể công bố riêng nhưng server không được cung cấp đáp án chuẩn cho lượt đang làm.
- `DB-34=A`: chỉ **một bản late canonical**. Cần tách trường hợp retry payload giống nhau khỏi việc cố gửi payload khác sau khi đã chốt.
- `AI-UX-01=A`: cuộc hội thoại theo lượt luyện tập khác với chat tự do và chat theo PDF; không áp dụng tự động sang `ASSIGNED_EXAM`.

## 7. Checklist trước khi cho triển khai migration

- [x] Người dùng xác nhận DB-30..DB-34 và AI-UX-01 (đều phương án A).
- [ ] Review `QUIZAI_DB05_ERD_DRAFT_v0.5.md`; chưa phê duyệt PK/FK cuối cùng hoặc ràng buộc giao dịch chống race.
- [ ] Thiết kế hash/token/owner permissions không lộ dữ liệu giữa users, teachers hoặc accounts.
- [ ] Kiểm thử mô hình DB-29 empty timeout, hai candidate, transfer, retry/revision, deadline/reconnect, kết quả 0 vs NULL.
- [x] Chốt NULL/release behavior (DB-33=A; REVIEW-PUBLICATION-01=C); [ ] review quy tắc sửa lịch sau khi từng công bố và lịch sử đổi nguồn điểm.
- [ ] Đưa quyết định thiết kế vào `../..` trước khi yêu cầu Codex sinh code.
- [ ] **Chưa tạo SQL, migration, JPA Entity, database hoặc sửa PostgreSQL ở TASK-003 hiện tại.**

---

## 8. Biên bản xác nhận v0.8 — 2026-11-07

**Người dùng đã duyệt:** AI-UX-01=A; DB-30=A; DB-31=A; DB-32=A; DB-33=A; DB-34=A. Các điều này không sửa các DEC nghiệp vụ và không tự thêm API/màn hình nghiệp vụ ngoài MVP.

**Đối chiếu nhanh:** DEC-006/020 (bank riêng) ↔ DB-32; DEC-016/030/031 (membership lịch sử) ↔ DB-31; DEC-014 (công bố kết quả) ↔ DB-33; DEC-023/027/032/034 và REQ-EXAM-09 (late/auto-submit/idempotency) ↔ DB-34; DEC-002/003/008/018 (gia sư AI trong tự học, không có trong lúc thi, quyền riêng tư) ↔ AI-UX-01; DEC-019/026 (catalog chung nhưng TEACHER không phải SUPERADMIN) ↔ DB-30.

**Khóa phạm vi:** Tài liệu này chỉ là thiết kế. Không có SQL, migration, JPA Entity, repository hay kết nối PostgreSQL được tạo.

---

## 9. Biên bản xác nhận v0.9 — REVIEW-LATE-01=A (2026-11-07)

**Người dùng xác nhận:** "Đã chủ động nộp và server ACK thành công thì không được gửi bản late khác để thay đổi kết quả". Trạng thái **CONFIRMED (A)**. Chỉ giải quyết quy tắc kết thúc một lượt thi đã **chủ động nộp thành công**; không làm mất cơ chế nhận bài offline muộn đối với `SERVER_TIMEOUT` theo DB-22, DB-22-R, DEC-023 và DEC-027.

### 9.1. Đối chiếu requirement FROZEN — kết luận có điều kiện diễn giải

| Nguồn | Điều khoản gốc | Cách bảo toàn |
|---|---|---|
| `DEC-023` / `REQ-EXAM-06` | Bản bài làm offline *hợp lệ* được đồng bộ sau hạn phải vào `PENDING_REVIEW`, không tự được chấp nhận | Bản đến muộn từ binding hiện hành sau `SERVER_TIMEOUT` **vẫn** tạo `CLIENT_LATE/PENDING_REVIEW`; bản **mới** gửi sau khi `CLIENT_ON_TIME` đã được server chốt **không còn là một lần nộp offline đang chờ hợp lệ**, mà là cố gắng thay bản final nên từ chối không tạo candidate |
| `DEC-029` / `REQ-EXAM-07` | Điểm tổng kết là điểm cao nhất của các attempt hợp lệ; không hợp lệ -> NULL | `CLIENT_ON_TIME` giữ điểm nguồn hiệu lực; retry hoặc payload mới không làm đổi score; late được duyệt sau `SERVER_TIMEOUT` vẫn chỉ thay nguồn điểm **của cùng attempt** |
| `DEC-034` / `REQ-EXAM-05` | Đến deadline tự nộp phần đã trả lời; offline chờ sync, không báo thành công trước ACK server | Nếu đã có `CLIENT_ON_TIME` thì timeout job **không tạo** `SERVER_TIMEOUT`; nếu chưa nộp đúng hạn thì timeout materialize checkpoint ACK, còn offline sync hợp lệ vẫn nhận để review |
| `REQ-EXAM-09` / `DEC-013` | Submit idempotent, reconnect không tạo attempt mới hoặc ghi đè bài đã chốt | Request cùng key+digest trả receipt cũ; key cũ+digest khác -> conflict; request mới+same payload không tạo candidate mới; request mới+payload khác sau final -> `ALREADY_FINALIZED` và audit |
| `DEC-033` | Binding đã revoke phải bị chặn trước late review | Kiểm tra binding/session hợp lệ trước khi xét có mở late candidate hay nhận retry; một request từ thiết bị cũ không được ghi thêm dữ liệu |

**Lưu ý phạm vi:** DEC-023 không ghi ngoại lệ này thành lời. Quy tắc REVIEW-LATE-01=A diễn giải việc **gửi bài offline muộn** là gửi một bản final **chưa từng được chủ động nộp thành công**. Nếu sau này muốn chấp nhận mọi payload đến muộn kể cả khi đã có `CLIENT_ON_TIME`, điều đó là đổi policy REVIEW-LATE-01 và phải review/Change Request phù hợp. Không được lén đánh đồng mọi request sau deadline là `PENDING_REVIEW`.

### 9.2. Decision table — chính sách đang có hiệu lực

| Trạng thái server trước request | Request tới server | Kết quả bắt buộc |
|---|---|---|
| Chưa có on-time final, đang trong hạn | Lệnh submit đúng hạn từ binding hợp lệ | Commit một `CLIENT_ON_TIME` bất biến, chấm và ACK; khóa autosave/final bổ sung |
| Có `CLIENT_ON_TIME` đã commit | Retry đúng `request_id` + digest | Trả receipt/ACK cũ; không tạo thêm candidate, không chấm lại |
| Có `CLIENT_ON_TIME` đã commit | Request mới cùng canonical payload | Trả reference bản đã chốt hoặc báo `ALREADY_FINALIZED` nhất quán theo API contract; **không ghi mới** |
| Có `CLIENT_ON_TIME` đã commit | Request mới với payload khác, trước/sau deadline | `ALREADY_FINALIZED` / conflict, audit; **không có `CLIENT_LATE/PENDING_REVIEW` mới** |
| Có `CLIENT_ON_TIME` đã commit | Timeout worker chạy hoặc retry | Không tạo `SERVER_TIMEOUT` |
| Có `SERVER_TIMEOUT` hợp lệ | Binding hợp lệ gửi bản offline final sau deadline, chưa có canonical late | Tạo duy nhất một `CLIENT_LATE`, `PENDING_REVIEW`; không thay kết quả hiện có cho tới khi teacher ACCEPT |
| Có `SERVER_TIMEOUT` + `CLIENT_LATE` | Retry cùng payload/key | Trả receipt cũ; không có candidate thứ hai |
| Có `SERVER_TIMEOUT` + late `ACCEPTED_LATE` | Teacher đã duyệt | Dùng late làm effective result, lưu immutable result event và audit |
| Có `SERVER_TIMEOUT` + late `REJECTED_LATE` | Teacher đã từ chối | Giữ kết quả timeout; cả attempt vẫn chỉ tiêu hao một lượt |
| Binding cũ đã revoke | Bất kỳ autosave/submit/late từ binding cũ | Từ chối do binding **trước** khi xét late/review/idempotent mutable action; ghi audit tối thiểu |

**Tình huống ACK thất lạc:** Nếu server đã commit `CLIENT_ON_TIME` nhưng ACK mạng bị thất lạc, client không thể suy ra đã nộp thành công chỉ vì đã gửi request. Khi reconnect, client hỏi trạng thái chuẩn và retry *cùng request ID và digest* để nhận receipt; nếu payload khác thì bị từ chối vì server đã final, không được xem đây là bản late chưa nộp.

**Tính chính xác transaction:** `finalize CLIENT_ON_TIME`, `timeout SERVER_TIMEOUT`, `late admission` và `result event` phải có cơ chế khóa/unique theo attempt. Classification đúng hạn dựa trusted ingress time; worker chạy chậm không thể gán thời gian nhận giả. Phải ngăn race giữa final, timeout, transfer và late; không cần thay đổi bất kỳ bản submission đã chốt nào.

### 9.3. REVIEW-PUBLICATION-01 — ĐÃ CHỐT C (tham chiếu mục 10)

Phương án A/B của vòng hỏi trước đã được thay bằng **C: giảng viên chọn mode công bố đáp án theo từng assignment**. Trạng thái hiện hành và điều kiện kiểm tra ở mục 10; không diễn giải mục 9.3 lịch sử thành quyết định mới. `score_release_at` và `answer_release_at` vẫn độc lập; NULL nghĩa chưa công bố.

### 9.4. Trạng thái review và giới hạn triển khai

- `REVIEW-LATE-01=A`: **CONFIRMED**; cập nhật ERD DRAFT v0.4.
- `REVIEW-PUBLICATION-01=C`: **CONFIRMED**; xem mục 10. `REVIEW-PUBLICATION-CHANGE` và `REVIEW-ACHIEVEMENT` còn mở.
- `REVIEW-CLASS-01`, `REVIEW-OPS`, `REVIEW-AI`, `REVIEW-TX`: vẫn DESIGN OPEN theo mức độ chi tiết đã ghi tại mục 6.
- **Không tạo SQL, CREATE TABLE, migration, JPA Entity, repository, kết nối hoặc sửa PostgreSQL; không sửa DEC/REQ gốc.**


---

## 10. Biên bản xác nhận v0.10 — REVIEW-PUBLICATION-01=C (2026-11-07)

**Lời xác nhận của người dùng:** “Giảng viên chọn chính sách công bố đáp án cho từng bài giao: sau khi sinh viên nộp hợp lệ hoặc sau khi toàn bài đóng; điểm và đáp án có lịch riêng. Bài tự học cá nhân xem đáp án và AI giải thích sau khi hoàn thành. Cảnh báo việc tiết lộ đáp án nếu bài cho nhiều lượt.” Người dùng yêu cầu **giữ bài không tính thành tích là DESIGN OPEN**, không tự đổi công thức tính điểm.

**Trạng thái:** `REVIEW-PUBLICATION-01 = C — CONFIRMED`. Đây là chi tiết thiết kế của DEC-014/REQ-EXAM-08, không sửa hay mở rộng phần hỗ trợ AI trong thi. Các quy tắc cũ DB-25=A, DB-33=A, AI-UX-01=A, DB-22/DB-22-R/DB-29, REVIEW-LATE-01=A vẫn giữ nguyên.

### 10.1. Hai chính sách xem đáp án do TEACHER lựa chọn theo assignment

| Chế độ logic | Điều kiện bắt buộc để API trả đáp án chuẩn của bài giao | Mục đích |
|---|---|---|
| `AFTER_VALID_SUBMISSION` | Đúng student/assignment, đã hoàn thành/nộp attempt hợp lệ (hoặc có `SERVER_TIMEOUT` đúng hạn được server chốt), `answer_release_at` khác NULL và `now >= answer_release_at`, không có attempt hiện đang làm của chính người xem cho assignment này | Ôn tập nhẹ, có thể xem ngay khi đã nộp và đã đến lịch |
| `AFTER_ASSIGNMENT_CLOSE` | Các điều kiện owner/hoàn tất/release như trên, **và** `now >= assignments.closes_at` | Giữ kín đáp án đến khi cửa sổ làm bài đóng |
| `PRACTICE_SESSION` (không phải mode của assignment) | Chủ sở hữu `practice_session` và lượt tự học đã hoàn thành | Xem đáp án, kết quả và AI giải thích theo AI-UX-01 ngay, không phụ thuộc lịch bài giao |

**Cơ chế kết hợp thời gian và mode:** `answer_release_at = NULL` → **chưa công bố**, bất kể mode. Nếu UI cho chọn “công bố ngay sau khi nộp”, UI/backend cần đặt `answer_release_at` thành mốc công bố hợp lệ khi giáo viên xác nhận thay vì lặng lẽ coi NULL là ngay lập tức. Chọn `AFTER_ASSIGNMENT_CLOSE` với `answer_release_at < closes_at` thì thời điểm hiệu lực sớm nhất là `closes_at`. Đồng hồ tin cậy là server.

**Điểm số:** `score_release_at` là điều kiện riêng, nullable độc lập với `answer_release_at` và mode đáp án; chỉ cấp điểm hợp lệ của attempt đã được server chốt/xét duyệt theo chính sách cũ, không tiết lộ qua các API khác. Bản late đang `PENDING_REVIEW` không tự làm candidate late thành hợp lệ; nếu có `SERVER_TIMEOUT` hợp lệ, điểm của snapshot đó vẫn là nguồn hiệu lực cho đến lúc duyệt theo DB-22-R.

**Chống lộ nội dung khi đang làm bài:** DEC-003/REQ-AI-06 vẫn chặn gia sư AI và trả đáp án chuẩn gắn với lượt thi được giao đang hoạt động. Khi một student có attempt khác cùng assignment đang `IN_PROGRESS`, không trả đáp án qua endpoint xem lại lịch sử assignment trong thời gian đó. Việc người dùng từng xem đáp án trước khi khởi động lượt kế tiếp thì không thể “xóa kiến thức đã thấy”; TEACHER phải thấy cảnh báo khi chọn mode công bố sớm và `max_attempts > 1` hoặc còn người chưa thi.

**Lượt nộp muộn:** `CLIENT_LATE/PENDING_REVIEW` chưa đủ điều kiện tự mở đáp án nếu không có `SERVER_TIMEOUT` đúng hạn hợp lệ; review muộn chỉ được thay nguồn điểm khi teacher ACCEPT có lý do, tuân thủ DEC-023/027/DB-22-R. Không thêm AI cho `ASSIGNED_EXAM` sau nộp trong bản thiết kế này; AI giải thích được khẳng định cho **tự học cá nhân**.

### 10.2. Đối chiếu với nghiệp vụ đã khóa

| Nguồn | Đối chiếu | Kết luận |
|---|---|---|
| DEC-003, REQ-AI-06 | AI chỉ dành cho tự học và chức năng PDF hợp lệ; từ chối yêu cầu AI gắn attempt chính thức đang diễn ra; ẩn đáp án/AI trong exam runtime | **Compatible** |
| DEC-014, REQ-EXAM-08 | Teacher quyết định mốc/điều kiện công bố, API server thực thi thay vì chỉ ẩn trên JavaFX; NULL giữ kín | **Compatible** |
| DEC-029, REQ-EXAM-07 | Điểm tổng kết là MAX các attempt eligible (0 hợp lệ khác NULL không có điểm); lựa chọn mode xem đáp án không đổi chấm điểm | **Compatible** |
| DEC-002, REQ-AI-05, AI-UX-01 | Bài tự học đã hoàn thành được xem đáp án và chat AI giải thích; mỗi practice session có một chat riêng | **Compatible** |
| DEC-015 | Correct options lấy từ snapshot đã giao, không lộ qua DTO trong khi thi | **Compatible** |
| DB-25, DB-33 | `score_release_at` và `answer_release_at` độc lập, mỗi trường có thể NULL = chưa công bố | **Compatible** |

### 10.3. Ánh xạ ERD DB-05 v0.5 — đề xuất schema CHƯA được duyệt

- `assignments.answer_release_mode`: enum/giá trị kiểm soát (`AFTER_VALID_SUBMISSION` / `AFTER_ASSIGNMENT_CLOSE`) do giảng viên chọn **theo từng bài**, không coi là phân loại `graded/ungraded`. Default cho assignment mới là **DESIGN OPEN**; phương án an toàn đề xuất `AFTER_ASSIGNMENT_CLOSE` nhưng **chưa tự khóa**.
- `assignments.answer_release_at TIMESTAMPTZ NULL`: NULL = chưa công bố. `assignments.score_release_at TIMESTAMPTZ NULL`: riêng và độc lập.
- Nên audit thay đổi mode/mốc giờ: actor, thời gian, giá trị cũ/mới và assignment; thực hiện owner check `classes.teacher_id == current teacher`.
- Không cần bảng mới cho mode ở mức logic; các bảng `attempts`, `submission_candidates`, `late_submission_reviews`, `attempt_result_events` vẫn dùng để xác định tính hợp lệ. API projection trả `can_view_answers`, `can_view_score` và lý do khóa thay vì làm lộ đáp án chuẩn.
- UI giao bài phải **cảnh báo rõ rủi ro rò đáp án** khi `AFTER_VALID_SUBMISSION`, nhất là `max_attempts>1` hoặc còn sinh viên chưa thi.

### 10.4. Kiểm thử bắt buộc (định nghĩa, chưa chạy)

1. `answer_release_at=NULL` dù đã nộp/chưa đóng → không lộ đáp án.
2. `AFTER_VALID_SUBMISSION`: A nộp hợp lệ 09:15, release 09:20, closes 09:35 → A xem từ 09:20; B đang thi không xem được.
3. `AFTER_ASSIGNMENT_CLOSE`: cùng ví dụ → A chỉ xem từ 09:35; B phải hoàn thành lượt trước khi được xem.
4. `score_release_at <= now`, `answer_release_at=NULL` → chỉ điểm; ngược lại có thể chỉ đáp án.
5. Student có attempt 2 đang IN_PROGRESS → không thể lấy đáp án assignment qua API xem lại attempt 1, bất kể đã đạt mốc release; không có AI trong exam.
6. `CLIENT_LATE` pending khi không có on-time effective → không mở đáp án dựa vào late chưa duyệt.
7. Late ACCEPT sau score release → ghi result event/audit, projection điểm mới theo lịch score; không sửa snapshot/result event cũ.
8. Teacher khác, student khác, user đã mất quyền theo ownership → không xem được dữ liệu.
9. PRACTICE_SESSION finished → chủ sở hữu xem đáp án và gọi AI; PRACTICE_SESSION unfinished → chưa kích hoạt tính năng xem lại theo AI-UX-01.
10. Dời lịch công bố sau khi đã có người xem → phải audit, không được ngụ ý hệ thống có thể thu hồi thông tin đã lộ; policy còn DESIGN OPEN.

### 10.5. DESIGN OPEN còn lại — KHÔNG chốt thay người dùng

- `REVIEW-PUBLICATION-CHANGE`: Teacher đã công bố, có sinh viên xem rồi, sau đó đặt mốc công bố thành tương lai hoặc tắt công bố: có cho phép không, và cách thể hiện lịch sử? Có thể tiếp tục đề xuất **chặn thu hồi** một khi đáp án/điểm đã hiển thị, nhưng chưa CONFIRMED.
- `REVIEW-ACHIEVEMENT`: Người dùng nhắc “bài đơn giản không tính thành tích”. **Chưa định nghĩa** liệu đó là assignment có/không đóng góp điểm tổng kết. Không tạo `is_graded`, `counts_to_grade` hay đổi DEC-029 cho tới khi có thiết kế/Change Request hợp lệ.
- `REVIEW-CLASS-01`, `REVIEW-AI`, `REVIEW-OPS`, `REVIEW-TX`: giữ DESIGN OPEN theo mục 6; `answer_release_mode` default và quy tắc chuyển mode sau đã công bố cũng cần review.

**Cấm triển khai ở bước này:** SQL/DDL, migration Flyway/Liquibase, JPA Entity/Repository, tạo hoặc sửa PostgreSQL; không sửa DEC-001…DEC-034, Requirements Baseline v1.0.


---

## 11. Quyết định mới — REVIEW-PUBLICATION-CHANGE=A (CONFIRMED, 2026-11-07)

**Xác nhận của người dùng:** chọn **A** trong câu hỏi: “Khi đáp án đã thực sự được công bố, không cho thu hồi/ẩn lại hoặc dời lịch sang tương lai; chỉ cho sửa chính sách/lịch trước khi công bố.”

**Phạm vi chính xác:** Chỉ áp dụng đối với **đáp án đúng của bài giảng viên giao** và `answer_release_mode`, `answer_release_at` của từng assignment. **Không suy diễn** rằng mọi thao tác thay lịch **điểm số** (`score_release_at`) cũng bị khóa vĩnh viễn — lịch điểm độc lập và vẫn cần policy review riêng. Bài tự học cá nhân không áp dụng lịch này; xem lời giải/AI theo practice_session đã hoàn thành (AI-UX-01). Không sửa DEC-001…DEC-034 hoặc Requirements Baseline v1.0.

### 11.1. Quy tắc bất biến sau công bố

1. Trước khi **bất kỳ sinh viên hợp lệ nào có thể truy cập** đáp án theo `REVIEW-PUBLICATION-01=C` (đã đáp ứng mốc công bố + điều kiện sau nộp / sau đóng bài), giảng viên sở hữu assignment có thể điều chỉnh `answer_release_mode`, `answer_release_at` theo quyền và audit.
2. Kể từ khi đáp án **đã trở nên khả dụng** cho ít nhất một sinh viên hợp lệ, **không cho** thao tác xóa mốc (`NULL`), đẩy mốc tới tương lai, hoặc chuyển mode nhằm thu hẹp/lùi quyền truy cập đã mở. Không căn cứ chỉ vào việc sinh viên đã bấm nút xem: không thể chứng minh chưa ai thấy thông tin đã công khai qua API.
3. Các cuộc gọi xem đáp án sau đó vẫn kiểm tra `owner/recipient`, quyền xem/hoàn tất lượt, mode, server time, không có attempt cùng assignment đang làm. Quyết định này không đồng nghĩa mở đáp án cho tất cả sinh viên hoặc bỏ kiểm tra quyền.
4. Mọi yêu cầu chỉnh lịch/mode, kể cả từ chối, cần được kiểm tra **atomically** với trạng thái đã công bố và ghi audit actor/time/assignment/old/new/outcome/reason code. Chặn race giữa giảng viên sửa lịch với lượt thi hoàn thành hoặc điểm bắt đầu công bố.
5. Không được thay `answer_release_at` nhằm điều khiển `score_release_at`; 2 lịch công bố vẫn độc lập theo DB-25, DB-33. Không thêm chế độ “không tính thành tích” (`REVIEW-ACHIEVEMENT` vẫn DESIGN OPEN).

### 11.2. Cụ thể hóa thời điểm bắt đầu khả dụng — định hướng kỹ thuật DRAFT

Đối với `AFTER_VALID_SUBMISSION`, đáp án trở nên khả dụng **khi vừa có ít nhất một người nhận bài có lượt hoàn thành hợp lệ, không có attempt đang làm, đồng thời `answer_release_at` đã đến**. Đối với `AFTER_ASSIGNMENT_CLOSE`, thêm điều kiện `now >= closes_at`. Nếu chưa có ai đủ điều kiện thì có thể chưa phát sinh công bố thực tế dù giờ đặt lịch đã qua; chi tiết giao dịch/đánh dấu thời điểm cần review trong ERD.

Đề xuất theo dõi `answer_first_available_at` hoặc sự kiện audit tương đương, **không chỉ dựa vào `first_viewed_at`**. Khi xác thực thao tác chỉnh lịch, server phải kiểm tra cả lịch đã công bố và điều kiện khả dụng hiện tại trong transaction; marker không đủ nếu worker ghi chậm. Tên cột, cách ghi marker, kiểm soát concurrency và nguyên tắc sửa theo hướng mở sớm hơn còn thuộc `REVIEW-TX / DESIGN OPEN`, chưa quyết định DDL.

### 11.3. Ma trận đối chiếu yêu cầu

| Nguồn | Kết quả |
|---|---|
| `DEC-014`, `REQ-EXAM-08` | Giảng viên điều khiển công bố, server kiểm soát quyền; sau khi thông tin đã khả dụng không giả vờ có thể thu hồi. Tương thích. |
| `DEC-003`, `REQ-AI-06` | Trong lúc có attempt chính thức đang thi, backend vẫn chặn AI gắn attempt và đáp án đúng; lịch đã mở cho người khác không bỏ chặn người đang thi. Tương thích. |
| `DEC-029`, `REQ-EXAM-07` | Lịch/mode đáp án không làm thay nguồn điểm, không đổi MAX eligible attempts, không biến “chưa có điểm” thành 0. Tương thích. |
| `DB-25`, `DB-33`, `REVIEW-PUBLICATION-01=C` | Hai lịch riêng, NULL = chưa công bố; chỉ khóa *thu hồi đáp án* sau khi đã mở, không mặc nhiên cấm chỉnh lịch điểm. Tương thích. |

### 11.4. Bộ kiểm thử yêu cầu cho DB-05 (chưa chạy)

| ID | Tình huống | Kỳ vọng |
|---|---|---|
| PUB-CHG-01 | Chưa có sinh viên nào đủ điều kiện thấy đáp án, TEACHER hợp lệ dời lịch | Cho phép + audit, nếu chưa có công bố thực tế |
| PUB-CHG-02 | Chế độ `AFTER_VALID_SUBMISSION`, A đã nộp hợp lệ, đã tới lịch; chưa ai gọi API xem | Vẫn coi đáp án đã khả dụng; từ chối ẩn/dời |
| PUB-CHG-03 | Đáp án đã mở, đổi sang `AFTER_ASSIGNMENT_CLOSE` để đóng lại trước `closes_at` | Từ chối, audit |
| PUB-CHG-04 | `answer_release_at=NULL` ngay sau khi đáp án đã khả dụng | Từ chối, audit |
| PUB-CHG-05 | `answer_release_at` chưa tới mốc, teacher đổi trước khi ai truy cập được | Cho phép, audit, nếu thỏa điều kiện chủ sở hữu |
| PUB-CHG-06 | Teacher khác không sở hữu assignment sửa lịch | Forbidden, audit không để lộ dữ liệu |
| PUB-CHG-07 | Sửa lịch đồng thời một sinh viên vừa đủ điều kiện công bố | Lock/transaction bảo đảm không có cửa sổ lộ đáp án rồi bị thu hồi |
| PUB-CHG-08 | Đáp án đã mở, teacher đổi `score_release_at` | **Không suy luận** từ quyết định này; cần review riêng policy lịch điểm |
| PUB-CHG-09 | PRACTICE đã hoàn thành muốn hỏi AI về câu khác cùng lượt | Giữ nguyên một chat riêng cho lượt, không phụ thuộc lịch assignment |

### 11.5. DESIGN OPEN sau khi chốt

- `REVIEW-CLASS-01`: rời lớp → vào lại tạo membership episode mới (DB-31=A); còn phải chốt có tự phục hồi quyền bắt đầu các assignment cũ chưa làm hay chỉ nhận assignment mới.
- `REVIEW-ACHIEVEMENT`: “bài không tính thành tích” chưa được định nghĩa làm thay đổi kết quả/điểm; không tạo `counts_to_grade`.
- `REVIEW-PUBLICATION-SCORE-CHANGE`: chính sách đổi lịch **điểm** đã hiển thị, không tự sao chép quy tắc đáp án.
- `REVIEW-PUBLICATION-TX`: định nghĩa marker khả dụng, concurrency, thay đổi tiến/lùi trước khi công bố, ghi audit.
- `REVIEW-OPS / REVIEW-AI / REVIEW-TX`: bảo mật token, lưu trữ tài liệu/chat/draft, giao dịch final/timeout/transfer/late và PK/FK/index chính thức còn review.

**Trạng thái:** DESIGN REVIEW; chưa cho phép SQL, DDL, migration, JPA Entity, repository, cấu hình hoặc sửa PostgreSQL.


---

## 12. REVIEW-CLASS-01=A — Không khôi phục quyền bắt đầu bài cũ khi tái gia nhập (CONFIRMED, 2026-11-07)

**Xác nhận của người dùng:** “A” — sinh viên đã rời lớp và tham gia lại **không tự động được bắt đầu bài kiểm tra chưa làm thuộc lần tham gia cũ**; chỉ những bài được giao hợp lệ trong lần tham gia mới. Điểm và lịch sử cũ được giữ; attempt đã bắt đầu từ lần trước vẫn được tiếp tục/nộp trong phạm vi deadline, binding và chính sách đã khóa.

**Đối chiếu:** DEC-030 chặn start mới khi rời lớp, giữ kết quả; DEC-031 cho hoàn tất attempt đã bắt đầu; DB-31=A quy định tạo `class_members` episode mới khi tái gia nhập; DB-05=A đóng băng người nhận khi giao; DB-28=A giới hạn thống kê tự học theo từng thời đoạn là thành viên. `REQ-CLASS-02` tương thích. **Không phát sinh Change Request** và không mở tính năng “giao lại bài cũ tự động”.

### 12.1. Invariant về membership episode và quyền làm bài

- `class_members` giữ mỗi lần tham gia thành một row bất biến về `id`, `class_id`, `student_id`, `joined_at`; `left_at` biểu thị kết thúc episode. Mỗi cặp `(class_id, student_id)` có **tối đa một episode ACTIVE**, dù có thể có nhiều episode lịch sử. Không xóa cứng row đã gắn với recipient/attempt.
- `assignment_recipients` dự kiến có `membership_id` (FK tới **episode đã được giao**) cùng `assignment_id`, `student_id`. Cần chứng minh `membership.student_id == recipient.student_id` và `membership.class_id == assignment.class_id` bằng ràng buộc ghép hoặc service transaction được review; FK riêng lẻ không đủ.
- Một `(assignment_id, student_id)` chỉ có một recipient lịch sử. Sau khi tái gia nhập, **không cập nhật `membership_id` của recipient cũ sang episode mới** để lách việc thu hồi quyền start; không tự chèn recipient mới cho assignment đã giao trước đó.
- **START attempt mới:** cần recipient có `membership_id` đúng với episode hiện đang ACTIVE của student, không chỉ kiểm tra “student hiện có mặt trong lớp”; đồng thời phải qua thời gian mở/đóng, `max_attempts` và các điều kiện giao bài.
- **CONTINUE / AUTOSAVE / SUBMIT attempt đã bắt đầu hợp lệ:** quyền gắn với attempt, owner/student, binding hiện hành và deadline; **không** đòi membership episode cũ vẫn ACTIVE. Việc rời/đến lại lớp không reset deadline, không tăng/giảm lượt hoặc thay đổi bản nộp bất biến.
- Bài mới được giao trong episode mới có recipient hợp lệ của episode mới. Không tự tái cấp quyền cho assignment cũ; nếu sản phẩm sau này cho phép giao lại theo nghiệp vụ mới, phải review riêng trước khi viết SQL.
- Giảng viên vẫn được xem **kết quả bài giao lịch sử thuộc lớp mình** theo DEC-030, và **thống kê tự học chỉ trong những khoảng thành viên hợp lệ** theo DEC-018/DB-28; không mặc nhiên mở toàn bộ lịch sử riêng tư của sinh viên.

### 12.2. Ràng buộc/transaction dự kiến — DESIGN DRAFT, chưa phải DDL

| Thành phần | Đề xuất bảo vệ tính đúng đắn |
|---|---|
| `class_members` | FK tới `classes`,`users`; partial unique `(class_id,student_id)` khi ACTIVE; `left_at > joined_at` nếu có; kiểm tra episode không chồng lấp khi cập nhật đồng thời |
| `assignment_recipients` | `assignment_id`, `student_id`, `membership_id` NOT NULL cho giao bài theo lớp; unique `(assignment_id,student_id)`; cùng class/student qua composite FK hoặc transaction kiểm tra |
| `attempts` | FK tới recipient bất biến; đồng bộ start bằng row lock/transaction để chống race với thao tác rời lớp hoặc tái gia nhập |
| `audit_events` | Ghi JOIN/LEAVE và START_DENIED_OLD_EPISODE có actor/time/reason code, không chép đáp án riêng hoặc token |
| Quyền xem | Tách predicate cho start khỏi continue/submit và historical result; không sử dụng `student currently active in class` làm quy tắc chung cho mọi endpoint |

**Định nghĩa interval:** `[joined_at, left_at)`; ACTIVE tương ứng `left_at IS NULL`. Không tự suy rằng nhập mã lớp lần nữa khi còn ACTIVE tạo episode mới.

### 12.3. Kiểm thử bắt buộc trước khi duyệt schema (chưa chạy)

| ID | Bối cảnh | Expected |
|---|---|---|
| CLASS-REJOIN-01 | Student nhận A trong episode 1 nhưng chưa start; leave; join episode 2; A vẫn còn mở | Không được START A vì recipient trỏ episode 1 |
| CLASS-REJOIN-02 | Student start A trong episode 1; leave giữa chừng | Được tiếp tục/submit attempt này theo binding/deadline (DEC-031) |
| CLASS-REJOIN-03 | Sau khi join lại, teacher giao bài B mới cho lớp | Student nhận B theo episode 2 và được start nếu đủ điều kiện |
| CLASS-REJOIN-04 | Double-click join hoặc hai request join đồng thời | Không tạo hai episode ACTIVE; retry idempotent hoặc trả trạng thái hiện tại |
| CLASS-REJOIN-05 | Teacher tra cứu điểm cũ sau student leave/rejoin | Giữ lịch sử attempt/score; không nhân bản hoặc cập nhật điểm cũ |
| CLASS-REJOIN-06 | Teacher xem thống kê tự học với filter 7/30 ngày trải qua khoảng leave | Chỉ tổng hợp event thuộc episode được phép; không tính khoảng đã rời |
| CLASS-REJOIN-07 | Tái gia nhập và cố thay recipient.membership_id của A | Từ chối/không cho phép mutate linkage để hồi sinh quyền |
| CLASS-REJOIN-08 | Student rời lớp đồng thời request start mới | Transaction serializes: start hợp lệ trước leave thì giữ attempt; sau leave thì deny |

### 12.4. DESIGN OPEN sau vòng REVIEW-CLASS-01

- `REVIEW-ACHIEVEMENT`: bài “không tính thành tích” vẫn chưa có định nghĩa kỹ thuật; **không thêm flag graded/ungraded hay đổi DEC-029**.
- `REVIEW-PUBLICATION-SCORE-CHANGE`: khả năng thu hồi/dời lịch **điểm** đã công bố chưa chốt; không lấy rule bất hồi tố của đáp án áp dụng tự động cho điểm.
- `REVIEW-OPS`: thời hạn lưu chat, file/text, local draft, audit, token; `REVIEW-AI`: giới hạn lịch sử/context; `REVIEW-TX`: định dạng FK/index và serialization cuối cùng; `REVIEW-PUBLICATION-TX`: race của marker đáp án.
- Schema/ERD **chưa phê duyệt**; không viết SQL, Flyway/Liquibase, JPA Entity, repository, thay đổi cấu hình kết nối hay tạo/sửa PostgreSQL.


---

## 13. REVIEW-PUBLICATION-SCORE-CHANGE=A — CONFIRMED (2026-11-07)

**Phê duyệt của chủ dự án:** "Điểm đã công bố không được thu hồi; các thay đổi điểm hợp lệ sau duyệt muộn phải có lịch sử và audit." Trạng thái `CONFIRMED (A)`. Điều khoản này bổ sung chi tiết thiết kế cho DEC-014, **không sửa** DEC-001…DEC-034 hoặc Requirements Baseline v1.0.

### 13.1. Phạm vi và định nghĩa

- Áp dụng cho **điểm bài kiểm tra giảng viên giao** (`ASSIGNED_EXAM`). `score_release_at` của mỗi assignment độc lập với `answer_release_at` và `answer_release_mode` (DB-25=A, DB-33=A, REVIEW-PUBLICATION-01=C). `score_release_at=NULL` có nghĩa là chưa mở điểm.
- Điểm được coi là **đã công bố/khả dụng** ngay khi `score_release_at <= server_now` và **ít nhất một người nhận bài đã có điểm hợp lệ, được phép xem qua API**. Không đợi sinh viên thực sự bấm xem. Nếu chưa có ai đủ điều kiện, trạng thái khả dụng chưa phát sinh; cập nhật lịch vẫn có thể xem xét theo điều kiện transaction.
- Khi đã khả dụng, giảng viên **không được** đặt `score_release_at=NULL`, dời lịch sang tương lai hoặc thực hiện thao tác khác làm mất quyền xem điểm đã công bố. Quy tắc mới chỉ khóa **thu hồi công bố**, không cấm sửa điểm hợp lệ phát sinh từ quy trình xét duyệt muộn. Các cập nhật lịch trước khi điểm khả dụng được audit.
- Đã công bố mà **điểm thực tế thay đổi hợp lệ** thì sinh viên tiếp tục thấy **điểm hiệu lực mới**, kèm dấu hiệu "đã điều chỉnh"/lịch sử thay đổi. Không bí mật rút lại quyền xem, không ghi đè điểm/candidate đã chốt.
- Không tự mở quyền công bố **đáp án đúng**, không đổi ràng buộc AI trong `ASSIGNED_EXAM`, không cho giảng viên khác đọc kết quả.

### 13.2. Ma trận kiểm tra DEC và Baseline

| Căn cứ đã khóa | Đối chiếu REVIEW-PUBLICATION-SCORE-CHANGE=A |
|---|---|
| `DEC-014`, `REQ-EXAM-08` | Teacher tùy chỉnh giờ công bố điểm/đáp án; server enforce. Quy tắc không thu hồi điểm sau khi đã mở là chi tiết thiết kế tương thích. |
| `DEC-027`, `REQ-EXAM-06` | Late candidate chỉ được ACCEPT/REJECT bởi teacher có quyền, bắt buộc lý do khác rỗng, `actor_id`, `reviewed_at`, `server_received_at`; pending/rejected không tự đóng góp điểm. |
| `DEC-029`, `REQ-EXAM-07` | Một attempt có **một** candidate hiệu lực; khi đổi candidate hợp lệ thì tính lại `MAX(eligible effective attempt scores)` cho recipient/assignment. Không có attempt hợp lệ = NULL/chưa có điểm; bài trắng hợp lệ = 0. |
| `DB-22-R=A`, `DB-22=B` | Late được duyệt sau `SERVER_TIMEOUT` thay nguồn điểm hiệu lực; late bị từ chối giữ score của `SERVER_TIMEOUT` hợp lệ. Cả hai candidate bất biến, history không mất. |
| `REVIEW-LATE-01=A`, `REQ-EXAM-09` | Nộp chủ động `CLIENT_ON_TIME` đã ACK không được thay bằng payload mới; retry đúng payload nhận lại response cũ. |
| `DB-25`, `DB-33`, `REVIEW-PUBLICATION-CHANGE=A` | Score và answer là **hai đường công bố độc lập**; không dùng việc đổi điểm để tự mở/đóng đáp án. |

### 13.3. Luồng điểm khi duyệt muộn hợp lệ — logical only

1. Server đã chốt `SERVER_TIMEOUT` đúng hạn; `submission_scores` lưu điểm candidate này **bất biến**. Nếu điểm đã khả dụng, student xem score cũ theo `score_release_at`.
2. Nhận `CLIENT_LATE` từ binding hiện hành (kiểm tra binding trước deadline/review), tạo **một** late candidate canonical `PENDING_REVIEW`, không thay điểm trong khi chờ duyệt; không tạo attempt thứ hai.
3. Teacher sở hữu assignment thực hiện ACCEPT/REJECT trong transaction, có lý do bắt buộc, actor/time/audit. REJECT: giữ candidate timeout hợp lệ. ACCEPT: hiệu lực chuyển sang late; không thay nội dung candidate cũ.
4. Ghi `attempt_result_events` append-only (`old_candidate_id`, `new_candidate_id`, reason, actor, timestamp), `audit_events` và lịch sử **điểm tổng kết trước/sau** trong cùng giao dịch hoặc một biểu diễn lịch sử được chứng minh tương đương. Vì `MAX` có thể thay đổi, không chỉ ghi delta của riêng candidate mà quên điểm tổng kết hiển thị trước đó.
5. Query điểm trả score hiệu lực hiện hành sau kiểm tra quyền + `score_release_at`. Nếu trước đó đã được xem, giữ quyền xem và hiển thị rõ đã cập nhật. Điểm có thể tăng **hoặc giảm**; không tự chọn điểm lớn nhất *giữa hai candidate của cùng attempt*. `MAX` của DEC-029 chỉ áp dụng giữa **các attempts hợp lệ**.
6. Khi đã `CLIENT_ON_TIME` được ACK từ thao tác nộp chủ động, không phát sinh nhánh late thay kết quả (REVIEW-LATE-01=A).

**Thiết kế bảng:** tiếp tục dùng `assignments`, `attempts`, `submission_candidates`, `submission_scores`, `late_submission_reviews`, `attempt_result_events`, `audit_events`. Tạm thời **đề xuất** `score_first_available_at`/`score_publication_events` hoặc audit marker tương đương, và nhật ký phiên bản điểm ở recipient/assignment để thể hiện before/after; **chưa khóa việc tạo bảng/cột mới** ở mức DDL.

### 13.4. Đồng thời và tính không thể thu hồi — thiết kế bắt buộc ở DB-05

- API đọc điểm **và** API đổi lịch công bố phải có cơ chế nhất quán quanh thời điểm `score_release_at` vừa đến; `first_viewed_at` là **không đủ** để xác định đã công bố. Kiểm tra tính khả dụng dựa vào server time + eligibility và marker được ghi nhất quán.
- Nếu tại thời điểm teacher cố dời lịch có ít nhất một recipient đã được quyền đọc điểm, server từ chối thay đổi gây thu hồi, kể cả người đó chưa gọi API. Nếu chưa có ai được đọc điểm, teacher có thể đổi lịch theo owner check và audit.
- Kết quả duyệt muộn, chuyển nguồn điểm, tính lại tổng kết và lịch sử phải nằm trong transaction bảo đảm nhất quán. Tranh chấp lịch công bố/đọc điểm/xét duyệt cần có trình tự lock hoặc mức isolation thích hợp; lựa chọn kỹ thuật chi tiết còn `REVIEW-PUBLICATION-TX` và `REVIEW-TX`, **không được coi đã implement**.
- REST và WSS/DTO không được gửi dữ liệu điểm bị giữ kín dưới field phụ, cache hoặc thông báo. Teacher chỉ xem bài thuộc lớp sở hữu, student chỉ xem điểm của mình; các bản điểm candidate khác không tự lộ cho student.

### 13.5. Ma trận test cần triển khai sau khi schema được duyệt (CHƯA CHẠY)

| ID | Tình huống | Kỳ vọng |
|---|---|---|
| SCORE-PUB-01 | `score_release_at=NULL`, candidate có điểm hợp lệ | Student không nhận điểm từ mọi endpoint |
| SCORE-PUB-02 | Giờ release chưa đến; teacher đổi lịch | Cho phép nếu có quyền, ghi audit |
| SCORE-PUB-03 | Đã qua release và có student eligible, chưa GET lần nào | Không được đặt NULL/dời lịch để thu hồi |
| SCORE-PUB-04 | Điểm đã thấy 7/10, teacher duyệt late hợp lệ thành 8/10 | Hiển thị 8/10 + đã điều chỉnh; giữ 7/10 trong lịch sử |
| SCORE-PUB-05 | Teacher REJECT late sau timeout đúng hạn 7/10 | Giữ 7/10, audit lý do REJECT |
| SCORE-PUB-06 | Late ACCEPT làm điểm giảm từ 8/10 xuống 6/10 | Có thể giảm, ghi history; không lấy MAX giữa hai candidate cùng attempt |
| SCORE-PUB-07 | Có hai attempts 7/10 và 8/10, late review làm một attempt từ 8 thành 6 | Điểm assignment mới bằng 7/10 (MAX các attempts hiệu lực) |
| SCORE-PUB-08 | Chưa có eligible attempt dù giờ release đã qua | Chưa tự tạo điểm 0; quyền đổi lịch cần được kiểm tra theo trạng thái khả dụng |
| SCORE-PUB-09 | GET điểm và đổi lịch đồng thời đúng mốc release | Transaction/order rõ; không lộ rồi thu hồi |
| SCORE-PUB-10 | Học viên không có quyền hoặc teacher không sở hữu lớp | FORBIDDEN, không lộ điểm/audit riêng tư |
| SCORE-PUB-11 | Giảng viên sửa lịch điểm sau khi đáp án đã mở | Hai cơ chế độc lập; chỉ từ chối nếu *điểm* đã khả dụng hoặc vi phạm quyền khác |
| SCORE-PUB-12 | Teacher duyệt muộn, hệ thống crash giữa lúc chuyển nguồn và ghi audit | Transaction rollback hoặc đầy đủ; không có effective score thay đổi mà thiếu history |

### 13.6. DESIGN OPEN sau quyết định này

- `REVIEW-PUBLICATION-SCORE-CHANGE=A` **CLOSED**. Những câu chữ OPEN về mục này ở các phần 6/10/11/12 **chỉ là lịch sử**, không còn hiệu lực; mục 13 ưu tiên cao hơn.
- `REVIEW-ACHIEVEMENT` **OPEN**: "bài đơn giản không tính thành tích" là kiểu bài ôn tập chỉ cần công bố đáp án linh hoạt, hay một assignment không đóng góp vào một tổng điểm/xếp loại? Baseline mới chốt điểm tổng kết **theo từng assignment**, chưa có một cơ chế tổng điểm toàn môn/lớp; không tự tạo `is_graded`/`counts_to_grade` hoặc sửa DEC-029.
- `REVIEW-PUBLICATION-TX`, `REVIEW-TX` OPEN: marker công bố, lock/isolation, các ràng buộc FK/index, lịch sử điểm tổng kết và concurrency.
- `REVIEW-OPS`, `REVIEW-AI` OPEN: retention chat/file/text/draft/audit, TTL token và ngữ cảnh AI.
- **Chưa phê duyệt ERD, chưa tạo SQL, migration, JPA Entity, Repository hay thay đổi PostgreSQL.**


---

## 14. AI-ASSIGNED-POST-01=A — Gia sư AI SAU lượt bài được giao (USER-CONFIRMED DESIGN, 2026-11-07)

### 14.1. Phát biểu người dùng và đối chiếu phạm vi

**Người dùng đã xác nhận:** sinh viên có nút **AI giải thích** ở trang xem lại từng câu của bài do giảng viên giao, **chỉ sau khi đã nộp hợp lệ và đáp án đã được công bố cho sinh viên ấy**. Họ có thể hỏi tiếp trong khung trò chuyện; nhiều câu thuộc **cùng attempt** dùng **một** conversation liên tục, attempt khác dùng conversation khác. Đối với bài tự học giữ `AI-UX-01=A`: một conversation cho một `practice_session`, không trộn hai loại.

| Nguồn khóa | Đối chiếu chính xác | Kết luận |
|---|---|---|
| DEC-003 / REQ-AI-06 | Trong **chế độ đang làm bài được giao** không cung cấp AI/giải đáp; backend phải chặn yêu cầu gắn với attempt đang thi. | **Không mâu thuẫn** nếu chỉ mở sau khi lượt thi đã kết thúc hợp lệ, không tồn tại đường vòng khi đang thi. |
| DEC-014 / REQ-EXAM-08 | Giảng viên chọn thời điểm công bố điểm/đáp án; server không trả dữ liệu chưa được phép xem. | **Không mâu thuẫn** nếu AI gọi **cùng điều kiện** `can_view_answers`; không coi quyền xem điểm là quyền xem đáp án. |
| AI-UX-01=A / DB-13 | Chat đã lưu, nhiều câu trong một lần làm cùng một mạch; lần làm khác chat khác, chủ sở hữu riêng. | **Tái sử dụng kiến trúc**, nhưng loại `PRACTICE_SESSION` và `ASSIGNED_ATTEMPT_REVIEW` phải tách theo scope. |
| REQ-AI-05 | Ghi rõ giải thích/chat AI cho **luyện tập tự học** và chat theo PDF. | **GAP PHẠM VI**: không ghi minh thị tính năng chat trên trang kết quả bài do giảng viên giao. Người dùng đã chọn chức năng này trong review thiết kế, nhưng trước khi nhận là một tính năng mới thuộc MVP cần ghi Change Request/điều chỉnh baseline theo cơ chế change control. Không sửa DEC hoặc baseline ngầm. |

**Trạng thái:** `USER-CONFIRMED DESIGN — MVP SCOPE GATE OPEN`. Không phải xung đột với DEC-003/014; đây là trường hợp chức năng mới chưa được liệt kê rõ trong `REQ-AI-05`. Chủ dự án đã xác nhận hành vi mong muốn; bước kiểm soát phạm vi trước coding cần ghi `CR`/phê duyệt cách đưa vào MVP và cập nhật tài liệu gốc theo quy trình. Chỉ lập ERD logic ở đây; không tạo migration.

### 14.2. Điều kiện quyền — một nơi kiểm tra ở server

`can_use_post_assigned_ai(user, attempt, question)` chỉ đúng khi **đồng thời**:

1. `user` là STUDENT chủ sở hữu của `attempt` và là người nhận assignment; auth session hợp lệ; không được lấy tài nguyên của bạn khác, kể cả đoán UUID.
2. Attempt **đã kết thúc và được server xác nhận trạng thái kết quả hợp lệ**: có bản nộp đúng hạn `CLIENT_ON_TIME`, hoặc `SERVER_TIMEOUT` hợp lệ, hoặc `CLIENT_LATE` đã được teacher `ACCEPT`. Chỉ `PENDING_SYNC` của client, autosave ACK, request nộp chưa ACK, hoặc **chỉ** `PENDING_REVIEW` không đủ. Nếu có bản late chờ duyệt trong khi vẫn có bản `SERVER_TIMEOUT` đúng hạn, cần xác nhận chính sách UX ở mục 14.5 trước khi code; không giả định câu trả lời cuối cùng là bản đang chờ.
3. `can_view_answers(user, attempt.assignment, attempt)` theo **cùng policy backend** đã chốt `REVIEW-PUBLICATION-01=C`, `DB-25/33`: `answer_release_at != NULL`, `now >= answer_release_at`, sinh viên có kết quả đủ điều kiện, và nếu mode `AFTER_CLOSE` thì `now >= closes_at`. Mode `AFTER_SUBMISSION` không yêu cầu đề toàn lớp đã đóng; quyền chỉ áp dụng sau khi lượt của chính sinh viên đã hoàn thành hợp lệ.
4. **Không có attempt bài được giao đang IN_PROGRESS thuộc student** mà người dùng có thể dùng chat AI trong QuizAI để hỏi hộ: cả Client cần ẩn AI ở màn hình thi và backend phải chặn đường gọi `POST /ai/...`, bao gồm mở một chat cũ theo session khác khi đang thi. Kiểm tra theo thời gian/trạng thái có hiệu lực ở server, không chỉ dựa vào tab UI.
5. Câu hỏi được yêu cầu thuộc chính `assignment_question_snapshots` của assignment/attempt đã xác thực; backend đọc đáp án chuẩn từ snapshot chỉ **sau khi** quyền công bố hợp lệ. Không nhận đáp án chuẩn do client truyền lên và không gửi context chưa công bố tới Gemini.

Bất biến thêm: quyền AI không được cấp chỉ vì **điểm** đã công bố, vì điểm và đáp án có hai lịch khác nhau; không sinh link chat hay API lấy lời giải trước khi công bố. Việc lựa chọn công bố sớm khi `max_attempts > 1` vẫn có nguy cơ rò rỉ cho lượt sau, vì giảng viên đã chọn chế độ `AFTER_SUBMISSION`; dashboard phải cảnh báo. Không tự đặt một cơ chế khóa số lượt mới.

### 14.3. Đề xuất ERD logic; KHÔNG phải bảng/cột được duyệt DDL

- `ai_conversations` dùng chung nhiều **loại hội thoại**, bổ sung `conversation_type`: `PRACTICE_SESSION`, `ASSIGNED_ATTEMPT_REVIEW`, `PDF_TUTOR` (các loại khác chỉ nếu đã nằm trong phạm vi hiện hành). Có `owner_student_id` và **một trong** các tham chiếu ngữ cảnh có thể null: `practice_session_id`, `assigned_attempt_id`, `source_pdf_document_id`.
- Với `ASSIGNED_ATTEMPT_REVIEW`, `assigned_attempt_id` NOT NULL và **unique theo attempt**, các FK practice/PDF NULL. Với `PRACTICE_SESSION`, `practice_session_id` NOT NULL và unique; không dùng chung conversation với assigned exam. Kiểm tra owner: `ai_conversations.owner_student_id` phải là owner thật của attempt/practice session.
- `ai_messages` vẫn gắn `conversation_id`; bổ sung **tham chiếu câu hỏi được giải thích** `assignment_question_snapshot_id?` và **nguồn kết quả tại thời điểm giải thích** `context_submission_candidate_id?` (hoặc normalized context-event nếu sau review có nhu cầu). Tin nhắn hỏi tiếp tự do có thể NULL những trường này. Ràng buộc candidate phải thuộc **chính attempt** và question snapshot thuộc assignment đó.
- Mỗi click **AI giải thích** từ câu 4, câu 9… tạo **tin nhắn/ngữ cảnh mới trong cùng conversation** của attempt; không tạo chat mới và không xóa messages cũ. Attempt khác luôn ra conversation khác.
- Lấy câu hỏi/đáp án đúng từ **snapshot đề giao** và câu trả lời sinh viên từ **effective submission candidate** đã được phép đọc, **không dùng** `attempt_answers` working autosave hoặc question version hiện hành. Nếu late ACCEPT đổi effective candidate, giữ lịch sử chat trước đó bất biến và dùng nguồn effective mới cho giải thích tiếp theo; cân nhắc hiển thị thông báo "kết quả đã được điều chỉnh" (UX detail chưa khóa).
- AI request chỉ qua `quizai-server` và Gemini API; không đưa Gemini key về JavaFX; hội thoại chỉ owner được xem; TEACHER chỉ có aggregate theo DEC-018 và **không được đọc** prompt/messages.

### 14.4. Test cases thiết kế bắt buộc (CHƯA thực thi)

| ID | Tình huống | Expected |
|---|---|---|
| AI-POST-01 | Đang thi, thử nút AI hoặc gọi trực tiếp API explain/chat | DENY tại server, không gửi đáp án hoặc prompt chứa solution cho Gemini |
| AI-POST-02 | Đã autosave có ACK, chưa nộp | DENY |
| AI-POST-03 | Đã bấm nộp nhưng chỉ PENDING_SYNC, chưa có ACK nộp | DENY / chờ fetch trạng thái authoritative |
| AI-POST-04 | Nộp hợp lệ, `answer_release_at=NULL` | DENY; có `score_release_at` không làm đổi kết quả |
| AI-POST-05 | Nộp hợp lệ, mode AFTER_SUBMISSION, answer release đến | ALLOW theo scope attempt chính chủ |
| AI-POST-06 | Nộp hợp lệ, mode AFTER_CLOSE, assignment còn mở | DENY đến cả `closes_at` và mốc release |
| AI-POST-07 | Hỏi câu 4, sau đó câu 9 trong cùng attempt | Một conversation, hai đoạn ngữ cảnh, lịch sử không mất |
| AI-POST-08 | Làm lượt khác trong cùng assignment | Conversation khác, không trộn lịch sử |
| AI-POST-09 | User cố mở chat hoặc snapshot của student khác | DENY theo owner, không rò rỉ chat/đáp án |
| AI-POST-10 | Câu bị bỏ trống, bấm AI giải thích sau release | ALLOW theo question snapshot, không phụ thuộc có row answer |
| AI-POST-11 | Late pending, không có effective accepted submission | DENY, chưa giả định bài đã hợp lệ |
| AI-POST-12 | Late được duyệt làm thay candidate hiệu lực | Chat cũ không sửa, lần giải thích tiếp theo dùng effective candidate mới |
| AI-POST-13 | Đã từng được xem AI một lượt, nay đang IN_PROGRESS ở lượt khác | Không dùng lịch sử/chat AI bên trong app để vượt cấm AI trong thi |
| AI-POST-14 | Hai request tạo chat đồng thời cho cùng attempt | Có một conversation nhờ unique + transaction |

### 14.5. DESIGN OPEN cần rà soát trước khi khóa ERD

- **CHANGE CONTROL:** xác nhận đưa AI explain/chat theo `ASSIGNED_ATTEMPT_REVIEW` vào MVP trong bản yêu cầu đã khóa (bằng CR theo quy trình); trước đó tuyệt đối không tự thêm màn hình/API khi giao Codex.
- **Late pending với fallback timeout:** attempt đã có `SERVER_TIMEOUT` hợp lệ nhưng có bản `CLIENT_LATE` đang `PENDING_REVIEW`: AI có thể giải thích checkpoint cũ hay chờ review xong? Đề xuất **chờ review** để tránh giải thích dựa vào đáp án tạm thời; cần duyệt.
- **Retention/context:** giới hạn chiều dài lịch sử đưa vào Gemini, thời hạn lưu chat, xóa chat, an toàn prompt và tốc độ/chi phí gọi AI; không tự thêm RAG/OCR.
- **UX revision:** cách hiển thị thông báo khi late ACCEPT đổi candidate hiệu lực sau khi chat đã có giải thích.
- `REVIEW-ACHIEVEMENT` vẫn DESIGN OPEN: không tự thêm cơ chế bảng điểm toàn môn/học kỳ hoặc cờ "không tính thành tích".

**STOP GATE:** Không tạo SQL, DDL, migration, JPA Entity, repository hoặc thay đổi PostgreSQL từ mục này. Các yêu cầu nghiệp vụ FROZEN không bị chỉnh sửa.

---

## 15. AI-LATE-PENDING=A — Chờ duyệt bản nộp muộn trước khi dùng AI theo attempt (CONFIRMED, 2026-11-07)

**Xác nhận của chủ dự án:** "Khi có `CLIENT_LATE` đang `PENDING_REVIEW`, chưa cho AI giải thích theo attempt dù `SERVER_TIMEOUT` đã hợp lệ; chờ quyết định duyệt/từ chối và đáp án công bố." **Trạng thái: `USER-CONFIRMED DESIGN`, không sửa các DEC đã ACCEPTED.**

### 15.1. Đối chiếu 34 DEC và Requirements Baseline (tập trung các điều khoản liên quan)

| Nguồn | Bất biến / phạm vi gốc | Kết luận với AI-LATE-PENDING=A |
|---|---|---|
| DEC-003 / `REQ-AI-06` | Không có gia sư AI trong lượt bài được giao đang thi; server chặn cả API. | **Tương thích:** điều kiện mới còn chặt hơn sau khi thi: không AI nếu đang có bản late chờ xét duyệt. Không cấp AI khi còn `IN_PROGRESS` ở một attempt đang thi. |
| DEC-014 / `REQ-EXAM-08` | Teacher kiểm soát thời gian công bố đáp án; server enforce. | **Tương thích:** việc review hoàn tất **chưa đủ**; vẫn cần `can_view_answers` đúng mode/lịch (`answer_release_at`, `closes_at`) và đúng owner. Công bố điểm không thay công bố đáp án. |
| DEC-023 / `REQ-EXAM-06` | Bản nộp offline sau hạn phải `PENDING_REVIEW`, teacher quyết định, không được tự xem là bài muộn hợp lệ. | **Tương thích:** trong thời gian đang xét duyệt không giải thích AI dựa trên nội dung `CLIENT_LATE` chưa duyệt hoặc giả định fallback đã là kết quả cuối. |
| DEC-027 | Duyệt/từ chối có lý do, actor, time và audit. | **Tương thích:** quyền AI chỉ mở lại sau transaction xét duyệt `ACCEPT/REJECT` được server commit; không mở khi teacher mới nhập form. |
| DB-22=B / DB-22-R=A / DB-29=A | Server auto-submit bản ACK timeout, kể cả bài trắng 0 điểm; ACCEPT late đổi effective candidate; REJECT giữ timeout. | **Tương thích:** `SERVER_TIMEOUT` tiếp tục là kết quả hợp lệ dự phòng và không bị sửa; **tạm chặn AI** khi có canonical late candidate đang pending. Quy tắc điểm không đổi. |
| AI-ASSIGNED-POST-01=A | Chat AI sau bài giảng viên giao, theo từng attempt; chỉ khi kết quả hợp lệ + đáp án công bố. | **Tương thích và bổ sung một gate bắt buộc:** `NOT EXISTS CLIENT_LATE PENDING_REVIEW` của cùng attempt. |
| AI-UX-01=A | Chat riêng theo từng lượt tự học (`practice_session`). | **Không ảnh hưởng:** gate này **chỉ áp dụng** `ASSIGNED_ATTEMPT_REVIEW`, không trì hoãn AI ở bài tự học hoàn thành. |
| DEC-018, DB-13 | Hội thoại riêng của student; teacher chỉ được xem aggregate tự học. | **Không đổi:** chat và messages do student sở hữu, không lộ cho teacher. |
| DEC-033 | Binding thiết bị cũ bị từ chối trước khi ghi bản nộp muộn. | **Không đổi:** dữ liệu từ binding bị thu hồi không tạo candidate/pending và không được dùng để khóa AI trái phép. |

**Lưu ý trạng thái:** `PENDING_REVIEW` là trạng thái **của candidate**, không tự động xóa/biến `SERVER_TIMEOUT` thành không hợp lệ. Việc tạm ngăn gọi AI là độc lập với điểm hoặc quyền xem kết quả có sẵn theo chính sách công bố. Không suy diễn rằng sinh viên mất điểm checkpoint; cũng không thay đổi quyền xem *đáp án* đã công bố theo REVIEW-PUBLICATION-01=C.

### 15.2. Policy backend (đề xuất chi tiết hiện thực; chưa chốt DDL)

`can_use_assigned_attempt_ai(student, attempt, question) =` tất cả điều kiện sau đều đúng:

1. Auth, owner của attempt, câu hỏi thuộc `assignment_question_snapshots` của attempt. Không thể dùng UUID do client gửi để đọc attempt của sinh viên khác.
2. Không đang ở chế độ thi được giao còn `IN_PROGRESS` mà AI có thể phục vụ gián tiếp (DEC-003); không chỉ ẩn nút JavaFX mà chặn trên mọi endpoint tạo message/gọi Gemini.
3. Đã có **server-confirmed eligible** effective submission candidate; autosave ACK ≠ submit ACK; chỉ `PENDING_SYNC` không đủ.
4. **Không có** canonical candidate `CLIENT_LATE` của cùng attempt còn `PENDING_REVIEW` (DB-34=A: tối đa một canonical late). Nếu có, trả `AI_REVIEW_PENDING`/trạng thái tương đương, **không gọi Gemini, không tạo tin nhắn giải thích mới**, bất kể fallback `SERVER_TIMEOUT` có điểm hợp lệ.
5. `can_view_answers` theo `answer_release_at` (khác NULL, đến giờ), `answer_release_mode` (`AFTER_SUBMISSION` hoặc `AFTER_CLOSE`) và không có truy cập trái phép trong khi đang thi. Chỉ `score_release_at` được mở không đủ.
6. Dựng prompt từ `assignment_question_snapshots` + **effective candidate sau duyệt**, không dùng `CLIENT_LATE PENDING_REVIEW` hoặc working autosave như nguồn đáp án đã được phê duyệt.

**Chuyển trạng thái (logic):**

| Trạng thái lượt / candidate | Đáp án công bố? | AI theo attempt |
|---|---|---|
| Attempt `IN_PROGRESS`, chưa nộp | Bất kỳ | **DENY** |
| `SERVER_TIMEOUT` hợp lệ, không có late pending | Có và các điều kiện khác đạt | **ALLOW** (phụ thuộc cổng MVP) |
| `SERVER_TIMEOUT` hợp lệ + `CLIENT_LATE PENDING_REVIEW` | Có hoặc chưa | **DENY — AI_REVIEW_PENDING** |
| Chỉ `CLIENT_LATE PENDING_REVIEW`, không có candidate hợp lệ | Có hoặc chưa | **DENY** |
| Late `ACCEPT` đã commit, chọn late thành effective candidate | Có | **ALLOW** nếu không còn cản trở khác |
| Late `REJECT` đã commit, `SERVER_TIMEOUT` vẫn effective | Có | **ALLOW** nếu không còn cản trở khác |
| Late đã duyệt (ACCEPT/REJECT) | Chưa / NULL | **DENY — ANSWERS_NOT_RELEASED** |
| Đã ACK `CLIENT_ON_TIME` | Có, không có late hợp lệ | **ALLOW**; theo REVIEW-LATE-01=A không nhận payload đổi bài |

**Nếu bản late chuyển từ pending sang ACCEPT**, conversation cũ (nếu đã tồn tại từ trước) tiếp tục tồn tại, không sửa tin nhắn cũ. Các giải thích mới sau review dùng candidate đã được duyệt. Nếu REJECT, dùng fallback `SERVER_TIMEOUT`, kể cả bài trắng 0 điểm. Chỉ chặn **AI giải thích/gửi chat mới** trong khi pending; quyền đọc lại tin nhắn cũ và cách UI hiển thị trạng thái pending cần rà soát thêm (không tự xóa/hide lịch sử chat đã lưu).

### 15.3. Hệ quả ERD, giao dịch và concurrency

- **Không cần tạo bảng riêng chỉ vì gate này.** Dùng `submission_candidates(attempt_id, candidate_type, review_status, ...)` và `late_submission_reviews` hiện có trong dự thảo; lấy từ nguồn server. Cấu trúc khóa `attempt_id + candidate_type`/partial uniqueness cho canonical late vẫn theo DB-34.
- `ai_conversations` tham chiếu `assigned_attempt_id` duy nhất theo AI-ASSIGNED-POST-01; không tạo conversation khác sau mỗi câu hỏi hoặc mỗi lần duyệt.
- Trước khi gọi Gemini, server kiểm tra owner, trạng thái thi, policy công bố, candidate pending và effective source trong một snapshot đọc nhất quán. Vì gọi AI có độ trễ, **kiểm tra lại trước khi lưu/trả lời** để tránh race: nếu một late pending xuất hiện hoặc quyền không còn hợp lệ thì không phát hành lời giải cũ; dùng source/version stamp hoặc cơ chế tương đương. Không giữ transaction DB mở xuyên suốt HTTP gọi Gemini.
- Teacher ACCEPT/REJECT late được commit cùng audit, cập nhật effective candidate/score (nếu có) và trạng thái review. Chính sách AI chỉ dựa vào bản ghi đã commit, không đọc form teacher chưa lưu.
- Không dùng tham số `pending=false` của client để bỏ qua server check. Log lỗi có mã/attempt ID và thời điểm phù hợp, **không lưu Gemini API key, token plaintext hoặc đáp án chuẩn** trong audit.

### 15.4. Kiểm thử cần đưa vào DB-05 (chưa thực thi)

| Mã | Tình huống | Kết quả mong đợi |
|---|---|---|
| AI-LP-01 | Có `SERVER_TIMEOUT` 7/10 và `CLIENT_LATE PENDING_REVIEW`; answer release đã mở | AI **DENY**, không gọi Gemini, không tạo message mới |
| AI-LP-02 | Late ACCEPT và reason/actor/time đã commit; đã công bố đáp án | AI ALLOW; lấy context từ late effective candidate |
| AI-LP-03 | Late REJECT và reason/actor/time đã commit; đã công bố đáp án | AI ALLOW; lấy context từ fallback `SERVER_TIMEOUT` |
| AI-LP-04 | Late được duyệt, `answer_release_at=NULL` hoặc chưa đến giờ | AI DENY |
| AI-LP-05 | Chỉ late PENDING_REVIEW, không có timeout eligible | AI DENY |
| AI-LP-06 | Câu bỏ trống và fallback timeout hợp lệ, late đã REJECT | AI có thể giải thích câu bằng snapshot nếu answer release mở |
| AI-LP-07 | Cùng attempt đã có chat trước, sau đó nhận late PENDING | Không xóa chat; chặn mọi message/explain mới khi pending |
| AI-LP-08 | Late PENDING được ghi giữa lúc Gemini đang tạo phản hồi | Không gửi/lưu lời giải dựa trên source cũ; recheck + bỏ/đánh dấu failed an toàn |
| AI-LP-09 | Student gọi REST trực tiếp hoặc tham chiếu attempt của người khác | DENY tại server; không lộ answer/chat |
| AI-LP-10 | Binding cũ đã thu hồi gửi payload muộn | Chặn trước tạo late candidate; không đổi AI gate hiện hành |
| AI-LP-11 | `practice_session` khác hoàn thành | Không bị chặn bởi late pending của assigned attempt nếu chính sách DEC-003 khác không chặn |

### 15.5. Change Control / trạng thái trước triển khai

- **AI-LATE-PENDING=A là quyết định thiết kế đã chốt.** Không có mâu thuẫn với các DEC được đối chiếu.
- **`AI-ASSIGNED-POST-01=A` vẫn là `USER-CONFIRMED DESIGN — MVP SCOPE GATE OPEN`:** `REQ-AI-05` ghi chat/giải thích cho self-study, không nêu rõ AI sau bài kiểm tra teacher giao. Cần Change Request/điều chỉnh baseline được duyệt **trước khi Codex triển khai tính năng này trong MVP**; xác nhận gate AI-LATE-PENDING không tự xem là CR đã phê duyệt. **Không sửa** DEC-001…DEC-034 và baseline trong lượt này.
- `REVIEW-ACHIEVEMENT`, retention/quota chat, policy về đọc lịch sử chat khi late pending, kiểm thử race và cơ chế lock chi tiết vẫn DESIGN OPEN. Không tạo thêm graded/ungraded flag hay chức năng mới.
- **STOP GATE:** Không tạo SQL/DDL, migration, JPA Entity, Repository, cấu hình kết nối hoặc thay đổi PostgreSQL từ file này. ERD đang chờ review cuối.



---

## 16. REVIEW-AI-HISTORY-PENDING=A — Đọc chat cũ ở chế độ chỉ đọc khi late chờ duyệt (CONFIRMED, 2026-11-07)

**Người dùng xác nhận:** “Cho sinh viên đọc lại các tin nhắn AI cũ trong trạng thái chỉ đọc khi late `PENDING_REVIEW`; khóa mọi lời giải/tin nhắn mới, vẫn chặn AI trong lúc đang thi.” Đây là **USER-CONFIRMED DESIGN**, không phải cấp phép đưa `ASSIGNED_ATTEMPT_REVIEW` vào MVP khi Change Control chưa hoàn thành.

### 16.1. Đối chiếu quyết định nghiệp vụ và giới hạn

| Nguồn | Kiểm tra | Kết luận / bắt buộc giữ |
|---|---|---|
| `DEC-003`, `REQ-AI-06` | Không cung cấp AI hỗ trợ trong lượt thi giảng viên giao đang diễn ra; backend chặn | **TƯƠNG THÍCH**: đọc chat đã lưu không được mở đường vòng trong một lượt thi còn `IN_PROGRESS` (kể cả lượt thi khác của cùng student). Không được gọi Gemini/new explain khi đang thi. |
| `DEC-014`, `REQ-EXAM-08`, `REVIEW-PUBLICATION-01=C` | Teacher/server kiểm soát công bố đáp án, điểm có lịch riêng | **TƯƠNG THÍCH**: chỉ trả *nội dung tin nhắn đã tồn tại và đã được phép phát hành*, không dựng context câu hỏi/đáp án mới. Không coi quyền đọc chat cũ là quyền truy cập bản nộp muộn đang chờ duyệt. |
| `DEC-018`, `REQ-PROG-01` | Teacher xem aggregate tự học, không được xem chat/tài liệu/bộ đề cá nhân | **TƯƠNG THÍCH**: chỉ owner student của conversation/attempt được đọc; TEACHER kể cả chủ lớp bị từ chối. |
| `DEC-023`, `REQ-EXAM-06` | Bản offline muộn do server nhận là `PENDING_REVIEW`; không tự nhận hợp lệ | **TƯƠNG THÍCH**: GET history không thay `review_status`, `effective_candidate_id`, điểm, hoặc quyền chấm. |
| `AI-LATE-PENDING=A` | Pending late khóa AI explain/chat theo attempt | **TƯƠNG THÍCH**: khóa **mọi AI action mới**; ngoại lệ duy nhất được xác nhận là **đọc lại tin nhắn đã lưu**, không regenerate/retrieve live answer context. |
| `DB-13`, `AI-UX-01`, `AI-ASSIGNED-POST-01` | Persist history và một chat/attempt | Giữ nguyên lịch sử và identity conversation, không sao chép sang chat mới; **AI sau bài giao vẫn cần Change Request để thêm vào MVP**. |

**Giải thích ranh giới:** Khi một late candidate xuất hiện *sau* khi student từng hỏi AI hợp lệ, các tin nhắn lịch sử vẫn tồn tại. Chính sách không thu hồi đáp án đã công bố (`REVIEW-PUBLICATION-CHANGE=A`) hỗ trợ việc đọc lại nội dung đã phát hành, nhưng không cấp quyền tiết lộ candidate pending, đáp án mới chưa được công bố hay dữ liệu của student khác. Không suy diễn rằng luôn luôn có lịch sử chat ở trạng thái pending; có thể chưa từng tạo conversation.

### 16.2. Ma trận quyền theo trạng thái (backend là nguồn sự thật)

| Điều kiện | GET danh sách/chat cũ `ASSIGNED_ATTEMPT_REVIEW` | POST chat/explain/regenerate hoặc stream AI |
|---|---|---|
| Attempt đã kết thúc; canonical `CLIENT_LATE PENDING_REVIEW`; student đúng owner; không có lượt thi đang hoạt động | **ALLOW READ-ONLY đối với tin đã lưu, đã phát hành**; 404/empty nếu chat chưa tồn tại; không tự tạo chat | **DENY `AI_REVIEW_PENDING`**, không tạo message, không gọi Gemini |
| Late đã `ACCEPT`/`REJECT`; student đúng owner; công bố đáp án hợp lệ; không có lượt thi đang hoạt động | **ALLOW** | **ALLOW** theo toàn bộ gates của mục 14/15; context mới đọc effective candidate sau review |
| Chưa đến lịch công bố đáp án, chat chưa từng được tạo hợp lệ | Không phát sinh chat mới, không trả ngữ cảnh đáp án | **DENY** |
| Sinh viên đang làm *bất kỳ* bài giao còn `IN_PROGRESS` | **DENY/ẩn AI history trong QuizAI** theo quy tắc chặn AI trong thi, không dùng chat cũ để lấy trợ giúp | **DENY** |
| Student khác / TEACHER / thiếu auth hợp lệ | **DENY** | **DENY** |

**Quan trọng:** Quyền read-only là quyền tải các **message bất biến đã được lưu trước đó**. GET history không được tự động sinh summary, context card mới, answer reveal, refresh completion hoặc trigger request Gemini. Nếu client hiển thị một thẻ ngữ cảnh câu hỏi trong message cũ, hiển thị đúng snapshot thông tin **đã được phép công bố lúc tạo message**; không JOIN trực tiếp live pending candidate để render đáp án mới.

### 16.3. Quy tắc xử lý và ERD cần giữ

- `ai_conversations` có `conversation_type='ASSIGNED_ATTEMPT_REVIEW'`, `assigned_attempt_id` unique theo attempt và `owner_student_id`; không tạo conversation bằng GET.
- `ai_messages` là lịch sử append-only, chứa message đã committed/được phát hành (theo chính sách lưu trữ). Nội dung cũ không bị sửa/ghi đè khi nhận `CLIENT_LATE` hoặc khi teacher xét duyệt.
- Trạng thái `is_read_only` nếu có là **DTO/UI derived** từ server, **không cần cột DB `is_read_only`, không tạo bảng mới**. Server dùng kiểm tra `(owner, active exam, candidate review state, release permission)` mỗi request.
- Khi late pending: chặn **POST** create-message / explain-question / regenerate / retry-with-new-output, mọi WSS/SSE stream sinh AI còn chạy; không gọi Gemini hay ghi chat mới. Race: recheck trước mỗi lần phát lời giải/ghi message; nếu pending xảy ra sau khi bắt đầu generation, hủy/không phát hành nội dung.
- Khi late resolved: dùng `attempt.effective_candidate_id` đang có hiệu lực cho **lời giải mới**, không rewrite lịch sử theo candidate cũ; GET lịch sử vẫn chỉ owner. Nếu bản late được chấp nhận đổi nguồn điểm, nội dung trước đây có thể đã lạc hậu; cách gắn nhãn giải thích cũ là **UX DESIGN OPEN**, không tự sửa lời AI đã lưu.
- Không thay đổi `submission_candidates`, `late_submission_reviews`, `attempts.effective_candidate_id`, `submission_scores`, điểm tổng kết hay audit verdict thông qua GET lịch sử.

### 16.4. Acceptance tests ở mức thiết kế (chưa chạy)

| ID | Test | Expected |
|---|---|---|
| AI-HIST-01 | Chat tồn tại, late pending và đã từng công bố đáp án hợp lệ, student owner không thi | GET trả đúng messages cũ; UI read-only; không gọi Gemini |
| AI-HIST-02 | Late pending, student cố `POST /messages` | DENY; không insert `ai_messages`, không gọi Gemini |
| AI-HIST-03 | Late pending, student gọi explain / regenerate / SSE/WSS AI | DENY/hủy stream; không phát lời giải mới |
| AI-HIST-04 | Late pending, student yêu cầu message thuộc student khác | DENY; không lộ nội dung hay context |
| AI-HIST-05 | TEACHER phụ trách lớp yêu cầu nội dung chat riêng | DENY theo DEC-018 |
| AI-HIST-06 | Không có chat cũ khi late pending | GET không tự tạo conversation hay trả lời AI |
| AI-HIST-07 | Student đang thi một attempt khác, muốn mở history chat cũ | DENY/ẩn AI history, không bypass DEC-003 |
| AI-HIST-08 | Late ACCEPT sau pending, đáp án đủ điều kiện công bố | Mở tạo tin nhắn mới; effective late, history cũ giữ nguyên |
| AI-HIST-09 | Late REJECT sau pending, timeout hợp lệ, đáp án đủ điều kiện | Mở tạo tin nhắn mới dựa fallback timeout; history cũ giữ nguyên |
| AI-HIST-10 | Late pending xuất hiện trong lúc Gemini đang sinh lời giải | Recheck và không lưu/phát sinh lời giải mới |
| AI-HIST-11 | GET history trong lúc đổi review state | Luôn trả nội dung đã lưu; permission/read-only response dựa authoritative server state; không leak candidate pending |
| AI-HIST-12 | Student chỉ mới `PENDING_SYNC`, chưa có ACK/hội thoại hợp lệ | Không tự tạo chat, không lấy đáp án nhờ API history |

### 16.5. Phạm vi chưa khóa — STOP GATE

- `AI-ASSIGNED-POST-01` vẫn là **USER-CONFIRMED DESIGN, MVP CHANGE CONTROL REQUIRED** vì Baseline `REQ-AI-05` chưa quy định tường minh hội thoại hậu bài do teacher giao. Quyết định đọc-only ở đây **không** phải CR phê duyệt.
- Thời hạn lưu chat, quota/tokens, cơ chế xóa lịch sử, cách ghi dấu lời giải gắn với candidate đã bị thay khi late ACCEPT, xử lý phiên streaming là **IMPLEMENTATION/UX DESIGN OPEN**.
- `REVIEW-ACHIEVEMENT` chưa định nghĩa chấm thành tích toàn môn/học kỳ; không thêm tính năng hay cờ mới.
- **KHÔNG tạo** SQL/DDL, Flyway/Liquibase migration, JPA Entity, Repository, cấu hình DB, hoặc sửa PostgreSQL; ERD vẫn DRAFT, chưa duyệt.

---

## 17. REVIEW-AI-HISTORY-VERSION=A — CONFIRMED

**Tình huống:** Một cuộc hội thoại AI của lượt thi đã có lời giải dựa trên effective submission candidate phiên bản cũ. Giảng viên chấp nhận `CLIENT_LATE`, làm thay đổi effective candidate và có thể thay đổi điểm.

**Quy tắc được người dùng xác nhận:**
1. Giữ nguyên văn bản, thời gian và thứ tự mọi tin nhắn AI đã phát hành; không sửa ngược hoặc tự regenerate.
2. Những lời giải **bị ảnh hưởng** bởi việc đổi nguồn kết quả phải được gắn nhãn trên UI: *“Lời giải này được tạo trước khi kết quả bài làm được điều chỉnh.”*
3. Mọi câu hỏi/giải thích mới dùng effective candidate hiện hành, sau khi qua đầy đủ quyền: đã kết thúc thi, đã công bố đáp án, không có `CLIENT_LATE PENDING_REVIEW`, không đang thi chính thức.
4. Khi late pending: vẫn chỉ đọc tin cũ theo `REVIEW-AI-HISTORY-PENDING=A`, không gửi tin mới.
5. Giảng viên không có quyền đọc chat cá nhân theo DEC-018. Không thay đổi kết quả, trạng thái duyệt hoặc thời điểm công bố.

**ERD draft:** `ai_messages` cần có tham chiếu ngữ cảnh bất biến tới candidate/version tại lúc sinh lời giải (ví dụ `context_submission_candidate_id` nullable; `context_result_event_id` nếu thực sự cần). Server so sánh tham chiếu này với effective candidate hiện hành để tính `is_stale_explanation` trong DTO; **không tạo cột mutable `is_stale`**. Chỉ đánh dấu những tin giải thích phụ thuộc kết quả cũ, không đánh dấu bừa toàn bộ hội thoại. Nếu lời giải chỉ nói kiến thức chung và không gắn candidate, không tự suy đoán là lỗi thời.

**Acceptance criteria:** late ACCEPT làm đổi nguồn → tin giải thích liên quan được gắn nhãn; late REJECT giữ timeout → không gắn nhãn sai; lời giải mới dùng nguồn mới; history bất biến; pending vẫn read-only; owner-only; không bypass DEC-003/014.

## 18. FINAL DESIGN REVIEW — CÁC QUYẾT ĐỊNH CHƯA XÁC NHẬN

Danh sách câu hỏi được gom ở file `QUIZAI_DB_IMPLEMENTATION_READINESS_CHECKLIST_v0.1.md`. Mọi phương án khuyến nghị trong file đó vẫn là **PROPOSED**, không phải CONFIRMED. Cần giải quyết các điểm chặn về retention, quyền, độ đồng thời, lưu trữ và phạm vi trước khi phê duyệt ERD. Không phát sinh chức năng xếp hạng/điểm tổng kết học kỳ chỉ vì trước đây có cụm từ “không tính thành tích”.

**STOP GATE:** Đợi người dùng trả lời vòng review cuối, rà soát ERD chi tiết và phê duyệt rõ ràng. Chưa tạo SQL/DDL, migration, JPA Entity/Repository hay thay đổi PostgreSQL.

---

## 19. FINAL DESIGN REVIEW — FINAL-01…FINAL-12 đều A, USER-CONFIRMED (2026-11-07)

**Thay thế trực tiếp trạng thái “chưa xác nhận” của mục 18 (lịch sử).** Người dùng đã xác nhận cả 12 quyết định. Không còn lựa chọn A/B mở trong bộ FINAL-01…FINAL-12. Đây là phê duyệt *nguyên tắc thiết kế*, **không phải phê duyệt ERD/schema hay cho phép code**.

| ID | Chọn | Nội dung chốt | Đối chiếu |
|---|---|---|---|
| FINAL-01 | **A — CONFIRMED** | MVP đã khóa trước; AI hậu bài giao deferred đến khi CR được duyệt | Phạm vi, DEC-003, REQ-AI-05/06 |
| FINAL-02 | **A — CONFIRMED** | UUID cho PK nghiệp vụ (FK cùng kiểu) | Mọi bảng nghiệp vụ |
| FINAL-03 | **A — CONFIRMED** | Flyway migrations có phiên bản, không Hibernate auto-create | Hạ tầng triển khai |
| FINAL-04 | **A — CONFIRMED** | PostgreSQL DEV và TEST tách biệt | Hạ tầng và kiểm thử |
| FINAL-05 | **A — CONFIRMED** | Private filesystem, DB chỉ lưu opaque storage key | DEC-005/008, DB-06/12 |
| FINAL-06 | **A — CONFIRMED** | Giữ attempt/snapshot/audit, soft-delete document; retention cụ thể chốt trước production | DEC-015/018/030, DB-14 |
| FINAL-07 | **A — CONFIRMED** | Row-lock transaction, unique/index và idempotency | DEC-013/022/023/033/034 |
| FINAL-08 | **A — CONFIRMED** | Giới hạn tệp/câu hỏi được cấu hình ở server, benchmark trước chốt số | DEC-005/009 |
| FINAL-09 | **A — CONFIRMED** | Chỉ practice session COMPLETED tính điểm và tỷ lệ đúng; dở dang lưu lịch sử riêng | DEC-018, DB-26/27/28 |
| FINAL-10 | **A — CONFIRMED** | Sự kiện/mốc FIRST_AVAILABLE và guard giao dịch không cho thu hồi score/answer | DEC-014, DB-25/33 |
| FINAL-11 | **A — CONFIRMED** | Giữ TEACHER tự đăng ký có quyền ngay; row owner, class membership và audit | DEC-021/026, REQ-AUTH-01/02 |
| FINAL-12 | **A — CONFIRMED** | AI quota/rate limit cấu hình ở backend; chưa ấn định số | DEC-003/008, REQ-AI-05 |

### 19.1. Kết luận tương thích với nguồn đã khóa

- Các quyết định FINAL lựa chọn biện pháp triển khai/diễn giải trong phạm vi DEC-001…DEC-034 và Requirements Baseline v1.0; **không sửa 34 DEC**.
- `FINAL-09` chỉ loại practice dở dang khỏi mẫu số thống kê accuracy/score; vẫn có thể hiển thị số phiên đang làm trong lịch sử riêng. Giáo viên chỉ nhận tổng hợp theo các membership episode lịch sử hợp lệ.
- `FINAL-10` áp dụng riêng cho hai loại công bố score và answer; điều kiện là **thực sự khả dụng cho ít nhất một recipient**, không phải đã bấm xem. Sau khi khả dụng không thể dời về sau, NULL hóa hoặc đổi mode làm mất quyền cũ.
- `FINAL-06` **không** xác nhận thời hạn lưu dữ liệu định lượng; đây là cổng vận hành trước production, không được đặt lịch xóa lịch sử ngầm.
- `FINAL-11` giữ nguyên rủi ro TEACHER tự khai; không thêm teacher verification hoặc quyền SUPERADMIN.
- `REVIEW-ACHIEVEMENT`: theo mô tả Dashboard, “ôn tập” và “giữa kỳ” là **cấu hình per assignment** đã có (`max_attempts`, deadlines, answer/score publication), không tạo course-grade hoặc `counts_to_grade` trong MVP.
- `AI-ASSIGNED-POST-01=A`, `AI-LATE-PENDING=A`, `REVIEW-AI-HISTORY-PENDING=A` và `REVIEW-AI-HISTORY-VERSION=A` là thiết kế đã được người dùng xác nhận về hành vi hậu bài giao, **nhưng tính năng này chưa được đưa vào MVP bởi Change Request**. ERD MVP không bắt buộc `assigned_attempt_id` trong AI chat, không cho phép sinh migration phần hậu MVP.

### 19.2. Kết quả bàn giao để chủ dự án xem xét phê duyệt riêng

- `QUIZAI_DB05_ERD_FINAL_FOR_APPROVAL_v1.0.md`: logical ERD/schema dictionary và giao dịch đã hòa giải; **FOR APPROVAL / NOT APPROVED**.
- `QUIZAI_DB_APPROVAL_GATE_v1.0.md`: checklist nghiệm thu ERD, Change Control, gate trước migration.
- Danh sách kỹ thuật về index, check, FK và hành vi race là **đề xuất cụ thể trong ERD trình duyệt**; chỉ trở thành cơ sở giao Codex sau khi người dùng đồng ý phê duyệt ERD và có lệnh bắt đầu triển khai tiếp theo.

**STOP GATE:** chưa tạo SQL/DDL, Flyway migration, JPA Entity/Repository hoặc thay đổi PostgreSQL.


---

## 20. ERD-V1.0-SIGNOFF — USER APPROVED (2026-11-07)

**Quyết định mới nhất của chủ dự án:** “Phê duyệt ERD v1.0 cho QuizAI Desktop. Chưa cho phép triển khai database; chuẩn bị hướng dẫn Codex thực hiện theo từng giai đoạn.”

- **APPROVED (thiết kế):** `QUIZAI_DB05_ERD_FINAL_FOR_APPROVAL_v1.0.md`, SHA-256 `5ff37a672d9a76ce4092301f53064a850304fed0ace74d8f71fa9db766aa31ab`. Toàn bộ 37 bảng logic, ràng buộc, state machine, phạm vi và acceptance matrix trong ERD v1.0 là baseline để thiết kế/chuẩn bị triển khai. Không có thay đổi nội dung schema trong mục 20.
- **NOT AUTHORIZED (triển khai):** Không tạo SQL/DDL, Flyway migrations, JPA Entity, Repository, sửa Gradle/config DB, kết nối/chỉnh PostgreSQL chỉ dựa trên phê duyệt ERD. Chủ dự án cấp phép theo từng phase riêng.
- **ALLOWED (chuẩn bị):** Prompt Codex **P0 read-only inspection**; không thay đổi files hoặc database.
- **DEFERRED / CR:** AI hậu bài giảng viên giao và luật liên quan là thiết kế UX đã xác nhận nhưng không vào MVP nếu chưa được Change Request phê duyệt.
- **Quản trị phiên bản:** Giữ nguyên file ERD v1.0 đã ký duyệt; trạng thái “AWAITING” trong ERD gốc bị **ghi nhận sign-off sau này** bởi `QUIZAI_DB05_ERD_APPROVAL_RECORD_v1.0.md`, không sửa nội dung ERD. Nếu cần thay đổi bất biến/schema, xuất bản v1.1 và trình phê duyệt lại.

**Handoff:** `QUIZAI_DB_CODEX_PHASED_HANDOFF_v1.0.md` và `QUIZAI_DB_CODEX_P0_READONLY_PROMPT_v1.0.md`. Đây là **tài liệu chuẩn bị**, không phải lệnh chạy migration.
