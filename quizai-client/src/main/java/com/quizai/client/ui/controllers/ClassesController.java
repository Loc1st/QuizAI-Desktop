package com.quizai.client.ui.controllers;

import com.quizai.common.api.ApiException;
import com.quizai.common.api.ApiModels.ClassView;
import com.quizai.common.api.QuizApiClient;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class ClassesController {
    @FXML private VBox classList;
    @FXML private VBox actions;
    @FXML private Label message;
    @FXML private Label count;
    @FXML private TextField joinCode;
    @FXML private ProgressIndicator progress;
    private QuizApiClient api;
    private Runnable onExpired;
    private boolean active = true;
    private boolean busy;

    public void configure(QuizApiClient api, Runnable onExpired) {
        this.api = api;
        this.onExpired = onExpired;
        refresh();
    }

    @FXML private void refresh() {
        run(api::listClasses, values -> {
            render(values);
            message.setText(values.isEmpty() ? "Bạn chưa tham gia lớp nào. Nhập mã do giảng viên cung cấp." : "Đã tải danh sách lớp.");
        });
    }

    @FXML private void join() {
        String code = joinCode.getText().trim();
        if (code.isEmpty()) { message.setText("Nhập mã tham gia lớp."); return; }
        run(() -> api.joinClass(code), result -> {
            joinCode.clear();
            reload("Đã tham gia lớp “" + result.name() + "”.");
        });
    }

    private void reload(String success) {
        run(api::listClasses, values -> { render(values); message.setText(success); });
    }

    private void render(List<ClassView> values) {
        classList.getChildren().clear();
        count.setText(values.size() + " lớp đã tham gia");
        for (ClassView item : values) {
            Label name = new Label(item.name());
            name.getStyleClass().add("section-title");
            name.setWrapText(true);
            Label status = new Label(item.status().equals("ACTIVE") ? "Đang hoạt động" : "Đã lưu trữ");
            status.getStyleClass().add("caption");
            VBox text = new VBox(6, name, status);
            HBox.setHgrow(text, Priority.ALWAYS);
            Button leave = new Button("Rời lớp");
            leave.setOnAction(event -> confirmLeave(item));
            HBox card = new HBox(16, text, leave);
            card.getStyleClass().add("card");
            card.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            classList.getChildren().add(card);
        }
    }

    private void confirmLeave(ClassView item) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(classList.getScene().getWindow());
        confirm.setTitle("Rời lớp");
        confirm.setHeaderText("Rời lớp “" + item.name() + "”?");
        confirm.setContentText("Lịch sử được giữ lại. Bạn không thể bắt đầu lượt thi mới; lượt đã bắt đầu vẫn được hoàn thành và nộp.");
        confirm.showAndWait().filter(button -> button == ButtonType.OK).ifPresent(button ->
                run(() -> api.leaveClass(item.id()), ignored -> reload("Đã rời lớp. Lịch sử đã có được giữ lại.")));
    }

    private <T> void run(Supplier<CompletableFuture<T>> operation, Consumer<T> completed) {
        if (!active || busy) return;
        setBusy(true);
        message.setText("Đang xử lý…");
        operation.get().whenComplete((result, failure) -> Platform.runLater(() -> {
            if (!active) return;
            setBusy(false);
            if (failure == null) completed.accept(result);
            else {
                Throwable cause = failure;
                while (cause.getCause() != null && !(cause instanceof ApiException)) cause = cause.getCause();
                if (cause instanceof ApiException error && error.status() == 401) onExpired.run();
                else message.setText(AuthController.friendly(cause));
            }
        }));
    }

    private void setBusy(boolean value) {
        busy = value;
        actions.setDisable(value);
        classList.setDisable(value);
        progress.setVisible(value);
        progress.setManaged(value);
    }

    public void deactivate() { active = false; joinCode.clear(); }
}
