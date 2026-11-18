package com.quizai.admin.ui.navigation;

/** Only the MVP destinations permitted for this desktop demo. */
public enum Screen {
    OVERVIEW("Tổng quan", "home", "Không gian giảng dạy của bạn"),
    CLASSES("Quản lý lớp học", "users", "Tạo lớp, quản lý mã tham gia và xem thành viên của mình."),
    BANK("Ngân hàng câu hỏi", "layers", "Ngân hàng riêng, phiên bản câu hỏi và bộ đề tái sử dụng."),
    REVIEW("Duyệt câu hỏi AI", "spark", "Nhập tài liệu, tạo câu AI và duyệt từng câu trước khi giao thi."),
    ASSIGN("Tạo và giao bài kiểm tra", "clipboard", "Cấu hình thời gian, số lượt, người nhận và lịch công bố độc lập."),
    RESULTS("Kết quả kiểm tra", "check", "Lịch sử và điểm cao nhất của các bài do bạn giao."),
    LATE("Duyệt bài nộp muộn", "clock", "Được hoãn theo CR-001; bài thi dùng những câu đã được máy chủ xác nhận đúng hạn."),
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
