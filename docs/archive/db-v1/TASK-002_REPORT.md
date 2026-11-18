# TASK-002 — Giao diện JavaFX theo ảnh tham khảo

## 1. Phạm vi và nguồn đã đọc

Đã đọc đầy đủ `../../../AGENTS.md`, `../../PROJECT_DECISIONS_v7.md`, `../../REQUIREMENTS_BASELINE_v1.0.md` và `TASK-001_REPORT.md` trước khi chỉnh UI. Đã mở nội dung thực tế của cả bốn ảnh bằng công cụ xem ảnh; không suy đoán từ tên file.

Task này triển khai **UI demo Light Modern** cho hai module desktop hiện có. Không thay đổi Decision Log, Requirements Baseline, kiến trúc multi-module, Gradle/dependency hoặc backend.

## 2. Danh sách ảnh và phân tích trực quan

### R1 — Chat AI tối

- File: [1791813452318_553694988727219104_725673515670291229_1db19f2db11488b5b3552046eb4b857b.jpg](../../ui-references/1791813452318_553694988727219104_725673515670291229_1db19f2db11488b5b3552046eb4b857b.jpg)
- Đọc thành công; kích thước **1130 × 824**.
- Phù hợp nhất với Gia sư AI của Client; chỉ tham khảo ngôn ngữ thiết kế cho Admin.
- Tổng thể có topbar logo Vortex, thanh icon rất hẹp bên trái, cột History và vùng New Chat lớn bên phải. Lịch sử chia Today/Yesterday, các mục dạng pill/card; hội thoại có avatar, bong bóng tin nhắn và ô nhập phía dưới.
- Nền navy/đen, tím đậm; chữ trắng/xám, điểm nhấn tím. Typography sans-serif, tên người gửi đậm hơn nội dung. Các khối bo góc lớn, khoảng cách giữa cột khá rộng; cột lịch sử khoảng một phần tư chiều rộng, hội thoại chiếm phần lớn còn lại.
- JavaFX có thể dựng bằng BorderPane/HBox, ListView, VBox, ScrollPane, TextArea và CSS. Không cần WebView.
- Chỉ lấy cảm hứng về phân vùng, nhịp khoảng cách và hình thức card. Không lấy Premium, ghi âm, âm thanh, tạo ảnh, tạo nhạc hoặc nội dung báo cáo kinh doanh thành chức năng QuizAI.
- TASK-002 giữ Gia sư AI ở placeholder rõ ràng, chưa dựng hội thoại hoặc phản hồi giả.

### R2 — Màn hình bài kiểm tra sáng

- File: [1791813477500_553694988727219104_725673515670291229_6b56acd22d2ce5574d8171f651a91b3d.jpg](../../ui-references/1791813477500_553694988727219104_725673515670291229_6b56acd22d2ce5574d8171f651a91b3d.jpg)
- Đọc thành công; kích thước **1137 × 740**.
- Phù hợp với Client, cụ thể khu vực bài kiểm tra.
- Khung trắng trên nền gradient xanh; breadcrumb phía trên. Cột trái là Exam Navigation với lưới 20 nút số câu và thời gian còn lại. Vùng phải có dải xanh navy hiển thị số câu/trạng thái, nội dung câu hỏi và các đáp án radio; footer có Previous/Next và chỉ số câu.
- Bảng màu trắng, navy, xanh lá. Chữ câu hỏi đậm, nội dung/đáp án phân cấp rõ. Cột điều hướng khoảng một phần tư; vùng câu hỏi khoảng hai phần ba. Dùng đường phân cách và khoảng trắng thay vì nhiều hiệu ứng.
- Có thể tái tạo bằng GridPane/TilePane, Label, ToggleGroup/RadioButton và BorderPane. Không cần layout tọa độ tuyệt đối.
- TASK-002 áp dụng sự tách biệt giữa luyện tập/thi chính thức và ngôn ngữ nhãn trạng thái. Không dựng hệ thống thi, đếm giờ, nộp bài, đánh dấu câu hỏi hay lấy nội dung FIFA làm nghiệp vụ.
- Bản Light Modern bỏ gradient nền; nhấn tím đồng nhất với toàn sản phẩm. Placeholder bài thi không có nút hỗ trợ AI trong nội dung.

### R3 — Tổng quan học tập tối

- File: [1791813656198_553694988727219104_725673515670291229_4eb1dcfa3816ae58f25bd1949dacafe3.jpg](../../ui-references/1791813656198_553694988727219104_725673515670291229_4eb1dcfa3816ae58f25bd1949dacafe3.jpg)
- Đọc thành công; kích thước **1061 × 771**.
- Phù hợp chủ yếu với Tổng quan Client.
- Sidebar icon ở trái, topbar Overview/tìm kiếm/thông tin người dùng. Nội dung chia ba nhóm cột: lịch ở trái; thống kê và Last lesson ở giữa; lời chào và AI Assistant ở phải.
- Nền đen/navy, điểm nhấn tím, card bo góc lớn. Có số liệu lớn, vòng tiến độ, thanh tiến độ, nút Continue/Join dạng pill và đồ họa nhân vật/quả cầu.
- JavaFX có thể dựng bố cục bằng GridPane/VBox, ProgressBar, chart/control có sẵn. Hình 3D chỉ nên xem là cảm hứng; không trích xuất hoặc sao chép tài nguyên từ ảnh.
- Bản Client giữ thứ bậc thống kê → nhịp học → tiến độ bộ đề → gia sư AI. Chuyển card tối thành trắng/tím nhạt; dùng BarChart và ProgressBar rõ ràng.
- Không đưa lịch hẹn, họp trực tuyến, điểm thưởng, hồ sơ khóa học hoặc ghi âm vào MVP.

### R4 — Tổng quan quản lý sáng

- File: [1791813672088_553694988727219104_725673515670291229_088edf6a8a20a09a19855ca57004e853.jpg](../../ui-references/1791813672088_553694988727219104_725673515670291229_088edf6a8a20a09a19855ca57004e853.jpg)
- Đọc thành công; kích thước **1058 × 765**.
- Phù hợp nhất với Admin; là nền tảng thị giác chung cho cả hai ứng dụng.
- Sidebar có logo, các nhóm menu và profile cuối cột; topbar lời chào. Content gồm hàng ba card thống kê, hai khối biểu đồ và bảng kết quả bên dưới.
- Nền trắng/xám rất nhạt, nhấn tím, bổ trợ teal/vàng; bảng có header nhạt, các hàng phân cách nhẹ. Typography sans-serif, số thống kê lớn, tiêu đề đậm và chú thích nhỏ.
- Sidebar khoảng 18% chiều rộng; nội dung dùng lưới đều, card bo góc vừa, khoảng cách nhỏ nhưng nhất quán.
- JavaFX TableView, AreaChart, GridPane, VBox và CSS tái tạo được các phần chính. Sidebar hiện dùng 238 px trong Scene 1280 px, gần tỷ lệ ảnh.
- Giữ layout quản lý sáng và nhịp card/bảng; không sao chép LMS, Certificates, Surveys, Settings, Invite Students hay Send certificates thành chức năng mới. Không xây dựng SUPERADMIN.
- Thay biểu đồ/phân tích ngoài yêu cầu bằng hoạt động bài nộp minh họa và bảng tổng hợp lớp thuộc giảng viên.

## 3. Thiết kế đã áp dụng

- Nền `#f7f8fc`, surface trắng, chữ `#202537`, chữ phụ `#788196`.
- Tím chính `#7955dc`, hover `#6844c7`, nền tím nhạt `#f0eafb`; teal/amber/blue chỉ bổ trợ cho trạng thái.
- Font Windows **Segoe UI**, hỗ trợ tiếng Việt. Tiêu đề trang 24 px, tiêu đề khu vực 16 px, nội dung 12–13 px, chú thích 11 px.
- Card bo góc 14 px, border sáng, bóng đổ rất nhẹ. Không thêm gradient trang trí hoặc ảnh ngoài.
- Header giữ nguyên khi cuộn; sidebar có selected/hover/focus. Button và text field có CSS hover/focus/disabled.
- Scene mặc định **1280 × 800**; Stage tối thiểu **1024 × 700**. Trên Windows kiểm tra được Scene tối thiểu **1008 × 661**, phần chênh do khung cửa sổ hệ điều hành.
- GridPane chia đều card thống kê; hai vùng nội dung dùng tỷ lệ 60/40. TableView co giãn tất cả cột thay vì dồn thay đổi vào cột cuối.
- Sidebar chuyển sang khoảng cách gọn khi chiều cao dưới 740 px và ẩn khối lời nhắc trang trí; đủ cả tám nút tại kích thước tối thiểu. Profile và điều hướng vẫn giữ nguyên.
- Tất cả nội dung dài nằm trong ScrollPane. Không dùng fixed absolute positioning cho bố cục màn hình.

## 4. Client

Tổng quan gồm lời chào, ba MetricCard (bộ đề, câu đã luyện, tỷ lệ đúng), BarChart bảy ngày, card luyện tập tự do, tiến độ hai môn, card Gia sư AI và TableView bài được giao.

Tám mục điều hướng:
1. Tổng quan.
2. Tự học / Luyện tập.
3. Bộ đề cá nhân.
4. Tài liệu học tập.
5. Gia sư AI.
6. Bài kiểm tra được giao.
7. Lớp học.
8. Kết quả / Thống kê.

Bảy mục ngoài Tổng quan dùng placeholder với nội dung riêng, ghi rõ **Chưa triển khai** và có nút về Tổng quan. Khu vực bài được giao có chú giải bản nháp cục bộ, chờ đồng bộ/chưa xác nhận, ví dụ server xác nhận và bài muộn chờ xét duyệt. Nhãn server xác nhận chỉ là chú giải có ghi ví dụ, không phải thông báo kết quả từ backend.

Luyện tập tự do khác bài thi chính thức. Không có màn hình làm bài hoặc nút nộp/AI hỗ trợ bài thi trong task này.

## 5. Admin

Tổng quan gồm lời chào, bốn MetricCard (lớp, sinh viên, câu đã duyệt, bài kiểm tra), AreaChart bảy ngày, card dẫn tới khu vực giao bài, TableView bốn lớp và dải điều hướng tới duyệt câu hỏi/bài muộn.

Tám mục điều hướng:
1. Tổng quan.
2. Quản lý lớp học.
3. Ngân hàng câu hỏi.
4. Duyệt câu hỏi AI.
5. Tạo và giao bài kiểm tra.
6. Kết quả kiểm tra.
7. Duyệt bài nộp muộn.
8. Thống kê tổng hợp.

Bảy mục ngoài Tổng quan dùng placeholder rõ ràng. Duyệt bài muộn có chú giải chính sách, không có nút phê duyệt giả. Chỉ hiển thị số liệu tổng hợp và lớp do giảng viên mẫu phụ trách; không có nội dung chat, tài liệu riêng hoặc bộ đề riêng của sinh viên.

## 6. Tổ chức FXML, controller và component

Mỗi module giữ UI riêng, không thêm UI vào Common:

```text
src/main/java/com/quizai/<client|admin>/
├── QuizAI…Application.java
└── ui/
    ├── components/
    │   ├── LineIcon.java
    │   ├── MetricCard.java
    │   └── StatusBadge.java
    ├── controllers/
    │   ├── ShellController.java
    │   ├── SidebarController.java
    │   ├── OverviewController.java
    │   └── PlaceholderController.java
    ├── demo/DemoData.java
    └── navigation/
        ├── Screen.java
        └── ViewLoader.java

src/main/resources/
├── css/theme.css
└── fxml/
    ├── shell.fxml
    ├── sidebar.fxml
    ├── overview.fxml
    └── placeholder.fxml
```

- `shell.fxml`: một Scene ổn định, header dùng lại và content host.
- `sidebar.fxml`: khối điều hướng dùng lại trên mọi trang của cùng ứng dụng.
- `ShellController`: chuyển nội dung; controller không gọi network hoặc business service.
- `OverviewController`: cấp fixture cho Chart/TableView và chuyển màn hình.
- `DemoData`: record và danh sách cố định chỉ phục vụ trình bày, tách khỏi service tương lai.
- `LineIcon`: hình học SVGPath vẽ cục bộ; không font/icon library bên ngoài.
- `MetricCard`, `StatusBadge`: tái sử dụng trong UI. Hai module có cùng từ vựng CSS; một số padding điều chỉnh theo mật độ dữ liệu.
- `module-info.java`: mở controller và custom component cho FXML, không mở toàn bộ module.
- Đã bỏ màn hình foundation/nút đóng thử của TASK-001 vì được thay bằng shell thực tế.

## 7. File thay đổi

Danh sách UI đầy đủ, đường dẫn tính từ root:

- `../../../quizai-admin/src/main/java/com/quizai/admin/QuizAIAdminApplication.java`
- `../../../quizai-admin/src/main/java/com/quizai/admin/ui/components/LineIcon.java`
- `../../../quizai-admin/src/main/java/com/quizai/admin/ui/components/MetricCard.java`
- `../../../quizai-admin/src/main/java/com/quizai/admin/ui/components/StatusBadge.java`
- `quizai-admin/src/main/java/com/quizai/admin/ui/controllers/FoundationController.java (removed)`
- `../../../quizai-admin/src/main/java/com/quizai/admin/ui/controllers/OverviewController.java`
- `../../../quizai-admin/src/main/java/com/quizai/admin/ui/controllers/PlaceholderController.java`
- `../../../quizai-admin/src/main/java/com/quizai/admin/ui/controllers/ShellController.java`
- `../../../quizai-admin/src/main/java/com/quizai/admin/ui/controllers/SidebarController.java`
- `../../../quizai-admin/src/main/java/com/quizai/admin/ui/demo/DemoData.java`
- `../../../quizai-admin/src/main/java/com/quizai/admin/ui/navigation/Screen.java`
- `../../../quizai-admin/src/main/java/com/quizai/admin/ui/navigation/ViewLoader.java`
- `../../../quizai-admin/src/main/java/module-info.java`
- `../../../quizai-admin/src/main/resources/css/theme.css`
- `quizai-admin/src/main/resources/fxml/foundation.fxml (removed)`
- `../../../quizai-admin/src/main/resources/fxml/overview.fxml`
- `../../../quizai-admin/src/main/resources/fxml/placeholder.fxml`
- `../../../quizai-admin/src/main/resources/fxml/shell.fxml`
- `../../../quizai-admin/src/main/resources/fxml/sidebar.fxml`
- `../../../quizai-client/src/main/java/com/quizai/client/QuizAIClientApplication.java`
- `../../../quizai-client/src/main/java/com/quizai/client/ui/components/LineIcon.java`
- `../../../quizai-client/src/main/java/com/quizai/client/ui/components/MetricCard.java`
- `../../../quizai-client/src/main/java/com/quizai/client/ui/components/StatusBadge.java`
- `quizai-client/src/main/java/com/quizai/client/ui/controllers/FoundationController.java (removed)`
- `../../../quizai-client/src/main/java/com/quizai/client/ui/controllers/OverviewController.java`
- `../../../quizai-client/src/main/java/com/quizai/client/ui/controllers/PlaceholderController.java`
- `../../../quizai-client/src/main/java/com/quizai/client/ui/controllers/ShellController.java`
- `../../../quizai-client/src/main/java/com/quizai/client/ui/controllers/SidebarController.java`
- `../../../quizai-client/src/main/java/com/quizai/client/ui/demo/DemoData.java`
- `../../../quizai-client/src/main/java/com/quizai/client/ui/navigation/Screen.java`
- `../../../quizai-client/src/main/java/com/quizai/client/ui/navigation/ViewLoader.java`
- `../../../quizai-client/src/main/java/module-info.java`
- `../../../quizai-client/src/main/resources/css/theme.css`
- `quizai-client/src/main/resources/fxml/foundation.fxml (removed)`
- `../../../quizai-client/src/main/resources/fxml/overview.fxml`
- `../../../quizai-client/src/main/resources/fxml/placeholder.fxml`
- `../../../quizai-client/src/main/resources/fxml/shell.fxml`
- `../../../quizai-client/src/main/resources/fxml/sidebar.fxml`

Tài liệu/artifact thêm hoặc cập nhật:
- `../../../README.md`: cập nhật hướng dẫn UI hiện hành.
- `docs/TASK-002_REPORT.md`: báo cáo này.
- `../../ui-previews/client-overview.png`
- `docs/ui-previews/admin-overview.png`
- `docs/ui-previews/client-minimum.png`
- `../../ui-previews/admin-minimum.png`
- `../../ui-previews/client-minimum-bottom.png`
- `../../ui-previews/admin-minimum-bottom.png`
- `../../ui-previews/client-status-placeholder.png`
- `../../ui-previews/admin-status-placeholder.png`
- `../../ui-previews/client-window.png`
- `../../ui-previews/admin-window.png`

Harness `../../../build/UiSmoke.java` và `build/task002-*-smoke.log` chỉ là artifact kiểm tra tạm bị Git ignore, không đóng gói vào ứng dụng. Không thêm hoặc đổi dependency/Gradle, không sửa source Server/Common/TCP.

## 8. Kiểm tra thực tế

Môi trường: Windows 11, Eclipse Temurin **21.0.10**, Wrapper **9.8.0**, JavaFX **21.0.9**.

| Kiểm tra | Kết quả |
|---|---|
| Build toàn bộ project | `gradlew.bat build :quizai-client:installDist :quizai-admin:installDist --console=plain` — **BUILD SUCCESSFUL** |
| Gradle chạy hai ứng dụng | `gradlew.bat :quizai-client:run :quizai-admin:run --warning-mode all --console=plain` — **BUILD SUCCESSFUL**, không cảnh báo deprecation |
| Cửa sổ thật | Windows UI Automation xác nhận đúng hai tiêu đề, chụp màn hình rồi đóng sạch |
| FXML/CSS | Nạp được shell, sidebar, hai overview và placeholder của mọi route |
| Navigation | **16/16 route** qua kiểm tra; nút chọn đúng, nút về Tổng quan hoạt động, Scene giữ nguyên |
| Chart/TableView | Bảy điểm mỗi biểu đồ; ba dòng Client, bốn dòng Admin đúng fixture |
| Resize/overflow | Stage 1024 × 700: đủ tám menu, metric không tràn ngang, content vừa viewport, tổng chiều rộng cột bảng vừa TableView |
| Cuộn | Đã chụp phần cuối trang và xem đầy đủ bảng/trạng thái ở kích thước tối thiểu |
| Unit test Gradle | Các task `test` báo **NO-SOURCE**; không gọi smoke check này là JUnit test |
| Requirements | Hai tài liệu yêu cầu không đổi; không có code nghiệp vụ mới |

Các lỗi phát hiện đã sửa: FXML custom component thiếu quyền opens; hàng metric chưa chia đều; cột trạng thái Admin bị khuất khi thu nhỏ; sidebar thiếu chỗ tại chiều cao nhỏ. Harness cũng đã được sửa để đợi sự kiện resize thật của Windows trước khi kiểm tra, tránh dùng kích thước Scene cũ.

Đã mở và xem trực quan các snapshot của bản cuối, không chỉ dựa vào build. Không phát hiện lỗi FXML/CSS/điều hướng hoặc overflow trong hai kích thước đã kiểm tra. Chưa kiểm tra mọi cấu hình DPI, font fallback hoặc mọi kích thước màn hình Windows.

## 9. Ảnh giao diện và so sánh

- [Client — Tổng quan](../../ui-previews/client-overview.png)
- [Admin — Tổng quan](ui-previews/admin-overview.png)
- [Client — cửa sổ Windows chạy bởi Gradle](../../ui-previews/client-window.png)
- [Admin — cửa sổ Windows chạy bởi Gradle](../../ui-previews/admin-window.png)
- [Client — kích thước tối thiểu](ui-previews/client-minimum.png)
- [Admin — kích thước tối thiểu](../../ui-previews/admin-minimum.png)
- [Client — bảng ở cuối trang nhỏ](../../ui-previews/client-minimum-bottom.png)
- [Admin — bảng ở cuối trang nhỏ](../../ui-previews/admin-minimum-bottom.png)
- [Client — chú giải trạng thái](../../ui-previews/client-status-placeholder.png)
- [Admin — bài muộn chưa triển khai](../../ui-previews/admin-status-placeholder.png)

Các ảnh `*-window.png` chụp màn hình Windows thật. Các ảnh còn lại là snapshot Scene JavaFX đã mở trong Stage thật, bao gồm trạng thái sau resize/cuộn.

Khác ảnh tham khảo có chủ đích:
- R1/R3 tối được chuyển sang Light Modern theo yêu cầu, không giữ glow, glass hoặc hình 3D.
- Sidebar có chữ thay vì chỉ icon để các mục MVP dễ hiểu bằng tiếng Việt.
- R4 dùng chart và bảng theo lớp; bỏ chứng chỉ/khảo sát và chức năng không có trong baseline.
- Client dùng BarChart/ProgressBar thay vòng điểm thưởng; Admin dùng AreaChart minh họa thay các chỉ số phân tích chưa chốt.
- Trang dài có cuộn; không cố nhét mọi card vào một ảnh như mockup.
- R2 và R1 chưa được dựng thành màn thi/chat thật; các mục tương ứng vẫn là placeholder.
- Không bảo đảm khớp từng pixel do font/DPI và thanh tiêu đề Windows.

## 10. Chức năng chưa triển khai

Không có đăng nhập thật, API mới, PostgreSQL/schema, Gemini, trích xuất tài liệu, chấm điểm, thi thật, lưu nháp thật, đồng bộ offline, WebSocket/TCP, lưu dữ liệu hay quyết định xét duyệt. Không có chia sẻ ngân hàng, SUPERADMIN hoặc đọc nội dung riêng tư của sinh viên.

Các thao tác UI chỉ điều hướng; dữ liệu cố định được gắn nhãn minh họa. Không có thông báo đã nộp/lưu lên server hoặc AI xử lý thành công. Không chuyển sang TASK-003.
