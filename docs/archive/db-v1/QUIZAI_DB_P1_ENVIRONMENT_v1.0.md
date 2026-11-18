# TASK-003 / DB-05 — P1 CONFIG-ONLY: cấu hình môi trường

Phạm vi được chủ dự án cấp phép: dependency JDBC/Flyway/PostgreSQL và cấu hình
Server, resolve dependency và `:quizai-server:compileJava`. P1 không cấp quyền
khởi động ứng dụng/test context, kết nối PostgreSQL, tạo schema/migration hoặc
thực hiện P2. ERD v1.0 giữ nguyên SHA-256
`5ff37a672d9a76ce4092301f53064a850304fed0ace74d8f71fa9db766aa31ab`.

## Dependency và ranh giới

Chỉ `../../../quizai-server/build.gradle` thêm:

- `org.springframework.boot:spring-boot-starter-jdbc`.
- `org.springframework.boot:spring-boot-starter-flyway` (starter của Boot 4).
- Runtime `org.postgresql:postgresql`.
- Runtime `org.flywaydb:flyway-database-postgresql`.

Phiên bản do BOM Spring Boot 4.0.9 hiện hữu quản lý; không override phiên bản
riêng, không thêm JPA, Hibernate, embedded database hoặc Gradle Flyway plugin.
Client/Admin/Common/TCP và root Gradle/Wrapper không thay đổi.

## Cách cấu hình hoạt động

| Cấu hình | Datasource | Migration / schema initialization |
|---|---|---|
| Mặc định, không chọn profile | `DataSourceAutoConfiguration` bị exclude, không tạo datasource | Flyway auto-configuration bị exclude, `enabled=false`; SQL init `never` |
| `dev` | Cho phép cấu hình datasource từ biến DEV bắt buộc | Giữ nguyên Flyway exclusion và các khóa an toàn của cấu hình chung |
| `test` trên test classpath | Cho phép cấu hình datasource từ biến TEST bắt buộc | Giữ nguyên Flyway exclusion và các khóa an toàn của cấu hình chung |

Tên class sử dụng đúng Boot 4:
`org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration` và
`org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration`.
Danh sách `spring.autoconfigure.exclude` ở profile **thay thế** danh sách mặc
định: DEV/TEST bỏ exclusion datasource nhưng vẫn exclude Flyway. Profile khác
không có file override sẽ kế thừa chế độ mặc định không datasource.

`spring.datasource.embedded-database-connection=none` chặn lựa chọn DB nhúng.
`spring.flyway.clean-disabled=true`, `baseline-on-migrate=false`,
`spring.sql.init.mode=never`, `spring.jpa.generate-ddl=false` và
`spring.jpa.hibernate.ddl-auto=none` là các khóa phòng vệ. Hai khóa JPA chưa
thực thi vì P1 không có JPA/Hibernate; không phải bằng chứng validate 37 bảng.

Không tự chọn `dev` hoặc `test`. `application-test.yml` chỉ ở
`../../../quizai-server/src/test/resources`, không đóng gói vào runtime chính, và không
tự kích hoạt profile `test`. Khi được cấp phép kiểm thử ở giai đoạn sau, phải
chọn profile `test` rõ ràng; không kết hợp `dev,test`.

## Biến môi trường

| Mục đích | DEV | TEST |
|---|---|---|
| JDBC URL đầy đủ | `QUIZAI_DEV_DB_URL` | `QUIZAI_TEST_DB_URL` |
| Tài khoản DB | `QUIZAI_DEV_DB_USERNAME` | `QUIZAI_TEST_DB_USERNAME` |
| Mật khẩu DB | `QUIZAI_DEV_DB_PASSWORD` | `QUIZAI_TEST_DB_PASSWORD` |

URL phải dùng giao thức JDBC PostgreSQL, xác định rõ host/port/tên database,
không nhúng user/password vào URL. DEV và TEST phải là database và bộ credentials
khác nhau do chủ dự án cung cấp; tài khoản không dùng quyền superuser.
Các template chỉ đọc nhóm biến tương ứng, không fallback sang nhóm còn lại,
không có URL, tên database, user hoặc mật khẩu mặc định. Không điền giá trị rỗng.

Không commit secrets, file `.env`, hoặc dùng các override chung
`SPRING_DATASOURCE_*`, `SPRING_AUTOCONFIGURE_EXCLUDE`, `SPRING_FLYWAY_*`,
`SPRING_SQL_INIT_*`, `SPRING_JPA_*` để vượt cấu hình đã review. Spring ưu tiên
environment/CLI hơn YAML; các khóa YAML không phải ranh giới bảo mật trước
override cố ý. Việc kiểm tra URL/credentials và xác nhận DEV/TEST thật sự tách
biệt phải thực hiện trong phiên runtime được cấp phép riêng.

DEV/TEST là template fail-closed theo nghĩa không có fallback DB/credentials;
thiếu/sai cấu hình không được chuyển sang môi trường khác. P1 chưa kiểm chứng
runtime fail-fast của từng biến thiếu/rỗng. Kích hoạt profile rồi khởi động có
thể tạo datasource/kết nối DB; **không chạy startup hoặc test trong P1 này**.

## Kiểm tra được phép trong P1

Dùng JDK 21 đã cài (đặt `JAVA_HOME` trong phiên terminal nếu PATH đang là JDK
khác), giữ nguyên Gradle Wrapper. Các lệnh sau chỉ resolve dependency/compile,
không chạy `main`, Spring context, tests hoặc Flyway:

```powershell
.\gradlew.bat :quizai-server:dependencies --configuration runtimeClasspath --no-daemon --console=plain
.\gradlew.bat :quizai-server:compileJava --no-daemon --console=plain
```

`compileJava` chỉ kiểm tra Java và compile classpath; không chứng minh YAML,
datasource, PostgreSQL, migration hoặc startup hoạt động. Báo cáo dependency
cần được kiểm tra không có `FAILED`/dependency chưa resolve, không chỉ dựa vào
exit code của task `dependencies`.

P1 dừng sau diff/config review và compile. Mọi migration P2-A, các batch P2
tiếp theo, JPA P3 và runtime/DB tests cần lệnh riêng. Sáu điểm kỹ thuật P0B
vẫn giữ nguyên để review tại P2; không sửa ERD hoặc baseline trong P1.

## Nguồn đối chiếu kỹ thuật

- [Spring Boot 4.0 — Auto-configuration](https://docs.spring.io/spring-boot/4.0/reference/using/auto-configuration.html).
- [Spring Boot 4.0 — SQL databases](https://docs.spring.io/spring-boot/4.0/reference/data/sql.html).
- [Spring Boot — Database initialization](https://docs.spring.io/spring-boot/how-to/data-initialization.html).
- Approval Record, phased handoff và `QUIZAI_TASK003_P0B_SOURCE_REPORT_v1.0.md` trong `docs/`.
