package com.quizai.client.ui.navigation;

/** Only the MVP destinations permitted for this desktop demo. */
public enum Screen {
    OVERVIEW("Tổng quan", "home", "Góc học tập của bạn"),
    PRACTICE("Tự học / Luyện tập", "book", "Luyện tập bộ đề riêng, lịch sử và giải thích AI sau khi hoàn thành."),
    QUIZZES("Bộ đề cá nhân", "layers", "Bộ đề riêng được tạo từ tài liệu và dùng cho tự học."),
    DOCUMENTS("Tài liệu học tập", "file", "Nhập PDF, DOCX, TXT hoặc dán văn bản để tạo câu hỏi qua máy chủ."),
    TUTOR("Gia sư AI", "spark", "Hỏi đáp riêng dựa trên PDF đã nhập; không khả dụng khi đang thi."),
    EXAMS("Bài kiểm tra được giao", "clipboard", "Làm bài online, lưu từng câu có xác nhận và nộp bài qua máy chủ."),
    CLASSES("Lớp học", "users", "Tham gia bằng mã và rời lớp, giữ lịch sử đã có."),
    RESULTS("Kết quả / Thống kê", "chart", "Lịch sử lượt làm và kết quả theo lịch công bố của giảng viên.");
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
