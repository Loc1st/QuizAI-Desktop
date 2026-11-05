package com.quizai.client.ui.navigation;

/** Only the MVP destinations permitted for this desktop demo. */
public enum Screen {
    OVERVIEW("Tổng quan", "home", "Góc học tập của bạn"),
    PRACTICE("Tự học / Luyện tập", "book", "Luyện tập tự do, tách biệt với bài kiểm tra chính thức. Chưa triển khai tạo lượt luyện tập."),
    QUIZZES("Bộ đề cá nhân", "layers", "Bộ đề thuộc tài khoản của bạn. Chưa triển khai tạo, sửa hoặc lưu bộ đề."),
    DOCUMENTS("Tài liệu học tập", "file", "Nguồn tạo câu hỏi: PDF, DOCX, TXT và văn bản nhập trực tiếp. Chưa triển khai nhập hoặc xử lý tài liệu."),
    TUTOR("Gia sư AI", "spark", "Hỏi đáp học tập và ngữ cảnh PDF. Chưa kết nối AI; không có hội thoại hay phản hồi AI thật."),
    EXAMS("Bài kiểm tra được giao", "clipboard", "Bài kiểm tra chính thức được tách khỏi luyện tập. Chưa bắt đầu lượt thi, đếm giờ, nộp bài hoặc đồng bộ."),
    CLASSES("Lớp học", "users", "Tham gia lớp bằng mã. Chưa triển khai việc tham gia hay rời lớp."),
    RESULTS("Kết quả / Thống kê", "chart", "Kết quả cá nhân chỉ được công bố theo chính sách bài kiểm tra. Chưa tải hoặc tính điểm thực tế.");
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
