# QUIZAI DESKTOP — CODEX NEW SESSION: HANDOFF ONLY / READ-ONLY

Tôi đang mở **session mới** vì session Codex cũ đã sử dụng ~82% cửa sổ ngữ cảnh. Hãy khôi phục trạng thái dự án từ tài liệu nguồn **trong repository hiện tại**, không suy đoán từ lịch sử chat không có ở session này.

**ĐỌC TRƯỚC:** `docs/QUIZAI_TASK003_CODEX_NEW_SESSION_HANDOFF_v1.0.md`, sau đó `docs/QUIZAI_DB05_ERD_APPROVAL_RECORD_v1.0.md`, `docs/QUIZAI_DB05_ERD_FINAL_FOR_APPROVAL_v1.0.md`, `docs/QUIZAI_DB_DESIGN_DECISIONS_v0.19.md`, `docs/PROJECT_DECISIONS_v7.md`, `docs/REQUIREMENTS_BASELINE_v1.0.md`, và `docs/QUIZAI_TASK003_P0B_SOURCE_REPORT_v1.0.md` nếu có. Đọc/select phạm vi cần thiết để xác nhận các gate và giới hạn, không cần xuất lại toàn văn.

**BẮT BUỘC:**
- G1 / ERD v1.0 = **APPROVED**. SHA-256 ERD phải là `5ff37a672d9a76ce4092301f53064a850304fed0ace74d8f71fa9db766aa31ab`.
- G2 / database implementation = **NOT AUTHORIZED**. G3 / production = **NOT AUTHORIZED**.
- P0 và P0B = **COMPLETE**; P0B khớp 37/37 bảng, DBX-01…24, T01–T30 ở mức **specification**, không có runtime test.
- P1 CONFIG-ONLY mới là draft. **Lời nhắn này KHÔNG cấp quyền thực hiện P1.**
- Giữ nguyên Git working tree và staged/untracked modifications có sẵn.
- KHÔNG sửa/tạo/xóa file, KHÔNG chạy Gradle/build/test/startup, KHÔNG tải dependency, KHÔNG kết nối PostgreSQL, KHÔNG tạo SQL/migration/Entity/Repository, KHÔNG tự chạy P1 hay P2.
- AI giải thích sau bài do giảng viên giao vẫn cần Change Request, chưa thuộc schema MVP.

**NHIỆM VỤ HIỆN TẠI (CHỈ ĐỌC):**
1. Xác nhận thư mục `docs/` chứa đúng nguồn và ERD checksum; kiểm tra `git status` (không thay đổi git).
2. Nắm sáu điểm P0B cần review ở P2 từ handoff; không tự sửa ERD.
3. Trả một báo cáo ngắn gồm `SOURCES / GATES / EXISTING GIT STATE / OPEN P2 DESIGN CLARIFICATIONS / NEXT AUTHORIZATION`.
4. Kết luận **`P1 NOT AUTHORIZED — WAITING FOR PROJECT OWNER`**, rồi dừng.

Không tự diễn giải lời nhắn này thành quyền sửa repository hoặc chạy database.
