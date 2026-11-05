package com.quizai.admin.ui.demo;

import java.util.List;

/** Fixed, aggregate teaching fixtures; no private student content. */
public final class DemoData {
    private DemoData() { }
    public record ClassSummary(String name, String subject, String students, String assignment, String progress, String status, String tone) { }
    public static List<ClassSummary> classes() {
        return List.of(
            new ClassSummary("CNTT K23 · Nhóm 01", "Lập trình Java", "36", "Kiểm tra chương 3", "28 / 36", "Đang mở · Mẫu", "green"),
            new ClassSummary("CNTT K23 · Nhóm 02", "Cơ sở dữ liệu", "32", "Ôn tập chương 2", "24 / 32", "Đang mở · Mẫu", "green"),
            new ClassSummary("CNTT K22 · Nhóm 01", "Mạng máy tính", "30", "Kiểm tra giữa kỳ", "30 / 30", "Đã đóng · Mẫu", "neutral"),
            new ClassSummary("CNTT K22 · Nhóm 02", "Lập trình Java", "30", "Kiểm tra chương 4", "0 / 30", "Sắp mở · Mẫu", "purple")
        );
    }
    public static List<Integer> weeklySubmissions() { return List.of(14, 23, 18, 32, 27, 41, 29); }
}
