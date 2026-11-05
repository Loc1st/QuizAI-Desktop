# TASK-001 — Bàn giao nền tảng dự án

## Kết quả

Đã tạo năm module Gradle, hai ứng dụng JavaFX FXML/CSS và Spring Boot Server tối thiểu. Đã đọc đầy đủ hai tài liệu yêu cầu; không có AGENTS.md trước task nên đã tạo theo yêu cầu. Không sửa Decision Log hoặc Requirements Baseline. Gradle Wrapper 9.8.0 được giữ nguyên.

## Kiểm tra thực tế

| Kiểm tra | Kết quả |
|---|---|
| JDK dùng kiểm tra | Eclipse Temurin 21.0.10; compiler/toolchain/release 21 |
| Gradle projects | Thành công, root + đúng năm module |
| Build tất cả module | BUILD SUCCESSFUL; tạo desktop distributions và bootJar |
| installDist Client/Admin | Thành công |
| Gradle run Client + Admin, bản cuối | BUILD SUCCESSFUL, không có cảnh báo deprecation ở lần chạy cuối |
| GUI thực tế | Windows UI Automation tìm thấy đúng hai tiêu đề, nhãn, ô nhập và nút đóng |
| FXML/controller | Nút đóng ở cả hai ứng dụng hoạt động, tiến trình kết thúc |
| CSS | Đã xem ảnh chụp hai cửa sổ, xác minh dark theme, màu tím/xanh, typography, card và text field |
| Spring Boot | Started QuizAIServerApplication; Tomcat localhost:8080, GET / trả 404 vì chưa có API |
| Kết thúc Server | Chủ động dừng tiến trình sau kiểm tra; bootRun báo exit -1 do dừng cưỡng bức, không phải lỗi startup |
| Test | Các task test báo NO-SOURCE; chưa có unit test nghiệp vụ để chạy |
| git diff --check | Thành công; Git chỉ nhắc chuyển LF/CRLF theo cấu hình máy |

Ảnh kiểm tra lưu tại `build/client-foundation.png` và `build/admin-foundation.png`, log JavaFX tại `build/*-startup*.log`. Đây là artifact kiểm tra bị Git ignore.

Kiểm tra Gradle model/configuration đã thành công. IntelliJ tự cập nhật `.idea/gradle.xml` với đủ năm module trong cùng một project; `.idea/misc.xml` dùng language level JDK 21. Agent không thao tác nút Reload trực tiếp và không có log Gradle Sync từ IDE để xác nhận riêng thao tác đó. Các thay đổi IDE có sẵn/tự sinh được giữ nguyên.

## Phiên bản chính

- Java: 21; Gradle Wrapper: 9.8.0.
- JavaFX controls/FXML: 21.0.9; OpenJFX Gradle plugin: 0.1.0.
- Spring Boot và webmvc starter BOM: 4.0.9.
- JUnit Jupiter BOM khai báo dùng chung: 6.0.0.
- Backend chỉ dùng Boot BOM + webmvc starter và Common, chưa có JPA/Security/AI/DB driver.

Xem nguồn tương thích và hướng dẫn chạy trong [README](../README.md).

## File chỉnh sửa và thư mục cũ

- `build.gradle`: root aggregation, Java 21 cho module, Maven Central, cấu hình JUnit và resources.
- `settings.gradle`: khai báo đủ năm module.
- `src/` tại root đã kiểm tra không có file, kể cả file ẩn; chỉ xóa cây thư mục rỗng, không xóa/di chuyển mã nguồn thực tế.
- Không chỉnh sửa Wrapper, tài liệu yêu cầu, .gitignore hay tự tạo IntelliJ project riêng.

## File tạo mới — danh sách đầy đủ

- `AGENTS.md`
- `README.md`
- `docs/TASK-001_REPORT.md`
- `docs/ui-references/README.md`
- `quizai-admin/build.gradle`
- `quizai-admin/src/main/java/com/quizai/admin/QuizAIAdminApplication.java`
- `quizai-admin/src/main/java/com/quizai/admin/config/.gitkeep`
- `quizai-admin/src/main/java/com/quizai/admin/network/.gitkeep`
- `quizai-admin/src/main/java/com/quizai/admin/service/.gitkeep`
- `quizai-admin/src/main/java/com/quizai/admin/ui/components/.gitkeep`
- `quizai-admin/src/main/java/com/quizai/admin/ui/controllers/FoundationController.java`
- `quizai-admin/src/main/java/com/quizai/admin/ui/navigation/ViewLoader.java`
- `quizai-admin/src/main/java/module-info.java`
- `quizai-admin/src/main/resources/css/theme.css`
- `quizai-admin/src/main/resources/fxml/foundation.fxml`
- `quizai-admin/src/main/resources/icons/.gitkeep`
- `quizai-admin/src/main/resources/images/.gitkeep`
- `quizai-admin/src/test/java/.gitkeep`
- `quizai-admin/src/test/resources/.gitkeep`
- `quizai-client/build.gradle`
- `quizai-client/src/main/java/com/quizai/client/QuizAIClientApplication.java`
- `quizai-client/src/main/java/com/quizai/client/config/.gitkeep`
- `quizai-client/src/main/java/com/quizai/client/network/.gitkeep`
- `quizai-client/src/main/java/com/quizai/client/service/.gitkeep`
- `quizai-client/src/main/java/com/quizai/client/ui/components/.gitkeep`
- `quizai-client/src/main/java/com/quizai/client/ui/controllers/FoundationController.java`
- `quizai-client/src/main/java/com/quizai/client/ui/navigation/ViewLoader.java`
- `quizai-client/src/main/java/com/quizai/client/util/.gitkeep`
- `quizai-client/src/main/java/module-info.java`
- `quizai-client/src/main/resources/css/theme.css`
- `quizai-client/src/main/resources/fxml/foundation.fxml`
- `quizai-client/src/main/resources/icons/.gitkeep`
- `quizai-client/src/main/resources/images/.gitkeep`
- `quizai-client/src/test/java/.gitkeep`
- `quizai-client/src/test/resources/.gitkeep`
- `quizai-common/build.gradle`
- `quizai-common/src/main/java/com/quizai/common/dto/.gitkeep`
- `quizai-common/src/main/java/com/quizai/common/enums/.gitkeep`
- `quizai-common/src/main/resources/.gitkeep`
- `quizai-common/src/test/java/.gitkeep`
- `quizai-common/src/test/resources/.gitkeep`
- `quizai-server/build.gradle`
- `quizai-server/src/main/java/com/quizai/server/QuizAIServerApplication.java`
- `quizai-server/src/main/java/com/quizai/server/ai/.gitkeep`
- `quizai-server/src/main/java/com/quizai/server/config/.gitkeep`
- `quizai-server/src/main/java/com/quizai/server/controller/.gitkeep`
- `quizai-server/src/main/java/com/quizai/server/dto/.gitkeep`
- `quizai-server/src/main/java/com/quizai/server/entity/.gitkeep`
- `quizai-server/src/main/java/com/quizai/server/repository/.gitkeep`
- `quizai-server/src/main/java/com/quizai/server/security/.gitkeep`
- `quizai-server/src/main/java/com/quizai/server/service/.gitkeep`
- `quizai-server/src/main/java/com/quizai/server/websocket/.gitkeep`
- `quizai-server/src/main/resources/application.yml`
- `quizai-server/src/main/resources/db/migration/.gitkeep`
- `quizai-server/src/test/java/.gitkeep`
- `quizai-server/src/test/resources/.gitkeep`
- `quizai-tcp-demo/build.gradle`
- `quizai-tcp-demo/src/main/java/com/quizai/tcpdemo/client/.gitkeep`
- `quizai-tcp-demo/src/main/java/com/quizai/tcpdemo/protocol/.gitkeep`
- `quizai-tcp-demo/src/main/java/com/quizai/tcpdemo/server/.gitkeep`
- `quizai-tcp-demo/src/main/resources/.gitkeep`
- `quizai-tcp-demo/src/test/java/.gitkeep`
- `quizai-tcp-demo/src/test/resources/.gitkeep`

Các `.gitkeep` chỉ giữ thư mục chuẩn chưa dùng và được loại khỏi resources đóng gói. Không tạo Java class trống.

## Cây source thực tế

Không liệt kê cache, .git/.idea và build output trong cây dưới đây.

```text
QuizAI-Desktop/
├── AGENTS.md
├── README.md
├── build.gradle
├── docs
│   ├── PROJECT_DECISIONS_v7.md
│   ├── REQUIREMENTS_BASELINE_v1.0.md
│   ├── TASK-001_REPORT.md
│   └── ui-references
│       └── README.md
├── gradle
│   └── wrapper
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── gradlew
├── gradlew.bat
├── quizai-admin
│   ├── build.gradle
│   └── src
│       ├── main
│       │   ├── java
│       │   │   ├── com
│       │   │   │   └── quizai
│       │   │   │       └── admin
│       │   │   │           ├── QuizAIAdminApplication.java
│       │   │   │           ├── config
│       │   │   │           │   └── .gitkeep
│       │   │   │           ├── network
│       │   │   │           │   └── .gitkeep
│       │   │   │           ├── service
│       │   │   │           │   └── .gitkeep
│       │   │   │           └── ui
│       │   │   │               ├── components
│       │   │   │               │   └── .gitkeep
│       │   │   │               ├── controllers
│       │   │   │               │   └── FoundationController.java
│       │   │   │               └── navigation
│       │   │   │                   └── ViewLoader.java
│       │   │   └── module-info.java
│       │   └── resources
│       │       ├── css
│       │       │   └── theme.css
│       │       ├── fxml
│       │       │   └── foundation.fxml
│       │       ├── icons
│       │       │   └── .gitkeep
│       │       └── images
│       │           └── .gitkeep
│       └── test
│           ├── java
│           │   └── .gitkeep
│           └── resources
│               └── .gitkeep
├── quizai-client
│   ├── build.gradle
│   └── src
│       ├── main
│       │   ├── java
│       │   │   ├── com
│       │   │   │   └── quizai
│       │   │   │       └── client
│       │   │   │           ├── QuizAIClientApplication.java
│       │   │   │           ├── config
│       │   │   │           │   └── .gitkeep
│       │   │   │           ├── network
│       │   │   │           │   └── .gitkeep
│       │   │   │           ├── service
│       │   │   │           │   └── .gitkeep
│       │   │   │           ├── ui
│       │   │   │           │   ├── components
│       │   │   │           │   │   └── .gitkeep
│       │   │   │           │   ├── controllers
│       │   │   │           │   │   └── FoundationController.java
│       │   │   │           │   └── navigation
│       │   │   │           │       └── ViewLoader.java
│       │   │   │           └── util
│       │   │   │               └── .gitkeep
│       │   │   └── module-info.java
│       │   └── resources
│       │       ├── css
│       │       │   └── theme.css
│       │       ├── fxml
│       │       │   └── foundation.fxml
│       │       ├── icons
│       │       │   └── .gitkeep
│       │       └── images
│       │           └── .gitkeep
│       └── test
│           ├── java
│           │   └── .gitkeep
│           └── resources
│               └── .gitkeep
├── quizai-common
│   ├── build.gradle
│   └── src
│       ├── main
│       │   ├── java
│       │   │   └── com
│       │   │       └── quizai
│       │   │           └── common
│       │   │               ├── dto
│       │   │               │   └── .gitkeep
│       │   │               └── enums
│       │   │                   └── .gitkeep
│       │   └── resources
│       │       └── .gitkeep
│       └── test
│           ├── java
│           │   └── .gitkeep
│           └── resources
│               └── .gitkeep
├── quizai-server
│   ├── build.gradle
│   └── src
│       ├── main
│       │   ├── java
│       │   │   └── com
│       │   │       └── quizai
│       │   │           └── server
│       │   │               ├── QuizAIServerApplication.java
│       │   │               ├── ai
│       │   │               │   └── .gitkeep
│       │   │               ├── config
│       │   │               │   └── .gitkeep
│       │   │               ├── controller
│       │   │               │   └── .gitkeep
│       │   │               ├── dto
│       │   │               │   └── .gitkeep
│       │   │               ├── entity
│       │   │               │   └── .gitkeep
│       │   │               ├── repository
│       │   │               │   └── .gitkeep
│       │   │               ├── security
│       │   │               │   └── .gitkeep
│       │   │               ├── service
│       │   │               │   └── .gitkeep
│       │   │               └── websocket
│       │   │                   └── .gitkeep
│       │   └── resources
│       │       ├── application.yml
│       │       └── db
│       │           └── migration
│       │               └── .gitkeep
│       └── test
│           ├── java
│           │   └── .gitkeep
│           └── resources
│               └── .gitkeep
├── quizai-tcp-demo
│   ├── build.gradle
│   └── src
│       ├── main
│       │   ├── java
│       │   │   └── com
│       │   │       └── quizai
│       │   │           └── tcpdemo
│       │   │               ├── client
│       │   │               │   └── .gitkeep
│       │   │               ├── protocol
│       │   │               │   └── .gitkeep
│       │   │               └── server
│       │   │                   └── .gitkeep
│       │   └── resources
│       │       └── .gitkeep
│       └── test
│           ├── java
│           │   └── .gitkeep
│           └── resources
│               └── .gitkeep
└── settings.gradle
```

## Cố ý chưa triển khai

Database/schema/migration SQL, login/register, API nghiệp vụ, Gemini/PDF extraction, xác thực, WebSocket/TCP Socket, DTO/entity nghiệp vụ, dashboard, light theme, điều hướng màn hình nghiệp vụ và bộ cài Windows jpackage chưa thuộc TASK-001. Thư mục db/migration chỉ chứa .gitkeep; Common không có kiểu dữ liệu suy đoán. Chưa chuyển sang TASK-002.

Chưa ghi nhận lỗi mã nguồn hoặc dependency còn tồn tại trong phạm vi kiểm tra. Việc xác nhận thao tác Gradle Sync trực tiếp trong IDE là giới hạn kiểm tra nêu trên.
