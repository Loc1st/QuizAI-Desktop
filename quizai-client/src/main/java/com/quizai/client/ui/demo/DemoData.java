package com.quizai.client.ui.demo;

import java.util.List;

/** Fixed presentation fixtures. Never read or written by a business service. */
public final class DemoData {
    private DemoData() { }
    public record Assignment(String title, String subject, String deadline, String status, String tone) { }
    public static List<Assignment> assignments() {
        return List.of(
            new Assignment("Kiểm tra chương 3", "Lập trình Java · CNTT K23", "08/11 · 23:59", "Sắp mở · Mẫu", "purple"),
            new Assignment("Ôn tập cơ sở dữ liệu", "Cơ sở dữ liệu · CNTT K23", "09/11 · 17:00", "Bản nháp · Mẫu", "neutral"),
            new Assignment("Kiểm tra mạng máy tính", "Mạng máy tính · CNTT K23", "05/11 · 20:00", "Chờ đồng bộ · Mẫu", "amber")
        );
    }
    public static List<Integer> weeklyAnswers() { return List.of(12, 20, 16, 28, 24, 36, 18); }
}
