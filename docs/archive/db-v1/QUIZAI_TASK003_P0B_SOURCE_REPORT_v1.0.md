# TASK-003 / DB-05 — P0B SOURCE REPORT (verbatim pasted Codex audit)

> Nguồn: báo cáo P0B do chủ dự án dán vào ChatGPT. Đây là kết quả đọc nguồn do Codex cũ báo cáo; **không phải** bằng chứng đã chạy database/runtime tests. Phần bên dưới giữ nguyên nội dung gốc.

---

P0B đã xác nhận nguồn được phục hồi đúng byte và danh sách 37 bảng khớp hoàn toàn với handoff. Đã đọc toàn bộ ERD, gồm data dictionary, DBX-01…24 và T01–T30. Có một số điểm tên gọi/thứ tự phụ thuộc cần làm rõ trước khi chuyển thành schema vật lý; chưa kiểm chứng runtime và chưa triển khai P1.
A. SHA-256 và quan hệ phiên bản
Cả tám tài liệu bắt buộc đều hiện diện. Checksum đầu và cuối audit không thay đổi.
Tài liệu trong docs/
SHA-256 thực tế
Đối chiếu
PROJECT_DECISIONS_v7.md
d99d1c7aeae173954beb110420422ba664bc76a37419dd7a070052202ee419a3
Khớp Approval Record
REQUIREMENTS_BASELINE_v1.0.md
f3d25763ac3c14042754a223c1ec4b330f0c1efa3d857ba196e90fbd2cc92c2a
Khớp Approval Record
QUIZAI_DB_DESIGN_DECISIONS_v0.18.md
0bc5fd4ca2889512581ab8539e8191f68d79f622b6b3806650929193d6e1859b
Khớp yêu cầu
QUIZAI_DB_DESIGN_DECISIONS_v0.19.md
fd0801fb7b0155e25648df9c02dc2388b7d8f35b07e80e99b205ba86eae884f8
Khớp yêu cầu
QUIZAI_DB05_ERD_FINAL_FOR_APPROVAL_v1.0.md
5ff37a672d9a76ce4092301f53064a850304fed0ace74d8f71fa9db766aa31ab
Khớp yêu cầu
QUIZAI_DB_APPROVAL_GATE_v1.0.md
4bb6d2764b9df319a9487c2da95570460a93513177d3bebda40ae1177c739827
Khớp yêu cầu
QUIZAI_DB05_ERD_APPROVAL_RECORD_v1.0.md
a9c55155c382f0db310e214f8ac044f93669573d9f8b6b89361a160c6fe07eba
Ghi nhận; prompt không cung cấp checksum kỳ vọng riêng
QUIZAI_DB_CODEX_PHASED_HANDOFF_v1.0.md
d409b958a7c4dc0fbc8003c867f6003c9a9b94f623a414668e071ea3f6ed832c
Ghi nhận; prompt không cung cấp checksum kỳ vọng riêng
Đã so sánh byte: toàn bộ 106.633 byte của v0.18 là tiền tố nguyên vẹn của v0.19. v0.19 dài 108.350 byte, bổ sung phần sign-off. Vì vậy, tiêu đề v0.18 được giữ lại là lịch sử nối tiếp, không cần sửa.
Trạng thái AWAITING trong ERD/Approval Gate bị thay thế bởi Approval Record §2 và Decision Log v0.19 §20. Kết luận vẫn là G1 APPROVED; G2/G3 NOT AUTHORIZED.
B. Checklist đúng 37 bảng

Đối chiếu ERD §3 với handoff P2-A…F:
ERD: 37 dòng, 37 tên phân biệt.
Handoff: 37 tên, 37 tên phân biệt.
Không thiếu, thừa hoặc trùng tên.
Tất cả 37 bảng khai báo id UUID PK.
Dấu ✓ dưới đây xác nhận tên bảng và batch khớp, không phải kết quả test database.
#
Batch
Bảng
Cột/khóa đã đối chiếu — điểm cần giữ
1
A
users ✓
email normalized unique; password_hash, role, status
2
A
auth_sessions ✓
user_id, session_token_hash, expires_at; revoked_at?, last_seen_at?
3
A
classes ✓
teacher_id, join_code_digest, join_enabled; unique code đang bật
4
A
class_members ✓
class_id, student_id, joined_at, left_at?; một episode active
5
A
subjects ✓
code unique, active, catalog_version
6
A
topics ✓
subject_id; unique (subject_id,code)
7
B
documents ✓
owner_id, input_type; storage/metadata và deleted_at nullable
8
B
document_extractions ✓
document_id, extraction_no; unique theo document/lần trích xuất
9
B
ai_generations ✓
requested_by; source_document_id?, source_extraction_id?
10
B
question_banks ✓
owner_teacher_id; archived_at?; không chia sẻ teacher
11
B
questions ✓
owner_id, bank_id?; các FK nguồn nullable
12
B
question_versions ✓
unique (question_id,version_no); subject/topic/approval metadata có điều kiện
13
B
question_options ✓
unique (question_version_id,position); is_correct
14
B
quizzes ✓
owner_id, quiz_type, status, archived_at?
15
B
quiz_questions ✓
quiz_id, question_version_id; unique (quiz_id,position)
16
C
practice_sessions ✓
student_id, quiz_id; finished_at?, kết quả nullable trước hoàn tất
17
C
practice_session_questions ✓
practice_session_id, question_version_id, position; lưu ý tên rút gọn bên dưới
18
C
practice_answers ✓
unique session_question_id; selected_option_id?, answered_at?, is_correct?
19
C
ai_conversations ✓
owner_student_id; practice/PDF context nullable theo loại; unique practice context
20
C
ai_messages ✓
conversation_id, role, content, practice_session_question_id?
21
D
assignments ✓
class/teacher/quiz FK; hai release timestamp nullable độc lập; policy_revision
22
D
assignment_recipients ✓
unique (assignment_id,student_id); membership_id episode gốc
23
D
assignment_question_snapshots ✓
unique (assignment_id,position); source_question_version_id?
24
D
assignment_snapshot_options ✓
unique (question_snapshot_id,position); đáp án đóng băng
25
D
assignment_publication_events ✓
unique (assignment_id,release_kind); first_available_at, recorded_at
26
E
attempts ✓
recipient/attempt number và start request unique; effective_candidate_id?
27
E
attempt_answers ✓
unique (attempt_id,question_snapshot_id); selected_option_id?, revision
28
E
attempt_device_bindings ✓
unique attempt/version; một active binding; revoked_at?
29
E
device_transfer_requests ✓
attempt/requester/new session FK; reviewer/time/reason nullable khi chưa xử lý
30
F
submission_candidates ✓
attempt_id, kind, digest; server_received_at?, source_revision?
31
F
submission_candidate_answers ✓
unique candidate/question; selected_option_id không nullable; câu trống không có row
32
F
late_submission_reviews ✓
unique candidate_id; decision, reason, reviewer/time
33
F
submission_scores ✓
unique candidate_id; earned/possible/normalized score
34
F
attempt_result_events ✓
old_candidate_id?, new_candidate_id, actor/time/reason
35
F
assignment_recipient_score_events ✓
recipient; old/new best attempt và điểm nullable; cause/actor/time
36
F
idempotency_receipts ✓
unique (attempt_id,operation,request_id); digest, durable ingress, response metadata
37
F
audit_events ✓
actor/assignment/attempt FK nullable; outcome và metadata_sanitized

Tổng theo batch: 6 + 9 + 5 + 5 + 4 + 8 = 37.
Các sai khác hoặc điểm cần làm rõ trước P2:
Vị trí
Nội dung thực tế
Đánh giá
practice_session_questions, ERD §3 so với §4 DBX-07/20
Dictionary dùng practice_session_id; DBX-07/20 ghi session_id
Tên cột không thống nhất. Không được tự tạo thêm session_id hoặc sinh index theo tên không có trong dictionary
classes, ERD §10 DEC-016
Ghi classes.owner, trong khi dictionary có teacher_id
Thuật ngữ owner rút gọn; không phải cột mới
ERD §4 DBX-19/21
candidates, scores, reviews, recipient_score_events, publication_events
Tên rút gọn của các bảng đầy đủ trong checklist, không phải năm bảng bổ sung
ai_conversations.practice_session_id, ai_messages.practice_session_question_id, ERD §13 bước 4
Nhóm M được đặt trước P trong thứ tự tổng quát, nhưng có FK tới P
Không thể áp nguyên văn thứ tự nhóm làm thứ tự tạo FK. Handoff P2-C đã liệt kê practice trước conversation/message
classes.join_code_digest, ERD §3/DBX-01
Dictionary ghi UQ?; DBX-01 yêu cầu unique trong tập enabled
Cần diễn giải rõ nullability/unique vật lý khi lập PR schema; không tự đổi thành global unique
submission_scores.normalized_score, ERD §5
Yêu cầu precision chính xác nhưng chưa ghi NUMERIC(p,s) cụ thể
Chi tiết vật lý còn phải review; không tự coi một precision tùy chọn đã được phê duyệt
Đây là các điểm đối chiếu tài liệu cần ghi nhận, không làm mất hiệu lực phê duyệt G1. Không chỉnh bản gốc; thay đổi schema/bất biến đáng kể phải qua v1.1 và phê duyệt lại.
C. PK/FK, NULL, UNIQUE/CHECK/index và DBX-16
Các nguyên tắc được xác nhận từ ERD:
Nhóm
Nội dung đã rõ ở mức thiết kế
PK/thời gian
37 UUID PK; FK cùng kiểu; thời gian server UTC/Instant, biểu diễn PostgreSQL tương đương TIMESTAMPTZ — §3
Nullable
Dấu ? là nullable; hai release timestamp NULL nghĩa chưa công bố; effective pointer NULL không đồng nghĩa điểm 0 — §3.1
Câu trả lời
Working/practice answer có thể chưa chọn; candidate answer không có row cho câu bỏ trống — §3
Pending late
Thiếu row review nghĩa pending; dictionary không khai báo cột submission_candidates.review_status — §3.1
Unique
Email, catalog, vị trí câu/option, recipient, start request, binding, candidate/review/score/receipt — DBX-01…17
Conditional unique
Một membership active; một binding active; một base thuộc CLIENT_ON_TIME hoặc SERVER_TIMEOUT, không phải mỗi kind một base — DBX-02/11/13
CHECK/guard
Thời gian hợp lệ; reason trim không rỗng; earned ≥ 0, possible > 0, earned ≤ possible, normalized ∈ [0,1] — §3.1, DBX-15
Cross-row/cross-table
Đúng một đáp án, owner/role, cùng subject/version/snapshot/assignment phải được bảo vệ bằng constraint và transaction phù hợp; không chỉ CHECK đơn giản — DBX-03…12
Index
Membership, recipient, assignment window, attempt deadline, candidate/score/review, completed practice, chat/audit/history/publication — DBX-18…21; lưu ý tên rút gọn đã nêu
Bất biến/lịch sử
Snapshot, candidate, score, review và event append-only; không cascade xóa lịch sử — DBX-22, §6

DBX-16 đã được xác minh trực tiếp tại ERD §4:164:
submission_candidates.attempt_id trỏ attempt.
attempts.effective_candidate_id là pointer nullable trỏ ngược candidate.
Candidate được chọn phải thuộc chính attempt đó. FK chỉ kiểm tra candidate tồn tại là chưa đủ.
ERD cho phép composite FK với unique (attempt_id,id) hoặc khóa giao dịch và kiểm chứng tương đương. Handoff yêu cầu không hạ xuống service-only khi FK khả thi.
P2-E/P2-F phải có kế hoạch xử lý vòng FK được review. P0B không chọn deferred constraint, trigger, staging hay kỹ thuật migration nào.
Nguồn bổ sung: ERD §2.2:71, Handoff:94.
Các bất biến nghiệp vụ liên quan cũng đã rõ:
Timeout trắng là 0 hợp lệ; không có eligible attempt là NULL.
Late ACCEPT thay nguồn điểm cùng attempt, có thể tăng hoặc giảm; MAX chỉ giữa effective scores của các attempt eligible.
Binding bị thu hồi bị chặn trước late.
Công bố score/answer độc lập, FIRST_AVAILABLE không đợi lần GET đầu tiên và không được thu hồi quyền đã khả dụng.
AI hậu bài giao và hai cột assigned_attempt_id, context_submission_candidate_id vẫn ngoài schema MVP khi chưa có CR.
T01–T30: đủ 30 ID liên tục, không thiếu hoặc trùng; đã đọc expected của từng case tại ERD §11.
Nhóm test
Nội dung
T01–T04
Start idempotency/concurrency, leave/rejoin, tiếp tục attempt cũ
T05–T08
Ownership/privacy, version và snapshot hợp lệ
T09–T16
0/NULL, timeout/late, review, retry và thu hồi thiết bị
T17–T22
Publication, lịch độc lập, chống lộ đáp án và race
T23–T25
Completed aggregate, PDF owner và khóa AI khi thi
T26–T30
History sau đổi điểm, WSS, soft-delete, email unique, backup/restore/secrets

Tất cả đều NOT RUN trong P0B. Ma trận chưa có dòng riêng nêu rõ thử gán effective candidate của attempt A sang B; cần đưa negative test DBX-16 vào kế hoạch kiểm thử được cấp phép sau này, không tự sửa T01–T30 hiện tại.
D. Cập nhật GAP/UNKNOWN của P0
Hạng mục
Kết quả mới
Thiếu ERD/v0.18/Approval Gate
Đã giải quyết, đúng checksum
Quan hệ v0.18 → v0.19
Đã xác nhận bằng byte, không còn UNKNOWN
G1 đã duyệt hay chưa
Đã xác nhận APPROVED; AWAITING chỉ lịch sử
37 tên bảng và batch
Đã xác nhận khớp 37/37
Cột logic, UUID, nullable, constraints/index yêu cầu
Đã xác minh tài liệu; các điểm tên gọi/vật lý được nêu ở B
DBX-16 và vòng FK
Đã rõ yêu cầu thiết kế; cách hiện thực chưa chọn
T01–T30
Đã xác minh specification; chưa có kết quả thực thi
PostgreSQL driver/Flyway/JPA
GAP triển khai hiện tại, không còn thiếu tài liệu để hiểu mục tiêu
DEV/TEST fail-closed
GAP cấu hình, phải thực hiện khi P1 được phép
Schema/migration/Entity/Repository thực tế
Chưa có trong source; không suy ra trạng thái PostgreSQL bên ngoài
Phiên bản PostgreSQL, quyền kết nối, physical constraints/index, deadlock/race
UNKNOWN runtime cho đến khi được phép kiểm tra
Retention định lượng, backup/restore production
Cổng vận hành riêng; chưa được tự chốt
Kiểm tra source hiện tại vẫn cho thấy: năm module đúng cấu trúc; Java 21, Wrapper 9.8.0, Boot 4.0.9; Server chỉ có launcher/Web MVC, chưa có persistence dependency hoặc profile DB. Server build và application.yml chưa thay đổi.
Chỉ Server được dùng persistence. Client/Admin/Common/TCP không có DB dependency. Đây là xác minh source, không phải xác minh kết nối hoặc startup.
E. Đề xuất phạm vi P1 — không phải lệnh thực hiện
Nếu sau này được cấp phép P1, phạm vi hợp lý là:

File/phạm vi dự kiến
Mục đích
quizai-server/build.gradle
Bộ JDBC/PostgreSQL/Flyway tương thích Boot thực tế; chỉ thêm phần được duyệt
quizai-server/src/main/resources/application.yml
Guard kích hoạt persistence và ngăn tự ghi schema
application-dev.yml trong resources Server
Cấu hình DEV bằng biến môi trường, không hardcode credentials
application-test.yml trong test resources
TEST tách biệt DB và credentials khỏi DEV
Tài liệu cấu hình môi trường được phép
Ghi profile, biến bắt buộc, quyền kết nối và tiêu chí dừng
Các bảo vệ cần có trong proposal P1:
Chế độ không bật persistence không tự tạo datasource; khi bật mà thiếu cấu hình hợp lệ phải fail-closed, không fallback sang DB khác.
Flyway không tự chạy khi chưa có quyền P2; SQL initialization không được tự thực thi.
Hibernate không create/update; dùng none/validate theo giai đoạn phù hợp. Chưa có Entity thì validation không chứng minh đủ 37 bảng.
Không kết nối DEV/TEST nếu authorization P1 chỉ cho sửa cấu hình.
Không thay root Gradle/Wrapper hoặc desktop/Common/TCP nếu không có lý do và phạm vi được duyệt.
P1 không tạo schema; P2 làm schema theo batch được phép; P3 mới Entity/Repository.
Không thêm teacher verification, SUPERADMIN, chia sẻ bank, course-grade hoặc AI hậu bài giao.
Các điểm tên gọi/thứ tự ở B cần được ghi nhận và xác nhận trước khi dịch sang DDL P2. Chưa có yêu cầu phải sửa bản ERD nguyên gốc để hoàn thành P0B.
F. Trạng thái kết thúc
git status trước/sau giống nhau. Các staged docs, thay đổi preview JavaFX và .idea/gradle.xml có sẵn được giữ nguyên. Không ghi báo cáo vào repository; không thay biến môi trường, chạy build/test, tải dependency, mở ứng dụng/container hoặc truy cập PostgreSQL.
P0B complete; G2 implementation NOT AUTHORIZED; no files or DB changed.