package com.quizai.admin.ui.navigation;

/** Only the MVP destinations permitted for this desktop demo. */
public enum Screen {
    OVERVIEW("Tổng quan", "home", "Không gian giảng dạy của bạn"),
    CLASSES("Quản lý lớp học", "users", "Quản lý các lớp do bạn phụ trách. Chưa tạo lớp, đổi mã hoặc thay đổi thành viên."),
    BANK("Ngân hàng câu hỏi", "layers", "Ngân hàng riêng theo môn học, chủ đề và độ khó. Chưa tạo hoặc lưu câu hỏi; không chia sẻ giữa giảng viên."),
    REVIEW("Duyệt câu hỏi AI", "spark", "Rà soát, chỉnh sửa và duyệt từng câu trước khi giao thi. Chưa gọi AI hoặc ghi nhận quyết định duyệt."),
    ASSIGN("Tạo và giao bài kiểm tra", "clipboard", "Cấu hình giờ mở, hạn nộp, thời lượng và số lượt. Chưa tạo bài giao hoặc snapshot."),
    RESULTS("Kết quả kiểm tra", "check", "Kết quả chỉ thuộc các bài do bạn giao. Chưa tải dữ liệu hoặc thực hiện chấm điểm."),
    LATE("Duyệt bài nộp muộn", "clock", "Bài muộn chờ giảng viên xét duyệt; duyệt hoặc từ chối đều cần lý do. Chưa ghi nhận quyết định."),
    SUMMARY("Thống kê tổng hợp", "chart", "Chỉ xem thống kê tổng hợp của sinh viên thuộc lớp phụ trách, không xem chat, tài liệu hay bộ đề riêng.");
    private final String title;
    private final String icon;
    private final String description;

    Screen(String title, String icon, String description) {
        this.title = title;
        this.icon = icon;
        this.description = description;
    }

    public String title() { return title; }
    public String icon() { return icon; }
    public String description() { return description; }
}
