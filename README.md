# QuizAI Desktop

Năm module Gradle, hai ứng dụng JavaFX độc lập và Spring Boot Server tối thiểu. TASK-002 bổ sung UI demo Light Modern cho sinh viên và giảng viên; chưa triển khai nghiệp vụ QuizAI.

## Yêu cầu môi trường

- JDK 21; đặt `JAVA_HOME` tới JDK 21 khi chạy từ terminal.
- Dùng Gradle Wrapper 9.8.0 đã có trong repository; không cần cài Gradle riêng.
- JavaFX cần phiên desktop có màn hình đồ họa.

Trong IntelliJ IDEA, mở thư mục gốc (hoặc `settings.gradle`), chọn Project SDK **21**, Gradle JVM **21**, dùng Wrapper rồi **Reload All Gradle Projects**. Chỉ một IntelliJ project chứa năm Gradle module. Không commit đường dẫn JDK riêng của máy vào cấu hình build.

## Build và chạy (PowerShell)

```powershell
# Thay bằng đường dẫn JDK 21 trên máy nếu JAVA_HOME chưa trỏ đúng.
$env:JAVA_HOME = 'C:\path\to\jdk-21'

.\gradlew.bat projects
.\gradlew.bat build
.\gradlew.bat test

# Mỗi ứng dụng chạy trong một terminal riêng; đóng cửa sổ JavaFX để kết thúc.
.\gradlew.bat :quizai-client:run
.\gradlew.bat :quizai-admin:run
.\gradlew.bat :quizai-server:bootRun
```

Server phát triển chỉ bind `127.0.0.1:8080`, chưa có API nên GET `/` trả 404. HTTP localhost ở đây chỉ phục vụ nền tảng phát triển; triển khai Internet HTTPS/WSS sẽ thuộc task sau. Chưa kết nối PostgreSQL, cấu hình xác thực hay tích hợp AI.

Build tạo executable Spring Boot jar tại `quizai-server/build/libs/quizai-server-0.1.0-SNAPSHOT.jar`, chạy độc lập bằng `java -jar` với JDK 21. Dừng server bằng Ctrl+C.

Để tạo thư mục phân phối desktop:

```powershell
.\gradlew.bat :quizai-client:installDist :quizai-admin:installDist
.\quizai-client\build\install\quizai-client\bin\quizai-client.bat
.\quizai-admin\build\install\quizai-admin\bin\quizai-admin.bat
```

Các bản phân phối này vẫn cần JDK 21, chưa phải bộ cài Windows kèm runtime. TCP Demo và Common hiện là module Java nền tảng, chưa có entry point/chức năng socket hoặc DTO nghiệp vụ.

## Phiên bản

| Thành phần | Phiên bản |
|---|---|
| Java toolchain / compiler release | 21 |
| Gradle Wrapper | 9.8.0 (giữ nguyên) |
| JavaFX controls + FXML | 21.0.9 |
| OpenJFX Gradle plugin | 0.1.0 |
| Spring Boot / webmvc starter BOM | 4.0.9 |
| JUnit Jupiter BOM khai báo chung | 6.0.0 |

Spring Boot quản lý dependency bắc cầu bằng BOM của chính Boot; chưa thêm JPA, PostgreSQL driver, Spring Security, Gemini, PDFBox hay WebSocket vì TASK-001 chưa sử dụng chúng.

Tham khảo kỹ thuật: [Spring Boot 4.0 hỗ trợ Java và Gradle](https://docs.spring.io/spring-boot/4.0/system-requirements.html), [OpenJFX Gradle/FXML](https://openjfx.io/openjfx-docs/), [OpenJFX Gradle plugin](https://plugins.gradle.org/plugin/org.openjfx.javafxplugin/0.1.0).

## Nền tảng UI

Client/Admin có `module-info.java`, entry point và `ui/navigation/ViewLoader`. Mỗi ứng dụng giữ một Scene ổn định; `shell.fxml` chứa header, sidebar và vùng thay đổi nội dung. `overview.fxml` trình bày Tổng quan, `placeholder.fxml` dùng cho các mục chưa triển khai. FXML controllers và custom components được mở riêng cho `javafx.fxml`.

UI sử dụng font hệ thống Windows `Segoe UI`, theme class `theme-light` và looked-up colors `-qa-*`. Nền trắng/xám nhạt, điểm nhấn tím, card bo góc, icon hình học vẽ bằng SVGPath và JavaFX Charts/TableView có sẵn. CSS có trạng thái selected/hover/focus/disabled; không dùng thư viện UI, WebView hoặc dữ liệu trực tuyến.

Scene mặc định 1280 × 800; cửa sổ tối thiểu 1024 × 700. Nội dung dài cuộn trong ScrollPane, sidebar thu gọn khoảng cách ở chiều cao nhỏ. Hai module giữ cùng tên token/style; Common không phụ thuộc JavaFX. Các giá trị trong `ui/demo/DemoData` là fixture trình bày cố định; không có service backend hoặc thao tác lưu/nộp thật.

Xem [báo cáo TASK-002](docs/TASK-002_REPORT.md) và [ảnh giao diện thực tế](docs/ui-previews/). Các mục ngoài Tổng quan hiện ghi rõ **Chưa triển khai**.

Đặt mẫu UI tương lai trong [docs/ui-references](docs/ui-references/README.md). Các mẫu không tự mở rộng yêu cầu nghiệp vụ.

## Yêu cầu chính thức

Đọc [Decision Log](docs/PROJECT_DECISIONS_v7.md), [Requirements Baseline](docs/REQUIREMENTS_BASELINE_v1.0.md) và [AGENTS.md](AGENTS.md) trước khi thay đổi. TASK-001 không thay đổi hai tài liệu yêu cầu.
