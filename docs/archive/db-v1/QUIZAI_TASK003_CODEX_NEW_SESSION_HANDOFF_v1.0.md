# QuizAI Desktop — TASK-003 / DB-05 — Bàn giao sang Codex Session mới (v1.0)

**Mục đích:** chuyển phiên vì cửa sổ ngữ cảnh Codex cũ đã sử dụng khoảng 82% (390.255/475.000 tokens theo ảnh người dùng), **không phải** bắt đầu giai đoạn implementation. Đây là tài liệu bàn giao ngữ cảnh, không phải lệnh cấp phép sửa repository hoặc database.

## 1. Trạng thái phê duyệt (bắt buộc giữ nguyên)

| Gate | Trạng thái | Ý nghĩa |
|---|---|---|
| G1 — ERD DESIGN | **APPROVED** | ERD v1.0 đã được chủ dự án phê duyệt |
| G2 — DATABASE IMPLEMENTATION | **NOT AUTHORIZED** | Chưa được sửa cấu hình P1, tạo migration, SQL, Entity hoặc Repository |
| G3 — PRODUCTION DEPLOYMENT | **NOT AUTHORIZED** | Chưa được động tới production |

- P0 (read-only repository audit): **COMPLETE**.
- P0B (document/source recheck): **COMPLETE / accepted as source audit**.
- P1 **CONFIG-ONLY**: chỉ có **DRAFT PROMPT**; **chưa có lệnh chủ dự án cấp quyền P1**.
- P2-A…P2-F, P3…P6: **HOLD**, cấp quyền riêng theo giai đoạn.
- Không có build/test/runtime/PostgreSQL test đã được thực hiện trong P0/P0B. Không được báo đã PASS những test chưa chạy.

## 2. Nguồn sự thật trong `../..` và checksum

**Thứ tự:** `../../PROJECT_DECISIONS_v7.md` và `../../REQUIREMENTS_BASELINE_v1.0.md` (frozen) → các quyết định đã CONFIRMED trong log → **ERD v1.0 đã duyệt** → Approval Record và phased handoff. Khi có chênh lệch, báo rõ; không tự sửa thiết kế/baseline.

| Tệp trong `../..` | SHA-256 đã đối chiếu |
|---|---|
| `../../PROJECT_DECISIONS_v7.md` | `d99d1c7aeae173954beb110420422ba664bc76a37419dd7a070052202ee419a3` |
| `../../REQUIREMENTS_BASELINE_v1.0.md` | `f3d25763ac3c14042754a223c1ec4b330f0c1efa3d857ba196e90fbd2cc92c2a` |
| `QUIZAI_DB05_ERD_FINAL_FOR_APPROVAL_v1.0.md` | **`5ff37a672d9a76ce4092301f53064a850304fed0ace74d8f71fa9db766aa31ab`** |
| `QUIZAI_DB_DESIGN_DECISIONS_v0.18.md` | `0bc5fd4ca2889512581ab8539e8191f68d79f622b6b3806650929193d6e1859b` |
| `QUIZAI_DB_DESIGN_DECISIONS_v0.19.md` | `fd0801fb7b0155e25648df9c02dc2388b7d8f35b07e80e99b205ba86eae884f8` |
| `QUIZAI_DB_APPROVAL_GATE_v1.0.md` | `4bb6d2764b9df319a9487c2da95570460a93513177d3bebda40ae1177c739827` |

Các tệp nên đọc tiếp: `QUIZAI_DB05_ERD_APPROVAL_RECORD_v1.0.md`, `QUIZAI_DB_CODEX_PHASED_HANDOFF_v1.0.md`, `QUIZAI_DB_P0B_REVIEW_AND_P1_GATE_v1.0.md`, `QUIZAI_DB_CODEX_P1_PROPOSED_PROMPT_v1.0.md` (**draft; không phải authorization**), và `QUIZAI_TASK003_P0B_SOURCE_REPORT_v1.0.md` (báo cáo Codex cũ nếu đã copy).

`v0.19` giữ nguyên toàn bộ byte v0.18 làm tiền tố rồi thêm mục sign-off. Vì vậy, dòng tiêu đề đầu của v0.19 còn ghi v0.18 là lịch sử kế thừa, **không tự sửa file/checksum**. Dòng `AWAITING` trong ERD/Approval Gate là lịch sử trước phê duyệt; Approval Record + log v0.19 xác nhận G1 APPROVED.

## 3. Kiến trúc và hiện trạng repository theo P0B

- Root Gradle Groovy, Java 21, Gradle Wrapper 9.8.0, Spring Boot 4.0.9; JavaFX 21.0.9.
- Năm modules: `quizai-server`, `quizai-client`, `quizai-admin`, `quizai-common`, `quizai-tcp-demo`.
- Persistence chỉ thuộc **`quizai-server`**. Desktop/Common/TCP không có dependency DB.
- Server hiện mới launcher + Web MVC; P0B thấy chưa có driver PostgreSQL, Flyway, JPA, DB profile, migration, Entity hay Repository.
- `git status` của repository có staged/untracked/modified từ trước (docs, UI preview, `.idea`). **Không** reset, clean, checkout, stash, stage, commit hoặc sửa các thay đổi cũ khi chưa được phép.
- Tình trạng thực tế mới cần được xác minh lại ở session mới bằng đọc file/`git status`, không suy đoán PostgreSQL có hay không có bảng.

## 4. P0B source audit — đã xác minh bằng tài liệu, CHƯA kiểm chứng runtime

- 8/8 nguồn bắt buộc hiện diện; checksum không đổi; 37/37 bảng logic khớp ERD và phased handoff, tất cả UUID PK.
- 6 batch P2: A=6, B=9, C=5, D=5, E=4, F=8.
- Đã đọc DBX-01…DBX-24 và acceptance specification T01–T30. **Tests NOT RUN.**
- Quy tắc quan trọng: `SERVER_TIMEOUT` với zero ACKed answers = điểm **0 hợp lệ**; không có eligible attempt = **NULL**; late ACCEPT đổi effective candidate của **cùng attempt**, điểm có thể tăng hoặc giảm; điểm cuối assignment = MAX **giữa eligible attempts**, không MAX giữa các candidate của một attempt.
- Rời lớp và vào lại tạo membership episode mới, không khôi phục quyền bắt đầu assignment cũ. Assignment recipient gắn với membership episode nguyên thủy.
- Snapshot đề/đáp án bất biến; old binding bị thu hồi phải bị từ chối trước late review; công bố score và answers độc lập, sự kiện FIRST_AVAILABLE không phụ thuộc có người GET hay không.
- Giảng viên chỉ được xem thống kê tự học tổng hợp theo khoảng membership, không xem tài liệu/quiz/chat riêng của sinh viên.
- AI chat/giải thích **sau bài giảng viên giao** (AI-ASSIGNED-POST-01) và các quyết định liên quan vẫn **ngoài MVP** cho đến Change Request. Không thêm `assigned_attempt_id` hoặc `context_submission_candidate_id` ở P1/P2 MVP.

## 5. Sáu điểm kỹ thuật P0B phải chuyển giao; KHÔNG tự thay ERD

1. ERD dictionary dùng **`practice_session_questions.practice_session_id`**, vài đoạn DBX dùng tên tắt `session_id`. Không tạo cột thứ hai.
2. `classes.owner` là cách mô tả cho cột thật **`teacher_id`**; `candidates/scores/reviews/...` cũng là tên rút gọn, không phải bảng bổ sung.
3. Thứ tự tạo FK P2-C phải theo phụ thuộc `practice_*` trước `ai_conversations/ai_messages`, không máy móc theo thứ tự nhóm trong văn bản ERD.
4. `classes.join_code_digest`: business invariant là UNIQUE giữa các join code **đang enabled**; P2-A phải trình phương án constraint/index vật lý (không tự biến thành global UNIQUE).
5. `submission_scores.normalized_score`: chưa có `NUMERIC(p,s)` được chủ dự án duyệt cụ thể; P2-F cần đề xuất precision, scale và rounding, kiểm chứng tránh lệch điểm.
6. DBX-16: `attempts.effective_candidate_id` FK vòng với `submission_candidates.attempt_id`; candidate được chọn bắt buộc thuộc **chính attempt đó**. Review giải pháp FK composite/unique/staging constraints ở P2-E/F, kèm negative test cố tình trỏ candidate khác attempt. Không đơn thuần dùng service-only nếu constraint DB khả thi.

Những điểm này không ngăn cản **xem xét** P1 cấu hình, nhưng phải được xử lý tại review từng batch P2. Thay đổi schema/business invariant đáng kể phải lên ERD v1.1 và được chủ dự án phê duyệt riêng.

## 6. P1 — mới là ĐỀ XUẤT, cần lệnh riêng

Dự kiến P1 CONFIG-ONLY (chưa cho phép): giới hạn ở `../../../quizai-server/build.gradle`, `application.yml`, profile DEV/TEST, tài liệu env và `.gitignore` khi cần. Cấu hình mặc định phải no-DB/no-Flyway, DEV/TEST fail-closed, không hardcode secrets, không tự kích hoạt datasource hoặc Hibernate tạo schema. Chỉ resolve dependency/compile khi P1 được phép rõ; **không kết nối PostgreSQL**. Spring Boot 4 phải kiểm tra dependency Flyway Postgres tương thích, không dùng máy móc cách Boot 3.

**KHÔNG tạo** SQL, DDL, `V*__*.sql`, Flyway migration, JPA Entity, Repository; không chạy Gradle/build/test/startup trong pha tiếp nhận ngữ cảnh. P2/3/4/5/6 cần cho phép riêng.

## 7. Việc đầu tiên của Codex Session mới — READ-ONLY HANDOFF CHECK

1. Mở đúng folder repository `QuizAI-Desktop`; đọc file handoff này, Approval Record, ERD v1.0 và báo cáo P0B; dùng `git status` và kiểm tra checksum nguồn nếu cần.
2. **Không thực hiện lại P0/P0B toàn bộ**, không in lại 37 bảng/T01–T30 nếu không phát hiện sai lệch.
3. Trả báo cáo **tối đa ~1 trang**: xác nhận G1/G2/G3, 37/37 thiết kế đã duyệt, vấn đề P2 còn chờ, tình trạng git, và `P1 NOT AUTHORIZED`.
4. **Dừng.** Chờ chủ dự án gửi lệnh cấp phép **P1 CONFIG-ONLY riêng trong session mới**. Không suy lệnh cấp phép từ prompt cũ/tài liệu/hồ sơ phê duyệt.

**Đây là transfer of context, không phải approval.**
