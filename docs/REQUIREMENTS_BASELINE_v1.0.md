# QuizAI Desktop — Requirements Baseline v1.0

**Ngày khóa:** 2026-11-05  
**Trạng thái:** `REQUIREMENTS FROZEN — MVP` (khóa phạm vi và nghiệp vụ; chưa phải phần mềm đã triển khai).  
**Nguồn quyết định:** `PROJECT_DECISIONS_v7.md`, DEC-001…DEC-034, thông tin chốt ngăn xếp công nghệ trong lịch sử dự án.  
**Kiểm soát thay đổi:** chức năng ngoài baseline hoặc điều chỉnh DEC cần Change Request được người dùng chấp thuận.

## A. Tên đề tài (giữ nguyên)
**SỬ DỤNG GIAO THỨC TCP, HTTPS, WEBSOCKET VÀ MÔ HÌNH CLIENT–SERVER ĐỂ XÂY DỰNG HỆ THỐNG QUIZAI DESKTOP HỖ TRỢ HỌC TẬP VÀ QUẢN LÝ KIỂM TRA CÓ TÍCH HỢP TRÍ TUỆ NHÂN TẠO.**

## B. Mục tiêu và biên hệ thống
Phần mềm QuizAI Desktop hỗ trợ sinh viên tự học qua tài liệu và AI; giảng viên tạo ngân hàng câu hỏi, duyệt câu hỏi, giao bài kiểm tra, theo dõi kết quả và thống kê học tập tổng hợp của sinh viên thuộc lớp mình. Gồm **QuizAI Client** (JavaFX, sinh viên), **QuizAI Admin** (JavaFX, giảng viên), và **QuizAI Server** (Java/Spring Boot). Không phát triển website hoặc ứng dụng di động trong MVP.

## C. Công nghệ đã chốt
| Thành phần | Lựa chọn |
|---|---|
| Ngôn ngữ chính | Java |
| Desktop (Client/Admin) | JavaFX + CSS |
| Server | Java Spring Boot, Spring Security |
| DB trung tâm | PostgreSQL |
| AI | Gemini API được gọi từ backend, khóa không có ở client |
| Tệp | PDF qua Apache PDFBox; DOCX/TXT/văn bản nhập trực tiếp qua extractor phù hợp; thư viện DOCX chưa chốt |
| API nghiệp vụ Internet | HTTPS/REST API |
| Thông báo | WSS/WebSocket; đồng bộ trạng thái sau reconnect bằng REST |
| TCP trực tiếp | Java Socket/ServerSocket, heartbeat/ACK, nhiều client, timeout; **chỉ LAN/localhost demo** |
| Cài đặt Windows | jpackage hoặc giải pháp tương đương |

## D. Yêu cầu chức năng MVP đã ACCEPTED

### D1. Tài khoản và lớp học
- `REQ-AUTH-01`: Sinh viên và giảng viên đều có thể tự đăng ký và chọn vai trò; chọn TEACHER thì **có quyền ngay** sau đăng ký. Không khẳng định đã xác minh danh tính giảng viên (DEC-021, 026).
- `REQ-AUTH-02`: Mọi thao tác được xác thực và phân quyền tại server theo role **và quyền sở hữu/lớp**; role TEACHER không phải SUPERADMIN.
- `REQ-CLASS-01`: Giảng viên tạo/quản lý lớp; sinh viên vào lớp bằng mã (DEC-001, 016, 017).
- `REQ-CLASS-02`: Khi rời lớp, giữ lịch sử đã có; chặn bắt đầu lượt thi mới, **nhưng cho hoàn thành/nộp lượt đã bắt đầu** (DEC-030, 031).

### D2. Tài liệu, AI và bộ câu hỏi
- `REQ-AI-01`: Client và Admin đều nhập PDF, DOCX, TXT hoặc dán văn bản để tạo câu hỏi AI qua backend (DEC-005, 009).
- `REQ-AI-02`: Sinh viên có bộ đề cá nhân; giảng viên có ngân hàng câu hỏi riêng, không chia sẻ giữa giảng viên trong MVP (DEC-006, 020).
- `REQ-AI-03`: Giảng viên xem xét/sửa/duyệt từng câu trước khi giao thi; ngân hàng câu hỏi có môn, chủ đề và độ khó (DEC-007, 019).
- `REQ-AI-04`: Tích lũy câu hỏi đã duyệt vào ngân hàng và tái sử dụng khi soạn đề; **không bao gồm tự động sinh nhiều mã đề từ ngân hàng lớn** (DEC-010).
- `REQ-AI-05`: Sinh viên tự học, làm đề luyện tập, nhận giải thích, chat AI và có thể hỏi đáp dựa trên PDF đã nhập (DEC-002, 008).
- `REQ-AI-06`: Không cung cấp gia sư AI/giải đáp trong lượt thi được giao; chặn ở backend đối với tác vụ gắn với lượt thi đó (DEC-003).

### D3. Quản lý bài kiểm tra
- `REQ-EXAM-01`: Admin giao đề cho sinh viên/lớp thuộc quyền; cấu hình giờ mở, hạn nộp, thời lượng mỗi lượt, số lượt tối đa (mặc định 1) (DEC-011, 013, 016).
- `REQ-EXAM-02`: Khi giao, tạo snapshot đề bất biến, giữ cả đáp án chuẩn ở server; sửa ngân hàng sau đó không làm đổi đề đang được giao (DEC-015).
- `REQ-EXAM-03`: Mỗi lượt thi chỉ cho một phiên thiết bị còn hiệu lực; chuyển máy chỉ khi giảng viên cho phép; phiên cũ bị từ chối khi upload dữ liệu (DEC-022, 028, 033).
- `REQ-EXAM-04`: Khi mất mạng trong lúc thi, tiếp tục làm và lưu tạm thông tin attempt/câu trả lời cần khôi phục; không tự mở rộng lưu offline toàn bộ thư viện (DEC-012, 024).
- `REQ-EXAM-05`: Hết thời gian tự nộp phần đã trả lời; nếu offline thì xếp hàng nộp chờ đồng bộ, tuyệt đối không báo đã nộp thành công khi chưa nhận ACK server (DEC-034).
- `REQ-EXAM-06`: Bài server nhận sau hạn: `PENDING_REVIEW` → giảng viên duyệt/từ chối **có lý do**; bài muộn bị từ chối vẫn tính 1 lượt; không dùng điểm chưa duyệt hoặc bị từ chối để tính điểm chính thức (DEC-023, 027, 032).
- `REQ-EXAM-07`: Điểm tổng kết của assignment bằng điểm cao nhất của các attempt hợp lệ đã chấm; không có attempt hợp lệ nghĩa là **chưa có điểm**, không tự gán 0 (DEC-029).
- `REQ-EXAM-08`: Thời điểm công bố điểm/đáp án do giảng viên tùy chỉnh; server không trả nội dung bị giữ kín trước thời điểm cho phép (DEC-014).
- `REQ-EXAM-09`: API nộp bài phải xử lý gửi trùng idempotent; reconnect không tạo thêm lượt làm (hệ quả của DEC-013, 023, 034).

### D4. Theo dõi học tập và thông báo
- `REQ-PROG-01`: Giảng viên xem điểm của bài do mình giao và **thống kê tự học tổng hợp** của sinh viên thuộc lớp, không được xem nội dung chat/tài liệu/bộ đề riêng (DEC-018).
- `REQ-NET-01`: REST qua HTTPS là nguồn đồng bộ dữ liệu nghiệp vụ; WebSocket/WSS thông báo bài mới và cập nhật tức thời; client kết nối lại phải lấy trạng thái chuẩn từ REST.
- `REQ-NET-02`: Module TCP riêng LAN/localhost chỉ demo kết nối, heartbeat/ACK, framing, timeout và nhiều client; không dùng theo dõi online Internet hoặc truyền dữ liệu thi nhạy cảm (DEC-004, 025).

## E. Quy tắc trạng thái và bất biến (thiết kế khởi đầu, không phải DB schema đã khóa)

### E1. Attempt — trạng thái nghiệp vụ gợi ý
- `IN_PROGRESS` — đã tạo lượt, thiết bị hiện hành được phép làm.
- `PENDING_SYNC` — **chỉ là trạng thái tại client**, chưa được server xác nhận nộp.
- `SUBMITTED_ON_TIME` — server đã nhận hợp lệ đúng hạn.
- `PENDING_REVIEW` — server nhận sau hạn, chờ duyệt.
- `ACCEPTED_LATE` — giảng viên chấp nhận bài muộn, có lý do.
- `REJECTED_LATE` — giảng viên từ chối bài muộn, có lý do, vẫn tiêu hao lượt.
- Không nhất thiết phải đưa các nhãn y hệt thành giá trị ENUM trong database; cần thiết kế state machine có chuyển trạng thái hợp lệ, audit và transaction.

### E2. Các bất biến không được phá
1. Bài đã giao sử dụng snapshot bất biến; câu hỏi trong ngân hàng có thể tiếp tục chỉnh sửa độc lập.
2. Server là nguồn sự thật về hạn, số lượt, điểm và quyền; mốc hết hạn tham chiếu `min(assignment.closes_at, attempt.started_at + duration)`.
3. Không được lấy đồng hồ client hoặc thời gian lưu nháp offline làm bằng chứng chắc chắn đã trả lời trước hạn.
4. Thiết bị/phiên được chuyển đổi bằng thao tác được phê duyệt; mọi gói từ binding cũ bị từ chối **trước khi** xét chính sách nộp muộn.
5. Rời lớp chặn start mới nhưng không chặn tiếp tục/nộp attempt đã bắt đầu hợp lệ.
6. Attempt bị từ chối vì nộp muộn vẫn tính một lần trong `max_attempts`, không đóng góp vào điểm tổng kết.
7. Server kiểm soát quyền xem đáp án/điểm; thi chính thức không dùng AI bên trong QuizAI.
8. Nộp trùng cùng `attempt_id` không tạo thêm attempt hoặc thay đổi kết quả đã chốt.

## F. Phân quyền baseline
| Hành động | STUDENT | TEACHER |
|---|---|---|
| Tự đăng ký và chọn vai trò | Có | Có; kích hoạt ngay, không xác minh |
| Tạo lớp và mã lớp | Không | Có với lớp của mình |
| Tham gia lớp bằng mã | Có | Không phải luồng học viên |
| Bộ đề cá nhân, lịch sử chat | Chỉ của mình | Không được xem của học viên |
| Ngân hàng câu hỏi giảng viên | Không | Chỉ ngân hàng của mình |
| Giao đề và xem báo cáo bài giao | Không | Trong lớp của mình |
| Xem thống kê tự học tổng hợp | Của mình | Của sinh viên thuộc lớp phụ trách |
| Duyệt bài muộn, chuyển thiết bị | Không | Chỉ attempt thuộc bài giao của mình |

## G. Ngoài MVP (DEFERRED/NOT INCLUDED)
- Tự động tạo nhiều mã đề khác nhau bằng thuật toán lấy câu hỏi từ ngân hàng lớn (DEC-010, DEFERRED).
- Chia sẻ/cộng tác ngân hàng câu hỏi giữa các giảng viên (DEC-020, NOT INCLUDED).
- Website quản trị, Android/iOS, AI model tự huấn luyện, OCR PDF scan, RAG/embedding hoặc trích dẫn trang chính xác mặc định.
- Đồng bộ offline toàn bộ tài liệu, chat và thư viện; giám sát màn hình/keyboard; các cơ chế phát hiện gian lận ngoài phạm vi ứng dụng.

## H. Các rủi ro còn lại KHÔNG phải blocker nghiệp vụ
| Mã | Rủi ro | Cách khống chế khi thiết kế/triển khai |
|---|---|---|
| R-01 | TEACHER tự đăng ký có thể giả mạo | Giữ đúng DEC-026; phân quyền tài nguyên, minh bạch hạn chế; nếu muốn xác minh, cần Change Request. |
| R-02 | Bài offline bị can thiệp/tua đồng hồ | Không tin timestamp client; bài đến muộn chờ duyệt, có audit; không quảng cáo chống gian lận tuyệt đối. |
| R-03 | Auto-submit không thể gửi khi offline/app đã tắt | Lưu nháp và ý định nộp; khi chạy lại/reconnect tiến hành sync, chỉ xác nhận sau ACK; kiểm thử mất điện/đóng app. |
| R-04 | Máy cũ bị thu hồi vẫn giữ bản nháp | Server chặn mọi dữ liệu từ binding cũ; client mới chỉ khôi phục phần server có. |
| R-05 | Quyền xem dữ liệu của người đã rời lớp | Giữ kết quả lịch sử theo bài đã giao; thống kê tự học sau khi rời lớp không được suy rộng, cần áp dụng quy tắc quyền hiện hành nhất quán. |
| R-06 | Server TCP demo bị nhầm với online Internet | Ghi chú/kiểm thử riêng; không public TCP demo. |

## I. DESIGN OPEN — công việc thiết kế, không mở thêm vòng yêu cầu
- Cách lưu an toàn session/binding và draft cục bộ; vòng đời dọn dẹp bản nháp.
- ERD/table mapping, cơ chế snapshot, transaction và versioning; request idempotency.
- Tiêu chí phân tích thống kê tự học, đơn vị thời gian và chính sách dữ liệu.
- UI flow, chi tiết thông báo lỗi, quyền hiển thị điểm/đáp án theo chính sách đã chốt.
- Chi tiết mô hình mã lớp, thành viên thuộc nhiều lớp, quản lý giáo viên/lớp, loại file DOCX và bảo vệ khỏi input quá lớn.
- Triển khai HTTPS/WSS, secrets, deployment và test trên môi trường cụ thể.

Các vấn đề trên được xử lý bằng **quyết định thiết kế nội bộ và kiểm thử**, miễn không làm thay đổi phạm vi/chính sách đã ACCEPTED; nếu cần thay đổi nghiệp vụ, lập Change Request.

## J. Chấp nhận baseline và bước kế tiếp
- `REQUIREMENTS FROZEN` cho **MVP v1.0**: Có thể bắt đầu ERD, state machine, hợp đồng API và thiết kế JavaFX.
- **Không có blocker nghiệp vụ mới** bắt buộc phải hỏi người dùng trước khi thiết kế. Rủi ro còn lại được ghi riêng, không che giấu.
- Mọi thay đổi chức năng mới/điều chỉnh DEC cần: `CR-ID`, lý do, tác động (API/DB/UI/bảo mật/tiến độ), quyết định người dùng.
- Trình tự tiếp theo: thiết kế ERD → state machine attempt/assignment → API & DTO → skeleton Spring Boot/JavaFX → tích hợp AI/tài liệu → kiểm thử.
